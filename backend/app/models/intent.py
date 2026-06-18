from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field

# Tope defensivo de longitud de texto enviado a la IA (Android ya trunca ~1200).
MAX_USER_TEXT_CHARS = 2000


class IntentInput(BaseModel):
    """Shape canónico que envía Android (EstelaIntentJsonContract.input)."""

    model_config = ConfigDict(extra="ignore")

    user_text: str = ""
    conversation_state: str | dict[str, Any] = "idle"
    pending_action: dict | None = None
    installed_apps: list[str] = Field(default_factory=list)
    memory_contacts: list[str] = Field(default_factory=list)
    active_app: str | None = None
    permissions_granted: dict = Field(default_factory=dict)


class ScreenContext(BaseModel):
    """Contexto opcional de pantalla para pruebas manuales. Por privacidad, solo
    usamos packageName como active_app; NO reenviamos visibleText a la IA."""

    model_config = ConfigDict(extra="ignore")

    packageName: str | None = None
    summary: str | None = None
    visibleText: list[str] = Field(default_factory=list)


class IntentRequest(BaseModel):
    """Acepta dos formas:
      - Android:  {model, system_prompt_id, input:{user_text, ...}}
      - Manual:   {text|user_text, locale, screenContext}
    """

    model_config = ConfigDict(extra="ignore")

    model: str | None = None
    system_prompt_id: str | None = None
    input: IntentInput | None = None

    # Conveniencia para pruebas manuales (curl).
    text: str | None = None
    user_text: str | None = None
    locale: str | None = None
    screenContext: ScreenContext | None = None

    def canonical_input(self) -> dict:
        base = self.input or IntentInput()
        user_text = (base.user_text or self.user_text or self.text or "").strip()
        user_text = user_text[:MAX_USER_TEXT_CHARS]

        active_app = base.active_app
        if active_app is None and self.screenContext is not None:
            active_app = self.screenContext.packageName

        return {
            "user_text": user_text,
            "conversation_state": base.conversation_state or "idle",
            "pending_action": base.pending_action,
            "installed_apps": base.installed_apps,
            "memory_contacts": base.memory_contacts,
            "active_app": active_app,
            "permissions_granted": base.permissions_granted,
        }


class IntentResponse(BaseModel):
    """Contrato de salida que parsea LlmIntentAdapter (Android). snake_case exacto."""

    intent: str
    confidence: float
    params: dict = Field(default_factory=dict)
    safety_level: str
    voice_response: str | None = None
    voice_response_template: str | None = None
    raw_text: str = ""
