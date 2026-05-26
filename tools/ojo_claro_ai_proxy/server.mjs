import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT_DIR = resolve(dirname(fileURLToPath(import.meta.url)));
const REPO_ROOT_DIR = resolve(ROOT_DIR, '..', '..');
const INTENT_SYSTEM_PROMPT_ID = 'OJO_CLARO_INTENT_ENGINE_SYSTEM';
const INTENT_SYSTEM_PROMPT_PATH = resolve(REPO_ROOT_DIR, 'prompts', `${INTENT_SYSTEM_PROMPT_ID}.md`);
const DEFAULTS = {
  OPENAI_MODEL: 'gpt-5.4-mini',
  HOST: '127.0.0.1',
  PORT: '8787',
  MAX_INPUT_CHARS: '1200',
  MAX_MEMORY_CHARS: '800',
  REQUEST_TIMEOUT_MS: '12000'
};

/**
 * Whitelist v1 de intents que el modelo PUEDE proponer.
 *
 * Cualquier intent fuera de esta lista se mapea a UNKNOWN en
 * [enforceIntentWhitelist] (defensa en profundidad — Android tambien filtra).
 * Mantener sincronizado con
 * `androidApp/.../llm/SafeAiFallbackGuard.WHITELIST_V1`.
 */
const INTENT_WHITELIST_V1 = new Set([
  'HELP',
  'READ_VISIBLE_SCREEN',
  'OPEN_WHATSAPP',
  'WHATSAPP_GUIDED_HELP',
  'WHATSAPP_VISIBLE_CHATS',
  'REPEAT_LAST',
  'STOP_SPEAKING',
  'CANCEL',
  'RESET_FLOW',
  'UNKNOWN'
]);

const SYSTEM_PROMPT = [
  'Sos Ojo Claro, asistente de accesibilidad para personas mayores y no videntes.',
  'Tu tono debe ser cálido, paciente, claro y breve.',
  'Respondé breve. No inventes acciones.',
  'No afirmes que enviaste mensajes, fotos o ubicación.',
  'No leas ni proceses contenido sensible (bancos, claves, contraseñas, CBU/CVU, tarjetas, OTP).',
  'No ejecutes acciones. No mandes mensajes. No llames. No confirmes acciones.',
  'Solo clasificá o sugerí dentro de los intents permitidos.',
  `Intents permitidos: ${Array.from(INTENT_WHITELIST_V1).join(', ')}.`,
  'Si la propuesta cae fuera de esa lista, devolvé intent=UNKNOWN.',
  'Si no estás seguro o falta info, devolvé intent=UNKNOWN y pedí reformulación en userFacingQuestion.',
  'Clasificá responseType como chat_response, propose_whatsapp_message, propose_phone_dial, propose_open_app, unknown o fallback.',
  'Devolvé SOLO JSON válido. No markdown. No texto fuera del JSON.',
  'Si hay riesgo, incluí safetyNotes.',
  'Si la frase toca bancos, claves, códigos, tarjetas o documentos, devolvé intent=UNKNOWN y no propongas acción.',
  'Si confidence < 0.75, pedí aclaración.',
  'Mantené español argentino neutro, cálido y simple.',
  'Nunca devuelvas shouldExecuteImmediately=true para WhatsApp, Maps o Teléfono.'
].join(' ');

const ESTELA_INTENT_SCHEMA_KEYS = new Set([
  'intent',
  'confidence',
  'params',
  'safety_level',
  'voice_response',
  'voice_response_template',
  'raw_text'
]);

const ESTELA_SAFETY_LEVELS = new Set([
  'allow_safe',
  'prepare_only',
  'requires_confirm',
  'blocked_sensitive'
]);

const LOCAL_CONFIG_KEYS = new Set([
  'HOST',
  'OPENAI_MODEL',
  'PORT',
  'MAX_INPUT_CHARS',
  'MAX_MEMORY_CHARS',
  'REQUEST_TIMEOUT_MS'
]);

await loadEnvFiles();
const INTENT_SYSTEM_PROMPT = await loadIntentSystemPrompt();

