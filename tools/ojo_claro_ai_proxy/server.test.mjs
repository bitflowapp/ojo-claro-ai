import assert from 'node:assert/strict';
import test from 'node:test';
import {
  applyLocalProxyEnv,
  buildStartupBanner,
  createProxyApp,
  enforceIntentWhitelist,
  redactSecrets
} from './server.mjs';

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
      originalText: 'decile a un contacto que llego tarde',
      normalizedText: 'decir a un contacto que llego tarde',
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

test('request body sent to openai uses gpt-5.4-mini and no reasoning field', async () => {
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
                intent: 'HELP',
                confidence: 0.91,
                userFacingQuestion: '¿En qué te ayudo?'
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
      originalText: 'que podes hacer',
      normalizedText: 'que podes hacer',
      locale: 'es-AR',
      agentState: 'IDLE',
      externalApp: null,
      memorySummary: '',
      knownSafeContacts: [],
      knownPlaces: [],
      activePendingTasks: [],
      allowedIntents: ['HELP'],
      forbiddenActions: []
    })
  }));
  const body = await response.json();

  assert.match(capturedBody, /"model":"gpt-5\.4-mini"/);
  assert.doesNotMatch(capturedBody, /"reasoning"/);
  assert.equal(body.intent, 'HELP');
  assert.equal(response.status, 200);
});

test('proxy rewrites compose/call intents to UNKNOWN under whitelist v1', async () => {
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
              confidence: 0.95,
              contactName: 'ContactoDemo',
              messageText: 'llego tarde',
              proposedMessage: 'Voy demorado.',
              shouldExecuteImmediately: true
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
      originalText: 'decile a ContactoDemo que llego tarde',
      normalizedText: 'decir a ContactoDemo que llego tarde',
      locale: 'es-AR',
      agentState: 'WAITING_MESSAGE',
      externalApp: 'WhatsApp',
      memorySummary: '',
      knownSafeContacts: ['ContactoDemo'],
      knownPlaces: [],
      activePendingTasks: [],
      allowedIntents: ['COMPOSE_WHATSAPP_MESSAGE'],
      forbiddenActions: []
    })
  }));
  const body = await response.json();

  // Whitelist v1: COMPOSE_WHATSAPP_MESSAGE no esta -> degradado a UNKNOWN, slots
  // limpiados, no se ejecuta automaticamente.
  assert.equal(body.intent, 'UNKNOWN');
  assert.equal(body.contactName, null);
  assert.equal(body.messageText, null);
  assert.equal(body.proposedMessage, null);
  assert.equal(body.shouldExecuteImmediately, false);
  assert.equal(body.safetyNotes, 'intent_outside_whitelist_v1');
});

test('proxy lets v1 whitelist intents pass through unchanged', async () => {
  for (const allowed of ['HELP', 'READ_VISIBLE_SCREEN', 'OPEN_WHATSAPP', 'REPEAT_LAST', 'STOP_SPEAKING', 'CANCEL']) {
    const app = createProxyApp({
      env: { OPENAI_API_KEY: 'test-key', OPENAI_MODEL: 'gpt-5.4-mini' },
      fetchImpl: async () => new Response(JSON.stringify({
        choices: [
          {
            message: {
              content: JSON.stringify({ intent: allowed, confidence: 0.91 })
            }
          }
        ]
      }), { status: 200 })
    });
    const response = await app(new Request('http://localhost/v1/interpret', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        originalText: 'algo',
        normalizedText: 'algo',
        locale: 'es-AR',
        agentState: 'IDLE',
        externalApp: null,
        memorySummary: '',
        knownSafeContacts: [],
        knownPlaces: [],
        activePendingTasks: [],
        allowedIntents: [allowed],
        forbiddenActions: []
      })
    }));
    const body = await response.json();
    assert.equal(body.intent, allowed, `expected ${allowed} to pass`);
    assert.notEqual(body.safetyNotes, 'intent_outside_whitelist_v1');
  }
});

