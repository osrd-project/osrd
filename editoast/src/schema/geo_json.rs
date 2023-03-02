use mvt::GeomType;

use serde::{Deserialize, Serialize};

/// GeoJson representation
#[derive(Clone, Serialize, Deserialize, Debug)]
#[serde(tag = "type")]
pub enum GeoJsonType {
    Point,
    MultiPoint,
    LineString,
    MultiLineString,
}

impl GeoJsonType {
    /// Gets MVT geom type corresponding to the GeoJson type
    pub fn get_geom_type(&self) -> GeomType {
        match self {
            GeoJsonType::Point => GeomType::Point,
            GeoJsonType::MultiPoint => GeomType::Point,
            GeoJsonType::LineString => GeomType::Linestring,
            GeoJsonType::MultiLineString => GeomType::Linestring,
        }
    }
}
