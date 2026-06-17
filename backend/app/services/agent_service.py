"""Servicio del endpoint /agent/next (Estela Agent Core v1).

GPT-5.4 mini SOLO propone la siguiente herramienta segura vía function calling.
Este servicio:
  - corta por presupuesto (steps/replans) sin gastar tokens;
  - llama a la Responses API con tool_choice=required, store=false y UNA función;
  - valida nombre y argumentos contra el registro local (whitelist);
  - degrada honesto ante 401/429/5xx/timeout/salida inválida;
  - nunca loguea goal completo, contenido privado ni API keys.

Sin fallback automático a un modelo grande: si gpt-5.4-mini no responde,
el agente lo dice y las rutas locales siguen vivas en Android.
"""

from __future__ import annotations

import json
import logging

import httpx

from app.core.config import settings
from app.models.agent import (
    AgentActionModel,
    AgentNextRequest,
    AgentNextResponse,
    AgentPostconditionModel,
)
from app.services.agent_prompt import load_agent_system_prompt
from app.services.agent_tools import (
    AGENT_TOOLS,
    AgentToolValidationError,
    STATUS_FAILED,
    openai_function_tools,
    validate_tool_call,
)

logger = logging.getLogger("ojoclaro.agent")

OPENAI_RESPONSES_URL = "https://api.openai.com/v1/responses"

SOURCE_GPT = "gpt"
SOURCE_MOCK = "mock"
SOURCE_FALLBACK = "fallback"
SOURCE_POLICY = "policy"

# Secuencia determinista del modo mock (tests / ALLOW_MOCK_AI). Refleja la
# misión de preparación; nunca llama a la red.
_MOCK_PLAN_SEQUENCE: tuple[tuple[str, dict], ...] = (
    ("check_accessibility_status", {}),
    ("check_microphone_permission", {}),
    ("check_backend_health", {"timeout_ms": 5000}),
    ("return_to_origin", {}),
    ("read_current_screen_local", {"mode": "SUMMARY"}),
    ("finish", {"summary": "Revisé accesibilidad, micrófono y conexión, y leí la pantalla."}),
)


class AgentPlanOutcome:
    def __init__(self, response: AgentNextResponse, source: str) -> None:
        self.response = response
        self.source = source
        self.model_used = response.model


def _build_response(
    request: AgentNextRequest,
    tool_name: str,
    arguments: dict,
    model: str,
    error_code: str | None = None,
    reasoning: str | None = None,
) -> AgentNextResponse:
    spec = AGENT_TOOLS[tool_name]
    summary = (reasoning or f"Paso {request.step_index}: {tool_name}.")[:160]
    return AgentNextResponse(
        ok=error_code is None,
        session_id=request.session_id,
        status=spec.status,
        action=AgentActionModel(tool=tool_name, arguments=arguments),
        spoken_progress=spec.spoken_progress or None,
        expected_postcondition=AgentPostconditionModel(type=spec.postcondition),
        reasoning_summary=summary,
        model=model,
        error_code=error_code,
    )


def _fail_response(
    request: AgentNextRequest,
    message: str,
    recoverable: bool,
    model: str,
    error_code: str | None,
) -> AgentNextResponse:
    return AgentNextResponse(
        ok=error_code is None,
        session_id=request.session_id,
        status=STATUS_FAILED,
        action=AgentActionModel(
            tool="fail",
            arguments={"message": message, "recoverable": recoverable},
        ),
        spoken_progress=None,
        expected_postcondition=AgentPostconditionModel(type="MISSION_FAILED"),
        reasoning_summary=message[:160],
        model=model,
        error_code=error_code,
    )


def _mock_plan(request: AgentNextRequest) -> AgentNextResponse:
    done = {step.tool for step in request.completed_steps if step.status == "SUCCESS"}
    for tool_name, arguments in _MOCK_PLAN_SEQUENCE:
        if tool_name not in done:
            return _build_response(
                request,
                tool_name,
                arguments,
                model="mock",
                reasoning=f"Plan determinista de prueba: {tool_name}.",
            )
    return _build_response(
        request,
        "finish",
        {"summary": "Misión de prueba completada."},
        model="mock",
    )


def _planner_input(request: AgentNextRequest) -> str:
    """Estado mínimo para el modelo. Sin contenido privado: la observación ya
    llega abstracta para PRIVATE_APP y este shape no agrega nada más."""
    payload = {
        "goal": request.goal,
        "step_index": request.step_index,
        "remaining_step_budget": request.remaining_step_budget,
        "replan_count": request.replan_count,
        "origin_package": request.origin_package,
        "current_observation": request.current_observation.model_dump(),
        "completed_steps": [step.model_dump() for step in request.completed_steps],
        "last_tool_result": (
            request.last_tool_result.model_dump() if request.last_tool_result else None
        ),
    }
    return json.dumps(payload, ensure_ascii=False)


def _extract_function_call(payload: dict) -> tuple[str, str] | None:
    """Devuelve (name, arguments_json) del primer function_call del output."""
    for item in payload.get("output", []) or []:
        if isinstance(item, dict) and item.get("type") == "function_call":
            name = item.get("name")
            arguments = item.get("arguments", "{}")
            if isinstance(name, str) and isinstance(arguments, str):
                return name, arguments
    return None


