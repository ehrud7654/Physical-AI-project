'use client';

import { useParams, useRouter } from 'next/navigation';
import { WorkDetailView } from '@/components/views/WorkDetailView';
import { useToast } from '@/hooks/useToast';

export default function WorkDetailPage() {
  const router = useRouter();
  const { showToast } = useToast();
  const { workId: rawId } = useParams<{ workId: string }>();
  const workId = decodeURIComponent(rawId);
  return <WorkDetailView
    workId={workId}
    onBack={() => router.push('/work')}
    onEdit={(id) => router.push(`/work/${encodeURIComponent(id)}/edit`)}
    onToast={showToast}
  />;
}
