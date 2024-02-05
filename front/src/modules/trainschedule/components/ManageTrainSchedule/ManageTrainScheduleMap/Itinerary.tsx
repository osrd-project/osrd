import React from 'react';
import { useSelector } from 'react-redux';
import { Source } from 'react-map-gl/maplibre';

import { useOsrdConfSelectors } from 'common/osrdContext';
import OrderedLayer from 'common/Map/Layers/OrderedLayer';

interface ItineraryProps {
  layerOrder: number;
}

export default function Itinerary({ layerOrder }: ItineraryProps) {
  const { getGeojson, getOrigin, getDestination } = useOsrdConfSelectors();
  const geojson = useSelector(getGeojson);
  const origin = useSelector(getOrigin);
  const destination = useSelector(getDestination);
  if (geojson && geojson.geographic && origin !== undefined && destination !== undefined) {
    return (
      <Source type="geojson" data={geojson.geographic}>
        <OrderedLayer
          type="line"
          paint={{
            'line-width': 5,
            'line-color': 'rgba(210, 225, 0, 0.75)',
          }}
          layerOrder={layerOrder}
        />
      </Source>
    );
  }
  return null;
}
