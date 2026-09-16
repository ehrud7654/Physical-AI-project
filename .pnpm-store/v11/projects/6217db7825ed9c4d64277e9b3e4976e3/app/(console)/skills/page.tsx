'use client';

import { SkillListView } from '@/components/views/SkillListView';
import { useToast } from '@/hooks/useToast';

export default function SkillsPage() {
  const { showToast } = useToast();
  return <SkillListView onToast={showToast} />;
}
