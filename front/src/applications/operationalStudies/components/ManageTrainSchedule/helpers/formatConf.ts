import { setFailure } from 'reducers/main';
import { OsrdConfState } from 'applications/operationalStudies/consts';
import { time2sec } from 'utils/timeManipulation';
import { Dispatch } from 'redux';
import { kmh2ms } from 'utils/physics';

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export default function formatConf(
  dispatch: Dispatch,
  t: (arg0: string) => string,
  osrdconf: OsrdConfState,
  ignoreTrainAddSettings = false
) {
  let error = false;
  if (!osrdconf.origin) {
    error = true;
    dispatch(
      setFailure({
        name: t('errorMessages.trainScheduleTitle'),
        message: t('errorMessages.noOrigin'),
      })
    );
  }
  if (!osrdconf.departureTime) {
    error = true;
    dispatch(
      setFailure({
        name: t('errorMessages.trainScheduleTitle'),
        message: t('errorMessages.noDepartureTime'),
      })
    );
  }
  if (!osrdconf.destination) {
    error = true;
    dispatch(
      setFailure({
        name: t('errorMessages.trainScheduleTitle'),
        message: t('errorMessages.noDestination'),
      })
    );
  }
  if (!osrdconf.rollingStockID) {
    error = true;
    dispatch(
      setFailure({
        name: t('errorMessages.trainScheduleTitle'),
        message: t('errorMessages.noRollingStock'),
      })
    );
  }
  if (!osrdconf.name) {
    error = true;
    dispatch(
      setFailure({
        name: t('errorMessages.trainScheduleTitle'),
        message: t('errorMessages.noName'),
      })
    );
  }
  if (!osrdconf.timetableID) {
    error = true;
    dispatch(
      setFailure({
        name: t('errorMessages.trainScheduleTitle'),
        message: t('errorMessages.noTimetable'),
      })
    );
  }

  // TrainAddSettings tests
  if (!ignoreTrainAddSettings) {
    if (osrdconf.trainCount < 1) {
      error = true;
      dispatch(
        setFailure({
          name: t('errorMessages.trainScheduleTitle'),
          message: t('errorMessages.noTrainCount'),
        })
      );
    }
    if (osrdconf.trainDelta < 1) {
      error = true;
      dispatch(
        setFailure({
          name: t('errorMessages.trainScheduleTitle'),
          message: t('errorMessages.noDelta'),
        })
      );
    }
    if (osrdconf.trainStep < 1) {
      error = true;
      dispatch(
        setFailure({
          name: t('errorMessages.trainScheduleTitle'),
          message: t('errorMessages.noTrainStep'),
        })
      );
    }
  }

  if (!error) {
    const osrdConfSchedule = {
      train_name: osrdconf.name,
      labels: osrdconf.labels,
      departure_time: time2sec(osrdconf.departureTime),
      initial_speed: osrdconf.initialSpeed ? kmh2ms(osrdconf.initialSpeed) : 0,
      rolling_stock: osrdconf.rollingStockID,
      comfort: osrdconf.rollingStockComfort,
      speed_limit_tags: osrdconf.speedLimitByTag,
      power_restriction_ranges: osrdconf.powerRestriction,
      options: {
        ignore_electrical_profiles: !osrdconf.usingElectricalProfiles,
      },
    };
    return osrdConfSchedule;
  }
  return false;
}
