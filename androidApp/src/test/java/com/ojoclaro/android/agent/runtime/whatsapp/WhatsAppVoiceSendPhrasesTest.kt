package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V1.2 — frases del envío seguro y audios de WhatsApp.
 *
 * Reglas:
 *  - Solo "enviá"/"mandalo"/"confirmo" confirman; "sí"/"dale" jamás.
 *  - "no"/"cancelar" siempre cancelan.
 *  - Credenciales y datos bancarios nunca se envían por voz.
 */
class WhatsAppVoiceSendPhrasesTest {

    @Test
    fun sendDraftCommandsParse() {
        listOf(
            "Enviá el mensaje",
            "envialo",
            "Mandalo",
            "mandá el mensaje",
            "enviar el mensaje",
            "mandale el mensaje",
            "envialo ahora"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppVoiceSendPhrases.isSendDraftCommand(phrase),
                "Debería iniciar envío: \"$phrase\""
            )
        }
    }

    @Test
    fun composeAndUnrelatedPhrasesAreNotSendDraftCommands() {
        listOf(
            // Componer es otro flujo (con contacto y contenido).
            "mandale a juan que estoy llegando",
            "escribile a maria: ya salgo",
            // Vecinas.
            "leé el mensaje",
            "abrí whatsapp",
            "llevame a la farmacia",
            "describí lo que tengo enfrente",
            "mandá un audio"
        ).forEach { phrase ->
            assertFalse(
                WhatsAppVoiceSendPhrases.isSendDraftCommand(phrase),
                "NO debería iniciar envío de borrador: \"$phrase\""
            )
        }
    }

    @Test
    fun onlyExplicitSendPhrasesConfirm() {
        listOf(
            "enviá", "envialo", "mandalo", "sí, enviá", "si envialo",
            "confirmo", "confirmar", "enviar", "aceptar", "confirmar envío"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppVoiceSendPhrases.isConfirmSend(phrase),
                "Debería confirmar envío: \"$phrase\""
            )
        }
    }

    @Test
    fun weakAffirmativesNeverConfirmSend() {
        listOf("sí", "si", "dale", "ok", "okey", "bueno", "ajá", "claro", "obvio")
            .forEach { phrase ->
                assertFalse(
                    WhatsAppVoiceSendPhrases.isConfirmSend(phrase),
                    "JAMÁS debería confirmar envío: \"$phrase\""
                )
            }
    }

    @Test
    fun anyNoAlwaysCancels() {
        listOf(
            "no", "No", "cancelar", "cancelá", "no lo envíes", "no lo mandes",
            "borralo", "mejor no", "no envíes nada"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppVoiceSendPhrases.isCancelSend(phrase),
                "Debería cancelar: \"$phrase\""
            )
        }
    }

    @Test
    fun sendAudioRequestsDetectedForHonestFallback() {
        listOf(
            "mandale un audio a juan",
            "mandá un audio",
            "quiero mandar un audio",
            "grabá un audio y mandaselo"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppVoiceSendPhrases.isSendAudioRequest(phrase),
                "Debería detectar pedido de audio: \"$phrase\""
            )
        }
        assertFalse(WhatsAppVoiceSendPhrases.isSendAudioRequest("enviá el mensaje"))
        assertFalse(WhatsAppVoiceSendPhrases.isSendAudioRequest("reproducí el audio"))
    }

    @Test
    fun playAudioCommandsParse() {
        listOf(
            "Reproducí el audio",
            "reproduce el audio",
            "Escuchá el audio",
            "Poné el audio",
            "Reproducí el último audio",
            "poneme el audio"
        ).forEach { phrase ->
            assertTrue(
                WhatsAppVoiceSendPhrases.isPlayAudioCommand(phrase),
                "Debería reproducir audio: \"$phrase\""
            )
        }
        listOf("poné música", "leé el mensaje", "mandá un audio").forEach { phrase ->
            assertFalse(
                WhatsAppVoiceSendPhrases.isPlayAudioCommand(phrase),
                "NO debería reproducir audio: \"$phrase\""
            )
        }
    }

    @Test
    fun sensitiveContentIsBlocked() {
        listOf(
            "mi contraseña es hola123",
            "la clave del banco",
            "el token es ABC123",
            "mi pin de la tarjeta",
            "CBU 2850590940090418135201",
            "4509 9535 6623 3704",
            "el codigo de seguridad es 123"
        ).forEach { message ->
            assertTrue(
                WhatsAppVoiceSendPhrases.looksSensitive(message),
                "Debería bloquearse por sensible: \"$message\""
            )
        }
    }

    @Test
    fun normalMessagesAreNotSensitive() {
        listOf(
            "estoy llegando",
            "llego en diez minutos",
            "ya salí, nos vemos a las 8",
            "compra pan y leche",
            "te llamo a las 21:30"
        ).forEach { message ->
            assertFalse(
                WhatsAppVoiceSendPhrases.looksSensitive(message),
                "NO debería bloquearse: \"$message\""
            )
        }
    }
}
