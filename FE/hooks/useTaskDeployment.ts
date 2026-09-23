'use client';

import { useCallback, useEffect, useState } from 'react';
import type { RobotExecution, SavedWork, TaskReadinessResult } from '@/types';
import {
  cancelRobotExecution,
  createDeployment,
  createRobotExecution,
  fetchDeployment,
  fetchDeployments,
  fetchTaskReadiness,
  type Deployment,
} from '@/services';

/**
 * 배포 실패 사유는 로봇 에이전트가 던진 예외 원문(프리사인드 URL·AWS 키 ID 포함)이 그대로 내려올 때가 있다.
 * 사용자에게는 원인만 남기고 원문은 숨긴다.
 */
function deploymentErrorMessage({ message }: { code: string; message: string }): string {
  if (/40[34]|not found|forbidden/i.test(message)) return '로봇에 내려받을 모델 파일을 찾을 수 없어요. 관리자에게 문의해 주세요.';
  if (message.includes('http') || message.length > 120) return '작업 버전을 로봇에 배포하지 못했어요. 잠시 후 다시 시도해 주세요.';
  return message;
}

export function useTaskDeployment(work: SavedWork | undefined, robotId: string) {
  const [record, setRecord] = useState<Deployment | null>(null);
  const [readiness, setReadiness] = useState<TaskReadinessResult | null>(null);
  const [execution, setExecution] = useState<RobotExecution | null>(null);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState('');
  const taskVersionId = work?.taskVersionId ?? '';

  const refresh = useCallback(async () => {
    if (!work || !robotId || !taskVersionId) {
      setRecord(null);
      setReadiness(null);
      return;
    }
    try {
      const [validation, deployments] = await Promise.all([
        fetchTaskReadiness(work.id, taskVersionId, robotId),
        fetchDeployments(robotId),
      ]);
      setReadiness(validation);
      setRecord(deployments.find((item) => item.taskVersionId === taskVersionId &&
        ['PENDING', 'DOWNLOADING', 'VERIFYING', 'APPLYING', 'SUCCEEDED'].includes(item.status)) ?? null);
      setError('');
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '실행 준비 상태를 확인하지 못했어요.');
    }
  }, [work, robotId, taskVersionId]);

  /* eslint-disable react-hooks/set-state-in-effect -- API 상태를 선택된 작업/로봇과 동기화한다. */
  useEffect(() => { void refresh(); }, [refresh]);
  /* eslint-enable react-hooks/set-state-in-effect */

  useEffect(() => {
    if (!record || record.status === 'SUCCEEDED' || record.status === 'FAILED' || record.status === 'CANCELED') return;
    const timer = window.setInterval(() => {
      void fetchDeployment(robotId, record.id).then(setRecord).catch(() => setError('배포 상태를 확인하지 못했어요.'));
    }, 2000);
    return () => window.clearInterval(timer);
  }, [record, robotId]);

  const deploy = async () => {
    if (!work || !taskVersionId || pending) return;
    setPending(true);
    setError('');
    try {
      const validation = await fetchTaskReadiness(work.id, taskVersionId, robotId);
      setReadiness(validation);
      if (!validation.valid) throw new Error(validation.blockingReasons[0]?.message ?? '배포할 수 없는 작업입니다.');
      setRecord(await createDeployment(robotId, taskVersionId));
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '배포하지 못했어요.');
    } finally {
      setPending(false);
    }
  };

  const execute = async () => {
    if (!record || record.status !== 'SUCCEEDED' || !taskVersionId) throw new Error('완료된 배포가 필요합니다.');
    const created = await createRobotExecution({ robotId, taskVersionId, deploymentId: record.id });
    setExecution(created);
    return created;
  };

  const cancel = async () => {
    if (!execution) return null;
    const canceled = await cancelRobotExecution(robotId, execution.id);
    setExecution(canceled);
    return canceled;
  };

  const ready = record?.status === 'SUCCEEDED' && readiness?.valid === true;
  return {
    ready,
    pending: pending || !!record && record.status !== 'SUCCEEDED' && record.status !== 'FAILED' && record.status !== 'CANCELED',
    error: record?.error ? deploymentErrorMessage(record.error) : error,
    version: work?.version,
    readiness,
    deploymentId: record?.id ?? null,
    execution,
    deploy,
    execute,
    cancel,
    refresh,
  };
}
