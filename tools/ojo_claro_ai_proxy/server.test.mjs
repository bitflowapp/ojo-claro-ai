import assert from 'node:assert/strict';
import test from 'node:test';
import { applyLocalProxyEnv, createProxyApp } from './server.mjs';

test('health reports missing api key cleanly', async () => {
  const app = createProxyApp({ env: { OPENAI_MODEL: 'gpt-5.4-mini' } });
  const response = await app(new Request('http://localhost/health'));
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.ok, true);
  assert.equal(body.model, 'gpt-5.4-mini');
  assert.equal(body.hasApiKey, false);
});

test('local env overrides global windows config for proxy settings', async () => {
  const targetEnv = {
    OPENAI_MODEL: 'openai-codex/gpt-5.3-codex',
    PORT: '9000',
    MAX_INPUT_CHARS: '999',
    MAX_MEMORY_CHARS: '111',
    REQUEST_TIMEOUT_MS: '222',
    OPENAI_API_KEY: 'keep-secret'
  };

  applyLocalProxyEnv(
    targetEnv,
    [
      'OPENAI_MODEL=gpt-5.4-mini',
      'PORT=8787',
      'MAX_INPUT_CHARS=1200',
      'MAX_MEMORY_CHARS=800',
      'REQUEST_TIMEOUT_MS=12000',
      'OPENAI_API_KEY=local-key'
    ].join('\n')
  );

  const app = createProxyApp({ env: targetEnv });
  const response = await app(new Request('http://localhost/health'));
  const body = await response.json();

  assert.equal(body.model, 'gpt-5.4-mini');
  assert.equal(body.hasApiKey, true);
  assert.equal(targetEnv.OPENAI_MODEL, 'gpt-5.4-mini');
  assert.equal(targetEnv.PORT, '8787');
  assert.equal(targetEnv.MAX_INPUT_CHARS, '1200');
  assert.equal(targetEnv.MAX_MEMORY_CHARS, '800');
  assert.equal(targetEnv.REQUEST_TIMEOUT_MS, '12000');
});

test('interpret without api key returns controlled fallback', async () => {
  const app = createProxyApp({ env: {} });
  const response = await app(new Request('http://localhost/v1/interpret', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      originalText: 'decile a Sofi que llego tarde',
      normalizedText: 'decir a Sofi que llego tarde',
      locale: 'es-AR',
      agentState: 'WAITING_MESSAGE',
      externalApp: null,
      memorySummary: '',
      knownSafeContacts: [],
      knownPlaces: [],
      activePendingTasks: [],
      allowedIntents: ['COMPOSE_WHATSAPP_MESSAGE'],
      forbiddenActions: []
    })
  }));
  const body = await response.json();

  assert.equal(response.status, 503);
  assert.equal(body.intent, null);
  assert.equal(body.shouldExecuteImmediately, false);
  assert.match(body.safetyNotes, /OPENAI_API_KEY/i);
});
