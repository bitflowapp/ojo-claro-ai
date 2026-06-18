package com.ojoclaro.android.agent.runtime.whatsapp

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V1.8 — confirmación inteligente de contacto antes de preparar/enviar.
 */
class WhatsAppSmartComposeParserTest {

    @Test
    fun composePhrasesExtractRecipientAndMessage() {
        val cases = mapOf(
            "Mandale a Marco que estoy llegando" to ("marco" to "estoy llegando"),
            "Enviá un WhatsApp a Juan diciendo estoy llegando" to ("juan" to "estoy llegando"),
            "Enviá WhatsApp a Juan diciendo estoy llegando" to ("juan" to "estoy llegando"),
            "Enviá un mensaje a Juan diciendo estoy llegando" to ("juan" to "estoy llegando"),
            "Mandale un mensaje a Marco diciendo que estoy llegando" to ("marco" to "estoy llegando"),
            "Mandale mensaje a Marco diciendo estoy llegando" to ("marco" to "estoy llegando"),
            "Escribile a Juan: estoy llegando" to ("juan" to "estoy llegando"),
            "Escribile a Marco: estoy llegando" to ("marco" to "estoy llegando"),
            "Decile a Juan que llego en diez" to ("juan" to "llego en diez"),
            "Avisale a Marco que estoy llegando" to ("marco" to "estoy llegando"),
            "Mandale a Marco con el texto estoy llegando" to ("marco" to "estoy llegando"),
            "Mandale a mi chat que prueba estela" to ("mi chat" to "prueba estela"),
            "Mandale a mí mismo que prueba estela" to ("mi mismo" to "prueba estela"),
            "Mandale al 0000000000 que prueba estela" to ("0000000000" to "prueba estela"),
            "Mandá este mensaje a Juan: ya salí" to ("juan" to "ya sali")
        )
        cases.forEach { (phrase, expected) ->
            val parsed = WhatsAppSmartComposeParser.parseCompose(phrase)
            assertEquals(expected.first, parsed?.recipientQuery, "destinatario de \"$phrase\"")
            assertEquals(expected.second, parsed?.message, "mensaje de \"$phrase\"")
        }
    }

    @Test
    fun composeWithoutMessageAsksLater() {
        val parsed = WhatsAppSmartComposeParser.parseCompose("Mandale a Marco")
        assertEquals("marco", parsed?.recipientQuery)
        assertEquals(null, parsed?.message)

        val withNoun = WhatsAppSmartComposeParser.parseCompose("Mandale un WhatsApp a Marco")
        assertEquals("marco", withNoun?.recipientQuery)
        assertEquals(null, withNoun?.message)
    }

    @Test
    fun composeWithoutRecipientAsksWho() {
        val parsed = WhatsAppSmartComposeParser.parseCompose(
            "Mandá un mensaje diciendo estoy llegando"
        )
        assertEquals(null, parsed?.recipientQuery)
        assertEquals("estoy llegando", parsed?.message)
    }

    @Test
    fun messageFirstFormParsesSoSensitiveContentGetsBlocked() {
        val parsed = WhatsAppSmartComposeParser.parseCompose(
            "Mandale mi clave 12340000 a Marco"
        )
        assertEquals("marco", parsed?.recipientQuery)
        assertEquals("mi clave 12340000", parsed?.message)
        assertTrue(
            WhatsAppVoiceSendPhrases.looksSensitive(parsed?.message.orEmpty()),
            "la clave debe disparar el bloqueo sensible antes de resolver contacto"
        )
    }

    @Test
    fun sensitiveRecipientQueryIsDetectedForBlocking() {
        // Sin separador "que", la clave queda dentro del DESTINATARIO largo:
        // el servicio debe detectarla por keyword y bloquear antes de que el
        // resolver trate "12340000" como número dictado.
        val parsed = WhatsAppSmartComposeParser.parseCompose(
            "Mandale a Marco Luna mi clave 12340000"
        )
        assertEquals(null, parsed?.message)
        assertTrue(
            WhatsAppVoiceSendPhrases.mentionsSensitiveKeyword(parsed?.recipientQuery.orEmpty()),
            "el destinatario con credencial debe disparar el bloqueo"
        )
        // Un número dictado legítimo NO debe bloquearse por keyword.
        assertFalse(WhatsAppVoiceSendPhrases.mentionsSensitiveKeyword("0000000000"))
    }

    @Test
    fun nonComposePhrasesDoNotParse() {
        listOf(
            "Enviá el mensaje",
            "mandalo",
            "Mandá un audio a Juan",
            "Describí lo que tengo enfrente",
            "Llevame a la farmacia",
            "gracias"
        ).forEach { phrase ->
            assertEquals(
                null,
                WhatsAppSmartComposeParser.parseCompose(phrase),
                "NO es compose: \"$phrase\""
            )
        }
    }

    @Test
    fun weakYesConfirmsContactButNeverSends() {
        listOf("sí", "sí, es ese", "ese", "correcto", "sí, a ese").forEach { phrase ->
            assertTrue(
                WhatsAppSmartComposeParser.isContactYes(phrase),
                "Debe confirmar CONTACTO: \"$phrase\""
            )
            assertFalse(
                WhatsAppVoiceSendPhrases.isConfirmSend(phrase),
                "JAMÁS debe confirmar ENVÍO: \"$phrase\""
            )
        }
    }

    @Test
    fun ordinalsAndFullNumberRequestsParse() {
        assertEquals(0, WhatsAppSmartComposeParser.ordinalChoice("el primero"))
        assertEquals(1, WhatsAppSmartComposeParser.ordinalChoice("el segundo"))
        assertEquals(null, WhatsAppSmartComposeParser.ordinalChoice("hola"))
        assertTrue(WhatsAppSmartComposeParser.isReadFullNumber("leeme el número completo"))
        assertEquals("0000", WhatsAppSmartComposeParser.phoneLast4("+5491150000000"))
    }

    @Test
    fun serviceContractContactFlowIsSafe() {
        val service = File(
            "src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt"
        ).readText()
        // El bloqueo sensible ocurre ANTES de resolver el contacto.
        val composeFn = service.substringAfter("private fun handleSmartCompose")
            .substringBefore("private fun handlePendingContactReply")
        assertTrue(
            composeFn.indexOf("looksSensitive") in 0 until composeFn.indexOf("smartComposeResolver.resolve"),
            "secretos se bloquean antes de resolver contacto"
        )
        // Confirmar contacto jamás toca enviar: tapWhatsAppSend sigue en UN lugar.
        val taps = Regex("tapWhatsAppSend\\(").findAll(service).count()
        assertEquals(1, taps, "el toque de enviar debe seguir en un único lugar")
        // El pending de contacto muere con el turno y con los stop commands.
        assertTrue(
            Regex("pendingContactConfirmation = null").findAll(service).count() >= 4,
            "el pending de contacto debe limpiarse en cancelar/stop/cierre"
        )
        // Por defecto solo últimos 4 dígitos.
        assertTrue(service.contains("phoneLast4"))
    }
}
