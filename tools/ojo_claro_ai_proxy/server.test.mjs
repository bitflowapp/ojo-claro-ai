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
  assert.equal(body.host, '127.0.0.1');
  assert.equal(body.port, 8787);
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

test('host from env wins and health reports host and port', async () => {
  const app = createProxyApp({
    env: {
      HOST: '0.0.0.0',
      PORT: '8788',
      OPENAI_API_KEY: 'test-key',
      OPENAI_MODEL: 'gpt-5.4-mini'
    }
  });

  const response = await app(new Request('http://localhost/health'));
  const body = await response.json();

  assert.equal(body.host, '0.0.0.0');
  assert.equal(body.port, 8788);
  assert.equal(body.hasApiKey, true);
});

test('request body sent to openai does not include reasoning', async () => {
  let capturedBody = '';
  const app = createProxyApp({
    env: {
      OPENAI_API_KEY: 'test-key',
      OPENAI_MODEL: 'gpt-5.4-mini',
      REQUEST_TIMEOUT_MS: '12000'
    },
    fetchImpl: async (_url, init) => {
      capturedBody = String(init.body || '');
      return new Response(JSON.stringify({
        choices: [
          {
            message: {
              content: JSON.stringify({
                intent: 'COMPOSE_WHATSAPP_MESSAGE',
                confidence: 0.91
              })
            }
          }
        ]
      }), { status: 200 });
    }
  });

  const response = await app(new Request('http://localhost/v1/interpret', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      originalText: 'decile a Sofi que llego tarde pero decilo bien',
      normalizedText: 'decir a Sofi que llego tarde pero decirlo bien',
      locale: 'es-AR',
      agentState: 'WAITING_MESSAGE',
      externalApp: 'WhatsApp',
      memorySummary: 'Contacto Sofi.',
      knownSafeContacts: ['Sofi'],
      knownPlaces: ['casa', 'laburo'],
      activePendingTasks: [],
      allowedIntents: ['COMPOSE_WHATSAPP_MESSAGE'],
      forbiddenActions: []
    })
  }));
  const body = await response.json();

  assert.match(capturedBody, /"model":"gpt-5\.4-mini"/);
  assert.doesNotMatch(capturedBody, /"reasoning"/);
  assert.equal(body.intent, 'COMPOSE_WHATSAPP_MESSAGE');
  assert.equal(body.contactName, 'Sofi');
  assert.equal(body.requiresConfirmation, true);
  assert.equal(body.shouldExecuteImmediately, false);
});

test('compose responses are enriched when openai omits slots', async () => {
  const app = createProxyApp({
    env: {
      OPENAI_API_KEY: 'test-key',
      OPENAI_MODEL: 'gpt-5.4-mini'
    },
    fetchImpl: async () => new Response(JSON.stringify({
      choices: [
        {
          message: {
            content: JSON.stringify({
              intent: 'COMPOSE_WHATSAPP_MESSAGE',
              confidence: 0.88
            })
          }
        }
      ]
    }), { status: 200 })
  });

  const response = await app(new Request('http://localhost/v1/interpret', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      originalText: 'decile a Sofi que llego tarde pero decilo bien',
      normalizedText: 'decir a Sofi que llego tarde pero decirlo bien',
      locale: 'es-AR',
      agentState: 'WAITING_MESSAGE',
      externalApp: 'WhatsApp',
      memorySummary: 'Contacto Sofi.',
      knownSafeContacts: ['Sofi'],
      knownPlaces: ['casa', 'laburo'],
      activePendingTasks: [],
      allowedIntents: ['COMPOSE_WHATSAPP_MESSAGE'],
      forbiddenActions: []
    })
  }));
  const body = await response.json();

  assert.equal(body.intent, 'COMPOSE_WHATSAPP_MESSAGE');
  assert.equal(body.contactName, 'Sofi');
  assert.equal(body.messageText, 'llego tarde');
  assert.match(body.proposedMessage, /voy un poco demorado/i);
  assert.equal(body.requiresConfirmation, true);
  assert.equal(body.shouldExecuteImmediately, false);
  assert.match(body.userFacingQuestion, /Lo preparo/i);
});
