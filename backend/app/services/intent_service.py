"""Servicio del endpoint /intent.

GPT SOLO clasifica o responde; NUNCA ejecuta acciones. Android decide las acciones
con sus routers locales seguros. Este servicio:
  - llama a OpenAI (modelo de env, con fallback configurable),
  - parsea JSON estricto con el esquema que espera el adapter de Android,
  - aplica una red de seguridad (no permite degradar acciones sensibles a allow_safe),
  - degrada a un fallback verbal honesto si no hay API key o si algo falla.
"""

from __future__ import annotations

import json
import logging

import httpx

from app.core.config import settings
from app.services.intent_prompt import load_intent_system_prompt

logger = logging.getLogger("ojoclaro.intent")

OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"
MAX_OUTPUT_TOKENS = 400
HTTP_TIMEOUT_SECONDS = 30.0

VALID_SAFETY_LEVELS = ("allow_safe", "prepare_only", "requires_confirm", "blocked_sensitive")
_SAFETY_RANK = {level: rank for rank, level in enumerate(VALID_SAFETY_LEVELS)}

# Piso de seguridad por intención: GPT no puede degradar estas acciones por debajo
# de este nivel. Defensa en profundidad además del prompt.
SENSITIVE_MIN_SAFETY = {
    "compose_whatsapp_message": "prepare_only",
    "call_contact": "requires_confirm",
    "read_visible_screen": "requires_confirm",
    "navigate_to_destination": "requires_confirm",
    "save_location_alias": "requires_confirm",
    "delete_location_alias": "requires_confirm",
    "save_contact": "requires_confirm",
    "save_contact_phone": "requires_confirm",
    "delete_contact": "requires_confirm",
    "remember_memory": "requires_confirm",
    "clear_memory": "requires_confirm",
}

# Fuentes posibles del resultado (se reportan por header, NO en el body).
SOURCE_GPT = "gpt"
SOURCE_FALLBACK = "fallback"
SOURCE_MOCK = "mock"


class IntentOutcome:
    def __init__(self, payload: dict, source: str, model_used: str) -> None:
        self.payload = payload
        self.source = source
        self.model_used = model_used


def _fallback_payload(user_text: str, voice: str) -> dict:
    return {
        "intent": "unknown",
        "confidence": 0.0,
        "params": {},
        "safety_level": "allow_safe",
        "voice_response": voice,
        "voice_response_template": None,
        "raw_text": user_text,
    }


def _mock_payload(user_text: str) -> dict:
    # Respuesta determinista para tests / ALLOW_MOCK_AI. No llama a la red.
    return _fallback_payload(
        user_text,
        "Estoy en modo de prueba local. No consulté la IA en la nube.",
    )


def sanitize_intent_payload(raw: dict, user_text: str) -> dict:
    """Normaliza y endurece la salida del modelo al contrato de Android.

    - Garantiza claves y tipos.
    - safety_level inválido → requires_confirm (conservador).
    - Acciones sensibles no pueden quedar por debajo de su piso de seguridad.
    """
    if not isinstance(raw, dict):
        return _fallback_payload(user_text, "No pude interpretar eso con seguridad. Probá de nuevo.")

    intent = raw.get("intent")
    if not isinstance(intent, str) or not intent.strip():
        intent = "unknown"
    intent = intent.strip()

    try:
        confidence = float(raw.get("confidence", 0.0))
    except (TypeError, ValueError):
        confidence = 0.0
    confidence = max(0.0, min(1.0, confidence))

    params = raw.get("params")
    if not isinstance(params, dict):
        params = {}

    safety_level = raw.get("safety_level")
    if safety_level not in VALID_SAFETY_LEVELS:
        # Si la intención es sensible, subimos al piso; si no, requires_confirm.
        safety_level = SENSITIVE_MIN_SAFETY.get(intent, "requires_confirm")

    # Piso de seguridad para acciones sensibles (no permitir downgrade del modelo).
    floor = SENSITIVE_MIN_SAFETY.get(intent)
    if floor and _SAFETY_RANK[safety_level] < _SAFETY_RANK[floor]:
        safety_level = floor

    voice_response = raw.get("voice_response")
    if voice_response is not None and not isinstance(voice_response, str):
        voice_response = None
    voice_response_template = raw.get("voice_response_template")
    if voice_response_template is not None and not isinstance(voice_response_template, str):
        voice_response_template = None

    raw_text = raw.get("raw_text")
    if not isinstance(raw_text, str) or not raw_text:
        raw_text = user_text

    return {
        "intent": intent,
        "confidence": confidence,
        "params": params,
        "safety_level": safety_level,
        "voice_response": voice_response,
        "voice_response_template": voice_response_template,
        "raw_text": raw_text,
    }


