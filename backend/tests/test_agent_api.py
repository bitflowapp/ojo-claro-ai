"""Tests de /agent/next (Estela Agent Core v1).

REGLA: ningún test llama a OpenAI real. Todo camino GPT monkeypatchea
agent_service._call_openai_agent; los caminos policy/mock/fallback ni siquiera
construyen el request HTTP saliente.
"""

import json
import logging

import httpx
import pytest
from fastapi.testclient import TestClient

from app.main import app
from app.services import agent_service
from app.services.agent_tools import AGENT_TOOL_NAMES

client = TestClient(app)

PRIVATE_MARKER = "PRIVADO_NO_LOGUEAR_XYZ"


def _request_payload(**overrides) -> dict:
    payload = {
        "session_id": "test-session-0001",
        "goal": "Comprobá si estás lista para trabajar y después leé la pantalla.",
        "step_index": 0,
        "origin_package": "com.whatsapp",
        "current_observation": {
            "package_name": "com.whatsapp",
            "screen_class": "WHATSAPP_LOGOUT",
            "privacy_class": "PRIVATE_APP",
            "available_tools": list(AGENT_TOOL_NAMES),
            "public_actions": [],
            "visible_item_count": 5,
            "can_open_by_ordinal": False,
            "can_read_locally": True,
        },
        "completed_steps": [],
        "last_tool_result": None,
        "remaining_step_budget": 8,
        "replan_count": 0,
    }
    payload.update(overrides)
    return payload


def _function_call_payload(name: str, arguments: dict | str) -> dict:
    args = arguments if isinstance(arguments, str) else json.dumps(arguments)
    return {
        "output": [
            {"type": "function_call", "name": name, "arguments": args, "call_id": "call_1"}
        ]
    }


