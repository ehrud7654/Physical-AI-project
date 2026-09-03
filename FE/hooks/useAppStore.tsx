'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { AdminUser, LearnedSkill, Robot, SavedWork, Training, Umi } from '@/types';
import { initialDatasets, initialLearnedSkills, initialPermissions, initialUsers } from '@/mocks';
import {
  addTraining,
  applyModel,
  cancelTraining,
  fetchRobots,
  fetchSavedWorks,
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
  /** 초기 데이터를 불러오는 동안 true */
  loading: boolean;
  /** 초기 데이터 로드 실패 여부 */
  loadError: boolean;
  /** 초기 데이터를 다시 불러온다. */
  refresh: () => Promise<void>;
  robots: Robot[];
  umis: Umi[];
  trainings: Training[];
  skills: LearnedSkill[];
  savedWorks: SavedWork[];
  users: AdminUser[];
  permissions: Record<string, string[]>;
  datasets: typeof initialDatasets;
  /** 장치 등록. 중복 ID면 Error를 던진다. */
  addDevice: (input: RegisterDeviceInput) => Promise<void>;
  applyModelToRobot: (robotId: string, model: string) => Promise<void>;
  updateRobotInfo: (robotId: string, patch: { name: string; location: string }) => Promise<void>;
  cancelTrainingByName: (name: string) => Promise<void>;
  retryTrainingByName: (name: string) => Promise<void>;
  completeLearning: (input: { name: string; robot: string }) => Promise<void>;
  saveWorkflow: (work: SavedWork) => Promise<void>;
  saveRobotPermissions: (next: Record<string, string[]>) => Promise<void>;
}

const AppStoreContext = createContext<AppStore | null>(null);

export function AppStoreProvider({ children }: { children: React.ReactNode }) {
  /* 초기값은 비워두고 서비스 계층에서 불러온다 — 로딩·오류 UI는 공통 게이트(S15P21A103-94)가 담당한다. */
  const [robots, setRobots] = useState<Robot[]>([]);
  const [umis, setUmis] = useState<Umi[]>([]);
  const [trainings, setTrainings] = useState<Training[]>([]);
  const [skills, setSkills] = useState<LearnedSkill[]>(initialLearnedSkills);
  const [savedWorks, setSavedWorks] = useState<SavedWork[]>([]);
  const [permissions, setPermissions] = useState<Record<string, string[]>>(initialPermissions);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  const refresh = useCallback(async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const [nextRobots, nextUmis, nextTrainings, nextWorks] = await Promise.all([
        fetchRobots(),
        fetchUmis(),
        fetchTrainings(),
        fetchSavedWorks(),
      ]);
      setRobots(nextRobots);
      setUmis(nextUmis);
      setTrainings(nextTrainings);
      setSavedWorks(nextWorks);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
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

  const cancelTrainingByName = useCallback(async (name: string) => {
    setTrainings(await cancelTraining(name));
  }, []);

  const retryTrainingByName = useCallback(async (name: string) => {
    setTrainings(await retryTraining(name));
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
      return [learned, ...current.filter((skill) => skill.id !== learned.id)];
    });
  }, []);

  const saveWorkflow = useCallback(async (work: SavedWork) => {
    setSavedWorks(await saveWork(work));
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
      trainings,
      skills,
      savedWorks,
      users: initialUsers,
      permissions,
      datasets: initialDatasets,
      addDevice,
      applyModelToRobot,
      updateRobotInfo,
      cancelTrainingByName,
      retryTrainingByName,
      completeLearning,
      saveWorkflow,
      saveRobotPermissions,
    }),
    [loading, loadError, refresh, robots, umis, trainings, skills, savedWorks, permissions, addDevice, applyModelToRobot, updateRobotInfo, cancelTrainingByName, retryTrainingByName, completeLearning, saveWorkflow, saveRobotPermissions],
  );

  return <AppStoreContext.Provider value={value}>{children}</AppStoreContext.Provider>;
}

export function useAppStore(): AppStore {
  const store = useContext(AppStoreContext);
  if (!store) throw new Error('useAppStore는 AppStoreProvider 안에서만 사용할 수 있어요.');
  return store;
}
