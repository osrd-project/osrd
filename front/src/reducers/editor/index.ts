import produce from 'immer';
import { Feature } from 'geojson';
import { omit, clone, isNil, isUndefined } from 'lodash';
import { JSONSchema7, JSONSchema7Definition } from 'json-schema';
import { Action, AnyAction, Dispatch, Reducer } from '@reduxjs/toolkit';

import { setLoading, setSuccess, setFailure, setSuccessWithoutMessage } from 'reducers/main';
import { updateIssuesSettings } from 'reducers/map';
import { osrdEditoastApi } from 'common/api/osrdEditoastApi';
import { ThunkAction, EditorSchema, EditorEntity } from 'types';
import {
  allInfraErrorTypes,
  infraErrorTypeList,
} from 'applications/editor/components/InfraErrors/types';
import { EditorState, LayerType } from 'applications/editor/tools/types';
import {
  entityToCreateOperation,
  entityToUpdateOperation,
  entityToDeleteOperation,
} from 'applications/editor/data/utils';
import infra_schema from '../osrdconf/infra_schema.json';

//
// Actions
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

const SELECT_LAYERS = 'editor/SELECT_LAYERS';
export interface ActionSelectLayers extends AnyAction {
  type: typeof SELECT_LAYERS;
  layers: Set<LayerType>;
}
export function selectLayers(
  layers: ActionSelectLayers['layers']
): ThunkAction<ActionSelectLayers> {
  return (dispatch) => {
    dispatch({
      type: SELECT_LAYERS,
      layers,
    });
  };
}

//
// Verify if the data model definition is already loaded.
// If not we do it and store it in the state
//
export const LOAD_DATA_MODEL = 'editor/LOAD_DATA_MODEL';
export interface ActionLoadDataModel extends AnyAction {
  type: typeof LOAD_DATA_MODEL;
  schema: EditorSchema;
}

export function loadDataModel(): ThunkAction<ActionLoadDataModel> {
  return async (dispatch: Dispatch, getState) => {
    // check if we need to load the model
    if (!Object.keys(getState().editor.editorSchema).length) {
      dispatch(setLoading());
      try {
        const schemaResponse = infra_schema as JSONSchema7;
        // parse the schema
        const fieldToOmit = ['id', 'geo', 'sch'];
        const schema = Object.keys(schemaResponse.properties || {})
          .filter((e: string) => {
            const property: JSONSchema7Definition | undefined = schemaResponse?.properties?.[e];
            return typeof property !== 'boolean' && property && property.type === 'array';
          })
          .map((e: string) => {
            // we assume here, that the definition of the object is ref and not inline
            const property: JSONSchema7 | undefined = schemaResponse?.properties?.[
              e
            ] as JSONSchema7;
            const items = property.items as JSONSchema7;
            type keys = keyof typeof items.$ref;
            const ref = items?.$ref?.split('/') as keys;
            const refTarget = clone(schemaResponse[ref[1]][ref[2]]) as JSONSchema7;
            refTarget.properties = omit(refTarget.properties, fieldToOmit);
            refTarget.required = (refTarget.required || []).filter(
              (field: string) => !fieldToOmit.includes(field)
            );
            return {
              layer: e,
              objType: ref[2],
              schema: {
                ...refTarget,
                [ref[1]]: schemaResponse[ref[1]],
              },
            } as EditorSchema[0];
          });
        dispatch(setSuccessWithoutMessage());
        dispatch({
          type: LOAD_DATA_MODEL,
          schema,
        });
      } catch (e) {
        console.error(e);
        dispatch(setFailure(e as Error));
      }
    }
  };
}

const UPDATE_TOTALS_ISSUE = 'editor/UPDATE_TOTALS_ISSUE';
export interface ActionUpdateTotalsIssue extends AnyAction {
  type: typeof UPDATE_TOTALS_ISSUE;
  issues: Pick<EditorState['issues'], 'total' | 'filterTotal'>;
}
export function updateTotalsIssue(
  infraID: number | undefined
): ThunkAction<ActionUpdateTotalsIssue> {
  return async (dispatch: Dispatch, getState) => {
    const { editor } = getState();
    dispatch(setLoading());
    try {
      let total = 0;
      let filterTotal = 0;
      if (infraID) {
        // Get total
        const totalResp = dispatch(
          osrdEditoastApi.endpoints.getInfraByIdErrors.initiate({
            id: infraID,
            level: 'all',
            errorType: undefined,
            pageSize: 0,
            page: 1,
          })
        );
        totalResp.unsubscribe();
        const totalResult = await totalResp;
        total = totalResult.data?.count || 0;

        // Get total for the active filters
        const filterResp = dispatch(
          osrdEditoastApi.endpoints.getInfraByIdErrors.initiate({
            id: infraID,
            level: editor.issues.filterLevel,
            errorType: editor.issues.filterType ?? undefined,
            pageSize: 0,
            page: 1,
          })
        );
        filterResp.unsubscribe();
        const filterResult = await filterResp;
        filterTotal = filterResult.data?.count || 0;
      }
      dispatch({
        type: UPDATE_TOTALS_ISSUE,
        issues: { total, filterTotal },
      });
    } catch (e) {
      dispatch(setFailure(e as Error));
      throw e;
    } finally {
      dispatch(setSuccessWithoutMessage());
    }
  };
}

