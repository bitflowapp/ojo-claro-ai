package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LocalIntentParserTest {
    private val parser = LocalIntentParser()

    @Test
    fun parseaQuePuedoDecirComoHelp() {
        assertEquals(AgentIntent.HELP, parser.parse("qué puedo decir").intent)
    }

    @Test
    fun parseaCallarComoStopSpeaking() {
        assertEquals(AgentIntent.STOP_SPEAKING, parser.parse("callar").intent)
    }

    @Test
    fun parseaConfirmarComoConfirm() {
        assertEquals(AgentIntent.CONFIRM, parser.parse("confirmar").intent)
    }

    @Test
    fun parseaCancelarComoCancel() {
        assertEquals(AgentIntent.CANCEL, parser.parse("cancelar").intent)
    }

    @Test
    fun parseaAbriWhatsAppComoOpenWhatsApp() {
        val parsed = parser.parse("abrí WhatsApp")

        assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent)
        assertEquals(listOf(AgentSlotName.WHATSAPP_ACTION), parsed.missingSlots)
    }

    @Test
    fun parseaAbriWhatsAppPrincipalComoOpenWhatsAppCompleto() {
        val parsed = parser.parse("abrí WhatsApp principal")

        assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent)
        assertTrue(parsed.missingSlots.isEmpty())
    }

    @Test
    fun parseaMensajeCompletoConContactoDemoYTexto() {
        val parsed = parser.parse("mandale un mensaje a ContactoDemo que estoy llegando")

        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", parsed.slotValue(AgentSlotName.MESSAGE_TEXT))
        assertTrue(parsed.missingSlots.isEmpty())
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaMensajeSinTextoConMissingMessageText() {
        val parsed = parser.parse("mandale a ContactoDemo")

        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals(listOf(AgentSlotName.MESSAGE_TEXT), parsed.missingSlots)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test
    fun parseaMensajeSinContactoDemoConMissingContactName() {
        val parsed = parser.parse("mandale un mensaje")

        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
        assertEquals(listOf(AgentSlotName.CONTACT_NAME), parsed.missingSlots)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test
    fun siSiYDaleNoSonConfirm() {
        listOf("sí", "si", "dale").forEach { phrase ->
            assertFalse(parser.parse(phrase).intent == AgentIntent.CONFIRM, phrase)
        }
    }

    @Test
    fun parseaLlamaAContactoDemoComoCallContact() {
        val parsed = parser.parse("llamá a ContactoDemo")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaLlamaAMamaComoCallContact() {
        val parsed = parser.parse("llama a mamá")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals("mamá", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaContactoDemoDeEmergenciaComoCallContact() {
        val parsed = parser.parse("llamar a mi contacto de emergencia")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals("mi contacto de emergencia", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaAbriTelefonoComoOpenPhone() {
        assertEquals(AgentIntent.OPEN_PHONE, parser.parse("abrí teléfono").intent)
        assertEquals(AgentIntent.OPEN_PHONE, parser.parse("abrir teléfono").intent)
    }

    @Test
    fun parseaLlamarSinContactoDemoConMissingContactName() {
        val parsed = parser.parse("llamar")

        assertEquals(AgentIntent.CALL_CONTACT, parsed.intent)
        assertEquals(listOf(AgentSlotName.CONTACT_NAME), parsed.missingSlots)
    }

    @Test
    fun mensajeConCodigoSeMarcaSensible() {
        val parsed = parser.parse("mandale a ContactoDemo que mi código de verificación es 123456")

        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
        val messageSlot = assertNotNull(parsed.slots.firstOrNull { it.name == AgentSlotName.MESSAGE_TEXT })
        assertTrue(messageSlot.isSensitive)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test
    fun parseaLecturasYMemoriaActuales() {
        assertEquals(AgentIntent.READ_OCR_TEXT, parser.parse("leer texto").intent)
        assertEquals(AgentIntent.READ_VISIBLE_SCREEN, parser.parse("qué dice la pantalla").intent)
        assertEquals(AgentIntent.READ_VISIBLE_SCREEN, parser.parse("leeme este mensaje").intent)
        assertEquals(AgentIntent.REMEMBER_MEMORY, parser.parse("recordá que prefiero respuestas cortas").intent)
        assertEquals(AgentIntent.LIST_MEMORY, parser.parse("qué recordás de mí").intent)
        assertEquals(AgentIntent.CLEAR_MEMORY, parser.parse("borrá tu memoria").intent)
    }
    @Test
    fun parseaContactoDemoDeConfianza() {
        val parsed = parser.parse("recordá que ContactoDemo es contacto de confianza")

        assertEquals(AgentIntent.SAVE_CONTACT, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals(LocalIntentParser.CONTACT_TYPE_TRUSTED, parsed.slotValue(AgentSlotName.CONTACT_TYPE))
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaContactoDemoDeEmergencia() {
        val parsed = parser.parse("mamá es contacto de emergencia")

        assertEquals(AgentIntent.SAVE_CONTACT, parsed.intent)
        assertEquals("mamá", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals(LocalIntentParser.CONTACT_TYPE_EMERGENCY, parsed.slotValue(AgentSlotName.CONTACT_TYPE))
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaNumeroDeContactoDemo() {
        val parsed = parser.parse("el número de ContactoDemo es 2991234567")

        assertEquals(AgentIntent.SAVE_CONTACT_PHONE, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("2991234567", parsed.slotValue(AgentSlotName.PHONE_NUMBER))
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaGuardarNumeroConMissingPhoneNumber() {
        val parsed = parser.parse("guardá el número de ContactoDemo")

        assertEquals(AgentIntent.SAVE_CONTACT_PHONE, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals(listOf(AgentSlotName.PHONE_NUMBER), parsed.missingSlots)
    }

    @Test
    fun parseaOlvidarContactoDemo() {
        val parsed = parser.parse("olvidá el contacto ContactoDemo")

        assertEquals(AgentIntent.DELETE_CONTACT, parsed.intent)
        assertEquals("ContactoDemo", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaListaContactoDemosDeConfianza() {
        val parsed = parser.parse("quiénes son mis contactos de confianza")

        assertEquals(AgentIntent.LIST_CONTACTS, parsed.intent)
        assertEquals(LocalIntentParser.CONTACT_TYPE_TRUSTED, parsed.slotValue(AgentSlotName.CONTACT_TYPE))
    }
    @Test
    fun parseaDondeEstoyComoGetCurrentLocation() {
        assertEquals(AgentIntent.GET_CURRENT_LOCATION, parser.parse("dónde estoy").intent)
        assertEquals(AgentIntent.GET_CURRENT_LOCATION, parser.parse("decime mi ubicación").intent)
    }

    @Test
    fun parseaAbrirMapasComoOpenMaps() {
        assertEquals(AgentIntent.OPEN_MAPS, parser.parse("abrí mapas").intent)
        assertEquals(AgentIntent.OPEN_MAPS, parser.parse("abrí Google Maps").intent)
    }

    @Test
    fun parseaLlevameACasaComoNavigateAlias() {
        val parsed = parser.parse("llevame a casa")

        assertEquals(AgentIntent.NAVIGATE_TO_DESTINATION, parsed.intent)
        assertEquals("casa", parsed.slotValue(AgentSlotName.LOCATION_ALIAS))
        assertEquals("casa", parsed.slotValue(AgentSlotName.DESTINATION))
    }

    @Test
    fun parseaComoLlegoAFarmaciaComoNavigate() {
        val parsed = parser.parse("cómo llego a la farmacia")

        assertEquals(AgentIntent.NAVIGATE_TO_DESTINATION, parsed.intent)
        assertEquals("farmacia", parsed.slotValue(AgentSlotName.DESTINATION))
    }

    @Test
    fun parseaAbrirMapasHaciaCalle() {
        val parsed = parser.parse("abrí mapas hacia calle San Martín")

        assertEquals(AgentIntent.NAVIGATE_TO_DESTINATION, parsed.intent)
        assertEquals("calle San Martín", parsed.slotValue(AgentSlotName.DESTINATION))
    }

    @Test
    fun parseaGuardarUbicacionComoCasa() {
        val parsed = parser.parse("guardá esta ubicación como casa")

        assertEquals(AgentIntent.SAVE_LOCATION_ALIAS, parsed.intent)
        assertEquals("casa", parsed.slotValue(AgentSlotName.LOCATION_ALIAS))
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaOlvidarUbicacionCasa() {
        val parsed = parser.parse("olvidá la ubicación casa")

        assertEquals(AgentIntent.DELETE_LOCATION_ALIAS, parsed.intent)
        assertEquals("casa", parsed.slotValue(AgentSlotName.LOCATION_ALIAS))
    }

    // --- OPEN_WHATSAPP_CHAT ---

    @Test
    fun parseaBuscarChatComoOpenWhatsAppChat() {
        listOf(
            "busca el chat de Marco Antonio",
            "buscá el chat de Marco Antonio",
            "buscame el chat de Marco Antonio",
            "buscar chat de Marco Antonio",
            "encontrá el chat de Marco Antonio",
            "abrí el chat de Marco Antonio",
            "andá al chat de Marco Antonio",
            "abrí WhatsApp con Marco Antonio",
            "abrí wp con Marco Antonio",
            "abrí wsp con Marco Antonio",
            "abrí WhatsApp y el chat de Marco Antonio",
            "abrí WhatsApp y el del chat de Marco Antonio",
            "abrí wp y el chat de Marco Antonio",
            "abrí wp y anda al chat de Marco Antonio",
            "quiero hablar con Marco Antonio por WhatsApp"
        ).forEach { phrase ->
            val parsed = parser.parse(phrase)

            assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent, phrase)
            assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME), phrase)
        }
    }

    @Test
    fun parseaAbrirChatDeContactoDemoComoOpenWhatsAppChat() {
        val parsed = parser.parse("abrí el chat de Marco Antonio")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertTrue(parsed.missingSlots.isEmpty())
        assertTrue(parsed.requiresConfirmation)
    }

    @Test
    fun parseaAbrirChatConContactoDemoComoOpenWhatsAppChat() {
        val parsed = parser.parse("abrí chat con Marco Antonio")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaAndaAlChatDeContactoDemoComoOpenWhatsAppChat() {
        val parsed = parser.parse("andá al chat de Marco Antonio")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaAbrirWhatsAppConContactoDemoComoOpenWhatsAppChat() {
        val parsed = parser.parse("abrí WhatsApp con Marco Antonio")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaAbrirWpConContactoDemoComoOpenWhatsAppChat() {
        val parsed = parser.parse("abrí wp con Marco Antonio")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaAbrirWspConContactoDemoComoOpenWhatsAppChat() {
        val parsed = parser.parse("abrí wsp con Marco Antonio")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun parseaQuieroHablarConContactoDemoPorWhatsAppComoOpenWhatsAppChat() {
        val parsed = parser.parse("quiero hablar con Marco Antonio por WhatsApp")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun abrirChatSinContactoDemoTieneMissingContactName() {
        val parsed = parser.parse("abrí chat")

        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent)
        assertEquals(listOf(AgentSlotName.CONTACT_NAME), parsed.missingSlots)
        assertFalse(parsed.requiresConfirmation)
    }

    @Test
    fun abrirWhatsAppSinContactoDemoEntraEnModoGuiado() {
        listOf("abrí WhatsApp", "abrí wp", "abrí wsp", "abrí wpp", "abrí wasap").forEach { phrase ->
            val parsed = parser.parse(phrase)

            assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent, phrase)
            assertEquals(listOf(AgentSlotName.WHATSAPP_ACTION), parsed.missingSlots, phrase)
        }
    }

    @Test
    fun parseaWhatsAppGuiadoConMuletillaArgentina() {
        val parsed = parser.parse("che abrí wp")

        assertEquals(AgentIntent.OPEN_WHATSAPP, parsed.intent)
        assertEquals(listOf(AgentSlotName.WHATSAPP_ACTION), parsed.missingSlots)
    }

    @Test
    fun parseaAbrimeYBuscameChatComoOpenWhatsAppChat() {
        listOf(
            "abrime el chat de Marco",
            "buscame el chat de Marco Antonio",
            "dale buscá el chat de Marco"
        ).forEach { phrase ->
            val parsed = parser.parse(phrase)

            assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, parsed.intent, phrase)
            assertTrue(parsed.slotValue(AgentSlotName.CONTACT_NAME).orEmpty().contains("Marco"), phrase)
        }
    }

    @Test
    fun parseaComposeConDecileYEscribile() {
        val decile = parser.parse("decile a ContactoDemo que estoy llegando")
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, decile.intent)
        assertEquals("ContactoDemo", decile.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", decile.slotValue(AgentSlotName.MESSAGE_TEXT))

        val escribile = parser.parse("escribile a mamá que estoy bien")
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, escribile.intent)
        assertEquals("mamá", escribile.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy bien", escribile.slotValue(AgentSlotName.MESSAGE_TEXT))
    }

    @Test
    fun parseaLlamadasArgentinas() {
        listOf("llamame a mamá", "llamá a mi viejo", "llamá a mi vieja", "llamá a mi novia").forEach { phrase ->
            assertEquals(AgentIntent.CALL_CONTACT, parser.parse(phrase).intent, phrase)
        }
    }

    @Test
    fun parseaMapasYUbicacionArgentina() {
        assertEquals(AgentIntent.GET_CURRENT_LOCATION, parser.parse("dónde ando").intent)
        assertEquals(AgentIntent.GET_CURRENT_LOCATION, parser.parse("ubicame").intent)
        assertEquals(AgentIntent.OPEN_MAPS, parser.parse("abrime mapas").intent)

        val laburo = parser.parse("llevame al laburo")
        assertEquals(AgentIntent.NAVIGATE_TO_DESTINATION, laburo.intent)
        assertEquals("laburo", laburo.slotValue(AgentSlotName.DESTINATION))
    }

    @Test
    fun mandaleAContactoDemoSigueSiendoComposeNoChat() {
        // Si la frase tiene verbo de mensajería, COMPOSE manda — no OpenChat.
        val parsed = parser.parse("mandale a Marco Antonio que estoy llegando")

        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
        assertEquals("Marco Antonio", parsed.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", parsed.slotValue(AgentSlotName.MESSAGE_TEXT))
    }

    @Test
    fun escribileAContactoDemoPorWhatsAppSigueSiendoComposeNoChat() {
        val parsed = parser.parse("escribile a Marco Antonio por WhatsApp que estoy llegando")

        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, parsed.intent)
    }
}
