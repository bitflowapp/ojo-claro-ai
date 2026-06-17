package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * #8 — clasificador del clarifier local. Con WhatsApp al frente:
 *  - contenido de mensaje / continuación ambigua → aclarar local (no LLM),
 *  - Q&A claro → permitido (no se reclama),
 *  - acción WhatsApp explícita → ni siquiera llega acá (la atienden forbidden/
 *    critical guard), pero igual NO se clasifica como contenido ambiguo.
 */
class WhatsAppMessageClarifierPhrasesTest {

    @Test
    fun ambiguousMessageLikeUtterancesAreFlagged() {
        listOf(
            "estoy llegando",          // D1
            "decile que sí",           // D2
            "mandale eso",             // D3
            "respondé eso",
            "sí, eso",
            "ya voy",
            "avisale",
            "contestale",
            "ponele ok",
            "mandá lo anterior",
            "eso",
            "dile que mañana",
            "pasale esto"
        ).forEach {
            assertTrue(
                WhatsAppMessageClarifierPhrases.looksLikeAmbiguousMessageContent(it),
                "debería pedir aclaración local: \"$it\""
            )
        }
    }

    @Test
    fun clearQuestionsAreAllowedAsQa() {
        listOf(
            "qué significa factura A",          // D4
            "explicame qué es una transferencia",
            "cómo se calcula el IVA",
            "qué hora es",
            "ayudame a entender este texto",
            "qué puedo responderle",            // D5
            "dame ideas para contestar"
        ).forEach {
            assertTrue(
                WhatsAppMessageClarifierPhrases.looksLikeQuestion(it),
                "debería ser Q&A: \"$it\""
            )
            assertFalse(
                WhatsAppMessageClarifierPhrases.looksLikeAmbiguousMessageContent(it),
                "Q&A NO debe tratarse como contenido de mensaje: \"$it\""
            )
        }
    }

    @Test
    fun explicitActionsAndSpecificMessagesAreNotAmbiguousContent() {
        listOf(
            "borrá este chat",                       // D6 (lo atiende forbidden)
            "mandá una foto por WhatsApp",           // D7 (lo atiende forbidden)
            "mandale a Juan que estoy llegando",     // compose específico → no clarifier
            "respondé que llego en cinco minutos",   // reply específico → no clarifier
            "gracias",
            "estoy cansado"
        ).forEach {
            assertFalse(
                WhatsAppMessageClarifierPhrases.looksLikeAmbiguousMessageContent(it),
                "NO debería pedir aclaración: \"$it\""
            )
        }
    }

    @Test
    fun forbiddenAndCriticalAreParsedElsewhere() {
        // las acciones explícitas las atienden los parsers previos, no este.
        assertTrue(WhatsAppForbiddenCommandParser.parse("borrá este chat") != null)
        assertTrue(WhatsAppForbiddenCommandParser.parse("mandá una foto por WhatsApp") != null)
        assertFalse(WhatsAppMessageClarifierPhrases.looksLikeQuestion("mandale eso"))
        assertFalse(WhatsAppMessageClarifierPhrases.looksLikeQuestion("estoy llegando"))
    }
}
