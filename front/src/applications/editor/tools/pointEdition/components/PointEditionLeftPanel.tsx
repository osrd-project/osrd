import React, { ComponentType, useContext, useEffect, useRef, useState } from 'react';
import { useDispatch } from 'react-redux';
import { useTranslation } from 'react-i18next';
import length from '@turf/length';
import along from '@turf/along';

import { useInfraID } from 'common/osrdContext';
import { save } from 'reducers/editor';
import {
  BufferStopEntity,
  DetectorEntity,
  EditorEntity,
  SignalEntity,
  TrackSectionEntity,
} from 'types';

import EditorContext from '../../../context';
import EntityError from '../../../components/EntityError';
import EditorForm from '../../../components/EditorForm';
import { getEntity } from '../../../data/api';
import { NEW_ENTITY_ID } from '../../../data/utils';
import { ExtendedEditorContextType } from '../../editorContextTypes';
import { EditoastType } from '../../types';
import { formatSignalingSystems } from '../utils';
import { PointEditionState } from '../types';
import CustomFlagSignalCheckbox from './CustomFlagSignalCheckbox';
import CustomPosition from './CustomPosition';
import RoutesList from './RoutesList';

type EditorPoint = BufferStopEntity | DetectorEntity | SignalEntity;

interface PointEditionLeftPanelProps {
  type: EditoastType;
}

/**
 * Generic component for point edition left panel:
 */
const PointEditionLeftPanel = <Entity extends EditorEntity>({
  type,
}: PointEditionLeftPanelProps) => {
  const dispatch = useDispatch();
  const { t } = useTranslation();
  const infraID = useInfraID();
  const { state, setState, isFormSubmited, setIsFormSubmited } = useContext(
    EditorContext
  ) as ExtendedEditorContextType<PointEditionState<Entity>>;
  const submitBtnRef = useRef<HTMLButtonElement>(null);

  const isWayPoint = type === 'BufferStop' || type === 'Detector';
  const isNew = state.entity.properties.id === NEW_ENTITY_ID;

  const [trackState, setTrackState] = useState<
    | { type: 'idle'; id?: undefined; track?: undefined }
    | { type: 'isLoading'; id: string; track?: undefined }
    | { type: 'ready'; id: string; track: TrackSectionEntity }
  >({ type: 'idle' });

  // Hack to be able to launch the submit event from the rjsf form by using
  // the toolbar button instead of the form one.
  // See https://github.com/rjsf-team/react-jsonschema-form/issues/500
  useEffect(() => {
    if (isFormSubmited && setIsFormSubmited && submitBtnRef.current) {
      submitBtnRef.current.click();
      setIsFormSubmited(false);
    }
  }, [isFormSubmited]);

  useEffect(() => {
    const firstLoading = trackState.type === 'idle';
    const trackId = state.entity.properties.track as string | undefined;

    if (trackId && trackState.id !== trackId) {
      setTrackState({ type: 'isLoading', id: trackId });
      getEntity<TrackSectionEntity>(infraID as number, trackId, 'TrackSection', dispatch).then(
        (track) => {
          setTrackState({ type: 'ready', id: trackId, track });

          if (!firstLoading) {
            const { position } = state.entity.properties;
            const turfPosition =
              (position * length(track, { units: 'meters' })) / track.properties.length;
            const point = along(track, turfPosition, { units: 'meters' });

            setState({ ...state, entity: { ...state.entity, geometry: point.geometry } });
          }
        }
      );
    }
  }, [infraID, setState, state, state.entity.properties.track, trackState.id, trackState.type]);

  return (
    <>
      {isWayPoint && !isNew && (
        <>
          <h3>{t('Editor.tools.point-edition.linked-routes')}</h3>
          <RoutesList type={type} id={state.entity.properties.id} />
          <div className="border-bottom" />
        </>
      )}
      <EditorForm
        data={state.entity as Entity}
        overrideUiSchema={{
          logical_signals: {
            items: {
              signaling_system: {
                'ui:widget': 'hidden',
              },
              settings: {
                'ui:description': ' ',
                Nf: {
                  'ui:description': ' ',
                  'ui:widget': CustomFlagSignalCheckbox,
                },
                distant: {
                  'ui:description': ' ',
                  'ui:widget': CustomFlagSignalCheckbox,
                },
                is_430: {
                  'ui:description': ' ',
                  'ui:widget': CustomFlagSignalCheckbox,
                },
              },
            },
          },
          position: {
            'ui:widget': CustomPosition,
          },
        }}
        onSubmit={async (savedEntity) => {
          // eslint-disable-next-line @typescript-eslint/no-explicit-any
          const res: any = await dispatch(
            save(
              infraID,
              state.entity.properties.id !== NEW_ENTITY_ID
                ? {
                    update: [
                      {
                        source: state.initialEntity,
                        target: savedEntity,
                      },
                    ],
                  }
                : { create: [savedEntity] }
            )
          );
          const { railjson } = res[0];
          const { id } = railjson;
          if (id && id !== savedEntity.properties.id) {
            const saveEntity = {
              ...state.entity,
              id,
              properties: {
                ...state.entity.properties,
                ...railjson,
              },
            };
            setState({
              ...state,
              initialEntity: saveEntity,
              entity: saveEntity,
            });
          }
        }}
        onChange={(entity: Entity | EditorPoint) => {
          const additionalUpdate: Partial<EditorPoint> = {};
          const additionalPropertiesUpdate: Partial<SignalEntity['properties']> = {};
          const newPosition = entity.properties?.position;
          const oldPosition = state.entity.properties?.position;
          const trackId = entity.properties?.track;
          if (
            typeof trackId === 'string' &&
            trackId === trackState.id &&
            trackState.type === 'ready' &&
            typeof newPosition === 'number' &&
            typeof oldPosition === 'number' &&
            newPosition !== oldPosition
          ) {
            const turfPosition =
              (newPosition * length(trackState.track, { units: 'meters' })) /
              trackState.track.properties.length;
            const point = along(trackState.track, turfPosition, { units: 'meters' });
            additionalUpdate.geometry = point.geometry;
          }
          if (entity.objType === 'Signal' && entity.properties.logical_signals) {
            additionalPropertiesUpdate.logical_signals = formatSignalingSystems(
              entity as SignalEntity
            );
          }
          setState({
            ...state,
            entity: {
              ...(entity as Entity),
              ...additionalUpdate,
              properties: { ...(entity as Entity).properties, ...additionalPropertiesUpdate },
            },
          });
        }}
      >
        <div>
          {/* We don't want to see the button but just be able to click on it */}
          <button type="submit" ref={submitBtnRef} style={{ display: 'none' }}>
            {t('common.save')}
          </button>
        </div>
      </EditorForm>

      {!isNew && <EntityError className="mt-1" entity={state.entity} />}
    </>
  );
};

const getPointEditionLeftPanel =
  (type: EditoastType): ComponentType =>
  () =>
    <PointEditionLeftPanel type={type} />;

export default getPointEditionLeftPanel;