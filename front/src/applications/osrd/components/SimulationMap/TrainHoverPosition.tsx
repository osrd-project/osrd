import React from 'react';
import { useSelector } from 'react-redux';
import { Layer, Source, Marker } from 'react-map-gl';
import lineSliceAlong from '@turf/line-slice-along';
import { Point, Feature, LineString } from 'geojson';
import cx from 'classnames';

import { RootState } from 'reducers';
import { datetime2time } from 'utils/timeManipulation';

interface TrainPosition {
  id: string;
  headPosition: Feature<Point>;
  tailPosition: Feature<Point>;
  headDistanceAlong: number;
  tailDistanceAlong: number;
  speedTime: {
    speed: number;
    time: number;
  };
  trainLength: number;
}

function getFill(isSelectedTrain: boolean, ecoBlocks) {
  if (isSelectedTrain) {
    return ecoBlocks ? '#82be00' : '#303383';
  }
  return '#333';
}

function getSpeedAndTimeLabel(isSelectedTrain, ecoBlocks, point: TrainPosition) {
  if (isSelectedTrain) {
    return (
      <>
        <span
          className={cx('small', 'font-weight-bold', ecoBlocks ? 'text-secondary' : 'text-primary')}
        >
          {Math.round(point?.speedTime?.speed ?? 0)}
          km/h
        </span>
        <span className="ml-2 small">{point.speedTime && datetime2time(point.speedTime.time)}</span>
      </>
    );
  }
  return (
    <>
      {/* <small>{point.properties.name}</small> */}
      <span className="small ml-1 font-weight-bold text-muted">
        {Math.round(point?.speedTime?.speed)}
        km/h
      </span>
    </>
  );
}

// When the train is backward, lineSliceAlong will crash. we need to have head and tail in the right order
function makeDisplayedHeadAndTail(point: TrainPosition) {
  const trueHead = Math.max(point.tailDistanceAlong, point.headDistanceAlong);
  const trueTail = Math.max(trueHead - point.trainLength, 0);
  const head = Math.max(trueHead, trueTail);
  const tail = Math.min(trueHead, trueTail);
  return { tail, head };
}

function getLengthFactorToKeepLabelPlacedCorrectlyWhenZooming(
  viewport: {
    zoom: number;
    transformRequest: (url: string, resourceType: string, urlmap: string) => any;
  },
  threshold = 12
) {
  return 2 ** (threshold - viewport?.zoom);
}

interface TrainHoverPositionProps {
  point: TrainPosition;
  isSelectedTrain: boolean;
  geojsonPath: Feature<LineString>;
}

const shiftFactor = {
  long: 1 / 450,
  lat: 1 / 1000,
};
function TrainHoverPosition(props: TrainHoverPositionProps) {
  const { point, isSelectedTrain, geojsonPath } = props;
  const { selectedTrain, allowancesSettings } = useSelector(
    (state: RootState) => state.osrdsimulation
  );
  const { viewport } = useSelector((state: RootState) => state.map);
  const simulation = useSelector((state: RootState) => state.osrdsimulation.simulation.present);
  const trainID = simulation.trains[selectedTrain].id;
  const { ecoBlocks } = allowancesSettings[trainID];
  const fill = getFill(isSelectedTrain, ecoBlocks);
  const label = getSpeedAndTimeLabel(isSelectedTrain, ecoBlocks, point);

  if (geojsonPath && point.headDistanceAlong && point.tailDistanceAlong) {
    const zoomLengthFactor = getLengthFactorToKeepLabelPlacedCorrectlyWhenZooming(viewport);
    const { tail, head } = makeDisplayedHeadAndTail(point);
    const trainGeoJsonPath = lineSliceAlong(geojsonPath, tail, head);

    return (
      <>
        <Marker
          className="map-search-marker"
          longitude={
            point.headPosition.geometry.coordinates[0] + zoomLengthFactor * shiftFactor.long
          }
          latitude={point.headPosition.geometry.coordinates[1] + zoomLengthFactor * shiftFactor.lat}
        >
          {label}
        </Marker>
        <Source type="geojson" data={trainGeoJsonPath}>
          <Layer
            id={`${point.id}-path`}
            type="line"
            paint={{
              'line-width': 8,
              'line-color': fill,
            }}
            layout={{
              'line-cap': 'round',
            }}
          />
        </Source>
      </>
    );
  }
  return null;
}

export default TrainHoverPosition;
