package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppForbiddenCommandParserTest {

    private fun act(phrase: String): WhatsAppActionType? =
        WhatsAppForbiddenCommandParser.parse(phrase)?.action

    @Test
    fun detectsEveryForbiddenAction() {
        val cases = mapOf(
            "borrá este chat" to WhatsAppActionType.DELETE_CHAT,
            "eliminá la conversación" to WhatsAppActionType.DELETE_CHAT,
            "borrá este mensaje" to WhatsAppActionType.DELETE_CHAT,
            "archivá este chat" to WhatsAppActionType.ARCHIVE_CHAT,
            "silenciá este grupo" to WhatsAppActionType.MUTE_CHAT,
            "fijá este chat" to WhatsAppActionType.PIN_CHAT,
            "bloqueá a esta persona" to WhatsAppActionType.BLOCK_CONTACT,
            "reportá este contacto" to WhatsAppActionType.REPORT_CONTACT,
            "mandale plata" to WhatsAppActionType.PAYMENT,
            "pagale por WhatsApp" to WhatsAppActionType.PAYMENT,
            "mandá un sticker" to WhatsAppActionType.SEND_STICKER,
            "mandá una foto" to WhatsAppActionType.SEND_PHOTO,
            "sacá una foto y mandala" to WhatsAppActionType.SEND_PHOTO,
            "adjuntá un archivo" to WhatsAppActionType.SEND_FILE,
            "reenviá este mensaje" to WhatsAppActionType.FORWARD_MESSAGE,
            "mandale mi ubicación" to WhatsAppActionType.SHARE_LOCATION,
            "compartí ubicación en vivo" to WhatsAppActionType.SHARE_LOCATION,
            "abrí ese link" to WhatsAppActionType.OPEN_SUSPICIOUS_LINK,
            "tocá el enlace" to WhatsAppActionType.OPEN_SUSPICIOUS_LINK
        )
        cases.forEach { (phrase, expected) ->
            assertEquals(expected, act(phrase), "forbidden de \"$phrase\"")
        }
    }

    @Test
    fun everyForbiddenActionHasAParserMatch() {
        // cobertura: cada acción FORBIDDEN del catálogo se detecta por alguna frase.
        val covered = WhatsAppActionCatalog.actionsOf(WhatsAppRiskLevel.FORBIDDEN).toSet()
        val detected = listOf(
            "borrá este chat", "archivá este chat", "silenciá este chat", "fijá este chat",
            "bloqueá a esta persona", "reportá este contacto", "pagale por WhatsApp",
            "mandá un sticker", "adjuntá un archivo", "mandá una foto por WhatsApp",
            "reenviá este mensaje", "compartí mi ubicación", "abrí ese link"
        ).mapNotNull { act(it) }.toSet()
        assertTrue(detected.containsAll(covered), "sin cobertura: ${covered - detected}")
    }

    @Test
    fun handlesMuletillaVariants() {
        assertEquals(WhatsAppActionType.DELETE_CHAT, act("ehh borrá este chat"))
        assertEquals(WhatsAppActionType.BLOCK_CONTACT, act("che bloqueá a este contacto"))
        assertEquals(WhatsAppActionType.SEND_PHOTO, act("no sé, mandá una foto"))
        assertEquals(WhatsAppActionType.FORWARD_MESSAGE, act("reenviá esto"))
    }

    @Test
    fun namedWhatsAppFlagIsSet() {
        assertTrue(WhatsAppForbiddenCommandParser.parse("mandá una foto por WhatsApp")!!.namedWhatsApp)
        assertTrue(WhatsAppForbiddenCommandParser.parse("sacá una foto para WhatsApp")!!.namedWhatsApp)
        assertEquals(false, WhatsAppForbiddenCommandParser.parse("borrá este chat")!!.namedWhatsApp)
    }

    @Test
    fun explicitMessagingObjectAnchorsDestructiveIntent() {
        // rule #7: una acción destructiva + objeto de mensajería explícito es
        // inequívocamente WhatsApp y debe bloquearse local aunque WhatsApp no sea
        // el contexto activo (no caer al LLM/backend).
        listOf(
            "borrá este chat", "archivá este chat", "bloqueá este contacto",
            "reportá este chat", "reenviá este mensaje", "silenciá este grupo"
        ).forEach {
            assertTrue(
                WhatsAppForbiddenCommandParser.mentionsExplicitMessagingObject(it),
                "ancla de mensajería esperada en: \"$it\""
            )
        }
        // "este/esto" a secas o un objeto NO-mensajería no debe anclar: podría ser
        // otro app (archivo, pantalla, documento) → que decida el contexto/LLM.
        listOf(
            "archivá el documento", "bloqueá la pantalla", "borrá ese archivo",
            "reenviá esto", "borrá esto", "pagale"
        ).forEach {
            assertEquals(
                false,
                WhatsAppForbiddenCommandParser.mentionsExplicitMessagingObject(it),
                "no debería anclar como mensajería: \"$it\""
            )
        }
    }

    @Test
    fun doesNotClaimLegitimateOrSafePhrases() {
        listOf(
            "borrá el borrador",                 // WA-5 clear draft
            "limpiá la memoria de WhatsApp",     // context forget
            "olvidá el contexto",                // context forget
            "leé la foto",                       // read, no send verb
            "qué dice el último mensaje",        // read
            "respondé estoy llegando",           // WA-5 reply
            "abrí WhatsApp con Juan",            // blind-first open
            "pará no mandes",                    // WA-5 cancel
            "apagá la pantalla",                 // not a payment ("apaga" != "paga")
            "describime qué estoy viendo"        // camera read
        ).forEach { assertNull(act(it), "no debería reclamar: \"$it\"") }
    }
}
