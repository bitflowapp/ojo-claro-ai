import json

from fastapi.testclient import TestClient

from app.main import app
from app.services import intent_service

client = TestClient(app)


def _android_payload(text: str) -> dict:
    return {
        "model": "gpt-5.4-mini",
        "system_prompt_id": "OJO_CLARO_INTENT_ENGINE_SYSTEM",
        "input": {
            "user_text": text,
            "conversation_state": {},
            "pending_action": None,
            "installed_apps": [],
            "memory_contacts": [],
            "active_app": None,
            "permissions_granted": {},
        },
    }


def _assert_android_compatible(data: dict, raw_text: str) -> None:
    assert set(data.keys()) == {
        "intent",
        "confidence",
        "params",
        "safety_level",
        "voice_response",
        "voice_response_template",
        "raw_text",
    }
    assert isinstance(data["intent"], str)
    assert isinstance(data["confidence"], float)
    assert isinstance(data["params"], dict)
    assert data["safety_level"] in intent_service.VALID_SAFETY_LEVELS
    assert data["raw_text"] == raw_text


def test_intent_exists_and_falls_back_without_api_key(monkeypatch) -> None:
    monkeypatch.setattr(intent_service.settings, "openai_api_key", "")
    monkeypatch.setattr(intent_service.settings, "allow_mock_ai", False)

    response = client.post("/intent", json=_android_payload("hola"))

    assert response.status_code == 200
    assert response.headers["x-ojo-intent-source"] == "fallback"
    assert response.headers["x-ojo-intent-model"] == "none"
    data = response.json()
    _assert_android_compatible(data, "hola")
    assert data["intent"] == "unknown"


def test_intent_uses_fallback_model_when_primary_model_is_rejected(monkeypatch) -> None:
    calls: list[str] = []

    async def fake_call_openai(system_prompt: str, input_json: str, model: str):
        calls.append(model)
        if model == "gpt-5.4-mini":
            return False, "http_404: model does not exist", True
        return True, json.dumps({
            "intent": "help",
            "confidence": 0.91,
            "params": {},
            "safety_level": "allow_safe",
            "voice_response": "Puedo ayudarte a abrir apps y preparar mensajes.",
            "voice_response_template": None,
            "raw_text": "hola",
        }), False

    monkeypatch.setattr(intent_service.settings, "openai_api_key", "test-key")
    monkeypatch.setattr(intent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(intent_service.settings, "openai_model", "gpt-5.4-mini")
    monkeypatch.setattr(intent_service.settings, "openai_model_fallback", "gpt-fallback-test")
    monkeypatch.setattr(intent_service, "_call_openai", fake_call_openai)

    response = client.post("/intent", json=_android_payload("hola"))

    assert response.status_code == 200
    assert response.headers["x-ojo-intent-source"] == "gpt"
    assert response.headers["x-ojo-intent-model"] == "gpt-fallback-test"
    assert calls == ["gpt-5.4-mini", "gpt-fallback-test"]
    data = response.json()
    _assert_android_compatible(data, "hola")
    assert data["intent"] == "help"


def test_intent_does_not_mark_sensitive_action_as_safe(monkeypatch) -> None:
    async def fake_call_openai(system_prompt: str, input_json: str, model: str):
        return True, json.dumps({
            "intent": "compose_whatsapp_message",
            "confidence": 0.96,
            "params": {"contact_query": "Sofi", "message_text": "ya llegue"},
            "safety_level": "allow_safe",
            "voice_response": "Preparo el mensaje para Sofi.",
            "voice_response_template": None,
            "raw_text": "mandale a Sofi que ya llegue",
        }), False

    monkeypatch.setattr(intent_service.settings, "openai_api_key", "test-key")
    monkeypatch.setattr(intent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(intent_service.settings, "openai_model", "gpt-5.4-mini")
    monkeypatch.setattr(intent_service.settings, "openai_model_fallback", "gpt-fallback-test")
    monkeypatch.setattr(intent_service, "_call_openai", fake_call_openai)

    response = client.post("/intent", json=_android_payload("mandale a Sofi que ya llegue"))

    assert response.status_code == 200
    data = response.json()
    _assert_android_compatible(data, "mandale a Sofi que ya llegue")
    assert data["intent"] == "compose_whatsapp_message"
    assert data["safety_level"] == "prepare_only"
