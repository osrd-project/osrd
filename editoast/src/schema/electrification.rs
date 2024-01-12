use super::utils::Identifier;
use super::utils::NonBlankString;
use super::ApplicableDirectionsTrackRange;
use super::OSRDIdentified;
use editoast_derive::InfraModel;

use super::OSRDTyped;
use super::ObjectType;

use crate::infra_cache::Cache;
use crate::infra_cache::ObjectCache;
use derivative::Derivative;

use serde::{Deserialize, Serialize};
#[derive(Debug, Derivative, Clone, Deserialize, Serialize, PartialEq, InfraModel)]
#[serde(deny_unknown_fields)]
#[infra_model(table = "crate::tables::infra_object_electrification")]
#[derivative(Default)]
pub struct Electrification {
    pub id: Identifier,
    pub voltage: NonBlankString,
    pub track_ranges: Vec<ApplicableDirectionsTrackRange>,
}

impl OSRDTyped for Electrification {
    fn get_type() -> ObjectType {
        ObjectType::Electrification
    }
}

impl OSRDIdentified for Electrification {
    fn get_id(&self) -> &String {
        &self.id
    }
}

impl Cache for Electrification {
    fn get_track_referenced_id(&self) -> Vec<&String> {
        self.track_ranges.iter().map(|tr| &*tr.track).collect()
    }

    fn get_object_cache(&self) -> ObjectCache {
        ObjectCache::Electrification(self.clone())
    }
}

#[cfg(test)]
mod test {

    use super::Electrification;
    use crate::models::infra::tests::test_infra_transaction;
    use actix_web::test as actix_test;
    use diesel_async::scoped_futures::ScopedFutureExt;

    #[actix_test]
    async fn test_persist() {
        test_infra_transaction(|conn, infra| {
            async move {
                let data = (0..10)
                    .map(|_| Electrification::default())
                    .collect::<Vec<Electrification>>();

                assert!(
                    Electrification::persist_batch(&data, infra.id.unwrap(), conn)
                        .await
                        .is_ok()
                );
            }
            .scope_boxed()
        })
        .await;
    }
}