package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentConversationManagerTest {
    private val parser = LocalIntentParser()

    @Test
    fun siFaltaContactoPreguntaAQuien() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("mandale un mensaje"))

        assertEquals("¿A quién querés mandarle el mensaje?", outcome.spokenText)
        assertEquals(AgentState.WAITING_CONTACT, outcome.targetState)
        assertEquals(AgentSlotName.CONTACT_NAME, outcome.missingSlot)
    }

    @Test
    fun siFaltaMensajePreguntaQueMensaje() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("mandale a Sofi"))

        assertEquals("¿Qué mensaje querés mandarle?", outcome.spokenText)
        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(AgentSlotName.MESSAGE_TEXT, outcome.missingSlot)
    }

    @Test
    fun cancelarLimpiaEstadoPendiente() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a Sofi"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun confirmarSinPendingRespondeClaro() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("confirmar"))

        assertTrue(outcome.isError)
        assertEquals("No hay ninguna acción pendiente para confirmar.", outcome.spokenText)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun nuevoComandoAbandonaPendingSensibleDeFormaSegura() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a Sofi"))

        val blocked = manager.handle(parser.parse("mi código de verificación es 123456"))
        assertTrue(blocked.isError)
        assertFalse(manager.hasPendingSlotRequest)

        val next = manager.handle(parser.parse("abrí WhatsApp"))
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, next.targetState)
        assertNull(next.suggestedIntent)
    }

    @Test
    fun erroresRecuperablesVuelvenAEscuchar() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("hacé algo raro"))

        assertTrue(outcome.isError)
        assertEquals(AgentState.ERROR_RECOVERABLE, outcome.targetState)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun callarEntraEnStoppedByUser() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a Sofi"))

        val outcome = manager.handle(parser.parse("callar"))

        assertEquals(AgentState.STOPPED_BY_USER, outcome.targetState)
        assertEquals("", outcome.spokenText)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun completaContactoYMensajeSinEjecutarAndroid() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))
        val asksMessage = manager.handle(parser.parse("Sofi"))
        val confirmation = manager.handle(parser.parse("estoy llegando"))

        assertEquals("¿Qué mensaje querés mandarle?", asksMessage.spokenText)
        assertEquals(AgentState.WAITING_CONFIRMATION, confirmation.targetState)
        assertTrue(confirmation.needsConfirmation)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, confirmation.suggestedIntent?.intent)
        assertEquals("Sofi", confirmation.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", confirmation.suggestedIntent?.slotValue(AgentSlotName.MESSAGE_TEXT))
    }

    @Test
    fun siNoConfirmaMensajePendiente() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a Marco Antonio que estoy llegando"))

        val outcome = manager.handle(parser.parse("sí"))

        assertTrue(outcome.isError)
        assertNull(outcome.suggestedIntent)
    }

    @Test
    fun confirmarConfirmaMensajePendiente() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a Marco Antonio que estoy llegando"))

        val outcome = manager.handle(parser.parse("confirmar"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, outcome.suggestedIntent?.intent)
        assertEquals("Marco Antonio", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun siFaltaContactoEnLlamadaPreguntaAQuien() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("llamar"))

        assertEquals("¿A quién querés llamar?", outcome.spokenText)
        assertEquals(AgentState.WAITING_CONTACT, outcome.targetState)
        assertEquals(AgentSlotName.CONTACT_NAME, outcome.missingSlot)
    }

    @Test
    fun cancelarLimpiaWaitingContactDeLlamada() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("llamar"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun nuevoComandoAbandonaPendingDeLlamadaDeFormaSegura() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("llamar"))

        val outcome = manager.handle(parser.parse("abrí WhatsApp"))

        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertNull(outcome.suggestedIntent)
        assertTrue(manager.hasPendingSlotRequest)
    }
    @Test
    fun siFaltaNumeroPreguntaNumero() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("guardá el número de Sofi"))

        assertEquals("¿Qué número querés guardar para Sofi?", outcome.spokenText)
        assertEquals(AgentState.WAITING_PHONE_NUMBER, outcome.targetState)
        assertEquals(AgentSlotName.PHONE_NUMBER, outcome.missingSlot)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun siFaltaNombreParaGuardarNumeroPreguntaNombre() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("guardá el número"))

        assertEquals("¿De quién querés guardar el número?", outcome.spokenText)
        assertEquals(AgentState.WAITING_CONTACT, outcome.targetState)
        assertEquals(AgentSlotName.CONTACT_NAME, outcome.missingSlot)
    }

    @Test
    fun guardarContactoPideConfirmacion() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("recordá que Sofi es contacto de confianza"))

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertTrue(outcome.needsConfirmation)
        assertEquals(AgentIntent.SAVE_CONTACT, outcome.suggestedIntent?.intent)
    }

    @Test
    fun cancelarGuardadoDeContactoLimpiaPending() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("recordá que Sofi es contacto de confianza"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun confirmarGuardadoDeContactoDevuelveIntentSugerido() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("recordá que Sofi es contacto de confianza"))

        val outcome = manager.handle(parser.parse("confirmar"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.SAVE_CONTACT, outcome.suggestedIntent?.intent)
    }

    @Test
    fun borrarContactoPideConfirmacion() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("olvidá el contacto Sofi"))

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertTrue(outcome.needsConfirmation)
        assertEquals(AgentIntent.DELETE_CONTACT, outcome.suggestedIntent?.intent)
    }
    @Test
    fun siFaltaDestinoPreguntaADonde() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("navegar"))

        assertEquals("¿A dónde querés ir?", outcome.spokenText)
        assertEquals(AgentState.WAITING_DESTINATION, outcome.targetState)
        assertEquals(AgentSlotName.DESTINATION, outcome.missingSlot)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun siFaltaAliasPreguntaConQueNombre() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("guardá esta ubicación"))

        assertEquals("¿Con qué nombre querés guardar esta ubicación?", outcome.spokenText)
        assertEquals(AgentState.WAITING_LOCATION_ALIAS, outcome.targetState)
        assertEquals(AgentSlotName.LOCATION_ALIAS, outcome.missingSlot)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun cancelarLimpiaPendingDeUbicacion() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("guardá esta ubicación"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    // --- WHATSAPP GUIDED MODE ---

    @Test
    fun abrirWpPreguntaAccionYQuedaEsperandoWhatsAppAction() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("abrí wp"))

        assertEquals(AgentConversationManager.WHATSAPP_GUIDED_QUESTION, outcome.spokenText)
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertEquals(AgentSlotName.WHATSAPP_ACTION, outcome.missingSlot)
        assertTrue(manager.hasPendingSlotRequest)
        assertNull(outcome.suggestedIntent)
    }

    @Test
    fun desdeWaitingWhatsAppActionChatDeMarcoResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("chat de Marco"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun desdeWaitingWhatsAppActionMandaleResuelveCompose() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("mandale a Marco que estoy llegando"))

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertTrue(outcome.needsConfirmation)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, outcome.suggestedIntent?.intent)
        assertEquals("Marco", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
        assertEquals("estoy llegando", outcome.suggestedIntent?.slotValue(AgentSlotName.MESSAGE_TEXT))
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun cancelarLimpiaWaitingWhatsAppAction() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun abrirWhatsAppPrincipalDesdeGuidedAbreHandoff() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("abrí WhatsApp principal"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP, outcome.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
    }

    // --- OPEN_WHATSAPP_CHAT ---

    @Test
    fun siFaltaContactoEnAbrirChatPreguntaQueChat() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("abrí chat"))

        assertEquals("¿Qué chat querés abrir?", outcome.spokenText)
        assertEquals(AgentState.WAITING_CONTACT, outcome.targetState)
        assertEquals(AgentSlotName.CONTACT_NAME, outcome.missingSlot)
    }

    @Test
    fun completarContactoEnAbrirChatProponeIntentParaOrquestador() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí chat"))

        val outcome = manager.handle(parser.parse("Marco Antonio"))

        // El manager NO ejecuta Android — sólo entrega el intent listo.
        assertEquals(AgentState.PROCESSING, outcome.targetState)
        val suggested = outcome.suggestedIntent
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, suggested?.intent)
        assertEquals("Marco Antonio", suggested?.slotValue(AgentSlotName.CONTACT_NAME))
        assertTrue(suggested?.missingSlots?.isEmpty() == true)
        assertTrue(suggested?.requiresConfirmation == true)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun abrirChatConContactoCompletoNoPideMasSlots() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("abrí el chat de Marco Antonio"))

        // Si vino completo, va directo al orquestador.
        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun cancelarLimpiaPendingDeAbrirChat() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí chat"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    // --- WHATSAPP REAL-WORLD TUNING ---

    @Test
    fun abrirWpRespondeFraseCortaConChatYMensajeYCancelar() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("abrí wp"))

        // Frase corta orientada a la realidad: dos opciones + salida.
        assertTrue(outcome.spokenText.contains("chat"), "esperaba 'chat' en: ${outcome.spokenText}")
        assertTrue(outcome.spokenText.contains("mensaje"), "esperaba 'mensaje' en: ${outcome.spokenText}")
        assertTrue(outcome.spokenText.contains("WhatsApp principal"), "esperaba WhatsApp principal en: ${outcome.spokenText}")
        // No debe ser largo: el guion físico falló por respuestas largas.
        assertTrue(outcome.spokenText.length <= 80, "largo > 80: ${outcome.spokenText}")
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
    }

    @Test
    fun desdeWaitingWhatsAppActionContactoSoloPreguntaChatOMensaje() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("Marco Antonio"))

        assertEquals(
            "¿Abrir chat con Marco Antonio o mandarle un mensaje?",
            outcome.spokenText
        )
        assertEquals(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE, outcome.targetState)
        assertTrue(manager.hasPendingSlotRequest)
        assertNull(outcome.suggestedIntent)
    }

    @Test
    fun desdeWaitingWhatsAppActionConMarcoLoTrataComoContactoAmbiguo() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("con Marco"))

        assertEquals(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE, outcome.targetState)
        assertTrue(outcome.spokenText.contains("Marco"))
    }

    @Test
    fun desdeWaitingWhatsAppActionElDeMarcoLoTrataComoContactoAmbiguo() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("el de Marco"))

        assertEquals(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE, outcome.targetState)
    }

    @Test
    fun desdeWaitingWhatsAppActionChatMarcoSinDeResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("chat Marco"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun desdeWaitingWhatsAppActionBuscaElChatDeMarcoResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("buscá el chat de Marco Antonio"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco Antonio", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun desdeWaitingWhatsAppActionDaleBuscaChatNoConfirmaEInterpretaChat() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("dale buscá el chat de Marco"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun daleNoConfirmaPendingReal() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a Marco que estoy llegando"))

        val outcome = manager.handle(parser.parse("dale"))

        assertTrue(outcome.isError)
        assertNull(outcome.suggestedIntent)
    }

    @Test
    fun desdeWaitingWhatsAppActionMensajeParaMarcoPideMensaje() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("mensaje para Marco Antonio"))

        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(AgentSlotName.MESSAGE_TEXT, outcome.missingSlot)
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageDecirChatResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("chat"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco Antonio", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageDecirMensajePreguntaTextoUsandoElContactoGuardado() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("mensaje"))

        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(
            "¿Qué mensaje querés mandarle a Marco Antonio?",
            outcome.spokenText
        )
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageSiNoConfirma() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("sí"))

        // "sí" no es ni "chat" ni "mensaje" — no debe disparar nada externo.
        assertNull(outcome.suggestedIntent)
        assertTrue(outcome.isError || outcome.shouldListenAgain)
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageCancelarLimpiaTodo() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun desdeWaitingWhatsAppActionFraseRuidoVuelveAlRetryCorto() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("uh eh"))

        // No reconoce contacto ni acción → fallback guiado, sigue escuchando.
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertEquals(
            "No escuché bien. Decime: chat de Marco, mensaje para Marco, o cancelar.",
            outcome.spokenText
        )
        assertTrue(manager.hasPendingSlotRequest)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun desdeWaitingWhatsAppActionFallbackNoSeRepiteEnLoop() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val first = manager.handle(parser.parse("uh eh"))
        val second = manager.handle(parser.parse("uh eh"))

        assertTrue(first.spokenText.isNotBlank())
        assertEquals("", second.spokenText)
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, second.targetState)
        assertTrue(second.shouldListenAgain)
        assertTrue(manager.hasPendingSlotRequest)
    }

    @Test
    fun errorSinTextoUtilNoLimpiaContextoWhatsAppGuided() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("abrí wp"))

        val outcome = manager.handle(parser.parse("uh eh"))

        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertTrue(manager.hasPendingSlotRequest)
    }
}
