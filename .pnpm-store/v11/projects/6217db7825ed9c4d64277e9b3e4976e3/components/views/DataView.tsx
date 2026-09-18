'use client';

import { datasetStatusMeta, recoveryActionLabel } from '@/lib/datasets';
import { useState, useEffect } from 'react';
import type { Dataset, EpisodePreviewSession } from '@/types';
import { useAppStore } from '@/hooks/useAppStore';
import { createEpisodePreviewSession } from '@/services';
export function DataView() {
  const { datasets, datasetRefreshing, datasetRefreshError, refreshDatasets } = useAppStore();
  const [sortMode, setSortMode] = useState<'latest' | 'oldest' | 'retention'>('latest');
  const [statusFilter, setStatusFilter] = useState('');
  const [detail, setDetail] = useState<Dataset | null>(null);
  const [preview, setPreview] = useState<EpisodePreviewSession | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [previewError, setPreviewError] = useState('');
  const processingStatuses: Dataset['status'][] = ['REGISTERED', 'UPLOADING', 'VALIDATING', 'DELETING'];
  const matchesStatus = (item: Dataset) => !statusFilter || (statusFilter === 'PROCESSING' ? processingStatuses.includes(item.status) : item.status === statusFilter);
  const filtered = datasets.filter(matchesStatus).sort((a, b) => sortMode === 'retention' ? a.retentionDays - b.retentionDays : sortMode === 'oldest' ? a.date.localeCompare(b.date) : b.date.localeCompare(a.date));
  const statusCount = (status: string) => datasets.filter(item => item.status === status).length;
  const processingCount = datasets.filter(item => processingStatuses.includes(item.status)).length;
  useEffect(() => {
    void refreshDatasets();
  }, [refreshDatasets]);
  const openPreview = async () => {
    if (!detail) return;
    setPreviewLoading(true); setPreviewError('');
    try { setPreview(await createEpisodePreviewSession(detail.id)); }
    catch (error) { setPreviewError(error instanceof Error ? error.message : '미리보기를 불러오지 못했어요.'); }
    finally { setPreviewLoading(false); }
  };
  return <>
    <div className="page-heading data-page-heading"><div><h1>데이터</h1>{datasetRefreshing && <p role="status">저장된 데이터를 불러오는 중이에요.</p>}{datasetRefreshError && <p className="form-error" role="alert">데이터 목록을 불러오지 못했어요. <button className="text-button" type="button" onClick={() => void refreshDatasets()}>다시 시도</button></p>}</div><div className="data-list-controls"><select aria-label="데이터 상태" value={statusFilter} onChange={e => setStatusFilter(e.target.value)}><option value="">전체 ({datasets.length})</option><option value="READY">학습 가능 ({statusCount('READY')})</option><option value="PROCESSING">처리 중 ({processingCount})</option><option value="INVALID">확인 실패 ({statusCount('INVALID')})</option></select><select aria-label="데이터 정렬" value={sortMode} onChange={e => setSortMode(e.target.value as typeof sortMode)}><option value="latest">최신 등록순</option><option value="oldest">오래된 등록순</option><option value="retention">보관 만료 임박순</option></select></div></div>
    <section className="card episode-panel data-table-panel">
      <div className="episode-table" role="table">
        <div className="episode-row header" role="row"><span>데이터 번호</span><span>학습 동작</span><span>사용 장치</span><span>확인 결과</span><span>보관 기간</span><span>학습 가능 여부</span></div>
        {filtered.map(item => { const status = datasetStatusMeta[item.status]; return <div className="episode-row" role="row" key={item.id}><span data-label="데이터 번호"><button className="text-button" onClick={() => setDetail(item)}>데이터 {String(item.seq).padStart(3, '0')} →</button><small>{item.date}</small></span><span data-label="학습 동작">{item.skillName}</span><span data-label="사용 장치">{item.umiId}</span><span data-label="확인 결과"><span className={`dataset-status ${status.className}`}><i />{status.label}</span></span><span data-label="보관 기간">{item.retentionDays}일 남음</span><span data-label="학습 가능 여부">{item.status === 'READY' ? '선택 가능' : item.status === 'INVALID' ? '재수집 필요' : '처리 완료 후 가능'}</span></div>; })}
        {!filtered.length && <div className="table-empty empty-with-action"><p>{datasets.length ? '조건에 맞는 데이터가 없어요.' : '아직 등록된 데이터가 없어요.'}</p>{datasets.length > 0 && <button className="secondary-button" onClick={() => setStatusFilter('')}>전체 상태 보기</button>}</div>}
      </div>
    </section>
    {detail && <div className="modal-backdrop" onMouseDown={() => { setDetail(null); setPreview(null); }}><section className="register-modal data-detail-modal" role="dialog" aria-modal="true" aria-label="데이터 상세" onMouseDown={e => e.stopPropagation()}><div className="modal-heading data-modal-heading"><div><p>데이터 상세</p><div className="data-modal-title"><h2>데이터 {String(detail.seq).padStart(3, '0')}</h2><span className={`dataset-status ${datasetStatusMeta[detail.status].className}`}><i />{datasetStatusMeta[detail.status].label}</span></div></div><button className="close-button" aria-label="닫기" onClick={() => { setDetail(null); setPreview(null); }}>×</button></div><dl className="data-detail-grid"><div><dt>학습 동작</dt><dd>{detail.skillName}</dd></div><div><dt>사용 장치</dt><dd>{detail.umiId}</dd></div><div><dt>촬영 정보</dt><dd>{detail.duration}<small>{detail.size}</small></dd></div><div><dt>학습 사용</dt><dd>{detail.trainable ? '사용 가능' : '사용할 수 없음'}</dd></div></dl>{detail.blockingReasons.length > 0 && <div className="validation-error" role="alert"><strong>확인이 필요한 이유</strong><ul>{detail.blockingReasons.map(reason => <li key={reason.code}>{reason.message}</li>)}</ul><p>{recoveryActionLabel[detail.recoveryAction]}</p></div>}{detail.status !== 'READY' && !detail.blockingReasons.length && <p className="validation-progress" role="status">상태가 바뀌면 이 화면에 자동으로 반영됩니다.</p>}{preview && <div className={`video-stage ${detail.tone}`} role="img" aria-label="데이터 프레임 미리보기"><span className="big-play">▦</span><p>프레임 미리보기 준비 완료</p></div>}{previewError && <p className="form-error" role="alert">{previewError}</p>}{(preview || (detail.status === 'READY' && !preview)) && <div className="data-modal-footer"><p className="modal-note">{preview ? `미리보기는 ${new Intl.DateTimeFormat('ko-KR', { hour: '2-digit', minute: '2-digit' }).format(new Date(preview.expiresAt))}까지 열 수 있어요.` : ''}</p>{detail.status === 'READY' && !preview && <button className="secondary-button" disabled={previewLoading} onClick={() => void openPreview()}>{previewLoading ? '준비 중...' : '데이터 미리보기'}</button>}</div>}</section></div>}
  </>;
}
