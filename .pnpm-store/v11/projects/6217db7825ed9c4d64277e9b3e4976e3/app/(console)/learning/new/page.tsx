'use client';

import { useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { LearnView } from '@/components/views/LearnView';
import { useAppStore } from '@/hooks/useAppStore';
import { useToast } from '@/hooks/useToast';
import { viewToPath } from '@/lib/routes';

export default function NewLearningPage() {
  const router = useRouter();
  const { showToast } = useToast();
  const { learningDraft, resetLearningDraft } = useAppStore();

  /* 학습 시작 전(step<5)에 화면을 떠나면 미완성 draft를 버린다 — 예전 navigateTo/onHome/logout 가드를 route 이탈로 통일. */
  const stepRef = useRef(learningDraft.step);
  useEffect(() => {
    stepRef.current = learningDraft.step;
  }, [learningDraft.step]);
  useEffect(() => () => {
    if (stepRef.current < 5) resetLearningDraft();
  }, [resetLearningDraft]);

  return <LearnView onNavigate={(view) => router.push(viewToPath(view))} onToast={showToast} />;
}
