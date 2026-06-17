import logging

import httpx
from fastapi import HTTPException

logger = logging.getLogger("ojoclaro.assist")

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


BASE_INSTRUCTIONS = """
Eres Ojo Claro / Estela, un asistente de accesibilidad para personas no videntes.
Responde en espanol claro, breve y accionable.
Prioriza seguridad practica: obstaculos, personas, puertas, escaleras, vehiculos, calles, senales, texto visible, dinero, productos, precios y riesgos inmediatos.
No inventes. Si una imagen es borrosa, parcial o no permite confirmar algo importante, dilo explicitamente.
No confirmes medicamentos, billetes, semaforos, senales de transito, instrucciones medicas o informacion peligrosa si no se ve con claridad.
Cuando haya riesgo, indica una accion prudente: detenerse, acercar la camara, pedir ayuda o verificar con otra fuente.
"""


class AssistantService:
    def __init__(self) -> None:
        self.prompt_service = PromptService()

    async def assist(self, request: AssistRequest) -> AssistResponse:
        if settings.allow_mock_ai:
            return self._mock_response(request)
        if not settings.openai_api_key:
            raise HTTPException(
                status_code=503,
                detail="OPENAI_API_KEY no esta configurada en backend/.env.",
            )
        return await self._cloud_response(request)

    async def _cloud_response(self, request: AssistRequest) -> AssistResponse:
        if request.command.type == AppCommandType.READ_TEXT:
            answer = await self.read_text(request.imageBase64 or "", "image/jpeg")
            category = ResponseCategory.TEXT_READING
        elif request.command.type == AppCommandType.DESCRIBE_SCENE:
            answer = await self.vision(
                request.imageBase64 or "",
                "image/jpeg",
                request.userMessage or "Describe que tengo enfrente.",
            )
            category = ResponseCategory.SCENE_DESCRIPTION
        else:
            answer = await self.ask(request.userMessage or request.command.originalText)
            category = ResponseCategory.SYSTEM

        return AssistResponse(
            spokenText=answer,
            shortText=answer[:180],
            confidence=ConfidenceLevel.MEDIUM,
            category=category,
        )

    async def ask(self, question: str) -> str:
        clean = question.strip()
        if len(clean) < 2:
            raise HTTPException(status_code=400, detail="Escribe o dicta una pregunta.")
        return await self._openai_response(
            instructions=(
                f"{BASE_INSTRUCTIONS}\n"
                "Responde como asistente conversacional. Si la pregunta requiere ver el entorno, pide usar la camara."
            ),
            content=[{"type": "input_text", "text": clean}],
        )

    async def vision(self, image_base64: str, mime_type: str, prompt: str | None = None) -> str:
        self._validate_image(image_base64, mime_type)
        return await self._openai_response(
            instructions=(
                f"{BASE_INSTRUCTIONS}\n"
                "Describe la escena para movilidad y orientacion. Menciona primero peligros u obstaculos cercanos, luego objetos utiles y texto visible. No des seguridad falsa."
            ),
            content=[
                {"type": "input_text", "text": prompt or "Describe que tengo enfrente para moverme con seguridad."},
                {
                    "type": "input_image",
                    "image_url": f"data:{mime_type};base64,{image_base64}",
                    "detail": "auto",
                },
            ],
        )

    async def read_text(self, image_base64: str, mime_type: str = "image/jpeg") -> str:
        self._validate_image(image_base64, mime_type)
        return await self._openai_response(
            instructions=(
                f"{BASE_INSTRUCTIONS}\n"
                "Lee y resume texto visible. Conserva numeros importantes como precios, fechas, direcciones, telefonos, nombres de producto y advertencias. Si el texto no se ve bien, dilo y pide acercar o enfocar mejor."
            ),
            content=[
                {"type": "input_text", "text": "Lee el texto visible y explicalo de forma simple."},
                {
                    "type": "input_image",
                    "image_url": f"data:{mime_type};base64,{image_base64}",
                    "detail": "high",
                },
            ],
        )

    async def _openai_response(self, instructions: str, content: list[dict]) -> str:
        if not settings.openai_api_key:
            raise HTTPException(
                status_code=503,
                detail="OPENAI_API_KEY no esta configurada en backend/.env.",
            )

        response = await self._openai_request(settings.openai_model, instructions, content)

        # Contrato de config.py: el fallback se usa SOLO si la API rechaza el
        # primario (p. ej. modelo inexistente/no disponible para la cuenta).
        # Un solo reintento, jamás en loop.
        fallback = (settings.openai_model_fallback or "").strip()
        if (
            response.status_code in (400, 404)
            and fallback
            and fallback != settings.openai_model
        ):
            logger.warning(
                "openai primary model rejected status=%d, retrying with fallback",
                response.status_code,
            )
            response = await self._openai_request(fallback, instructions, content)

        if response.status_code >= 400:
            # Diagnóstico sin payload: status + primeros chars del error
            # upstream (nunca la imagen ni la API key).
            logger.warning(
                "openai upstream error status=%d detail=%s",
                response.status_code,
                response.text[:160].replace("\n", " "),
            )
            raise HTTPException(
                status_code=502,
                detail=f"OpenAI respondio con error HTTP {response.status_code}.",
            )

        text = self._extract_output_text(response.json())
        if not text:
            raise HTTPException(status_code=502, detail="La IA no devolvio texto util.")
        return text

    async def _openai_request(
        self, model: str, instructions: str, content: list[dict]
    ) -> "httpx.Response":
        payload = {
            "model": model,
            "instructions": instructions,
            "input": [{"role": "user", "content": content}],
            "max_output_tokens": 450,
        }
        headers = {
            "Authorization": f"Bearer {settings.openai_api_key}",
            "Content-Type": "application/json",
        }
        try:
            async with httpx.AsyncClient(timeout=60.0) as client:
                return await client.post(
                    "https://api.openai.com/v1/responses",
                    headers=headers,
                    json=payload,
                )
        except httpx.HTTPError as error:
            raise HTTPException(
                status_code=502,
                detail="No pude conectar con OpenAI desde el backend.",
            ) from error

    def _extract_output_text(self, payload: dict) -> str:
        direct = payload.get("output_text")
        if isinstance(direct, str) and direct.strip():
            return direct.strip()
        parts: list[str] = []
        for item in payload.get("output", []) or []:
            for content in item.get("content", []) or []:
                text = content.get("text")
                if isinstance(text, str) and text.strip():
                    parts.append(text.strip())
        return "\n".join(parts).strip()

    def _validate_image(self, image_base64: str, mime_type: str) -> None:
        if not image_base64:
            raise HTTPException(status_code=400, detail="Falta imageBase64.")
        if mime_type not in {"image/jpeg", "image/png", "image/webp"}:
            raise HTTPException(status_code=400, detail="Formato de imagen no soportado.")
        approx_bytes = len(image_base64) * 3 // 4
        if approx_bytes > 8 * 1024 * 1024:
            raise HTTPException(status_code=413, detail="La imagen es demasiado grande.")

    def _mock_response(self, request: AssistRequest) -> AssistResponse:
        command_type = request.command.type

        if command_type == AppCommandType.READ_TEXT:
            return AssistResponse(
                spokenText=(
                    "Modo lectura activado. Estoy usando respuesta local de prueba porque ALLOW_MOCK_AI=true."
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
                    "Modo descripcion activado. Estoy usando respuesta local de prueba porque ALLOW_MOCK_AI=true."
                ),
                shortText="Modo descripcion listo.",
                confidence=ConfidenceLevel.MEDIUM,
                category=ResponseCategory.SCENE_DESCRIPTION,
            )

        if command_type == AppCommandType.EMERGENCY_HELP:
            return AssistResponse(
                spokenText=(
                    "Si estas en peligro, llama a tu contacto de emergencia o pedi ayuda a una persona cercana."
                ),
                shortText="Emergencia.",
                confidence=ConfidenceLevel.HIGH,
                category=ResponseCategory.EMERGENCY,
                safetyNotice="La app no reemplaza servicios de emergencia ni acompanamiento humano.",
            )

        return AssistResponse(
            spokenText=(
                "No reconoci bien la orden. Podes decir: leer texto, describir, producto, documento, ubicacion o ayuda."
            ),
            shortText="Comando no reconocido.",
            confidence=ConfidenceLevel.LOW,
            category=ResponseCategory.SYSTEM,
        )
