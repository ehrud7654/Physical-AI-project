import type { DeviceType, Robot, Umi } from '@/types';
import { apiRequest } from './client';

type ApiDeviceType = 'UMI_CAMERA' | 'JETSON';

interface ApiDevice {
  id: string;
  ownerId: string;
  deviceType: ApiDeviceType;
  handleNumber: string;
  name: string;
  location: string | null;
  connectionStatus: 'ONLINE' | 'OFFLINE';
  lastSeenAt: string | null;
  createdAt: string;
}

export interface RegisterDeviceInput {
  type: DeviceType;
  id: string;
  name: string;
  location: string;
}

export async function fetchRobots(): Promise<Robot[]> {
  const devices = await apiRequest<ApiDevice[]>('/devices?deviceType=JETSON');
  return devices.map(toRobot);
}

export async function fetchUmis(): Promise<Umi[]> {
  const devices = await apiRequest<ApiDevice[]>('/devices?deviceType=UMI_CAMERA');
  return devices.map(toUmi);
}

export async function registerDevice(
  input: RegisterDeviceInput,
): Promise<{ robots: Robot[]; umis: Umi[] }> {
  await apiRequest<ApiDevice>('/devices', {
    method: 'POST',
    body: JSON.stringify({
      deviceType: toApiDeviceType(input.type),
      handleNumber: input.id.trim(),
      name: input.name.trim(),
      location: input.location.trim(),
    }),
  });
  const [robots, umis] = await Promise.all([fetchRobots(), fetchUmis()]);
  return { robots, umis };
}

/** 모델 배포 API 연동 전까지 서버의 최신 로봇 목록을 유지한다. */
export async function applyModel(robotId: string, model: string): Promise<Robot[]> {
  void robotId;
  void model;
  return fetchRobots();
}

/** devices.id를 사용해 Jetson 장치의 이름과 설치 위치를 수정한다. */
export async function updateRobot(
  deviceId: string,
  patch: { name: string; location: string },
): Promise<Robot[]> {
  await apiRequest<ApiDevice>(`/devices/${deviceId}`, {
    method: 'PATCH',
    body: JSON.stringify({ name: patch.name.trim(), location: patch.location.trim() }),
  });
  return fetchRobots();
}

function toApiDeviceType(type: DeviceType): ApiDeviceType {
  return type === 'robot' ? 'JETSON' : 'UMI_CAMERA';
}

function toRobot(device: ApiDevice): Robot {
  return {
    id: device.id,
    name: device.name,
    location: device.location ?? '위치 미지정',
    status: device.connectionStatus === 'ONLINE' ? '온라인' : '오프라인',
    model: '배포 정보 없음',
  };
}

function toUmi(device: ApiDevice): Umi {
  return {
    id: device.handleNumber,
    place: device.location ?? '위치 미지정',
    status: device.connectionStatus === 'ONLINE' ? '온라인' : '오프라인',
    collected: device.lastSeenAt ? formatLastSeen(device.lastSeenAt) : '사용 기록 없음',
    data: 0,
  };
}

function formatLastSeen(value: string): string {
  return new Intl.DateTimeFormat('ko-KR', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value));
}
