# Ojo Claro AI Proxy

Local backend that keeps the OpenAI API key out of the Android APK.

## What it does

- Reads `OPENAI_API_KEY` from a local `.env` (never bundled in the APK).
- Talks to OpenAI Chat Completions with model `gpt-5.4-mini` by default.
- Exposes `GET /health`, `GET /metrics`, and `POST /v1/interpret`.
- Returns strict JSON for Ojo Claro.
- Enforces an intent whitelist v1 — any intent the model proposes outside the list is rewritten to `UNKNOWN`.
- Blocks sensitive content (bancos, contraseñas, tarjetas, OTP, etc.) before talking to OpenAI.
- Never logs the API key (`buildStartupBanner` only reports `hasApiKey=true|false`; `redactSecrets` scrubs `sk-...` patterns).

## Setup

1. Copy `.env.example` to `.env` (this directory).
2. Fill `OPENAI_API_KEY=sk-...` with your real key.
3. Keep `.env` private. It is gitignored — do NOT commit it.

`.env` template:

```env
OPENAI_API_KEY=sk-...
OPENAI_MODEL=gpt-5.4-mini
HOST=0.0.0.0
PORT=8787
MAX_INPUT_CHARS=1200
MAX_MEMORY_CHARS=800
REQUEST_TIMEOUT_MS=12000
```

## Run

```bash
cd tools/ojo_claro_ai_proxy
node server.mjs
```

On start, the proxy prints a single line that includes the model and `hasApiKey=true|false`, but never the key itself.

## Smoke tests

```bash
# Health (should show model + hasApiKey)
curl http://127.0.0.1:8787/health

# Metrics (safe counters only; no input, output, headers, or API key)
curl http://127.0.0.1:8787/metrics

# Sample interpret call (HELP intent — safe and inside whitelist v1)
curl -X POST http://127.0.0.1:8787/v1/interpret ^
  -H "Content-Type: application/json" ^
  -d "{\"originalText\":\"que podes hacer\",\"normalizedText\":\"que podes hacer\",\"locale\":\"es-AR\",\"agentState\":\"IDLE\",\"externalApp\":null,\"memorySummary\":\"\",\"knownSafeContacts\":[],\"knownPlaces\":[],\"activePendingTasks\":[],\"allowedIntents\":[\"HELP\"],\"forbiddenActions\":[]}"
```

## Whitelist v1

The proxy only allows the model to return one of these intents. Anything else is rewritten to `UNKNOWN` with `safetyNotes: "intent_outside_whitelist_v1"`:

- HELP
- READ_VISIBLE_SCREEN
- OPEN_WHATSAPP
- WHATSAPP_GUIDED_HELP
- WHATSAPP_VISIBLE_CHATS
- REPEAT_LAST
- STOP_SPEAKING
- CANCEL
- RESET_FLOW
- UNKNOWN

Android additionally enforces the same whitelist client-side via `SafeAiFallbackGuard.WHITELIST_V1` (defense in depth).

## Android base URLs

Set `ASSISTANT_BASE_URL` in `gradle.properties` or via `BuildConfig`:

- Android emulator: `http://10.0.2.2:8787`
- Samsung físico en la misma red LAN: `http://<IP_DE_TU_PC>:8787` (do NOT use `127.0.0.1` — that points to the phone itself).

Find your PC IP:

```powershell
ipconfig | findstr IPv4
```

## Stop using GPT mini

- Leave `OPENAI_API_KEY` empty (or remove `.env`) and restart the proxy.
- `/health` will return `hasApiKey: false`.
- Android degrades silently via `SafeAiFallbackCopy.contextual(...)` — the user never hears "no estoy usando la IA".

## Tests

```bash
cd tools/ojo_claro_ai_proxy
npm test
```

Covers: env loading priority, default model, model override, missing key fallback, whitelist enforcement, key never in logs, sensitive-data block, host/port reporting, and safe `/metrics` counters.
