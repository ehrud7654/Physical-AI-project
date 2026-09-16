'use client';

import { useState, useEffect } from 'react';
import type { Training, TrainingStatus, View } from '@/types';
import { Pagination } from '@/components/Pagination';
import { usePagination } from '@/hooks/usePagination';
import { useAppStore } from '@/hooks/useAppStore';
import { fetchTrainingDetail } from '@/services';
export function TrainingStatusView({ onNavigate, onToast, onUseResult }: { onNavigate: (view: View) => void; onToast: (message: string) => void; onUseResult: (name: string) => void }) {
  const { skills, trainings, trainingRefreshing, trainingRefreshError, trainingUpdatedAt, refreshTrainings, cancelTrainingById, deleteTrainingsById, retryTraining } = useAppStore();
  const [statusFilter, setStatusFilter] = useState<'all' | TrainingStatus>('all');
  const [detail, setDetail] = useState<Training | null>(null);
  const [confirmFor, setConfirmFor] = useState<Training | null>(null);
  const [busy, setBusy] = useState(false);
  const [selecting, setSelecting] = useState(false);
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [deleteOpen, setDeleteOpen] = useState(false);
  useEffect(() => {
    if (!detail || ['완료', '실패', '취소됨'].includes(detail.status)) return;
    let active = true;
    const refreshDetail = async () => {
      try {
        const latest = await fetchTrainingDetail(detail.id);
        if (active) setDetail(latest);
      } catch {
        if (active) onToast('학습 진행 상태를 갱신하지 못했어요.');
      }
    };
    const timer = window.setInterval(() => void refreshDetail(), 3000);
    return () => {
      active = false;
      window.clearInterval(timer);
    };
  }, [detail?.id, detail?.status, onToast]);
  const countOf = (status: TrainingStatus) => trainings.filter((item) => item.status === status).length;
  const filtered = statusFilter === 'all' ? trainings : trainings.filter((item) => item.status === statusFilter);
  const { page, pageCount, pageItems, setPage } = usePagination(filtered);
  const openDetail = async (item: Training) => {
    if (item.id.startsWith('draft-')) { setDetail(item); return; }
    try { setDetail(await fetchTrainingDetail(item.id)); }
    catch { onToast('학습 상세 정보를 불러오지 못했어요.'); }
  };

  const retry = async (item: Training) => {
    setDetail(null);
    await retryTraining(item);
    onToast(`‘${item.name}’ 학습을 다시 시작했어요.`);
  };
  const cancelConfirmed = async () => {
    if (!confirmFor) return;
    setBusy(true);
    await cancelTrainingById(confirmFor.id);
    await refreshTrainings();
    onToast(`‘${confirmFor.name}’ 학습을 취소했어요.`);
    setBusy(false);
    setConfirmFor(null);
    setDetail(null);
  };
  const refreshStatus = async () => {
    const refreshed = await refreshTrainings();
    onToast(refreshed ? '학습 상태를 최신 정보로 갱신했어요.' : '학습 상태를 불러오지 못했어요. 다시 시도해 주세요.');
  };
  const deletable = filtered.filter((item) => item.status !== '진행 중' && item.status !== '취소 요청');
  const deleteSelected = async () => {
    setBusy(true);
    try {
      await deleteTrainingsById(selectedIds);
      onToast(`${selectedIds.length}개 학습을 삭제했어요.`);
      setSelectedIds([]);
      setSelecting(false);
      setDeleteOpen(false);
    } catch { onToast('삭제 API가 아직 연결되지 않았거나 삭제에 실패했어요.'); }
    finally { setBusy(false); }
  };
  const updatedLabel = trainingUpdatedAt
    ? new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit', second: '2-digit' }).format(trainingUpdatedAt)
    : '확인 중';

  if (detail) {
    const terminal = ['완료', '실패', '취소됨'].includes(detail.status);
    return <>
      <div className="progress-layout">
        <section className="card progress-card">
          <div className="progress-card-actions">{!terminal && <button className="danger-button" disabled={busy} onClick={() => setConfirmFor(detail)}>학습 취소</button>}</div>
          <div className="learning-orbit"><span>{detail.status === '완료' ? '✓' : '✦'}</span></div>
          <p className="eyebrow">{detail.status}</p>
          <h2>{detail.name}</h2>
          <div className="progress-number">{detail.progress}<small>%</small></div>
          <div className="progress-track"><i style={{ width: `${detail.progress}%` }} /></div>
          <div className="progress-meta"><div><small>현재 단계</small><strong>{detail.stage}</strong></div><div><small>시작 시각</small><strong>{detail.started}</strong></div><div><small>입력 방식</small><strong>{detail.inputMode === 'EXISTING_EPISODES' ? '수집 데이터' : '신규 수집'}</strong></div></div>
        </section>
        <section className="card log-card">
          <div className="log-heading"><div><h3>진행 기록</h3></div><span>API 조회 결과</span></div>
          <div className="log-list"><p><time>{detail.started}</time><span className={terminal ? 'log-ok' : 'log-run'}>{detail.status}</span>{detail.stage} · {detail.progress}%</p></div>
          <div className="training-save-actions"><button className="secondary-button" onClick={() => { setDetail(null); void refreshTrainings(); }}>학습 목록으로 돌아가기</button>{detail.status === '완료' && <button className="primary-button" disabled={!skills.some(skill => skill.name === detail.name)} onClick={() => onUseResult(detail.name)}>이 동작으로 작업 생성</button>}</div>
        </section>
      </div>
      {confirmFor && <div className="modal-backdrop" onMouseDown={() => !busy && setConfirmFor(null)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="학습 취소 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>학습을 취소할까요?</h2><p>취소 후 학습 목록을 자동으로 갱신합니다.</p></div><button className="close-button" onClick={() => setConfirmFor(null)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={busy} onClick={() => setConfirmFor(null)}>계속 진행</button><button className="danger-button" disabled={busy} onClick={() => void cancelConfirmed()}>{busy ? '요청 중...' : '취소 요청'}</button></div></section></div>}
    </>;
  }

  return <>
    <div className="page-heading list-page-heading title-divider"><div><h1>학습 목록</h1></div><button className="primary-button page-create-button" onClick={() => onNavigate('learn')}>새 학습 생성</button></div>
    <div className="training-summary" aria-label="학습 상태별 보기"><button className={statusFilter === 'all' ? 'active' : ''} onClick={() => setStatusFilter('all')}><small>전체</small><strong>{trainings.length}</strong></button><button className={statusFilter === '진행 중' ? 'active' : ''} onClick={() => setStatusFilter('진행 중')}><small>진행 중</small><strong>{countOf('진행 중')}</strong></button><button className={statusFilter === '완료' ? 'active' : ''} onClick={() => setStatusFilter('완료')}><small>완료</small><strong>{countOf('완료')}</strong></button><button className={statusFilter === '실패' ? 'active' : ''} onClick={() => setStatusFilter('실패')}><small>확인 필요</small><strong>{countOf('실패')}</strong></button></div>
    <section className="card training-table">
      <div className="training-table-head"><div className="selection-summary">{selecting && <input className="list-select-checkbox" type="checkbox" aria-label="삭제 가능한 학습 전체 선택" disabled={!deletable.length} checked={deletable.length > 0 && selectedIds.length === deletable.length} onChange={() => setSelectedIds(selectedIds.length === deletable.length ? [] : deletable.map(item => item.id))} />}<p className={`refresh-status ${trainingRefreshError ? 'failed' : ''}`} role="status">{selecting ? `${selectedIds.length}개 선택됨` : trainingRefreshError ? '마지막 갱신에 실패했어요.' : `마지막 갱신 ${updatedLabel}`}</p></div><div className="training-controls"><button className={selecting ? 'danger-button list-delete-button' : 'secondary-button'} onClick={() => { if (!selecting) setSelecting(true); else if (selectedIds.length) setDeleteOpen(true); else setSelecting(false); }}>삭제</button><button className="secondary-button refresh-button" disabled={trainingRefreshing} onClick={() => void refreshStatus()}>{trainingRefreshing ? '갱신 중...' : '↻ 새로고침'}</button><select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value as 'all' | TrainingStatus)} aria-label="학습 상태 필터"><option value="all">전체 상태</option><option value="진행 중">진행 중</option><option value="취소 요청">취소 요청</option><option value="취소됨">취소됨</option><option value="완료">완료</option><option value="실패">실패</option></select></div></div>
      {pageItems.map(item => <article key={item.id}>
        {selecting && <input className="list-select-checkbox" type="checkbox" aria-label={`${item.name} 선택`} disabled={item.status === '진행 중' || item.status === '취소 요청'} checked={selectedIds.includes(item.id)} onChange={() => setSelectedIds(current => current.includes(item.id) ? current.filter(id => id !== item.id) : [...current, item.id])} />}
        <div className="training-main"><span className="learning-symbol">✦</span><div><button className="training-title-button" onClick={() => void openDetail(item)}>{item.name}</button><small>{item.robot} · {item.started}</small></div></div>
        <div><small>현재 단계</small><strong>{item.stage}</strong></div>
        <div className="training-progress"><div><i style={{width: `${item.progress}%`}} /></div><strong>{item.progress}%</strong></div>
        <span className={`training-badge ${item.status === '실패' ? 'failed' : item.status === '완료' ? 'complete' : ''}`}>{item.status}</span>
      </article>)}
      {!filtered.length && <div className="table-empty empty-with-action"><p>{trainings.length ? '선택한 상태의 학습이 없어요.' : '아직 생성한 학습이 없어요.'}</p><button className="secondary-button" onClick={() => trainings.length ? setStatusFilter('all') : onNavigate('learn')}>{trainings.length ? '전체 상태 보기' : '학습 생성하기'}</button></div>}
      <Pagination page={page} pageCount={pageCount} onChange={setPage} />
    </section>
    {detail && <div className="modal-backdrop" onMouseDown={() => setDetail(null)}><section className="register-modal data-detail-modal training-detail-modal" role="dialog" aria-modal="true" aria-label="학습 상세 정보" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading data-modal-heading"><div><p>학습 상세</p><div className="data-modal-title"><h2>{detail.name}</h2><span className={`training-badge ${detail.status === '실패' ? 'failed' : detail.status === '완료' ? 'complete' : ''}`}>{detail.status}</span></div></div><button className="close-button" onClick={() => setDetail(null)} aria-label="닫기">×</button></div><dl className="data-detail-grid"><div><dt>현재 단계</dt><dd>{detail.stage}</dd></div><div><dt>대상 로봇</dt><dd>{detail.robot}</dd></div><div><dt>시작 시각</dt><dd>{detail.started}</dd></div><div><dt>진행률</dt><dd>{detail.progress}%</dd></div></dl><div className="training-modal-progress"><div className="training-progress"><div><i style={{width: `${detail.progress}%`}} /></div><strong>{detail.progress}%</strong></div></div>{(['완료', '진행 중', '실패'] as TrainingStatus[]).includes(detail.status) && <div className="data-modal-footer training-modal-footer"><div>{detail.status === '완료' && !skills.some(skill => skill.name === detail.name) && <p className="modal-note">이 학습 결과의 동작 연결 정보가 아직 없어요.</p>}</div>{detail.status === '완료' && <button className="primary-button" disabled={!skills.some(skill => skill.name === detail.name)} onClick={() => onUseResult(detail.name)}>이 동작으로 작업 생성</button>}{detail.status === '진행 중' && <button className="danger-button" onClick={() => { setDetail(null); setConfirmFor(detail); }}>학습 취소</button>}{detail.status === '실패' && <button className="primary-button" onClick={() => void retry(detail)}>다시 시도</button>}</div>}</section></div>}
    {confirmFor && <div className="modal-backdrop" onMouseDown={() => !busy && setConfirmFor(null)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="학습 취소 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>학습을 취소할까요?</h2><p>취소 요청 후 서버가 중단을 확인하면 취소 완료로 표시돼요.</p></div><button className="close-button" onClick={() => setConfirmFor(null)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={busy} onClick={() => setConfirmFor(null)}>계속 진행</button><button className="danger-button" disabled={busy} onClick={cancelConfirmed}>{busy ? '요청 중...' : '취소 요청'}</button></div></section></div>}
    {deleteOpen && <div className="modal-backdrop" onMouseDown={() => !busy && setDeleteOpen(false)}><section className="confirm-modal" role="dialog" aria-modal="true" aria-label="학습 선택 삭제 확인" onMouseDown={(e) => e.stopPropagation()}><div className="modal-heading"><div><h2>선택한 학습을 삭제할까요?</h2><p>{selectedIds.length}개 학습이 삭제됩니다.</p></div><button className="close-button" onClick={() => setDeleteOpen(false)} aria-label="닫기">×</button></div><div className="card-actions"><button className="secondary-button" disabled={busy} onClick={() => setDeleteOpen(false)}>돌아가기</button><button className="danger-button" disabled={busy} onClick={() => void deleteSelected()}>{busy ? '삭제 중...' : '삭제'}</button></div></section></div>}
  </>;
}
