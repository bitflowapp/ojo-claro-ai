package com.ojoclaro.android.agent.runtime.conversation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
    fun dangerousImperativeNeverGoesToConversation() {
        // con marca/contexto WhatsApp → bloqueo explícito
        assertEquals(
            SafeLlmRoute.BLOCK_DANGEROUS,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true, namesWhatsApp = true))
        )
        assertEquals(
            SafeLlmRoute.BLOCK_DANGEROUS,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true, whatsAppActive = true))
        )
        // sin contexto → fallback local seguro, jamás conversación
        assertEquals(
            SafeLlmRoute.NO_MATCH_SAFE_HELP,
            SafeLlmFallbackPolicy.decide(signals(looksDangerous = true))
        )
        // INVARIANTE: peligroso + no-pregunta NUNCA es ALLOW_CONVERSATION
        for (wa in listOf(true, false)) for (names in listOf(true, false)) for (conv in listOf(true, false)) {
            val r = SafeLlmFallbackPolicy.decide(
                signals(conversational = conv, whatsAppActive = wa, namesWhatsApp = names, looksDangerous = true)
            )
            assertNotEquals(SafeLlmRoute.ALLOW_CONVERSATION, r, "peligroso jamás va a conversación: wa=$wa names=$names conv=$conv")
        }
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
