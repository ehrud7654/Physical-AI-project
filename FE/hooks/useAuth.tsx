'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { AuthenticatedUser } from '@/types';
import { logout as serverLogout, clearAuthentication, restoreAuthentication } from '@/services';
import { ApiError, SESSION_EXPIRED_EVENT } from '@/services/client';
import { useAppStore } from '@/hooks/useAppStore';
import { useToast } from '@/hooks/useToast';

interface AuthState {
  authenticated: boolean;
  initializing: boolean;
  currentUser: AuthenticatedUser | null;
  currentUserId: string;
  sessionExpiredMessage: string;
  login: (user: AuthenticatedUser) => void;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { resetLearningDraft } = useAppStore();
  const { showToast } = useToast();
  const [currentUser, setCurrentUser] = useState<AuthenticatedUser | null>(null);
  const [authenticated, setAuthenticated] = useState(false);
  const [initializing, setInitializing] = useState(true);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState('');

  /* eslint-disable react-hooks/set-state-in-effect -- mount 시 저장된 세션을 1회 복원한다. */
  useEffect(() => {
    const restoredUser = restoreAuthentication();
    if (restoredUser) {
      setCurrentUser(restoredUser);
      setAuthenticated(true);
    }
    setInitializing(false);
  }, []);
  /* eslint-enable react-hooks/set-state-in-effect */

  const login = useCallback((user: AuthenticatedUser) => {
    setCurrentUser(user);
    setAuthenticated(true);
    setSessionExpiredMessage('');
  }, []);

  const logout = useCallback(async () => {
    try {
      await serverLogout();
    } catch (error) {
      if (!(error instanceof ApiError && error.status === 401)) {
        showToast('서버 로그아웃에 실패했습니다. 로그인 상태를 유지합니다. 다시 시도해 주세요.');
      }
      return;
    }
    resetLearningDraft();
    setCurrentUser(null);
    setAuthenticated(false);
    setSessionExpiredMessage('');
    router.push('/');
    showToast('로그아웃되었습니다.');
  }, [resetLearningDraft, router, showToast]);

  useEffect(() => {
    const handleSessionExpired = () => {
      clearAuthentication();
      resetLearningDraft();
      setCurrentUser(null);
      setAuthenticated(false);
      setSessionExpiredMessage('로그인 시간이 만료되어 자동으로 로그아웃됐어요. 다시 로그인해 주세요.');
      router.replace('/');
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, handleSessionExpired);
  }, [resetLearningDraft, router]);

  const value = useMemo<AuthState>(
    () => ({ authenticated, initializing, currentUser, currentUserId: currentUser?.name ?? 'admin01', sessionExpiredMessage, login, logout }),
    [authenticated, initializing, currentUser, sessionExpiredMessage, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있어요.');
  return auth;
}