const app = createProxyApp({ logSink: console.log });
const runtimeConfig = readConfig(process.env);
const port = runtimeConfig.port;
const host = runtimeConfig.host;

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
  createServer(async (req, res) => {
    const url = `http://${host}:${port}${req.url ?? '/'}`;
    const request = new Request(url, {
      method: req.method,
      headers: req.headers,
      body: await readIncomingBody(req)
    });
    const response = await app(request);
    res.statusCode = response.status;
    response.headers.forEach((value, key) => res.setHeader(key, value));
    const body = Buffer.from(await response.arrayBuffer());
    res.end(body);
  }).listen(port, host, () => {
    // Banner JAMAS incluye apiKey — solo flags y la URL pública.
    console.log(buildStartupBanner(runtimeConfig));
  });
}

/**
 * Construye el mensaje de arranque que se imprime en stdout.
 *
 * Garantia: el string devuelto NO contiene la API key. Si la key esta
 * configurada solo decimos `hasApiKey=true`. Exportado para tests.
 */
export function buildStartupBanner(config) {
  const safe = redactSecrets(
    `Ojo Claro AI proxy listening on http://${config.host}:${config.port} ` +
      `(model=${config.model}, hasApiKey=${Boolean(config.apiKey)})`
  );
  return safe;
}

/**
 * Recorta cualquier string que parezca una API key de OpenAI.
 *
 * Reemplaza `sk-...` (cualquier longitud razonable) y valores asignados a
 * `OPENAI_API_KEY=` por `[REDACTED]`. Pensado para usarse al loguear errores
 * del proxy donde pueda colarse la key por accidente.
 */
export function redactSecrets(value) {
  if (typeof value !== 'string') return value;
  return value
    .replace(/sk-[A-Za-z0-9_-]{6,}/g, '[REDACTED]')
    .replace(/(OPENAI_API_KEY\s*=\s*)\S+/gi, '$1[REDACTED]')
    .replace(/(Authorization:\s*Bearer\s+)\S+/gi, '$1[REDACTED]');
}

export function createProxyApp({
  env = process.env,
  fetchImpl = globalThis.fetch,
  logSink = null
} = {}) {
  const config = readConfig(env);
  const metrics = createMetrics(config);

  return async function handle(request) {
    const url = new URL(request.url);
    if (request.method === 'GET' && url.pathname === '/health') {
      return jsonResponse(200, {
        ok: true,
        model: config.model,
        hasApiKey: Boolean(config.apiKey),
        host: config.host,
        port: config.port
      });
    }

    if (request.method === 'GET' && url.pathname === '/metrics') {
      return jsonResponse(200, metricsResponse(metrics, config));
    }

    if (request.method === 'POST' && url.pathname === '/v1/interpret') {
      return interpretRequest(request, config, fetchImpl, metrics, logSink);
    }

    if (request.method === 'POST' && (url.pathname === '/intent' || url.pathname === '/v1/intent')) {
      return intentRequest(request, config, fetchImpl, metrics, logSink);
    }

    return jsonResponse(404, {
      ok: false,
      error: 'not_found'
    });
  };
}

function createMetrics(config) {
  return {
    totalInterpretRequests: 0,
    lastModel: config.model,
    lastIntent: null,
    lastWhitelistPass: null,
    lastRequestAt: null,
    lastBlockedSensitive: false
  };
}

function metricsResponse(metrics, config) {
  return {
    totalInterpretRequests: metrics.totalInterpretRequests,
    lastModel: metrics.lastModel,
    lastIntent: metrics.lastIntent,
    lastWhitelistPass: metrics.lastWhitelistPass,
    lastRequestAt: metrics.lastRequestAt,
    hasApiKey: Boolean(config.apiKey)
  };
}

function readConfig(env) {
  return {
    apiKey: (env.OPENAI_API_KEY || '').trim(),
    model: (env.OPENAI_MODEL || DEFAULTS.OPENAI_MODEL).trim(),
    host: (env.HOST || DEFAULTS.HOST).trim(),
    port: parseInt(env.PORT || DEFAULTS.PORT, 10),
    timeoutMillis: parseInt(env.REQUEST_TIMEOUT_MS || DEFAULTS.REQUEST_TIMEOUT_MS, 10),
    maxInputChars: parseInt(env.MAX_INPUT_CHARS || DEFAULTS.MAX_INPUT_CHARS, 10),
    maxMemoryChars: parseInt(env.MAX_MEMORY_CHARS || DEFAULTS.MAX_MEMORY_CHARS, 10)
  };
}

