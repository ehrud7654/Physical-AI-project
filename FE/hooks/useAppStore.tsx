'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { AdminUser, Robot, Training, Umi } from '@/types';
import {
  initialDatasets,
  initialPermissions,
  initialRobots,
  initialSavedWorks,
  initialTrainings,
  initialUmis,
  initialUsers,
} from '@/mocks';
import {
  cancelTraining,
  fetchRobots,
  fetchSavedWorks,
  fetchTrainings,
  fetchUmis,
  registerDevice,
  retryTraining,
  savePermissions,
  saveWork,
  type RegisterDeviceInput,
  type SavedWork,
} from '@/services';

interface AppStore {
  robots: Robot[];
  umis: Umi[];
  trainings: Training[];
  savedWorks: SavedWork[];
  users: AdminUser[];
  permissions: Record<string, string[]>;
  datasets: typeof initialDatasets;
  /** 장치 등록. 중복 ID면 Error를 던진다. */
  addDevice: (input: RegisterDeviceInput) => Promise<void>;
  cancelTrainingByName: (name: string) => Promise<void>;
  retryTrainingByName: (name: string) => Promise<void>;
  saveWorkflow: (work: SavedWork) => Promise<void>;
  saveRobotPermissions: (next: Record<string, string[]>) => Promise<void>;
}

const AppStoreContext = createContext<AppStore | null>(null);

export function AppStoreProvider({ children }: { children: React.ReactNode }) {
  /* 초기값은 Mock 시드로 즉시 렌더하고, mount 후 서비스 계층과 동기화한다.
     백엔드 연동 시 초기값을 비우고 공통 로딩 UI(S15P21A103-94)로 대체한다. */
  const [robots, setRobots] = useState<Robot[]>(initialRobots);
  const [umis, setUmis] = useState<Umi[]>(initialUmis);
  const [trainings, setTrainings] = useState<Training[]>(initialTrainings);
  const [savedWorks, setSavedWorks] = useState<SavedWork[]>(initialSavedWorks);
  const [permissions, setPermissions] = useState<Record<string, string[]>>(initialPermissions);

  useEffect(() => {
    let alive = true;
    Promise.all([fetchRobots(), fetchUmis(), fetchTrainings(), fetchSavedWorks()]).then(
      ([nextRobots, nextUmis, nextTrainings, nextWorks]) => {
        if (!alive) return;
        setRobots(nextRobots);
        setUmis(nextUmis);
        setTrainings(nextTrainings);
        setSavedWorks(nextWorks);
      },
    );
    return () => {
      alive = false;
    };
  }, []);

  const addDevice = useCallback(async (input: RegisterDeviceInput) => {
    const next = await registerDevice(input);
    setRobots(next.robots);
    setUmis(next.umis);
  }, []);

  const cancelTrainingByName = useCallback(async (name: string) => {
    setTrainings(await cancelTraining(name));
  }, []);

  const retryTrainingByName = useCallback(async (name: string) => {
    setTrainings(await retryTraining(name));
  }, []);

  const saveWorkflow = useCallback(async (work: SavedWork) => {
    setSavedWorks(await saveWork(work));
  }, []);

  const saveRobotPermissions = useCallback(async (next: Record<string, string[]>) => {
    setPermissions(await savePermissions(next));
  }, []);

  const value = useMemo<AppStore>(
    () => ({
      robots,
      umis,
      trainings,
      savedWorks,
      users: initialUsers,
      permissions,
      datasets: initialDatasets,
      addDevice,
      cancelTrainingByName,
      retryTrainingByName,
      saveWorkflow,
      saveRobotPermissions,
    }),
    [robots, umis, trainings, savedWorks, permissions, addDevice, cancelTrainingByName, retryTrainingByName, saveWorkflow, saveRobotPermissions],
  );

  return <AppStoreContext.Provider value={value}>{children}</AppStoreContext.Provider>;
}

export function useAppStore(): AppStore {
  const store = useContext(AppStoreContext);
  if (!store) throw new Error('useAppStore는 AppStoreProvider 안에서만 사용할 수 있어요.');
  return store;
}
