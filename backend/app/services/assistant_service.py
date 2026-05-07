from app.core.config import settings
from app.models.assist import (
    AppCommandType,
    AssistRequest,
    AssistResponse,
    ConfidenceLevel,
    ResponseCategory,
    SuggestedAction,
)
from app.services.prompt_service import PromptService


class AssistantService:
    def __init__(self) -> None:
        self.prompt_service = PromptService()

    async def assist(self, request: AssistRequest) -> AssistResponse:
        if settings.allow_mock_ai or not settings.openai_api_key:
            return self._mock_response(request)

        return await self._cloud_response(request)

    async def _cloud_response(self, request: AssistRequest) -> AssistResponse:
        # Implementación preparada para conectar proveedor multimodal real.
        # Se mantiene separada del contrato público para poder alternar OpenAI, Gemini u otro proveedor.
        return self._mock_response(request)

    def _mock_response(self, request: AssistRequest) -> AssistResponse:
        command_type = request.command.type

        if command_type == AppCommandType.READ_TEXT:
            return AssistResponse(
                spokenText=(
                    "Modo lectura activado. Todavía estoy usando respuesta local de prueba. "
                    "Cuando conectes IA y cámara, voy a leer el texto visible."
                ),
                shortText="Modo lectura listo.",
                confidence=ConfidenceLevel.MEDIUM,
                category=ResponseCategory.TEXT_READING,
                suggestedActions=[
                    SuggestedAction(
                        id="describe_scene",
                        label="Describir escena",
                        commandType=AppCommandType.DESCRIBE_SCENE,
                    )
                ],
            )

        if command_type == AppCommandType.DESCRIBE_SCENE:
            return AssistResponse(
                spokenText=(
                    "Modo descripción activado. Apuntá la cámara de forma estable. "
                    "La versión conectada va a describir objetos, obstáculos y texto visible."
                ),
                shortText="Modo descripción listo.",
                confidence=ConfidenceLevel.MEDIUM,
                category=ResponseCategory.SCENE_DESCRIPTION,
            )

        if command_type == AppCommandType.EMERGENCY_HELP:
            return AssistResponse(
                spokenText=(
                    "Si estás en peligro, llamá a tu contacto de emergencia o pedí ayuda a una persona cercana. "
                    "Puedo preparar el envío de tu ubicación cuando esté configurado."
                ),
                shortText="Emergencia.",
                confidence=ConfidenceLevel.HIGH,
                category=ResponseCategory.EMERGENCY,
                safetyNotice="La app no reemplaza servicios de emergencia ni acompañamiento humano.",
            )

        return AssistResponse(
            spokenText=(
                "No reconocí bien la orden. Podés decir: leer texto, describir, producto, documento, ubicación o ayuda."
            ),
            shortText="Comando no reconocido.",
            confidence=ConfidenceLevel.LOW,
            category=ResponseCategory.SYSTEM,
        )
