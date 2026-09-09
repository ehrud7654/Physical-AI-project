import type { RobotExecution, SavedWork, TaskReadinessResult } from '@/types';
import { clone, delay } from './client';

let executions: RobotExecution[] = [];

/** TASK-08: 실행 직전 서버 준비 상태를 다시 확인한다. */
export async function fetchTaskReadiness(work: SavedWork, robotId: string, deploymentId: string | null): Promise<TaskReadinessResult> {
  await delay();
  const blockingReasons = !work.blocks.length ? [{ code: 'EMPTY_TASK', message: '실행할 동작이 없어요.', recoveryAction: 'NONE' as const }] : [];
  return { valid: !blockingReasons.length, readiness: blockingReasons.length ? 'BLOCKED' : deploymentId ? 'READY' : 'PREPARING', robotId, taskVersionId: `${work.id}-current`, deploymentId, observedAt: new Date().toISOString(), blockingReasons, warnings: [] };
}

/** EXEC-01: 실제 연동 시 Idempotency-Key와 정확한 taskVersionId/deploymentId를 전달한다. */
export async function createRobotExecution(input: { robotId: string; taskVersionId: string; deploymentId: string; totalSteps: number }): Promise<RobotExecution> {
  await delay();
  const now = new Date().toISOString();
  const execution: RobotExecution = { id: `execution_${Date.now()}`, robotId: input.robotId, taskVersionId: input.taskVersionId, deploymentId: input.deploymentId, status: 'REQUESTED', currentStep: null, totalSteps: input.totalSteps, progressPercent: 0, error: null, requestedAt: now, startedAt: null, finishedAt: null, updatedAt: now };
  executions = [execution, ...executions];
  return clone(execution);
}

/** EXEC-02 */
export async function fetchRobotExecutions(robotId: string): Promise<{ data: RobotExecution[]; page: { nextCursor: null; hasNext: false; totalCount: number } }> {
  await delay();
  const data = executions.filter((item) => item.robotId === robotId);
  return { data: clone(data), page: { nextCursor: null, hasNext: false, totalCount: data.length } };
}

/** EXEC-04: 요청 접수와 실제 취소 완료를 구분한다. */
export async function cancelRobotExecution(executionId: string): Promise<RobotExecution> {
  await delay();
  let result = executions.find((item) => item.id === executionId);
  if (!result) throw new Error('실행 기록을 찾을 수 없어요.');
  const status = result.status === 'REQUESTED' ? 'CANCELED' : 'CANCEL_REQUESTED';
  executions = executions.map((item) => item.id === executionId ? { ...item, status, updatedAt: new Date().toISOString() } : item);
  result = executions.find((item) => item.id === executionId)!;
  return clone(result);
}
