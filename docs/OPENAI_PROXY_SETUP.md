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
PORT=8787
MAX_INPUT_CHARS=1200
MAX_MEMORY_CHARS=800
REQUEST_TIMEOUT_MS=12000
OPENAI_REASONING_EFFORT=medium
```

Do not commit `.env`.

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
  "hasApiKey": true
}
```

## Interpret check

```bash
curl -X POST http://127.0.0.1:8787/v1/interpret \
  -H "Content-Type: application/json" \
  -d "{\"originalText\":\"decile a Sofi que llego tarde pero decilo bien\",\"normalizedText\":\"decir a Sofi que llego tarde pero decirlo bien\",\"locale\":\"es-AR\",\"agentState\":\"WAITING_MESSAGE\",\"externalApp\":\"WhatsApp\",\"memorySummary\":\"Contacto Sofi.\",\"knownSafeContacts\":[\"Sofi\"],\"knownPlaces\":[\"casa\",\"laburo\"],\"activePendingTasks\":[],\"allowedIntents\":[\"COMPOSE_WHATSAPP_MESSAGE\"],\"forbiddenActions\":[\"read_contacts\",\"call_phone\",\"action_call\"]}"
```

## Android base URLs

- Emulator: `http://10.0.2.2:8787`
- Samsung on LAN: `http://192.168.x.x:8787`

Set the Android debug base URL to the proxy address you want to test.

## Stop GPT mini

1. Clear `OPENAI_API_KEY` from `tools/ojo_claro_ai_proxy/.env`
2. Restart the proxy