def _strip_json_text(text: str) -> str:
    cleaned = text.strip()
    if cleaned.startswith("```"):
        # Quita una posible cerca de código ```json ... ```
        cleaned = cleaned.split("```", 2)[1] if cleaned.count("```") >= 2 else cleaned
        if cleaned.lstrip().lower().startswith("json"):
            cleaned = cleaned.lstrip()[4:]
    return cleaned.strip()


def _extract_output_text(payload: dict) -> str:
    direct = payload.get("output_text")
    if isinstance(direct, str) and direct.strip():
        return direct.strip()
    parts: list[str] = []
    for item in payload.get("output", []) or []:
        for content in item.get("content", []) or []:
            text = content.get("text")
            if isinstance(text, str) and text.strip():
                parts.append(text.strip())
    return "\n".join(parts).strip()


def _looks_like_model_error(status_code: int, body: str) -> bool:
    if status_code not in (400, 404):
        return False
    low = body.lower()
    return "model" in low and ("not" in low or "does not exist" in low or "invalid" in low)


async def _call_openai(system_prompt: str, input_json: str, model: str) -> tuple[bool, str, bool]:
    """Devuelve (ok, text_o_error, is_model_error)."""
    payload = {
        "model": model,
        "instructions": system_prompt,
        "input": [{"role": "user", "content": [{"type": "input_text", "text": input_json}]}],
        "max_output_tokens": MAX_OUTPUT_TOKENS,
    }
    headers = {
        "Authorization": f"Bearer {settings.openai_api_key}",
        "Content-Type": "application/json",
    }
    async with httpx.AsyncClient(timeout=HTTP_TIMEOUT_SECONDS) as client:
        response = await client.post(OPENAI_RESPONSES_URL, headers=headers, json=payload)
    if response.status_code >= 400:
        body = response.text[:500]
        return False, f"http_{response.status_code}: {body}", _looks_like_model_error(response.status_code, body)
    text = _extract_output_text(response.json())
    if not text:
        return False, "empty_output", False
    return True, text, False


async def classify_intent(canonical_input: dict) -> IntentOutcome:
    user_text = canonical_input.get("user_text", "")

    if settings.allow_mock_ai:
        logger.info("intent mock response (ALLOW_MOCK_AI) text_len=%d", len(user_text))
        return IntentOutcome(_mock_payload(user_text), SOURCE_MOCK, "mock")

    if not settings.has_openai_api_key:
        logger.warning("intent fallback: OPENAI_API_KEY missing")
        return IntentOutcome(
            _fallback_payload(user_text, "No tengo conexión con la IA ahora. Probá de nuevo en un momento."),
            SOURCE_FALLBACK,
            "none",
        )

    system_prompt = load_intent_system_prompt()
    input_json = json.dumps(canonical_input, ensure_ascii=False)

    # Modelos a intentar: primario y, solo si el primario falla por modelo, el fallback.
    models: list[str] = []
    for candidate in (settings.openai_model, settings.openai_model_fallback):
        c = (candidate or "").strip()
        if c and c not in models:
            models.append(c)

    last_error = "unknown_error"
    for index, model in enumerate(models):
        try:
            ok, result, is_model_error = await _call_openai(system_prompt, input_json, model)
        except httpx.HTTPError as error:
            last_error = f"network_error: {error.__class__.__name__}"
            logger.warning("intent OpenAI network error model=%s err=%s", model, error.__class__.__name__)
            break  # red caída: no tiene sentido reintentar el fallback
        if ok:
            try:
                parsed = json.loads(_strip_json_text(result))
            except json.JSONDecodeError:
                logger.warning("intent OpenAI returned non-JSON model=%s", model)
                last_error = "malformed_json"
                break
            payload = sanitize_intent_payload(parsed, user_text)
            logger.info("intent gpt ok model=%s intent=%s safety=%s", model, payload["intent"], payload["safety_level"])
            return IntentOutcome(payload, SOURCE_GPT, model)

        last_error = result
        # Solo reintentamos con el fallback si el error fue por modelo no disponible.
        if is_model_error and index + 1 < len(models):
            logger.warning("intent model unavailable=%s detail=%s -> trying fallback", model, result[:300])
            continue
        logger.warning("intent OpenAI error model=%s detail=%s", model, result[:120])
        break

    return IntentOutcome(
        _fallback_payload(
            user_text,
            "No pude interpretar eso ahora. Probá de nuevo o decilo más corto.",
        ),
        SOURCE_FALLBACK,
        "none",
    )
