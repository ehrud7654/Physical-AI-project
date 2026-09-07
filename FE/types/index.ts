export type View = 'dashboard' | 'data' | 'library' | 'devices' | 'robot-detail' | 'training' | 'learn' | 'work' | 'admin';

export type BlockKind = 'umi' | 'vlm' | 'motor' | 'repeat';

export type RobotStatus = '온라인' | '오프라인' | '작업 중' | '오류';

export type TrainingStatus = '진행 중' | '완료' | '실패';

export type DeviceType = 'robot' | 'umi';

export interface Robot {
  id: string;
  name: string;
  location: string;
  status: RobotStatus;
  /** 현재 배포된 Skill 모델 이름 */
  model: string;
}

export interface DeployedModel {
  version: string;
  name: string;
  deployedAt: string;
}

export interface Umi {
  id: string;
  place: string;
  status: '온라인' | '오프라인';
  collected: string;
  data: number;
}

export interface Dataset {
  id: number;
  date: string;
  duration: string;
  size: string;
  tone: string;
  skillKey: string;
  umiId: string;
  ready: boolean;
  retentionDays: number;
}

export interface Training {
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
  name: string;
  kind: Extract<BlockKind, 'umi' | 'vlm'>;
  description: string;
  mark: string;
}

/** 여러 Skill과 제어 블록을 순서대로 조합한 저장 작업 */
export interface SavedWork {
  id: string;
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
  selected: number[];
  progress: number;
  startedAt: number | null;
  name: string;
  error?: string;
}
