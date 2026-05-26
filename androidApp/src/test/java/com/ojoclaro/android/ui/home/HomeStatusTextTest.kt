package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifica que la UI no diga "Esperando confirmación" cuando en realidad el agente
 * está esperando una sub-acción específica. La QA física reportó este bug exacto:
 * tras "abrí wp" la pantalla mostraba "Esperando confirmación" cuando debería indicar
 * el contexto vivo de WhatsApp.
 */
class HomeStatusTextTest {

    @Test
    fun waitingWhatsAppActionMuestraLeyendoWhatsApp() {
        val label = statusText(
            appState = AppState.WAITING_CONFIRMATION,
            agentState = AgentState.WAITING_WHATSAPP_ACTION
        )

        assertEquals("Leyendo WhatsApp", label)
    }

    @Test
    fun waitingWhatsAppChatOrMessageMuestraLeyendoWhatsApp() {
        val label = statusText(
            appState = AppState.WAITING_CONFIRMATION,
            agentState = AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE
        )

        assertEquals("Leyendo WhatsApp", label)
    }

    @Test
    fun waitingConfirmationGenericoSinAgentStateMantieneEsperandoConfirmacion() {
        val label = statusText(
            appState = AppState.WAITING_CONFIRMATION,
            agentState = null
        )

        assertEquals("Estela está esperando tu confirmación.", label)
    }

    @Test
    fun waitingContactMuestraEsperandoContactoDemo() {
        val label = statusText(
            appState = AppState.WAITING_CONFIRMATION,
            agentState = AgentState.WAITING_CONTACT
        )

        assertEquals("Esperando contacto", label)
    }

    @Test
    fun waitingMessageMuestraEsperandoMensaje() {
        val label = statusText(
            appState = AppState.WAITING_CONFIRMATION,
            agentState = AgentState.WAITING_MESSAGE
        )

        assertEquals("Esperando mensaje", label)
    }

    @Test
    fun listeningMuestraEscuchando() {
        val label = statusText(
            appState = AppState.LISTENING,
            agentState = null
        )

        assertEquals("Estela está escuchando.", label)
    }

    @Test
    fun externalAppHandoffMuestraAppExterna() {
        val label = statusText(
            appState = AppState.EXTERNAL_APP_HANDOFF,
            agentState = null
        )

        assertEquals("App externa", label)
    }

    @Test
    fun robotStatusBlockMuestraEstadoCompactoYPendingSeguro() {
        val text = robotStatusBlockText(
            appState = AppState.WAITING_WHATSAPP_ACTION,
            agentState = AgentState.WAITING_WHATSAPP_ACTION,
            pendingSummary = "WAITING_WHATSAPP_ACTION",
            loading = false,
            micListening = false,
            ttsSpeaking = false
        )

        assertEquals("Estado: Leyendo WhatsApp\nPendiente: accion de WhatsApp", text)
    }

    @Test
    fun recognizedSpeechBlockEsCompacto() {
        assertEquals("Última frase: abrir WhatsApp", recognizedSpeechBlockText("abrir WhatsApp"))
        assertEquals("Última frase: -", recognizedSpeechBlockText(""))
    }

    @Test
    fun pendingActionLabelNoIncluyeContactoNiMensaje() {
        val label = pendingActionLabel(
            appState = AppState.WAITING_CONFIRMATION,
            agentState = null,
            pendingSummary = "COMPOSE_WHATSAPP_MESSAGE"
        )

        assertEquals("confirmacion", label)
    }
}
