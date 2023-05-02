import React, { useMemo } from 'react';
import { useDispatch, useSelector } from 'react-redux';

import {
  updateSpeedSpaceSettings,
  updateTimePositionValues,
} from 'reducers/osrdsimulation/actions';
import {
  getIsPlaying,
  getPositionValues,
  getPresentSimulation,
  getSelectedTrain,
  getTimePosition,
} from 'reducers/osrdsimulation/selectors';
import prepareData from './prepareData';
import SpeedSpaceChart from './SpeedSpaceChart';

/**
 * HOC to provide store data
 * @param {RFC} Component
 * @returns RFC with OSRD Data. SignalSwitch
 */
const withOSRDData = (Component) =>
  function WrapperComponent(props) {
    const positionValues = useSelector(getPositionValues);
    const selectedTrain = useSelector(getSelectedTrain);
    const speedSpaceSettings = useSelector((state) => state.osrdsimulation.speedSpaceSettings);
    const timePosition = useSelector(getTimePosition);
    const simulation = useSelector(getPresentSimulation);

    const isPlaying = useSelector(getIsPlaying);

    const dispatch = useDispatch();

    const dispatchUpdateTimePositionValues = (newTimePositionValues) => {
      dispatch(updateTimePositionValues(newTimePositionValues));
    };

    const toggleSetting = (settingName) => {
      dispatch(
        updateSpeedSpaceSettings({
          ...speedSpaceSettings,
          [settingName]: !speedSpaceSettings[settingName],
        })
      );
    };

    // Prepare data
    const trainSimulation = useMemo(
      () => prepareData(simulation.trains[selectedTrain]),
      [simulation, selectedTrain]
    );

    return (
      <Component
        {...props}
        positionValues={positionValues}
        dispatchUpdateTimePositionValues={dispatchUpdateTimePositionValues}
        simulationIsPlaying={isPlaying}
        speedSpaceSettings={speedSpaceSettings}
        timePosition={timePosition}
        toggleSetting={toggleSetting}
        trainSimulation={trainSimulation}
      />
    );
  };

export const OSRDSpeedSpaceChart = withOSRDData(SpeedSpaceChart);

export default OSRDSpeedSpaceChart;
