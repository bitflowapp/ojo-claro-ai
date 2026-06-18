"""V1.7 — conversación libre de Estela: seguridad y contrato."""

import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services.conversation_service import (
    ESTELA_CONVERSATION_PROMPT,
    converse,
    sanitize_short_memory,
)

client = TestClient(app)


class StubOpenAiResponse:
    def __init__(self, status_code: int, payload: dict | None = None, text: str = ""):
        self.status_code = status_code
        self._payload = payload or {}
        self.text = text

    def json(self) -> dict:
        return self._payload


def test_crossing_questions_never_reach_the_model():
    # Camino sin LLM: respuesta fija prudente, safety_level=caution.
    import asyncio

    result = asyncio.run(converse("¿es seguro cruzar la calle?"))
    assert result["safety_level"] == "caution"
    assert "No puedo confirmar" in result["reply"]
    assert "bastón" in result["reply"]


def test_crossing_questions_do_not_call_assistant_service(monkeypatch):
    import asyncio

    from app.services.assistant_service import AssistantService

    async def fail_if_called(self, instructions: str, content: list[dict]) -> str:
        raise AssertionError("crossing safety must not call the model")

    monkeypatch.setattr(AssistantService, "_openai_response", fail_if_called)

    result = asyncio.run(converse("Es seguro cruzar?"))

    assert result["ok"] is True
    assert result["safety_level"] == "caution"
    assert result["reply"]


def test_sensitive_content_blocked_without_model():
    import asyncio

    result = asyncio.run(converse("mi clave es 12345678"))
    assert result["safety_level"] == "blocked"
    assert "12345678" not in result["reply"]


def test_short_memory_sanitizes_secrets_and_caps_turns():
    memory = [
        "hola",
        "mi token es ABC123",
        "tarjeta 4509 9535 6623 3704",
        "describime el entorno",
        "gracias",
        "qué hago ahora",
        "estoy nervioso",
    ]
    clean = sanitize_short_memory(memory)
    assert len(clean) <= 5
    joined = " ".join(clean)
    assert "ABC123" not in joined
    assert "4509" not in joined
    assert "[turno omitido por contenido sensible]" in joined


def test_prompt_enforces_safety_rules():
    prompt = ESTELA_CONVERSATION_PROMPT
    assert "Nunca confirmes que es seguro cruzar" in prompt
    assert "sin confirmación explícita" in prompt
    assert "1 a 3 frases" in prompt
    assert "No inventes capacidades" in prompt
    assert "no decís que sentís emociones reales" in prompt


def test_assistant_service_uses_fallback_when_primary_model_returns_400(monkeypatch):
    import asyncio

    from app.services import assistant_service
    from app.services.assistant_service import AssistantService

    calls: list[str] = []

    async def fake_openai_request(
        self, model: str, instructions: str, content: list[dict]
    ) -> StubOpenAiResponse:
        calls.append(model)
        if model == "gpt-primary-test":
            return StubOpenAiResponse(400, text='{"error":"model rejected"}')
        return StubOpenAiResponse(200, {"output_text": "Acá estoy con vos."})

    monkeypatch.setattr(assistant_service.settings, "openai_api_key", "test-key")
    monkeypatch.setattr(assistant_service.settings, "openai_model", "gpt-primary-test")
    monkeypatch.setattr(assistant_service.settings, "openai_model_fallback", "gpt-fallback-test")
    monkeypatch.setattr(AssistantService, "_openai_request", fake_openai_request)

    reply = asyncio.run(
        AssistantService()._openai_response(
            instructions="test instructions",
            content=[{"type": "input_text", "text": "Estoy nervioso"}],
        )
    )

    assert reply == "Acá estoy con vos."
    assert calls == ["gpt-primary-test", "gpt-fallback-test"]


def test_assistant_service_extracts_responses_api_text_formats():
    from app.services.assistant_service import AssistantService

    service = AssistantService()

    assert service._extract_output_text({"output_text": " Texto directo "}) == "Texto directo"
    assert service._extract_output_text(
        {
            "output": [
                {
                    "content": [
                        {"type": "output_text", "text": "Primer bloque"},
                        {"type": "output_text", "text": "Segundo bloque"},
                    ]
                }
            ]
        }
    ) == "Primer bloque\nSegundo bloque"


def test_endpoint_returns_non_empty_reply(monkeypatch):
    from app.services import conversation_service

    monkeypatch.setattr(conversation_service.settings, "allow_mock_ai", True)

    response = client.post(
        "/api/v1/conversation",
        json={
            "user_text": "Estoy nervioso",
            "conversation_state": {
                "active_app": "UNKNOWN",
                "route_active": False,
                "whatsapp_pending": False,
                "short_memory": [],
            },
        },
    )

    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert body["reply"].strip()
    assert body["safety_level"] == "normal"


def test_endpoint_contract(monkeypatch):
    import app.routes.conversation as conversation_routes

    async def fake_converse(user_text: str, conversation_state=None) -> dict:
        return {
            "ok": True,
            "reply": "Acá estoy con vos.",
            "safety_level": "normal",
            "suggested_followup": False,
        }

    monkeypatch.setattr(conversation_routes, "converse", fake_converse)
    response = client.post(
        "/api/v1/conversation",
        json={"user_text": "hola estela", "conversation_state": {"route_active": False}},
    )
    assert response.status_code == 200
    body = response.json()
    assert body["ok"] is True
    assert len(body["reply"]) < 330


def test_endpoint_rejects_empty_text():
    response = client.post("/conversation", json={"user_text": ""})
    assert response.status_code == 422
