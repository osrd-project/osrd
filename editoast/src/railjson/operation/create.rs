use super::{ObjectType, TrackSection};
use crate::infra_cache::InfraCache;
use diesel::{sql_query, PgConnection, RunQueryDsl};
use rocket::serde::Deserialize;
use std::collections::{HashMap, HashSet};

#[derive(Clone, Deserialize)]
#[serde(crate = "rocket::serde", tag = "obj_type")]
pub enum CreateOperation {
    TrackSection { railjson: TrackSection },
}

impl CreateOperation {
    pub fn apply(&self, infra_id: i32, conn: &PgConnection) {
        sql_query(format!(
            "INSERT INTO {} (infra_id, obj_id, data) VALUES ({}, '{}', '{}')",
            self.get_obj_type().get_table(),
            infra_id,
            self.get_obj_id(),
            self.get_data(),
        ))
        .execute(conn)
        .expect("An error occured while applying a deletion");
    }

    pub fn get_updated_objects(
        &self,
        update_lists: &mut HashMap<ObjectType, HashSet<String>>,
        infra_cache: &InfraCache,
    ) {
        update_lists
            .entry(self.get_obj_type())
            .or_insert(Default::default())
            .insert(self.get_obj_id());

        if self.get_obj_type() == ObjectType::TrackSection {
            infra_cache.get_tracks_dependencies(&self.get_obj_id(), update_lists);
        }
    }

    pub fn get_obj_type(&self) -> ObjectType {
        match self {
            CreateOperation::TrackSection { railjson: _ } => ObjectType::TrackSection,
        }
    }

    pub fn get_obj_id(&self) -> String {
        match self {
            CreateOperation::TrackSection { railjson } => railjson.id.clone(),
        }
    }

    pub fn get_data(&self) -> String {
        match self {
            CreateOperation::TrackSection { railjson } => serde_json::to_string(railjson).unwrap(),
        }
    }
}
