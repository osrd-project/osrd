use crate::error::Result;
use crate::models::Create;
use crate::models::Delete;
use crate::models::Project;
use crate::models::Retrieve;
use crate::models::Study;
use crate::models::StudyWithScenarios;
use crate::views::pagination::{PaginatedResponse, PaginationQueryParam};
use crate::DbPool;
use actix_web::dev::HttpServiceFactory;
use actix_web::web::{self, Data, Json, Path, Query};
use actix_web::{delete, get, post, HttpResponse};
use chrono::NaiveDateTime;
use chrono::Utc;
use derivative::Derivative;
use editoast_derive::EditoastError;
use serde::{Deserialize, Serialize};
use thiserror::Error;

/// Returns `/projects/{project}/studies` routes
pub fn routes() -> impl HttpServiceFactory {
    web::scope("/studies")
        .service((create, list))
        .service(web::scope("/{study}").service((delete, get)))
}

#[derive(Debug, Error, EditoastError)]
#[editoast_error(base_id = "study")]
enum StudyError {
    /// Couldn't found the project with the given project_id
    #[error("Project '{project_id}', could not be found")]
    #[editoast_error(status = 404)]
    ProjectNotFound { project_id: i64 },
    /// Couldn't found the project with the given project_id
    #[error("Study '{study_id}', could not be found")]
    #[editoast_error(status = 404)]
    NotFound { study_id: i64 },
}

/// This structure is used by the post endpoint to create a study
#[derive(Serialize, Deserialize, Derivative)]
#[derivative(Default)]
pub struct StudyCreateForm {
    pub name: String,
    #[serde(default)]
    pub description: String,
    pub start_date: Option<NaiveDateTime>,
    pub expected_end_date: Option<NaiveDateTime>,
    pub actual_end_date: Option<NaiveDateTime>,
    #[serde(default)]
    pub business_code: String,
    #[serde(default)]
    pub service_code: String,
    #[serde(default)]
    pub budget: i32,
    #[serde(default)]
    pub tags: Vec<String>,
    #[serde(default)]
    pub state: String,
    #[serde(default)]
    pub study_type: String,
}

impl StudyCreateForm {
    pub fn into_study(self, project_id: i64) -> Study {
        Study {
            name: Some(self.name),
            project_id: Some(project_id),
            description: Some(self.description),
            budget: Some(self.budget),
            tags: Some(self.tags),
            creation_date: Some(Utc::now().naive_utc()),
            business_code: Some(self.business_code),
            service_code: Some(self.service_code),
            state: Some(self.state),
            study_type: Some(self.study_type),
            start_date: Some(self.start_date),
            expected_end_date: Some(self.expected_end_date),
            actual_end_date: Some(self.actual_end_date),
            ..Default::default()
        }
    }
}

#[post("")]
async fn create(
    db_pool: Data<DbPool>,
    data: Json<StudyCreateForm>,
    project: Path<i64>,
) -> Result<Json<StudyWithScenarios>> {
    let project_id = project.into_inner();
    // Check if project exists
    let project = match Project::retrieve(db_pool.clone(), project_id).await? {
        None => return Err(StudyError::ProjectNotFound { project_id }.into()),
        Some(project) => project,
    };

    // Create study
    let study: Study = data.into_inner().into_study(project_id);
    let study = study.create(db_pool.clone()).await?;

    // Update project last_modification field
    let project = project.update_last_modified(db_pool).await?;
    project.expect("Project should exist");

    // Return study with list of scenarios
    let study_with_scenarios = StudyWithScenarios {
        study,
        scenarios: vec![],
    };

    Ok(Json(study_with_scenarios))
}

/// Delete a study
#[delete("")]
async fn delete(path: Path<(i64, i64)>, db_pool: Data<DbPool>) -> Result<HttpResponse> {
    let (project_id, study_id) = path.into_inner();
    // Check if project exists
    let project = match Project::retrieve(db_pool.clone(), project_id).await? {
        None => return Err(StudyError::ProjectNotFound { project_id }.into()),
        Some(project) => project,
    };

    // Delete study
    if !Study::delete(db_pool.clone(), study_id).await? {
        return Err(StudyError::NotFound { study_id }.into());
    }

    // Update project last_modification field
    let project = project.update_last_modified(db_pool).await?;
    project.expect("Project should exist");

    Ok(HttpResponse::NoContent().finish())
}

