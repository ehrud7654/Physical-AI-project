'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { AdminUser, Dataset, LearnedSkill, Robot, SavedWork, Training, Umi, LearningDraft } from '@/types';
import { initialLearnedSkills, initialPermissions, initialUsers } from '@/mocks';
import {
  addTraining,
  applyModel,
  cancelTraining,
  fetchRobots,
  fetchDatasets,
  fetchSavedWorks,
  fetchLearnedSkills,
  fetchTrainings,
  fetchUmis,
  registerDevice,
  retryTraining,
  savePermissions,
  saveWork,
  updateRobot,
  type RegisterDeviceInput,
} from '@/services';

interface AppStore {
  learningDraft: LearningDraft;
  updateLearningDraft: (patch: Partial<LearningDraft>) => void;
  draftReady: boolean;
  draftStored: boolean;
  /** 초기 데이터를 불러오는 동안 true */
  loading: boolean;
  /** 초기 데이터 로드 실패 여부 */
  loadError: boolean;
  /** 초기 데이터를 다시 불러온다. */
  refresh: () => Promise<void>;
  robots: Robot[];
  umis: Umi[];
  trainings: Training[];
  trainingRefreshing: boolean;
  trainingRefreshError: boolean;
  trainingUpdatedAt: number | null;
  refreshTrainings: () => Promise<boolean>;
  skills: LearnedSkill[];
  savedWorks: SavedWork[];
  users: AdminUser[];
  permissions: Record<string, string[]>;
  datasets: Dataset[];
  /** 장치 등록. 중복 ID면 Error를 던진다. */
  addDevice: (input: RegisterDeviceInput) => Promise<void>;
  applyModelToRobot: (robotId: string, model: string) => Promise<void>;
  updateRobotInfo: (robotId: string, patch: { name: string; location: string }) => Promise<void>;
  cancelTrainingById: (trainingJobId: string) => Promise<void>;
  retryTraining: (training: Training) => Promise<void>;
  completeLearning: (input: { name: string; robot: string }) => Promise<void>;
  saveWorkflow: (work: SavedWork) => Promise<SavedWork>;
  saveRobotPermissions: (next: Record<string, string[]>) => Promise<void>;
}

const emptyDraft: LearningDraft = { step: 1, skillKey: 'pick_place', method: null, umiId: 'UMI-024', expectedEpisodes: 10, selected: [], progress: 12, startedAt: null, name: '' };
const DRAFT_KEY = 'ssafy-learning-draft-v1';
const AppStoreContext = createContext<AppStore | null>(null);

