package com.ojoclaro.android.quality

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCriticalGuard
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppDangerousCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppForbiddenCommandParser
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMediaCallRefusalPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppReplyPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppVoiceSendPhrases
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * TASK 02 — Fuzzing dirigido a la SIGUIENTE capa de gaps de routing voz/WhatsApp,
 * continuación de [VoiceFuzzMassiveTest] / [VoiceFuzzAdversarialTest]. Tres
 * contratos nuevos, TODOS en dirección segura (jamás habilitan un envío/acción):
 *
 *  1) BOTÓN DISFRAZADO por ÍCONO/COLOR sin la palabra "botón": para un no-vidente
 *     "tocá el avioncito" / "el avión de papel" / "la flechita de enviar" /
 *     "el verde" ES el botón de ENVIAR de WhatsApp. Debe quedar como negativa
 *     LOCAL (crítica) y JAMÁS viajar al LLM. Complementa el fix de "botón verde"
 *     del commit 7850ff5 (que exigía la palabra "botón").
 *
 *  2) CANCELACIÓN robusta: "abortá" / "frená todo" / "detené eso" / "pará todo"
 *     deben reconocerse como cancelación, así el guard de pending (que espeja
 *     estos sets) da el aviso "Cancelado. No envié nada." ANTES del STOP global
 *     mudo (que limpia el pending pero silencia sin avisar al no-vidente).
 *
 *  3) REPLY-HELP: variantes "qué le escribo" / "cómo le digo" / "qué le puedo
 *     poner" / "qué podría responderle" = ayuda de redacción → SUGGEST_REPLY_ONLY
 *     (sugerir SIN enviar). Hoy caen a fallback genérico.
 *
 * PURO/determinista. Peor caso: WhatsApp SIEMPRE activo (whatsAppActive=true).
 */
class VoiceFuzzRoutingGapsTest {

    // Espejo EXACTO de GlobalAssistantService.buildSafeLlmSignals().looksDangerous
    // (+ detectores de acción peligrosa por contacto y pagos sensibles).
    private fun dangerousDetected(p: String): Boolean =
        WhatsAppCriticalGuard.isCritical(p) ||
            WhatsAppForbiddenCommandParser.parse(p) != null ||
            WhatsAppMediaCallRefusalPhrases.classify(p) != null ||
            WhatsAppDangerousCommandParser.parse(p) != null ||
            PaymentGuidePhrases.classify(p) == PaymentGuidePhrases.Kind.SENSITIVE_BLOCK

    private fun cancelDetected(p: String): Boolean =
        VoiceCommandDispatcher.isBareCancelCommand(p) ||
            WhatsAppVoiceSendPhrases.isCancelSend(p) ||
            WhatsAppReplyPhrases.isCancel(p)

    private fun route(p: String): SafeLlmRoute = SafeLlmFallbackPolicy.decide(
        SafeLlmSignals(
            conversational = false,
            whatsAppActive = true,
            namesWhatsApp = true,
            looksDangerous = dangerousDetected(p),
            looksLikeMessageContent = false,
            looksLikeReplyHelp = SafeLlmPhrases.isReplyHelp(p),
            wantsChatContent = SafeLlmPhrases.wantsChatContent(p),
            looksLikeSafeQuestion = SafeLlmPhrases.isSafeQuestion(p)
        )
    )

    // ---------- 1. Botón de enviar disfrazado por ícono/color ----------
    private val DISGUISED_SEND_BUTTONS = listOf(
        "tocá el avioncito", "apretá el avioncito", "presioná el avioncito",
        "tocá el avión de papel", "apretá el avión de papel",
        "tocá la flechita de enviar", "apretá la flechita de enviar",
        "tocá la flechita", "tocá el verde", "apretá el verde", "dale al verde",
        "dale al avioncito",
        // Regresión: la forma con "botón" (cubierta por 7850ff5) sigue crítica.
        "tocá el botón verde", "apretá el botón de enviar"
    )

    // Navegación segura + PREGUNTAS sobre el ícono + "modo avión": NUNCA críticas
    // (el fix está anclado a verbo de toque + artículo, no roba estas).
    private val NON_CRITICAL_BUTTONS = listOf(
        "tocá el botón de atrás", "tocá el de inicio", "volvé atrás",
        "qué es el avioncito", "para qué sirve el avioncito",
        "qué significa el avioncito", "qué es el modo avión",
        "para qué sirve el modo avión", "activá el modo avión",
        "tiene doble marca verde", "qué es la flechita"
    )