const UPDATE_FILTERS_ISSUE = 'editor/UPDATE_FILTERS_ISSUE';
export interface ActionUpdateFiltersIssue extends AnyAction {
  type: typeof UPDATE_FILTERS_ISSUE;
  issues: Omit<EditorState['issues'], 'total' | 'filterTotal'>;
}
export function updateFiltersIssue(
  infraID: number | undefined,
  filters: Partial<Pick<EditorState['issues'], 'filterLevel' | 'filterType'>>
): ThunkAction<ActionUpdateTotalsIssue> {
  return async (dispatch: Dispatch, getState) => {
    const { editor } = getState() as { editor: EditorState };
    let level = isUndefined(filters.filterLevel) ? editor.issues.filterLevel : filters.filterLevel;
    let type = isUndefined(filters.filterType) ? editor.issues.filterType : filters.filterType;
    // Check compatibility btw level & type
    // if both are provided and there is an incompatibility, we keep the level
    if (!isNil(filters.filterLevel) && !isNil(filters.filterType)) {
      if (type && level !== 'all' && !infraErrorTypeList[level].has(type)) {
        type = null;
      }
    }
    // if only level is provided, we set the type to undefined
    if (!isNil(filters.filterLevel) && isNil(filters.filterType)) {
      type = null;
    }
    // if only type is provided, we check the level compatibility.
    // if it is not, we set it to "all"
    if (!isNil(filters.filterType) && isNil(filters.filterLevel)) {
      if (level !== 'all' && !infraErrorTypeList[level].has(filters.filterType)) {
        level = 'all';
      }
    }

    dispatch({
      type: UPDATE_FILTERS_ISSUE,
      issues: { filterLevel: level, filterType: type },
    });
    dispatch(updateTotalsIssue(infraID));

    // dispatch the list of types matched by the filter to the map
    let derivedTypes = [];
    if (!isNil(type)) derivedTypes = [type];
    else derivedTypes = level === 'all' ? allInfraErrorTypes : [...infraErrorTypeList[level]];
    dispatch(
      updateIssuesSettings({
        types: derivedTypes,
      })
    );
  };
}

//
// Save modifications
//
const SAVE = 'editor/SAVE';
export interface ActionSave extends AnyAction {
  type: typeof SAVE;
  operations: {
    create?: Array<Feature>;
    update?: Array<Feature>;
    delete?: Array<Feature>;
  };
}
export function save(
  infraID: number | undefined,
  operations: {
    create?: Array<EditorEntity>;
    update?: Array<{ source: EditorEntity; target: EditorEntity }>;
    delete?: Array<EditorEntity>;
  }
): ThunkAction<ActionSave> {
  return async (dispatch: Dispatch) => {
    dispatch(setLoading());
    try {
      const payload = [
        ...(operations.create || []).map((e) => entityToCreateOperation(e)),
        ...(operations.update || []).map((e) => entityToUpdateOperation(e.target, e.source)),
        ...(operations.delete || []).map((e) => entityToDeleteOperation(e)),
      ];
      if (isNil(infraID)) throw new Error('No infrastructure');
      const response = await dispatch(
        osrdEditoastApi.endpoints.postInfraById.initiate({
          id: infraID,
          body: payload,
        })
      );
      if ('data' in response) {
        // success message
        dispatch(
          setSuccess({
            title: 'Modifications enregistrées',
            text: `Vos modifications ont été publiées`,
          })
        );
        return response.data;
      }
      throw new Error(JSON.stringify(response.error));
    } catch (e) {
      dispatch(setFailure(e as Error));
      throw e;
    } finally {
      dispatch(updateTotalsIssue(infraID));
    }
  };
}

export type EditorActions =
  | ActionLoadDataModel
  | ActionSave
  | ActionSelectLayers
  | ActionUpdateTotalsIssue
  | ActionUpdateFiltersIssue;

//
// State definition
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
export const initialState: EditorState = {
  // Definition of entities (json schema)
  editorSchema: [],
  // ID of selected layers on which we are working
  editorLayers: new Set(['track_sections', 'errors']),
  // Editor issue management
  issues: {
    total: 0,
    filterTotal: 0,
    filterLevel: 'all',
    filterType: null,
  },
};

//
// State reducer
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~
const reducer = (inputState: EditorState | undefined, action: EditorActions) => {
  const state = inputState || initialState;

  return produce(state, (draft) => {
    switch (action.type) {
      case SELECT_LAYERS:
        draft.editorLayers = action.layers;
        break;
      case LOAD_DATA_MODEL:
        draft.editorSchema = action.schema;
        break;
      case UPDATE_FILTERS_ISSUE:
      case UPDATE_TOTALS_ISSUE:
        draft.issues = {
          ...state.issues,
          ...action.issues,
        };
        break;
      default:
        // Nothing to do here
        break;
    }
  });
};

// TODO: to avoid error "Type 'Action<any>' is not assignable to type 'EditorActions'"
// We need to migrate the editor store with slice
export default reducer as Reducer<EditorState, Action>;
