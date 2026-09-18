'use client';

import { AppStoreProvider } from '@/hooks/useAppStore';
import { AuthProvider } from '@/hooks/useAuth';
import { ToastProvider } from '@/hooks/useToast';

/* 순서 고정: AuthProvider가 useAppStore(resetLearningDraft)에 의존한다. */
export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <AppStoreProvider>
      <AuthProvider>
        <ToastProvider>{children}</ToastProvider>
      </AuthProvider>
    </AppStoreProvider>
  );
}
