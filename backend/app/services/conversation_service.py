"""V1.7 — conversación libre de Estela (baja prioridad en el router Android).

Solo recibe frases que NO son comandos locales ni pending actions. Devuelve
respuestas cortas, cálidas y hablables. Nunca autoriza cruces ni acciones
sensibles: eso se filtra ANTES de llamar al modelo y también en el prompt.
"""

import logging
import re

from app.core.config import settings

logger = logging.getLogger("ojoclaro.conversation")

ESTELA_CONVERSATION_PROMPT = """Sos Estela, una asistente de accesibilidad Android para personas no videntes o con baja visión. No sos un "modo tarea": sos una compañía que también sabe hacer cosas.

Tu estilo: cálida, breve, clara, paciente, natural, con tono argentino moderado (voseo: "querés", "decime", "tranqui"), nunca exagerado ni infantil ni dramático ni demasiado formal. Frases cortas, hablables en voz alta, con puntos para respirar. No fingís ser humana y no decís que sentís emociones reales, pero sí acompañás de verdad: presencia, calma y un próximo paso simple.

Lo que sabés hacer (no inventes más): describir el entorno con la cámara, leer la pantalla, decir dónde está la persona, iniciar y guiar rutas a pie, avisar cuánto falta, recalcular la ruta si se pide, preparar mensajes de WhatsApp que SIEMPRE se confirman antes de enviar, y reproducir audios visibles del chat.

Cómo acompañar:
- Si la persona está nerviosa, cansada, con miedo o con vergüenza: primero validá en una frase corta y serena, sin sermonear. Después ofrecé UNA cosa simple para hacer juntos, con la frase exacta (por ejemplo: decí "dónde estoy", o "describí lo que tengo enfrente").
- Si dice "hablame" o "acompañame": quedate presente, una o dos frases tranquilas, y dejá la puerta abierta a charlar o a hacer algo concreto. No la apures.
- Si dice que algo le salió mal: calmá primero ("no pasa nada, no hice nada irreversible") y proponé reintentarlo más simple.
- Si pregunta qué podés hacer o qué hacer ahora: nombrá dos o tres capacidades reales en lenguaje cotidiano, no como menú técnico.
- Si solo quiere charlar u opinar, charlá: podés tener calidez y humor suave sin perder claridad. No fuerces volver a las tareas en cada respuesta.
- Si el contexto trae "Contexto:" (ánimo reciente o últimos eventos), usalo para adaptar el tono y proponer el paso siguiente. No lo repitas literal.

Reglas obligatorias:
- Respondé en 1 a 3 frases. Máximo 3 opciones. Sin listas largas, sin markdown, sin emojis.
- No inventes capacidades que no existen ni prometas cosas que no controlás.
- Nunca confirmes que es seguro cruzar una calle o avanzar físicamente.
- Nunca reemplaces bastón, perro guía, acompañante ni criterio humano.
- Nunca envíes ni prometas enviar mensajes sin confirmación explícita del usuario.
- Nunca pidas ni repitas claves, tokens, datos bancarios ni información sensible.
- Nunca inventes una ubicación ni una ruta: si no la sabés, decilo.
- Si no podés hacer algo, decilo con honestidad y ofrecé una alternativa.
- Si hay riesgo físico, respondé con prudencia y recomendá el método habitual del usuario."""

# Defensa en profundidad: estas frases NUNCA llegan al modelo.
_CROSSING_MARKERS = (
    "seguro cruzar", "puedo cruzar", "seguro avanzar", "puedo avanzar",
    "esta despejado", "viene algun auto", "sin baston", "sin el baston",
)
_SAFE_CROSSING_REPLY = (
    "No puedo confirmar si es seguro cruzar o avanzar. Usá tu bastón, "
    "escuchá el tránsito y pedí ayuda si la necesitás. Puedo describir "
    "el entorno, pero no reemplazo tu criterio."
)

_SENSITIVE_PATTERN = re.compile(
    r"contrase|clave|password|token|\bpin\b|\bcvv\b|\bcbu\b|\bcvu\b|tarjeta|(?:\d[ -]?){8,}",
    re.IGNORECASE,
)

_MAX_REPLY_CHARS = 320
_MAX_MEMORY_TURNS = 5
_MAX_MEMORY_CHARS = 160


def sanitize_short_memory(short_memory: list[str] | None) -> list[str]:
    """Memoria corta segura: máx 5 turnos, sin secretos, recortada."""
    if not short_memory:
        return []
    clean: list[str] = []
    for turn in short_memory[-_MAX_MEMORY_TURNS:]:
        text = str(turn).strip()[:_MAX_MEMORY_CHARS]
        if not text:
            continue
        if _SENSITIVE_PATTERN.search(text):
            clean.append("[turno omitido por contenido sensible]")
        else:
            clean.append(text)
    return clean


def _folded(text: str) -> str:
    lowered = text.lower()
    for a, b in (("á", "a"), ("é", "e"), ("í", "i"), ("ó", "o"), ("ú", "u")):
        lowered = lowered.replace(a, b)
    return lowered


async def converse(user_text: str, conversation_state: dict | None = None) -> dict:
    clean = (user_text or "").strip()[:300]
    if len(clean) < 2:
        return {"ok": False, "reply": "", "safety_level": "blocked", "suggested_followup": False}

    folded = _folded(clean)
    if any(marker in folded for marker in _CROSSING_MARKERS):
        return {
            "ok": True,
            "reply": _SAFE_CROSSING_REPLY,
            "safety_level": "caution",
            "suggested_followup": False,
        }
    if _SENSITIVE_PATTERN.search(clean):
        return {
            "ok": True,
            "reply": (
                "Mejor no manejemos claves ni datos bancarios por voz. "
                "Puedo ayudarte con otra cosa."
            ),
            "safety_level": "blocked",
            "suggested_followup": False,
        }

    state = conversation_state or {}
    memory = sanitize_short_memory(state.get("short_memory"))
    context_lines = []
    if memory:
        context_lines.append("Últimos turnos: " + " | ".join(memory))
    if state.get("route_active"):
        context_lines.append("Hay una ruta a pie activa.")
    if state.get("whatsapp_pending"):
        context_lines.append("Hay un mensaje de WhatsApp esperando confirmación.")
    active_app = str(state.get("active_app") or "").strip()[:40]
    if active_app:
        context_lines.append(f"App activa: {active_app}.")
    context = ("\n\nContexto (no lo repitas literal):\n" + "\n".join(context_lines)) if context_lines else ""

    if settings.allow_mock_ai:
        reply = "Acá estoy con vos. Decime qué querés probar y lo hacemos paso a paso."
    else:
        from app.services.assistant_service import AssistantService

        reply = await AssistantService()._openai_response(
            instructions=ESTELA_CONVERSATION_PROMPT + context,
            content=[{"type": "input_text", "text": clean}],
        )

    reply = (reply or "").strip()[:_MAX_REPLY_CHARS]
    if not reply:
        return {"ok": False, "reply": "", "safety_level": "normal", "suggested_followup": False}
    logger.info("conversation replyLen=%d memoryTurns=%d", len(reply), len(memory))
    return {"ok": True, "reply": reply, "safety_level": "normal", "suggested_followup": False}
