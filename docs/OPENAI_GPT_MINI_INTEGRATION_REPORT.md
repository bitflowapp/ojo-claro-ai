# OpenAI GPT Mini Integration Report

## Summary

Ojo Claro now has a local proxy architecture for GPT mini.

Android:

- sends a compact `LlmAgentRequest`
- receives a strict `LlmAgentResponse`
- validates with `LlmSafetyPolicy`
- keeps the OpenAI API key out of the APK

Proxy:

- reads `OPENAI_API_KEY` from a local `.env`
- calls GPT-5.4 mini
- truncates oversized inputs
- rejects sensitive payloads
- returns controlled fallback JSON on failure

## What changed

- added `tools/ojo_claro_ai_proxy`
- added Android client config and network client
- added `OpenAiProxyAgentInterpreter`
- added `LlmUsageGuard`
- moved the JSON contract to a Kotlinx-based implementation

## Safety

- the Android app never stores the API key
- `shouldExecuteImmediately` is forced off for WhatsApp, Maps, Phone and similar sensitive flows
- low confidence falls back to local logic
- proxy errors still return a parseable JSON body

## Cost control

- local parser first
- usage guard caps calls per session and day
- proxy truncates prompt input and memory summaries
- the model is only called for ambiguous or human-like phrasing

## Next step

Wire the proxy base URL to the device being tested and validate the live flow:

- emulator: `http://10.0.2.2:8787`
- Samsung: `http://<pc-lan-ip>:8787`

