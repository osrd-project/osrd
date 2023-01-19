mod bounding_box;
mod layer_cache;
mod map_layers;
mod redis_utils;

use std::collections::HashMap;

use crate::db_connection::RedisPool;
pub use bounding_box::{BoundingBox, InvalidationZone};
pub use map_layers::{Layer, MapLayers};
use redis::RedisError;

use self::layer_cache::{
    count_tiles, get_cache_tile_key, get_layer_cache_prefix, get_tiles_to_invalidate,
    get_view_cache_prefix, Tile,
};
use self::redis_utils::{delete, keys};

async fn invalidate_full_layer_cache(
    redis_pool: &RedisPool,
    layer_name: &str,
    infra_id: i32,
    view_name: Option<&str>,
) -> Result<u64, RedisError> {
    let prefix: String = view_name.map_or(get_layer_cache_prefix(layer_name, infra_id), |view| {
        get_view_cache_prefix(layer_name, infra_id, view)
    });
    let matching_keys = keys(redis_pool, &format!("{prefix}.*")).await?;
    let number_of_deleted_keys = delete(redis_pool, matching_keys).await?;
    Ok(number_of_deleted_keys)
}

async fn invalidate_cache(
    redis_pool: &RedisPool,
    layer_name: &str,
    infra_id: i32,
    tiles_to_invalidate: HashMap<String, Vec<Tile>>,
) -> Result<u64, RedisError> {
    let mut keys_to_delete: Vec<String> = Vec::new();
    for (view_name, tiles) in tiles_to_invalidate {
        let cache_location = get_view_cache_prefix(layer_name, infra_id, &view_name);
        for tile in tiles {
            keys_to_delete.push(get_cache_tile_key(&cache_location, &tile));
        }
    }
    let number_of_deleted_keys = delete(redis_pool, keys_to_delete).await?;
    Ok(number_of_deleted_keys)
}

async fn invalidate_layer_zone(
    redis_pool: &RedisPool,
    infra_id: i32,
    layer_name: &str,
    zone: &InvalidationZone,
) -> Result<(), RedisError> {
    let max_tiles = 120;
    let mut affected_tiles: HashMap<String, Vec<Tile>> = HashMap::new();
    for (view_name, bbox) in [("geo", &zone.geo), ("sch", &zone.sch)] {
        if count_tiles(18, bbox) > max_tiles {
            invalidate_full_layer_cache(redis_pool, layer_name, infra_id, Some(view_name)).await?;
        } else {
            affected_tiles.insert(
                get_view_cache_prefix(layer_name, infra_id, view_name),
                get_tiles_to_invalidate(12, bbox),
            );
        }
    }
    if !affected_tiles.is_empty() {
        invalidate_cache(redis_pool, layer_name, infra_id, affected_tiles).await?;
    }
    Ok(())
}

async fn invalidate_layer(
    redis_pool: &RedisPool,
    infra_id: i32,
    layer_name: &str,
) -> Result<(), RedisError> {
    invalidate_full_layer_cache(redis_pool, layer_name, infra_id, None).await?;
    Ok(())
}

/// Invalidate a zone for all map layers
/// If the zone is invalide nothing is done
/// panic if fail
pub async fn invalidate_zone(
    redis_pool: &RedisPool,
    layers: &Vec<String>,
    infra_id: i32,
    zone: &InvalidationZone,
) {
    if !zone.is_valid() {
        return;
    }
    for layer in layers {
        let result = invalidate_layer_zone(redis_pool, infra_id, layer, zone).await;
        if result.is_err() {
            panic!("Failed to invalidate map layer: {}", result.unwrap_err());
        }
    }
}

/// Invalidate all map layers
/// panic if fail
pub async fn invalidate_all(redis_pool: &RedisPool, layers: &Vec<String>, infra_id: i32) {
    for layer_name in layers {
        let result = invalidate_layer(redis_pool, infra_id, layer_name).await;
        if result.is_err() {
            panic!("Failed to invalidate map layer: {}", result.unwrap_err());
        }
    }
}
