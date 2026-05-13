package com.ojoclaro.android.agent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentConversationManagerTest {
    private val parser = LocalIntentParser()

    /**
     * QA Samsung 2026-05-13: el parser ya NO genera OPEN_WHATSAPP con
     * missingSlots=[WHATSAPP_ACTION] desde frases tipo "abrí wp" porque eso
     * dejaba la app colgada en "Pendiente: acción de WhatsApp" sin abrir
     * WhatsApp. Para los tests legacy que ejercitan el flujo guiado,
     * construimos manualmente el ParsedAgentIntent con el slot pendiente.
     * En produccion, ese intent solo se genera desde frases ambiguas tipo
     * "WhatsApp" a secas (a futuro) o desde el LLM/orchestrator.
     */
    private fun whatsAppGuidedIntent(rawText: String = "WhatsApp"): ParsedAgentIntent =
        ParsedAgentIntent(
            intent = AgentIntent.OPEN_WHATSAPP,
            slots = emptyList(),
            rawText = rawText,
            confidence = 0.92f,
            missingSlots = listOf(AgentSlotName.WHATSAPP_ACTION)
        )

    @Test
    fun siFaltaContactoDemoPreguntaAQuien() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("mandale un mensaje"))

        assertEquals("¿A quién querés mandarle el mensaje?", outcome.spokenText)
        assertEquals(AgentState.WAITING_CONTACT, outcome.targetState)
        assertEquals(AgentSlotName.CONTACT_NAME, outcome.missingSlot)
    }

    @Test
    fun siFaltaMensajePreguntaQueMensaje() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("mandale a ContactoDemo"))

        assertEquals("¿Qué mensaje querés mandarle?", outcome.spokenText)
        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(AgentSlotName.MESSAGE_TEXT, outcome.missingSlot)
    }

    @Test
    fun cancelarLimpiaEstadoPendiente() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a ContactoDemo"))

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
        manager.handle(parser.parse("mandale a ContactoDemo"))

        val blocked = manager.handle(parser.parse("mi código de verificación es 123456"))
        assertTrue(blocked.isError)
        assertFalse(manager.hasPendingSlotRequest)

        // "abrí WhatsApp" ahora es directo: el manager lo emite como suggestedIntent
        // OPEN_WHATSAPP en estado PROCESSING para que el caller (orchestrator) abra
        // la app. NO debe quedarse en WAITING_WHATSAPP_ACTION.
        val next = manager.handle(parser.parse("abrí WhatsApp"))
        assertEquals(AgentState.PROCESSING, next.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP, next.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
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
        manager.handle(parser.parse("mandale a ContactoDemo"))

        val outcome = manager.handle(parser.parse("callar"))

        assertEquals(AgentState.STOPPED_BY_USER, outcome.targetState)
        assertEquals("", outcome.spokenText)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun completaContactoDemoYMensajeSinEjecutarAndroid() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale un mensaje"))
        val asksMessage = manager.handle(parser.parse("ContactoDemo"))
        val confirmation = manager.handle(parser.parse("estoy llegando"))

        assertEquals("¿Qué mensaje querés mandarle?", asksMessage.spokenText)
        assertEquals(AgentState.WAITING_CONFIRMATION, confirmation.targetState)
        assertTrue(confirmation.needsConfirmation)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, confirmation.suggestedIntent?.intent)
        assertEquals("ContactoDemo", confirmation.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
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
    fun siFaltaContactoDemoEnLlamadaPreguntaAQuien() {
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

        // "abrí WhatsApp" abre directamente: limpia pending y emite suggestedIntent.
        val outcome = manager.handle(parser.parse("abrí WhatsApp"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP, outcome.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
    }
    @Test
    fun siFaltaNumeroPreguntaNumero() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("guardá el número de ContactoDemo"))

        assertEquals("¿Qué número querés guardar para ContactoDemo?", outcome.spokenText)
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
    fun guardarContactoDemoPideConfirmacion() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("recordá que ContactoDemo es contacto de confianza"))

        assertEquals(AgentState.WAITING_CONFIRMATION, outcome.targetState)
        assertTrue(outcome.needsConfirmation)
        assertEquals(AgentIntent.SAVE_CONTACT, outcome.suggestedIntent?.intent)
    }

    @Test
    fun cancelarGuardadoDeContactoDemoLimpiaPending() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("recordá que ContactoDemo es contacto de confianza"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun confirmarGuardadoDeContactoDemoDevuelveIntentSugerido() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("recordá que ContactoDemo es contacto de confianza"))

        val outcome = manager.handle(parser.parse("confirmar"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.SAVE_CONTACT, outcome.suggestedIntent?.intent)
    }

    @Test
    fun borrarContactoDemoPideConfirmacion() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("olvidá el contacto ContactoDemo"))

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
    fun abrirWpAbreWhatsAppDirectamenteSinPedirAccion() {
        // QA Samsung 2026-05-13: "abrí wp" debe abrir WhatsApp directamente.
        // Antes pedía "Decime: chat de ContactoDemo, mensaje para ContactoDemo, o
        // WhatsApp principal" y dejaba la app colgada.
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("abrí wp"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP, outcome.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
    }

    /**
     * Defensa: el flujo guiado sigue intacto si el caller construye explícitamente
     * un OPEN_WHATSAPP con missingSlot=WHATSAPP_ACTION (caso reservado para futuras
     * frases ambiguas como "WhatsApp" a secas). El parser ya no lo emite desde
     * "abrí wp".
     */
    @Test
    fun openWhatsAppConSlotPendienteExplicitoSigueEntrandoEnFlujoGuiado() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(whatsAppGuidedIntent("WhatsApp"))

        assertEquals(AgentConversationManager.WHATSAPP_GUIDED_QUESTION, outcome.spokenText)
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertEquals(AgentSlotName.WHATSAPP_ACTION, outcome.missingSlot)
        assertTrue(manager.hasPendingSlotRequest)
        assertNull(outcome.suggestedIntent)
    }

    @Test
    fun desdeWaitingWhatsAppActionChatDeMarcoResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("chat de Marco"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun desdeWaitingWhatsAppActionMandaleResuelveCompose() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

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
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun abrirWhatsAppPrincipalDesdeGuidedAbreHandoff() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("abrí WhatsApp principal"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP, outcome.suggestedIntent?.intent)
        assertFalse(manager.hasPendingSlotRequest)
    }

    // --- OPEN_WHATSAPP_CHAT ---

    @Test
    fun siFaltaContactoDemoEnAbrirChatPreguntaQueChat() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("abrí chat"))

        assertEquals("¿Qué chat querés abrir?", outcome.spokenText)
        assertEquals(AgentState.WAITING_CONTACT, outcome.targetState)
        assertEquals(AgentSlotName.CONTACT_NAME, outcome.missingSlot)
    }

    @Test
    fun completarContactoDemoEnAbrirChatProponeIntentParaOrquestador() {
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
    fun abrirChatConContactoDemoCompletoNoPideMasSlots() {
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
    fun guidedQuestionCubreChatMensajeYCancelarYEsCorta() {
        // El flujo guiado sigue intacto cuando el caller construye explicitamente
        // un OPEN_WHATSAPP con slot pendiente (futuras frases ambiguas tipo
        // "WhatsApp" a secas). "abrí wp" ya no entra acá — abre directamente.
        val manager = AgentConversationManager()

        val outcome = manager.handle(whatsAppGuidedIntent())

        assertTrue(outcome.spokenText.contains("chat"), "esperaba 'chat' en: ${outcome.spokenText}")
        assertTrue(outcome.spokenText.contains("mensaje"), "esperaba 'mensaje' en: ${outcome.spokenText}")
        assertTrue(outcome.spokenText.contains("WhatsApp principal"), "esperaba WhatsApp principal en: ${outcome.spokenText}")
        assertTrue(outcome.spokenText.length <= 80, "largo > 80: ${outcome.spokenText}")
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
    }

    @Test
    fun desdeWaitingWhatsAppActionContactoDemoSoloPreguntaChatOMensaje() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

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
    fun desdeWaitingWhatsAppActionConMarcoLoTrataComoContactoDemoAmbiguo() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("con Marco"))

        assertEquals(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE, outcome.targetState)
        assertTrue(outcome.spokenText.contains("Marco"))
    }

    @Test
    fun desdeWaitingWhatsAppActionElDeMarcoLoTrataComoContactoDemoAmbiguo() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("el de Marco"))

        assertEquals(AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE, outcome.targetState)
    }

    @Test
    fun desdeWaitingWhatsAppActionChatMarcoSinDeResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("chat Marco"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun desdeWaitingWhatsAppActionBuscaElChatDeMarcoResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("buscá el chat de Marco Antonio"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco Antonio", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
    }

    @Test
    fun desdeWaitingWhatsAppActionDaleBuscaChatNoConfirmaEInterpretaChat() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

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
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("mensaje para Marco Antonio"))

        assertEquals(AgentState.WAITING_MESSAGE, outcome.targetState)
        assertEquals(AgentSlotName.MESSAGE_TEXT, outcome.missingSlot)
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageDecirChatResuelveOpenChat() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("chat"))

        assertEquals(AgentState.PROCESSING, outcome.targetState)
        assertEquals(AgentIntent.OPEN_WHATSAPP_CHAT, outcome.suggestedIntent?.intent)
        assertEquals("Marco Antonio", outcome.suggestedIntent?.slotValue(AgentSlotName.CONTACT_NAME))
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageDecirMensajePreguntaTextoUsandoElContactoDemoGuardado() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())
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
        manager.handle(whatsAppGuidedIntent())
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("sí"))

        // "sí" no es ni "chat" ni "mensaje" — no debe disparar nada externo.
        assertNull(outcome.suggestedIntent)
        assertTrue(outcome.isError || outcome.shouldListenAgain)
    }

    @Test
    fun desdeWaitingWhatsAppChatOrMessageCancelarLimpiaTodo() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())
        manager.handle(parser.parse("Marco Antonio"))

        val outcome = manager.handle(parser.parse("cancelar"))

        assertEquals("Acción cancelada.", outcome.spokenText)
        assertEquals(AgentState.IDLE, outcome.targetState)
        assertFalse(manager.hasPendingSlotRequest)
    }

    @Test
    fun desdeWaitingWhatsAppActionFraseRuidoVuelveAlRetryCorto() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("uh eh"))

        // No reconoce contacto ni acción → fallback guiado, sigue escuchando.
        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertEquals(
            "No entendi. Estas en un flujo de WhatsApp. Podes decir: WhatsApp principal, chat de Marco, mensaje para Marco, o cancelar.",
            outcome.spokenText
        )
        assertTrue(manager.hasPendingSlotRequest)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun desdeWaitingWhatsAppActionFallbackNoSeRepiteEnLoop() {
        val manager = AgentConversationManager()
        manager.handle(whatsAppGuidedIntent())

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
        manager.handle(whatsAppGuidedIntent())

        val outcome = manager.handle(parser.parse("uh eh"))

        assertEquals(AgentState.WAITING_WHATSAPP_ACTION, outcome.targetState)
        assertTrue(manager.hasPendingSlotRequest)
    }

    // --- REPEAT_LAST ---

    @Test
    fun repetirSinHistorialDevuelveFallback() {
        val manager = AgentConversationManager()

        val outcome = manager.handle(parser.parse("repetí"))

        assertEquals(
            AgentConversationManager.REPEAT_LAST_FALLBACK_TEXT,
            outcome.spokenText
        )
        assertNull(outcome.suggestedIntent)
        assertFalse(outcome.needsConfirmation)
        assertTrue(outcome.shouldListenAgain)
    }

    @Test
    fun repetirDevuelveUltimaRespuestaConPrefijoRepito() {
        val manager = AgentConversationManager()
        val first = manager.handle(parser.parse("llamar"))
        assertEquals("¿A quién querés llamar?", first.spokenText)

        val repeated = manager.handle(parser.parse("repetí"))

        assertEquals("Repito. ${first.spokenText}", repeated.spokenText)
        assertNull(repeated.suggestedIntent)
        assertFalse(repeated.needsConfirmation)
    }

    @Test
    fun repetirNoExpulsaPendingActivo() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("llamar"))
        assertTrue(manager.hasPendingSlotRequest)

        manager.handle(parser.parse("repetí"))

        assertTrue(manager.hasPendingSlotRequest)
    }

    @Test
    fun repetirDosVecesUsaSiempreLaRespuestaOriginal() {
        val manager = AgentConversationManager()
        val first = manager.handle(parser.parse("llamar"))

        val firstRepeat = manager.handle(parser.parse("repetí"))
        val secondRepeat = manager.handle(parser.parse("repetir"))

        assertEquals("Repito. ${first.spokenText}", firstRepeat.spokenText)
        assertEquals("Repito. ${first.spokenText}", secondRepeat.spokenText)
    }

    @Test
    fun repetirNoEjecutaAccionExterna() {
        val manager = AgentConversationManager()
        manager.handle(parser.parse("mandale a ContactoDemo que estoy llegando"))

        val outcome = manager.handle(parser.parse("repetí"))

        assertNull(outcome.suggestedIntent)
        assertFalse(outcome.needsConfirmation)
    }

    @Test
    fun repetirDespuesDeCallarSigueRecordandoLaRespuesta() {
        val manager = AgentConversationManager()
        val first = manager.handle(parser.parse("llamar"))
        manager.handle(parser.parse("callar"))

        val repeated = manager.handle(parser.parse("repetí"))

        assertEquals("Repito. ${first.spokenText}", repeated.spokenText)
    }
}
