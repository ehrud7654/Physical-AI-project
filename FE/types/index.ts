export type View = 'dashboard' | 'data' | 'library' | 'devices' | 'robot-detail' | 'training' | 'learn' | 'work' | 'admin';

export type BlockKind = 'umi' | 'vlm' | 'motor' | 'repeat';

export type RobotStatus = '온라인' | '오프라인' | '작업 중' | '오류';

export type TrainingStatus = '진행 중' | '취소 요청' | '취소됨' | '완료' | '실패';

export type DeviceType = 'robot' | 'umi';

export interface AuthenticatedUser {
  id: string;
  email: string;
  name: string;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: AuthenticatedUser;
}

export type DashboardRobotStatus = 'AVAILABLE' | 'BUSY' | 'OFFLINE';

export interface DashboardData {
  summary: {
    umi: { totalCount: number; onlineCount: number };
    robots: { totalCount: number; availableCount: number };
    trainings: { runningCount: number; averageProgressPercent: number };
    executions: {
      completedTodayCount: number;
      succeededTodayCount: number;
      failedTodayCount: number;
      successRatePercent: number;
    };
  };
  robots: Array<{
    id: string;
    name: string;
    status: DashboardRobotStatus;
    lastSeenAt: string | null;
    currentDeploymentId: string | null;
  }>;
  recentTrainings: Array<{
    id: string;
    skillId: string;
    skillName: string;
    umiDeviceId: string;
    umiDeviceName: string;
    status: string;
    stage: string | null;
    progressPercent: number;
    startedAt: string | null;
    updatedAt: string;
  }>;
  generatedAt: string;
}

export interface Robot {
  id: string;
  /** robots.id. 과거 등록 데이터처럼 Robot 행이 없는 경우에는 존재하지 않는다. */
  robotId?: string;
  jetsonDeviceId?: string;
  name: string;
  location: string;
  status: RobotStatus;
  /** 현재 배포된 Skill 모델 이름 */
  model: string;
}

export interface RobotDetail {
  id: string;
  jetsonDeviceId: string;
  name: string;
  status: RobotStatus;
  currentDeploymentId: string | null;
  currentTaskVersionId: string | null;
  lastSeenAt: string | null;
  createdAt: string;
}

export interface DeployedModel {
  version: string;
  name: string;
  deployedAt: string;
}

export interface Umi {
  id: string;
  /** 서버 devices.id(UUID). 화면 표시용 핸들 번호인 id와 구분한다. */
  deviceId: string;
  place: string;
  status: '온라인' | '오프라인';
  collected: string;
  data: number;
}

export type DatasetStatus = 'REGISTERED' | 'UPLOADING' | 'VALIDATING' | 'READY' | 'INVALID' | 'DELETING' | 'DELETED' | 'DELETE_FAILED';

/** EPISODE-01/02 응답을 데이터 탭 표시용으로 정리한 모델. id는 서버 Episode UUID다. */
export interface Dataset {
  id: string;
  /** 화면 표시용 순번(등록 오래된 순 1부터). 새로고침마다 다시 계산되므로 저장 키로 쓰지 않는다. */
  seq: number;
  /** 화면 표시 문자열 `YYYY. MM. DD  HH:mm` — 0을 채워 문자열 정렬이 시간순과 같다. */
  date: string;
  startedAt: string;
  duration: string;
  size: string;
  tone: string;
  skillKey: string;
  skillName: string;
  umiId: string;
  ready: boolean;
  status: DatasetStatus;
  validationErrors?: string[];
  retentionDays: number;
  retentionExpiresAt: string;
  trainable: boolean;
  blockingReasons: BlockingReason[];
  recoveryAction: RecoveryAction;
}

export interface RetentionPolicy {
  name: string;
  defaultRetentionDays: number;
  postTrainingGraceDays: number;
  maxRetentionDays: number;
  allowManualExtension: boolean;
  allowManualPurge: boolean;
}