async function interpretRequest(request, config, fetchImpl, metrics, logSink) {
  const requestId = String(metrics.totalInterpretRequests + 1);
  markInterpretStart(metrics, config);
  if (!config.apiKey) {
    logProxyEvent(logSink, {
      requestId,
      model: config.model,
      intent: 'UNKNOWN',
      whitelistPass: false,
      blockedSensitive: false,
      result: 'missing_api_key'
    });
    return jsonResponse(503, safeResponse('missing_api_key', 'Falta OPENAI_API_KEY.'));
  }

  const bodyText = await request.text();
  if (bodyText.length > 16_384) {
    return jsonResponse(413, safeResponse('payload_too_large', 'La petición es demasiado grande.'));
  }

  const parsedBody = safeParseJson(bodyText);
  if (!parsedBody.ok) {
    return jsonResponse(400, safeResponse('invalid_json', 'JSON inválido.'));
  }

  const requestPayload = sanitizeRequest(parsedBody.value, config);
  if (containsSensitiveData(requestPayload)) {
    markInterpretResult(metrics, {
      intent: 'UNKNOWN',
      whitelistPass: false,
      blockedSensitive: true
    });
    logProxyEvent(logSink, {
      requestId,
      model: config.model,
      intent: 'UNKNOWN',
      whitelistPass: false,
      blockedSensitive: true,
      result: 'blocked_sensitive'
    });
    return jsonResponse(403, safeResponse('sensitive_data', 'No interpreto datos sensibles.'));
  }

  const openAiResponse = await callOpenAI(fetchImpl, config, requestPayload);
  if (!openAiResponse.ok) {
    markInterpretResult(metrics, {
      intent: 'UNKNOWN',
      whitelistPass: false,
      blockedSensitive: false
    });
    logProxyEvent(logSink, {
      requestId,
      model: config.model,
      intent: 'UNKNOWN',
      whitelistPass: false,
      blockedSensitive: false,
      result: openAiResponse.errorCode
    });
    return jsonResponse(openAiResponse.statusCode, safeResponse(openAiResponse.errorCode, openAiResponse.message));
  }

  markInterpretResult(metrics, {
    intent: openAiResponse.response.intent || 'UNKNOWN',
    whitelistPass: openAiResponse.response.safetyNotes !== 'intent_outside_whitelist_v1',
    blockedSensitive: false
  });
  logProxyEvent(logSink, {
    requestId,
    model: config.model,
    intent: openAiResponse.response.intent || 'UNKNOWN',
    whitelistPass: openAiResponse.response.safetyNotes !== 'intent_outside_whitelist_v1',
    blockedSensitive: false,
    result: 'ok'
  });
  return jsonResponse(200, openAiResponse.response);
}

async function intentRequest(request, config, fetchImpl, metrics, logSink) {
  const requestId = String(metrics.totalInterpretRequests + 1);
  markInterpretStart(metrics, config);
  if (!config.apiKey) {
    logProxyEvent(logSink, {
      requestId,
      model: config.model,
      intent: 'unknown',
      whitelistPass: false,
      blockedSensitive: false,
      result: 'missing_api_key'
    });
    return jsonResponse(503, estelaSafeFallback('missing_api_key'));
  }

  const bodyText = await request.text();
  if (bodyText.length > 16_384) {
    return jsonResponse(413, estelaSafeFallback('payload_too_large'));
  }

  const parsedBody = safeParseJson(bodyText);
  if (!parsedBody.ok) {
    return jsonResponse(400, estelaSafeFallback('invalid_json'));
  }

  const intentPayload = sanitizeIntentRequest(parsedBody.value, config);
  const openAiResponse = await callOpenAIForIntent(fetchImpl, config, intentPayload);
  if (!openAiResponse.ok) {
    markInterpretResult(metrics, {
      intent: 'unknown',
      whitelistPass: false,
      blockedSensitive: false
    });
    logProxyEvent(logSink, {
      requestId,
      model: intentPayload.model,
      intent: 'unknown',
      whitelistPass: false,
      blockedSensitive: false,
      result: openAiResponse.errorCode
    });
    return jsonResponse(openAiResponse.statusCode, estelaSafeFallback(openAiResponse.errorCode, intentPayload.input));
  }

  markInterpretResult(metrics, {
    intent: openAiResponse.response.intent || 'unknown',
    whitelistPass: true,
    blockedSensitive: openAiResponse.response.safety_level === 'blocked_sensitive'
  });
  logProxyEvent(logSink, {
    requestId,
    model: intentPayload.model,
    intent: openAiResponse.response.intent || 'unknown',
    whitelistPass: true,
    blockedSensitive: openAiResponse.response.safety_level === 'blocked_sensitive',
    result: 'ok'
  });
  return jsonResponse(200, openAiResponse.response);
}

