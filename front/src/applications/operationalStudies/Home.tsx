import React, { useEffect, useMemo, useState } from 'react';
import { useDispatch } from 'react-redux';
import FilterTextField from 'applications/operationalStudies/components/FilterTextField';
import ProjectCard from 'applications/operationalStudies/components/Home/ProjectCard';
import ProjectCardEmpty from 'applications/operationalStudies/components/Home/ProjectCardEmpty';
import { PROJECTS_URI } from 'applications/operationalStudies/components/operationalStudiesConsts';
import { MODES } from 'applications/operationalStudies/consts';
import osrdLogo from 'assets/pictures/osrd.png';
import logo from 'assets/pictures/views/projects.svg';
import NavBarSNCF from 'common/BootstrapSNCF/NavBarSNCF';
import OptionsSNCF from 'common/BootstrapSNCF/OptionsSNCF';
import Loader from 'common/Loader';
import { get } from 'common/requests';
import { useTranslation } from 'react-i18next';
import nextId from 'react-id-generator';
import { updateMode } from 'reducers/osrdconf';
import { PostSearchApiArg, ProjectResult, osrdEditoastApi } from 'common/api/osrdEditoastApi';

type SortOptions =
  | 'NameAsc'
  | 'NameDesc'
  | 'CreationDateAsc'
  | 'CreationDateDesc'
  | 'LastModifiedAsc'
  | 'LastModifiedDesc';

function displayCards(
  projectsList: ProjectResult[],
  setFilterChips: (filterChips: string) => void
) {
  return projectsList ? (
    <div className="projects-list">
      <div className="row">
        <div className="col-lg-3 col-md-4 col-sm-6" key={nextId()}>
          <ProjectCardEmpty />
        </div>
        {projectsList.map((project) => (
          <div className="col-lg-3 col-md-4 col-sm-6" key={nextId()}>
            <ProjectCard project={project} setFilterChips={setFilterChips} />
          </div>
        ))}
      </div>
    </div>
  ) : (
    <div className="mt-5">
      <Loader position="center" />
    </div>
  );
}

export default function Home() {
  const { t } = useTranslation('operationalStudies/home');
  const [sortOption, setSortOption] = useState<SortOptions>('LastModifiedDesc');
  const [projectsList, setProjectsList] = useState<ProjectResult[]>([]);
  const [filter, setFilter] = useState('');
  const [filterChips, setFilterChips] = useState('');
  const dispatch = useDispatch();
  const [postSearch] = osrdEditoastApi.usePostSearchMutation();
  const [getProjects] = osrdEditoastApi.useLazyGetProjectsQuery();

  const sortOptions = [
    {
      label: t('sortOptions.byName'),
      value: 'NameAsc',
    },
    {
      label: t('sortOptions.byRecentDate'),
      value: 'LastModifiedDesc',
    },
  ];

  const getProjectList = async () => {
    if (filter) {
      const payload: PostSearchApiArg = {
        body: {
          object: 'project',
          query: ['and', ['or', ['search', ['name'], filter], ['search', ['description'], filter]]],
        },
      };
      try {
        const data: ProjectResult[] = await postSearch(payload).unwrap();
        setProjectsList(data);
      } catch (error) {
        console.error('filter projetcs error : ', error);
      }
    } else {
      try {
        const projects = await getProjects({ ordering: sortOption });
        if (projects.data?.results) {
          setProjectsList(projects.data.results);
        }
      } catch (error) {
        console.error('get Projetcs error : ', error);
      }
    }
  };

  const handleSortOptions = (e: React.ChangeEvent<HTMLInputElement>) => {
    setSortOption(e.target.value as SortOptions);
  };

  useEffect(() => {
    getProjectList();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [sortOption, filter]);

  useEffect(() => {
    dispatch(updateMode(MODES.simulation));
  }, []);

  return (
    <>
      <NavBarSNCF appName={<div className="navbar-breadcrumbs">{t('projects')}</div>} logo={logo} />
      <main className="mastcontainer mastcontainer-no-mastnav">
        <div className="p-3">
          <div className="application-title d-none">
            <img src={osrdLogo} alt="OSRD logo" />
            <h1>Open Source Railway Designer</h1>
          </div>
          <div className="projects-toolbar">
            <div className="h1 mb-0">
              {t('projectsCount', { count: projectsList ? projectsList.length : 0 })}
            </div>
            <div className="flex-grow-1">
              <FilterTextField
                id="projects-filter"
                setFilter={setFilter}
                filterChips={filterChips}
              />
            </div>
            <OptionsSNCF
              name="projects-sort-filter"
              onChange={handleSortOptions}
              selectedValue={sortOption}
              options={sortOptions}
            />
          </div>
          {useMemo(() => displayCards(projectsList, setFilterChips), [projectsList])}
        </div>
      </main>
    </>
  );
}
