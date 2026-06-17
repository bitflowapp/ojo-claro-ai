"""V1.7 — POST /conversation: charla libre de Estela (baja prioridad)."""

from fastapi import APIRouter
from pydantic import BaseModel, ConfigDict, Field

from app.services.conversation_service import converse

router = APIRouter(tags=["conversation"])


class ConversationState(BaseModel):
    model_config = ConfigDict(extra="ignore")

    active_app: str | None = None
    last_assistant_response: str | None = None
    route_active: bool = False
    whatsapp_pending: bool = False
    last_capability_used: str | None = None
    short_memory: list[str] = Field(default_factory=list, max_length=8)


class ConversationRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    user_text: str = Field(min_length=1, max_length=300)
    conversation_state: ConversationState | None = None


class ConversationResponse(BaseModel):
    ok: bool
    reply: str
    safety_level: str
    suggested_followup: bool


@router.post("/conversation", response_model=ConversationResponse)
async def conversation(request: ConversationRequest) -> ConversationResponse:
    result = await converse(
        user_text=request.user_text,
        conversation_state=(
            request.conversation_state.model_dump() if request.conversation_state else None
        ),
    )
    return ConversationResponse.model_validate(result)
