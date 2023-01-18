mod bounding_box;
mod layer_cache;

use std::collections::HashMap;

use crate::db_connection::RedisPool;
pub use bounding_box::BoundingBox;
pub use bounding_box::InvalidationZone;
use redis::RedisError;
use rocket_db_pools::deadpool_redis::redis::cmd;

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

fn get_cache_tile_key(view_prefix: &str, tile: &Tile) -> String {
    format!("{view_prefix}.tile/{}/{}/{}", tile.z, tile.x, tile.y)
}

async fn get_redis_keys(
    redis_pool: &RedisPool,
    key_pattern: &str,
) -> Result<Vec<String>, RedisError> {
    cmd(&format!("KEYS {key_pattern}"))
        .query_async::<_, Vec<String>>(&mut redis_pool.get().await.unwrap())
        .await
}

async fn delete(redis_pool: &RedisPool, keys_to_delete: Vec<String>) -> Result<u64, RedisError> {
    cmd(&format!("DEL {}", keys_to_delete.join(" ")))
        .query_async::<_, u64>(&mut redis_pool.get().await.unwrap())
        .await
}

async fn invalidate_full_layer_cache(
    redis_pool: &RedisPool,
    layer_name: &str,
    infra_id: i32,
    view_name: Option<&str>,
) -> Result<u64, RedisError> {
    let prefix: String = view_name.map_or(get_layer_cache_prefix(layer_name, infra_id), |view| {
        get_view_cache_prefix(layer_name, infra_id, view)
    });
    let matching_keys = get_redis_keys(redis_pool, &format!("{prefix}.*")).await?;
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
    tiles_to_invalidate.iter().map(|(view_name, tiles)| {
        let cache_location = get_view_cache_prefix(layer_name, infra_id, view_name);
        tiles
            .iter()
            .map(|tile| keys_to_delete.push(get_cache_tile_key(&cache_location, tile)));
    });
    let number_of_deleted_keys = delete(redis_pool, keys_to_delete).await?;
    Ok(number_of_deleted_keys)
}

/// Invalidate a zone for all chartos layers
/// If the zone is invalide nothing is done
pub async fn invalidate_zone(
    redis_pool: &RedisPool,
    infra_id: i32,
    zone: &InvalidationZone,
) -> Result<(), RedisError> {
    if !zone.is_valid() {
        return Ok(());
    }
    for layer in LAYERS {
        invalidate_layer_zone(redis_pool, infra_id, layer, zone).await?;
    }
    Ok(())
}

/// Invalidate all chartos layers
pub async fn invalidate_all(redis_pool: &RedisPool, infra_id: i32) -> Result<(), RedisError> {
    for layer in LAYERS {
        invalidate_layer(redis_pool, infra_id, layer).await?;
    }
    Ok(())
}

/// Invalidate a zone of chartos layer
/// Panic if the request failed
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
                get_tiles_to_invalidate(12, &bbox),
            );
        }
    }
    if !affected_tiles.is_empty() {
        invalidate_cache(redis_pool, layer_name, infra_id, affected_tiles).await?;
    }
    Ok(())
}

/// Invalidate a whole chartos layer
/// Panic if the request failed
async fn invalidate_layer(
    redis_pool: &RedisPool,
    infra_id: i32,
    layer_name: &str,
) -> Result<(), RedisError> {
    invalidate_full_layer_cache(redis_pool, layer_name, infra_id, None).await?;
    Ok(())
}
