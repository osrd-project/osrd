import React, { type Dispatch, type SetStateAction, useMemo } from 'react';

import { useTranslation } from 'react-i18next';
import Spreadsheet, { createEmptyMatrix } from 'react-spreadsheet';
import type { CellBase, Matrix } from 'react-spreadsheet';

import type { ConditionalEffortCurveForm, EffortCurveForms } from 'modules/rollingStock/types';
import { replaceElementAtIndex } from 'utils/array';
import { msToKmh } from 'utils/physics';

import formatCurve from './formatSpreadSheetCurve';

type CurveSpreadsheetProps = {
  selectedCurve: ConditionalEffortCurveForm;
  selectedCurveIndex: number;
  selectedTractionModeCurves: ConditionalEffortCurveForm[];
  effortCurves: EffortCurveForms | null;
  setEffortCurves: Dispatch<SetStateAction<EffortCurveForms | null>>;
  selectedTractionMode: string | null;
  isDefaultCurve: boolean;
};

const CurveSpreadsheet = ({
  selectedCurve,
  selectedCurveIndex,
  selectedTractionModeCurves,
  effortCurves,
  setEffortCurves,
  selectedTractionMode,
  isDefaultCurve,
}: CurveSpreadsheetProps) => {
  const { t } = useTranslation('rollingstock');

  const spreadsheetCurve = useMemo(() => {
    const { speeds, max_efforts } = selectedCurve.curve;
    const filledMatrix: (
      | {
          value: string;
        }
      | undefined
    )[][] =
      speeds && max_efforts
        ? max_efforts.map((effort, index) => [
            {
              value:
                speeds[index] !== undefined ? Math.round(msToKmh(speeds[index]!)).toString() : '',
            },
            // Effort needs to be displayed in kN
            { value: effort !== undefined ? Math.round(effort / 1000).toString() : '' },
          ])
        : [];
    const numberOfRows = filledMatrix.length < 8 ? 8 - filledMatrix.length : 1;
    return filledMatrix.concat(createEmptyMatrix<CellBase<string>>(numberOfRows, 2));
  }, [selectedCurve]);

  const updateRollingStockCurve = (e: Matrix<{ value: string }>) => {
    if (!selectedTractionMode || !effortCurves) return;
    const formattedCurve = formatCurve(e);

    const updatedSelectedCurve = {
      ...selectedCurve,
      curve: formattedCurve,
    };

    // replace the updated curve
    const updatedCurves = replaceElementAtIndex(
      selectedTractionModeCurves,
      selectedCurveIndex,
      updatedSelectedCurve
    );

    const updatedEffortCurve = {
      ...effortCurves,
      [selectedTractionMode]: {
        ...effortCurves[selectedTractionMode],
        curves: updatedCurves,
        ...(isDefaultCurve ? { default_curve: formattedCurve } : {}),
      },
    };
    setEffortCurves(updatedEffortCurve);
  };

  const orderSpreadsheetValues = () => {
    const orderedValuesByVelocity = spreadsheetCurve.sort((a, b) => {
      // if a row has a max_effort, but no speed, it should appear at the top of the table
      if (b[0] && b[0].value === '') return 1;
      return Number(a[0]?.value) - Number(b[0]?.value);
    });
    updateRollingStockCurve(orderedValuesByVelocity);
  };

  return (
    <div className="rollingstock-editor-spreadsheet">
      <Spreadsheet
        data={spreadsheetCurve}
        onChange={(e) => {
          updateRollingStockCurve(e);
        }}
        onBlur={orderSpreadsheetValues}
        onKeyDown={(e) => {
          if (e.key === 'Enter') orderSpreadsheetValues();
        }}
        columnLabels={[t('speed'), t('effort')]}
      />
    </div>
  );
};

export default CurveSpreadsheet;
