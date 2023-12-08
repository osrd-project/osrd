import * as d3 from 'd3';
import { select as d3select } from 'd3-selection';

import { Chart, SimulationTrain, ConsolidatedPosition } from 'reducers/osrdsimulation/types';
import {
  defineLinear,
  defineTime,
  isSpaceTimeChart,
} from 'modules/simulationResult/components/ChartHelpers/ChartHelpers';
import defineChart from 'modules/simulationResult/components/ChartHelpers/defineChart';
import { ChartAxes } from '../simulationResultsConsts';

export default function createSpaceTimeChart<T extends number | Date>(
  chart: Chart | undefined,
  chartID: string,
  simulationTrains: SimulationTrain[],
  heightOfSpaceTimeChart: number,
  keyValues: ChartAxes,
  ref: React.MutableRefObject<HTMLDivElement> | React.RefObject<HTMLDivElement>,
  reset: boolean,
  rotate: boolean
): Chart {
  d3select(`#${chartID}`).remove();

  const xValues: T[] = simulationTrains
    .map((train) =>
      train.headPosition.map((section) =>
        section.map((position) => (isSpaceTimeChart(keyValues) ? position.time : position.position))
      )
    )
    .flat(Infinity) as T[];

  function getMax(pos: 'tailPosition' | 'headPosition') {
    return d3.max(
      simulationTrains.flatMap(
        (train) =>
          d3.max(
            train[pos].map(
              (section) => d3.max(section.map((step: ConsolidatedPosition) => step.position)) as T
            )
          ) as T
      )
    );
  }
  const dataSimulationLinearMax = d3.max([getMax('tailPosition'), getMax('headPosition')] as T[]);

  const defineX = chart === undefined || reset ? defineTime(d3.extent(xValues) as [T, T]) : chart.x;

  const defineY =
    chart === undefined || reset ? defineLinear(Number(dataSimulationLinearMax), 0.05) : chart.y;

  const width = parseInt(d3select(`#container-${chartID}`)?.style('width'), 10);

  const chartLocal = defineChart(
    width,
    heightOfSpaceTimeChart,
    defineX,
    defineY,
    ref,
    rotate,
    keyValues,
    chartID
  );
  return chart === undefined || reset ? chartLocal : { ...chartLocal, x: chart.x, y: chart.y };
}
