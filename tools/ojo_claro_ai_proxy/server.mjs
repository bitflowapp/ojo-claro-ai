import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT_DIR = resolve(dirname(fileURLToPath(import.meta.url)));
const DEFAULTS = {
  OPENAI_MODEL: 'gpt-5.4-mini',
  HOST: '127.0.0.1',
  PORT: '8787',
  MAX_INPUT_CHARS: '1200',
  MAX_MEMORY_CHARS: '800',
  REQUEST_TIMEOUT_MS: '12000'
};

const SYSTEM_PROMPT = [
  'Sos el intérprete conversacional de Ojo Claro AI.',
  'Ojo Claro ayuda a personas mayores y no videntes.',
  'Tu tono debe ser cálido, paciente, claro y breve.',
  'No ejecutes acciones.',
  'No mandes mensajes.',
  'No llames.',
  'No confirmes acciones.',
  'Solo interpretá intención, extraé slots y proponé texto.',
  'Devolvé SOLO JSON válido.',
  'No markdown.',
  'No texto fuera del JSON.',
  'Si falta información, usá userFacingQuestion.',
  'Si hay riesgo, incluí safetyNotes.',
  'Si la frase toca bancos, claves, códigos, tarjetas o documentos, no propongas acción.',
  'Si confidence < 0.75, pedí aclaración.',
  'Mantené español argentino neutro, cálido y simple.',
  'Forzá shouldExecuteImmediately=false para WhatsApp, Maps y Teléfono.'
].join(' ');

const LOCAL_CONFIG_KEYS = new Set([
  'HOST',
  'OPENAI_MODEL',
  'PORT',
  'MAX_INPUT_CHARS',
  'MAX_MEMORY_CHARS',
  'REQUEST_TIMEOUT_MS'
]);

await loadEnvFiles();

const app = createProxyApp();
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
    console.log(`Ojo Claro AI proxy listening on http://${host}:${port}`);
  });
}

export function createProxyApp({
  env = process.env,
  fetchImpl = globalThis.fetch
} = {}) {
  const config = readConfig(env);

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

    if (request.method === 'POST' && url.pathname === '/v1/interpret') {
      return interpretRequest(request, config, fetchImpl);
    }

    return jsonResponse(404, {
      ok: false,
      error: 'not_found'
    });
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

async function interpretRequest(request, config, fetchImpl) {
  if (!config.apiKey) {
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
    return jsonResponse(403, safeResponse('sensitive_data', 'No interpreto datos sensibles.'));
  }

  const openAiResponse = await callOpenAI(fetchImpl, config, requestPayload);
  if (!openAiResponse.ok) {
    return jsonResponse(openAiResponse.statusCode, safeResponse(openAiResponse.errorCode, openAiResponse.message));
  }

  return jsonResponse(200, openAiResponse.response);
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
  const response = await fetchWithTimeout(
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

  if (!response.ok) {
    const fallback = await parseErrorResponse(response, 'openai_http_error');
    return {
      ok: false,
      statusCode: response.status,
      errorCode: fallback.errorCode,
      message: fallback.message
    };
  }

  const data = safeParseJson(await response.text());
  if (!data.ok) {
    return {
      ok: false,
      statusCode: 502,
      errorCode: 'openai_invalid_json',
      message: 'OpenAI devolvió JSON inválido.'
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
      message: 'No pude interpretar la respuesta del modelo.'
    };
  }

  const normalized = normalizeResponse(parsed.value);
  return {
    ok: true,
    response: enrichResponseFromRequest(requestPayload, normalized)
  };
}

function normalizeResponse(response) {
  const intent = response?.intent ?? null;
  const shouldExecuteImmediately = Boolean(response?.shouldExecuteImmediately);
  const forbiddenIntents = new Set(['OPEN_WHATSAPP', 'OPEN_WHATSAPP_CHAT', 'COMPOSE_WHATSAPP_MESSAGE', 'CALL_CONTACT', 'OPEN_PHONE', 'OPEN_MAPS', 'NAVIGATE_TO_DESTINATION']);

  return {
    intent,
    confidence: clampNumber(response?.confidence, 0, 1, 0),
    contactName: nullableString(response?.contactName),
    messageText: nullableString(response?.messageText),
    proposedMessage: nullableString(response?.proposedMessage),
    destination: nullableString(response?.destination),
    locationAlias: nullableString(response?.locationAlias),
    routineName: nullableString(response?.routineName),
    pendingTask: nullableString(response?.pendingTask),
    missingSlots: toStringArray(response?.missingSlots),
    userFacingQuestion: nullableString(response?.userFacingQuestion),
    suggestionText: nullableString(response?.suggestionText),
    requiresConfirmation: Boolean(response?.requiresConfirmation),
    shouldExecuteImmediately: intent && forbiddenIntents.has(intent) ? false : shouldExecuteImmediately,
    safetyNotes: nullableString(response?.safetyNotes)
  };
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

  const matches = raw.match(/(?:a|para|con|de|del|la|el|mi|mi\\s+)?([A-ZÁÉÍÓÚÑ][\\p{L}'-]*(?:\\s+[A-ZÁÉÍÓÚÑ][\\p{L}'-]*){0,2})/u);
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
  const warmContact = contactName && /sofi|sofí|sofia/.test(contactName.toLowerCase());

  if (lower.includes('llego tarde') || lower.includes('voy tarde') || lower.includes('llego demorado')) {
    return warmContact
      ? 'Amor, voy un poco demorado. Llego en unos minutos.'
      : 'Voy un poco demorado. Llego en unos minutos.';
  }

  if (lower.includes('llego en 10') || lower.includes('llego en diez')) {
    return warmContact
      ? 'Amor, llego en 10 minutos. Ya salgo.'
      : 'Llego en 10 minutos. Ya salgo.';
  }

  if (lower.includes('estoy llegando')) {
    return warmContact ? 'Amor, ya estoy llegando.' : 'Ya estoy llegando.';
  }

  if (!messageText) {
    return 'Te escribo en un momento.';
  }

  return messageText.replace(/^([a-záéíóúñ]+)\s+/i, (match) => match.trim());
}

function inferComposeQuestion(contactName, proposedMessage) {
  if (contactName && proposedMessage) {
    return `Te propongo: ${proposedMessage} ¿Lo preparo?`;
  }
  if (contactName) {
    return `¿Querés mandarle un mensaje a ${contactName}?`;
  }
  return '¿Querés que lo prepare?';
}

function escapeRegExp(value) {
  return String(value).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function safeResponse(code, message) {
  return normalizeResponse({
    intent: null,
    confidence: 0,
    contactName: null,
    messageText: null,
    proposedMessage: null,
    destination: null,
    locationAlias: null,
    routineName: null,
    pendingTask: null,
    missingSlots: [],
    userFacingQuestion: 'No uso la IA ahora. Probá decirlo más simple.',
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
      errorCode: parsed.value?.error?.code || fallbackCode,
      message
    };
  }
  return {
    errorCode: fallbackCode,
    message: fallbackCode
  };
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