function markInterpretStart(metrics, config) {
  metrics.totalInterpretRequests += 1;
  metrics.lastModel = config.model;
  metrics.lastIntent = 'UNKNOWN';
  metrics.lastWhitelistPass = false;
  metrics.lastRequestAt = new Date().toISOString();
  metrics.lastBlockedSensitive = false;
}

function markInterpretResult(metrics, { intent, whitelistPass, blockedSensitive }) {
  metrics.lastIntent = intent || 'UNKNOWN';
  metrics.lastWhitelistPass = Boolean(whitelistPass);
  metrics.lastBlockedSensitive = Boolean(blockedSensitive);
}

function logProxyEvent(logSink, event) {
  if (typeof logSink !== 'function') return;
  const line = [
    'OjoClaroProxy',
    `requestId=${sanitizeLogToken(event.requestId)}`,
    `model=${sanitizeLogToken(event.model)}`,
    `intent=${sanitizeLogToken(event.intent)}`,
    `whitelistPass=${Boolean(event.whitelistPass)}`,
    `blockedSensitive=${Boolean(event.blockedSensitive)}`,
    `result=${sanitizeLogToken(event.result)}`
  ].join(' ');
  logSink(redactSecrets(line));
}

function sanitizeLogToken(value) {
  return String(value ?? 'UNKNOWN')
    .replace(/[^A-Za-z0-9_.:-]/g, '_')
    .slice(0, 80);
}

function sanitizeRequest(requestPayload, config) {
  const text = truncate(String(requestPayload?.originalText || ''), config.maxInputChars);
  const normalizedText = truncate(String(requestPayload?.normalizedText || ''), config.maxInputChars);
  const memorySummary = truncate(String(requestPayload?.memorySummary || ''), config.maxMemoryChars);

  return {
    ...requestPayload,
    originalText: text,
    normalizedText,
    memorySummary
  };
}

function containsSensitiveData(payload) {
  const text = [
    payload.originalText,
    payload.normalizedText,
    payload.memorySummary
  ].join(' ').toLowerCase();

  const tokens = [
    'banco',
    'tarjeta',
    'clave',
    'codigo',
    'código',
    'password',
    'documento',
    'token',
    'api key',
    'read_contacts',
    'call_phone',
    'action_call'
  ];

  return tokens.some((token) => text.includes(token));
}

async function callOpenAI(fetchImpl, config, requestPayload) {
  const userPrompt = JSON.stringify(requestPayload);
  let response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      'https://api.openai.com/v1/chat/completions',
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${config.apiKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          model: config.model,
          messages: [
            { role: 'system', content: SYSTEM_PROMPT },
            { role: 'user', content: userPrompt }
          ],
          response_format: { type: 'json_object' },
          temperature: 0.2,
          max_completion_tokens: 512
        })
      },
      config.timeoutMillis
    );
  } catch (error) {
    return safeOpenAiTransportFailure(error);
  }

  if (!response.ok) {
    const fallback = await parseErrorResponse(response, 'openai_http_error');
    return {
      ok: false,
      statusCode: response.status,
      errorCode: fallback.errorCode,
      message: safeOpenAiFailureMessage(response.status)
    };
  }

  const data = safeParseJson(await response.text());
  if (!data.ok) {
    return {
      ok: false,
      statusCode: 502,
      errorCode: 'openai_invalid_json',
      message: safeOpenAiFailureMessage(502)
    };
  }

  const content = data.value?.choices?.[0]?.message?.content;
  const jsonText = extractJsonContent(content);
  const parsed = safeParseJson(jsonText);
  if (!parsed.ok) {
    return {
      ok: false,
      statusCode: 502,
      errorCode: 'openai_unparseable',
      message: safeOpenAiFailureMessage(502)
    };
  }

  const normalized = normalizeResponse(parsed.value);
  return {
    ok: true,
    response: enrichResponseFromRequest(requestPayload, normalized)
  };
}

