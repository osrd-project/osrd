use std::collections::HashMap;

use tracing::debug;

use super::new_ref_fix_delete_pair;
use super::Fix;
use crate::schema::InfraError;
use crate::schema::InfraErrorType;
use crate::schema::OSRDObject as _;
use crate::schema::ObjectRef;
use crate::schema::ObjectType;
use crate::schema::SwitchCache;

pub fn fix_switch(
    switch: &SwitchCache,
    errors: impl Iterator<Item = InfraError>,
) -> HashMap<ObjectRef, Fix> {
    errors
        .filter_map(|infra_error| match infra_error.get_sub_type() {
            InfraErrorType::InvalidSwitchPorts => Some(new_ref_fix_delete_pair(switch)),
            InfraErrorType::InvalidReference { reference }
                if reference.obj_type == ObjectType::TrackSection =>
            {
                Some(new_ref_fix_delete_pair(switch))
            }
            _ => {
                debug!("error not (yet) fixable for '{}'", infra_error.get_type());
                None
            }
        })
        .collect()
}
