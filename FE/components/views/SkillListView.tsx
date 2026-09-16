'use client';

import { deploymentStatusMeta } from '@/lib/datasets';
import { type FormEvent, useState, useEffect, useCallback } from 'react';
import type { SkillOverview } from '@/types';
import { createCustomSkill, fetchSkillOverviews, updateSkillDescription } from '@/services';
import { formatApiDateTimeParts } from '@/lib/workflow';
import { Pagination } from '@/components/Pagination';
import { usePagination } from '@/hooks/usePagination';
export function SkillListView({ onToast }: { onToast: (message: string) => void }) {
  const [skills, setSkills] = useState<SkillOverview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [createOpen, setCreateOpen] = useState(false);
  const [displayName, setDisplayName] = useState('');
  const [description, setDescription] = useState('');
  const [createError, setCreateError] = useState('');
  const [creating, setCreating] = useState(false);
  const [editingSkill, setEditingSkill] = useState<SkillOverview | null>(null);
  const [editDescription, setEditDescription] = useState('');
  const [editError, setEditError] = useState('');
  const [updating, setUpdating] = useState(false);
  const { page, pageCount, pageItems, setPage } = usePagination(skills);

  const loadSkills = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      setSkills(await fetchSkillOverviews());
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : '동작 목록을 불러오지 못했어요.');
      onToast('동작 목록을 불러오지 못했어요.');
    } finally {
      setLoading(false);
    }
  }, [onToast]);

  /* eslint-disable react-hooks/set-state-in-effect -- 화면 진입 시 동작 목록을 조회한다. */
  useEffect(() => {
    void loadSkills();
  }, [loadSkills]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const submitSkill = async (event: FormEvent) => {
    event.preventDefault();
    const name = displayName.trim();
    if (!name) {
      setCreateError('동작 이름을 입력해 주세요.');
      return;
    }
    setCreating(true);
    setCreateError('');
    try {
      await createCustomSkill({ displayName: name, description });
      await loadSkills();
      setCreateOpen(false);
      setDisplayName('');
      setDescription('');
      onToast('동작이 추가되었어요.');
    } catch (cause) {
      setCreateError(cause instanceof Error ? cause.message : '동작을 추가하지 못했어요.');
    } finally {
      setCreating(false);
    }
  };

  const openSkillEditor = (skill: SkillOverview) => {
    setEditingSkill(skill);
    setEditDescription(skill.description ?? '');
    setEditError('');
  };

  const submitSkillDescription = async (event: FormEvent) => {
    event.preventDefault();
    if (!editingSkill) return;
    setUpdating(true);
    setEditError('');
    try {
      await updateSkillDescription(editingSkill.id, editDescription);
      await loadSkills();
      setEditingSkill(null);
      onToast('동작 설명이 수정되었어요.');
    } catch (cause) {
      setEditError(cause instanceof Error ? cause.message : '동작 설명을 수정하지 못했어요.');
    } finally {
      setUpdating(false);
    }
  };

  return <>
    <div className="page-heading data-page-heading">
      <div>
        <h1>동작 목록</h1>
        {loading && <p role="status">등록된 동작을 불러오는 중이에요.</p>}
        {error && <p className="form-error" role="alert">동작 목록을 불러오지 못했어요. <button className="text-button" type="button" onClick={() => void loadSkills()}>다시 시도</button></p>}
      </div>
      <button className="primary-button page-create-button" type="button" onClick={() => { setCreateError(''); setCreateOpen(true); }}>동작 추가</button>
    </div>
    <section className="card episode-panel data-table-panel">
      <div className="episode-table" role="table" aria-label="동작 목록">
        <div className="episode-row skill-list-row header" role="row">
          <span>동작 이름</span><span>설명</span><span>최신 버전</span><span>버전 생성 시각</span><span>배포 상태</span><span>최근 학습 요청</span>
        </div>
        {pageItems.map((skill) => { const deployment = deploymentStatusMeta[skill.deploymentStatus]; const versionCreatedAt = skill.latestVersionCreatedAt ? formatApiDateTimeParts(skill.latestVersionCreatedAt) : null; const latestTrainingRequestedAt = skill.latestTrainingRequestedAt ? formatApiDateTimeParts(skill.latestTrainingRequestedAt) : null; return <div className="episode-row skill-list-row" role="row" key={`${skill.displayName}-${skill.latestVersionCreatedAt ?? 'empty'}`}>
          <span className="skill-list-name" data-label="동작 이름"><button className="text-button skill-name-button" type="button" onClick={() => openSkillEditor(skill)}>{skill.displayName}</button></span>
          <span data-label="설명">{skill.description || '-'}</span>
          <span className="skill-list-version" data-label="최신 버전">{skill.latestVersion == null ? '-' : `v${skill.latestVersion}`}</span>
          <span className="skill-list-datetime" data-label="버전 생성 시각">{versionCreatedAt ? <><strong>{versionCreatedAt.date}</strong><small>{versionCreatedAt.time}</small></> : '-'}</span>
          <span data-label="배포 상태"><span className={`dataset-status ${deployment.className}`}><i />{deployment.label}</span></span>
          <span className="skill-list-datetime" data-label="최근 학습 요청">{latestTrainingRequestedAt ? <><strong>{latestTrainingRequestedAt.date}</strong><small>{latestTrainingRequestedAt.time}</small></> : '-'}</span>
        </div>; })}
        {!loading && !error && !skills.length && <div className="table-empty"><p>등록된 동작이 없어요.</p></div>}
      </div>
      <Pagination page={page} pageCount={pageCount} onChange={setPage} />
    </section>
    {createOpen && <div className="modal-backdrop" onMouseDown={() => !creating && setCreateOpen(false)}>
      <form className="register-modal skill-create-modal" role="dialog" aria-modal="true" aria-label="동작 추가" onSubmit={submitSkill} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-heading"><div><p>새로운 커스텀 동작</p><h2>동작 추가</h2></div><button className="close-button" type="button" aria-label="닫기" disabled={creating} onClick={() => setCreateOpen(false)}>×</button></div>
        <label>동작 이름 <strong aria-hidden="true">*</strong><input maxLength={150} value={displayName} onChange={(event) => { setDisplayName(event.target.value); setCreateError(''); }} placeholder="예: 백기잡기" autoFocus required /></label>
        <label>설명 <textarea maxLength={2000} value={description} onChange={(event) => setDescription(event.target.value)} placeholder="동작에 대한 설명을 입력해 주세요. (선택)" rows={5} /></label>
        <p className="modal-note">동작 키, 분류와 상태는 서버에서 자동으로 생성됩니다.</p>
        {createError && <p className="form-error" role="alert">{createError}</p>}
        <div className="modal-actions"><button className="secondary-button" type="button" disabled={creating} onClick={() => setCreateOpen(false)}>취소</button><button className="primary-button" disabled={creating}>{creating ? '추가 중...' : '추가'}</button></div>
      </form>
    </div>}
    {editingSkill && <div className="modal-backdrop" onMouseDown={() => !updating && setEditingSkill(null)}>
      <form className="register-modal skill-create-modal" role="dialog" aria-modal="true" aria-label="동작 설명 수정" onSubmit={submitSkillDescription} onMouseDown={(event) => event.stopPropagation()}>
        <div className="modal-heading"><div><p>동작 정보</p><h2>동작 설명 수정</h2></div><button className="close-button" type="button" aria-label="닫기" disabled={updating} onClick={() => setEditingSkill(null)}>×</button></div>
        <label>동작 이름<input value={editingSkill.displayName} disabled aria-readonly="true" /></label>
        <label>설명 <textarea maxLength={2000} value={editDescription} onChange={(event) => { setEditDescription(event.target.value); setEditError(''); }} placeholder="동작에 대한 설명을 입력해 주세요." rows={5} autoFocus /></label>
        {editError && <p className="form-error" role="alert">{editError}</p>}
        <div className="modal-actions"><button className="secondary-button" type="button" disabled={updating} onClick={() => setEditingSkill(null)}>취소</button><button className="primary-button" disabled={updating}>{updating ? '수정 중...' : '수정'}</button></div>
      </form>
    </div>}
  </>;
}
