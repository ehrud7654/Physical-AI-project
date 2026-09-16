'use client';

import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import type { AuthenticatedUser } from '@/types';
import { logout as clearAuthentication } from '@/services';
import { SESSION_EXPIRED_EVENT } from '@/services/client';
import { useAppStore } from '@/hooks/useAppStore';

interface AuthState {
  authenticated: boolean;
  currentUser: AuthenticatedUser | null;
  currentUserId: string;
  sessionExpiredMessage: string;
  login: (user: AuthenticatedUser) => void;
  logout: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

/* 액세스 토큰은 services/client 메모리에만 있으므로 새로고침 시 인증이 풀린다.
   BE refresh 엔드포인트가 준비되면 여기서 부팅 시 재발급을 시도한다. */
export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const { resetLearningDraft } = useAppStore();
  const [currentUser, setCurrentUser] = useState<AuthenticatedUser | null>(null);
  const [authenticated, setAuthenticated] = useState(false);
  const [sessionExpiredMessage, setSessionExpiredMessage] = useState('');

  const login = useCallback((user: AuthenticatedUser) => {
    setCurrentUser(user);
    setAuthenticated(true);
    setSessionExpiredMessage('');
  }, []);

  const logout = useCallback(() => {
    clearAuthentication();
    setCurrentUser(null);
    setAuthenticated(false);
    setSessionExpiredMessage('');
    router.push('/');
  }, [router]);

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
    () => ({ authenticated, currentUser, currentUserId: currentUser?.name ?? 'admin01', sessionExpiredMessage, login, logout }),
    [authenticated, currentUser, sessionExpiredMessage, login, logout],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const auth = useContext(AuthContext);
  if (!auth) throw new Error('useAuth는 AuthProvider 안에서만 사용할 수 있어요.');
  return auth;
}
