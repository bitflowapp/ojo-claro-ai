from fastapi import APIRouter

from app.models.assist import AskRequest, AskResponse, ImageRequest, AssistRequest, AssistResponse
from app.services.assistant_service import AssistantService

router = APIRouter(tags=["assist"])
service = AssistantService()


@router.post("/assist", response_model=AssistResponse)
async def assist(request: AssistRequest) -> AssistResponse:
    return await service.assist(request)


@router.post("/api/ask", response_model=AskResponse)
async def ask(request: AskRequest) -> AskResponse:
    answer = await service.ask(request.question)
    return AskResponse(answer=answer)


@router.post("/api/vision", response_model=AskResponse)
async def vision(request: ImageRequest) -> AskResponse:
    answer = await service.vision(request.imageBase64, request.mimeType, request.prompt)
    return AskResponse(answer=answer)


@router.post("/api/read-text", response_model=AskResponse)
async def read_text(request: ImageRequest) -> AskResponse:
    answer = await service.read_text(request.imageBase64, request.mimeType)
    return AskResponse(answer=answer)