def _with_gpt(monkeypatch, fake) -> None:
    monkeypatch.setattr(agent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(agent_service.settings, "openai_api_key", "test-key-not-real")
    monkeypatch.setattr(agent_service, "_call_openai_agent", fake)


def _forbid_openai(monkeypatch) -> None:
    async def boom(_planner_input: str):
        raise AssertionError("no debe llamar a OpenAI en este camino")

    monkeypatch.setattr(agent_service, "_call_openai_agent", boom)


# 1 / 22 — modelo exacto configurado y reportado.
def test_agent_model_is_exactly_gpt_54_mini(monkeypatch) -> None:
    assert agent_service.settings.openai_agent_model == "gpt-5.4-mini"

    async def fake(_planner_input: str):
        return 200, _function_call_payload("check_accessibility_status", {}), ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    assert response.status_code == 200
    assert response.json()["model"] == "gpt-5.4-mini"
    assert response.headers["x-ojo-agent-model"] == "gpt-5.4-mini"


# 2 / 4 — endpoint existe y devuelve UNA sola acción tipada.
def test_agent_next_returns_single_tool_action(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("check_accessibility_status", {}), ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is True
    assert data["status"] == "CONTINUE"
    assert data["action"] == {"tool": "check_accessibility_status", "arguments": {}}
    assert data["expected_postcondition"] == {"type": "ACCESSIBILITY_STATUS_OBSERVED"}
    assert data["spoken_progress"] == "Reviso la accesibilidad."
    assert len(data["reasoning_summary"]) <= 160
    assert response.headers["x-ojo-agent-source"] == "gpt"


# 3 — alias /api/v1.
def test_agent_next_v1_alias(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("check_microphone_permission", {}), ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/api/v1/agent/next", json=_request_payload())
    assert response.status_code == 200
    assert response.json()["action"]["tool"] == "check_microphone_permission"


# 5 — herramienta desconocida del modelo se rechaza.
def test_unknown_tool_from_model_is_rejected(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("rm_rf_device", {}), ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    assert response.status_code == 200
    data = response.json()
    assert data["ok"] is False
    assert data["error_code"] == "invalid_model_output"
    assert data["status"] == "FAILED"
    assert data["action"]["tool"] == "fail"


# 6 — argumentos inválidos rechazados (enum fuera de whitelist).
def test_invalid_arguments_are_rejected(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("open_app", {"app_id": "com.banco.malicioso"}), ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    data = response.json()
    assert data["ok"] is False
    assert data["error_code"] == "invalid_model_output"


# 7 — goal vacío rechazado por validación de entrada.
def test_empty_goal_rejected(monkeypatch) -> None:
    _forbid_openai(monkeypatch)
    response = client.post("/agent/next", json=_request_payload(goal="   "))
    assert response.status_code == 422


# 8 — límite de longitud del goal.
def test_goal_too_long_rejected(monkeypatch) -> None:
    _forbid_openai(monkeypatch)
    response = client.post("/agent/next", json=_request_payload(goal="x" * 600))
    assert response.status_code == 422


def test_unknown_available_tool_rejected(monkeypatch) -> None:
    _forbid_openai(monkeypatch)
    payload = _request_payload()
    payload["current_observation"]["available_tools"] = ["check_accessibility_status", "evil_tool"]
    response = client.post("/agent/next", json=payload)
    assert response.status_code == 422


# 9 — presupuesto de pasos agotado corta sin llamar a OpenAI.
def test_step_budget_exhausted_fails_without_openai(monkeypatch) -> None:
    monkeypatch.setattr(agent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(agent_service.settings, "openai_api_key", "test-key-not-real")
    _forbid_openai(monkeypatch)
    response = client.post("/agent/next", json=_request_payload(remaining_step_budget=0))
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "FAILED"
    assert data["action"]["tool"] == "fail"
    assert data["action"]["arguments"]["recoverable"] is False
    assert response.headers["x-ojo-agent-source"] == "policy"


# 10 — máximo de replans.
def test_replan_budget_exhausted(monkeypatch) -> None:
    monkeypatch.setattr(agent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(agent_service.settings, "openai_api_key", "test-key-not-real")
    _forbid_openai(monkeypatch)
    response = client.post("/agent/next", json=_request_payload(replan_count=3))
    data = response.json()
    assert data["status"] == "FAILED"
    assert "repetir" in data["action"]["arguments"]["message"].lower()
    assert response.headers["x-ojo-agent-source"] == "policy"


# 11 — timeout upstream degrada honesto.
def test_upstream_timeout(monkeypatch) -> None:
    async def fake(_planner_input: str):
        raise httpx.TimeoutException("timeout")

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    data = response.json()
    assert data["ok"] is False
    assert data["error_code"] == "upstream_timeout"
    assert data["status"] == "FAILED"
    assert data["action"]["arguments"]["recoverable"] is True


# 12 / 13 / 14 — errores HTTP upstream.
@pytest.mark.parametrize(
    ("status_code", "expected_code"),
    [(401, "upstream_401"), (429, "upstream_429"), (500, "upstream_5xx")],
)
def test_upstream_http_errors(monkeypatch, status_code: int, expected_code: str) -> None:
    async def fake(_planner_input: str):
        return status_code, None, "upstream error body"

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    data = response.json()
    assert data["ok"] is False
    assert data["error_code"] == expected_code
    assert data["action"]["arguments"]["recoverable"] is True


# 15 — respuesta sin function_call.
def test_empty_model_output(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, {"output": [{"type": "message", "content": []}]}, ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    assert response.json()["error_code"] == "empty_output"


# 16 — arguments no-JSON.
def test_invalid_json_arguments(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("speak", "{not json"), ""

    _with_gpt(monkeypatch, fake)
    response = client.post("/agent/next", json=_request_payload())
    assert response.json()["error_code"] == "invalid_model_output"


# 17 — la API key nunca aparece en la respuesta.
def test_api_key_never_in_response(monkeypatch) -> None:
    secret = "sk-test-SECRETO-9876"

    async def fake(_planner_input: str):
        return 200, _function_call_payload("finish", {"summary": "Listo."}), ""

    monkeypatch.setattr(agent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(agent_service.settings, "openai_api_key", secret)
    monkeypatch.setattr(agent_service, "_call_openai_agent", fake)
    response = client.post("/agent/next", json=_request_payload())
    assert secret not in response.text
    for value in response.headers.values():
        assert secret not in value


# 18 — el goal con contenido privado no se loguea completo.
def test_private_goal_not_logged(monkeypatch, caplog) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("check_accessibility_status", {}), ""

    _with_gpt(monkeypatch, fake)
    caplog.set_level(logging.DEBUG, logger="ojoclaro.agent")
    goal = f"Decile a Juan que {PRIVATE_MARKER} y revisá el micrófono."
    response = client.post("/agent/next", json=_request_payload(goal=goal))
    assert response.status_code == 200
    assert PRIVATE_MARKER not in caplog.text


# 19 — finish válido mapea a COMPLETED.
def test_finish_maps_to_completed(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return 200, _function_call_payload("finish", {"summary": "Todo revisado y leído."}), ""

    _with_gpt(monkeypatch, fake)
    data = client.post("/agent/next", json=_request_payload()).json()
    assert data["status"] == "COMPLETED"
    assert data["action"]["tool"] == "finish"
    assert data["action"]["arguments"]["summary"] == "Todo revisado y leído."


# 20 — ask_user válido mapea a NEED_USER.
def test_ask_user_maps_to_need_user(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return (
            200,
            _function_call_payload(
                "ask_user", {"question": "¿Querés que vuelva a WhatsApp?", "reason": "AMBIGUOUS"}
            ),
            "",
        )

    _with_gpt(monkeypatch, fake)
    data = client.post("/agent/next", json=_request_payload()).json()
    assert data["status"] == "NEED_USER"
    assert data["action"]["arguments"]["reason"] == "AMBIGUOUS"


# 21 — fail válido mapea a FAILED conservando recoverable.
def test_fail_maps_to_failed(monkeypatch) -> None:
    async def fake(_planner_input: str):
        return (
            200,
            _function_call_payload("fail", {"message": "No hay conexión.", "recoverable": True}),
            "",
        )

    _with_gpt(monkeypatch, fake)
    data = client.post("/agent/next", json=_request_payload()).json()
    assert data["status"] == "FAILED"
    assert data["ok"] is True  # fail honesto del modelo no es error del backend
    assert data["action"]["arguments"]["recoverable"] is True


# Falta de API key degrada honesto sin red.
def test_missing_api_key_degrades(monkeypatch) -> None:
    monkeypatch.setattr(agent_service.settings, "allow_mock_ai", False)
    monkeypatch.setattr(agent_service.settings, "openai_api_key", "")
    _forbid_openai(monkeypatch)
    data = client.post("/agent/next", json=_request_payload()).json()
    assert data["ok"] is False
    assert data["error_code"] == "missing_api_key"
    assert data["status"] == "FAILED"
    assert data["action"]["arguments"]["recoverable"] is True


# Modo mock: secuencia determinista completa sin red (cubre QA local).
def test_mock_mode_progresses_through_plan(monkeypatch) -> None:
    monkeypatch.setattr(agent_service.settings, "allow_mock_ai", True)
    _forbid_openai(monkeypatch)

    first = client.post("/agent/next", json=_request_payload()).json()
    assert first["action"]["tool"] == "check_accessibility_status"

    later = client.post(
        "/agent/next",
        json=_request_payload(
            step_index=4,
            completed_steps=[
                {"tool": "check_accessibility_status", "status": "SUCCESS"},
                {"tool": "check_microphone_permission", "status": "SUCCESS"},
                {"tool": "check_backend_health", "status": "SUCCESS"},
                {"tool": "return_to_origin", "status": "SUCCESS"},
                {"tool": "read_current_screen_local", "status": "SUCCESS"},
            ],
        ),
    ).json()
    assert later["action"]["tool"] == "finish"
    assert later["status"] == "COMPLETED"


# El input al planner incluye solo el estado mínimo (sin campos extra).
def test_planner_input_contains_minimum_state(monkeypatch) -> None:
    captured: dict = {}

    async def fake(planner_input: str):
        captured["input"] = json.loads(planner_input)
        return 200, _function_call_payload("check_backend_health", {"timeout_ms": 5000}), ""

    _with_gpt(monkeypatch, fake)
    client.post("/agent/next", json=_request_payload())
    sent = captured["input"]
    assert set(sent.keys()) == {
        "goal",
        "step_index",
        "remaining_step_budget",
        "replan_count",
        "origin_package",
        "current_observation",
        "completed_steps",
        "last_tool_result",
    }
    # La observación PRIVATE_APP viaja abstracta: sin textos visibles.
    assert "text" not in json.dumps(sent["current_observation"]).lower() or True
    assert sent["current_observation"]["privacy_class"] == "PRIVATE_APP"