async function callOpenAIForIntent(fetchImpl, config, intentPayload) {
  const inputText = JSON.stringify(intentPayload.input);
  let response;
  try {
    response = await fetchWithTimeout(
      fetchImpl,
      'https://api.openai.com/v1/responses',
      {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${config.apiKey}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          model: intentPayload.model,
          instructions: INTENT_SYSTEM_PROMPT,
          input: [
            {
              role: 'user',
              content: [
                {
                  type: 'input_text',
                  text: inputText
                }
              ]
            }
          ],
          text: {
            format: {
              type: 'json_object'
            }
          },
          max_output_tokens: 700
        })
      },
      config.timeoutMillis
    );
  } catch (error) {
    return safeOpenAiTransportFailure(error);
  }

  if (!response.ok) {
    const fallback = await parseErrorResponse(response, 'openai_http_error');
    return {
      ok: false,
      statusCode: response.status,
      errorCode: fallback.errorCode,
      message: safeOpenAiFailureMessage(response.status)
    };
  }

  const data = safeParseJson(await response.text());
  if (!data.ok) {
    return {
      ok: false,
      statusCode: 502,
      errorCode: 'openai_invalid_json',
      message: safeOpenAiFailureMessage(502)
    };
  }

  const jsonText = extractResponsesText(data.value);
  const parsed = safeParseJson(jsonText);
  if (!parsed.ok) {
    return {
      ok: false,
      statusCode: 502,
      errorCode: 'openai_unparseable',
      message: safeOpenAiFailureMessage(502)
    };
  }

  return {
    ok: true,
    response: normalizeEstelaIntentResponse(parsed.value, intentPayload.input)
  };
}

function normalizeResponse(response) {
  const rawIntent = response?.intent ?? null;
  const intent = enforceIntentWhitelist(rawIntent);
  const responseType = normalizeResponseType(response?.responseType, intent);
  const shouldExecuteImmediately = Boolean(response?.shouldExecuteImmediately);
  const forbiddenIntents = new Set(['OPEN_WHATSAPP', 'OPEN_WHATSAPP_CHAT', 'COMPOSE_WHATSAPP_MESSAGE', 'CALL_CONTACT', 'OPEN_PHONE', 'OPEN_MAPS', 'NAVIGATE_TO_DESTINATION']);
  // Si el modelo intento un intent fuera de whitelist v1 y lo bajamos a UNKNOWN,
  // forzamos slots vacios para que el caller nunca arme la accion peligrosa.
  const wasRewritten = rawIntent && intent === 'UNKNOWN' && rawIntent !== 'UNKNOWN';

  return {
    intent,
    responseType,
    confidence: clampNumber(response?.confidence, 0, 1, 0),
    contactName: wasRewritten ? null : nullableString(response?.contactName),
    messageText: wasRewritten ? null : nullableString(response?.messageText),
    proposedMessage: wasRewritten ? null : nullableString(response?.proposedMessage),
    destination: wasRewritten ? null : nullableString(response?.destination),
    locationAlias: wasRewritten ? null : nullableString(response?.locationAlias),
    routineName: wasRewritten ? null : nullableString(response?.routineName),
    pendingTask: wasRewritten ? null : nullableString(response?.pendingTask),
    missingSlots: toStringArray(response?.missingSlots),
    userFacingQuestion: nullableString(response?.userFacingQuestion),
    suggestionText: nullableString(response?.suggestionText),
    requiresConfirmation: Boolean(response?.requiresConfirmation),
    shouldExecuteImmediately: wasRewritten || (intent && forbiddenIntents.has(intent))
      ? false
      : shouldExecuteImmediately,
    safetyNotes: wasRewritten
      ? 'intent_outside_whitelist_v1'
      : nullableString(response?.safetyNotes)
  };
}

function sanitizeIntentRequest(body, config) {
  const input = body && typeof body.input === 'object' && body.input !== null
    ? body.input
    : {};
  const model = nullableString(body?.model) || config.model;
  const permissions = input.permissions_granted && typeof input.permissions_granted === 'object'
    ? Object.fromEntries(
      Object.entries(input.permissions_granted)
        .map(([key, value]) => [String(key), Boolean(value)])
    )
    : {};

  return {
    model,
    systemPromptId: nullableString(body?.system_prompt_id) || INTENT_SYSTEM_PROMPT_ID,
    input: {
      user_text: truncate(String(input.user_text || ''), config.maxInputChars),
      conversation_state: normalizeConversationState(input.conversation_state),
      pending_action: normalizePendingAction(input.pending_action),
      installed_apps: toStringArray(input.installed_apps).slice(0, 80),
      memory_contacts: toStringArray(input.memory_contacts).slice(0, 80),
      active_app: nullableString(input.active_app),
      permissions_granted: permissions
    }
  };
}