    @Test
    fun disguisedSendButtonsAreCriticalAndBlocked() {
        DISGUISED_SEND_BUTTONS.forEach { p ->
            assertTrue(
                WhatsAppCriticalGuard.isCritical(p),
                "disguised send button must be CRITICAL: \"$p\""
            )
            assertTrue(
                route(p) == SafeLlmRoute.BLOCK_DANGEROUS,
                "disguised send button must BLOCK_DANGEROUS (got ${route(p)}): \"$p\""
            )
        }
    }

    @Test
    fun safeButtonsAndIconQuestionsStayNonCritical() {
        NON_CRITICAL_BUTTONS.forEach { p ->
            assertFalse(
                WhatsAppCriticalGuard.isCritical(p),
                "safe button / icon question must NOT be critical: \"$p\""
            )
            // Y nunca se bloquean como peligrosas (se navegan o se conversan).
            assertTrue(
                route(p) != SafeLlmRoute.BLOCK_DANGEROUS,
                "safe button / icon question must NOT block (got ${route(p)}): \"$p\""
            )
        }
    }

    // ---------- 2. Cancelación robusta (pánico / abortar) ----------
    private val ROBUST_CANCELS = listOf(
        "abortá", "abortar", "abortalo", "abortala", "abortá todo", "abortar todo",
        "frená todo", "frená la mano", "frená eso", "frenalo",
        "detené eso", "detené todo", "detené esto", "detenelo",
        "pará todo", "pará la mano",
        "no quiero", "ya no quiero", "no sigas"
    )

    @Test
    fun robustCancellationsRecognizedLocally() {
        ROBUST_CANCELS.forEach { p ->
            assertTrue(cancelDetected(p), "cancellation not recognized (gap): \"$p\"")
        }
    }

    @Test
    fun robustCancellationsNeverFlipToSend() {
        // Dirección segura: una cancelación JAMÁS confirma un envío.
        ROBUST_CANCELS.forEach { p ->
            assertFalse(
                WhatsAppVoiceSendPhrases.isConfirmSend(p),
                "cancel must never confirm send: \"$p\""
            )
            assertFalse(
                WhatsAppReplyPhrases.isStrongSendConfirm(p),
                "cancel must never strong-confirm: \"$p\""
            )
        }
        // Anti-negación: "no canceles" / "no abortes" NO deben cancelar.
        listOf("no canceles", "no canceles nada", "no abortes", "no pares").forEach { p ->
            assertFalse(cancelDetected(p), "negated cancel must NOT cancel: \"$p\"")
        }
    }

    // ---------- 3. Reply-help (ayuda de redacción, NO envía) ----------
    private val REPLY_HELP_EXTRA = listOf(
        "qué le escribo", "cómo le digo", "qué le puedo poner",
        "qué le puedo escribir", "qué le puedo decir", "cómo le pongo",
        "qué le respondería", "qué podría responderle"
    )

    @Test
    fun extendedReplyHelpSuggestsOnly() {
        REPLY_HELP_EXTRA.forEach { p ->
            assertTrue(SafeLlmPhrases.isReplyHelp(p), "must be reply-help: \"$p\"")
            // Reply-help GANA sobre "dangerous": aunque "respondería" dispare el
            // marcador 'respond' del guard, la política lo deriva a sugerir-sin-enviar
            // (mismo rescate que GlobalAssistantService.handleWhatsAppCriticalGuard).
            // El contrato es la RUTA (suggest-only), nunca BLOCK_DANGEROUS ni envío.
            assertTrue(
                route(p) == SafeLlmRoute.SUGGEST_REPLY_ONLY,
                "reply-help must suggest-only (got ${route(p)}): \"$p\""
            )
        }
    }

    @Test
    fun composeWithTextIsNotReplyHelp() {
        // El compose explícito ("respondéle que ya voy") NO es ayuda de redacción.
        listOf(
            "respondéle que ya voy", "contestale que ya voy", "decile que llego",
            "escribí buenas tardes"
        ).forEach { p ->
            assertFalse(SafeLlmPhrases.isReplyHelp(p), "compose-with-text is NOT reply-help: \"$p\"")
        }
    }
}
