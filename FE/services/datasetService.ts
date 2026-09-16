import type { BlockingReason, Dataset, DatasetStatus, EpisodePreviewSession, RecoveryAction } from '@/types';
import { apiRequest, delay } from './client';

/** EPISODE-01/02 응답(API 명세 1.2.0 `Episode`) 중 데이터 탭이 쓰는 필드만 선언한다. */
interface ApiEpisode {
  id: string;
  umiDeviceId: string;
  umiDeviceHandleNumber: string | null;
  skillId: string;
  skillKey: string | null;
  skillDisplayName: string | null;
  status: 'UPLOADING' | 'VALIDATING' | 'READY' | 'INVALID' | 'DELETING' | 'DELETED' | 'DELETE_FAILED';
  startedAt: string;
  endedAt: string;
  retentionExpiresAt: string;
  archive: { sizeBytes: number } | null;
  trainable: boolean;
  blockingReasons: BlockingReason[];
  recoveryAction: RecoveryAction;
  error: { code: string; message: string; recoveryAction: RecoveryAction } | null;
  createdAt: string;
}

const TONES = ['orange', 'blue', 'green', 'purple'];

/** EPISODE-01: 내 Episode 전체. 서버는 최신순으로 주므로 표시 순번은 오래된 순으로 매긴다. */
export async function fetchDatasets(): Promise<Dataset[]> {
  const episodes = await apiRequest<ApiEpisode[]>('/episodes');
  const total = episodes.length;
  return episodes.map((episode, index) => toDataset(episode, total - index));
}

/** EPISODE-08은 아직 서버에 없다. 미리보기 URL은 짧게 만료되므로 화면을 열 때마다 발급하는 계약만 흉내 낸다. */
export async function createEpisodePreviewSession(episodeId: string): Promise<EpisodePreviewSession> {
  await delay();
  return {
    episodeId,
    status: 'READY',
    previewType: 'FRAME_SEQUENCE',
    manifestUrl: `/api/v1/episode-previews/${episodeId}/manifest`,
    thumbnailUrl: `/api/v1/episode-previews/${episodeId}/thumbnail`,
    expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
  };
}

function toDataset(episode: ApiEpisode, seq: number): Dataset {
  const status: DatasetStatus = episode.status;
  return {
    id: episode.id,
    seq,
    date: formatDate(episode.startedAt),
    startedAt: episode.startedAt,
    duration: formatDuration(Date.parse(episode.endedAt) - Date.parse(episode.startedAt)),
    size: episode.archive ? formatBytes(episode.archive.sizeBytes) : '크기 확인 중',
    tone: TONES[Math.abs(hash(episode.id)) % TONES.length],
    skillKey: episode.skillKey ?? '',
    skillName: episode.skillDisplayName ?? episode.skillKey ?? '알 수 없는 동작',
    umiId: episode.umiDeviceHandleNumber ?? episode.umiDeviceId,
    ready: status === 'READY',
    status,
    validationErrors: status === 'INVALID' ? episode.blockingReasons.map((reason) => reason.message) : undefined,
    retentionDays: Math.max(0, Math.ceil((Date.parse(episode.retentionExpiresAt) - Date.now()) / 86_400_000)),
    retentionExpiresAt: episode.retentionExpiresAt,
    trainable: episode.trainable,
    blockingReasons: episode.blockingReasons,
    recoveryAction: episode.recoveryAction,
  };
}

function formatDate(iso: string): string {
  const d = new Date(iso);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}. ${pad(d.getMonth() + 1)}. ${pad(d.getDate())}  ${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

function formatDuration(ms: number): string {
  const total = Math.max(0, Math.round(ms / 1000));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
}

function formatBytes(bytes: number): string {
  if (bytes >= 1_073_741_824) return `${(bytes / 1_073_741_824).toFixed(1)} GB`;
  if (bytes >= 1_048_576) return `${Math.round(bytes / 1_048_576)} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function hash(value: string): number {
  let h = 0;
  for (let i = 0; i < value.length; i++) h = (h * 31 + value.charCodeAt(i)) | 0;
  return h;
}
