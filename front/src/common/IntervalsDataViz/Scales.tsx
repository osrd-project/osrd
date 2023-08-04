import React, { useEffect, useState } from 'react';
import cx from 'classnames';

import { roundNumber, shortNumber } from './utils';

export const ResizingScale: React.FC<{
  begin: number;
  end: number;
  className?: string;
}> = ({ begin, end, className }) => {
  const [ticksCount, setTicksCount] = useState<number>(10);

  const inf = roundNumber(begin, true);
  const sup = roundNumber(end, false);
  const step = roundNumber((end - begin) / ticksCount, true);
  const stepMultiplier = 10 ** (step.toString().length - 1);
  const roundedStep = Math.ceil(step / stepMultiplier) * stepMultiplier;
  console.log('step : ', step, stepMultiplier, roundedStep);
  console.log('width : ', Math.round((roundedStep / sup) * 100));

  /** redraw the scale when window resized horizontally */
  useEffect(() => {
    const debounceResize = () => {
      let debounceTimeoutId;
      clearTimeout(debounceTimeoutId);
      debounceTimeoutId = setTimeout(() => {
        const graphWidth = document.getElementById('linear-metadata-dataviz-content')?.offsetWidth;
        if (graphWidth) {
          setTicksCount(Math.round(graphWidth / 100));
        }
      }, 15);
    };
    window.addEventListener('resize', debounceResize);
    return () => {
      window.removeEventListener('resize', debounceResize);
    };
  }, []);

  return (
    <div className={`scale ${className}`}>
      <div className="axis-values">
        {Array(ticksCount)
          .fill(0)
          .map((_, i) => (
            <div
              key={i}
              style={{
                width: i !== ticksCount - 1 ? `${Math.round((roundedStep / sup) * 100)}%` : '',
                // flexBasis: i !== ticksCount - 1 ? `${Math.round((roundedStep / sup) * 100)}%` : '',
              }}
            >
              {i === 0 && <span className="bottom-axis-value">{shortNumber(inf)}</span>}
              {i !== ticksCount - 1 && (
                <span className="ticks-axis-value">
                  {shortNumber(roundedStep * i + roundedStep + inf, true)}
                </span>
              )}
              {i === ticksCount - 1 && <span className="top-axis-value">{shortNumber(sup)}</span>}
            </div>
          ))}
      </div>
    </div>
  );
};

export const SimpleScale: React.FC<{
  className?: string;
  begin: number;
  end: number;
  min?: number;
  max?: number;
}> = ({ className, begin, end, min, max }) => {
  const [inf, setInf] = useState<number>(0);
  const [sup, setSup] = useState<number>(0);

  useEffect(() => {
    setInf(roundNumber(begin, true));
    setSup(roundNumber(end, false));
  }, [begin, end]);

  return (
    <div className={`scale ${className}`}>
      <div className="axis-values">
        <p
          className={cx(
            (min === undefined || (min !== undefined && min === begin)) && 'font-weight-bold'
          )}
          title={`${inf}`}
        >
          {shortNumber(inf)}
        </p>
        <p
          className={cx(
            (max === undefined || (max !== undefined && max === end)) && 'font-weight-bold'
          )}
          title={`${sup}`}
        >
          {shortNumber(sup)}
        </p>
      </div>
    </div>
  );
};
