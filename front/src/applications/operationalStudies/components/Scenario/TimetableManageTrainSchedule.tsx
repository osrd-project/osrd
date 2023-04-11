import React from 'react';
import { FaPen, FaPlus } from 'react-icons/fa';
import { MANAGE_TRAIN_SCHEDULE_TYPES } from 'applications/operationalStudies/consts';
import { useTranslation } from 'react-i18next';

type Props = {
  displayTrainScheduleManagement: string;
  setDisplayTrainScheduleManagement: (type: string) => void;
};

export default function TimetableManageTrainSchedule({
  displayTrainScheduleManagement,
  setDisplayTrainScheduleManagement,
}: Props) {
  const { t } = useTranslation('operationalStudies/manageTrainSchedule');

  const textContent = () => {
    if (displayTrainScheduleManagement === MANAGE_TRAIN_SCHEDULE_TYPES.add) {
      return (
        <>
          <span className="text-primary">
            <FaPlus />
          </span>
          <span className="text-center">{t('addTrainSchedule')}</span>
        </>
      );
    }
    if (displayTrainScheduleManagement === MANAGE_TRAIN_SCHEDULE_TYPES.edit) {
      return (
        <>
          <span className="text-orange">
            <FaPen />
          </span>
          <span className="text-center flex-grow-1">{t('updateTrainSchedule')}</span>
        </>
      );
    }
    return null;
  };

  return (
    <div
      className="scenario-timetable-managetrainschedule"
      role="button"
      tabIndex={0}
      onClick={() => setDisplayTrainScheduleManagement(MANAGE_TRAIN_SCHEDULE_TYPES.none)}
    >
      <div className="scenario-timetable-managetrainschedule-header">
        <div className="d-flex gap-1 align-items-center justify-content-center mb-2 p-4 bg-white rounded h-100">
          {textContent()}
        </div>
        <button
          className="btn btn-secondary btn-block"
          type="button"
          onClick={() => setDisplayTrainScheduleManagement(MANAGE_TRAIN_SCHEDULE_TYPES.none)}
        >
          <i className="icons-arrow-prev mr-2" />
          {t('returnToSimulationResults')}
        </button>
      </div>
      <div className="scenario-timetable-managetrainschedule-body" />
    </div>
  );
}
