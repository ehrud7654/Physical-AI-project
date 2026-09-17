'use client';

import { useState } from 'react';
import { useAppStore } from '@/hooks/useAppStore';
import { useTaskDeployment } from '@/hooks/useTaskDeployment';
import { blockCatalog } from '@/lib/blocks';
import { isSkillBlock, flattenWorkflowBlocks, formatWorkDuration, formatUpdatedAt, getWorkflowIssues } from '@/lib/workflow';

/** 작업 하나의 상세(라우트): 구성 블록, 실행 로봇 선택, 배포·실행·중지. */
export function WorkDetailView({ workId, onBack, onEdit, onToast }: { workId: string; onBack: () => void; onEdit: (workId: string) => void; onToast: (message: string) => void }) {
  const { robots, savedWorks } = useAppStore();
  const work = savedWorks.find((item) => item.id === workId);
  const [robotId, setRobotId] = useState(() => work && robots.some((robot) => robot.id === work.robotId)
    ? work.robotId
    : (robots.find((robot) => robot.status === '온라인')?.id ?? robots[0]?.id ?? ''));
  const [executing, setExecuting] = useState(false);
  const deployment = useTaskDeployment(work, robotId);

  if (!work) {
    return <><button className="back-link" type="button" onClick={onBack}>← 작업 목록</button><div className="center-panel"><section className="card state-card"><span className="state-icon">!</span><h2>작업을 찾을 수 없어요</h2><p>삭제되었거나 주소가 잘못되었을 수 있어요.</p></section></div></>;
  }

  const selectedRobot = robots.find((robot) => robot.id === robotId);
  const blocks = flattenWorkflowBlocks(work.blocks);
  const skillCount = blocks.filter(isSkillBlock).length;
  const controlCount = blocks.length - skillCount;
  const workflowIssues = getWorkflowIssues(work);
  const robotReady = selectedRobot?.status === '온라인';
  const canExecute = deployment.ready && !!selectedRobot && robotReady && !workflowIssues.length && !executing;

  const execute = async () => {
    if (!canExecute) return;
    try {
      await deployment.execute();
      setExecuting(true);
      onToast(`‘${work.name}’ 작업 실행을 요청했어요.`);
    } catch (cause) {
      onToast(cause instanceof Error ? cause.message : '작업을 시작하지 못했어요.');
    }
  };

  return <>
    <button className="back-link" type="button" onClick={onBack}>← 작업 목록</button>
    <section className="card saved-work-detail work-detail-page" aria-label="작업 상세">
      <div className="saved-work-detail-head"><div><p className="eyebrow">작업 상세</p><h2>{work.name}</h2><span>{work.id} · {formatUpdatedAt(work.updatedAt)} 수정</span></div><button className="secondary-button" type="button" onClick={() => onEdit(work.id)}>수정하기</button></div>
      <div className="work-summary-metrics"><div><small>학습된 동작</small><strong>{skillCount}<em>개</em></strong></div><div><small>제어 블록</small><strong>{controlCount}<em>개</em></strong></div><div><small>예상 실행 시간</small><strong>{formatWorkDuration(work.estimatedSeconds).replace('약 ', '')}</strong></div></div>

      <section className="detail-section"><div className="detail-section-title"><div><h3>작업 흐름</h3></div><span>{blocks.length}개 블록</span></div><div className="library-workflow-flow">
        {work.blocks.map((block, index) => <article key={block.id} className="library-flow-block"><span className="flow-order">{index + 1}</span><span className={`block-mark ${block.kind}`}>{blockCatalog.find((item) => item.kind === block.kind)?.mark}</span><div><strong>{block.title}</strong><small>{isSkillBlock(block) ? '학습된 단일 동작' : block.kind === 'repeat' ? `${block.repeat ?? 1}회 반복 · 제어 블록` : `${block.motor} ${block.angle}° · 제어 블록`}</small>{block.children?.length ? <p>{block.children.map((child) => child.title).join(' → ')}</p> : null}</div></article>)}
      </div></section>

      <section className="detail-section execution-setup"><div className="detail-section-title"><div><h3>실행 설정</h3><p>실행할 로봇을 선택하고 준비 상태를 확인하세요.</p></div></div><label>실행할 로봇<select value={robotId} disabled={executing} onChange={(event) => { setRobotId(event.target.value); setExecuting(false); }}>{robots.map((robot) => <option key={robot.id} value={robot.id}>{robot.name} · {robot.status}</option>)}</select></label>
        <div className="preflight-grid"><div className={robotReady ? 'check-pass' : 'check-fail'}><span>{robotReady ? '✓' : '!'}</span><div><strong>로봇 상태</strong><small>{selectedRobot ? `${selectedRobot.name} · ${selectedRobot.status}` : '로봇을 선택해 주세요.'}</small></div></div><div className={!workflowIssues.length ? 'check-pass' : 'check-fail'}><span>{!workflowIssues.length ? '✓' : '!'}</span><div><strong>작업 유효성</strong><small>{workflowIssues[0] ?? `학습 동작 ${skillCount}개와 제어 블록 ${controlCount}개를 실행할 수 있어요.`}</small></div></div></div>
        <div className={`deployment-status${deployment.error ? ' failed' : ''}`} role="status"><strong>{deployment.pending ? '배포 중' : deployment.ready ? `작업 v${deployment.version} · 실행 가능` : '배포 필요'}</strong><p>{deployment.ready ? `${selectedRobot?.name}에 현재 작업 버전이 준비됐어요.` : '현재 작업 구성을 확인하고 선택한 로봇에 배포하세요.'}</p>{deployment.error && <p className="deployment-error" role="alert"><span aria-hidden="true">!</span>{deployment.error}</p>}{!deployment.ready && <button className="secondary-button" type="button" disabled={!robotReady || !!workflowIssues.length || deployment.pending} onClick={() => void deployment.deploy()}>{deployment.pending ? '배포 중...' : deployment.error ? '배포 다시 시도' : '작업 버전 배포'}</button>}</div>
        <div className="execution-actions"><button className="secondary-button" type="button" onClick={() => onEdit(work.id)}>수정하기</button><button className="primary-button" type="button" disabled={!canExecute} onClick={() => void execute()}>{executing ? '작업 실행 요청됨' : '▶ 작업 실행'}</button></div>
        {!robotReady && <p className="execution-disabled-reason">온라인 상태의 로봇에서만 작업을 실행할 수 있어요.</p>}
        {executing && <div className="library-running"><i /><span>선택한 로봇에서 작업을 실행하고 있어요.</span><button className="danger-button" type="button" onClick={() => void deployment.cancel().then(() => { setExecuting(false); onToast('작업 중지를 요청했어요.'); })}>작업 중지</button></div>}
      </section>
    </section>
  </>;
}
