mod bounding_box;
mod layer_cache;

use std::collections::HashMap;
use std::f32::consts::E;

pub use bounding_box::BoundingBox;
pub use bounding_box::InvalidationZone;
use redis::RedisError;
use rocket_db_pools::deadpool_redis::redis::cmd;

use reqwest::Client;
use serde_json::json;

use crate::client::ChartosConfig;
use crate::db_connection::RedisPool;

use self::layer_cache::count_tiles;
use self::layer_cache::get_tiles_to_invalidate;
use self::layer_cache::Tile;

const LAYERS: [&str; 12] = [
    "track_sections",
    "signals",
    "speed_sections",
    "track_section_links",
    "switches",
    "detectors",
    "buffer_stops",
    "routes",
    "operational_points",
    "catenaries",
    "lpv_panels",
    "errors",
];

pub fn get_layer_cache_prefix(layer_name: &str, infra_id: i32) -> String {
    format!("chartis.layer.{layer_name}.infra_{infra_id}")
}

pub fn get_view_cache_prefix(layer_name: &str, infra_id: i32, view_name: &str) -> String {
    format!(
        "{layer_prefix}.{view_name}",
        layer_prefix = get_layer_cache_prefix(layer_name, infra_id),
    )
}

async fn invalidate_full_layer_cache(
    redis_pool: &RedisPool,
    layer_name: &str,
    infra_id: i32,
    view_name: Option<&str>,
) -> Result<i64, RedisError> {
    let prefix: String = view_name.map_or(get_layer_cache_prefix(layer_name, infra_id), |view| {
        get_view_cache_prefix(layer_name, infra_id, view)
    });
    let key_pattern = format!("{prefix}.*");
    let matching_keys = cmd(&format!("KEYS {key_pattern}"))
        .query_async::<_, Vec<String>>(&mut redis_pool.get().await.unwrap())
        .await?;
    let number_of_deleted_keys = cmd(&format!("DEL {}", matching_keys.join(" ")))
        .query_async::<_, i64>(&mut redis_pool.get().await.unwrap())
        .await?;
    Ok(number_of_deleted_keys)
}

async fn invalidate_cache(
    redis_pool: &RedisPool,
    layer_name: &str,
    infra_id: i32,
    tiles_to_invalidate: HashMap<String, Tile>,
) {
}

/// Invalidate a zone for all chartos layers
/// If the zone is invalide nothing is done
pub async fn invalidate_zone(
    infra_id: i32,
    chartos_config: &ChartosConfig,
    zone: &InvalidationZone,
) {
    if !zone.is_valid() {
        return;
    }

    for layer in LAYERS {
        invalidate_layer_zone(infra_id, layer, zone, chartos_config).await;
    }
}

/// Invalidate all chartos layers
pub async fn invalidate_all(infra_id: i32, chartos_config: &ChartosConfig) {
    for layer in LAYERS {
        invalidate_layer(infra_id, layer, chartos_config).await;
    }
}

/// Invalidate a zone of chartos layer
/// Panic if the request failed
async fn invalidate_layer_zone(
    redis_pool: &RedisPool,
    infra_id: i32,
    layer_name: &str,
    zone: &InvalidationZone,
    chartos_config: &ChartosConfig,
) -> Result<(), RedisError> {
    let max_tiles = 120;
    let mut affected_tiles: HashMap<String, Vec<Tile>> = HashMap::new();
    for (view_name, bbox) in [("geo", &zone.geo), ("sch", &zone.sch)] {
        if count_tiles(18, bbox) > max_tiles {
            invalidate_full_layer_cache(redis_pool, layer_name, infra_id, Some(view_name)).await?;
        } else {
            affected_tiles.insert(view_name.into(), get_tiles_to_invalidate(12, &bbox));
        }
    }
    if !affected_tiles.is_empty() {
        // invalidate_cache
    }
    Ok(())

    // let resp = Client::new()
    //     .post(format!(
    //         "{}layer/{}/invalidate_bbox/?infra={}",
    //         chartos_config.url(),
    //         layer,
    //         infra_id
    //     ))
    //     .json(&json!([
    //         {
    //             "view": "geo",
    //             "bbox": zone.geo,
    //         },
    //         {
    //             "view": "sch",
    //             "bbox": zone.sch,
    //         }
    //     ]))
    //     .bearer_auth(&chartos_config.chartos_token)
    //     .send()
    //     .await
    //     .expect("Failed to send invalidate request to chartos");
    // if !resp.status().is_success() {
    //     panic!("Failed to invalidate chartos layer: {}", resp.status());
    // }
}

/// Invalidate a whole chartos layer
/// Panic if the request failed
async fn invalidate_layer(infra_id: i32, layer: &str, chartos_config: &ChartosConfig) {
    let resp = Client::new()
        .post(format!(
            "{}layer/{}/invalidate/?infra={}",
            chartos_config.url(),
            layer,
            infra_id
        ))
        .bearer_auth(&chartos_config.chartos_token)
        .send()
        .await
        .expect("Failed to send invalidate request to chartos");
    if !resp.status().is_success() {
        panic!("Failed to invalidate chartos layer: {}", resp.status());
    }
}
