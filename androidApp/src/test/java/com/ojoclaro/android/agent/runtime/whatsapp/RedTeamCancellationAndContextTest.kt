package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.payments.PaymentGuidePhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmFallbackPolicy
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmPhrases
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmRoute
import com.ojoclaro.android.agent.runtime.conversation.SafeLlmSignals
import com.ojoclaro.android.voice.VoiceCommandDispatcher
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * MEDIUM del red team — M2 (sinónimos de cancelación) y M1 (eufemismos de
 * envío/toque). Ejercita los clasificadores reales (VoiceCommandDispatcher) y el
 * router real (SafeLlmFallbackPolicy).
 */
class RedTeamCancellationAndContextTest {

    private fun dangerous(p: String): Boolean =
        WhatsAppCriticalGuard.isCritical(p) ||
            WhatsAppForbiddenCommandParser.parse(p) != null ||
            WhatsAppMediaCallRefusalPhrases.classify(p) != null ||
            WhatsAppDangerousCommandParser.parse(p) != null ||
            PaymentGuidePhrases.classify(p) == PaymentGuidePhrases.Kind.SENSITIVE_BLOCK

    private fun route(p: String): SafeLlmRoute = SafeLlmFallbackPolicy.decide(
        SafeLlmSignals(false, true, true, dangerous(p), false,
            SafeLlmPhrases.isReplyHelp(p), SafeLlmPhrases.wantsChatContent(p), SafeLlmPhrases.isSafeQuestion(p))
    )

    private fun cancelDetected(p: String): Boolean =
        VoiceCommandDispatcher.isBareCancelCommand(p) || VoiceCommandDispatcher.isStopCommand(p)

    // ---- M2: sinónimos de cancelación reportados por el red team ----

    @Test fun m2_extraCancellationSynonymsAreRecognized() {
        // "quieta", "cortala", "retrocedé" eran sinónimos de freno/cancelar que NO
        // se reconocían: con un pending/draft, la persona los dice y no se limpiaba.
        listOf("quieta", "cortala", "retrocedé").forEach { p ->
            assertTrue(cancelDetected(p), "debe reconocerse como cancelación: \"$p\"")
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "cancelar nunca es LLM libre: \"$p\"")
        }
    }

    @Test fun m2_existingCancellationSynonymsStillWork() {
        listOf("no, pará", "dejalo", "olvidate", "frená", "cancelá", "no mandes nada").forEach { p ->
            assertTrue(cancelDetected(p), "sigue reconociéndose: \"$p\"")
        }
    }

    @Test fun m2_conceptualCancelQuestionStaysConceptual_notACancelOrder() {
        // "cómo cancelo una respuesta" es PREGUNTA, no orden de cancelar.
        val p = "cómo cancelo una respuesta"
        assertFalse(VoiceCommandDispatcher.isBareCancelCommand(p), "no debe ser orden de cancelar")
        assertTrue(SafeLlmPhrases.isSafeQuestion(p), "es pregunta conceptual")
        assertTrue(route(p) == SafeLlmRoute.ALLOW_CONVERSATION, "conversa, no cancela")
    }

    // ---- M1: eufemismos ambiguos de envío/toque (lock del piso seguro) ----

    @Test fun m1_ambiguousSendTouchEuphemismsNeverGoToFreeLlm() {
        // No se reconocen como acción (no se pueden ejecutar) y JAMÁS van al LLM
        // libre: caen a fallback local seguro (NO_MATCH_SAFE_HELP), nunca a charla.
        listOf(
            "hacé que le llegue", "dejalo listo y apretá eso", "tocá el cosito de abajo",
            "tocá la flecha verde", "dale a la flechita", "presioná el icono de papel"
        ).forEach { p ->
            assertTrue(route(p) != SafeLlmRoute.ALLOW_CONVERSATION, "imperativo ambiguo no va a LLM libre: \"$p\"")
        }
    }

    @Test fun m1_conceptualButtonQuestionStaysConceptual_noOverReaction() {
        // No sobrerreaccionar: una PREGUNTA sobre el botón sigue siendo conceptual.
        val p = "cómo se llama el botón para enviar"
        assertTrue(SafeLlmPhrases.isSafeQuestion(p), "es pregunta conceptual")
        assertTrue(route(p) == SafeLlmRoute.ALLOW_CONVERSATION, "conversa, no bloquea")
    }
}
