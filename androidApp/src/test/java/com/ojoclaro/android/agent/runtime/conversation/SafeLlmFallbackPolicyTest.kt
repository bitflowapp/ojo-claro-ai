package com.ojoclaro.android.agent.runtime.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Safe LLM Fallback Router — política pura + clasificadores. */
class SafeLlmFallbackPolicyTest {

    private fun signals(
        conversational: Boolean = false,
        whatsAppActive: Boolean = false,
        namesWhatsApp: Boolean = false,
        looksDangerous: Boolean = false,
        looksLikeMessageContent: Boolean = false,
        looksLikeReplyHelp: Boolean = false,
        wantsChatContent: Boolean = false,
        looksLikeSafeQuestion: Boolean = false
    ) = SafeLlmSignals(
        conversational, whatsAppActive, namesWhatsApp, looksDangerous,
        looksLikeMessageContent, looksLikeReplyHelp, wantsChatContent, looksLikeSafeQuestion
    )

    @Test
    fun safeConversationAndQuestionsGoToConversation() {
        assertEquals(SafeLlmRoute.ALLOW_CONVERSATION, SafeLlmFallbackPolicy.decide(signals(conversational = true)))
        assertEquals(SafeLlmRoute.ALLOW_CONVERSATION, SafeLlmFallbackPolicy.decide(signals(looksLikeSafeQuestion = true)))
        // pregunta-concepto que MENCIONA un término peligroso ("qué es una transferencia")
        // igual va a conversación: es pregunta, no acción.
        assertEquals(
            SafeLlmRoute.ALLOW_CONVERSATION,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true, looksLikeSafeQuestion = true))
        )
    }

    @Test
    fun dangerousImperativeAlwaysGetsExplicitRefusal() {
        // peligroso → SIEMPRE negativa explícita (BLOCK_DANGEROUS), con o sin contexto
        // WhatsApp. NUNCA NO_MATCH genérico, NUNCA conversación.
        assertEquals(
            SafeLlmRoute.BLOCK_DANGEROUS,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true, namesWhatsApp = true))
        )
        assertEquals(
            SafeLlmRoute.BLOCK_DANGEROUS,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true, whatsAppActive = true))
        )
        // sin contexto WhatsApp → IGUAL negativa explícita (era el gap del QA físico:
        // "tocá el botón enviar" con activeContext=false caía a NO_MATCH genérico).
        assertEquals(
            SafeLlmRoute.BLOCK_DANGEROUS,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true))
        )
        // INVARIANTE: peligroso + no-pregunta SIEMPRE BLOCK_DANGEROUS, jamás
        // ALLOW_CONVERSATION ni NO_MATCH_SAFE_HELP (en TODA combinación de contexto).
        for (wa in listOf(true, false)) for (names in listOf(true, false)) for (conv in listOf(true, false)) {
            val r = SafeLlmFallbackPolicy.decide(
                signals(conversational = conv, whatsAppActive = wa, namesWhatsApp = names, looksDangerous = true)
            )
            assertEquals(
                SafeLlmRoute.BLOCK_DANGEROUS, r,
                "peligroso → refusal explícito: wa=$wa names=$names conv=$conv"
            )
        }
    }

    @Test
    fun dangerousConceptQuestionsStillAllowedToConversation() {
        // "qué es una transferencia" tiene substring peligroso pero es PREGUNTA →
        // no debe bloquearse como acción (no relajamos: sigue sin ejecutar nada).
        assertEquals(
            SafeLlmRoute.ALLOW_CONVERSATION,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true, looksLikeSafeQuestion = true))
        )
    }

    @Test
    fun whatsAppContextRoutesReplyClarifyAndPrivate() {
        assertEquals(
            SafeLlmRoute.SUGGEST_REPLY_ONLY,
            SafeLlmFallbackPolicy.decide(signals(whatsAppActive = true, looksLikeReplyHelp = true))
        )
        // reply-help gana aunque "responder" dispare dangerous (no es un envío)
        assertEquals(
            SafeLlmRoute.SUGGEST_REPLY_ONLY,
            SafeLlmFallbackPolicy.decide(signals(whatsAppActive = true, looksLikeReplyHelp = true, looksDangerous = true))
        )
        assertEquals(
            SafeLlmRoute.BLOCK_PRIVATE_CONTEXT,
            SafeLlmFallbackPolicy.decide(signals(whatsAppActive = true, wantsChatContent = true))
        )
        // El harness debug queda foreground y puede ocultar WhatsApp. Estas dos
        // rutas siguen siendo seguras y locales aun con activeContext=false.
        assertEquals(
            SafeLlmRoute.SUGGEST_REPLY_ONLY,
            SafeLlmFallbackPolicy.decide(signals(looksLikeReplyHelp = true, looksDangerous = true))
        )
        assertEquals(
            SafeLlmRoute.BLOCK_PRIVATE_CONTEXT,
            SafeLlmFallbackPolicy.decide(signals(wantsChatContent = true))
        )
        assertEquals(
            SafeLlmRoute.ASK_CLARIFY,
            SafeLlmFallbackPolicy.decide(signals(whatsAppActive = true, looksLikeMessageContent = true))
        )
    }

    @Test
    fun nothingSafeFallsToLocalHelp() {
        assertEquals(SafeLlmRoute.NO_MATCH_SAFE_HELP, SafeLlmFallbackPolicy.decide(signals()))
    }

    // ---- clasificadores (frases reales) ----

    @Test
    fun safeQuestionRecognizesConceptQuestions() {
        listOf(
            "qué significa factura A",
            "por qué los mensajes de texto funcionan sin internet",
            "explicame qué es una transferencia",
            "cómo funciona el CBU",
            "para qué sirve el modo avión"
        ).forEach { assertTrue(SafeLlmPhrases.isSafeQuestion(it), "debería ser pregunta segura: \"$it\"") }

        listOf(
            "borrá este chat", "mandale a Sofi que estoy llegando", "abrí el chat de Sofi",
            "estoy llegando", "hacé videollamada"
        ).forEach { assertFalse(SafeLlmPhrases.isSafeQuestion(it), "no es pregunta-concepto: \"$it\"") }
    }

    @Test
    fun conceptualQuestionsAboutDangerousActionsAreSafeButImperativesAreNot() {
        // QA fuzz MEDIUM #1: preguntas conceptuales que MENCIONAN una acción
        // peligrosa (no la ejecutan) → pregunta segura → conversación conceptual.
        listOf(
            "qué es una transferencia",
            "cómo funciona una transferencia",
            "qué significa transferencia",
            "cómo se bloquea a alguien en WhatsApp",
            "cómo se manda un mensaje por WhatsApp"
        ).forEach { assertTrue(SafeLlmPhrases.isSafeQuestion(it), "pregunta conceptual: \"$it\"") }
        // Los IMPERATIVOS peligrosos NO son preguntas seguras (rule #18): siguen bloqueados.
        listOf(
            "pagale", "transferile plata", "hacé una transferencia", "bloquealo",
            "bloqueá a CONTACTO_A", "mandá el mensaje", "reenviá esto", "borrá el chat",
            "tocá enviar"
        ).forEach { assertFalse(SafeLlmPhrases.isSafeQuestion(it), "imperativo peligroso NO es pregunta: \"$it\"") }
    }

    @Test
    fun replyHelpRecognizesSuggestionRequestsNotSends() {
        listOf(
            "qué puedo responderle", "qué le respondo", "qué le digo", "ayudame a responder",
            "cómo le contesto"
        ).forEach { assertTrue(SafeLlmPhrases.isReplyHelp(it), "debería ser reply-help: \"$it\"") }
        // imperativos de envío NO son reply-help
        listOf("mandale que le respondo", "respondele a Sofi que sí", "escribile a Juan").forEach {
            assertFalse(SafeLlmPhrases.isReplyHelp(it), "envío NO es reply-help: \"$it\"")
        }
    }

    @Test
    fun wantsChatContentRecognizesPrivateContextRequests() {
        listOf("resumime el chat", "resumí la conversación", "leelo y respondele según el chat").forEach {
            assertTrue(SafeLlmPhrases.wantsChatContent(it), "pide contenido del chat: \"$it\"")
        }
        assertFalse(SafeLlmPhrases.wantsChatContent("qué significa factura A"))
    }
}
