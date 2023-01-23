mod info;
mod layer;

use info::info_route;
use layer::{layer_view, mvt_view_tile};
use rocket::{routes, Route};

pub fn routes() -> Vec<Route> {
    routes![info_route, layer_view, mvt_view_tile]
}