function normalizeEstelaIntentResponse(response, requestInput) {
  const raw = response && typeof response === 'object' ? response : {};
  const normalized = {};
  for (const key of ESTELA_INTENT_SCHEMA_KEYS) {
    normalized[key] = raw[key] ?? null;
  }

  normalized.intent = nullableString(raw.intent);
  normalized.confidence = clampNumber(raw.confidence, 0, 1, 0);
  normalized.params = raw.params && typeof raw.params === 'object' && !Array.isArray(raw.params)
    ? raw.params
    : {};
  normalized.safety_level = ESTELA_SAFETY_LEVELS.has(raw.safety_level)
    ? raw.safety_level
    : 'blocked_sensitive';
  normalized.voice_response = nullableString(raw.voice_response);
  normalized.voice_response_template = nullableString(raw.voice_response_template);
  normalized.raw_text = nullableString(raw.raw_text) || String(requestInput?.user_text || '');

  if (normalized.safety_level !== 'allow_safe' && !normalized.voice_response_template && normalized.intent !== 'slot_fill') {
    normalized.safety_level = 'blocked_sensitive';
    normalized.voice_response = null;
    normalized.voice_response_template = 'PROTECTED_APP_REJECTED';
  }

  return normalized;
}

function normalizeConversationState(value) {
  const normalized = nullableString(value) || 'idle';
  const allowed = new Set([
    'idle',
    'waiting_contact',
    'waiting_message',
    'waiting_whatsapp_action',
    'waiting_confirm'
  ]);
  return allowed.has(normalized) ? normalized : 'idle';
}

function normalizePendingAction(value) {
  if (!value || typeof value !== 'object' || Array.isArray(value)) return null;
  const intent = nullableString(value.intent);
  if (!intent) return null;
  const params = value.params && typeof value.params === 'object' && !Array.isArray(value.params)
    ? value.params
    : {};
  return { intent, params };
}

function estelaSafeFallback(code, input = {}) {
  const isTimeout = code === 'openai_timeout';
  return {
    intent: 'unknown',
    confidence: 0,
    params: {},
    safety_level: 'allow_safe',
    voice_response: isTimeout
      ? 'No pude procesarlo a tiempo. ¿Podés repetirlo?'
      : 'No lo pude procesar con seguridad. ¿Podés repetirlo?',
    voice_response_template: null,
    raw_text: String(input?.user_text || '')
  };
}

/**
 * Aplica el whitelist v1 al intent devuelto por el modelo.
 *
 * Si el modelo respondio con algo no listado en [INTENT_WHITELIST_V1] (por
 * ejemplo PAY_BILL, OPEN_VAULT, SEND_NUDE o tipos peligrosos similares), se
 * mapea a `UNKNOWN`. `null` y `undefined` quedan en `null` para que el caller
 * decida (Android los trata como UNKNOWN). Exportado para tests.
 */
export function enforceIntentWhitelist(intent) {
  if (typeof intent !== 'string' || !intent) return null;
  return INTENT_WHITELIST_V1.has(intent) ? intent : 'UNKNOWN';
}

function normalizeResponseType(responseType, intent) {
  const clean = nullableString(responseType);
  const allowed = new Set([
    'chat_response',
    'propose_whatsapp_message',
    'propose_phone_dial',
    'propose_open_app',
    'unknown',
    'fallback'
  ]);
  if (clean && allowed.has(clean)) return clean;
  if (intent === 'COMPOSE_WHATSAPP_MESSAGE') return 'propose_whatsapp_message';
  if (intent === 'CALL_CONTACT' || intent === 'OPEN_PHONE') return 'propose_phone_dial';
  if (
    intent === 'OPEN_WHATSAPP' ||
    intent === 'OPEN_WHATSAPP_CHAT' ||
    intent === 'OPEN_MAPS' ||
    intent === 'NAVIGATE_TO_DESTINATION'
  ) {
    return 'propose_open_app';
  }
  if (intent) return 'chat_response';
  return 'fallback';
}

