# OpenAI Proxy Setup

## Goal

Run GPT mini behind a local proxy so the Android app never contains the OpenAI API key.

## Files

- `tools/ojo_claro_ai_proxy/package.json`
- `tools/ojo_claro_ai_proxy/server.mjs`
- `tools/ojo_claro_ai_proxy/.env.example`
- `tools/ojo_claro_ai_proxy/README.md`

## Create the local `.env`

Copy `tools/ojo_claro_ai_proxy/.env.example` to `tools/ojo_claro_ai_proxy/.env` and fill it locally:

```env
OPENAI_API_KEY=your_real_key_here
OPENAI_MODEL=gpt-5.4-mini
HOST=0.0.0.0
PORT=8787
MAX_INPUT_CHARS=1200
MAX_MEMORY_CHARS=800
REQUEST_TIMEOUT_MS=12000
OPENAI_REASONING_EFFORT=medium
```

Do not commit `.env`.

## Priority rule

The proxy-local `.env` wins for these settings:

- `OPENAI_MODEL`
- `PORT`
- `MAX_INPUT_CHARS`
- `MAX_MEMORY_CHARS`
- `REQUEST_TIMEOUT_MS`

That means a Windows global variable like `OPENAI_MODEL=openai-codex/gpt-5.3-codex` will be ignored if the proxy `.env` says `OPENAI_MODEL=gpt-5.4-mini`.

`OPENAI_API_KEY` can still come from the proxy `.env` or the environment, but it is never printed.

## Request shape

This first integration uses Chat Completions with a minimal JSON request:

- `model`
- `messages`
- `response_format`
- `temperature`
- `max_completion_tokens`

It does **not** send `reasoning`, so the proxy stays compatible and cheap for the first GPT mini pass.

## Run the proxy

```bash
cd tools/ojo_claro_ai_proxy
node server.mjs
```

## Health check

```bash
curl http://127.0.0.1:8787/health
```

Expected:

```json
{
  "ok": true,
  "model": "gpt-5.4-mini",
  "hasApiKey": true,
  "host": "0.0.0.0",
  "port": 8787
}
```

## Interpret check

```bash
curl -X POST http://127.0.0.1:8787/v1/interpret \
  -H "Content-Type: application/json" \
  -d "{\"originalText\":\"decile a Sofi que llego tarde pero decilo bien\",\"normalizedText\":\"decir a Sofi que llego tarde pero decirlo bien\",\"locale\":\"es-AR\",\"agentState\":\"WAITING_MESSAGE\",\"externalApp\":\"WhatsApp\",\"memorySummary\":\"Contacto Sofi.\",\"knownSafeContacts\":[\"Sofi\"],\"knownPlaces\":[\"casa\",\"laburo\"],\"activePendingTasks\":[],\"allowedIntents\":[\"COMPOSE_WHATSAPP_MESSAGE\"],\"forbiddenActions\":[\"read_contacts\",\"call_phone\",\"action_call\"]}"
```

## Android base URLs

- PC local: `http://127.0.0.1:8787`
- Emulator Android: `http://10.0.2.2:8787`
- Samsung físico: `http://IP_DE_LA_PC:8787`

To find the PC IP on Windows:

```powershell
ipconfig
```

Use the IPv4 address of the Wi-Fi adapter that is on the same network as the Samsung.

The PC and the Samsung must be on the same Wi-Fi.
If Windows Firewall blocks the proxy, allow Node.js on the private network.

Set the Android debug base URL to the proxy address you want to test.

For emulator, the default debug build uses:

```text
http://10.0.2.2:8787
```

For Samsung physical QA, build with the LAN URL:

```powershell
.\gradlew.bat :androidApp:assembleDebug -PojoClaroAssistantBaseUrl=http://IP_DE_LA_PC:8787 --console=plain
```

Alternative local environment variable:

```powershell
$env:OJO_CLARO_ASSISTANT_BASE_URL="http://IP_DE_LA_PC:8787"
.\gradlew.bat :androidApp:assembleDebug --console=plain
```

Release alpha builds keep the URL empty unless explicitly configured. In that mode Android must degrade safely with local rules and a clear "IA flexible apagada" message. Never put `OPENAI_API_KEY` in Android.

## Stop GPT mini

1. Clear `OPENAI_API_KEY` from `tools/ojo_claro_ai_proxy/.env`
2. Restart the proxy
