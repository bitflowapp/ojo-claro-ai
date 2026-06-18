"""Endpoint POST /agent/next (Estela Agent Core v1).

El alias /api/v1/agent/next lo monta app.main con include_router(prefix=/api/v1),
igual que /assist e /intent.
"""

from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.models.agent import AgentNextRequest
from app.services.agent_service import plan_next_action

router = APIRouter(tags=["agent"])


@router.post("/agent/next")
async def agent_next(request: AgentNextRequest) -> JSONResponse:
    outcome = await plan_next_action(request)
    return JSONResponse(
        content=outcome.response.model_dump(),
        headers={
            "x-ojo-agent-source": outcome.source,
            "x-ojo-agent-model": outcome.model_used,
        },
    )
