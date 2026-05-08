# Ojo Claro AI Proxy

Small local backend that keeps the OpenAI API key out of Android.

## What it does

- reads `OPENAI_API_KEY` from a local `.env`
- exposes `GET /health`
- exposes `POST /v1/interpret`
- calls GPT-5.4 mini
- returns strict JSON for Ojo Claro

## Setup

1. Copy `.env.example` to `.env`
2. Fill `OPENAI_API_KEY`
3. Keep the file private and uncommitted

## Run

```bash
cd tools/ojo_claro_ai_proxy
node server.mjs
```

## Health check

```bash
curl http://127.0.0.1:8787/health
```

## Interpret example

```bash
curl -X POST http://127.0.0.1:8787/v1/interpret ^
  -H "Content-Type: application/json" ^
  -d "{\"originalText\":\"decile a ContactoDemo que llego tarde pero decilo bien\",\"normalizedText\":\"decir a ContactoDemo que llego tarde\",\"locale\":\"es-AR\",\"agentState\":\"WAITING_MESSAGE\",\"externalApp\":\"WhatsApp\",\"memorySummary\":\"Contacto demo guardado.\",\"knownSafeContacts\":[\"ContactoDemo\"],\"knownPlaces\":[\"casa\",\"laburo\"],\"activePendingTasks\":[],\"allowedIntents\":[\"COMPOSE_WHATSAPP_MESSAGE\"],\"forbiddenActions\":[\"read_contacts\",\"call_phone\",\"action_call\"]}"
```

## Android

- Emulator: `http://10.0.2.2:8787`
- Samsung on LAN: `http://192.168.x.x:8787`

## Stop GPT mini

- empty or remove `OPENAI_API_KEY`
- restart the proxy