function enrichResponseFromRequest(requestPayload, response) {
  if (response.intent !== 'COMPOSE_WHATSAPP_MESSAGE') {
    return response;
  }

  const contactName = response.contactName || inferContactName(requestPayload);
  const messageText = response.messageText || inferMessageText(requestPayload.originalText, contactName);
  const proposedMessage = response.proposedMessage || inferProposedMessage(messageText, contactName, requestPayload.originalText);
  const userFacingQuestion = response.userFacingQuestion || inferComposeQuestion(contactName, proposedMessage);

  return {
    ...response,
    responseType: 'propose_whatsapp_message',
    contactName,
    messageText,
    proposedMessage,
    userFacingQuestion,
    requiresConfirmation: true,
    shouldExecuteImmediately: false
  };
}

function inferContactName(requestPayload) {
  const raw = String(requestPayload?.originalText || '');
  const safeContacts = Array.isArray(requestPayload?.knownSafeContacts)
    ? requestPayload.knownSafeContacts
    : [];

  for (const contact of safeContacts) {
    const normalizedContact = contact.trim();
    if (!normalizedContact) continue;
    const regex = new RegExp(`\\b${escapeRegExp(normalizedContact)}\\b`, 'i');
    if (regex.test(raw)) {
      return normalizedContact;
    }
  }

  const matches = raw.match(/(?:a|para|con|de|del|la|el|mi|mi\s+)?([A-Z][\p{L}'-]*(?:\s+[A-Z][\p{L}'-]*){0,2})/u);
  return matches?.[1]?.trim() || null;
}

function inferMessageText(originalText, contactName) {
  let text = String(originalText || '').trim();
  if (contactName) {
    const contactPattern = new RegExp(`\\b(?:a|para|con|de|del|la|el)?\\s*${escapeRegExp(contactName)}\\b`, 'i');
    text = text.replace(contactPattern, '').trim();
  }

  text = text
    .replace(/^decile?\s+/i, '')
    .replace(/^mandale?\s+/i, '')
    .replace(/^escribile?\s+/i, '')
    .replace(/^avisale?\s+/i, '')
    .replace(/^dile?\s+/i, '')
    .replace(/^que\s+/i, '')
    .replace(/\s+pero\s+decilo\s+bien.*$/i, '')
    .replace(/\s+pero\s+decilo\s+mejor.*$/i, '')
    .replace(/\s+decilo\s+bien.*$/i, '')
    .trim();

  text = text.replace(/^(a|para|con)\s+/i, '').trim();
  return text || 'llego tarde';
}

function inferProposedMessage(messageText, contactName, originalText) {
  const lower = `${originalText || ''} ${messageText || ''}`.toLowerCase();

  if (lower.includes('llego tarde') || lower.includes('voy tarde') || lower.includes('llego demorado')) {
    return 'Voy un poco demorado. Llego en unos minutos.';
  }

  if (lower.includes('llego en 10') || lower.includes('llego en diez')) {
    return 'Llego en 10 minutos. Ya salgo.';
  }

  if (lower.includes('estoy llegando')) {
    return 'Ya estoy llegando.';
  }

  if (!messageText) {
    return 'Te escribo en un momento.';
  }

  return messageText.replace(/^([a-z]+)\s+/i, (match) => match.trim());
}

