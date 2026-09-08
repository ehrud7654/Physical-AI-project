import type { Dataset, EpisodePreviewSession } from '@/types';
import { initialDatasets } from '@/mocks';
import { clone, delay } from './client';

/** 장치별 데이터 목록을 조회한다. 실제 API 연결 시 이 함수의 구현만 교체한다. */
export async function fetchDatasets(): Promise<Dataset[]> {
  await delay();
  return clone(initialDatasets);
}

/** EPISODE-08: 미리보기 URL은 짧게 만료되므로 화면을 열 때마다 발급한다. */
export async function createEpisodePreviewSession(episodeId: number): Promise<EpisodePreviewSession> {
  await delay();
  const episode = initialDatasets.find((item) => item.id === episodeId);
  if (!episode || episode.status !== 'READY') throw new Error('미리보기를 준비할 수 없는 데이터예요.');
  return {
    episodeId: `episode_${episodeId}`,
    status: 'READY',
    previewType: 'FRAME_SEQUENCE',
    manifestUrl: `/api/v1/episode-previews/preview_${episodeId}/manifest`,
    thumbnailUrl: `/api/v1/episode-previews/preview_${episodeId}/thumbnail`,
    expiresAt: new Date(Date.now() + 5 * 60_000).toISOString(),
  };
}