export function AppStoreProvider({ children }: { children: React.ReactNode }) {
  const [skills, setSkills] = useState<LearnedSkill[]>(initialLearnedSkills);
  /* 초기값은 비워두고 서비스 계층에서 불러온다 — 로딩·오류 UI는 공통 게이트(S15P21A103-94)가 담당한다. */
  const [learningDraft, setLearningDraft] = useState<LearningDraft>(emptyDraft);
  const [draftReady, setDraftReady] = useState(false);
  const [draftStored, setDraftStored] = useState(false);
  const updateLearningDraft = useCallback((patch: Partial<LearningDraft>) => setLearningDraft(current => ({ ...current, ...patch })), []);
  /* eslint-disable react-hooks/set-state-in-effect -- restore browser-local draft after hydration */
  useEffect(() => {
    try {
      const saved = JSON.parse(localStorage.getItem(DRAFT_KEY) || 'null');
      if (saved && typeof saved.skillKey === 'string' && Array.isArray(saved.selected) && saved.step >= 1 && saved.step <= 6) setLearningDraft({ ...emptyDraft, ...saved });
      const savedSkills = JSON.parse(localStorage.getItem('ssafy-learned-skills-v1') || 'null');
      if (Array.isArray(savedSkills) && savedSkills.every(item => item && typeof item.id === 'string' && typeof item.name === 'string')) setSkills(savedSkills);
    } catch { /* Invalid or unavailable storage starts a fresh draft. */ }
    setDraftReady(true);
  }, []);
  useEffect(() => {
    if (!draftReady) return;
    try { localStorage.setItem(DRAFT_KEY, JSON.stringify(learningDraft)); setDraftStored(true); }
    catch { setDraftStored(false); }
  }, [learningDraft, draftReady]);
  /* eslint-enable react-hooks/set-state-in-effect */
  const [robots, setRobots] = useState<Robot[]>([]);
  const [umis, setUmis] = useState<Umi[]>([]);
  const [datasets, setDatasets] = useState<Dataset[]>([]);
  const [trainings, setTrainings] = useState<Training[]>([]);
  const [trainingRefreshing, setTrainingRefreshing] = useState(false);
  const [trainingRefreshError, setTrainingRefreshError] = useState(false);
  const [trainingUpdatedAt, setTrainingUpdatedAt] = useState<number | null>(null);
  const [savedWorks, setSavedWorks] = useState<SavedWork[]>([]);
  const [permissions, setPermissions] = useState<Record<string, string[]>>(initialPermissions);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const [nextRobots, nextUmis, nextDatasets, nextTrainings, nextSkills, nextWorks] = await Promise.all([
        fetchRobots(),
        fetchUmis(),
        fetchDatasets(),
        fetchTrainings(),
        fetchLearnedSkills(),
        fetchSavedWorks(),
      ]);
      setRobots(nextRobots);
      setUmis(nextUmis);
      setDatasets(nextDatasets);
      setTrainings(nextTrainings);
      setSkills(nextSkills);
      setTrainingUpdatedAt(Date.now());
      setSavedWorks(nextWorks);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshTrainings = useCallback(async () => {
    setTrainingRefreshing(true);
    setTrainingRefreshError(false);
    try {
      setTrainings(await fetchTrainings());
      setTrainingUpdatedAt(Date.now());
      return true;
    } catch {
      setTrainingRefreshError(true);
      return false;
    } finally {
      setTrainingRefreshing(false);
    }
  }, []);

  /* eslint-disable react-hooks/set-state-in-effect -- mount 시 초기 로드 1회 */
  useEffect(() => {
    void refresh();
  }, [refresh]);
  /* eslint-enable react-hooks/set-state-in-effect */

  const addDevice = useCallback(async (input: RegisterDeviceInput) => {
    const next = await registerDevice(input);
    setRobots(next.robots);
    setUmis(next.umis);
  }, []);

  const applyModelToRobot = useCallback(async (robotId: string, model: string) => {
    setRobots(await applyModel(robotId, model));
  }, []);

  const updateRobotInfo = useCallback(async (robotId: string, patch: { name: string; location: string }) => {
    setRobots(await updateRobot(robotId, patch));
  }, []);

  const cancelTrainingById = useCallback(async (trainingJobId: string) => {
    setTrainings(await cancelTraining(trainingJobId));
  }, []);

  const retryFailedTraining = useCallback(async (training: Training) => {
    setTrainings(await retryTraining(training));
  }, []);

  const completeLearning = useCallback(async (input: { name: string; robot: string }) => {
    setTrainings(await addTraining(input));
    setSkills((current) => {
      const existing = current.find((skill) => skill.name === input.name);
      const learned: LearnedSkill = {
        id: existing?.id ?? `SKILL-${Date.now()}`,
        name: input.name,
        kind: 'umi',
        description: 'UMI 데이터로 새로 학습한 동작',
        mark: 'S',
      };
      const next = [learned, ...current.filter((skill) => skill.id !== learned.id)];
      try { localStorage.setItem('ssafy-learned-skills-v1', JSON.stringify(next)); } catch { /* Learning remains available in memory. */ }
      return next;
    });
  }, []);

  useEffect(() => {
    if (!draftReady || learningDraft.step !== 5 || !learningDraft.startedAt) return;
    let finishing = false;
    const startedAt = learningDraft.startedAt;
    const timer = window.setInterval(() => {
      const progress = Math.min(100, 12 + Math.floor((Date.now() - startedAt) / 650) * 4);
      if (progress < 100) { updateLearningDraft({ progress }); return; }
      if (finishing) return;
      finishing = true;
      window.clearInterval(timer);
      void completeLearning({ name: learningDraft.name, robot: learningDraft.umiId }).then(() => {
        updateLearningDraft({ progress: 100, step: 6, error: undefined });
      }).catch(() => {
        updateLearningDraft({ error: '학습 결과를 저장하지 못했어요. 다시 시도해 주세요.', startedAt: null });
      });
    }, 650);
    return () => window.clearInterval(timer);
  }, [draftReady, learningDraft.step, learningDraft.startedAt, learningDraft.name, learningDraft.umiId, completeLearning, updateLearningDraft]);

  const visibleTrainings = useMemo(() => learningDraft.step === 5 && learningDraft.name
    ? [{ id: `draft-${learningDraft.startedAt ?? 'new'}`, skillKey: learningDraft.skillKey, umiDeviceId: '', inputMode: learningDraft.method === 'existing' ? 'EXISTING_EPISODES' as const : 'COLLECT_NEW' as const, expectedEpisodeCount: learningDraft.expectedEpisodes, name: learningDraft.name, robot: learningDraft.umiId, progress: learningDraft.progress, stage: learningDraft.error || (learningDraft.progress < 35 ? '데이터 준비' : learningDraft.progress < 75 ? '동작 학습' : '결과 확인'), status: learningDraft.error ? '실패' as const : '진행 중' as const, started: '방금 전' }, ...trainings]
    : trainings, [learningDraft, trainings]);

  const saveWorkflow = useCallback(async (work: SavedWork) => {
    const result = await saveWork(work);
    setSavedWorks(result.works);
    const saved = result.works.find((item) => item.id === result.savedId);
    if (!saved) throw new Error('저장된 작업을 다시 조회하지 못했어요.');
    return saved;
  }, []);

  const saveRobotPermissions = useCallback(async (next: Record<string, string[]>) => {
    setPermissions(await savePermissions(next));
  }, []);

  const value = useMemo<AppStore>(
    () => ({
      loading,
      loadError,
      refresh,
      robots,
      umis,
      trainings: visibleTrainings,
      trainingRefreshing,
      trainingRefreshError,
      trainingUpdatedAt,
      refreshTrainings,
      learningDraft, updateLearningDraft, draftReady, draftStored,
      skills,
      savedWorks,
      users: initialUsers,
      permissions,
      datasets,
      addDevice,
      applyModelToRobot,
      updateRobotInfo,
      cancelTrainingById,
      retryTraining: retryFailedTraining,
      completeLearning,
      saveWorkflow,
      saveRobotPermissions,
    }),
    [loading, loadError, refresh, robots, umis, datasets, visibleTrainings, trainingRefreshing, trainingRefreshError, trainingUpdatedAt, refreshTrainings, learningDraft, updateLearningDraft, draftReady, draftStored, skills, savedWorks, permissions, addDevice, applyModelToRobot, updateRobotInfo, cancelTrainingById, retryFailedTraining, completeLearning, saveWorkflow, saveRobotPermissions],
  );

  return <AppStoreContext.Provider value={value}>{children}</AppStoreContext.Provider>;
}

export function useAppStore(): AppStore {
  const store = useContext(AppStoreContext);
  if (!store) throw new Error('useAppStore는 AppStoreProvider 안에서만 사용할 수 있어요.');
  return store;
}
