use std::collections::HashMap;

use crate::api_error::ApiResult;
use crate::chartos::{
    get_cache_tile_key, get_view_cache_prefix, GeoJsonType, Layer, MapLayers, Tile, View,
};
use crate::client::MapLayersConfig;
use crate::db_connection::{DBConnection, RedisPool};
use diesel::sql_types::{Json, Jsonb, Nullable};
use diesel::{sql_query, RunQueryDsl};
use mvt::{Feature, GeomEncoder, MapGrid, Tile as MvtTile, TileId};
use rocket::serde::json::{json, Value as JsonValue};
use rocket::State;
use rocket_db_pools::deadpool_redis::redis::cmd;
use serde::{Deserialize, Serialize};

use rocket::http::Status;
use thiserror::Error;

use crate::api_error::ApiError;

#[derive(Debug, Error)]
enum LayersError {
    #[error("Layer {} not found. Expected one of {:?}", .layer_name, .expected_names)]
    LayerNotFound {
        layer_name: String,
        expected_names: Vec<String>,
    },
    #[error("View {} not found. Expected one of {:?}", .view_name, .expected_names)]
    ViewNotFound {
        view_name: String,
        expected_names: Vec<String>,
    },
}

impl ApiError for LayersError {
    fn get_status(&self) -> Status {
        Status::NotFound
    }

    fn get_type(&self) -> &'static str {
        match self {
            LayersError::LayerNotFound { .. } => "editoast:layers:LayerNotFound",
            LayersError::ViewNotFound { .. } => "editoast:layers:ViewNotFound",
        }
    }
}

impl LayersError {
    pub fn new_layer_not_found<T: AsRef<str>>(name: T, map_layers: &MapLayers) -> Self {
        let mut expected_names: Vec<_> = map_layers.layers.keys().cloned().collect();
        expected_names.sort();
        Self::LayerNotFound {
            layer_name: name.as_ref().to_string(),
            expected_names,
        }
    }
    pub fn new_view_not_found<T: AsRef<str>>(name: T, layer: &Layer) -> Self {
        let mut expected_names: Vec<_> = layer.views.keys().cloned().collect();
        expected_names.sort();
        Self::ViewNotFound {
            view_name: name.as_ref().to_string(),
            expected_names,
        }
    }
}

fn create_get_object_sql_query(
    infra_id: i64,
    table_name: &str,
    view: &View,
    z: u64,
    x: u64,
    y: u64,
) -> String {
    format!(
        "
        WITH bbox AS (
            SELECT TileBBox({z}, {x}, {y}, 3857) AS geom
        )
        SELECT ST_AsGeoJson(geographic) AS geom, 
            {data_expr} {exclude_fields} AS data 
        FROM {table_name} layer 
            CROSS JOIN bbox 
            {joins} 
        WHERE layer.infra_id = {infra_id} 
            {where_condition}
            AND {on_field} && bbox.geom 
            AND ST_GeometryType({on_field}) != 'ST_GeometryCollection'
        ",
        on_field = view.on_field,
        data_expr = view.data_expr,
        exclude_fields = &format!(
            "{} {}",
            if view.exclude_fields.is_empty() {
                ""
            } else {
                "-"
            },
            view.exclude_fields
                .iter()
                .map(|field| format!("'{}'", field))
                .collect::<Vec<String>>()
                .join(" - ")
        ),
        joins = view.joins.join(" "),
        where_condition = &format!(
            "{} {}",
            if view.where_expr.is_empty() {
                ""
            } else {
                "AND"
            },
            view.where_expr
                .iter()
                .map(|field| format!("({})", field))
                .collect::<Vec<String>>()
                .join(" AND ")
        ),
    )
}

/// Returns layer view metadata to query tiles
#[get("/layer/<layer_slug>/mvt/<view_slug>?<infra>")]
pub async fn layer_view(
    layer_slug: &str,
    view_slug: &str,
    infra: i64,
    map_layers: &State<MapLayers>,
    map_layers_config: &State<MapLayersConfig>,
) -> ApiResult<JsonValue> {
    let layer = match map_layers.layers.get(layer_slug) {
        Some(layer) => layer,
        None => return Err(LayersError::new_layer_not_found(layer_slug, map_layers).into()),
    };

    if !layer.views.contains_key(view_slug) {
        return Err(LayersError::new_view_not_found(view_slug, layer).into());
    }

    let tiles_url_pattern = format!(
        "{root_url}/layers/tile/{layer_slug}/{view_slug}/{{z}}/{{x}}/{{y}}/?infra={infra}",
        root_url = map_layers_config.root_url
    );

    Ok(json!({
        "type": "vector",
        "name": layer_slug,
        "promoteId": {layer_slug: layer.id_field},
        "scheme": "xyz",
        "tiles": [tiles_url_pattern],
        "attribution": layer.attribution.clone().unwrap_or_default(),
        "minzoom": 0,
        "maxzoom": map_layers_config.max_zoom,
    }))
}

