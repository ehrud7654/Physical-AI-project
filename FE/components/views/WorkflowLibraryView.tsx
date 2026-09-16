'use client';

import { useState } from 'react';
import type { SavedWork } from '@/types';
import { blockCatalog } from '@/mocks';
import { useTaskDeployment } from '@/hooks/useTaskDeployment';
import { useAppStore } from '@/hooks/useAppStore';
import { isSkillBlock, flattenWorkflowBlocks, formatWorkDuration, formatUpdatedAt, getWorkflowIssues } from '@/lib/workflow';
import { ActiveRobotExecutionsCard } from '@/components/ActiveRobotExecutionsCard';
export function WorkflowLibraryView({ onEdit, onToast }: { onEdit: (workId: string) => void; onToast: (message: string) => void }) {
  const { robots, savedWorks, deleteWorksById } = useAppStore();
  const [mobileDetail, setMobileDetail] = useState(false);
  const [query, setQuery] = useState('');
  const [selectedWorkId, setSelectedWorkId] = useState(savedWorks[0]?.id ?? '');
  const initialWork = savedWorks.find((work) => work.id === selectedWorkId) ?? savedWorks[0];
  const [robotId, setRobotId] = useState(initialWork?.robotId || robots[0]?.id || '');
  const [executing, setExecuting] = useState(false);
  const [selectingWorks, setSelectingWorks] = useState(false);
  const [selectedWorkIds, setSelectedWorkIds] = useState<string[]>([]);
  const [deleteWorksOpen, setDeleteWorksOpen] = useState(false);
  const [deletingWorks, setDeletingWorks] = useState(false);

  const filteredWorks = savedWorks.filter((work) => {
    const blocks = flattenWorkflowBlocks(work.blocks);
    const normalizedQuery = query.trim().toLocaleLowerCase('ko-KR');
    return !normalizedQuery || [work.name, ...blocks.map((block) => block.title)].some((value) => value.toLocaleLowerCase('ko-KR').includes(normalizedQuery));
  });

  const selectedWork = savedWorks.find((work) => work.id === selectedWorkId) ?? savedWorks[0];
  const selectedRobot = robots.find((robot) => robot.id === robotId);
  const blocks = selectedWork ? flattenWorkflowBlocks(selectedWork.blocks) : [];
  const skillCount = blocks.filter(isSkillBlock).length;
  const controlCount = blocks.length - skillCount;
  const workflowIssues = selectedWork ? getWorkflowIssues(selectedWork) : ['작업을 선택해 주세요.'];
  const robotReady = selectedRobot?.status === '온라인';
  const deployment = useTaskDeployment(selectedWork, robotId);
  const canExecute = deployment.ready && !!selectedWork && !!selectedRobot && robotReady && !workflowIssues.length && !executing;

  const selectWork = (work: SavedWork) => {
    setMobileDetail(true);
    window.scrollTo({ top: 0, behavior: 'instant' });
    setSelectedWorkId(work.id);
    setRobotId(robots.some((robot) => robot.id === work.robotId) ? work.robotId : (robots.find((robot) => robot.status === '온라인')?.id ?? robots[0]?.id ?? ''));
    setExecuting(false);
  };

  const executeSelectedWork = async () => {
    if (!selectedWork || !canExecute) return;
    try {
      await deployment.execute();
      setExecuting(true);
      onToast(`‘${selectedWork.name}’ 작업 실행을 요청했어요.`);
    } catch (cause) {
      onToast(cause instanceof Error ? cause.message : '작업을 시작하지 못했어요.');
    }
  };
  const deleteSelectedWorks = async () => {
    setDeletingWorks(true);
    try {
      await deleteWorksById(selectedWorkIds);
      onToast(`${selectedWorkIds.length}개 작업을 삭제했어요.`);
      setSelectedWorkIds([]); setSelectingWorks(false); setDeleteWorksOpen(false);
    } catch { onToast('삭제 API가 아직 연결되지 않았거나 삭제에 실패했어요.'); }
    finally { setDeletingWorks(false); }
  };

  if (!selectedWork) {
    return <><div className="page-heading library-page-heading title-divider"><div><h1>작업 목록</h1></div></div><ActiveRobotExecutionsCard /><div className="center-panel"><section className="card state-card"><span className="state-icon">i</span><h2>저장된 작업이 없어요</h2><p>첫 작업을 만들어 로봇이 할 일을 구성해 보세요.</p></section></div><div className="skill-list-actions list-create-actions"><button className="primary-button skill-add-button" onClick={() => onEdit('')}>새 작업 생성</button></div></>;
  }

  return <>
    <div className="page-heading library-page-heading title-divider"><div><h1>작업 목록</h1></div></div>
    <ActiveRobotExecutionsCard />

    <section className="workflow-library-toolbar" aria-label="작업 검색">
      {selectingWorks && <div className="selection-summary"><input className="list-select-checkbox" type="checkbox" aria-label="작업 전체 선택" disabled={!filteredWorks.length} checked={filteredWorks.length > 0 && selectedWorkIds.length === filteredWorks.length} onChange={() => setSelectedWorkIds(selectedWorkIds.length === filteredWorks.length ? [] : filteredWorks.map(work => work.id))} /><span>{selectedWorkIds.length}개 선택됨</span></div>}
      <label className="workflow-search"><span aria-hidden="true">⌕</span><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="작업명 또는 포함된 블록 검색" aria-label="저장된 작업 검색" /></label>
      <button className={`${selectingWorks ? 'danger-button' : 'secondary-button'} list-delete-button`} onClick={() => { if (!selectingWorks) setSelectingWorks(true); else if (selectedWorkIds.length) setDeleteWorksOpen(true); else setSelectingWorks(false); }}>삭제</button>
    </section>

    <div className={`workflow-library-layout${mobileDetail ? ' mobile-detail-open' : ''}`}>
      <section className="card saved-work-list" aria-label="저장된 작업 목록">
        <div className="saved-work-columns" aria-hidden="true"><span>작업</span><span>예상 시간</span><span>최근 수정일</span><span className="saved-work-total">전체 {savedWorks.length}개</span></div>
        {filteredWorks.length ? filteredWorks.map((work) => {
          const workBlocks = flattenWorkflowBlocks(work.blocks);
          const workSkills = workBlocks.filter(isSkillBlock).length;
          const workControls = workBlocks.length - workSkills;
          return <div key={work.id} className="saved-work-select-row">{selectingWorks && <input className="list-select-checkbox" type="checkbox" aria-label={`${work.name} 선택`} checked={selectedWorkIds.includes(work.id)} onChange={() => setSelectedWorkIds(current => current.includes(work.id) ? current.filter(id => id !== work.id) : [...current, work.id])} />}<button className="saved-work-row" onClick={() => selectingWorks ? setSelectedWorkIds(current => current.includes(work.id) ? current.filter(id => id !== work.id) : [...current, work.id]) : selectWork(work)}>
            <span className="saved-work-main"><i>W</i><span><strong>{work.name}</strong><small>{workSkills ? `학습 동작 ${workSkills}개` : '학습 동작 없음'} · 제어 블록 {workControls}개</small><em>{workBlocks.slice(0, 3).map((block) => block.title).join(' · ')}{workBlocks.length > 3 ? ` 외 ${workBlocks.length - 3}개` : ''}</em></span></span>
            <span className="saved-work-duration">{formatWorkDuration(work.estimatedSeconds)}</span>
            <span className="saved-work-updated">{formatUpdatedAt(work.updatedAt)}</span>
            <span className="saved-work-chevron">›</span>
          </button></div>;
        }) : <div className="library-empty"><strong>검색 결과가 없어요</strong><p>다른 작업명이나 동작을 검색해 보세요.</p></div>}
      </section>

      <aside className="card saved-work-detail"><button className="text-button mobile-detail-back" onClick={() => setMobileDetail(false)}>← 작업 목록</button>
        <div className="saved-work-detail-head"><div><p className="eyebrow">작업 상세</p><h2>{selectedWork.name}</h2><span>{selectedWork.id} · {formatUpdatedAt(selectedWork.updatedAt)} 수정</span></div><button className="secondary-button" onClick={() => onEdit(selectedWork.id)}>수정하기</button></div>
        <div className="work-summary-metrics"><div><small>학습된 동작</small><strong>{skillCount}<em>개</em></strong></div><div><small>제어 블록</small><strong>{controlCount}<em>개</em></strong></div><div><small>예상 실행 시간</small><strong>{formatWorkDuration(selectedWork.estimatedSeconds).replace('약 ', '')}</strong></div></div>

        <section className="detail-section"><div className="detail-section-title"><div><h3>작업 흐름</h3></div><span>{blocks.length}개 블록</span></div><div className="library-workflow-flow">
          {selectedWork.blocks.map((block, index) => <article key={block.id} className="library-flow-block"><span className="flow-order">{index + 1}</span><span className={`block-mark ${block.kind}`}>{blockCatalog.find((item) => item.kind === block.kind)?.mark}</span><div><strong>{block.title}</strong><small>{isSkillBlock(block) ? '학습된 단일 동작' : block.kind === 'repeat' ? `${block.repeat ?? 1}회 반복 · 제어 블록` : `${block.motor} ${block.angle}° · 제어 블록`}</small>{block.children?.length ? <p>{block.children.map((child) => child.title).join(' → ')}</p> : null}</div><em className={isSkillBlock(block) ? 'skill-label' : 'control-label'}>{isSkillBlock(block) ? '학습 동작' : '제어'}</em></article>)}
        </div></section>

        <section className="detail-section execution-setup"><div className="detail-section-title"><div><h3>실행 설정</h3><p>실행할 로봇을 선택하고 준비 상태를 확인하세요.</p></div></div><label>실행할 로봇<select value={robotId} disabled={executing} onChange={(event) => { setRobotId(event.target.value); setExecuting(false); }}>{robots.map((robot) => <option key={robot.id} value={robot.id}>{robot.name} · {robot.status}</option>)}</select></label>
          <div className="preflight-grid"><div className={robotReady ? 'check-pass' : 'check-fail'}><span>{robotReady ? '✓' : '!'}</span><div><strong>로봇 상태</strong><small>{selectedRobot ? `${selectedRobot.name} · ${selectedRobot.status}` : '로봇을 선택해 주세요.'}</small></div></div><div className={!workflowIssues.length ? 'check-pass' : 'check-fail'}><span>{!workflowIssues.length ? '✓' : '!'}</span><div><strong>작업 유효성</strong><small>{workflowIssues[0] ?? `학습 동작 ${skillCount}개와 제어 블록 ${controlCount}개를 실행할 수 있어요.`}</small></div></div></div>
          <div className="deployment-status" role="status"><strong>{deployment.pending ? '배포 중' : deployment.ready ? `작업 v${deployment.version} · 실행 가능` : '배포 필요'}</strong><p>{deployment.ready ? `${selectedRobot?.name}에 현재 작업 버전이 준비됐어요.` : '현재 작업 구성을 확인하고 선택한 로봇에 배포하세요.'}</p>{deployment.error && <p role="alert">{deployment.error}</p>}{!deployment.ready && <button className="secondary-button" disabled={!robotReady || !!workflowIssues.length || deployment.pending} onClick={() => void deployment.deploy()}>{deployment.pending ? '배포 중...' : deployment.error ? '배포 다시 시도' : '작업 버전 배포'}</button>}</div>
          <div className="execution-actions"><button className="secondary-button" onClick={() => onEdit(selectedWork.id)}>수정하기</button><button className="primary-button" disabled={!canExecute} onClick={() => void executeSelectedWork()}>{executing ? '작업 실행 요청됨' : '▶ 작업 실행'}</button></div>
          {!robotReady && <p className="execution-disabled-reason">온라인 상태의 로봇에서만 작업을 실행할 수 있어요.</p>}
          {executing && <div className="library-running"><i /><span>선택한 로봇에서 작업을 실행하고 있어요.</span><button className="danger-button" onClick={() => void deployment.cancel().then(() => { setExecuting(false); onToast('작업 중지를 요청했어요.'); })}>작업 중지</button></div>}
        </section>
      </aside>
    </div>
    <div className="skill-list-actions list-create-actions"><button className="primary-button skill-add-button" onClick={() => onEdit('')}>새 작업 생성</button></div>
    {deleteWorksOpen && <div className="modal-backdrop" onMouseDown={() => !deletingWorks && setDeleteWorksOpen(false)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="작업 선택 삭제 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>선택한 작업을 삭제할까요?</h2><p>{selectedWorkIds.length}개 작업이 삭제됩니다.</p></div><button className="close-button" onClick={() => setDeleteWorksOpen(false)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={deletingWorks} onClick={() => setDeleteWorksOpen(false)}>돌아가기</button><button className="danger-button" disabled={deletingWorks} onClick={() => void deleteSelectedWorks()}>{deletingWorks ? '삭제 중...' : '삭제'}</button></div></section></div>}
  </>;
}
