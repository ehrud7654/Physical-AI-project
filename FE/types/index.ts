export type View = 'dashboard' | 'devices' | 'robot-detail' | 'training' | 'learn' | 'work' | 'admin';

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
  motor?: string;
  angle?: number;
  repeat?: number;
  children?: WorkflowBlock[];
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
