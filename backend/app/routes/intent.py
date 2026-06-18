from fastapi import APIRouter
from fastapi.responses import JSONResponse

from app.models.intent import IntentRequest, IntentResponse
from app.services.intent_service import classify_intent

router = APIRouter(tags=["intent"])


@router.post("/intent", response_model=IntentResponse)
async def intent(request: IntentRequest) -> JSONResponse:
    outcome = await classify_intent(request.canonical_input())
    response = IntentResponse.model_validate(outcome.payload)
    return JSONResponse(
        content=response.model_dump(),
        headers={
            "x-ojo-intent-source": outcome.source,
            "x-ojo-intent-model": outcome.model_used,
        },
    )