#[derive(Clone, QueryableByName, Queryable, Debug, Serialize, Deserialize)]
struct GeomAndData {
    #[diesel(sql_type = Nullable<Json>)]
    pub geom: Option<JsonValue>,
    #[diesel(sql_type = Jsonb)]
    pub data: JsonValue,
}

// LINE STRING GEOJSON

#[derive(Serialize, Deserialize)]
struct GeoJsonCRS {
    #[serde(rename = "type")]
    name: String,
    properties: HashMap<String, String>,
}

#[derive(Serialize, Deserialize)]
struct GeoJsonLineString {
    #[serde(rename = "type")]
    geo_type: String,
    crs: GeoJsonCRS,
    coordinates: Vec<(f64, f64)>,
}

#[derive(Serialize, Deserialize)]
struct GeoJsonMultiLine {
    #[serde(rename = "type")]
    geo_type: String,
    crs: GeoJsonCRS,
    coordinates: Vec<Vec<(f64, f64)>>,
}

#[derive(Serialize, Deserialize)]
struct GeoJsonPoint {
    #[serde(rename = "type")]
    geo_type: String,
    crs: GeoJsonCRS,
    coordinates: (f64, f64),
}

impl GeomAndData {
    pub fn parse(&self, mut encoder: GeomEncoder, geom_type: &GeoJsonType) -> GeomEncoder {
        match geom_type {
            GeoJsonType::Point => {
                let point =
                    serde_json::from_value::<GeoJsonPoint>(self.geom.clone().unwrap()).unwrap();
                encoder = encoder.point(point.coordinates.0, point.coordinates.1);
            }
            GeoJsonType::MultiPoint => {
                let line_string =
                    serde_json::from_value::<GeoJsonLineString>(self.geom.clone().unwrap())
                        .unwrap();
                for (x, y) in line_string.coordinates {
                    encoder = encoder.point(x, y);
                }
            }
            GeoJsonType::LineString => {
                let line_string =
                    serde_json::from_value::<GeoJsonLineString>(self.geom.clone().unwrap())
                        .unwrap();
                for (x, y) in line_string.coordinates {
                    encoder = encoder.point(x, y);
                }
            }
            GeoJsonType::MultiLineString => {
                let polygon =
                    serde_json::from_value::<GeoJsonMultiLine>(self.geom.clone().unwrap()).unwrap();
                for line in polygon.coordinates {
                    for (x, y) in line.iter() {
                        encoder = encoder.point(*x, *y);
                    }
                    encoder = encoder.complete().unwrap();
                }
            }
        };
        encoder.complete().unwrap()
    }
}

fn flatten_data(data: &JsonValue, feature: &mut Feature, key: String) {
    match data {
        JsonValue::Bool(bool) => feature.add_tag_bool(&key, *bool),
        JsonValue::Number(number) => {
            if number.is_i64() {
                feature.add_tag_int(&key, number.as_i64().unwrap());
            } else if number.is_u64() {
                feature.add_tag_uint(&key, number.as_u64().unwrap());
            } else {
                feature.add_tag_double(&key, number.as_f64().unwrap());
            }
        }
        JsonValue::String(string) => feature.add_tag_string(&key, &string),
        JsonValue::Array(json_values) => {
            for value in json_values.iter() {
                flatten_data(value, feature, key.clone());
            }
        }
        JsonValue::Object(map_values) => {
            for (k, value) in map_values.iter() {
                flatten_data(value, feature, format!("{key}_{k}"));
            }
        }
        JsonValue::Null => (),
    }
}

