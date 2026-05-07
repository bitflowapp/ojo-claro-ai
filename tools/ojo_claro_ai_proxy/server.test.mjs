import assert from 'node:assert/strict';
import test from 'node:test';
import { createProxyApp } from './server.mjs';

test('health reports missing api key cleanly', async () => {
  const app = createProxyApp({ env: { OPENAI_MODEL: 'gpt-5.4-mini' } });
  const response = await app(new Request('http://localhost/health'));
  const body = await response.json();

  assert.equal(response.status, 200);
  assert.equal(body.ok, true);
  assert.equal(body.model, 'gpt-5.4-mini');
  assert.equal(body.hasApiKey, false);
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

