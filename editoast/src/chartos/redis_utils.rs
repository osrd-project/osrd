pub use super::bounding_box::{BoundingBox, InvalidationZone};
pub use super::map_layers::{Layer, MapLayers};
use crate::db_connection::RedisPool;
use redis::RedisError;
use rocket_db_pools::deadpool_redis::redis::cmd;

pub async fn keys(redis_pool: &RedisPool, key_pattern: &str) -> Result<Vec<String>, RedisError> {
    cmd("KEYS")
        .arg(key_pattern)
        .query_async::<_, Vec<String>>(&mut redis_pool.get().await.unwrap())
        .await
}

pub async fn delete(
    redis_pool: &RedisPool,
    keys_to_delete: Vec<String>,
) -> Result<u64, RedisError> {
    cmd("DEL")
        .arg(keys_to_delete.join(" "))
        .query_async::<_, u64>(&mut redis_pool.get().await.unwrap())
        .await
}

#[cfg(test)]
mod tests {
    use redis::RedisError;
    use rocket::tokio;
    use rocket_db_pools::deadpool_redis::{redis::cmd, Config as RedisPoolConfig, Runtime};

    use crate::{
        chartos::redis_utils::{delete, keys},
        client::RedisConfig,
        db_connection::RedisPool,
    };

    async fn set(redis_pool: &RedisPool, key: &str, value: &str) -> Result<(), RedisError> {
        cmd("SET")
            .arg(key)
            .arg(value)
            .query_async::<_, ()>(&mut redis_pool.get().await.unwrap())
            .await
    }

    fn create_redis_pool() -> RedisPool {
        let cfg = RedisPoolConfig::from_url(
            RedisConfig {
                ..Default::default()
            }
            .redis_url,
        );
        let pool = cfg.create_pool(Some(Runtime::Tokio1)).unwrap();
        RedisPool(pool)
    }

    #[tokio::test]
    async fn test_write_and_delete_key() {
        let redis_pool = create_redis_pool();
        let test_keys = keys(&redis_pool, "test_*").await.unwrap();
        assert!(test_keys.is_empty());
        set(&redis_pool, "test_1", "value_1").await.unwrap();
        let test_keys = keys(&redis_pool, "test_*").await.unwrap();
        assert_eq!(test_keys, vec!["test_1"]);
        let result = delete(&redis_pool, vec![String::from("test_1")])
            .await
            .unwrap();
        assert_eq!(result, 1);
        let test_keys = keys(&redis_pool, "test_*").await.unwrap();
        assert!(test_keys.is_empty());
    }
}
