import type { DataSheetCurve, EffortCurveForm } from 'modules/rollingStock/types';
import { kmhToMs } from 'utils/physics';

const formatToEffortCurve = (rows: DataSheetCurve[]): EffortCurveForm =>
  rows.reduce<EffortCurveForm>(
    (result, row) => {
      const speed = row.speed !== null ? kmhToMs(Number(row.speed)) : undefined;
      // Back-end needs effort in newton
      const effort = row.effort !== null ? Number(row.effort) * 1000 : undefined;

      if (speed !== undefined) {
        result.speeds.push(speed);
      }
      if (effort !== undefined) {
        result.max_efforts.push(effort);
      }

      return result;
    },
    { max_efforts: [], speeds: [] }
  );
/**
 * Given a spreadsheet, return an EffortCurve
 * - convert rows data to EffortCurve
 */
const formatCurve = (sheetValues: DataSheetCurve[]): EffortCurveForm =>
  formatToEffortCurve(sheetValues);

export default formatCurve;