export type RecoveryAction = 'WAIT' | 'RETRY_UPLOAD' | 'RECORD_AGAIN' | 'SET_LABEL' | 'EXTEND_RETENTION' | 'CREATE_DEPLOYMENT' | 'RETRY' | 'CONTACT_SUPPORT' | 'NONE';

export interface BlockingReason { code: string; message: string; recoveryAction: RecoveryAction; }

export interface EpisodePreviewSession {
  episodeId: string;
  status: 'READY';
  previewType: 'FRAME_SEQUENCE' | 'VIDEO';
  manifestUrl: string;
  thumbnailUrl: string;
  expiresAt: string;
}

export interface TrainingRequirements {
  skillId: string;
  skillKey: string;
  minimumEpisodeCount: number;
  recommendedEpisodeCount: number;
  maximumEpisodeCount: number;
  allowedSources: Array<'SIM' | 'REAL'>;
  requiresSameUmiDevice: boolean;
  requiredContractVersion: string;
  supported: boolean;
  requirementsVersion: number;
}

export interface TrainingSkill {
  id: string;
  skillKey: string;
  displayName: string;
  description: string | null;
  status: string;
}

export type TaskReadiness = 'PREPARING' | 'READY' | 'BLOCKED' | 'UNKNOWN';
export interface TaskReadinessResult {
  valid: boolean; readiness: TaskReadiness; robotId: string; taskVersionId: string;
  deploymentId: string | null; observedAt: string; blockingReasons: BlockingReason[]; warnings: string[];
}

export type RobotExecutionStatus = 'REQUESTED' | 'STARTING' | 'RUNNING' | 'CANCEL_REQUESTED' | 'CANCELED' | 'SUCCEEDED' | 'FAILED' | 'STATUS_UNKNOWN';
export interface RobotExecution {
  id: string; robotId: string; taskVersionId: string; deploymentId: string; status: RobotExecutionStatus;
  currentStep: number | null; totalSteps: number; progressPercent: number;
  error: { code: string; message: string; recoveryAction: RecoveryAction } | null;
  requestedAt: string; startedAt: string | null; finishedAt: string | null; updatedAt: string;
}

export interface Training {
  id: string;
  skillKey: string;
  umiDeviceId: string;
  inputMode: 'COLLECT_NEW' | 'EXISTING_EPISODES';
  expectedEpisodeCount: number;
  receivedEpisodeCount: number;
  apiStatus: string;
  name: string;
  robot: string;
  progress: number;
  stage: string;
  status: TrainingStatus;
  started: string;
}

export interface WorkflowBlock {
  id: number;
  kind: BlockKind;
  title: string;
  /** 학습된 Skill 블록일 때 참조하는 Skill ID */
  skillId?: string;
  motor?: string;
  angle?: number;
  repeat?: number;
  children?: WorkflowBlock[];
}

/** 학습이 완료되어 WORKFLOW에서 사용할 수 있는 단일 동작 */
export interface LearnedSkill {
  id: string;
  skillId?: string;
  name: string;
  kind: Extract<BlockKind, 'umi' | 'vlm'>;
  description: string;
  mark: string;
}

/** 여러 Skill과 제어 블록을 순서대로 조합한 저장 작업 */
export interface SavedWork {
  id: string;
  taskKey?: string;
  taskVersionId?: string | null;
  version?: number | null;
  name: string;
  /** 편집기에서 마지막으로 선택한 로봇. 실행 시 다른 로봇으로 변경할 수 있다. */
  robotId: string;
  blocks: WorkflowBlock[];
  estimatedSeconds: number;
  updatedAt: string;
}

export interface BlockCatalogItem {
  kind: BlockKind;
  title: string;
  description: string;
  mark: string;
}

export interface AdminUser {
  id: string;
  name: string;
  team: string;
}

export interface LearningDraft {
  step: number;
  skillKey: string;
  method: 'new' | 'existing' | null;
  umiId: string;
  expectedEpisodes: number;
  /** 선택한 Episode ID(UUID) */
  selected: string[];
  progress: number;
  startedAt: number | null;
  name: string;
  error?: string;
  trainingJobId?: string;
}