async def _call_openai_agent(planner_input: str) -> tuple[int, dict | None, str]:
    """Devuelve (status_code, json_payload|None, error_text)."""
    payload = {
        "model": settings.openai_agent_model,
        "instructions": load_agent_system_prompt(),
        "input": [
            {
                "role": "user",
                "content": [{"type": "input_text", "text": planner_input}],
            }
        ],
        "tools": openai_function_tools(),
        "tool_choice": "required",
        "parallel_tool_calls": False,
        "store": False,
        "max_output_tokens": settings.openai_agent_max_output_tokens,
    }
    headers = {
        "Authorization": f"Bearer {settings.openai_api_key}",
        "Content-Type": "application/json",
    }
    timeout = httpx.Timeout(settings.openai_agent_timeout_seconds)
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(OPENAI_RESPONSES_URL, headers=headers, json=payload)
    if response.status_code >= 400:
        return response.status_code, None, response.text[:300]
    try:
        return response.status_code, response.json(), ""
    except json.JSONDecodeError:
        return response.status_code, None, "non_json_body"


async def plan_next_action(request: AgentNextRequest) -> AgentPlanOutcome:
    # Presupuestos: cortar acá es más barato y más seguro que pedírselo al modelo.
    if request.remaining_step_budget <= 0 or request.step_index >= settings.openai_agent_max_steps:
        logger.info("agent budget exhausted session=%s step=%d", request.session_id[:8], request.step_index)
        return AgentPlanOutcome(
            _fail_response(
                request,
                "Se agotó el presupuesto de pasos de la misión.",
                recoverable=False,
                model=settings.openai_agent_model,
                error_code=None,
            ),
            SOURCE_POLICY,
        )
    if request.replan_count > settings.openai_agent_max_replans:
        logger.info("agent replan budget exhausted session=%s", request.session_id[:8])
        return AgentPlanOutcome(
            _fail_response(
                request,
                "No pude completar la tarea sin repetir acciones. Te devuelvo el control.",
                recoverable=False,
                model=settings.openai_agent_model,
                error_code=None,
            ),
            SOURCE_POLICY,
        )

    if settings.allow_mock_ai:
        logger.info("agent mock plan session=%s step=%d", request.session_id[:8], request.step_index)
        return AgentPlanOutcome(_mock_plan(request), SOURCE_MOCK)

    if not settings.has_openai_api_key:
        logger.warning("agent fallback: OPENAI_API_KEY missing")
        return AgentPlanOutcome(
            _fail_response(
                request,
                "El modo agente necesita conexión con el planificador, pero las funciones locales siguen disponibles.",
                recoverable=True,
                model="none",
                error_code="missing_api_key",
            ),
            SOURCE_FALLBACK,
        )

    planner_input = _planner_input(request)
    try:
        status_code, payload, error_text = await _call_openai_agent(planner_input)
    except httpx.TimeoutException:
        logger.warning("agent openai timeout session=%s", request.session_id[:8])
        return AgentPlanOutcome(
            _fail_response(
                request,
                "El planificador tardó demasiado. Podés intentar de nuevo.",
                recoverable=True,
                model=settings.openai_agent_model,
                error_code="upstream_timeout",
            ),
            SOURCE_FALLBACK,
        )
    except httpx.HTTPError as error:
        logger.warning("agent openai network error err=%s", error.__class__.__name__)
        return AgentPlanOutcome(
            _fail_response(
                request,
                "No pude conectarme al planificador. Las funciones locales siguen disponibles.",
                recoverable=True,
                model=settings.openai_agent_model,
                error_code="upstream_network",
            ),
            SOURCE_FALLBACK,
        )

    if status_code >= 400 or payload is None:
        error_code = {
            401: "upstream_401",
            403: "upstream_401",
            429: "upstream_429",
        }.get(status_code, "upstream_5xx" if status_code >= 500 else "upstream_error")
        if payload is None and status_code < 400:
            error_code = "invalid_model_output"
        logger.warning(
            "agent openai http error status=%d code=%s detail=%s",
            status_code,
            error_code,
            error_text[:120],
        )
        return AgentPlanOutcome(
            _fail_response(
                request,
                "El planificador no está disponible ahora. Las funciones locales siguen disponibles.",
                recoverable=True,
                model=settings.openai_agent_model,
                error_code=error_code,
            ),
            SOURCE_FALLBACK,
        )

    call = _extract_function_call(payload)
    if call is None:
        logger.warning("agent model returned no function_call")
        return AgentPlanOutcome(
            _fail_response(
                request,
                "El planificador devolvió una respuesta vacía. Podés intentar de nuevo.",
                recoverable=True,
                model=settings.openai_agent_model,
                error_code="empty_output",
            ),
            SOURCE_FALLBACK,
        )

    name, arguments_json = call
    try:
        raw_arguments = json.loads(arguments_json) if arguments_json.strip() else {}
    except json.JSONDecodeError:
        logger.warning("agent model arguments non-json tool=%s", str(name)[:40])
        return AgentPlanOutcome(
            _fail_response(
                request,
                "El planificador devolvió argumentos inválidos. Podés intentar de nuevo.",
                recoverable=True,
                model=settings.openai_agent_model,
                error_code="invalid_model_output",
            ),
            SOURCE_FALLBACK,
        )

    try:
        _, clean_arguments = validate_tool_call(name, raw_arguments)
    except AgentToolValidationError as error:
        logger.warning("agent tool rejected tool=%s reason=%s", str(name)[:40], error)
        return AgentPlanOutcome(
            _fail_response(
                request,
                "El planificador propuso una acción no permitida y fue rechazada.",
                recoverable=True,
                model=settings.openai_agent_model,
                error_code="invalid_model_output",
            ),
            SOURCE_FALLBACK,
        )

    logger.info(
        "agent gpt ok session=%s step=%d tool=%s goal_len=%d",
        request.session_id[:8],
        request.step_index,
        name,
        len(request.goal),
    )
    return AgentPlanOutcome(
        _build_response(request, name, clean_arguments, model=settings.openai_agent_model),
        SOURCE_GPT,
    )
