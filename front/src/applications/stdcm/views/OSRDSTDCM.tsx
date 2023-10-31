import React, { useEffect, useState } from 'react';
import { useDispatch } from 'react-redux';

import STDCM_REQUEST_STATUS from 'applications/stdcm/consts';
import type { StdcmRequestStatus } from 'applications/stdcm/types';
import OSRDStdcmConfig from 'applications/stdcm/views/OSRDStdcmConfig';
import StdcmRequestModal from 'applications/stdcm/views/StdcmRequestModal';

import { updateSelectedTrainId, updateSelectedProjection } from 'reducers/osrdsimulation/actions';

export default function OSRDSTDCM() {
  const dispatch = useDispatch();
  const [currentStdcmRequestStatus, setCurrentStdcmRequestStatus] = useState<StdcmRequestStatus>(
    STDCM_REQUEST_STATUS.idle
  );
  useEffect(
    () => () => {
      dispatch(updateSelectedTrainId(undefined));
      dispatch(updateSelectedProjection(undefined));
    },
    []
  );

  return (
    <>
      <OSRDStdcmConfig
        currentStdcmRequestStatus={currentStdcmRequestStatus}
        setCurrentStdcmRequestStatus={setCurrentStdcmRequestStatus}
      />
      {currentStdcmRequestStatus === STDCM_REQUEST_STATUS.pending && (
        <StdcmRequestModal
          setCurrentStdcmRequestStatus={setCurrentStdcmRequestStatus}
          currentStdcmRequestStatus={currentStdcmRequestStatus}
        />
      )}
    </>
  );
}
