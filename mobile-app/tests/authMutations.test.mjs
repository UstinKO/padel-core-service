import assert from 'node:assert/strict';
import test from 'node:test';
import { createAuthClient } from '../src/auth.ts';
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } });
const token = { accessToken: 'test-access', refreshToken: 'test-refresh', expiresIn: 60000 };
const player = { id: 4, email: 'test@example.test', nombreCompleto: 'Test Player' };
function fixture(reply) {
  let saved = null; const calls = [];
  const client = createAuthClient({ store: { get: async () => saved, set: async value => { saved = value; } }, fetchImpl: async (url, init) => {
    calls.push({ url, init });
    if (url.endsWith('/login') || url.endsWith('/refresh')) return json(token);
    if (url.endsWith('/me')) return json(player);
    return reply(url, init);
  } });
  return { client, calls };
}
test('authenticated registration sends one empty POST over HTTPS with no cookies or player ID', async () => {
  const f = fixture(() => json({ success: true, status: 'CONFIRMED' })); await f.client.login(player.email, 'test-only-password');
  await f.client.registerIndividual(42);
  const call = f.calls.at(-1); assert.equal(call.url, 'https://1-padel.com/players/tournaments/42/register');
  assert.equal(call.init.method, 'POST'); assert.equal(call.init.body, undefined); assert.equal(call.init.credentials, 'omit');
  assert.equal(call.init.headers.Authorization, 'Bearer test-access');
  assert.equal(f.calls.filter(call => call.init.method === 'POST' && call.url.endsWith('/register')).length, 1);
});
test('business flags and rejection text survive JSON 400 and success=false', async () => {
  for (const status of [200, 400]) {
    const f = fixture(() => json({ success: false, message: 'Set your level', needLevel: true }, status)); await f.client.login(player.email, 'test-only-password');
    await assert.rejects(f.client.registerIndividual(42), error => error.code === 'REJECTED' && error.needLevel && error.detail === 'Set your level');
  }
});
test('authenticated cancellation sends one empty POST over HTTPS with no cookies or player ID', async () => {
  const f = fixture(() => json({ success: true, message: 'Cancelled' })); await f.client.login(player.email, 'test-only-password');
  await f.client.cancelIndividual(42);
  const call = f.calls.at(-1); assert.equal(call.url, 'https://1-padel.com/players/tournaments/42/cancel');
  assert.equal(call.init.method, 'POST'); assert.equal(call.init.body, undefined); assert.equal(call.init.credentials, 'omit');
  assert.equal(call.init.headers.Authorization, 'Bearer test-access');
  assert.equal(f.calls.filter(call => call.init.method === 'POST' && call.url.endsWith('/cancel')).length, 1);
});
test('cancellation rejection text (deadline passed) survives JSON 400', async () => {
  const f = fixture(() => json({ success: false, message: 'Cannot cancel registration after deadline' }, 400)); await f.client.login(player.email, 'test-only-password');
  await assert.rejects(f.client.cancelIndividual(42), error => error.code === 'REJECTED' && error.detail === 'Cannot cancel registration after deadline');
});
test('401 on cancellation renews tokens but does not replay the mutation', async () => {
  const f = fixture(() => json({}, 401)); await f.client.login(player.email, 'test-only-password');
  await assert.rejects(f.client.cancelIndividual(42), { code: 'REVIEW_REQUIRED' });
  assert.equal(f.calls.filter(call => call.url.endsWith('/cancel')).length, 1);
});
test('401 renews tokens but does not replay a mutation; 403 does not falsely classify player as organizer', async () => {
  for (const status of [401, 403]) {
    const f = fixture(() => json({}, status)); await f.client.login(player.email, 'test-only-password');
    await assert.rejects(f.client.registerIndividual(42), { code: status === 401 ? 'REVIEW_REQUIRED' : 'FORBIDDEN' });
    assert.equal(f.calls.filter(call => call.url.endsWith('/register')).length, 1);
  }
});
test('contact PATCH uses the same private session and does not include user ID', async () => {
  const f = fixture(() => json({ success: true })); await f.client.login(player.email, 'test-only-password');
  await f.client.saveContact({ telegramUsername: '@test_person' }); const call = f.calls.at(-1);
  assert.equal(call.init.method, 'PATCH'); assert.equal(call.init.credentials, 'omit'); assert.deepEqual(JSON.parse(call.init.body), { telegramUsername: '@test_person' });
});
