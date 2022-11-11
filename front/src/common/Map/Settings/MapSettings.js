import React from 'react';
import PropTypes from 'prop-types';
import MapSettingsLayers from 'common/Map/Settings/MapSettingsLayers';
import MapSettingsMapStyle from 'common/Map/Settings/MapSettingsMapStyle';
import MapSettingsShowOrthoPhoto from 'common/Map/Settings/MapSettingsShowOrthoPhoto';
import MapSettingsShowOSM from 'common/Map/Settings/MapSettingsShowOSM';
import MapSettingsShowOSMtracksections from 'common/Map/Settings/MapSettingsShowOSMtracksections';
import MapSettingsSignals from 'common/Map/Settings/MapSettingsSignals';
import MapSettingsSpeedLimits from 'common/Map/Settings/MapSettingsSpeedLimits';
import MapSettingsTrackSources from 'common/Map/Settings/MapSettingsTrackSources';
import { useTranslation } from 'react-i18next';

export default function MapSettings(props) {
  const { active, toggleMapSettings } = props;
  const { t } = useTranslation(['translation', 'map-settings']);
  if (active) {
    return (
      <div className={`map-modal${active ? ' active' : ''}`}>
        <div className="h2">{t('map-settings:mapSettings')}</div>
        <MapSettingsMapStyle />
        <div className="my-2" />
        <MapSettingsTrackSources />
        <div className="my-2" />
        <div className="row">
          <div className="col-lg-6">
            <MapSettingsShowOSM />
            <div className="my-1" />
            <MapSettingsShowOSMtracksections />
          </div>
          <div className="col-lg-6">
            <MapSettingsShowOrthoPhoto />
          </div>
        </div>
        <div className="mb-1 mt-3 border-bottom">{t('map-settings:signalisation')}</div>
        <MapSettingsSignals />
        <div className="mb-1 mt-3 border-bottom">{t('map-settings:layers')}</div>
        <MapSettingsLayers />
        <div className="mb-1 mt-3 border-bottom">{t('map-settings:speedlimits')}</div>
        <MapSettingsSpeedLimits />
        <div className="mt-2 d-flex flex-row-reverse w-100">
          <button className="btn btn-secondary btn-sm" type="button" onClick={toggleMapSettings}>
            {t('translation:common.close')}
          </button>
        </div>
      </div>
    );
  }
  return null;
}

MapSettings.propTypes = {
  active: PropTypes.bool,
  toggleMapSettings: PropTypes.func.isRequired,
};

MapSettings.defaultProps = {
  active: false,
};
