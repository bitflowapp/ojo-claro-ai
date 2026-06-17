package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * WhatsApp Anxiety Hardening — narrador PURO del estado de WhatsApp para una
 * persona no vidente, ansiosa o desorientada. Devuelve frases CORTAS y CALMAS,
 * como una persona vidente paciente: dice dónde está, qué puede hacer y que no
 * se envió nada.
 *
 * Garantía dura: nunca incluye contenido de chats, nombres de contactos ni
 * labels de la UI. Solo el "dónde estás" estructural (booleanos), conteos y la
 * tranquilidad de que no se tocó nada. Es la contraparte hablada de
 * [WhatsAppScreenState], que ya es content-free por diseño.
 */
object WhatsAppStateNarrator {

    /**
     * Snapshot mínimo y content-free para narrar. [pendingStep] vale 1/2 para el
     * flujo de respuesta WA-5 y [WhatsAppReplyConfirmationResolver.STEP_SEND_DRAFT]
     * para el borrador V1.2; 0 cuando no hay envío preparado.
     */
    data class Snapshot(
        val isOpen: Boolean,
        val isInChat: Boolean,
        val isUnknown: Boolean,
        val hasPendingReply: Boolean,
        val pendingStep: Int,
        val hasPendingSendDraft: Boolean,
        val recentNotificationCount: Int,
        val safeMode: Boolean,
        val namesWhatsApp: Boolean
    ) {
        val hasPending: Boolean get() = hasPendingReply || hasPendingSendDraft
    }

    private const val NOTHING_SENT = "No envié nada."

    /** "¿Dónde estoy?" / "¿qué estoy viendo?" — ubicación estructural y corta. */
    fun orientation(s: Snapshot): String {
        if (s.hasPending) {
            val where = if (s.isInChat) "dentro de un chat" else "en WhatsApp"
            return "Estás $where, con un mensaje preparado. $NOTHING_SENT ${howToProceed(s)}"
        }
        val place = when {
            s.isInChat -> "Estás dentro de un chat de WhatsApp."
            s.isOpen -> "Estás en WhatsApp, en la lista de chats."
            s.namesWhatsApp -> "WhatsApp no está abierto. Decí: abrí WhatsApp."
            s.isUnknown -> "No estoy seguro de qué pantalla es esta. No voy a tocar nada."
            else -> "No estás en WhatsApp ahora."
        }
        if (s.safeMode) return place
        val notif = notificationsTail(s)
        return (place + notif).trim()
    }

    /** "¿Qué puedo hacer?" — opciones cortas según contexto. */
    fun contextualHelp(s: Snapshot): String = when {
        s.hasPending ->
            "Tenés un mensaje preparado sin enviar. ${howToProceed(s)}"
        s.isInChat ->
            "Podés decir: leé los mensajes, respondé algo, volver, repetir o cancelar."
        s.isOpen ->
            "Podés decir: leé los chats, abrir un chat por número, repetir o cancelar."
        else ->
            "Podés decir: abrí WhatsApp, leé la pantalla, repetir o cancelar."
    }

    /** Fase conversacional DERIVADA del estado (sin plumbing): para "qué pasó". */
    enum class ConversationPhase {
        AWAITING_SEND_CONFIRMATION,
        PREPARING_REPLY,
        IN_CHAT,
        IN_WHATSAPP,
        AWAY
    }

    fun phaseOf(s: Snapshot): ConversationPhase = when {
        s.hasPendingReply && s.pendingStep >= 2 -> ConversationPhase.AWAITING_SEND_CONFIRMATION
        s.hasPendingSendDraft -> ConversationPhase.AWAITING_SEND_CONFIRMATION
        s.hasPendingReply -> ConversationPhase.PREPARING_REPLY
        s.isInChat -> ConversationPhase.IN_CHAT
        s.isOpen -> ConversationPhase.IN_WHATSAPP
        else -> ConversationPhase.AWAY
    }

    /** "¿Qué pasó?" — recap del último estado/respuesta + fase, sin contenido. */
    fun whatHappened(lastResponse: String?, s: Snapshot): String {
        val recap = lastResponse
            ?.takeIf { it.isNotBlank() }
            ?.let { "Lo último que dije fue: ${it.trimEnd('.', ' ')}." }
        return listOfNotNull(recap, phaseSummary(s)).joinToString(" ")
    }

    private fun phaseSummary(s: Snapshot): String = when (phaseOf(s)) {
        ConversationPhase.AWAITING_SEND_CONFIRMATION ->
            "Tenés un mensaje preparado, esperando tu confirmación. $NOTHING_SENT ${howToProceed(s)}"
        ConversationPhase.PREPARING_REPLY ->
            "Estás preparando una respuesta. $NOTHING_SENT ${howToProceed(s)}"
        ConversationPhase.IN_CHAT ->
            "Estás dentro de un chat. No hay nada enviándose. No toqué nada."
        ConversationPhase.IN_WHATSAPP ->
            "Estás en WhatsApp. No hay nada enviándose. No toqué nada."
        ConversationPhase.AWAY ->
            "No hay nada enviándose. No toqué nada."
    }

    fun safeModeEnabled(hasPending: Boolean, pendingStep: Int): String {
        val base = "Modo seguro activado. Te voy a hablar corto y confirmar antes de tocar algo."
        if (!hasPending) return base
        val tail = if (pendingStep >= 2) {
            "Seguís con un mensaje preparado. Para enviar decí: mandalo. Para cancelar, decí: cancelar."
        } else {
            "Seguís con un mensaje preparado, sin enviar. Decí: sí para seguir, o cancelar."
        }
        return "$base $tail"
    }

    fun safeModeDisabled(): String = "Listo, vuelvo a hablarte normal."

    /** Instrucción corta y honesta de cómo seguir o frenar un envío preparado. */
    private fun howToProceed(s: Snapshot): String = when {
        s.pendingStep >= WhatsAppReplyConfirmationResolver.STEP_SEND_DRAFT ->
            "Para enviar decí: enviá. Para cancelar decí: cancelar."
        s.pendingStep >= 2 ->
            "Para enviar decí: mandalo. Para cancelar decí: cancelar."
        else ->
            "Decí: sí para seguir, o cancelar."
    }

    private fun notificationsTail(s: Snapshot): String = when {
        s.recentNotificationCount <= 0 -> ""
        s.recentNotificationCount == 1 -> " Hay 1 mensaje reciente guardado."
        else -> " Hay ${s.recentNotificationCount} mensajes recientes guardados."
    }
}
