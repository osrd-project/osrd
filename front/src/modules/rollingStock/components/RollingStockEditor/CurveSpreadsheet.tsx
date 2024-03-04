import React, { useEffect, useMemo, useState } from 'react';
import type { Dispatch, SetStateAction } from 'react';

import { DataSheetGrid, keyColumn, intColumn, floatColumn } from 'react-datasheet-grid';
import 'react-datasheet-grid/dist/style.css';
import { useTranslation } from 'react-i18next';

import type {
  ConditionalEffortCurveForm,
  DataSheetCurve,
  EffortCurveForms,
} from 'modules/rollingStock/types';
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
  const columns = [
    { ...keyColumn('speed', intColumn), title: t('speed') },
    { ...keyColumn('effort', floatColumn), title: t('effort') },
  ];

  const [needsSort, setNeedsSort] = useState<boolean>(false);

  const handleBlur = () => {
    setNeedsSort(true);
  };

  const updateRollingStockCurve = (newCurve: DataSheetCurve[]) => {
    if (!selectedTractionMode || !effortCurves) return;

    // Format the new curve
    const formattedCurve = formatCurve(newCurve);

    // Create the updated selected curve
    const updatedSelectedCurve = {
      ...selectedCurve,
      curve: formattedCurve,
    };

    // Replace the updated curve in the selected traction mode curves
    const updatedCurves = replaceElementAtIndex(
      selectedTractionModeCurves,
      selectedCurveIndex,
      updatedSelectedCurve
    );

    // Update the effort curves
    const updatedEffortCurve = {
      ...effortCurves,
      [selectedTractionMode]: {
        ...effortCurves[selectedTractionMode],
        curves: updatedCurves,
        ...(isDefaultCurve ? { default_curve: formattedCurve } : {}),
      },
    };

    // Set the updated effort curves
    setEffortCurves(updatedEffortCurve);
  };

  const spreadsheetCurve = useMemo(() => {
    const { speeds, max_efforts } = selectedCurve.curve;
    const filledDataSheet =
      speeds && max_efforts
        ? max_efforts.map((effort, index) => ({
            speed: speeds[index] !== undefined ? Math.round(msToKmh(speeds[index]!)) : '',
            // Effort needs to be displayed in kN
            effort: effort !== undefined ? effort / 1000 : '',
          }))
        : [];
    return filledDataSheet;
  }, [selectedCurve]);

  useEffect(() => {
    if (needsSort) {
      const sortedSpreadsheetValues = spreadsheetCurve
        .filter((item) => item.speed !== null || item.effort !== null)
        .sort((a, b) => {
          if (a.speed === null && b.speed === null) return 0;
          if (a.speed === null) return 1;
          if (b.speed === null) return -1;
          return Number(a.speed) - Number(b.speed);
        });

      updateRollingStockCurve(sortedSpreadsheetValues);
      setNeedsSort(false);
    }
  }, [needsSort]);

  return (
    <div className="rollingstock-editor-spreadsheet">
      <DataSheetGrid
        value={spreadsheetCurve}
        columns={columns}
        onChange={(e) => updateRollingStockCurve(e as DataSheetCurve[])}
        autoAddRow
        rowHeight={30}
        addRowsComponent={false}
        onBlur={handleBlur}
        onSelectionChange={handleBlur}
      />
    </div>
  );
};

export default CurveSpreadsheet;
