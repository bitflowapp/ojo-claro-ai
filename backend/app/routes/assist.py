from fastapi import APIRouter

from app.models.assist import AssistRequest, AssistResponse
from app.services.assistant_service import AssistantService

router = APIRouter(tags=["assist"])
service = AssistantService()


@router.post("/assist", response_model=AssistResponse)
async def assist(request: AssistRequest) -> AssistResponse:
    return await service.assist(request)
