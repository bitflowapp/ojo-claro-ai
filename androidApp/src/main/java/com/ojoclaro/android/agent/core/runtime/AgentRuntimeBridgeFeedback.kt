package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.core.AgentAction
import com.ojoclaro.android.agent.core.AgentToolId

/**
 * Copy estable para [AgentRuntimeBridge].
 *
 * Centraliza las frases que el bridge sugiere al caller para hablar por TTS.
 * Mantener la lista corta y testeable.
 *
 * Reglas de redacción:
 *  - Frases breves (≤ 120 caracteres). Un usuario ciego no quiere oír un
 *    párrafo antes de tomar acción.
 *  - Siempre incluir la palabra "confirmar" o "cancelar" para que el usuario
 *    sepa qué decir.
 *  - Nunca prometer que la acción ya se ejecutó. Solo describir el próximo
 *    paso.
 */
object AgentRuntimeBridgeFeedback {

    const val GENERIC_PENDING: String =
        "Esta acción puede ser sensible. Decime confirmar para continuar o cancelar para detenerla."

    const val NO_PENDING_CONFIRMATION: String =
        "No tengo ninguna acción esperando confirmación."

    const val NO_PENDING_CANCELLATION: String =
        "No hay nada que cancelar."

    const val CANCELLED: String =
        "Cancelado. No ejecuto nada."

    const val EXPIRED: String =
        "La acción venció. Pedímela de nuevo si querés."

    /**
     * Frase para describir que la acción quedó lista para ejecutarse tras
     * confirmación. NO afirma que ya se hizo — eso lo dice quien ejecute.
     */
    fun confirmed(action: AgentAction): String = when (action.toolId) {
        AgentToolId.WHATSAPP -> "Confirmado. Preparo el WhatsApp. No envío automáticamente."
        AgentToolId.MAPS -> "Confirmado. Abro Maps."
        AgentToolId.PHONE -> "Confirmado. Abro el marcador. La llamada la disparás vos."
        AgentToolId.SCREEN_READER -> "Confirmado. Leo la pantalla."
        AgentToolId.OCR -> "Confirmado. Leo el texto con la cámara."
        AgentToolId.MEMORY -> "Confirmado. Actualizo la memoria."
        AgentToolId.PREFERENCE -> "Confirmado. Ajusto la preferencia."
        AgentToolId.EMERGENCY -> "Confirmado. Activo modo emergencia."
        AgentToolId.REPEAT_LAST -> "Confirmado."
        AgentToolId.GENERIC_APP -> "Confirmado."
    }
}
