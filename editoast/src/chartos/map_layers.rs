use std::collections::HashMap;

use mvt::GeomType;
use serde::{Deserialize, Serialize};
use serde_yaml::{self};

// select C.stuff from A inner join B C on C.id = C.id;
//                       \___________________________/
//                             a join expression
//                            C is an alias for B
type JoinExpr = String;

/// Layer view description
#[derive(Serialize, Deserialize, PartialEq, Debug)]
pub struct View {
    pub on_field: String,
    pub data_expr: String,
    #[serde(default)]
    pub exclude_fields: Vec<String>,
    #[serde(default)]
    pub joins: Vec<JoinExpr>,
    pub cache_duration: u32,
    #[serde(rename = "where", default)]
    pub where_expr: Vec<String>,
}

/// Layer description
#[derive(Serialize, Deserialize, PartialEq, Debug)]
pub struct Layer {
    pub table_name: String,
    pub views: HashMap<String, View>,
    #[serde(default)]
    pub id_field: Option<String>,
    #[serde(default)]
    pub attribution: Option<String>,
    geom_type: String,
}

pub enum GeoJsonType {
    Point,
    MultiPoint,
    LineString,
    MultiLineString,
}

impl Layer {
    pub fn geom_type(&self) -> GeomType {
        match self.geom_type.as_ref() {
            "Point" => GeomType::Point,
            "MultiPoint" => GeomType::Point,
            "LineString" => GeomType::Linestring,
            "MultiLineString" => GeomType::Linestring,
            value => panic!("Unsupported geom_type {value}"),
        }
    }
    pub fn geo_json_type(&self) -> GeoJsonType {
        match self.geom_type.as_ref() {
            "Point" => GeoJsonType::Point,
            "MultiPoint" => GeoJsonType::MultiPoint,
            "LineString" => GeoJsonType::LineString,
            "MultiLineString" => GeoJsonType::MultiLineString,
            value => panic!("Unsupported geom_type {value}"),
        }
    }
}

#[derive(Serialize, Deserialize, PartialEq, Debug)]
pub struct MapLayers {
    pub layers: HashMap<String, Layer>,
}

impl MapLayers {
    /// Parses file containing layers' description into MapLayers struct
    pub fn parse() -> MapLayers {
        serde_yaml::from_str(include_str!("../../map_layers.yml")).unwrap()
    }
}
