package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class LocalIntentParserVariantsTest {

    private val parser = LocalIntentParser()

    @Test
    fun exactSamsungHelpAndGreetingPhrasesAreHelpOnly() {
        listOf(
            "Hola Estela",
            "Hola, qu\u00E9 pod\u00E9s hacer",
            "Qu\u00E9 pod\u00E9s hacer",
            "Qu\u00E9 puedes hacer",
            "Ayuda"
        ).forEach { phrase ->
            val parsed = parser.parse(phrase)

            assertEquals(AgentIntent.HELP, parsed.intent, "phrase=$phrase")
            assertFalse(parsed.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE, "phrase=$phrase")
            assertFalse(parsed.intent == AgentIntent.OPEN_WHATSAPP, "phrase=$phrase")
            assertFalse(parsed.requiresConfirmation, "phrase=$phrase")
        }
    }

    @Test
    fun exactSamsungOpenWhatsAppPhrasesAreOpenAppOnly() {
        listOf(
            "Abrir WhatsApp",
            "Abr\u00ED WhatsApp",
            "Abrime WhatsApp",
            "Entrar a WhatsApp"
        ).forEach { phrase ->
            val parsed = parser.parse(phrase)

            assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent, "phrase=$phrase")
            assertEquals(emptyList(), parsed.missingSlots, "phrase=$phrase")
            assertFalse(parsed.intent == AgentIntent.COMPOSE_WHATSAPP_MESSAGE, "phrase=$phrase")
            assertFalse(parsed.requiresConfirmation, "phrase=$phrase")
        }
    }

    @Test
    fun exactSamsungComposePhrasesAreComposeAndRequireConfirmationWhenComplete() {
        listOf(
            "Mandale un mensaje a Sofi diciendo que ya llegu\u00E9",
            "Decile a Sofi que ya llegu\u00E9",
            "Escribile a Sofi que ya llegu\u00E9",
            "Mandale a Sofi que ya llegu\u00E9"
        ).forEach { phrase ->
            val parsed = parser.parse(phrase)

            assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent, "phrase=$phrase")
            assertEquals(emptyList(), parsed.missingSlots, "phrase=$phrase")
            assertEquals("Sofi", parsed.slotValue(AgentSlotName.CONTACT_NAME), "phrase=$phrase")
            assertFalse(parsed.slotValue(AgentSlotName.MESSAGE_TEXT).isNullOrBlank(), "phrase=$phrase")
            assertFalse(parsed.intent == AgentIntent.OPEN_WHATSAPP, "phrase=$phrase")
            assertEquals(true, parsed.requiresConfirmation, "phrase=$phrase")
        }
    }

    // ---------- HELP ----------

    @Test
    fun queryPodesHacerEsHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("qué podés hacer").intent)
    }

    @Test
    fun querySabesHacerEsHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("qué sabes hacer").intent)
    }

    @Test
    fun queryPuedesHacerEsHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("qué puedes hacer").intent)
    }

    @Test
    fun ayudameEsHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("ayudame").intent)
    }

    @Test
    fun comoMePodesAyudarEsHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("cómo me podés ayudar").intent)
    }

    @Test
    fun holaEstelaEsHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("hola Estela").intent)
    }

    // ---------- STOP ----------

    @Test
    fun callatePorFavorEsStop() {
        assertEquals(AgentIntent.STOP_SPEAKING, parser.parse("callate por favor").intent)
    }

    @Test
    fun dejaDeHablarEsStop() {
        assertEquals(AgentIntent.STOP_SPEAKING, parser.parse("dejá de hablar").intent)
    }

    @Test
    fun paraEsStop() {
        assertEquals(AgentIntent.STOP_SPEAKING, parser.parse("pará").intent)
    }

    // ---------- OCR ----------

    @Test
    fun leeEstoEsOcr() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("lee esto").intent)
    }

    @Test
    fun leemeEstoEsOcr() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("leeme esto").intent)
    }

    @Test
    fun queDiceAhiEsOcr() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("qué dice ahí").intent)
    }

    @Test
    fun escanearTextoEsOcr() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("escanear texto").intent)
    }

    @Test
    fun leerCartelEsOcr() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("leer cartel").intent)
    }

    @Test
    fun leeElCartelEsOcr() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("lee el cartel").intent)
    }

    /**
     * Importante: NO confundir OCR con lectura visible de pantalla.
     * "leeme este mensaje" sigue mapeando a READ_VISIBLE_SCREEN porque
     * "este mensaje" implica leer la pantalla, no apuntar la cámara.
     */
    @Test
    fun leemeEsteMensajeSiguresSiendoReadVisibleScreen() {
        assertEquals(AgentIntent.READ_VISIBLE_SCREEN, parser.parse("leeme este mensaje").intent)
    }

    // ---------- CALL ----------

    @Test
    fun quieroLlamarAContactoDemoEsCallContact() {
        val parsed = parser.parse("quiero llamar a ContactoDemo")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun llamarAl911EsCallContactConContactoDemo911() {
        val parsed = parser.parse("llamar al 911")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals("911", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun llamaEmergenciasEsCallContact() {
        val parsed = parser.parse("llama emergencias")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals("emergencias", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun marcaEsteNumeroEsOpenPhone() {
        assertEquals(AgentIntent.OPEN_PHONE, parser.parse("marcá este número").intent)
    }

    @Test
    fun quieroLlamarSinContactoDemoEsCallConMissingSlot() {
        val parsed = parser.parse("quiero llamar")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals(listOf(AgentSlotName.CONTACT_NAME), parsed.missingSlots)
        assertFalse(parsed.requiresConfirmation)
    }

    // ---------- CONFIRM ESTRICTO ----------

    @Test
    fun confirmarLlamadaEsConfirm() {
        assertEquals(AgentIntent.CONFIRM, parser.parse("confirmar llamada").intent)
    }

    @Test
    fun confirmarMensajeEsConfirm() {
        assertEquals(AgentIntent.CONFIRM, parser.parse("confirmar mensaje").intent)
    }

    @Test
    fun okNoConfirma() {
        assertFalse(parser.parse("ok").intent == AgentIntent.CONFIRM)
    }

    @Test
    fun siNoConfirma() {
        assertFalse(parser.parse("sí").intent == AgentIntent.CONFIRM)
        assertFalse(parser.parse("si").intent == AgentIntent.CONFIRM)
    }

    @Test
    fun daleNoConfirma() {
        assertFalse(parser.parse("dale").intent == AgentIntent.CONFIRM)
    }
}
