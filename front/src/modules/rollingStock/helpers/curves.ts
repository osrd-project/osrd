import { geti18nKeyForNull } from 'utils/strings';
import type { RollingStockComfortType } from 'common/api/osrdEditoastApi';

export const getCurveName = (
  name: string,
  comfort: RollingStockComfortType,
  electricalProfileLevel: string | null,
  powerRestrictionCode: string | null,
  isOnEditionMode?: boolean
) => {
  const electricalProfile = isOnEditionMode ? ` ${geti18nKeyForNull(electricalProfileLevel)}` : '';
  const powerRestriction = powerRestrictionCode
    ? ` ${geti18nKeyForNull(powerRestrictionCode)}`
    : '';
  return `${name} ${comfort}${electricalProfile}${powerRestriction}`;
};

export default getCurveName;
