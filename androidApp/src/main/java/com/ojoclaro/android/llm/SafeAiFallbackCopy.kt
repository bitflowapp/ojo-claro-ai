package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.model.AppState

/**
 * Copy humano para cuando Ojo Claro no pudo entender o cuando el AI fallback no
 * pudo proponer una accion segura.
 *
 * Regla de producto:
 *  - El usuario final NUNCA escucha frases tecnicas tipo "no estoy usando la IA",
 *    "proxy no configurado" o "modo IA".
 *  - El asistente siempre devuelve sugerencias humanas sobre que se puede decir.
 *  - Si la pantalla es sensible, se avisa que no se va a leerla.
 */
object SafeAiFallbackCopy {

    const val GENERAL: String =
        "No entendí bien. Podés decir: qué hay en pantalla, qué puedo hacer acá, abrí WhatsApp o repetí."

    const val WHATSAPP_OPEN: String =
        "No entendí bien. En WhatsApp podés decir: qué chats ves, qué puedo hacer en este chat, " +
            "cómo mando una foto o cancelar."

    const val WHATSAPP_WAITING: String =
        "No entendí. Estás en un flujo de WhatsApp. Podés decir: WhatsApp principal, " +
            "chat de un contacto, mensaje para un contacto, o cancelar."

    const val SENSITIVE_SCREEN: String =
        "Esta pantalla puede contener información sensible. No voy a leerla."

    const val SAFE_MODE_REMINDER: String =
        "Estoy en modo seguro. No voy a hacer acciones sensibles sin confirmarte."

    const val UNABLE_TO_RESOLVE: String =
        "No lo pude resolver con seguridad. Decime una acción concreta."

    const val CAPABILITIES_SUMMARY: String =
        "Puedo leer pantalla, abrir WhatsApp, guiarte o repetir lo último."

    /**
     * Devuelve la sugerencia contextual segun el estado actual del agente.
     *
     * - WhatsApp activo o flujo de WhatsApp -> sugerencias de WhatsApp.
     * - WAITING_CONFIRMATION -> recuerda como confirmar/cancelar.
     * - cualquier otro estado -> sugerencias generales.
     *
     * Si [sensitiveScreen] es true, devuelve el aviso de pantalla sensible y nada mas.
     */
    fun contextual(
        appState: AppState,
        agentState: AgentState? = null,
        externalApp: String? = null,
        sensitiveScreen: Boolean = false
    ): String {
        if (sensitiveScreen) return SENSITIVE_SCREEN
        val inWhatsAppFlow = externalApp.equals("whatsapp", ignoreCase = true) ||
            appState == AppState.WAITING_WHATSAPP_ACTION ||
            appState == AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
            appState == AppState.WAITING_CONTACT ||
            appState == AppState.WAITING_MESSAGE ||
            agentState == AgentState.WAITING_WHATSAPP_ACTION ||
            agentState == AgentState.WAITING_WHATSAPP_CHAT_OR_MESSAGE ||
            agentState == AgentState.WAITING_CONTACT ||
            agentState == AgentState.WAITING_MESSAGE
        if (inWhatsAppFlow) {
            return if (externalApp.equals("whatsapp", ignoreCase = true)) {
                WHATSAPP_OPEN
            } else {
                WHATSAPP_WAITING
            }
        }
        if (appState == AppState.WAITING_CONFIRMATION) {
            return "No entendí. Tenés una acción pendiente. Podés decir: confirmar o cancelar."
        }
        return GENERAL
    }

    /**
     * Tokens internos/tecnicos que NUNCA deben llegar al usuario final.
     *
     * Si el LLM o un guard devuelve una respuesta que contiene alguno, el caller
     * debe degradar a copy contextual humano via [contextual].
     */
    private val DEBUG_TOKENS: List<String> = listOf(
        "no estoy usando la ia",
        "no uso la ia",
        "ia flexible",
        "modo ia",
        "no tengo ia",
        "proxy",
        "llm",
        "low confidence",
        "disabled",
        "api key"
    )

    fun looksLikeAiDebugCopy(text: String): Boolean {
        if (text.isBlank()) return false
        val lower = text.lowercase()
        return DEBUG_TOKENS.any { it in lower }
    }

    /**
     * Etiqueta corta para mostrar en UI sobre el "modo" actual.
     * No menciona IA ni proxy.
     */
    fun modeLabel(
        appState: AppState,
        externalApp: String? = null
    ): String {
        if (externalApp.equals("whatsapp", ignoreCase = true)) return "Modo: WhatsApp"
        return when (appState) {
            AppState.WAITING_WHATSAPP_ACTION,
            AppState.WAITING_WHATSAPP_CHAT_OR_MESSAGE,
            AppState.WAITING_CONTACT,
            AppState.WAITING_MESSAGE -> "Modo: WhatsApp"

            AppState.WAITING_CONFIRMATION -> "Modo: guía"
            AppState.SCANNING -> "Modo: pantalla"
            else -> "Modo: seguro"
        }
    }
}
