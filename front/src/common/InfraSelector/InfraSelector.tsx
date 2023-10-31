import React, { useState, useEffect } from 'react';
import { useDispatch } from 'react-redux';
import { useTranslation } from 'react-i18next';
import { GoLock } from 'react-icons/go';

import icon from 'assets/pictures/components/tracks.svg';

import 'common/InfraSelector/InfraSelector.scss';
import { useInfraID } from 'common/osrdContext';
import type { Infra } from 'common/api/osrdEditoastApi';
import { useModal } from 'common/BootstrapSNCF/ModalSNCF';
import { osrdEditoastApi } from 'common/api/osrdEditoastApi';
import InfraSelectorModal from 'common/InfraSelector/InfraSelectorModal';

import { setFailure } from 'reducers/main';

type InfraSelectorProps = {
  isModalOnly?: boolean;
};

const InfraSelector = ({ isModalOnly = false }: InfraSelectorProps) => {
  const dispatch = useDispatch();
  const [selectedInfra, setSelectedInfra] = useState<Infra | undefined>(undefined);
  const infraID = useInfraID();
  const { openModal } = useModal();
  const [getInfraByID] = osrdEditoastApi.endpoints.getInfraById.useLazyQuery({});
  const { t } = useTranslation(['infraManagement']);

  const getInfra = async (id: number) => {
    getInfraByID({ id })
      .unwrap()
      .then((infra) => setSelectedInfra(infra))
      .catch((e) =>
        dispatch(
          setFailure({
            name: t('errorMessages.unableToRetrieveInfra'),
            message: e.message,
          })
        )
      );
  };

  useEffect(() => {
    if (infraID !== undefined) {
      getInfra(infraID);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [infraID]);

  if (isModalOnly) return <InfraSelectorModal />;

  return (
    <div className="osrd-config-item mb-2">
      <div
        className="osrd-config-item-container osrd-config-item-clickable"
        data-testid="infra-selector"
        role="button"
        tabIndex={-1}
        onClick={() => openModal(<InfraSelectorModal />, 'lg')}
      >
        <div className="infraselector-button" data-testid="infraselector-button">
          <img width="32px" className="mr-2" src={icon} alt="infraIcon" />
          {selectedInfra !== undefined ? (
            <>
              <span className="">{selectedInfra.name}</span>
              <span className="ml-1 small align-self-center">({selectedInfra.id})</span>
              {selectedInfra.locked && (
                <span className="infra-lock ml-auto">
                  <GoLock />
                </span>
              )}
            </>
          ) : (
            t('infraManagement:chooseInfrastructure')
          )}
        </div>
      </div>
    </div>
  );
};

export default InfraSelector;