/// Returns layer view metadata to query tiles
#[get(
    "/tile/<layer_slug>/<view_slug>/<z>/<x>/<y>?<infra>",
    format = "application/x-protobuf"
)]
pub async fn mvt_view_tile<'a>(
    layer_slug: &str,
    view_slug: &str,
    z: u64,
    x: u64,
    y: u64,
    infra: i64,
    map_layers: &State<MapLayers>,
    // map_layers_config: &State<MapLayersConfig>,
    conn: DBConnection,
    pool: &RedisPool,
) -> ApiResult<Vec<u8>> {
    let layer = match map_layers.layers.get(layer_slug) {
        Some(layer) => layer,
        None => return Err(LayersError::new_layer_not_found(layer_slug, map_layers).into()),
    };
    let view = match layer.views.get(view_slug) {
        Some(view) => view,
        None => return Err(LayersError::new_view_not_found(view_slug, layer).into()),
    };
    let cache_key = get_cache_tile_key(
        &get_view_cache_prefix(layer_slug, infra, view_slug),
        &Tile { x, y, z },
    );
    let mvt_tile = cmd("GET")
        .arg(&cache_key)
        .query_async::<_, Vec<u8>>(&mut pool.get().await.unwrap())
        .await
        .unwrap_or_default();
    if !mvt_tile.is_empty() {
        return Ok(mvt_tile);
    }

    let query = create_get_object_sql_query(infra, &layer.table_name, view, z, x, y);
    let records = conn
        .run::<_, ApiResult<_>>(move |conn| {
            match sql_query(query).get_results::<GeomAndData>(conn) {
                Ok(results) => Ok(results),
                Err(err) => Err(err.into()),
            }
        })
        .await?;

    let mut tile = MvtTile::new(4096);
    let mvt_layer = tile.create_layer(layer_slug);
    let ts = tile.extent() as f64;
    let transform = MapGrid::default()
        .tile_transform(
            TileId::new(
                x.try_into().unwrap(),
                y.try_into().unwrap(),
                z.try_into().unwrap(),
            )
            .unwrap(),
        )
        .scale(ts, ts);
    let geo_json_type = layer.geo_json_type();
    let mut encoder = GeomEncoder::new(layer.geom_type(), transform);
    for record in records.iter() {
        encoder = record.parse(encoder, &geo_json_type);
    }
    let mut feature = mvt_layer.into_feature(encoder.encode().unwrap());
    for record in records.iter() {
        flatten_data(&record.data, &mut feature, String::new());
    }
    let layer = feature.into_layer();
    tile.add_layer(layer).unwrap();

    let bytes = tile.to_bytes().unwrap();
    let _ = cmd("SET")
        .arg(cache_key)
        .arg(bytes)
        .query_async::<_, ()>(&mut pool.get().await.unwrap())
        .await
        .unwrap();

    Ok(tile.to_bytes().unwrap())
}

#[cfg(test)]
mod tests {
    use crate::chartos::MapLayers;
    use crate::views::tests::create_test_client;
    use rocket::http::Status;
    use rocket::serde::json::{json, Value as JsonValue};

    use super::create_get_object_sql_query;

    fn test_query(uri: &str, status: Status, expected_body: JsonValue) {
        let client = create_test_client();
        let response = client.get(uri).dispatch();
        assert_eq!(response.status().clone(), status);
        let body: JsonValue =
            serde_json::from_str(response.into_string().unwrap().as_str()).unwrap();
        assert_eq!(expected_body, body)
    }

    #[test]
    fn layer_view() {
        test_query(
            "/layers/layer/track_sections/mvt/does_not_exist?infra=2",
            Status::NotFound,
            json!({
                "message": "View does_not_exist not found. Expected one of [\"geo\", \"sch\"]",
                "osrd_error_type": "editoast:layers:ViewNotFound"
            }),
        );
        test_query(
            "/layers/layer/track_sections/mvt/geo?infra=2",
            Status::Ok,
            json!({
                "type": "vector",
                "name": "track_sections",
                "promoteId": {
                    "track_sections": "id"
                },
                "scheme": "xyz",
                "tiles": ["http://localhost:8090/layers/tile/track_sections/geo/{z}/{x}/{y}/?infra=2"],
                "attribution": "",
                "minzoom": 0,
                "maxzoom": 18
            }),
        );
    }

    #[test]
    fn test_query_creation() {
        let map_layers = MapLayers::parse();
        let expected_queries = [
        "
        WITH bbox AS (
            SELECT TileBBox(7, 64, 44, 3857) AS geom
        )
        SELECT ST_AsMVTGeom(schematic, bbox.geom, 4096, 64) AS geom, 
            track_section.data - 'geo' - 'sch' AS data 
        FROM osrd_infra_tracksectionlayer layer 
            CROSS JOIN bbox 
            inner join osrd_infra_tracksectionmodel track_section on track_section.obj_id = layer.obj_id and track_section.infra_id = layer.infra_id 
        WHERE layer.infra_id = 1 
             
            AND schematic && bbox.geom 
            AND ST_GeometryType(schematic) != 'ST_GeometryCollection'
        ",
        "
        WITH bbox AS (
            SELECT TileBBox(7, 64, 44, 3857) AS geom
        )
        SELECT ST_AsMVTGeom(schematic, bbox.geom, 4096, 64) AS geom, 
            speed_section.data   AS data 
        FROM osrd_infra_speedsectionlayer layer 
            CROSS JOIN bbox 
            inner join osrd_infra_speedsectionmodel speed_section on speed_section.obj_id = layer.obj_id and speed_section.infra_id = layer.infra_id 
        WHERE layer.infra_id = 1 
            AND (not (speed_section.data @? '$.extensions.lpv_sncf.z'))
            AND schematic && bbox.geom 
            AND ST_GeometryType(schematic) != 'ST_GeometryCollection'
        "
        ];
        for (i, layer_name) in ["track_sections", "speed_sections"].iter().enumerate() {
            let track_sections = map_layers.layers.get(*layer_name).unwrap();
            let query = create_get_object_sql_query(
                1,
                &track_sections.table_name,
                track_sections.views.get("sch").unwrap(),
                7,
                64,
                44,
            );
            assert_eq!(expected_queries[i], query);
        }
    }
}
