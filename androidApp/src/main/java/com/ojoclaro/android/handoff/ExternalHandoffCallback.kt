package com.ojoclaro.android.handoff

import com.ojoclaro.android.external.ExternalActionEvent

/**
 * Tipo de handoff externo que Ojo Claro acaba de iniciar.
 *
 * Sirve solamente para elegir la frase corta que se dice al volver al frente,
 * no habilita ninguna acción nueva.
 */
enum class ExternalHandoffKind {
    WHATSAPP,
    MAPS,
    PHONE,
    GENERIC
}

/**
 * Frases y clasificación del callback verbal post-handoff.
 *
 * Reglas de producto:
 *  - WhatsApp NUNCA dice "envié" ni "mensaje enviado". Ojo Claro no auto-envía.
 *  - Phone NUNCA afirma que la llamada se completó.
 *  - Maps puede decir que se abrió la navegación, porque eso es lo que pasó.
 *  - Si no se puede clasificar, hay un fallback genérico honesto.
 */
object ExternalHandoffCallbacks {

    const val WHATSAPP_TEXT =
        "Volviste a Ojo Claro. WhatsApp quedó abierto, pero yo no envié nada automáticamente."

    const val MAPS_TEXT =
        "Volviste a Ojo Claro. Abrí la navegación en Maps."

    const val PHONE_TEXT =
        "Volviste a Ojo Claro. Abrí el teléfono, pero la llamada depende de Android."

    const val GENERIC_TEXT =
        "Volviste a Ojo Claro. Abrí la app externa, pero no completé nada automáticamente."

    fun textFor(kind: ExternalHandoffKind): String = when (kind) {
        ExternalHandoffKind.WHATSAPP -> WHATSAPP_TEXT
        ExternalHandoffKind.MAPS -> MAPS_TEXT
        ExternalHandoffKind.PHONE -> PHONE_TEXT
        ExternalHandoffKind.GENERIC -> GENERIC_TEXT
    }

    fun classify(handoff: ExternalActionEvent.ExternalAppHandoff): ExternalHandoffKind {
        val byName = classifyByName(handoff.externalAppName)
        if (byName != ExternalHandoffKind.GENERIC) return byName
        return classifyByDelegate(handoff.delegate)
    }

    private fun classifyByName(externalAppName: String): ExternalHandoffKind {
        val normalized = externalAppName.trim().lowercase()
        return when {
            normalized.contains("whatsapp") -> ExternalHandoffKind.WHATSAPP
            normalized.contains("maps") || normalized.contains("mapa") -> ExternalHandoffKind.MAPS
            normalized.contains("telefono") || normalized.contains("teléfono") ||
                normalized.contains("dialer") || normalized.contains("marcador") ||
                normalized.contains("phone") -> ExternalHandoffKind.PHONE
            else -> ExternalHandoffKind.GENERIC
        }
    }

    private fun classifyByDelegate(delegate: ExternalActionEvent): ExternalHandoffKind = when (delegate) {
        is ExternalActionEvent.OpenWhatsApp,
        is ExternalActionEvent.OpenWhatsAppChat,
        is ExternalActionEvent.ComposeWhatsAppMessage -> ExternalHandoffKind.WHATSAPP

        is ExternalActionEvent.OpenMaps,
        is ExternalActionEvent.OpenCurrentLocation,
        is ExternalActionEvent.NavigateToDestination,
        is ExternalActionEvent.NavigateToCoordinates -> ExternalHandoffKind.MAPS

        is ExternalActionEvent.OpenPhone,
        is ExternalActionEvent.DialPhoneNumber -> ExternalHandoffKind.PHONE

        else -> ExternalHandoffKind.GENERIC
    }
}

/**
 * Recuerda que Ojo Claro lanzó un handoff externo y entrega el "kind" pendiente
 * una sola vez. La idea es que la UI, al volver al foreground, pida [consumeIfPending]
 * y solo si recibe algo, hable el callback corto.
 *
 * No persiste nada. Vive en memoria del proceso. Si el proceso muere mientras el
 * usuario está en la app externa, no decimos nada al volver — lo cual es preferible
 * a inventar contexto.
 */
class ExternalHandoffCallbackTracker {

    private var pending: ExternalHandoffKind? = null

    val hasPending: Boolean
        get() = pending != null

    fun markStarted(kind: ExternalHandoffKind) {
        pending = kind
    }

    fun consumeIfPending(): ExternalHandoffKind? {
        val current = pending
        pending = null
        return current
    }

    fun clear() {
        pending = null
    }
}
