package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Accessibility Onboarding & WhatsApp Reliability — matriz de capacidades de
 * RUNTIME (signal-driven). PURA: dada la combinación real de señales del
 * sistema, dice QUÉ puede hacer Estela ahora, qué NO, y cuál es el ÚNICO
 * próximo paso que destraba más. La narración vive en
 * [WhatsAppCapabilityNarrator] para no dejar al usuario perdido.
 *
 * Contrato: el envío real NUNCA está acá (siempre dry-run/doble confirmación);
 * la matriz solo habilita LEER/PREPARAR/AVISAR según permisos.
 */
object WhatsAppCapabilityMatrix {

    /** Señales reales del sistema (todas lecturas, ninguna acción). */
    data class Inputs(
        val whatsappInstalled: Boolean,
        /** Servicio de accesibilidad realmente VINCULADO (isConnected), no solo "enabled". */
        val accessibilityBound: Boolean,
        val notificationListener: Boolean,
        val inWhatsApp: Boolean,
        val inChat: Boolean,
        val backendAvailable: Boolean
    )

    enum class Capability {
        CHECK_NOTIFICATIONS,
        OPEN_WHATSAPP,
        READ_SCREEN,
        PREPARE_REPLY_DRY_RUN
    }

    /** El próximo paso accionable que más destraba (uno solo, para no abrumar). */
    enum class NextStep {
        INSTALL_WHATSAPP,
        ENABLE_ACCESSIBILITY,
        OPEN_WHATSAPP,
        OPEN_CHAT,
        GRANT_NOTIFICATIONS,
        READY
    }

    data class Verdict(
        val can: List<Capability>,
        val blocked: List<Capability>,
        val nextStep: NextStep
    ) {
        fun canDo(capability: Capability): Boolean = capability in can
    }

    fun evaluate(inputs: Inputs): Verdict {
        val can = mutableListOf<Capability>()
        val blocked = mutableListOf<Capability>()

        // Revisar notificaciones: SOLO necesita el listener (no accesibilidad).
        addTo(Capability.CHECK_NOTIFICATIONS, inputs.notificationListener, can, blocked)
        // Abrir WhatsApp: SOLO necesita estar instalado (intent, sin accesibilidad).
        addTo(Capability.OPEN_WHATSAPP, inputs.whatsappInstalled, can, blocked)
        // Leer la pantalla: accesibilidad VINCULADA + WhatsApp adelante.
        addTo(Capability.READ_SCREEN, inputs.accessibilityBound && inputs.inWhatsApp, can, blocked)
        // Preparar respuesta (dry-run + doble confirmación): accesibilidad + dentro de un chat.
        addTo(Capability.PREPARE_REPLY_DRY_RUN, inputs.accessibilityBound && inputs.inChat, can, blocked)

        val nextStep = when {
            !inputs.whatsappInstalled -> NextStep.INSTALL_WHATSAPP
            !inputs.accessibilityBound -> NextStep.ENABLE_ACCESSIBILITY
            !inputs.inWhatsApp -> NextStep.OPEN_WHATSAPP
            !inputs.inChat -> NextStep.OPEN_CHAT
            !inputs.notificationListener -> NextStep.GRANT_NOTIFICATIONS
            else -> NextStep.READY
        }
        return Verdict(can = can.toList(), blocked = blocked.toList(), nextStep = nextStep)
    }

    private fun addTo(
        capability: Capability,
        available: Boolean,
        can: MutableList<Capability>,
        blocked: MutableList<Capability>
    ) {
        if (available) can.add(capability) else blocked.add(capability)
    }
}
