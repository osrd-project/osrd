import { useSelector } from 'react-redux';

import { updateSelectedTrainId } from 'reducers/osrdsimulation/actions';
import {
  getAllowancesSettings,
  getIsPlaying,
  getPresentSimulation,
  getSelectedProjection,
  getSelectedTrain,
} from 'reducers/osrdsimulation/selectors';
import { persistentUpdateSimulation } from 'reducers/osrdsimulation/simulation';
import type { SimulationSnapshot } from 'reducers/osrdsimulation/types';
import { useAppDispatch } from 'store';

export const useStoreDataForSpaceTimeChart = () => {
  const dispatch = useAppDispatch();

  return {
    allowancesSettings: useSelector(getAllowancesSettings),
    selectedTrain: useSelector(getSelectedTrain),
    selectedProjection: useSelector(getSelectedProjection),
    simulation: useSelector(getPresentSimulation),
    simulationIsPlaying: useSelector(getIsPlaying),
    dispatchUpdateSelectedTrainId: (_selectedTrainId: number) => {
      dispatch(updateSelectedTrainId(_selectedTrainId));
    },
    dispatchPersistentUpdateSimulation: (simulation: SimulationSnapshot) => {
      dispatch(persistentUpdateSimulation(simulation));
    },
  };
};

export default useStoreDataForSpaceTimeChart;
