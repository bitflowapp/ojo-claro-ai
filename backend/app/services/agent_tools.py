"""Registro central de herramientas del Estela Agent Core v1.

Única fuente de verdad para:
  - whitelist de herramientas que el planner puede elegir;
  - JSON Schema estricto de cada función (Responses API function calling);
  - poscondición declarada que Android debe verificar;
  - plantilla de progreso hablado (es-AR) — el texto libre del modelo queda
    limitado a speak/ask_user/finish/fail y siempre se sanitiza por longitud.

GPT propone. El policy gate de Android autoriza o rechaza. Android ejecuta.
El observador local verifica. Este módulo no ejecuta nada.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import Any, Callable

# Límites de texto libre generado por el modelo (defensa en profundidad,
# Android vuelve a sanitizar antes de hablar).
MAX_SPEAK_CHARS = 280
MAX_QUESTION_CHARS = 240
MAX_SUMMARY_CHARS = 280
MAX_FAIL_CHARS = 240

ASK_USER_REASONS = ("AMBIGUOUS", "CONFIRMATION_REQUIRED", "MANUAL_ACTION_REQUIRED")
READ_MODES = ("SUMMARY", "VISIBLE_ACTIONS", "VISIBLE_MESSAGES")
LIST_CATEGORIES = ("GENERAL", "SETTINGS")
SCROLL_DIRECTIONS = ("FORWARD", "BACKWARD")
OPEN_APP_IDS = ("WHATSAPP", "WHATSAPP_BUSINESS", "SETTINGS", "OJO_CLARO")

# status del contrato /agent/next
STATUS_CONTINUE = "CONTINUE"
STATUS_NEED_USER = "NEED_USER"
STATUS_COMPLETED = "COMPLETED"
STATUS_FAILED = "FAILED"
STATUS_CANCELLED = "CANCELLED"
VALID_STATUSES = (
    STATUS_CONTINUE,
    STATUS_NEED_USER,
    STATUS_COMPLETED,
    STATUS_FAILED,
    STATUS_CANCELLED,
)


class AgentToolValidationError(ValueError):
    """Argumentos del modelo rechazados por el validador local."""


def _no_args_schema() -> dict:
    return {
        "type": "object",
        "properties": {},
        "required": [],
        "additionalProperties": False,
    }


def _require_str(args: dict, key: str, max_len: int) -> str:
    value = args.get(key)
    if not isinstance(value, str) or not value.strip():
        raise AgentToolValidationError(f"argumento '{key}' invalido")
    cleaned = " ".join(value.split())
    if len(cleaned) > max_len:
        cleaned = cleaned[:max_len].rstrip()
    return cleaned


def _require_enum(args: dict, key: str, allowed: tuple[str, ...]) -> str:
    value = args.get(key)
    if not isinstance(value, str) or value not in allowed:
        raise AgentToolValidationError(f"argumento '{key}' fuera de enum")
    return value


def _validate_empty(args: dict) -> dict:
    if args:
        # Tolerante con {} pero estricto con basura extra.
        unexpected = [k for k in args.keys()]
        if unexpected:
            raise AgentToolValidationError(f"argumentos inesperados: {unexpected[:3]}")
    return {}


def _validate_backend_health(args: dict) -> dict:
    timeout = args.get("timeout_ms", 5000)
    if not isinstance(timeout, int) or isinstance(timeout, bool):
        raise AgentToolValidationError("timeout_ms debe ser entero")
    return {"timeout_ms": max(500, min(10_000, timeout))}


def _validate_remember_origin(args: dict) -> dict:
    package = _require_str(args, "package_name", 120)
    if any(ch.isspace() for ch in package) or ".." in package:
        raise AgentToolValidationError("package_name invalido")
    return {"package_name": package}


def _validate_open_app(args: dict) -> dict:
    return {"app_id": _require_enum(args, "app_id", OPEN_APP_IDS)}


def _validate_read_screen(args: dict) -> dict:
    return {"mode": _require_enum(args, "mode", READ_MODES)}


def _validate_list_actions(args: dict) -> dict:
    return {"category": _require_enum(args, "category", LIST_CATEGORIES)}


def _validate_activate_action(args: dict) -> dict:
    action_id = _require_str(args, "action_id", 64)
    return {"action_id": action_id}


def _validate_scroll(args: dict) -> dict:
    return {"direction": _require_enum(args, "direction", SCROLL_DIRECTIONS)}


def _validate_speak(args: dict) -> dict:
    return {"message": _require_str(args, "message", MAX_SPEAK_CHARS)}


def _validate_ask_user(args: dict) -> dict:
    return {
        "question": _require_str(args, "question", MAX_QUESTION_CHARS),
        "reason": _require_enum(args, "reason", ASK_USER_REASONS),
    }


def _validate_finish(args: dict) -> dict:
    return {"summary": _require_str(args, "summary", MAX_SUMMARY_CHARS)}


def _validate_fail(args: dict) -> dict:
    recoverable = args.get("recoverable")
    if not isinstance(recoverable, bool):
        raise AgentToolValidationError("recoverable debe ser booleano")
    return {
        "message": _require_str(args, "message", MAX_FAIL_CHARS),
        "recoverable": recoverable,
    }


@dataclass(frozen=True)
class AgentToolSpec:
    name: str
    description: str
    parameters: dict
    postcondition: str
    spoken_progress: str
    status: str = STATUS_CONTINUE
    validate: Callable[[dict], dict] = field(default=_validate_empty)


AGENT_TOOLS: dict[str, AgentToolSpec] = {
    spec.name: spec
    for spec in (
        AgentToolSpec(
            name="check_accessibility_status",
            description=(
                "Consulta local del estado real del servicio de accesibilidad de Estela "
                "(enabled, connected, overlay_attached). No cambia nada."
            ),
            parameters=_no_args_schema(),
            postcondition="ACCESSIBILITY_STATUS_OBSERVED",
            spoken_progress="Reviso la accesibilidad.",
        ),
        AgentToolSpec(
            name="check_microphone_permission",
            description="Consulta local del permiso real de micrófono (granted). No cambia nada.",
            parameters=_no_args_schema(),
            postcondition="MICROPHONE_PERMISSION_OBSERVED",
            spoken_progress="Reviso el micrófono.",
        ),
        AgentToolSpec(
            name="check_backend_health",
            description=(
                "Llama localmente al endpoint /health del backend de Estela y reporta "
                "reachable/status/latency_ms. Usar timeout_ms=5000 salvo necesidad."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "timeout_ms": {
                        "type": "integer",
                        "description": "Timeout en milisegundos (500..10000).",
                    }
                },
                "required": ["timeout_ms"],
                "additionalProperties": False,
            },
            postcondition="BACKEND_HEALTH_OBSERVED",
            spoken_progress="Pruebo la conexión.",
            validate=_validate_backend_health,
        ),
        AgentToolSpec(
            name="remember_origin_app",
            description=(
                "Registra como origen el paquete realmente observado al iniciar la misión. "
                "Solo acepta paquetes observados; no inventa paquetes."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "package_name": {
                        "type": "string",
                        "description": "Paquete Android observado (ej com.whatsapp).",
                    }
                },
                "required": ["package_name"],
                "additionalProperties": False,
            },
            postcondition="ORIGIN_REGISTERED",
            spoken_progress="",
            validate=_validate_remember_origin,
        ),
        AgentToolSpec(
            name="open_app",
            description=(
                "Abre una app de la whitelist fija. app_id es un identificador lógico; "
                "Android lo resuelve localmente. No acepta paquetes arbitrarios."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "app_id": {"type": "string", "enum": list(OPEN_APP_IDS)},
                },
                "required": ["app_id"],
                "additionalProperties": False,
            },
            postcondition="APP_IN_FOREGROUND",
            spoken_progress="Abro la aplicación.",
            validate=_validate_open_app,
        ),
        AgentToolSpec(
            name="return_to_origin",
            description=(
                "Vuelve a la aplicación registrada como origen de la misión. "
                "No usa HOME ni RECENTS."
            ),
            parameters=_no_args_schema(),
            postcondition="ORIGIN_IN_FOREGROUND",
            spoken_progress="Vuelvo a la aplicación anterior.",
        ),
        AgentToolSpec(
            name="read_current_screen_local",
            description=(
                "Lee la pantalla actual con la ruta LOCAL determinista y la dice por TTS. "
                "El contenido leído NUNCA viaja al planner."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "mode": {"type": "string", "enum": list(READ_MODES)},
                },
                "required": ["mode"],
                "additionalProperties": False,
            },
            postcondition="SCREEN_READ_SPOKEN",
            spoken_progress="Leo la pantalla.",
            validate=_validate_read_screen,
        ),
        AgentToolSpec(
            name="list_visible_actions_local",
            description=(
                "Devuelve un catálogo local sanitizado de acciones públicas visibles "
                "cuando es seguro. En pantallas privadas devuelve solo conteos."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "category": {"type": "string", "enum": list(LIST_CATEGORIES)},
                },
                "required": ["category"],
                "additionalProperties": False,
            },
            postcondition="ACTIONS_CATALOGUED",
            spoken_progress="",
            validate=_validate_list_actions,
        ),
        AgentToolSpec(
            name="activate_visible_action",
            description=(
                "Activa una acción pública por action_id efímero emitido por el catálogo "
                "local de la observación ACTUAL. Nunca inventar action_id."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "action_id": {"type": "string"},
                },
                "required": ["action_id"],
                "additionalProperties": False,
            },
            postcondition="ACTION_ACTIVATED",
            spoken_progress="",
            validate=_validate_activate_action,
        ),
        AgentToolSpec(
            name="go_back",
            description="Ejecuta el BACK global seguro ya autorizado.",
            parameters=_no_args_schema(),
            postcondition="BACK_PERFORMED",
            spoken_progress="",
        ),
        AgentToolSpec(
            name="scroll_accessibility",
            description="Desplaza un contenedor realmente scrollable. Sin coordenadas, sin gestos.",
            parameters={
                "type": "object",
                "properties": {
                    "direction": {"type": "string", "enum": list(SCROLL_DIRECTIONS)},
                },
                "required": ["direction"],
                "additionalProperties": False,
            },
            postcondition="SCROLL_PERFORMED",
            spoken_progress="",
            validate=_validate_scroll,
        ),
        AgentToolSpec(
            name="speak",
            description="Dice un mensaje breve por TTS. No puede citar contenido privado que no recibió.",
            parameters={
                "type": "object",
                "properties": {
                    "message": {"type": "string"},
                },
                "required": ["message"],
                "additionalProperties": False,
            },
            postcondition="MESSAGE_SPOKEN",
            spoken_progress="",
            validate=_validate_speak,
        ),
        AgentToolSpec(
            name="ask_user",
            description="Hace UNA pregunta hablable al usuario y espera su respuesta.",
            parameters={
                "type": "object",
                "properties": {
                    "question": {"type": "string"},
                    "reason": {"type": "string", "enum": list(ASK_USER_REASONS)},
                },
                "required": ["question", "reason"],
                "additionalProperties": False,
            },
            postcondition="USER_REPLY_RECEIVED",
            spoken_progress="",
            status=STATUS_NEED_USER,
            validate=_validate_ask_user,
        ),
        AgentToolSpec(
            name="finish",
            description=(
                "Termina la misión con un resumen HONESTO basado únicamente en resultados "
                "observados (completed_steps / last_tool_result). No afirmar nada no comprobado."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "summary": {"type": "string"},
                },
                "required": ["summary"],
                "additionalProperties": False,
            },
            postcondition="MISSION_COMPLETED",
            spoken_progress="",
            status=STATUS_COMPLETED,
            validate=_validate_finish,
        ),
        # --- Outdoor Guidance v1 (Fase 3B) ---
        AgentToolSpec(
            name="check_location_permission",
            description="Consulta local del permiso real de ubicación (granted). No cambia nada.",
            parameters=_no_args_schema(),
            postcondition="LOCATION_PERMISSION_OBSERVED",
            spoken_progress="Reviso el permiso de ubicación.",
        ),
        AgentToolSpec(
            name="check_location_services_enabled",
            description="Consulta local de si los servicios de ubicación del sistema están activos.",
            parameters=_no_args_schema(),
            postcondition="LOCATION_SERVICES_OBSERVED",
            spoken_progress="",
        ),
        AgentToolSpec(
            name="get_current_location",
            description=(
                "Obtiene una ubicación real local. Al planner SOLO llegan métricas "
                "abstractas (available, accuracy_bucket, age_bucket): las coordenadas "
                "exactas nunca salen del dispositivo por esta vía."
            ),
            parameters=_no_args_schema(),
            postcondition="LOCATION_OBSERVED",
            spoken_progress="Busco tu ubicación.",
        ),
        AgentToolSpec(
            name="describe_current_location",
            description=(
                "Dice por TTS dónde está el usuario con precisión HONESTA "
                "(ej: 'precisión aproximada de 15 metros'). Nunca inventa direcciones."
            ),
            parameters=_no_args_schema(),
            postcondition="LOCATION_DESCRIBED",
            spoken_progress="",
        ),
        AgentToolSpec(
            name="start_outdoor_guidance",
            description=(
                "Inicia orientación peatonal por voz hacia un destino dicho por el "
                "usuario. La ruta es caminando. NUNCA afirma que cruzar o avanzar sea "
                "seguro: solo informa distancia y próxima maniobra."
            ),
            parameters={
                "type": "object",
                "properties": {
                    "destination": {
                        "type": "string",
                        "description": "Destino pronunciado por el usuario.",
                    }
                },
                "required": ["destination"],
                "additionalProperties": False,
            },
            postcondition="GUIDANCE_STARTED",
            spoken_progress="Preparo la ruta a pie.",
            validate=lambda args: {"destination": _require_str(args, "destination", 200)},
        ),
        AgentToolSpec(
            name="get_route_progress",
            description="Informa distancia restante y próxima maniobra de la guía activa.",
            parameters=_no_args_schema(),
            postcondition="ROUTE_PROGRESS_OBSERVED",
            spoken_progress="",
        ),
        AgentToolSpec(
            name="stop_outdoor_guidance",
            description="Detiene la orientación exterior y limpia la sesión de navegación.",
            parameters=_no_args_schema(),
            postcondition="GUIDANCE_STOPPED",
            spoken_progress="",
        ),
        AgentToolSpec(
            name="describe_scene_on_demand",
            description=(
                "Captura UNA imagen (con aviso sonoro) solo porque el usuario lo pidió "
                "explícitamente, la describe con lenguaje prudente y la descarta. "
                "PROHIBIDO afirmar que el camino esté libre o que sea seguro avanzar."
            ),
            parameters=_no_args_schema(),
            postcondition="SCENE_DESCRIBED",
            spoken_progress="Voy a mirar con la cámara.",
        ),
        AgentToolSpec(
            name="fail",
            description="Termina la misión explicando honestamente qué no se pudo y si es recuperable.",
            parameters={
                "type": "object",
                "properties": {
                    "message": {"type": "string"},
                    "recoverable": {"type": "boolean"},
                },
                "required": ["message", "recoverable"],
                "additionalProperties": False,
            },
            postcondition="MISSION_FAILED",
            spoken_progress="",
            status=STATUS_FAILED,
            validate=_validate_fail,
        ),
    )
}

AGENT_TOOL_NAMES: tuple[str, ...] = tuple(AGENT_TOOLS.keys())


def openai_function_tools() -> list[dict[str, Any]]:
    """Definiciones de funciones para la Responses API (strict mode)."""
    return [
        {
            "type": "function",
            "name": spec.name,
            "description": spec.description,
            "parameters": spec.parameters,
            "strict": True,
        }
        for spec in AGENT_TOOLS.values()
    ]


def validate_tool_call(name: str, raw_arguments: dict) -> tuple[AgentToolSpec, dict]:
    """Valida nombre + argumentos del modelo. Lanza AgentToolValidationError."""
    spec = AGENT_TOOLS.get(name)
    if spec is None:
        raise AgentToolValidationError(f"herramienta desconocida: {str(name)[:40]}")
    if not isinstance(raw_arguments, dict):
        raise AgentToolValidationError("arguments debe ser objeto JSON")
    return spec, spec.validate(raw_arguments)
