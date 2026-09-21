import assert from 'node:assert/strict';
import { beforeEach, test } from 'node:test';
import { clearAuthentication, login, logout, restoreAuthentication, checkEmailAvailability, signup } from '../services/authService';

const stored = new Map<string, string>();
const user = { id: 'test-user', email: 'test@example.com', name: 'Test' };

test('invalid email does not send availability or signup requests', async () => {
  globalThis.fetch = async () => { throw new Error('Unexpected network request'); };
  for (const email of ['plain-id', 'user@localhost', 'a b@example.com', '']) {
    await assert.rejects(checkEmailAvailability(email), /이메일/);
    await assert.rejects(signup('Test', email, 'password'), /이메일/);
  }
});

test('availability normalizes and encodes email, without caching', async () => {
  globalThis.fetch = async (url, init) => {
    assert.ok(String(url).endsWith('/auth/email-availability?email=test%2Btag%40example.com'));
    assert.equal(init?.cache, 'no-store');
    return Response.json({ data: { email: 'test+tag@example.com', available: true } });
  };
  assert.equal((await checkEmailAvailability(' Test+Tag@Example.com ')).available, true);
});

test('signup sends actual credentials and surfaces duplicate conflicts', async () => {
  globalThis.fetch = async (url, init) => {
    assert.ok(String(url).endsWith('/auth/signup'));
    assert.equal(init?.method, 'POST');
    assert.deepEqual(JSON.parse(String(init?.body)), { name: 'Test', email: user.email, password: 'password' });
    return Response.json({ data: user }, { status: 201 });
  };
  assert.deepEqual(await signup(' Test ', ' TEST@EXAMPLE.COM ', 'password'), user);
  assert.equal(restoreAuthentication(), null);
  globalThis.fetch = async () => Response.json({ code: 'EMAIL_ALREADY_EXISTS', message: '이미 사용 중인 이메일입니다.' }, { status: 409 });
  await assert.rejects(signup('Test', user.email, 'password'), /이미 사용/);
});

beforeEach(() => {
  stored.clear();
  Object.defineProperty(globalThis, 'localStorage', { configurable: true, value: {
    getItem: (key: string) => stored.get(key) ?? null,
    setItem: (key: string, value: string) => stored.set(key, value),
    removeItem: (key: string) => stored.delete(key),
  } });
  Object.defineProperty(globalThis, 'window', { configurable: true, value: new EventTarget() });
  clearAuthentication();
});

async function signIn() {
  globalThis.fetch = async () => Response.json({ data: { accessToken: 'test-token', expiresIn: 900, user } });
  await login(user.email, 'password');
}

test('logout sends the existing token before clearing local authentication', async () => {
  await signIn();
  globalThis.fetch = async (url, init) => {
    assert.ok(String(url).endsWith('/auth/logout'));
    assert.equal(init?.method, 'POST');
    assert.equal(new Headers(init?.headers).get('Authorization'), 'Bearer test-token');
    assert.ok(stored.has('ssafy:auth-session'));
    return new Response(null, { status: 204 });
  };
  await logout();
  assert.equal(restoreAuthentication(), null);
  assert.equal(stored.size, 0);
});

test('Redis/server failure preserves authentication so logout can be retried', async () => {
  await signIn();
  globalThis.fetch = async () => Response.json({ code: 'AUTH_SESSION_UNAVAILABLE' }, { status: 503 });
  await assert.rejects(logout());
  assert.deepEqual(restoreAuthentication(), user);
});

test('network failure preserves authentication and does not report logout success', async () => {
  await signIn();
  globalThis.fetch = async () => { throw new TypeError('Network unavailable'); };
  await assert.rejects(logout(), /Network unavailable/);
  assert.deepEqual(restoreAuthentication(), user);
});

test('local expiry cleanup never calls the logout endpoint', async () => {
  await signIn();
  globalThis.fetch = async () => { throw new Error('Unexpected network request'); };
  clearAuthentication();
  assert.equal(restoreAuthentication(), null);
});
