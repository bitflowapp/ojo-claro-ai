"""Contrato tipado de /agent/next (Estela Agent Core v1).

Android conserva el AgentSessionState; el backend es stateless y solo ve el
mínimo necesario. Nunca llegan acá mensajes, nombres de contactos ni texto de
pantalla: para PRIVATE_APP la observación es abstracta por diseño.
"""

from __future__ import annotations

import re
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator

from app.services.agent_tools import AGENT_TOOL_NAMES, VALID_STATUSES

MAX_GOAL_CHARS = 500
MAX_SESSION_ID_CHARS = 64
MAX_STEPS_HISTORY = 16
MAX_PUBLIC_ACTIONS = 12
MAX_OBSERVED_KEYS = 12
MAX_OBSERVED_VALUE_CHARS = 120

_SESSION_ID_RE = re.compile(r"^[A-Za-z0-9_-]{1,64}$")
_PACKAGE_RE = re.compile(r"^[A-Za-z0-9_.]{1,120}$")

PRIVACY_CLASSES = ("PUBLIC_UI", "PRIVATE_APP", "SENSITIVE_APP", "UNKNOWN")
TOOL_RESULT_STATUSES = ("SUCCESS", "FAILED", "BLOCKED")


class AgentPublicAction(BaseModel):
    """Acción pública sanitizada del catálogo local (solo PUBLIC_UI)."""

    model_config = ConfigDict(extra="forbid")

    action_id: str = Field(min_length=1, max_length=64)
    label: str = Field(min_length=1, max_length=60)


class AgentObservationModel(BaseModel):
    model_config = ConfigDict(extra="forbid")

    package_name: str | None = Field(default=None, max_length=120)
    screen_class: str = Field(default="UNKNOWN", max_length=48)
    privacy_class: str = Field(default="UNKNOWN")
    available_tools: list[str] = Field(default_factory=list, max_length=24)
    public_actions: list[AgentPublicAction] = Field(default_factory=list, max_length=MAX_PUBLIC_ACTIONS)
    visible_item_count: int = Field(default=0, ge=0, le=500)
    can_open_by_ordinal: bool = False
    can_read_locally: bool = True

    @field_validator("privacy_class")
    @classmethod
    def _privacy_in_enum(cls, value: str) -> str:
        if value not in PRIVACY_CLASSES:
            raise ValueError("privacy_class invalida")
        return value

    @field_validator("available_tools")
    @classmethod
    def _tools_known(cls, tools: list[str]) -> list[str]:
        unknown = [t for t in tools if t not in AGENT_TOOL_NAMES]
        if unknown:
            raise ValueError(f"available_tools desconocidas: {unknown[:3]}")
        return tools

    @field_validator("package_name")
    @classmethod
    def _package_shape(cls, value: str | None) -> str | None:
        if value is None or value == "":
            return None
        if not _PACKAGE_RE.match(value):
            raise ValueError("package_name invalido")
        return value


class AgentCompletedStep(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool: str = Field(max_length=48)
    status: str

    @field_validator("tool")
    @classmethod
    def _tool_known(cls, value: str) -> str:
        if value not in AGENT_TOOL_NAMES:
            raise ValueError("tool desconocida en completed_steps")
        return value

    @field_validator("status")
    @classmethod
    def _status_known(cls, value: str) -> str:
        if value not in TOOL_RESULT_STATUSES:
            raise ValueError("status invalido en completed_steps")
        return value


class AgentLastToolResult(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool: str = Field(max_length=48)
    status: str
    postcondition_verified: bool = False
    failure_reason: str | None = Field(default=None, max_length=160)
    observed: dict[str, Any] = Field(default_factory=dict)
    user_reply: str | None = Field(default=None, max_length=240)

    @field_validator("tool")
    @classmethod
    def _tool_known(cls, value: str) -> str:
        if value not in AGENT_TOOL_NAMES:
            raise ValueError("tool desconocida en last_tool_result")
        return value

    @field_validator("status")
    @classmethod
    def _status_known(cls, value: str) -> str:
        if value not in TOOL_RESULT_STATUSES:
            raise ValueError("status invalido en last_tool_result")
        return value

    @field_validator("observed")
    @classmethod
    def _observed_bounded(cls, observed: dict[str, Any]) -> dict[str, Any]:
        if len(observed) > MAX_OBSERVED_KEYS:
            raise ValueError("observed demasiado grande")
        clean: dict[str, Any] = {}
        for key, value in observed.items():
            if not isinstance(key, str) or len(key) > 48:
                raise ValueError("clave observed invalida")
            if isinstance(value, bool) or isinstance(value, int):
                clean[key] = value
            elif isinstance(value, str):
                clean[key] = value[:MAX_OBSERVED_VALUE_CHARS]
            else:
                raise ValueError("valor observed debe ser bool/int/str")
        return clean


class AgentNextRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    session_id: str
    goal: str
    step_index: int = Field(ge=0, le=32)
    origin_package: str | None = Field(default=None, max_length=120)
    current_observation: AgentObservationModel
    completed_steps: list[AgentCompletedStep] = Field(default_factory=list, max_length=MAX_STEPS_HISTORY)
    last_tool_result: AgentLastToolResult | None = None
    remaining_step_budget: int = Field(ge=0, le=16)
    replan_count: int = Field(default=0, ge=0, le=8)

    @field_validator("session_id")
    @classmethod
    def _session_shape(cls, value: str) -> str:
        if not _SESSION_ID_RE.match(value):
            raise ValueError("session_id invalido")
        return value

    @field_validator("goal")
    @classmethod
    def _goal_bounds(cls, value: str) -> str:
        cleaned = " ".join(value.split())
        if not cleaned:
            raise ValueError("goal vacio")
        if len(cleaned) > MAX_GOAL_CHARS:
            raise ValueError("goal demasiado largo")
        return cleaned

    @field_validator("origin_package")
    @classmethod
    def _origin_shape(cls, value: str | None) -> str | None:
        if value is None or value == "":
            return None
        if not _PACKAGE_RE.match(value):
            raise ValueError("origin_package invalido")
        return value


class AgentActionModel(BaseModel):
    model_config = ConfigDict(extra="forbid")

    tool: str
    arguments: dict[str, Any] = Field(default_factory=dict)


class AgentPostconditionModel(BaseModel):
    model_config = ConfigDict(extra="forbid")

    type: str


class AgentNextResponse(BaseModel):
    """Contrato estable hacia Android. snake_case exacto."""

    model_config = ConfigDict(extra="forbid")

    ok: bool
    session_id: str
    status: str
    action: AgentActionModel | None = None
    spoken_progress: str | None = None
    expected_postcondition: AgentPostconditionModel | None = None
    reasoning_summary: str | None = Field(default=None, max_length=160)
    model: str
    error_code: str | None = None

    @field_validator("status")
    @classmethod
    def _status_known(cls, value: str) -> str:
        if value not in VALID_STATUSES:
            raise ValueError("status invalido")
        return value
