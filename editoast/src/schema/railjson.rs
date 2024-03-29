use derivative::Derivative;
use serde::Deserialize;
use serde::Serialize;

use super::BufferStop;
use super::Detector;
use super::Electrification;
use super::NeutralSection;
use super::OperationalPoint;
use super::Route;
use super::Signal;
use super::SpeedSection;
use super::Switch;
use super::SwitchType;
use super::TrackSection;

pub const RAILJSON_VERSION: &str = "3.4.11";

#[derive(Deserialize, Derivative, Serialize, Clone, Debug)]
#[derivative(Default)]
#[serde(deny_unknown_fields)]
pub struct RailJson {
    #[derivative(Default(value = r#"RAILJSON_VERSION.to_string()"#))]
    pub version: String,
    pub operational_points: Vec<OperationalPoint>,
    pub routes: Vec<Route>,
    pub extended_switch_types: Vec<SwitchType>,
    pub switches: Vec<Switch>,
    pub track_sections: Vec<TrackSection>,
    pub speed_sections: Vec<SpeedSection>,
    pub neutral_sections: Vec<NeutralSection>,
    pub electrifications: Vec<Electrification>,
    pub signals: Vec<Signal>,
    pub buffer_stops: Vec<BufferStop>,
    pub detectors: Vec<Detector>,
}
