import type { AdminUser } from '@/types';
import { initialPermissions, initialUsers } from '@/mocks';
import { clone, delay } from './client';

let permissions: Record<string, string[]> = clone(initialPermissions);

export async function fetchUsers(): Promise<AdminUser[]> {
  await delay();
  return clone(initialUsers);
}

export async function fetchPermissions(): Promise<Record<string, string[]>> {
  await delay();
  return clone(permissions);
}

export async function savePermissions(next: Record<string, string[]>): Promise<Record<string, string[]>> {
  await delay();
  permissions = clone(next);
  return clone(permissions);
}
