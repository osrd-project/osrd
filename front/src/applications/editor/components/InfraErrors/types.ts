import type {
  GetInfraByIdErrorsApiArg,
  InfraError as InfraErrorApiType,
  InfraErrorType,
} from 'common/api/osrdEditoastApi';

// Error level
export type InfraErrorLevel = GetInfraByIdErrorsApiArg['level'];

// Error type
export type { InfraErrorType };

interface ObjectRef {
  obj_id: string;
  type: string;
}
type InfraErrorInformation = Omit<InfraErrorApiType['information'], 'error_type'>;
type InfraErrorDuplicatedGroup = InfraErrorInformation & {
  error_type: 'duplicated_group';
  original_group_path: string;
};
type InfraErrorEmptyObject = InfraErrorInformation & { error_type: 'empty_object' };
type InfraErrorInvalidGroup = InfraErrorInformation & {
  error_type: 'invalid_group';
  group: string;
  switch_type: string;
};
type InfraErrorInvalidReference = InfraErrorInformation & {
  error_type: 'invalid_reference';
  reference: ObjectRef;
};
type InfraErrorInvalidRoute = InfraErrorInformation & { error_type: 'invalid_route' };
type InfraErrorInvalidSwitchPorts = InfraErrorInformation & { error_type: 'invalid_switch_ports' };
type InfraErrorMissingRoute = InfraErrorInformation & { error_type: 'missing_route' };
type InfraErrorMissingBufferStop = InfraErrorInformation & {
  error_type: 'missing_buffer_stop';
  endpoint: string;
};
type InfraErrorObjectOutOfPath = InfraErrorInformation & {
  error_type: 'object_out_of_path';
  reference: ObjectRef;
};
type InfraErrorOddBufferStopLocation = InfraErrorInformation & {
  error_type: 'odd_buffer_stop_location';
};
type InfraErrorOutOfRange = InfraErrorInformation & {
  error_type: 'out_of_range';
  position: number;
  expected_range: [number, number];
};
type InfraErrorOverlappingSpeedSections = InfraErrorInformation & {
  error_type: 'overlapping_speed_sections';
  reference: ObjectRef;
};
type InfraErrorOverlappingSwitches = InfraErrorInformation & {
  error_type: 'overlapping_switches';
  reference: ObjectRef;
};
type InfraErrorOverlappingElectrifications = InfraErrorInformation & {
  error_type: 'overlapping_electrifications';
  reference: ObjectRef;
};
type InfraErrorUnknownPortName = InfraErrorInformation & {
  error_type: 'unknown_port_name';
  port_name: string;
};
type InfraErrorUnusedPort = InfraErrorInformation & {
  error_type: 'unused_port';
  port_name: string;
};
type InfraErrorNodeEndpointsNotUnique = InfraErrorInformation & {
  error_type: 'node_endpoints_not_unique';
};

// Type of an error
export type InfraError = Omit<InfraErrorApiType, 'informations'> & {
  information:
    | InfraErrorInformation
    | InfraErrorInvalidGroup
    | InfraErrorInvalidReference
    | InfraErrorInvalidRoute
    | InfraErrorInvalidSwitchPorts
    | InfraErrorObjectOutOfPath
    | InfraErrorOutOfRange
    | InfraErrorUnknownPortName
    | InfraErrorDuplicatedGroup
    | InfraErrorEmptyObject
    | InfraErrorMissingRoute
    | InfraErrorMissingBufferStop
    | InfraErrorOddBufferStopLocation
    | InfraErrorOverlappingSpeedSections
    | InfraErrorOverlappingSwitches
    | InfraErrorOverlappingElectrifications
    | InfraErrorUnusedPort
    | InfraErrorNodeEndpointsNotUnique;
};
