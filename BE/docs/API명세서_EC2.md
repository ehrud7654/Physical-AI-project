# EC2 구현 API

## 인증

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/auth/login` | 이메일·비밀번호를 검증하고 로그인 세션과 액세스 토큰을 발급합니다. | [LoginRequest](#ec2--loginrequest) | [LoginResponse](#ec2--loginresponse) | 200 |
| `POST /api/v1/auth/signup` | 회원 계정을 생성합니다. | [SignupRequest](#ec2--signuprequest) | [AuthenticatedUserResponse](#ec2--authenticateduserresponse) | 201 |
| `GET /api/v1/auth/email-availability` | 이메일 중복 여부를 조회합니다. | 없음 | [EmailAvailabilityResponse](#ec2--emailavailabilityresponse) | 200 |
| `POST /api/v1/auth/logout` | 현재 로그인 세션과 Refresh 쿠키를 폐기합니다. | 없음 | 없음 | 204 |
| `POST /api/v1/auth/refresh` | Refresh 쿠키를 검증하고 Access Token과 회전된 Refresh 쿠키를 발급합니다. | 없음 | [LoginResponse](#ec2--loginresponse) | 200 |
| `POST /api/v1/auth/session/activity` | 사용자 활동에 따라 미사용 만료 시각을 갱신합니다. | 없음 | [AuthController.ActivityResponse](#ec2--activityresponse) | 200 |

## 장치

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/devices` | 수집 장치 또는 Jetson 장치를 등록합니다. | [DeviceCreateRequest](#ec2--devicecreaterequest) | [DeviceResponse](#ec2--deviceresponse) | 201 |
| `GET /api/v1/devices` | 소유 장치를 조회합니다. | 없음 | [DeviceResponse](#ec2--deviceresponse) 배열 | 200 |
| `GET /api/v1/devices/{deviceId}` | 장치 상세를 조회합니다. | 없음 | [DeviceResponse](#ec2--deviceresponse) | 200 |
| `PATCH /api/v1/devices/{deviceId}` | 장치 이름·위치를 수정합니다. | [DeviceUpdateRequest](#ec2--deviceupdaterequest) | [DeviceResponse](#ec2--deviceresponse) | 200 |
| `POST /api/v1/devices/{deviceId}/enrollment` | 장치 등록용 QR 정보를 발급합니다. | 없음 | [DeviceEnrollmentStartResponse](#ec2--deviceenrollmentstartresponse) | 201 |

## 장치 인증

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/device-auth/enroll` | 등록 토큰을 확인하고 장치 secret을 발급합니다. | [DeviceEnrollmentCompleteRequest](#ec2--deviceenrollmentcompleterequest) | [DeviceEnrollmentCompleteResponse](#ec2--deviceenrollmentcompleteresponse) | 200 |
| `POST /api/v1/device-auth/token` | 장치 자격 증명으로 액세스 토큰을 발급합니다. | [DeviceTokenRequest](#ec2--devicetokenrequest) | [DeviceTokenResponse](#ec2--devicetokenresponse) | 200 |
| `POST /api/v1/device-auth/skills` | 장치 자격 증명으로 사용 가능한 스킬을 조회합니다. | [DeviceTokenRequest](#ec2--devicetokenrequest) | [SkillResponses.SkillResponse](#ec2--skillresponses-skillresponse) 배열 | 200 |

## 로봇

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/robots` | 로봇과 연결 장치를 등록합니다. | [RobotCreateRequest](#ec2--robotcreaterequest) | [RobotResponse](#ec2--robotresponse) | 201 |
| `GET /api/v1/robots` | 소유 로봇 목록을 조회합니다. | 없음 | [RobotResponse](#ec2--robotresponse) 배열 | 200 |
| `GET /api/v1/robots/{robotId}` | 로봇 상세와 현재 상태를 조회합니다. | 없음 | [RobotResponse](#ec2--robotresponse) | 200 |

## 대시보드

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `GET /api/v1/dashboard` | 장치·학습·실행 집계와 최근 학습을 조회합니다. | 없음 | [DashboardResponse](#ec2--dashboardresponse) | 200 |

## 스킬

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `GET /api/v1/skills` | 사용 가능한 스킬 목록을 조회합니다. | 없음 | [SkillResponses.SkillResponse](#ec2--skillresponses-skillresponse) 배열 | 200 |
| `GET /api/v1/skills/overview` | 스킬별 최신 모델·학습 상태를 조회합니다. | 없음 | [SkillResponses.SkillOverviewResponse](#ec2--skillresponses-skilloverviewresponse) 배열 | 200 |
| `POST /api/v1/skills` | 사용자 스킬을 생성합니다. | [SkillApiModels.CreateCustomSkillRequest](#ec2--skillapimodels-createcustomskillrequest) | [SkillResponses.SkillResponse](#ec2--skillresponses-skillresponse) | 201 |
| `PATCH /api/v1/skills/{skillId}` | 스킬 설명을 수정합니다. | [SkillApiModels.UpdateSkillDescriptionRequest](#ec2--skillapimodels-updateskilldescriptionrequest) | [SkillResponses.SkillResponse](#ec2--skillresponses-skillresponse) | 200 |
| `GET /api/v1/skills/{skillId}/versions/latest` | 게이트를 통과한 최신 READY 모델 버전을 조회합니다. | 없음 | [SkillResponses.SkillVersionResponse](#ec2--skillresponses-skillversionresponse) | 200 |
| `GET /api/v1/skills/{skillId}/training-requirements` | 스킬의 학습 입력 요구사항을 조회합니다. | 없음 | [SkillResponses.TrainingRequirementsResponse](#ec2--skillresponses-trainingrequirementsresponse) | 200 |

## 업로드

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/episode/data-upload-request` | Episode ZIP 업로드용 S3 Presigned URL을 발급합니다. | [EpisodeUploadSessionRequest](#ec2--episodeuploadsessionrequest) | [EpisodeUploadSessionResponse](#ec2--episodeuploadsessionresponse) | 201 |
| `POST /api/v1/episode/data-upload-request/{uploadSessionId}/complete` | S3 업로드 완료를 접수하고 비동기 검증을 시작합니다. | [EpisodeUploadCompleteRequest](#ec2--episodeuploadcompleterequest) | [EpisodeUploadCompleteResponse](#ec2--episodeuploadcompleteresponse) | 202 / 중복 200 |
| `POST /api/v1/episode/data-upload-request/{uploadSessionId}/renew` | 업로드 URL을 재발급합니다. | 없음 | [EpisodeUploadSessionResponse](#ec2--episodeuploadsessionresponse) | 200 |
| `DELETE /api/v1/episode/data-upload-request/{uploadSessionId}` | 업로드 세션을 취소합니다. | 없음 | 없음 | 204 |
| `GET /api/v1/episode/data-upload-request/by-session/{sessionId}` | 원본 sessionId로 등록·검증 상태를 조회합니다. | 없음 | [EpisodeProcessingStatusResponse](#ec2--episodeprocessingstatusresponse) | 200 |

## Episode

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `GET /api/v1/episodes` | 소유 Episode를 조건별로 조회합니다. | 없음 | [EpisodeApiModels.EpisodeListResponse](#ec2--episodeapimodels-episodelistresponse) 배열 | 200 |
| `GET /api/v1/episodes/{episodeId}` | Episode 메타데이터·검증·보관 상세를 조회합니다. | 없음 | [EpisodeApiModels.EpisodeResponse](#ec2--episodeapimodels-episoderesponse) | 200 |
| `GET /api/v1/episodes/retention-policy` | 기본 보관 정책을 조회합니다. | 없음 | [EpisodeApiModels.RetentionPolicyResponse](#ec2--episodeapimodels-retentionpolicyresponse) | 200 |

## 학습

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/training-jobs` | 선택한 Episode로 학습 작업을 생성합니다. | [TrainingJobApiModels.CreateRequest](#ec2--trainingjobapimodels-createrequest) | [TrainingJobApiModels.JobResponse](#ec2--trainingjobapimodels-jobresponse) | 201 |
| `GET /api/v1/training-jobs` | 학습 작업 목록을 조회합니다. | 없음 | [TrainingJobApiModels.JobResponse](#ec2--trainingjobapimodels-jobresponse) 배열 | 200 |
| `GET /api/v1/training-jobs/{id}` | 학습 진행 상태와 결과를 조회합니다. | 없음 | [TrainingJobApiModels.JobResponse](#ec2--trainingjobapimodels-jobresponse) | 200 |
| `POST /api/v1/training-jobs/{id}/cancel` | 학습 취소를 요청합니다. | 없음 | [TrainingJobApiModels.JobResponse](#ec2--trainingjobapimodels-jobresponse) | 200 |
| `GET /api/v1/training-jobs/{id}/episodes` | 학습에 연결된 Episode와 입력 순서를 조회합니다. | 없음 | [TrainingJobApiModels.EpisodeInputResponse](#ec2--trainingjobapimodels-episodeinputresponse) 배열 | 200 |
| `DELETE /api/v1/training-jobs/{id}` | 종료된 학습 작업을 소프트 삭제합니다. | 없음 | 없음 | 204 |

## 작업

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/tasks` | 작업과 선택적인 단계 구성을 생성합니다. | [TaskApiModels.CreateRequest](#ec2--taskapimodels-createrequest) | [TaskApiModels.TaskResponse](#ec2--taskapimodels-taskresponse) | 201 |
| `GET /api/v1/tasks` | 작업 목록을 검색합니다. | 없음 | [TaskApiModels.TaskResponse](#ec2--taskapimodels-taskresponse) 배열 | 200 |
| `GET /api/v1/tasks/{taskId}` | 작업 상세를 조회합니다. | 없음 | [TaskApiModels.TaskResponse](#ec2--taskapimodels-taskresponse) | 200 |
| `PATCH /api/v1/tasks/{taskId}` | 작업 이름·설명·단계를 수정합니다. | [TaskApiModels.UpdateRequest](#ec2--taskapimodels-updaterequest) | [TaskApiModels.TaskResponse](#ec2--taskapimodels-taskresponse) | 200 |
| `POST /api/v1/tasks/{taskId}/versions` | 단계 구성으로 새 작업 버전을 생성합니다. | [TaskApiModels.VersionCreateRequest](#ec2--taskapimodels-versioncreaterequest) | [TaskApiModels.VersionResponse](#ec2--taskapimodels-versionresponse) | 201 |
| `GET /api/v1/tasks/{taskId}/versions` | 작업 버전 목록을 조회합니다. | 없음 | [TaskApiModels.VersionResponse](#ec2--taskapimodels-versionresponse) 배열 | 200 |
| `GET /api/v1/tasks/{taskId}/versions/{versionId}` | 특정 작업 버전을 조회합니다. | 없음 | [TaskApiModels.VersionResponse](#ec2--taskapimodels-versionresponse) | 200 |
| `POST /api/v1/tasks/{taskId}/versions/{versionId}/validate` | 해당 로봇에서 작업을 실행할 준비가 됐는지 검증합니다. | [TaskApiModels.ValidateRequest](#ec2--taskapimodels-validaterequest) | [TaskApiModels.ValidationResponse](#ec2--taskapimodels-validationresponse) | 200 |
| `DELETE /api/v1/tasks/{taskId}` | 작업을 삭제합니다. | 없음 | 없음 | 204 |

## 배포

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/robots/{robotId}/deployments` | 배포를 요청하고 로봇에 명령을 전달합니다. | [DeploymentApiModels.CreateRequest](#ec2--deploymentapimodels-createrequest) | [DeploymentApiModels.DeploymentResponse](#ec2--deploymentapimodels-deploymentresponse) | 201 |
| `GET /api/v1/robots/{robotId}/deployments` | 배포 이력을 조회합니다. | 없음 | [DeploymentApiModels.DeploymentResponse](#ec2--deploymentapimodels-deploymentresponse) 배열 | 200 |
| `GET /api/v1/robots/{robotId}/deployments/{deploymentId}` | 배포 상세 상태를 조회합니다. | 없음 | [DeploymentApiModels.DeploymentResponse](#ec2--deploymentapimodels-deploymentresponse) | 200 |
| `POST /api/v1/robots/{robotId}/deployments/{deploymentId}/cancel` | 배포 취소를 요청합니다. | 없음 | [DeploymentApiModels.DeploymentResponse](#ec2--deploymentapimodels-deploymentresponse) | 200 |

## 실행

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/robots/{robotId}/executions` | 실행을 요청하고 로봇에 명령을 전달합니다. | [ExecutionApiModels.CreateRequest](#ec2--executionapimodels-createrequest) | [ExecutionApiModels.ExecutionResponse](#ec2--executionapimodels-executionresponse) | 202 |
| `GET /api/v1/robots/{robotId}/executions` | 실행 이력을 조회합니다. | 없음 | [ExecutionApiModels.ExecutionResponse](#ec2--executionapimodels-executionresponse) 배열 | 200 |
| `GET /api/v1/robots/{robotId}/executions/{executionId}` | 실행 상세 상태를 조회합니다. | 없음 | [ExecutionApiModels.ExecutionResponse](#ec2--executionapimodels-executionresponse) | 200 |
| `POST /api/v1/robots/{robotId}/executions/{executionId}/cancel` | 실행 취소를 요청합니다. | 없음 | [ExecutionApiModels.ExecutionResponse](#ec2--executionapimodels-executionresponse) | 202 |

## 장치 보고

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /api/v1/devices/{deviceId}/heartbeat` | 장치 생존 상태와 런타임 버전을 보고합니다. | [JetsonHeartbeatRequest](#ec2--jetsonheartbeatrequest) | [JetsonHeartbeatResponse](#ec2--jetsonheartbeatresponse) | 200 |

## Jetson

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `GET /api/v1/robots/me/command-stream` | 로봇 명령을 SSE로 수신하고 재연결 시 재생합니다. | 없음 | [JetsonCommand](#ec2--jetsoncommand) | 200 |
| `GET /api/v1/robots/me/deployments/pending` | 대기 중 배포의 모델 다운로드 manifest를 조회합니다. | 없음 | [JetsonDeploymentModels.Manifest](#ec2--jetsondeploymentmodels-manifest) | 200 / 없음 204 |
| `POST /api/v1/robots/me/deployments/{deploymentId}/manifest` | 배포 manifest와 다운로드 URL을 갱신합니다. | 없음 | [JetsonDeploymentModels.Manifest](#ec2--jetsondeploymentmodels-manifest) | 200 |
| `PATCH /api/v1/robots/me/deployments/{deploymentId}` | 모델 다운로드·검증·적용 상태를 보고합니다. | [JetsonDeploymentModels.ReportRequest](#ec2--jetsondeploymentmodels-reportrequest) | [DeploymentApiModels.DeploymentResponse](#ec2--deploymentapimodels-deploymentresponse) | 200 |
| `PATCH /api/v1/robots/me/executions/{executionId}` | 로봇 실행 진행·완료·실패를 보고합니다. | [JetsonExecutionReportRequest](#ec2--jetsonexecutionreportrequest) | [ExecutionApiModels.ExecutionResponse](#ec2--executionapimodels-executionresponse) | 200 |
| `GET /api/v1/robots/me/deployment/current` | 현재 적용된 작업·스킬 배포를 조회합니다. | 없음 | [JetsonStateModels.CurrentDeployment](#ec2--jetsonstatemodels-currentdeployment) | 200 |
| `GET /api/v1/robots/me/skills/active` | 활성 스킬 버전을 조회합니다. | 없음 | [JetsonStateModels.ActiveSkill](#ec2--jetsonstatemodels-activeskill) 배열 | 200 |

## 내부 학습

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /internal/training-jobs/claim` | GPU가 작업을 할당받고 다운로드 URL·lease를 받습니다. | [GpuTrainingApiModels.ClaimRequest](#ec2--gputrainingapimodels-claimrequest) | [GpuTrainingApiModels.ClaimResponse](#ec2--gputrainingapimodels-claimresponse) | 200 / 작업 없음 204 |
| `POST /internal/training-jobs/{id}/heartbeat` | GPU가 작업 lease를 갱신하고 취소 여부를 확인합니다. | [GpuTrainingApiModels.HeartbeatRequest](#ec2--gputrainingapimodels-heartbeatrequest) | [GpuTrainingApiModels.HeartbeatResponse](#ec2--gputrainingapimodels-heartbeatresponse) | 200 |
| `POST /internal/training-jobs/{id}/events` | GPU가 학습 상태 이벤트를 보고합니다. | [GpuTrainingApiModels.EventRequest](#ec2--gputrainingapimodels-eventrequest) | [GpuTrainingApiModels.EventResponse](#ec2--gputrainingapimodels-eventresponse) | 200 |

## 체크포인트

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /internal/training-jobs/{trainingJobId}/checkpoint-upload-session` | 체크포인트 업로드 URL과 모델 버전을 발급합니다. | [CheckpointApiModels.UploadSessionRequest](#ec2--checkpointapimodels-uploadsessionrequest) | [CheckpointApiModels.UploadSessionResponse](#ec2--checkpointapimodels-uploadsessionresponse) | 201 |
| `POST /internal/training-jobs/{trainingJobId}/checkpoint-complete` | 체크포인트 완료를 접수하고 모델 검증을 시작합니다. | [CheckpointApiModels.CompleteRequest](#ec2--checkpointapimodels-completerequest) | [CheckpointApiModels.CompleteResponse](#ec2--checkpointapimodels-completeresponse) | 202 |

## 게이트

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /internal/skill-versions/{id}/gate-result` | 모델 성능 평가 결과를 저장하고 게이트 통과 여부를 갱신합니다. | [GateApiModels.GateResultRequest](#ec2--gateapimodels-gateresultrequest) | [GateApiModels.GateResultResponse](#ec2--gateapimodels-gateresultresponse) | 200 |

## 저장 노드

| API | 작업 | 요청 body | 응답 data | HTTP |
|---|---|---|---|---|
| `POST /internal/storage-nodes/register` | GPU 저장 노드를 등록하거나 갱신합니다. | [StorageNodeApiModels.RegisterRequest](#ec2--storagenodeapimodels-registerrequest) | [StorageNodeApiModels.NodeResponse](#ec2--storagenodeapimodels-noderesponse) | 201 / 기존 200 |
| `POST /internal/storage-nodes/{id}/heartbeat` | 저장 노드의 용량·생존·작업 수를 갱신합니다. | [StorageNodeApiModels.HeartbeatRequest](#ec2--storagenodeapimodels-heartbeatrequest) | [StorageNodeApiModels.HeartbeatResponse](#ec2--storagenodeapimodels-heartbeatresponse) | 200 |

## 헤더·쿼리·분기

- **`POST /api/v1/auth/login`** — 공개 / Origin이 있으면 CORS 허용 목록과 대조 / Set-Cookie 발급 / Cache-Control: no-store
- **`POST /api/v1/auth/signup`** — 공개
- **`GET /api/v1/auth/email-availability`** — 공개 / Query: email=<email> (필수)
- **`POST /api/v1/auth/logout`** — Refresh 쿠키 / X-Session-Request: 1 필수 / Origin 검사 / 쿠키 삭제 / Cache-Control: no-store. 유효 쿠키 없이도 멱등적으로 204. 기존 사용자 Bearer 토큰 방식은 쿠키가 없을 때만 지원.
- **`POST /api/v1/auth/refresh`** — Refresh 쿠키 / X-Session-Request: 1 필수 / Origin 검사 / Access Token 불필요 / Set-Cookie 발급 / Cache-Control: no-store.
- **`POST /api/v1/auth/session/activity`** — 사용자 JWT / Cache-Control: no-store / 자동 polling은 호출하지 않음.
- **`POST /api/v1/devices`** — 사용자 JWT
- **`GET /api/v1/devices`** — 사용자 JWT / Query: deviceType (선택)
- **`GET /api/v1/devices/{deviceId}`** — 사용자 JWT
- **`PATCH /api/v1/devices/{deviceId}`** — 사용자 JWT
- **`POST /api/v1/devices/{deviceId}/enrollment`** — 사용자 JWT
- **`POST /api/v1/device-auth/enroll`** — 공개: 등록 토큰
- **`POST /api/v1/device-auth/token`** — 공개: 장치 ID·secret
- **`POST /api/v1/device-auth/skills`** — 공개: 장치 ID·secret
- **`POST /api/v1/robots`** — 사용자 JWT
- **`GET /api/v1/robots`** — 사용자 JWT
- **`GET /api/v1/robots/{robotId}`** — 사용자 JWT
- **`GET /api/v1/dashboard`** — 사용자 JWT
- **`GET /api/v1/skills`** — 사용자 JWT
- **`GET /api/v1/skills/overview`** — 사용자 JWT
- **`POST /api/v1/skills`** — 사용자 JWT / `Idempotency-Key` 필수
- **`PATCH /api/v1/skills/{skillId}`** — 사용자 JWT
- **`GET /api/v1/skills/{skillId}/versions/latest`** — 사용자 JWT
- **`GET /api/v1/skills/{skillId}/training-requirements`** — 사용자 JWT
- **`POST /api/v1/episode/data-upload-request`** — 장치 JWT / `Idempotency-Key` 필수 / metadata.schemaVersion과 summary.schemaVersion은 문자열입니다. contentType=application/zip, source=REAL, timestampTimebase=MONOTONIC_NS. 파일 본문은 이 API가 아닌 반환 uploadUrl로 PUT합니다.
- **`POST /api/v1/episode/data-upload-request/{uploadSessionId}/complete`** — 장치 JWT / `Idempotency-Key` 필수 / 2xx는 검증 완료를 뜻하지 않습니다. by-session으로 최종 상태를 조회합니다.
- **`POST /api/v1/episode/data-upload-request/{uploadSessionId}/renew`** — 장치 JWT
- **`DELETE /api/v1/episode/data-upload-request/{uploadSessionId}`** — 장치 JWT
- **`GET /api/v1/episode/data-upload-request/by-session/{sessionId}`** — 장치 JWT
- **`GET /api/v1/episodes`** — 사용자 JWT / Query: umiDeviceId, skillId, skillKey, source, labelStatus, success, contractVersion, qualityStatus, status, availableForTraining, startedFrom, startedTo (모두 선택) / 현재 DTO에는 dataType 필드가 없습니다. 페이지네이션 래퍼 없이 배열을 반환합니다.
- **`GET /api/v1/episodes/{episodeId}`** — 사용자 JWT
- **`GET /api/v1/episodes/retention-policy`** — 사용자 JWT
- **`POST /api/v1/training-jobs`** — 사용자 JWT / `Idempotency-Key` 필수 / skillKey와 newSkill 중 정확히 하나를 지정합니다. episodeIds는 중복 없는 비어 있지 않은 배열입니다. trainingMode 미지정 시 FROM_SCRATCH입니다. 현재 서비스는 Mapping 정확히 1개와 Demonstration 1개 이상을 요구합니다.
- **`GET /api/v1/training-jobs`** — 사용자 JWT / Query: skillId, skillKey, umiDeviceId, status (선택)
- **`GET /api/v1/training-jobs/{id}`** — 사용자 JWT
- **`POST /api/v1/training-jobs/{id}/cancel`** — 사용자 JWT
- **`GET /api/v1/training-jobs/{id}/episodes`** — 사용자 JWT / 현재 응답에는 inputRole이 없습니다.
- **`DELETE /api/v1/training-jobs/{id}`** — 사용자 JWT
- **`POST /api/v1/tasks`** — 사용자 JWT
- **`GET /api/v1/tasks`** — 사용자 JWT / Query: q (선택), includeLatestVersion=false (기본값)
- **`GET /api/v1/tasks/{taskId}`** — 사용자 JWT / Query: includeLatestVersion=false (기본값)
- **`PATCH /api/v1/tasks/{taskId}`** — 사용자 JWT
- **`POST /api/v1/tasks/{taskId}/versions`** — 사용자 JWT / `Idempotency-Key` 필수
- **`GET /api/v1/tasks/{taskId}/versions`** — 사용자 JWT
- **`GET /api/v1/tasks/{taskId}/versions/{versionId}`** — 사용자 JWT
- **`POST /api/v1/tasks/{taskId}/versions/{versionId}/validate`** — 사용자 JWT
- **`DELETE /api/v1/tasks/{taskId}`** — 사용자 JWT
- **`POST /api/v1/robots/{robotId}/deployments`** — 사용자 JWT / `Idempotency-Key` 필수
- **`GET /api/v1/robots/{robotId}/deployments`** — 사용자 JWT
- **`GET /api/v1/robots/{robotId}/deployments/{deploymentId}`** — 사용자 JWT
- **`POST /api/v1/robots/{robotId}/deployments/{deploymentId}/cancel`** — 사용자 JWT
- **`POST /api/v1/robots/{robotId}/executions`** — 사용자 JWT / `Idempotency-Key` 필수
- **`GET /api/v1/robots/{robotId}/executions`** — 사용자 JWT
- **`GET /api/v1/robots/{robotId}/executions/{executionId}`** — 사용자 JWT
- **`POST /api/v1/robots/{robotId}/executions/{executionId}/cancel`** — 사용자 JWT / `Idempotency-Key` 필수
- **`POST /api/v1/devices/{deviceId}/heartbeat`** — 장치 JWT
- **`GET /api/v1/robots/me/command-stream`** — 장치 JWT / 응답은 JSON 래퍼가 아닌 text/event-stream입니다. 선택 헤더 Last-Event-ID로 재연결합니다. event=robot.command, id=commandId, data=JetsonCommand JSON; 연결·ping은 SSE 주석입니다.
- **`GET /api/v1/robots/me/deployments/pending`** — 장치 JWT
- **`POST /api/v1/robots/me/deployments/{deploymentId}/manifest`** — 장치 JWT
- **`PATCH /api/v1/robots/me/deployments/{deploymentId}`** — 장치 JWT / items의 실제 키: deploymentItemId, status, error(선택: code/message).
- **`PATCH /api/v1/robots/me/executions/{executionId}`** — 장치 JWT
- **`GET /api/v1/robots/me/deployment/current`** — 장치 JWT
- **`GET /api/v1/robots/me/skills/active`** — 장치 JWT
- **`POST /internal/training-jobs/claim`** — 내부 API: SecurityConfig permitAll; heartbeat/events는 lease 검증
- **`POST /internal/training-jobs/{id}/heartbeat`** — 내부 API: SecurityConfig permitAll; heartbeat/events는 lease 검증
- **`POST /internal/training-jobs/{id}/events`** — 내부 API: SecurityConfig permitAll; heartbeat/events는 lease 검증
- **`POST /internal/training-jobs/{trainingJobId}/checkpoint-upload-session`** — 내부 API: attempt·leaseToken / `Idempotency-Key` 필수
- **`POST /internal/training-jobs/{trainingJobId}/checkpoint-complete`** — 내부 API: attempt·leaseToken / `Idempotency-Key` 필수
- **`POST /internal/skill-versions/{id}/gate-result`** — 내부 API: SecurityConfig permitAll / `Idempotency-Key` 필수
- **`POST /internal/storage-nodes/register`** — 내부 API: SecurityConfig permitAll
- **`POST /internal/storage-nodes/{id}/heartbeat`** — 내부 API: SecurityConfig permitAll
## 내부 학습 이벤트 payload

`POST /internal/training-jobs/{id}/events`의 `type`별 실제 `payload`입니다. 외부 필드는 EventRequest를 따릅니다.

| type | payload |
|---|---|
| STARTED | `{}` |
| PROGRESS | `{"stage":"<stage>","percent":30,"epoch":1,"totalEpochs":100}` (`epoch`, `totalEpochs` 선택) |
| TRAINING_FINISHED | `{"checkpointSizeBytes":12345,"checkpointSha256":"<lowercase sha256 64hex>"}` |
| FAILED | `{"code":"<code>","message":"<message>"}` |
| CANCELED | `{}` |

현재 `INPUT_PREPARED` 이벤트와 requestedEpisodeCount / centralAcceptedEpisodeCount / gpuAcceptedEpisodeCount 필드는 구현되어 있지 않습니다.

## 프레임워크 제공 경로

- `GET /actuator/health`: 상태 확인; 정상 예시 `{"status":"UP"}`. 컨트롤러 API 수에는 포함하지 않습니다. 구성에 따라 컴포넌트 및 하위 health 경로가 달라질 수 있습니다.
- S3 `PUT <uploadUrl>`는 위 서버의 API가 아닙니다. 발급 응답의 requiredHeaders/headers를 적용하고 ZIP 또는 체크포인트 바이너리를 전송합니다.

## 요청·응답 JSON 모델

문자열 자리표시자(`<uuid>`, `<string>`, `<ISO-8601 UTC>`)와 숫자는 형식을 보여주는 값이며 실제 데이터가 아닙니다. 응답 모델은 `data` 내부 형식입니다. `{}`는 자유 형식 객체, 재귀 `children: []`는 같은 단계 모델의 배열입니다. 필드별 null 허용 여부는 DTO 및 서비스 조건을 함께 따릅니다.

<a id="ec2--authenticateduserresponse"></a>

### AuthenticatedUserResponse

```json
{
  "id": "<uuid>",
  "email": "<string>",
  "name": "<string>",
  "createdAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `email: String`, `name: String`, `createdAt: Instant`

코드: [AuthenticatedUserResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/auth/dto/AuthenticatedUserResponse.java)

<a id="ec2--capturemetadata"></a>

### CaptureMetadata

```json
{
  "device": {},
  "camera": {},
  "coordinateSystem": {},
  "units": {}
}
```

필드 타입: `device: Map<String, Object>`, `camera: Map<String, Object>`, `coordinateSystem: Map<String, Object>`, `units: Map<String, Object>`

코드: [CaptureMetadata.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/vo/CaptureMetadata.java)

<a id="ec2--checkpointapimodels-checkpoint"></a>

### CheckpointApiModels.Checkpoint

```json
{
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>"
}
```

필드 타입: `sizeBytes: long`, `sha256: String`

코드: [CheckpointApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/checkpoint/dto/CheckpointApiModels.java)

DTO 검증 선언: `sizeBytes`: `@Positive long sizeBytes`; `sha256`: `@Pattern(regexp="[0-9a-f]{64}") String sha256`

<a id="ec2--checkpointapimodels-completerequest"></a>

### CheckpointApiModels.CompleteRequest

```json
{
  "attempt": 1,
  "leaseToken": "<string>",
  "skillVersionId": "<uuid>",
  "checkpoint": {
    "sizeBytes": 1,
    "sha256": "<소문자 SHA-256 64자리>"
  },
  "metadata": {
    "architecture": "<string>",
    "framework": "<string>",
    "frameworkVersion": "<string>",
    "contractVersion": "<string>",
    "robotProfileId": "<uuid>",
    "actionSpace": "JOINT_ABSOLUTE",
    "actionSpec": {},
    "runtimeSpec": {},
    "nParams": 1,
    "controlRateHz": 0.5,
    "cameraNames": [
      "<string>"
    ],
    "inputSchema": {},
    "outputSchema": {}
  }
}
```

필드 타입: `attempt: int`, `leaseToken: String`, `skillVersionId: UUID`, `checkpoint: Checkpoint`, `metadata: Metadata`

코드: [CheckpointApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/checkpoint/dto/CheckpointApiModels.java)

DTO 검증 선언: `attempt`: `@Positive int attempt`; `leaseToken`: `@NotBlank String leaseToken`; `skillVersionId`: `@NotNull UUID skillVersionId`; `checkpoint`: `@NotNull @Valid Checkpoint checkpoint`; `metadata`: `@NotNull @Valid Metadata metadata`

<a id="ec2--checkpointapimodels-completeresponse"></a>

### CheckpointApiModels.CompleteResponse

```json
{
  "skillVersionId": "<uuid>",
  "status": "<string>"
}
```

필드 타입: `skillVersionId: UUID`, `status: String`

코드: [CheckpointApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/checkpoint/dto/CheckpointApiModels.java)

<a id="ec2--checkpointapimodels-metadata"></a>

### CheckpointApiModels.Metadata

```json
{
  "architecture": "<string>",
  "framework": "<string>",
  "frameworkVersion": "<string>",
  "contractVersion": "<string>",
  "robotProfileId": "<uuid>",
  "actionSpace": "JOINT_ABSOLUTE",
  "actionSpec": {},
  "runtimeSpec": {},
  "nParams": 1,
  "controlRateHz": 0.5,
  "cameraNames": [
    "<string>"
  ],
  "inputSchema": {},
  "outputSchema": {}
}
```

필드 타입: `architecture: String`, `framework: String`, `frameworkVersion: String`, `contractVersion: String`, `robotProfileId: UUID`, `actionSpace: ActionSpace`, `actionSpec: Map<String,Object>`, `runtimeSpec: Map<String,Object>`, `nParams: long`, `controlRateHz: BigDecimal`, `cameraNames: List<String>`, `inputSchema: Map<String,Object>`, `outputSchema: Map<String,Object>`

코드: [CheckpointApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/checkpoint/dto/CheckpointApiModels.java)

DTO 검증 선언: `architecture`: `@NotBlank String architecture`; `framework`: `@NotBlank String framework`; `frameworkVersion`: `@NotBlank String frameworkVersion`; `contractVersion`: `@NotBlank String contractVersion`; `robotProfileId`: `@NotNull UUID robotProfileId`; `actionSpace`: `@NotNull ActionSpace actionSpace`; `actionSpec`: `@NotEmpty Map<String,Object> actionSpec`; `runtimeSpec`: `@NotEmpty Map<String,Object> runtimeSpec`; `nParams`: `@Min(2) long nParams`; `controlRateHz`: `@Positive BigDecimal controlRateHz`; `cameraNames`: `@NotEmpty List<String> cameraNames`; `inputSchema`: `@NotNull Map<String,Object> inputSchema`; `outputSchema`: `@NotNull Map<String,Object> outputSchema`

<a id="ec2--checkpointapimodels-uploadsessionrequest"></a>

### CheckpointApiModels.UploadSessionRequest

```json
{
  "attempt": 1,
  "leaseToken": "<string>",
  "filename": "<string>",
  "modelCompletedAt": "<ISO-8601 UTC>",
  "contentType": "<string>",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>",
  "metadata": {
    "architecture": "<string>",
    "framework": "<string>",
    "frameworkVersion": "<string>",
    "contractVersion": "<string>",
    "robotProfileId": "<uuid>",
    "actionSpace": "JOINT_ABSOLUTE",
    "actionSpec": {},
    "runtimeSpec": {},
    "nParams": 1,
    "controlRateHz": 0.5,
    "cameraNames": [
      "<string>"
    ],
    "inputSchema": {},
    "outputSchema": {}
  }
}
```

필드 타입: `attempt: int`, `leaseToken: String`, `filename: String`, `modelCompletedAt: Instant`, `contentType: String`, `sizeBytes: long`, `sha256: String`, `metadata: Metadata`

코드: [CheckpointApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/checkpoint/dto/CheckpointApiModels.java)

DTO 검증 선언: `attempt`: `@Positive int attempt`; `leaseToken`: `@NotBlank String leaseToken`; `filename`: `@NotBlank String filename`; `modelCompletedAt`: `@NotNull Instant modelCompletedAt`; `contentType`: `@NotBlank String contentType`; `sizeBytes`: `@Positive long sizeBytes`; `sha256`: `@Pattern(regexp="[0-9a-f]{64}") String sha256`; `metadata`: `@NotNull @Valid Metadata metadata`

<a id="ec2--checkpointapimodels-uploadsessionresponse"></a>

### CheckpointApiModels.UploadSessionResponse

```json
{
  "skillVersionId": "<uuid>",
  "version": 1,
  "method": "<string>",
  "uploadUrl": "<string>",
  "expiresAt": "<ISO-8601 UTC>",
  "headers": {}
}
```

필드 타입: `skillVersionId: UUID`, `version: int`, `method: String`, `uploadUrl: String`, `expiresAt: Instant`, `headers: Map<String,String>`

코드: [CheckpointApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/checkpoint/dto/CheckpointApiModels.java)

<a id="ec2--dashboardresponse"></a>

### DashboardResponse

```json
{
  "summary": {
    "umi": {
      "totalCount": 1,
      "onlineCount": 1
    },
    "robots": {
      "totalCount": 1,
      "availableCount": 1
    },
    "trainings": {
      "runningCount": 1,
      "averageProgressPercent": 0.5
    },
    "executions": {
      "completedTodayCount": 1,
      "succeededTodayCount": 1,
      "failedTodayCount": 1,
      "successRatePercent": 0.5
    }
  },
  "robots": [
    {
      "id": "<uuid>",
      "name": "<string>",
      "status": "AVAILABLE",
      "lastSeenAt": "<ISO-8601 UTC>",
      "currentDeploymentId": "<uuid>"
    }
  ],
  "recentTrainings": [
    {
      "id": "<uuid>",
      "skillId": "<uuid>",
      "skillName": "<string>",
      "umiDeviceId": "<uuid>",
      "umiDeviceName": "<string>",
      "status": "<string>",
      "stage": "<string>",
      "progressPercent": 0.5,
      "startedAt": "<ISO-8601 UTC>",
      "updatedAt": "<ISO-8601 UTC>"
    }
  ],
  "generatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `summary: DashboardSummaryResponse`, `robots: List<DashboardRobotResponse>`, `recentTrainings: List<RecentTrainingResponse>`, `generatedAt: Instant`

코드: [DashboardResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardResponse.java)

<a id="ec2--dashboardrobotresponse"></a>

### DashboardRobotResponse

```json
{
  "id": "<uuid>",
  "name": "<string>",
  "status": "AVAILABLE",
  "lastSeenAt": "<ISO-8601 UTC>",
  "currentDeploymentId": "<uuid>"
}
```

필드 타입: `id: UUID`, `name: String`, `status: DashboardRobotStatus`, `lastSeenAt: Instant`, `currentDeploymentId: UUID`

코드: [DashboardRobotResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardRobotResponse.java)

<a id="ec2--dashboardsummaryresponse"></a>

### DashboardSummaryResponse

```json
{
  "umi": {
    "totalCount": 1,
    "onlineCount": 1
  },
  "robots": {
    "totalCount": 1,
    "availableCount": 1
  },
  "trainings": {
    "runningCount": 1,
    "averageProgressPercent": 0.5
  },
  "executions": {
    "completedTodayCount": 1,
    "succeededTodayCount": 1,
    "failedTodayCount": 1,
    "successRatePercent": 0.5
  }
}
```

필드 타입: `umi: UmiSummary`, `robots: RobotSummary`, `trainings: TrainingSummary`, `executions: ExecutionSummary`

코드: [DashboardSummaryResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardSummaryResponse.java)

<a id="ec2--dashboardsummaryresponse-executionsummary"></a>

### DashboardSummaryResponse.ExecutionSummary

```json
{
  "completedTodayCount": 1,
  "succeededTodayCount": 1,
  "failedTodayCount": 1,
  "successRatePercent": 0.5
}
```

필드 타입: `completedTodayCount: long`, `succeededTodayCount: long`, `failedTodayCount: long`, `successRatePercent: BigDecimal`

코드: [DashboardSummaryResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardSummaryResponse.java)

<a id="ec2--dashboardsummaryresponse-robotsummary"></a>

### DashboardSummaryResponse.RobotSummary

```json
{
  "totalCount": 1,
  "availableCount": 1
}
```

필드 타입: `totalCount: long`, `availableCount: long`

코드: [DashboardSummaryResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardSummaryResponse.java)

<a id="ec2--dashboardsummaryresponse-trainingsummary"></a>

### DashboardSummaryResponse.TrainingSummary

```json
{
  "runningCount": 1,
  "averageProgressPercent": 0.5
}
```

필드 타입: `runningCount: long`, `averageProgressPercent: BigDecimal`

코드: [DashboardSummaryResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardSummaryResponse.java)

<a id="ec2--dashboardsummaryresponse-umisummary"></a>

### DashboardSummaryResponse.UmiSummary

```json
{
  "totalCount": 1,
  "onlineCount": 1
}
```

필드 타입: `totalCount: long`, `onlineCount: long`

코드: [DashboardSummaryResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/DashboardSummaryResponse.java)

<a id="ec2--deploymentapimodels-createrequest"></a>

### DeploymentApiModels.CreateRequest

```json
{
  "taskVersionId": "<uuid>"
}
```

필드 타입: `taskVersionId: UUID`

코드: [DeploymentApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deployment/dto/DeploymentApiModels.java)

DTO 검증 선언: `taskVersionId`: `@NotNull UUID taskVersionId`

<a id="ec2--deploymentapimodels-deploymentresponse"></a>

### DeploymentApiModels.DeploymentResponse

```json
{
  "id": "<uuid>",
  "robotId": "<uuid>",
  "taskVersionId": "<uuid>",
  "status": "PENDING",
  "progressPercent": 0.5,
  "items": [
    {
      "id": "<uuid>",
      "skillVersionId": "<uuid>",
      "gatePassed": true,
      "status": "PENDING",
      "downloadedAt": "<ISO-8601 UTC>",
      "verifiedAt": "<ISO-8601 UTC>",
      "error": {
        "code": "<string>",
        "message": "<string>"
      }
    }
  ],
  "error": {
    "code": "<string>",
    "message": "<string>"
  },
  "createdAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>",
  "appliedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `robotId: UUID`, `taskVersionId: UUID`, `status: DeploymentStatus`, `progressPercent: BigDecimal`, `items: List<ItemResponse>`, `error: ErrorInfo`, `createdAt: Instant`, `updatedAt: Instant`, `appliedAt: Instant`

코드: [DeploymentApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deployment/dto/DeploymentApiModels.java)

<a id="ec2--deploymentapimodels-errorinfo"></a>

### DeploymentApiModels.ErrorInfo

```json
{
  "code": "<string>",
  "message": "<string>"
}
```

필드 타입: `code: String`, `message: String`

코드: [DeploymentApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deployment/dto/DeploymentApiModels.java)

<a id="ec2--deploymentapimodels-itemresponse"></a>

### DeploymentApiModels.ItemResponse

```json
{
  "id": "<uuid>",
  "skillVersionId": "<uuid>",
  "gatePassed": true,
  "status": "PENDING",
  "downloadedAt": "<ISO-8601 UTC>",
  "verifiedAt": "<ISO-8601 UTC>",
  "error": {
    "code": "<string>",
    "message": "<string>"
  }
}
```

필드 타입: `id: UUID`, `skillVersionId: UUID`, `gatePassed: boolean`, `status: DeploymentItemStatus`, `downloadedAt: Instant`, `verifiedAt: Instant`, `error: ErrorInfo`

코드: [DeploymentApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deployment/dto/DeploymentApiModels.java)

<a id="ec2--devicecreaterequest"></a>

### DeviceCreateRequest

```json
{
  "deviceType": "UMI_CAMERA",
  "handleNumber": "<string>",
  "name": "<string>",
  "location": "<string>"
}
```

필드 타입: `deviceType: DeviceType`, `handleNumber: String`, `name: String`, `location: String`

코드: [DeviceCreateRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/device/dto/DeviceCreateRequest.java)

DTO 검증 선언: `deviceType`: `@NotNull DeviceType deviceType`; `handleNumber`: `@NotBlank @Size(max = 100) String handleNumber`; `name`: `@NotBlank @Size(max = 100) String name`; `location`: `@Size(max = 200) String location`

<a id="ec2--deviceenrollmentcompleterequest"></a>

### DeviceEnrollmentCompleteRequest

```json
{
  "enrollmentId": "<uuid>",
  "registrationToken": "<string>"
}
```

필드 타입: `enrollmentId: UUID`, `registrationToken: String`

코드: [DeviceEnrollmentCompleteRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deviceenrollment/dto/DeviceEnrollmentCompleteRequest.java)

DTO 검증 선언: `enrollmentId`: `@NotNull UUID enrollmentId`; `registrationToken`: `@NotBlank String registrationToken`

<a id="ec2--deviceenrollmentcompleteresponse"></a>

### DeviceEnrollmentCompleteResponse

```json
{
  "deviceId": "<uuid>",
  "deviceSecret": "<string>",
  "enrolledAt": "<ISO-8601 UTC>"
}
```

필드 타입: `deviceId: UUID`, `deviceSecret: String`, `enrolledAt: Instant`

코드: [DeviceEnrollmentCompleteResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deviceenrollment/dto/DeviceEnrollmentCompleteResponse.java)

<a id="ec2--deviceenrollmentstartresponse"></a>

### DeviceEnrollmentStartResponse

```json
{
  "enrollmentId": "<uuid>",
  "deviceId": "<uuid>",
  "qrPayload": "<string>",
  "expiresAt": "<ISO-8601 UTC>"
}
```

필드 타입: `enrollmentId: UUID`, `deviceId: UUID`, `qrPayload: String`, `expiresAt: Instant`

코드: [DeviceEnrollmentStartResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deviceenrollment/dto/DeviceEnrollmentStartResponse.java)

<a id="ec2--deviceresponse"></a>

### DeviceResponse

```json
{
  "id": "<uuid>",
  "ownerId": "<uuid>",
  "deviceType": "UMI_CAMERA",
  "handleNumber": "<string>",
  "name": "<string>",
  "location": "<string>",
  "connectionStatus": "ONLINE",
  "registrationStatus": "PENDING",
  "lastSeenAt": "<ISO-8601 UTC>",
  "createdAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `ownerId: UUID`, `deviceType: DeviceType`, `handleNumber: String`, `name: String`, `location: String`, `connectionStatus: LiveConnectionStatus`, `registrationStatus: DeviceRegistrationStatus`, `lastSeenAt: Instant`, `createdAt: Instant`

코드: [DeviceResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/device/dto/DeviceResponse.java)

<a id="ec2--devicetokenrequest"></a>

### DeviceTokenRequest

```json
{
  "deviceId": "<string>",
  "deviceSecret": "<string>"
}
```

필드 타입: `deviceId: String`, `deviceSecret: String`

코드: [DeviceTokenRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deviceauth/dto/DeviceTokenRequest.java)

DTO 검증 선언: `deviceId`: `@NotBlank(message = "장치 ID는 필수입니다.") String deviceId`; `deviceSecret`: `@NotBlank(message = "장치 secret은 필수입니다.") String deviceSecret`

<a id="ec2--devicetokenresponse"></a>

### DeviceTokenResponse

```json
{
  "accessToken": "<string>",
  "tokenType": "<string>",
  "expiresIn": 1,
  "deviceType": "UMI_CAMERA"
}
```

필드 타입: `accessToken: String`, `tokenType: String`, `expiresIn: long`, `deviceType: DeviceType`

코드: [DeviceTokenResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/deviceauth/dto/DeviceTokenResponse.java)

<a id="ec2--deviceupdaterequest"></a>

### DeviceUpdateRequest

```json
{
  "name": "<string>",
  "location": "<string>"
}
```

필드 타입: `name: String`, `location: String`

코드: [DeviceUpdateRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/device/dto/DeviceUpdateRequest.java)

DTO 검증 선언: `name`: `@NotBlank @Size(max = 100) String name`; `location`: `@Size(max = 200) String location`

<a id="ec2--emailavailabilityresponse"></a>

### EmailAvailabilityResponse

```json
{
  "email": "<string>",
  "available": true
}
```

필드 타입: `email: String`, `available: boolean`

코드: [EmailAvailabilityResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/auth/dto/EmailAvailabilityResponse.java)

<a id="ec2--episodeapimodels-archiveresponse"></a>

### EpisodeApiModels.ArchiveResponse

```json
{
  "id": "<uuid>",
  "contentType": "<string>",
  "archiveFormatVersion": "<string>",
  "compressionMethod": "STORE",
  "sizeBytes": 1,
  "uncompressedSizeBytes": 1,
  "entryCount": 1,
  "sha256": "<소문자 SHA-256 64자리>",
  "status": "UPLOADING",
  "storedAt": "<ISO-8601 UTC>",
  "validatedAt": "<ISO-8601 UTC>",
  "deletedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `contentType: String`, `archiveFormatVersion: String`, `compressionMethod: ArchiveCompressionMethod`, `sizeBytes: long`, `uncompressedSizeBytes: long`, `entryCount: int`, `sha256: String`, `status: EpisodeArchiveStatus`, `storedAt: Instant`, `validatedAt: Instant`, `deletedAt: Instant`

코드: [EpisodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/dto/EpisodeApiModels.java)

<a id="ec2--episodeapimodels-blockingreason"></a>

### EpisodeApiModels.BlockingReason

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "WAIT"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: RecoveryAction`

코드: [EpisodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/dto/EpisodeApiModels.java)

<a id="ec2--episodeapimodels-episodelistresponse"></a>

### EpisodeApiModels.EpisodeListResponse

```json
{
  "id": "<uuid>",
  "skillKey": "<string>",
  "fileName": "<string>",
  "uploadedAt": "<ISO-8601 UTC>",
  "durationMs": 1,
  "umiDeviceId": "<uuid>",
  "umiDeviceHandleNumber": "<string>",
  "status": "UPLOADING",
  "retentionExpiresAt": "<ISO-8601 UTC>",
  "trainable": true,
  "blockingReasons": [
    {
      "code": "<string>",
      "message": "<string>",
      "recoveryAction": "WAIT"
    }
  ],
  "recoveryAction": "WAIT"
}
```

필드 타입: `id: UUID`, `skillKey: String`, `fileName: String`, `uploadedAt: Instant`, `durationMs: long`, `umiDeviceId: UUID`, `umiDeviceHandleNumber: String`, `status: EpisodeStatus`, `retentionExpiresAt: Instant`, `trainable: boolean`, `blockingReasons: List<BlockingReason>`, `recoveryAction: RecoveryAction`

코드: [EpisodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/dto/EpisodeApiModels.java)

<a id="ec2--episodeapimodels-episoderesponse"></a>

### EpisodeApiModels.EpisodeResponse

```json
{
  "id": "<uuid>",
  "ownerId": "<uuid>",
  "umiDeviceId": "<uuid>",
  "umiDeviceHandleNumber": "<string>",
  "skillId": "<uuid>",
  "skillKey": "<string>",
  "skillDisplayName": "<string>",
  "sessionId": "<string>",
  "source": "SIM",
  "success": true,
  "labelStatus": "UNLABELED",
  "labelSource": "UMI",
  "labeledAt": "<ISO-8601 UTC>",
  "contractVersion": "<string>",
  "metaSchemaVersion": "<string>",
  "summarySchemaVersion": "<string>",
  "status": "UPLOADING",
  "startedAt": "<ISO-8601 UTC>",
  "endedAt": "<ISO-8601 UTC>",
  "startMonotonicNs": 1,
  "timestampTimebase": "MONOTONIC_NS",
  "firstTimestampNs": 1,
  "lastTimestampNs": 1,
  "durationNs": 1,
  "frameCount": 1,
  "poseSampleCount": 1,
  "imuSampleCount": 1,
  "gripperSampleCount": 1,
  "missingFrameCount": 1,
  "trackingGapCount": 1,
  "trackingLostDurationNs": 1,
  "qualityStatus": "PENDING",
  "validatedAt": "<ISO-8601 UTC>",
  "lastUsedAt": "<ISO-8601 UTC>",
  "retentionExpiresAt": "<ISO-8601 UTC>",
  "deletedAt": "<ISO-8601 UTC>",
  "captureMetadata": {
    "device": {},
    "camera": {},
    "coordinateSystem": {},
    "units": {}
  },
  "qualitySummary": {
    "values": {}
  },
  "archive": {
    "id": "<uuid>",
    "contentType": "<string>",
    "archiveFormatVersion": "<string>",
    "compressionMethod": "STORE",
    "sizeBytes": 1,
    "uncompressedSizeBytes": 1,
    "entryCount": 1,
    "sha256": "<소문자 SHA-256 64자리>",
    "status": "UPLOADING",
    "storedAt": "<ISO-8601 UTC>",
    "validatedAt": "<ISO-8601 UTC>",
    "deletedAt": "<ISO-8601 UTC>"
  },
  "trainable": true,
  "blockingReasons": [
    {
      "code": "<string>",
      "message": "<string>",
      "recoveryAction": "WAIT"
    }
  ],
  "recoveryAction": "WAIT",
  "error": {
    "code": "<string>",
    "message": "<string>",
    "recoveryAction": "WAIT"
  },
  "createdAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `ownerId: UUID`, `umiDeviceId: UUID`, `umiDeviceHandleNumber: String`, `skillId: UUID`, `skillKey: String`, `skillDisplayName: String`, `sessionId: String`, `source: EpisodeSource`, `success: Boolean`, `labelStatus: EpisodeLabelStatus`, `labelSource: EpisodeLabelSource`, `labeledAt: Instant`, `contractVersion: String`, `metaSchemaVersion: String`, `summarySchemaVersion: String`, `status: EpisodeStatus`, `startedAt: Instant`, `endedAt: Instant`, `startMonotonicNs: long`, `timestampTimebase: TimestampTimebase`, `firstTimestampNs: long`, `lastTimestampNs: long`, `durationNs: long`, `frameCount: int`, `poseSampleCount: int`, `imuSampleCount: int`, `gripperSampleCount: int`, `missingFrameCount: int`, `trackingGapCount: int`, `trackingLostDurationNs: long`, `qualityStatus: EpisodeQualityStatus`, `validatedAt: Instant`, `lastUsedAt: Instant`, `retentionExpiresAt: Instant`, `deletedAt: Instant`, `captureMetadata: CaptureMetadata`, `qualitySummary: QualitySummary`, `archive: ArchiveResponse`, `trainable: boolean`, `blockingReasons: List<BlockingReason>`, `recoveryAction: RecoveryAction`, `error: ResourceError`, `createdAt: Instant`

코드: [EpisodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/dto/EpisodeApiModels.java)

<a id="ec2--episodeapimodels-resourceerror"></a>

### EpisodeApiModels.ResourceError

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "WAIT"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: RecoveryAction`

코드: [EpisodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/dto/EpisodeApiModels.java)

<a id="ec2--episodeapimodels-retentionpolicyresponse"></a>

### EpisodeApiModels.RetentionPolicyResponse

```json
{
  "name": "<string>",
  "defaultRetentionDays": 1,
  "postTrainingGraceDays": 1,
  "maxRetentionDays": 1,
  "allowManualExtension": true,
  "allowManualPurge": true
}
```

필드 타입: `name: String`, `defaultRetentionDays: int`, `postTrainingGraceDays: int`, `maxRetentionDays: int`, `allowManualExtension: boolean`, `allowManualPurge: boolean`

코드: [EpisodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/dto/EpisodeApiModels.java)

<a id="ec2--episodeprocessingstatusresponse"></a>

### EpisodeProcessingStatusResponse

```json
{
  "episodeId": "<uuid>",
  "sessionId": "<uuid>",
  "status": "<string>",
  "qualityStatus": "<string>",
  "error": {
    "code": "<string>",
    "message": "<string>",
    "recoveryAction": "<string>"
  }
}
```

필드 타입: `episodeId: UUID`, `sessionId: UUID`, `status: String`, `qualityStatus: String`, `error: ProcessingError`

코드: [EpisodeProcessingStatusResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeProcessingStatusResponse.java)

<a id="ec2--episodeprocessingstatusresponse-processingerror"></a>

### EpisodeProcessingStatusResponse.ProcessingError

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "<string>"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: String`

코드: [EpisodeProcessingStatusResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeProcessingStatusResponse.java)

<a id="ec2--episodeuploadcompleterequest"></a>

### EpisodeUploadCompleteRequest

```json
{
  "sessionId": "<uuid>",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>"
}
```

필드 타입: `sessionId: UUID`, `sizeBytes: long`, `sha256: String`

코드: [EpisodeUploadCompleteRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeUploadCompleteRequest.java)

DTO 검증 선언: `sessionId`: `@NotNull UUID sessionId`; `sizeBytes`: `@Positive long sizeBytes`; `sha256`: `@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sha256`

<a id="ec2--episodeuploadcompleteresponse"></a>

### EpisodeUploadCompleteResponse

```json
{
  "episodeId": "<uuid>",
  "sessionId": "<uuid>",
  "status": "<string>",
  "duplicate": true
}
```

필드 타입: `episodeId: UUID`, `sessionId: UUID`, `status: String`, `duplicate: boolean`

코드: [EpisodeUploadCompleteResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeUploadCompleteResponse.java)

<a id="ec2--episodeuploadsessionrequest"></a>

### EpisodeUploadSessionRequest

```json
{
  "sessionId": "<uuid>",
  "contentType": "application/zip",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>",
  "metadata": {
    "contractVersion": "<string>",
    "schemaVersion": "<string>",
    "skillKey": "<string>",
    "source": "REAL",
    "success": true,
    "labelStatus": "LABELED",
    "startedAt": "<ISO-8601 UTC>",
    "endedAt": "<ISO-8601 UTC>",
    "startMonotonicNs": 1,
    "timestampTimebase": "MONOTONIC_NS",
    "device": {},
    "camera": {},
    "coordinateSystem": {},
    "units": {}
  },
  "summary": {
    "schemaVersion": "<string>",
    "firstTimestampNs": 1,
    "lastTimestampNs": 1,
    "frameCount": 1,
    "poseSampleCount": 1,
    "imuSampleCount": 1,
    "gripperSampleCount": 1,
    "missingFrameCount": 1,
    "trackingGapCount": 1,
    "trackingLostDurationNs": 1
  }
}
```

필드 타입: `sessionId: UUID`, `contentType: String`, `sizeBytes: long`, `sha256: String`, `metadata: Metadata`, `summary: Summary`

코드: [EpisodeUploadSessionRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeUploadSessionRequest.java)

DTO 검증 선언: `sessionId`: `@NotNull UUID sessionId`; `contentType`: `@NotBlank @Pattern(regexp = "application/zip") String contentType`; `sizeBytes`: `@Positive long sizeBytes`; `sha256`: `@NotBlank @Pattern(regexp = "[0-9a-f]{64}") String sha256`; `metadata`: `@NotNull @Valid Metadata metadata`; `summary`: `@NotNull @Valid Summary summary`

<a id="ec2--episodeuploadsessionrequest-metadata"></a>

### EpisodeUploadSessionRequest.Metadata

```json
{
  "contractVersion": "<string>",
  "schemaVersion": "<string>",
  "skillKey": "<string>",
  "source": "REAL",
  "success": true,
  "labelStatus": "LABELED",
  "startedAt": "<ISO-8601 UTC>",
  "endedAt": "<ISO-8601 UTC>",
  "startMonotonicNs": 1,
  "timestampTimebase": "MONOTONIC_NS",
  "device": {},
  "camera": {},
  "coordinateSystem": {},
  "units": {}
}
```

필드 타입: `contractVersion: String`, `schemaVersion: String`, `skillKey: String`, `source: String`, `success: Boolean`, `labelStatus: String`, `startedAt: Instant`, `endedAt: Instant`, `startMonotonicNs: long`, `timestampTimebase: String`, `device: Map<String, Object>`, `camera: Map<String, Object>`, `coordinateSystem: Map<String, Object>`, `units: Map<String, Object>`

코드: [EpisodeUploadSessionRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeUploadSessionRequest.java)

DTO 검증 선언: `contractVersion`: `@NotBlank String contractVersion`; `schemaVersion`: `@NotBlank String schemaVersion`; `skillKey`: `@NotBlank String skillKey`; `source`: `@NotBlank @Pattern(regexp = "REAL") String source`; `labelStatus`: `@NotBlank @Pattern(regexp = "LABELED\|UNLABELED") String labelStatus`; `startedAt`: `@NotNull Instant startedAt`; `endedAt`: `@NotNull Instant endedAt`; `startMonotonicNs`: `@PositiveOrZero long startMonotonicNs`; `timestampTimebase`: `@NotBlank @Pattern(regexp = "MONOTONIC_NS") String timestampTimebase`; `device`: `@NotNull Map<String, Object> device`; `camera`: `@NotNull Map<String, Object> camera`

<a id="ec2--episodeuploadsessionrequest-summary"></a>

### EpisodeUploadSessionRequest.Summary

```json
{
  "schemaVersion": "<string>",
  "firstTimestampNs": 1,
  "lastTimestampNs": 1,
  "frameCount": 1,
  "poseSampleCount": 1,
  "imuSampleCount": 1,
  "gripperSampleCount": 1,
  "missingFrameCount": 1,
  "trackingGapCount": 1,
  "trackingLostDurationNs": 1
}
```

필드 타입: `schemaVersion: String`, `firstTimestampNs: long`, `lastTimestampNs: long`, `frameCount: int`, `poseSampleCount: int`, `imuSampleCount: int`, `gripperSampleCount: int`, `missingFrameCount: int`, `trackingGapCount: int`, `trackingLostDurationNs: long`

코드: [EpisodeUploadSessionRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeUploadSessionRequest.java)

DTO 검증 선언: `schemaVersion`: `@NotBlank String schemaVersion`; `firstTimestampNs`: `@PositiveOrZero long firstTimestampNs`; `lastTimestampNs`: `@PositiveOrZero long lastTimestampNs`; `frameCount`: `@Positive int frameCount`; `poseSampleCount`: `@PositiveOrZero int poseSampleCount`; `imuSampleCount`: `@Positive int imuSampleCount`; `gripperSampleCount`: `@PositiveOrZero int gripperSampleCount`; `missingFrameCount`: `@PositiveOrZero int missingFrameCount`; `trackingGapCount`: `@PositiveOrZero int trackingGapCount`; `trackingLostDurationNs`: `@PositiveOrZero long trackingLostDurationNs`

<a id="ec2--episodeuploadsessionresponse"></a>

### EpisodeUploadSessionResponse

```json
{
  "uploadSessionId": "<uuid>",
  "sessionId": "<uuid>",
  "uploadMethod": "<string>",
  "uploadUrl": "<string>",
  "requiredHeaders": {},
  "expiresAt": "<ISO-8601 UTC>",
  "maxUploadBytes": 1
}
```

필드 타입: `uploadSessionId: UUID`, `sessionId: UUID`, `uploadMethod: String`, `uploadUrl: String`, `requiredHeaders: Map<String, String>`, `expiresAt: Instant`, `maxUploadBytes: long`

코드: [EpisodeUploadSessionResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episodeupload/dto/EpisodeUploadSessionResponse.java)

<a id="ec2--executionapimodels-createrequest"></a>

### ExecutionApiModels.CreateRequest

```json
{
  "taskVersionId": "<uuid>",
  "deploymentId": "<uuid>"
}
```

필드 타입: `taskVersionId: UUID`, `deploymentId: UUID`

코드: [ExecutionApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/execution/dto/ExecutionApiModels.java)

DTO 검증 선언: `taskVersionId`: `@NotNull UUID taskVersionId`; `deploymentId`: `@NotNull UUID deploymentId`

<a id="ec2--executionapimodels-errorinfo"></a>

### ExecutionApiModels.ErrorInfo

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "WAIT"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: ExecutionRecoveryAction`

코드: [ExecutionApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/execution/dto/ExecutionApiModels.java)

<a id="ec2--executionapimodels-executionresponse"></a>

### ExecutionApiModels.ExecutionResponse

```json
{
  "id": "<uuid>",
  "ownerId": "<uuid>",
  "robotId": "<uuid>",
  "taskVersionId": "<uuid>",
  "deploymentId": "<uuid>",
  "status": "REQUESTED",
  "currentStep": 1,
  "totalSteps": 1,
  "progressPercent": 0.5,
  "error": {
    "code": "<string>",
    "message": "<string>",
    "recoveryAction": "WAIT"
  },
  "requestedAt": "<ISO-8601 UTC>",
  "startedAt": "<ISO-8601 UTC>",
  "finishedAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `ownerId: UUID`, `robotId: UUID`, `taskVersionId: UUID`, `deploymentId: UUID`, `status: RobotExecutionStatus`, `currentStep: Integer`, `totalSteps: int`, `progressPercent: BigDecimal`, `error: ErrorInfo`, `requestedAt: Instant`, `startedAt: Instant`, `finishedAt: Instant`, `updatedAt: Instant`

코드: [ExecutionApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/execution/dto/ExecutionApiModels.java)

<a id="ec2--gateapimodels-gateresultrequest"></a>

### GateApiModels.GateResultRequest

```json
{
  "metric": "<string>",
  "n": 1,
  "runs": 1,
  "value": 0.5,
  "ciLow": 0.5,
  "ciHigh": 0.5,
  "measuredAt": "<ISO-8601 UTC>"
}
```

필드 타입: `metric: String`, `n: int`, `runs: int`, `value: double`, `ciLow: double`, `ciHigh: double`, `measuredAt: Instant`

코드: [GateApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/gate/dto/GateApiModels.java)

DTO 검증 선언: `metric`: `@NotBlank String metric`; `n`: `@Positive int n`; `runs`: `@Positive int runs`; `value`: `@DecimalMin("0.0") @DecimalMax("1.0") double value`; `ciLow`: `@DecimalMin("0.0") @DecimalMax("1.0") double ciLow`; `ciHigh`: `@DecimalMin("0.0") @DecimalMax("1.0") double ciHigh`; `measuredAt`: `@NotNull Instant measuredAt`

<a id="ec2--gateapimodels-gateresultresponse"></a>

### GateApiModels.GateResultResponse

```json
{
  "skillVersionId": "<uuid>",
  "status": "<string>",
  "gatePassed": true,
  "metric": "<string>",
  "n": 1,
  "runs": 1,
  "value": 0.5,
  "ciLow": 0.5,
  "ciHigh": 0.5,
  "measuredAt": "<ISO-8601 UTC>"
}
```

필드 타입: `skillVersionId: UUID`, `status: String`, `gatePassed: boolean`, `metric: String`, `n: int`, `runs: int`, `value: double`, `ciLow: double`, `ciHigh: double`, `measuredAt: Instant`

코드: [GateApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/gate/dto/GateApiModels.java)

<a id="ec2--gputrainingapimodels-archivedata"></a>

### GpuTrainingApiModels.ArchiveData

```json
{
  "downloadUrl": "<string>",
  "expiresAt": "<ISO-8601 UTC>",
  "contentType": "<string>",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>"
}
```

필드 타입: `downloadUrl: String`, `expiresAt: Instant`, `contentType: String`, `sizeBytes: long`, `sha256: String`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--gputrainingapimodels-basemodeldata"></a>

### GpuTrainingApiModels.BaseModelData

```json
{
  "skillVersionId": "<uuid>",
  "version": 1,
  "downloadUrl": "<string>",
  "expiresAt": "<ISO-8601 UTC>",
  "contentType": "<string>",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>",
  "architecture": "<string>",
  "framework": "<string>",
  "frameworkVersion": "<string>",
  "contractVersion": "<string>"
}
```

필드 타입: `skillVersionId: UUID`, `version: int`, `downloadUrl: String`, `expiresAt: Instant`, `contentType: String`, `sizeBytes: long`, `sha256: String`, `architecture: String`, `framework: String`, `frameworkVersion: String`, `contractVersion: String`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--gputrainingapimodels-claimrequest"></a>

### GpuTrainingApiModels.ClaimRequest

```json
{
  "workerId": "<string>",
  "gpuIndex": 1,
  "gitRev": "<string>",
  "storageNodeId": "<uuid>",
  "ingestBaseUrl": "<string>",
  "availableMemoryMiB": 1,
  "storageCapacityBytes": 1,
  "storageAvailableBytes": 1
}
```

필드 타입: `workerId: String`, `gpuIndex: Integer`, `gitRev: String`, `storageNodeId: UUID`, `ingestBaseUrl: String`, `availableMemoryMiB: Long`, `storageCapacityBytes: Long`, `storageAvailableBytes: Long`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

DTO 검증 선언: `workerId`: `@NotBlank String workerId`; `gpuIndex`: `@NotNull @PositiveOrZero Integer gpuIndex`; `storageNodeId`: `@NotNull UUID storageNodeId`; `availableMemoryMiB`: `@PositiveOrZero Long availableMemoryMiB`; `storageCapacityBytes`: `@PositiveOrZero Long storageCapacityBytes`; `storageAvailableBytes`: `@PositiveOrZero Long storageAvailableBytes`

<a id="ec2--gputrainingapimodels-claimresponse"></a>

### GpuTrainingApiModels.ClaimResponse

```json
{
  "job": {
    "id": "<uuid>",
    "ownerId": "<uuid>",
    "skillKey": "<string>",
    "inputMode": "EXISTING_EPISODES",
    "trainingMode": "FROM_SCRATCH",
    "baseModel": {
      "skillVersionId": "<uuid>",
      "version": 1,
      "downloadUrl": "<string>",
      "expiresAt": "<ISO-8601 UTC>",
      "contentType": "<string>",
      "sizeBytes": 1,
      "sha256": "<소문자 SHA-256 64자리>",
      "architecture": "<string>",
      "framework": "<string>",
      "frameworkVersion": "<string>",
      "contractVersion": "<string>"
    },
    "datasetQuery": {},
    "inputSnapshotSha256": "<소문자 SHA-256 64자리>",
    "episodes": [
      {
        "episodeId": "<uuid>",
        "sessionId": "<string>",
        "inputRole": "<string>",
        "qualityStatus": "<string>",
        "contractVersion": "<string>",
        "archive": {
          "downloadUrl": "<string>",
          "expiresAt": "<ISO-8601 UTC>",
          "contentType": "<string>",
          "sizeBytes": 1,
          "sha256": "<소문자 SHA-256 64자리>"
        }
      }
    ]
  },
  "attempt": 1,
  "leaseToken": "<string>",
  "leaseExpiresAt": "<ISO-8601 UTC>",
  "heartbeatIntervalSeconds": 1
}
```

필드 타입: `job: JobData`, `attempt: int`, `leaseToken: String`, `leaseExpiresAt: Instant`, `heartbeatIntervalSeconds: int`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--gputrainingapimodels-episodedata"></a>

### GpuTrainingApiModels.EpisodeData

```json
{
  "episodeId": "<uuid>",
  "sessionId": "<string>",
  "inputRole": "<string>",
  "qualityStatus": "<string>",
  "contractVersion": "<string>",
  "archive": {
    "downloadUrl": "<string>",
    "expiresAt": "<ISO-8601 UTC>",
    "contentType": "<string>",
    "sizeBytes": 1,
    "sha256": "<소문자 SHA-256 64자리>"
  }
}
```

필드 타입: `episodeId: UUID`, `sessionId: String`, `inputRole: String`, `qualityStatus: String`, `contractVersion: String`, `archive: ArchiveData`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--gputrainingapimodels-eventrequest"></a>

### GpuTrainingApiModels.EventRequest

```json
{
  "attempt": 1,
  "leaseToken": "<string>",
  "eventId": "<string>",
  "sequence": 1,
  "type": "<string>",
  "occurredAt": "<ISO-8601 UTC>",
  "payload": {}
}
```

필드 타입: `attempt: int`, `leaseToken: String`, `eventId: String`, `sequence: long`, `type: String`, `occurredAt: Instant`, `payload: Map<String,Object>`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

DTO 검증 선언: `attempt`: `@Positive int attempt`; `leaseToken`: `@NotBlank String leaseToken`; `eventId`: `@NotBlank String eventId`; `sequence`: `@Positive long sequence`; `type`: `@NotBlank String type`; `occurredAt`: `@NotNull Instant occurredAt`; `payload`: `@NotNull Map<String,Object> payload`

<a id="ec2--gputrainingapimodels-eventresponse"></a>

### GpuTrainingApiModels.EventResponse

```json
{
  "accepted": true,
  "jobStatus": "CREATED",
  "lastSequence": 1
}
```

필드 타입: `accepted: boolean`, `jobStatus: TrainingJobStatus`, `lastSequence: long`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--gputrainingapimodels-heartbeatrequest"></a>

### GpuTrainingApiModels.HeartbeatRequest

```json
{
  "workerId": "<string>",
  "storageNodeId": "<uuid>",
  "attempt": 1,
  "leaseToken": "<string>",
  "storageAvailableBytes": 1
}
```

필드 타입: `workerId: String`, `storageNodeId: UUID`, `attempt: int`, `leaseToken: String`, `storageAvailableBytes: Long`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

DTO 검증 선언: `workerId`: `@NotBlank String workerId`; `attempt`: `@Positive int attempt`; `leaseToken`: `@NotBlank String leaseToken`; `storageAvailableBytes`: `@PositiveOrZero Long storageAvailableBytes`

<a id="ec2--gputrainingapimodels-heartbeatresponse"></a>

### GpuTrainingApiModels.HeartbeatResponse

```json
{
  "leaseExpiresAt": "<ISO-8601 UTC>",
  "cancelRequested": true,
  "serverTime": "<ISO-8601 UTC>"
}
```

필드 타입: `leaseExpiresAt: Instant`, `cancelRequested: boolean`, `serverTime: Instant`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--gputrainingapimodels-jobdata"></a>

### GpuTrainingApiModels.JobData

```json
{
  "id": "<uuid>",
  "ownerId": "<uuid>",
  "skillKey": "<string>",
  "inputMode": "EXISTING_EPISODES",
  "trainingMode": "FROM_SCRATCH",
  "baseModel": {
    "skillVersionId": "<uuid>",
    "version": 1,
    "downloadUrl": "<string>",
    "expiresAt": "<ISO-8601 UTC>",
    "contentType": "<string>",
    "sizeBytes": 1,
    "sha256": "<소문자 SHA-256 64자리>",
    "architecture": "<string>",
    "framework": "<string>",
    "frameworkVersion": "<string>",
    "contractVersion": "<string>"
  },
  "datasetQuery": {},
  "inputSnapshotSha256": "<소문자 SHA-256 64자리>",
  "episodes": [
    {
      "episodeId": "<uuid>",
      "sessionId": "<string>",
      "inputRole": "<string>",
      "qualityStatus": "<string>",
      "contractVersion": "<string>",
      "archive": {
        "downloadUrl": "<string>",
        "expiresAt": "<ISO-8601 UTC>",
        "contentType": "<string>",
        "sizeBytes": 1,
        "sha256": "<소문자 SHA-256 64자리>"
      }
    }
  ]
}
```

필드 타입: `id: UUID`, `ownerId: UUID`, `skillKey: String`, `inputMode: TrainingInputMode`, `trainingMode: TrainingMode`, `baseModel: BaseModelData`, `datasetQuery: Map<String,Object>`, `inputSnapshotSha256: String`, `episodes: List<EpisodeData>`

코드: [GpuTrainingApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/internal/GpuTrainingApiModels.java)

<a id="ec2--jetsoncommand"></a>

### JetsonCommand

```json
{
  "commandId": "<string>",
  "sequence": 1,
  "type": "<string>",
  "robotId": "<uuid>",
  "executionId": "<uuid>",
  "taskVersionId": "<uuid>",
  "deploymentId": "<uuid>",
  "steps": [
    {
      "stepId": "<uuid>",
      "parentStepId": "<uuid>",
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "parameters": {}
    }
  ],
  "issuedAt": "<ISO-8601 UTC>",
  "contractVersion": "<string>"
}
```

필드 타입: `commandId: String`, `sequence: long`, `type: String`, `robotId: UUID`, `executionId: UUID`, `taskVersionId: UUID`, `deploymentId: UUID`, `steps: List<Step>`, `issuedAt: Instant`, `contractVersion: String`

코드: [JetsonCommand.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonCommand.java)

<a id="ec2--jetsoncommand-step"></a>

### JetsonCommand.Step

```json
{
  "stepId": "<uuid>",
  "parentStepId": "<uuid>",
  "order": 1,
  "type": "<string>",
  "skillVersionId": "<uuid>",
  "timeoutSeconds": 1,
  "repeatCount": 1,
  "parameters": {}
}
```

필드 타입: `stepId: UUID`, `parentStepId: UUID`, `order: int`, `type: String`, `skillVersionId: UUID`, `timeoutSeconds: Integer`, `repeatCount: Integer`, `parameters: Map<String, Object>`

코드: [JetsonCommand.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonCommand.java)

<a id="ec2--jetsondeploymentmodels-errorinfo"></a>

### JetsonDeploymentModels.ErrorInfo

```json
{
  "code": "<string>",
  "message": "<string>"
}
```

필드 타입: `code: String`, `message: String`

코드: [JetsonDeploymentModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonDeploymentModels.java)

DTO 검증 선언: `code`: `@NotBlank String code`; `message`: `@NotBlank String message`

<a id="ec2--jetsondeploymentmodels-manifest"></a>

### JetsonDeploymentModels.Manifest

```json
{
  "deploymentId": "<uuid>",
  "robotId": "<uuid>",
  "taskVersionId": "<uuid>",
  "models": [
    {
      "deploymentItemId": "<uuid>",
      "skillVersionId": "<uuid>",
      "downloadUrl": "<string>",
      "expiresAt": "<ISO-8601 UTC>",
      "sizeBytes": 1,
      "sha256": "<소문자 SHA-256 64자리>",
      "architecture": "<string>",
      "framework": "<string>",
      "frameworkVersion": "<string>",
      "contractVersion": "<string>",
      "actionSpace": "JOINT_ABSOLUTE",
      "actionSpec": {},
      "runtimeSpec": {},
      "nParams": 1,
      "controlRateHz": 0.5,
      "cameraNames": [
        "<string>"
      ],
      "inputSchema": {},
      "outputSchema": {}
    }
  ],
  "steps": [
    {
      "stepId": "<uuid>",
      "parentStepId": "<uuid>",
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "parameters": {}
    }
  ],
  "generatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `deploymentId: UUID`, `robotId: UUID`, `taskVersionId: UUID`, `models: List<Model>`, `steps: List<JetsonCommand.Step>`, `generatedAt: Instant`

코드: [JetsonDeploymentModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonDeploymentModels.java)

<a id="ec2--jetsondeploymentmodels-model"></a>

### JetsonDeploymentModels.Model

```json
{
  "deploymentItemId": "<uuid>",
  "skillVersionId": "<uuid>",
  "downloadUrl": "<string>",
  "expiresAt": "<ISO-8601 UTC>",
  "sizeBytes": 1,
  "sha256": "<소문자 SHA-256 64자리>",
  "architecture": "<string>",
  "framework": "<string>",
  "frameworkVersion": "<string>",
  "contractVersion": "<string>",
  "actionSpace": "JOINT_ABSOLUTE",
  "actionSpec": {},
  "runtimeSpec": {},
  "nParams": 1,
  "controlRateHz": 0.5,
  "cameraNames": [
    "<string>"
  ],
  "inputSchema": {},
  "outputSchema": {}
}
```

필드 타입: `deploymentItemId: UUID`, `skillVersionId: UUID`, `downloadUrl: String`, `expiresAt: Instant`, `sizeBytes: long`, `sha256: String`, `architecture: String`, `framework: String`, `frameworkVersion: String`, `contractVersion: String`, `actionSpace: ActionSpace`, `actionSpec: Map<String, Object>`, `runtimeSpec: Map<String, Object>`, `nParams: long`, `controlRateHz: BigDecimal`, `cameraNames: List<String>`, `inputSchema: Map<String, Object>`, `outputSchema: Map<String, Object>`

코드: [JetsonDeploymentModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonDeploymentModels.java)

<a id="ec2--jetsondeploymentmodels-reportrequest"></a>

### JetsonDeploymentModels.ReportRequest

```json
{
  "reportId": "<string>",
  "sequence": 1,
  "status": "PENDING",
  "progressPercent": 0.5,
  "items": [
    {
      "deploymentItemId": "<uuid>",
      "status": "VERIFIED",
      "error": null
    }
  ],
  "error": {
    "code": "<string>",
    "message": "<string>"
  }
}
```

필드 타입: `reportId: String`, `sequence: long`, `status: DeploymentStatus`, `progressPercent: BigDecimal`, `items: List<Map<String, Object>>`, `error: ErrorInfo`

코드: [JetsonDeploymentModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonDeploymentModels.java)

DTO 검증 선언: `reportId`: `@NotBlank @Size(max = 150) String reportId`; `sequence`: `@Positive long sequence`; `status`: `@NotNull DeploymentStatus status`; `progressPercent`: `@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent`

<a id="ec2--jetsonexecutionreportrequest"></a>

### JetsonExecutionReportRequest

```json
{
  "reportId": "<string>",
  "sequence": 1,
  "status": "REQUESTED",
  "currentStep": 1,
  "progressPercent": 0.5,
  "error": {
    "code": "<string>",
    "message": "<string>",
    "recoveryAction": "WAIT"
  }
}
```

필드 타입: `reportId: String`, `sequence: long`, `status: RobotExecutionStatus`, `currentStep: Integer`, `progressPercent: BigDecimal`, `error: ErrorInfo`

코드: [JetsonExecutionReportRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonExecutionReportRequest.java)

DTO 검증 선언: `reportId`: `@NotBlank @Size(max = 150) String reportId`; `sequence`: `@Positive long sequence`; `status`: `@NotNull RobotExecutionStatus status`; `currentStep`: `@Positive Integer currentStep`; `progressPercent`: `@NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal progressPercent`; `error`: `@Valid ErrorInfo error`

<a id="ec2--jetsonexecutionreportrequest-errorinfo"></a>

### JetsonExecutionReportRequest.ErrorInfo

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "WAIT"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: ExecutionRecoveryAction`

코드: [JetsonExecutionReportRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonExecutionReportRequest.java)

DTO 검증 선언: `code`: `@NotBlank @Size(max = 100) String code`; `message`: `@NotBlank String message`

<a id="ec2--jetsonheartbeatrequest"></a>

### JetsonHeartbeatRequest

```json
{
  "firmwareVersion": "<string>",
  "runtimeVersion": "<string>",
  "contractVersion": "<string>"
}
```

필드 타입: `firmwareVersion: String`, `runtimeVersion: String`, `contractVersion: String`

코드: [JetsonHeartbeatRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonHeartbeatRequest.java)

DTO 검증 선언: `firmwareVersion`: `@NotBlank @Size(max = 100) String firmwareVersion`; `runtimeVersion`: `@NotBlank @Size(max = 100) String runtimeVersion`; `contractVersion`: `@NotBlank @Size(max = 100) String contractVersion`

<a id="ec2--jetsonheartbeatresponse"></a>

### JetsonHeartbeatResponse

```json
{
  "serverTime": "<ISO-8601 UTC>",
  "nextHeartbeatSeconds": 1
}
```

필드 타입: `serverTime: Instant`, `nextHeartbeatSeconds: int`

코드: [JetsonHeartbeatResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonHeartbeatResponse.java)

<a id="ec2--jetsonstatemodels-activeskill"></a>

### JetsonStateModels.ActiveSkill

```json
{
  "skillId": "<uuid>",
  "skillKey": "<string>",
  "skillVersionId": "<uuid>",
  "version": 1,
  "deploymentId": "<uuid>",
  "status": "ACTIVE",
  "deployedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `skillId: UUID`, `skillKey: String`, `skillVersionId: UUID`, `version: int`, `deploymentId: UUID`, `status: RobotSkillDeploymentStatus`, `deployedAt: Instant`

코드: [JetsonStateModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonStateModels.java)

<a id="ec2--jetsonstatemodels-currentdeployment"></a>

### JetsonStateModels.CurrentDeployment

```json
{
  "deploymentId": "<uuid>",
  "taskVersionId": "<uuid>",
  "steps": [
    {
      "stepId": "<uuid>",
      "parentStepId": "<uuid>",
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "parameters": {}
    }
  ],
  "skills": [
    {
      "skillId": "<uuid>",
      "skillKey": "<string>",
      "skillVersionId": "<uuid>",
      "version": 1,
      "deploymentId": "<uuid>",
      "status": "ACTIVE",
      "deployedAt": "<ISO-8601 UTC>"
    }
  ],
  "appliedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `deploymentId: UUID`, `taskVersionId: UUID`, `steps: List<JetsonCommand.Step>`, `skills: List<ActiveSkill>`, `appliedAt: Instant`

코드: [JetsonStateModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/jetson/dto/JetsonStateModels.java)

<a id="ec2--loginrequest"></a>

### LoginRequest

```json
{
  "email": "<string>",
  "password": "<string>"
}
```

필드 타입: `email: String`, `password: String`

코드: [LoginRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/auth/dto/LoginRequest.java)

DTO 검증 선언: `email`: `@NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") String email`; `password`: `@NotBlank(message = "비밀번호는 필수입니다.") String password`

<a id="ec2--loginresponse"></a>

### LoginResponse

```json
{
  "accessToken": "<string>",
  "tokenType": "Bearer",
  "expiresIn": 900,
  "user": {
    "id": "<uuid>",
    "email": "<string>",
    "name": "<string>",
    "createdAt": "<ISO-8601 UTC>"
  },
  "sessionExpiresAt": "<ISO-8601 UTC>",
  "absoluteExpiresAt": "<ISO-8601 UTC>"
}
```

필드 타입: `accessToken: String`, `tokenType: String`, `expiresIn: long`, `user: AuthenticatedUserResponse`, `sessionExpiresAt: Instant`, `absoluteExpiresAt: Instant`

코드: [LoginResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/auth/dto/LoginResponse.java)

<a id="ec2--qualitysummary"></a>

### QualitySummary

```json
{
  "values": {}
}
```

필드 타입: `values: Map<String, Object>`

코드: [QualitySummary.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/episode/vo/QualitySummary.java)

<a id="ec2--recenttrainingresponse"></a>

### RecentTrainingResponse

```json
{
  "id": "<uuid>",
  "skillId": "<uuid>",
  "skillName": "<string>",
  "umiDeviceId": "<uuid>",
  "umiDeviceName": "<string>",
  "status": "<string>",
  "stage": "<string>",
  "progressPercent": 0.5,
  "startedAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `skillId: UUID`, `skillName: String`, `umiDeviceId: UUID`, `umiDeviceName: String`, `status: String`, `stage: String`, `progressPercent: BigDecimal`, `startedAt: Instant`, `updatedAt: Instant`

코드: [RecentTrainingResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/dashboard/dto/RecentTrainingResponse.java)

<a id="ec2--robotcreaterequest"></a>

### RobotCreateRequest

```json
{
  "handleNumber": "<string>",
  "name": "<string>",
  "location": "<string>",
  "robotProfileId": "<uuid>"
}
```

필드 타입: `handleNumber: String`, `name: String`, `location: String`, `robotProfileId: UUID`

코드: [RobotCreateRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/robot/dto/RobotCreateRequest.java)

DTO 검증 선언: `handleNumber`: `@NotBlank @Size(max = 100) String handleNumber`; `name`: `@NotBlank @Size(max = 150) String name`; `location`: `@Size(max = 200) String location`

<a id="ec2--robotresponse"></a>

### RobotResponse

```json
{
  "id": "<uuid>",
  "ownerId": "<uuid>",
  "jetsonDeviceId": "<uuid>",
  "robotProfileId": "<uuid>",
  "name": "<string>",
  "connectionStatus": "ONLINE",
  "busy": true,
  "currentDeploymentId": "<uuid>",
  "currentTaskVersionId": "<uuid>",
  "lastSeenAt": "<ISO-8601 UTC>",
  "createdAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `ownerId: UUID`, `jetsonDeviceId: UUID`, `robotProfileId: UUID`, `name: String`, `connectionStatus: LiveConnectionStatus`, `busy: boolean`, `currentDeploymentId: UUID`, `currentTaskVersionId: UUID`, `lastSeenAt: Instant`, `createdAt: Instant`

코드: [RobotResponse.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/robot/dto/RobotResponse.java)

<a id="ec2--signuprequest"></a>

### SignupRequest

```json
{
  "name": "<string>",
  "email": "<string>",
  "password": "<string>"
}
```

필드 타입: `name: String`, `email: String`, `password: String`

코드: [SignupRequest.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/auth/dto/SignupRequest.java)

DTO 검증 선언: `name`: `@NotBlank(message = "이름은 필수입니다.") @Size(max = 100, message = "이름은 100자 이하여야 합니다.") String name`; `email`: `@NotBlank(message = "이메일은 필수입니다.") @Email(message = "이메일 형식이 올바르지 않습니다.") @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$", message = "이메일 형식이 올바르지 않습니다.") @Size(max = 320) String email`; `password`: `@NotBlank(message = "비밀번호는 필수입니다.") @Size(min = 8, max = 72, message = "비밀번호는 8자 이상, 72자 이하여야 합니다.") String password`

<a id="ec2--skillapimodels-createcustomskillrequest"></a>

### SkillApiModels.CreateCustomSkillRequest

```json
{
  "displayName": "<string>",
  "description": "<string>"
}
```

필드 타입: `displayName: String`, `description: String`

코드: [SkillApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/skill/dto/SkillApiModels.java)

DTO 검증 선언: `displayName`: `@NotBlank @Size(max = 150) String displayName`; `description`: `@Size(max = 2000) String description`

<a id="ec2--skillapimodels-updateskilldescriptionrequest"></a>

### SkillApiModels.UpdateSkillDescriptionRequest

```json
{
  "description": "<string>"
}
```

필드 타입: `description: String`

코드: [SkillApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/skill/dto/SkillApiModels.java)

DTO 검증 선언: `description`: `@Size(max = 2000) String description`

<a id="ec2--skillresponses-skilloverviewresponse"></a>

### SkillResponses.SkillOverviewResponse

```json
{
  "id": "<uuid>",
  "displayName": "<string>",
  "description": "<string>",
  "latestVersion": 1,
  "latestVersionCreatedAt": "<ISO-8601 UTC>",
  "deploymentStatus": "PLANNED",
  "latestTrainingRequestedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `displayName: String`, `description: String`, `latestVersion: Integer`, `latestVersionCreatedAt: Instant`, `deploymentStatus: SkillStatus`, `latestTrainingRequestedAt: Instant`

코드: [SkillResponses.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/skill/dto/SkillResponses.java)

<a id="ec2--skillresponses-skillresponse"></a>

### SkillResponses.SkillResponse

```json
{
  "id": "<uuid>",
  "skillKey": "<string>",
  "tier": "TUTORIAL",
  "status": "PLANNED",
  "displayName": "<string>",
  "description": "<string>",
  "persona": "<string>",
  "createdAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `skillKey: String`, `tier: SkillTier`, `status: SkillStatus`, `displayName: String`, `description: String`, `persona: String`, `createdAt: Instant`, `updatedAt: Instant`

코드: [SkillResponses.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/skill/dto/SkillResponses.java)

<a id="ec2--skillresponses-skillversionresponse"></a>

### SkillResponses.SkillVersionResponse

```json
{
  "id": "<uuid>",
  "skillId": "<uuid>",
  "version": 1,
  "status": "<string>",
  "checkpointBytes": 1,
  "checkpointSha256": "<소문자 SHA-256 64자리>",
  "robotProfileId": "<uuid>",
  "contractVersion": "<string>",
  "gatePassed": true,
  "createdAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `skillId: UUID`, `version: int`, `status: String`, `checkpointBytes: long`, `checkpointSha256: String`, `robotProfileId: UUID`, `contractVersion: String`, `gatePassed: boolean`, `createdAt: Instant`

코드: [SkillResponses.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/skill/dto/SkillResponses.java)

<a id="ec2--skillresponses-trainingrequirementsresponse"></a>

### SkillResponses.TrainingRequirementsResponse

```json
{
  "skillId": "<uuid>",
  "skillKey": "<string>",
  "minimumEpisodeCount": 1,
  "recommendedEpisodeCount": 1,
  "maximumEpisodeCount": 1,
  "allowedSources": [
    "<string>"
  ],
  "requiresSameUmiDevice": true,
  "requiredContractVersion": "<string>",
  "supported": true,
  "requirementsVersion": 1
}
```

필드 타입: `skillId: UUID`, `skillKey: String`, `minimumEpisodeCount: int`, `recommendedEpisodeCount: int`, `maximumEpisodeCount: int`, `allowedSources: java.util.List<String>`, `requiresSameUmiDevice: boolean`, `requiredContractVersion: String`, `supported: boolean`, `requirementsVersion: int`

코드: [SkillResponses.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/skill/dto/SkillResponses.java)

<a id="ec2--storagenodeapimodels-heartbeatrequest"></a>

### StorageNodeApiModels.HeartbeatRequest

```json
{
  "workerId": "<string>",
  "status": "ONLINE",
  "capacityBytes": 1,
  "availableBytes": 1,
  "activeUploadCount": 1,
  "activeTrainingCount": 1
}
```

필드 타입: `workerId: String`, `status: StorageNodeStatus`, `capacityBytes: long`, `availableBytes: long`, `activeUploadCount: int`, `activeTrainingCount: int`

코드: [StorageNodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/storage/dto/internal/StorageNodeApiModels.java)

DTO 검증 선언: `workerId`: `@NotBlank String workerId`; `status`: `@NotNull StorageNodeStatus status`; `capacityBytes`: `@PositiveOrZero long capacityBytes`; `availableBytes`: `@PositiveOrZero long availableBytes`; `activeUploadCount`: `@PositiveOrZero int activeUploadCount`; `activeTrainingCount`: `@PositiveOrZero int activeTrainingCount`

<a id="ec2--storagenodeapimodels-heartbeatresponse"></a>

### StorageNodeApiModels.HeartbeatResponse

```json
{
  "serverTime": "<ISO-8601 UTC>",
  "nextHeartbeatSeconds": 1,
  "acceptNewUploads": true
}
```

필드 타입: `serverTime: Instant`, `nextHeartbeatSeconds: int`, `acceptNewUploads: boolean`

코드: [StorageNodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/storage/dto/internal/StorageNodeApiModels.java)

<a id="ec2--storagenodeapimodels-noderesponse"></a>

### StorageNodeApiModels.NodeResponse

```json
{
  "id": "<uuid>",
  "workerId": "<string>",
  "nodeKey": "<string>",
  "name": "<string>",
  "storageType": "LOCAL",
  "status": "ONLINE",
  "capacityBytes": 1,
  "availableBytes": 1,
  "lastSeenAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `workerId: String`, `nodeKey: String`, `name: String`, `storageType: StorageType`, `status: StorageNodeStatus`, `capacityBytes: long`, `availableBytes: long`, `lastSeenAt: Instant`

코드: [StorageNodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/storage/dto/internal/StorageNodeApiModels.java)

<a id="ec2--storagenodeapimodels-registerrequest"></a>

### StorageNodeApiModels.RegisterRequest

```json
{
  "workerId": "<string>",
  "nodeKey": "<string>",
  "name": "<string>",
  "storageType": "LOCAL",
  "capacityBytes": 1,
  "availableBytes": 1
}
```

필드 타입: `workerId: String`, `nodeKey: String`, `name: String`, `storageType: StorageType`, `capacityBytes: long`, `availableBytes: long`

코드: [StorageNodeApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/storage/dto/internal/StorageNodeApiModels.java)

DTO 검증 선언: `workerId`: `@NotBlank String workerId`; `nodeKey`: `@NotBlank String nodeKey`; `name`: `@NotBlank String name`; `storageType`: `@NotNull StorageType storageType`; `capacityBytes`: `@PositiveOrZero long capacityBytes`; `availableBytes`: `@PositiveOrZero long availableBytes`

<a id="ec2--taskapimodels-createrequest"></a>

### TaskApiModels.CreateRequest

```json
{
  "taskKey": "<string>",
  "displayName": "<string>",
  "description": "<string>",
  "steps": [
    {
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "parameters": {},
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "children": [
        {}
      ]
    }
  ]
}
```

필드 타입: `taskKey: String`, `displayName: String`, `description: String`, `steps: List< StepRequest>`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

DTO 검증 선언: `taskKey`: `@NotBlank @Size(max = 100) String taskKey`; `displayName`: `@NotBlank @Size(max = 150) String displayName`; `steps`: `@Size(min = 1) List<@Valid StepRequest> steps`

<a id="ec2--taskapimodels-stepresponse"></a>

### TaskApiModels.StepResponse

```json
{
  "order": 1,
  "type": "<string>",
  "skillVersionId": "<uuid>",
  "parameters": {},
  "timeoutSeconds": 1,
  "repeatCount": 1,
  "children": []
}
```

필드 타입: `order: int`, `type: String`, `skillVersionId: UUID`, `parameters: Map<String, Object>`, `timeoutSeconds: Integer`, `repeatCount: Integer`, `children: List<StepResponse>`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

<a id="ec2--taskapimodels-taskresponse"></a>

### TaskApiModels.TaskResponse

```json
{
  "id": "<uuid>",
  "ownerId": "<uuid>",
  "taskKey": "<string>",
  "displayName": "<string>",
  "description": "<string>",
  "latestVersion": 1,
  "latestVersionDetails": {
    "id": "<uuid>",
    "taskId": "<uuid>",
    "version": 1,
    "steps": [
      {
        "order": 1,
        "type": "<string>",
        "skillVersionId": "<uuid>",
        "parameters": {},
        "timeoutSeconds": 1,
        "repeatCount": 1,
        "children": []
      }
    ],
    "createdAt": "<ISO-8601 UTC>"
  },
  "createdAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `ownerId: UUID`, `taskKey: String`, `displayName: String`, `description: String`, `latestVersion: Integer`, `latestVersionDetails: VersionResponse`, `createdAt: Instant`, `updatedAt: Instant`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

<a id="ec2--taskapimodels-updaterequest"></a>

### TaskApiModels.UpdateRequest

```json
{
  "displayName": "<string>",
  "description": "<string>",
  "steps": [
    {
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "parameters": {},
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "children": [
        {}
      ]
    }
  ]
}
```

필드 타입: `displayName: String`, `description: String`, `steps: List< StepRequest>`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

DTO 검증 선언: `displayName`: `@Size(max = 150) String displayName`; `steps`: `@Size(min = 1) List<@Valid StepRequest> steps`

<a id="ec2--taskapimodels-validaterequest"></a>

### TaskApiModels.ValidateRequest

```json
{
  "robotId": "<uuid>"
}
```

필드 타입: `robotId: UUID`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

DTO 검증 선언: `robotId`: `@NotNull UUID robotId`

<a id="ec2--taskapimodels-validationissue"></a>

### TaskApiModels.ValidationIssue

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "<string>"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: String`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

<a id="ec2--taskapimodels-validationresponse"></a>

### TaskApiModels.ValidationResponse

```json
{
  "valid": true,
  "readiness": "<string>",
  "robotId": "<uuid>",
  "taskVersionId": "<uuid>",
  "deploymentId": "<uuid>",
  "observedAt": "<ISO-8601 UTC>",
  "blockingReasons": [
    {
      "code": "<string>",
      "message": "<string>",
      "recoveryAction": "<string>"
    }
  ],
  "warnings": [
    {
      "code": "<string>",
      "message": "<string>",
      "recoveryAction": "<string>"
    }
  ]
}
```

필드 타입: `valid: boolean`, `readiness: String`, `robotId: UUID`, `taskVersionId: UUID`, `deploymentId: UUID`, `observedAt: Instant`, `blockingReasons: List<ValidationIssue>`, `warnings: List<ValidationIssue>`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

<a id="ec2--taskapimodels-versioncreaterequest"></a>

### TaskApiModels.VersionCreateRequest

```json
{
  "steps": [
    {
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "parameters": {},
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "children": [
        {}
      ]
    }
  ]
}
```

필드 타입: `steps: List< StepRequest>`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

DTO 검증 선언: `steps`: `@NotEmpty List<@Valid StepRequest> steps`

<a id="ec2--taskapimodels-versionresponse"></a>

### TaskApiModels.VersionResponse

```json
{
  "id": "<uuid>",
  "taskId": "<uuid>",
  "version": 1,
  "steps": [
    {
      "order": 1,
      "type": "<string>",
      "skillVersionId": "<uuid>",
      "parameters": {},
      "timeoutSeconds": 1,
      "repeatCount": 1,
      "children": []
    }
  ],
  "createdAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `taskId: UUID`, `version: int`, `steps: List<StepResponse>`, `createdAt: Instant`

코드: [TaskApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/task/dto/TaskApiModels.java)

<a id="ec2--trainingjobapimodels-createrequest"></a>

### TrainingJobApiModels.CreateRequest

```json
{
  "skillKey": "<string>",
  "newSkill": null,
  "umiDeviceId": "<uuid>",
  "episodeIds": [
    "<mapping-episode-uuid>",
    "<demonstration-episode-uuid>"
  ],
  "trainingMode": "FROM_SCRATCH",
  "baseSkillVersionId": null
}
```

필드 타입: `skillKey: String`, `newSkill: CreateCustomSkillRequest`, `umiDeviceId: UUID`, `episodeIds: List<UUID>`, `trainingMode: TrainingMode`, `baseSkillVersionId: UUID`

코드: [TrainingJobApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/TrainingJobApiModels.java)

DTO 검증 선언: `newSkill`: `@Valid CreateCustomSkillRequest newSkill`; `umiDeviceId`: `@NotNull UUID umiDeviceId`

<a id="ec2--trainingjobapimodels-episodeinputresponse"></a>

### TrainingJobApiModels.EpisodeInputResponse

```json
{
  "episodeId": "<uuid>",
  "inputOrder": 1
}
```

필드 타입: `episodeId: UUID`, `inputOrder: int`

코드: [TrainingJobApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/TrainingJobApiModels.java)

<a id="ec2--trainingjobapimodels-errorinfo"></a>

### TrainingJobApiModels.ErrorInfo

```json
{
  "code": "<string>",
  "message": "<string>",
  "recoveryAction": "<string>"
}
```

필드 타입: `code: String`, `message: String`, `recoveryAction: String`

코드: [TrainingJobApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/TrainingJobApiModels.java)

<a id="ec2--trainingjobapimodels-jobresponse"></a>

### TrainingJobApiModels.JobResponse

```json
{
  "id": "<uuid>",
  "skillKey": "<string>",
  "skillDisplayName": "<string>",
  "umiDeviceId": "<uuid>",
  "inputMode": "EXISTING_EPISODES",
  "trainingMode": "FROM_SCRATCH",
  "baseSkillVersionId": "<uuid>",
  "status": "CREATED",
  "progressStage": "<string>",
  "progressPercent": 0.5,
  "expectedEpisodeCount": 1,
  "receivedEpisodeCount": 1,
  "receivedBytes": 1,
  "episodeCount": 1,
  "resultSkillVersionId": "<uuid>",
  "error": {
    "code": "<string>",
    "message": "<string>",
    "recoveryAction": "<string>"
  },
  "createdAt": "<ISO-8601 UTC>",
  "startedAt": "<ISO-8601 UTC>",
  "finishedAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>"
}
```

필드 타입: `id: UUID`, `skillKey: String`, `skillDisplayName: String`, `umiDeviceId: UUID`, `inputMode: TrainingInputMode`, `trainingMode: TrainingMode`, `baseSkillVersionId: UUID`, `status: TrainingJobStatus`, `progressStage: String`, `progressPercent: BigDecimal`, `expectedEpisodeCount: int`, `receivedEpisodeCount: int`, `receivedBytes: long`, `episodeCount: int`, `resultSkillVersionId: UUID`, `error: ErrorInfo`, `createdAt: Instant`, `startedAt: Instant`, `finishedAt: Instant`, `updatedAt: Instant`

코드: [TrainingJobApiModels.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/training/dto/TrainingJobApiModels.java)


## 참조 enum 값

- `ActionSpace`: `JOINT_ABSOLUTE`, `JOINT_DELTA`, `EEF_RELATIVE_ROT6D`
- `ArchiveCompressionMethod`: `STORE`
- `DashboardRobotStatus`: `AVAILABLE`, `BUSY`, `UNKNOWN`, `OFFLINE`
- `DeploymentItemStatus`: `PENDING`, `DOWNLOADING`, `VERIFIED`, `FAILED`
- `DeploymentStatus`: `PENDING`, `DOWNLOADING`, `VERIFYING`, `APPLYING`, `SUCCEEDED`, `FAILED`, `CANCELED`
- `DeviceRegistrationStatus`: `PENDING`, `REGISTERED`
- `DeviceType`: `UMI_CAMERA`, `JETSON`
- `EpisodeArchiveStatus`: `UPLOADING`, `AVAILABLE`, `DELETING`, `DELETED`, `DELETE_FAILED`
- `EpisodeLabelSource`: `UMI`, `USER`, `SERVER`
- `EpisodeLabelStatus`: `UNLABELED`, `LABELED`
- `EpisodeQualityStatus`: `PENDING`, `VALID`, `WARNING`, `INVALID`
- `EpisodeSource`: `SIM`, `REAL`
- `EpisodeStatus`: `UPLOADING`, `VALIDATING`, `READY`, `INVALID`, `DELETING`, `DELETED`, `DELETE_FAILED`
- `ExecutionRecoveryAction`: `WAIT`, `RETRY`, `CREATE_DEPLOYMENT`, `CONTACT_SUPPORT`, `NONE`
- `LiveConnectionStatus`: `ONLINE`, `OFFLINE`, `UNKNOWN`
- `RecoveryAction`: `WAIT`, `RETRY_UPLOAD`, `RECORD_AGAIN`, `SET_LABEL`, `EXTEND_RETENTION`, `CREATE_DEPLOYMENT`, `RETRY`, `CONTACT_SUPPORT`, `NONE`
- `RobotExecutionStatus`: `REQUESTED`, `STARTING`, `RUNNING`, `CANCEL_REQUESTED`, `CANCELED`, `SUCCEEDED`, `FAILED`, `STATUS_UNKNOWN`
- `RobotSkillDeploymentStatus`: `ACTIVE`, `RETIRED`
- `SkillStatus`: `PLANNED`, `COLLECTING`, `TRAINING`, `GATE_FAILED`, `DEPLOYED`
- `SkillTier`: `TUTORIAL`, `DEMO`, `CUSTOM`
- `StorageNodeStatus`: `ONLINE`, `OFFLINE`, `DRAINING`
- `StorageType`: `LOCAL`, `S3`
- `TimestampTimebase`: `MONOTONIC_NS`
- `TrainingInputMode`: `EXISTING_EPISODES`
- `TrainingJobStatus`: `CREATED`, `COLLECTING`, `INPUT_VALIDATING`, `INPUT_READY`, `QUEUED`, `PREPARING`, `RUNNING`, `MODEL_UPLOADING`, `MODEL_VALIDATING`, `CANCEL_REQUESTED`, `CANCELED`, `SUCCEEDED`, `FAILED`
- `TrainingMode`: `FROM_SCRATCH`, `FINE_TUNE`

<a id="ec2--activityresponse"></a>

### AuthController.ActivityResponse

```json
{
  "sessionExpiresAt": "<ISO-8601 UTC>",
  "absoluteExpiresAt": "<ISO-8601 UTC>"
}
```

필드 타입: `sessionExpiresAt: Instant`, `absoluteExpiresAt: Instant`. 실제 응답은 `{"data": {...}}`입니다.

코드: [AuthController.java](/D:/Eunchan_/Git_/S15P21A103/BE/EC2_/src/main/java/com/anssa8bu/ssafy/auth/presentation/AuthController.java).

## 로그인 세션 관리 계약 (0e23128)

| 항목 | 동작 |
|---|---|
| Access Token | 기본 15분, 세션 만료가 더 빠르면 해당 시각으로 제한 |
| 미사용 만료 | 마지막 인증된 활동 보고 이후 기본 30분 |
| 최대 유지 | 최초 로그인 이후 기본 8시간; refresh/activity로 연장 불가 |
| refresh | 새 Access Token 발급; 미사용 만료 시각은 갱신하지 않음 |
| activity | `min(서버 현재 시각 + 30분, absoluteExpiresAt)`으로 세션 TTL을 원자적으로 갱신 |
| 저장소 | Redis가 인증 기준; DB refresh_sessions 테이블과 DB 스키마 변경 없음 |
| 장치 인증 | 사용자 refresh 세션과 분리; 기존 장치 JWT 계약 유지 |

로그인·refresh 성공 응답 헤더:

```http
Cache-Control: no-store
Set-Cookie: ssafy_refresh=<token>; Path=/api/v1/auth; Max-Age=<최대 유지 시각까지 남은 초>; Secure; HttpOnly; SameSite=Lax
```

Refresh Token은 JSON body에 포함하지 않습니다. 형식은 무작위 sessionId와 회전 카운터에 도메인 분리 HMAC을 붙인 서버 인증 토큰이며, Redis에는 원문 대신 SHA-256 해시와 카운터를 저장합니다. 회전 직후 10초 동안 직전 토큰 재시도는 현재 토큰을 그대로 반환하고 추가 회전·세션 연장을 하지 않습니다. 현재 토큰의 연속 refresh도 이 구간에는 같은 Refresh Token을 반환합니다. 이 구간 밖에서 이미 사용된 토큰을 제출하면 세션을 폐기합니다.

로그아웃은 Refresh 쿠키를 사용하므로 Access Token이 만료됐어도 가능합니다. 응답은 HTTP 204와 `Max-Age=0`인 같은 쿠키 삭제 헤더입니다. 세션 삭제 이후 활동 보고·refresh가 Redis 키를 재생성하지 않도록 compare-and-set을 사용합니다.

대표 세션 오류:

| HTTP | code | 처리 |
|---|---|---|
| 401 | AUTH_SESSION_EXPIRED | 쿠키 없음·위조·세션 만료·폐기·비활성 계정; 쿠키 삭제 |
| 401 | REFRESH_TOKEN_REUSED | 유예 범위를 벗어난 유효 과거 토큰 재사용; 세션·쿠키 폐기 |
| 503 | AUTH_SESSION_UNAVAILABLE | Redis 장애 또는 동시 갱신 재시도 소진; 프론트 로그인 상태 유지 |
| 403 | 프레임워크 응답 | 쿠키 API의 X-Session-Request 누락 또는 허용되지 않은 Origin |

```json
{"code":"AUTH_SESSION_EXPIRED","message":"로그인 세션이 만료되었습니다."}
```

CORS는 정확한 허용 Origin과 credentials를 사용합니다. 쿠키 API 호출은 `credentials: include`와 `X-Session-Request: 1`을 사용합니다. 헤더 없는 cross-origin form 요청은 갱신·로그아웃을 수행할 수 없습니다. 로컬 HTTP Compose만 `SESSION_COOKIE_SECURE=false`이며 운영 기본값은 true입니다. 프론트·API는 SameSite=Lax 쿠키가 전달 가능한 동일 사이트 구성이어야 합니다.

프론트는 Access Token을 메모리에만 보관하고 새로고침 시 refresh로 세션을 복원합니다. 만료 임박 또는 API 401 시 refresh 후 기존 요청을 최대 한 번 재시도하며, 네트워크·503 오류는 로그아웃으로 처리하지 않습니다. 여러 탭은 Web Locks/BroadcastChannel로 갱신·로그아웃을 조정하며 로그인 세션 변경 시 이전 요청을 다른 세션으로 재전송하지 않습니다.

사용자가 보이는 탭에서 실제 입력·포인터·휠 이벤트를 발생시킨 경우에만 최대 30초에 한 번 활동을 보고합니다. 자동 학습 조회와 refresh 자체는 활동이 아닙니다. 입력 없는 화면은 서버의 미사용/절대 만료 시 종료됩니다. 기존 schemaVersion=1 세션은 원래 만료까지만 유효하고 refresh를 지원하지 않아 재로그인이 필요합니다.

Redis session 값 (schemaVersion=2):

```json
{
  "schemaVersion": 2,
  "sessionId": "<uuid>",
  "userId": "<uuid>",
  "expiresAt": "<미사용 또는 최대 유지 시각 중 이른 값>",
  "revoked": false,
  "createdAt": "<ISO-8601 UTC>",
  "lastActivityAt": "<ISO-8601 UTC>",
  "absoluteExpiresAt": "<ISO-8601 UTC>",
  "refreshTokenHash": "<SHA-256>",
  "refreshVersion": 0,
  "rotatedAt": "<ISO-8601 UTC>"
}
```

환경변수: `JWT_ACCESS_TOKEN_TTL`(기본 15m), `SESSION_IDLE_TIMEOUT`(30m), `SESSION_ABSOLUTE_TIMEOUT`(8h), `SESSION_COOKIE_SECURE`(true).