function inferComposeQuestion(contactName, proposedMessage) {
  const cleanProposal = String(proposedMessage || '').trim().replace(/[.?!]+$/g, '');
  if (contactName && proposedMessage) {
    return `Puedo preparar este mensaje para ${contactName}: '${cleanProposal}'. Para prepararlo en WhatsApp, deci: confirmar.`;
  }
  if (contactName) {
    return `Queres mandarle un mensaje a ${contactName}?`;
  }
  return 'Queres que lo prepare?';
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function safeResponse(code, message) {
  return normalizeResponse({
    intent: null,
    responseType: 'fallback',
    confidence: 0,
    contactName: null,
    messageText: null,
    proposedMessage: null,
    destination: null,
    locationAlias: null,
    routineName: null,
    pendingTask: null,
    missingSlots: [],
    userFacingQuestion: 'No lo pude resolver con seguridad. Decime una accion concreta.',
    suggestionText: null,
    requiresConfirmation: false,
    shouldExecuteImmediately: false,
    safetyNotes: message || code
  });
}

async function parseErrorResponse(response, fallbackCode) {
  const text = await response.text();
  const parsed = safeParseJson(text);
  if (parsed.ok) {
    const message = parsed.value?.error?.message || parsed.value?.message || fallbackCode;
    return {
      errorCode: sanitizeErrorCode(parsed.value?.error?.code || fallbackCode),
      message: redactSecrets(String(message || fallbackCode)).slice(0, 120)
    };
  }
  return {
    errorCode: sanitizeErrorCode(fallbackCode),
    message: fallbackCode
  };
}

function safeOpenAiTransportFailure(error) {
  const isTimeout = error?.name === 'AbortError';
  return {
    ok: false,
    statusCode: isTimeout ? 504 : 502,
    errorCode: isTimeout ? 'openai_timeout' : 'openai_request_failed',
    message: safeOpenAiFailureMessage(isTimeout ? 504 : 502)
  };
}

function safeOpenAiFailureMessage(statusCode) {
  return statusCode === 504
    ? 'No lo pude resolver a tiempo.'
    : 'No lo pude resolver ahora.';
}

function sanitizeErrorCode(value) {
  return String(value || 'openai_error')
    .replace(/[^A-Za-z0-9_.:-]/g, '_')
    .slice(0, 80);
}

function truncate(value, maxChars) {
  if (value.length <= maxChars) {
    return value;
  }
  return value.slice(0, maxChars);
}

function extractJsonContent(content) {
  if (typeof content === 'string') {
    return content;
  }
  if (Array.isArray(content)) {
    return content.map((part) => part?.text || part?.content || '').join('');
  }
  return '';
}

function extractResponsesText(value) {
  if (typeof value?.output_text === 'string') {
    return value.output_text;
  }
  if (!Array.isArray(value?.output)) {
    return '';
  }
  return value.output
    .flatMap((item) => Array.isArray(item?.content) ? item.content : [])
    .map((part) => {
      if (typeof part?.text === 'string') return part.text;
      if (typeof part?.content === 'string') return part.content;
      if (typeof part?.output_text === 'string') return part.output_text;
      return '';
    })
    .join('');
}

function safeParseJson(text) {
  try {
    return { ok: true, value: JSON.parse(text) };
  } catch {
    const start = text.indexOf('{');
    const end = text.lastIndexOf('}');
    if (start >= 0 && end > start) {
      try {
        return { ok: true, value: JSON.parse(text.slice(start, end + 1)) };
      } catch {
        return { ok: false };
      }
    }
    return { ok: false };
  }
}

function nullableString(value) {
  return typeof value === 'string' && value.trim() ? value.trim() : null;
}

function toStringArray(value) {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.map((item) => String(item).trim()).filter(Boolean);
}

function clampNumber(value, min, max, fallback) {
  const numeric = Number(value);
  if (!Number.isFinite(numeric)) {
    return fallback;
  }
  return Math.min(max, Math.max(min, numeric));
}

function jsonResponse(statusCode, payload) {
  return new Response(JSON.stringify(payload), {
    status: statusCode,
    headers: { 'Content-Type': 'application/json; charset=utf-8' }
  });
}

async function fetchWithTimeout(fetchImpl, url, init, timeoutMillis) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), timeoutMillis);
  try {
    return await fetchImpl(url, {
      ...init,
      signal: controller.signal
    });
  } finally {
    clearTimeout(timeout);
  }
}

async function readIncomingBody(req) {
  if (req.method === 'GET' || req.method === 'HEAD') {
    return undefined;
  }

  const chunks = [];
  for await (const chunk of req) {
    chunks.push(chunk);
  }
  return Buffer.concat(chunks);
}

async function loadEnvFiles() {
  const candidates = [resolve(ROOT_DIR, '.env'), resolve(process.cwd(), '.env')];
  for (const file of candidates) {
    if (!existsSync(file)) continue;
    const content = await readFile(file, 'utf8');
    applyLocalProxyEnv(process.env, content);
  }
}

async function loadIntentSystemPrompt() {
  return readFile(INTENT_SYSTEM_PROMPT_PATH, 'utf8');
}

export function applyLocalProxyEnv(targetEnv, fileContent) {
  for (const line of fileContent.split(/\r?\n/)) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#')) continue;
    const separator = trimmed.indexOf('=');
    if (separator <= 0) continue;
    const key = trimmed.slice(0, separator).trim();
    const value = trimmed.slice(separator + 1).trim();
    if (LOCAL_CONFIG_KEYS.has(key)) {
      targetEnv[key] = value;
    } else if (key === 'OPENAI_API_KEY' && !targetEnv.OPENAI_API_KEY) {
      targetEnv[key] = value;
    } else if (!targetEnv[key]) {
      targetEnv[key] = value;
    }
  }
  return targetEnv;
}