test('enforceIntentWhitelist strips dangerous intents', () => {
  assert.equal(enforceIntentWhitelist('HELP'), 'HELP');
  assert.equal(enforceIntentWhitelist('READ_VISIBLE_SCREEN'), 'READ_VISIBLE_SCREEN');
  assert.equal(enforceIntentWhitelist('OPEN_WHATSAPP'), 'OPEN_WHATSAPP');
  assert.equal(enforceIntentWhitelist('UNKNOWN'), 'UNKNOWN');
  assert.equal(enforceIntentWhitelist('CANCEL'), 'CANCEL');
  // Fuera de whitelist:
  assert.equal(enforceIntentWhitelist('COMPOSE_WHATSAPP_MESSAGE'), 'UNKNOWN');
  assert.equal(enforceIntentWhitelist('CALL_CONTACT'), 'UNKNOWN');
  assert.equal(enforceIntentWhitelist('OPEN_PHONE'), 'UNKNOWN');
  assert.equal(enforceIntentWhitelist('PAY_BILL'), 'UNKNOWN');
  assert.equal(enforceIntentWhitelist('OPEN_VAULT'), 'UNKNOWN');
  // Edge cases:
  assert.equal(enforceIntentWhitelist(null), null);
  assert.equal(enforceIntentWhitelist(''), null);
  assert.equal(enforceIntentWhitelist(42), null);
});

test('default OPENAI_MODEL is gpt-5.4-mini when env is empty', async () => {
  const app = createProxyApp({ env: {} });
  const response = await app(new Request('http://localhost/health'));
  const body = await response.json();
  assert.equal(body.model, 'gpt-5.4-mini');
});

test('OPENAI_MODEL env override is honoured when present', async () => {
  const app = createProxyApp({
    env: { OPENAI_MODEL: 'gpt-5.4-mini-canary' }
  });
  const response = await app(new Request('http://localhost/health'));
  const body = await response.json();
  assert.equal(body.model, 'gpt-5.4-mini-canary');
});

test('redactSecrets hides sk- keys and OPENAI_API_KEY assignments', () => {
  const tricky = 'starting proxy with sk-ABC1234567890 and OPENAI_API_KEY=sk-XYZSECRETSECRET ' +
    'and Authorization: Bearer sk-bearer123secret';
  const safe = redactSecrets(tricky);
  assert.doesNotMatch(safe, /sk-[A-Za-z0-9]{6,}/);
  assert.doesNotMatch(safe, /sk-XYZSECRETSECRET/);
  assert.doesNotMatch(safe, /sk-bearer123secret/);
  assert.match(safe, /\[REDACTED\]/);
});

test('startup banner reports flags but never the api key', () => {
  const banner = buildStartupBanner({
    apiKey: 'sk-VERY-SECRET-1234567890',
    model: 'gpt-5.4-mini',
    host: '0.0.0.0',
    port: 8787
  });
  assert.doesNotMatch(banner, /sk-[A-Za-z0-9]/);
  assert.doesNotMatch(banner, /VERY-SECRET/);
  assert.match(banner, /hasApiKey=true/);
  assert.match(banner, /gpt-5\.4-mini/);
  assert.match(banner, /0\.0\.0\.0:8787/);
});

test('startup banner reports hasApiKey=false when no key is set', () => {
  const banner = buildStartupBanner({
    apiKey: '',
    model: 'gpt-5.4-mini',
    host: '0.0.0.0',
    port: 8787
  });
  assert.match(banner, /hasApiKey=false/);
});

test('health response never contains the api key value', async () => {
  const secret = 'sk-DO-NOT-LEAK-ABCDEFG1234567';
  const app = createProxyApp({
    env: { OPENAI_API_KEY: secret, OPENAI_MODEL: 'gpt-5.4-mini' }
  });
  const response = await app(new Request('http://localhost/health'));
  const text = await response.text();
  assert.doesNotMatch(text, /DO-NOT-LEAK/);
  assert.doesNotMatch(text, /sk-/);
  const body = JSON.parse(text);
  assert.equal(body.hasApiKey, true);
});

test('sensitive data in request is blocked before calling openai', async () => {
  let openAiCalled = false;
  const app = createProxyApp({
    env: { OPENAI_API_KEY: 'test-key', OPENAI_MODEL: 'gpt-5.4-mini' },
    fetchImpl: async () => {
      openAiCalled = true;
      return new Response('{}', { status: 200 });
    }
  });
  const response = await app(new Request('http://localhost/v1/interpret', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      originalText: 'transferi mil al banco con clave 1234',
      normalizedText: 'transferi mil al banco con clave 1234',
      locale: 'es-AR',
      agentState: 'IDLE',
      externalApp: null,
      memorySummary: '',
      knownSafeContacts: [],
      knownPlaces: [],
      activePendingTasks: [],
      allowedIntents: ['HELP'],
      forbiddenActions: []
    })
  }));
  assert.equal(openAiCalled, false);
  assert.equal(response.status, 403);
});