/// Return a list of studies
#[get("")]
async fn list(
    db_pool: Data<DbPool>,
    pagination_params: Query<PaginationQueryParam>,
    project: Path<i64>,
) -> Result<Json<PaginatedResponse<StudyWithScenarios>>> {
    let project = project.into_inner();
    let page = pagination_params.page;
    let per_page = pagination_params.page_size.unwrap_or(25).max(10);
    let studies = Study::list(db_pool, page, per_page, project).await?;

    Ok(Json(studies))
}

/// Return a specific studies
#[get("")]
async fn get(db_pool: Data<DbPool>, path: Path<(i64, i64)>) -> Result<Json<StudyWithScenarios>> {
    let (project, study) = path.into_inner();
    let study = Study::retrieve(db_pool.clone(), project, study).await?;
    Ok(Json(study))
}

#[cfg(test)]
mod test {
    use crate::models::Project;
    use crate::models::Study;
    use crate::views::projects::test::{create_project_request, delete_project_request};
    use crate::views::tests::create_test_service;
    use actix_http::Request;
    use actix_web::http::StatusCode;
    use actix_web::test as actix_test;
    use actix_web::test::call_and_read_body_json;
    use actix_web::test::{call_service, read_body_json, TestRequest};
    use serde_json::json;

    pub async fn create_study_request() -> Request {
        let app = create_test_service().await;
        let response = call_service(&app, create_project_request()).await;
        let project: Project = read_body_json(response).await;
        let project_id = project.id.unwrap();
        TestRequest::post()
            .uri(format!("/projects/{project_id}/studies").as_str())
            .set_json(json!({ "name": "study_test" }))
            .to_request()
    }

    pub fn delete_study_request(project_id: i64, study_id: i64) -> Request {
        TestRequest::delete()
            .uri(format!("/projects/{project_id}/studies/{study_id}").as_str())
            .to_request()
    }

    #[actix_test]
    async fn study_create_delete() {
        let app = create_test_service().await;
        let response = call_service(&app, create_study_request().await).await;
        assert_eq!(response.status(), StatusCode::OK);
        let study: Study = read_body_json(response).await;
        assert_eq!(study.name.unwrap(), "study_test");
        let response = call_service(
            &app,
            delete_study_request(study.project_id.unwrap(), study.id.unwrap()),
        )
        .await;
        assert_eq!(response.status(), StatusCode::NO_CONTENT);
        let response = call_service(&app, delete_project_request(study.project_id.unwrap())).await;
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }

    #[actix_test]
    async fn study_list() {
        let app = create_test_service().await;
        let response = call_service(&app, create_study_request().await).await;
        assert_eq!(response.status(), StatusCode::OK);
        let study: Study = read_body_json(response).await;
        let project_id = study.project_id.unwrap();
        let req = TestRequest::get()
            .uri(format!("/projects/{project_id}/studies").as_str())
            .to_request();
        let response = call_service(&app, req).await;
        assert_eq!(response.status(), StatusCode::OK);
    }

    #[actix_test]
    async fn project_get() {
        let app = create_test_service().await;
        let study: Study = call_and_read_body_json(&app, create_study_request().await).await;

        let req = TestRequest::get()
            .uri(
                format!(
                    "/projects/{}/studies/{}/",
                    study.project_id.unwrap(),
                    study.id.unwrap()
                )
                .as_str(),
            )
            .to_request();
        let response = call_service(&app, req).await;
        assert_eq!(response.status(), StatusCode::OK);

        let req = TestRequest::delete()
            .uri(
                format!(
                    "/projects/{}/studies/{}/",
                    study.project_id.unwrap(),
                    study.id.unwrap()
                )
                .as_str(),
            )
            .to_request();
        let response = call_service(&app, req).await;
        assert_eq!(response.status(), StatusCode::NO_CONTENT);

        let response = call_service(
            &app,
            delete_study_request(study.project_id.unwrap(), study.id.unwrap()),
        )
        .await;
        assert_eq!(response.status(), StatusCode::NOT_FOUND);
    }
}
