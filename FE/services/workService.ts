import type { LearnedSkill, SavedWork, WorkflowBlock } from '@/types';
import { ApiError, apiRequest } from './client';

interface ApiSkill { id: string; displayName: string; description: string | null; }
interface ApiSkillVersion { id: string; skillId: string; version: number; }
interface ApiTaskStep { order: number; skillVersionId: string; }
interface ApiTaskVersion { id: string; taskId: string; version: number; steps: ApiTaskStep[]; createdAt: string; }
interface ApiTask {
  id: string;
  taskKey: string;
  displayName: string;
  description: string | null;
  latestVersion: number | null;
  latestVersionDetails: ApiTaskVersion | null;
  updatedAt: string;
}

export async function fetchLearnedSkills(): Promise<LearnedSkill[]> {
  const skills = await apiRequest<ApiSkill[]>('/skills');
  const resolved = await Promise.all(skills.map(async (skill) => {
    try {
      const version = await apiRequest<ApiSkillVersion>(`/skills/${skill.id}/versions/latest`);
      return {
        id: version.id,
        skillId: skill.id,
        name: skill.displayName,
        kind: 'umi' as const,
        description: skill.description ?? '',
        mark: 'S',
      };
    } catch (cause) {
      if (cause instanceof ApiError && cause.status === 404) return null;
      throw cause;
    }
  }));
  return resolved.filter((skill): skill is LearnedSkill => skill !== null);
}

export async function fetchSavedWorks(): Promise<SavedWork[]> {
  const [tasks, skills] = await Promise.all([
    apiRequest<ApiTask[]>('/tasks?includeLatestVersion=true'),
    fetchLearnedSkills(),
  ]);
  const skillByVersionId = new Map(skills.map((skill) => [skill.id, skill]));
  return tasks.map((task) => toSavedWork(task, skillByVersionId));
}

export async function saveWork(work: SavedWork): Promise<SavedWork[]> {
  let taskId = work.id;
  if (work.id.startsWith('draft-')) {
    const created = await apiRequest<ApiTask>('/tasks', {
      method: 'POST',
      body: JSON.stringify({ taskKey: `web_${Date.now()}`, displayName: work.name, description: null }),
    });
    taskId = created.id;
  } else {
    await apiRequest<ApiTask>(`/tasks/${taskId}`, {
      method: 'PATCH',
      body: JSON.stringify({ displayName: work.name, description: null }),
    });
  }

  const skillBlocks = flatten(work.blocks);
  await apiRequest<ApiTaskVersion>(`/tasks/${taskId}/versions`, {
    method: 'POST',
    headers: { 'Idempotency-Key': crypto.randomUUID() },
    body: JSON.stringify({
      steps: skillBlocks.map((block, index) => ({
        order: index + 1,
        skillVersionId: block.skillId,
        parameters: {},
        timeoutSeconds: 30,
      })),
    }),
  });
  return fetchSavedWorks();
}

function flatten(blocks: WorkflowBlock[]): WorkflowBlock[] {
  return blocks.flatMap((block) => [block, ...flatten(block.children ?? [])]);
}

function toSavedWork(task: ApiTask, skills: Map<string, LearnedSkill>): SavedWork {
  const version = task.latestVersionDetails;
  const blocks = (version?.steps ?? []).map((step, index) => {
    const skill = skills.get(step.skillVersionId);
    return {
      id: index + 1,
      kind: 'umi' as const,
      title: skill?.name ?? '사용할 수 없는 학습 동작',
      skillId: step.skillVersionId,
    };
  });
  return {
    id: task.id,
    taskKey: task.taskKey,
    taskVersionId: version?.id ?? null,
    version: version?.version ?? task.latestVersion,
    name: task.displayName,
    robotId: '',
    blocks,
    estimatedSeconds: blocks.length * 25,
    updatedAt: task.updatedAt,
  };
}
