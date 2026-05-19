package com.ojoclaro.android.agent.core

/**
 * Resultado de evaluar una intención del usuario contra Safety + Risk + Context.
 *
 * Sealed para forzar manejo explícito en el caller. Cada variante incluye
 * texto hablado listo para el usuario, así la UI no inventa frases.
 */
sealed class AgentActionDecision {

    /** La acción se puede ejecutar sin pedir confirmación. */
    data class Allowed(val action: AgentAction) : AgentActionDecision()

    /**
     * La acción se preparó pero necesita confirmación explícita del usuario.
     * El llamador debe guardar [action] como pendiente y reproducir
     * [spokenConfirmationPrompt] por voz.
     */
    data class NeedsConfirmation(
        val action: AgentAction,
        val spokenConfirmationPrompt: String
    ) : AgentActionDecision()

    /**
     * Faltan datos para completar la acción (ej. contacto, destino).
     */
    data class NeedsSlot(
        val toolId: AgentToolId,
        val slot: String,
        val spokenPrompt: String
    ) : AgentActionDecision()

    /**
     * La acción se rechaza por motivos de seguridad o privacidad.
     * No se va a ejecutar. [reason] es un código corto para logs.
     */
    data class Rejected(
        val spokenText: String,
        val reason: String
    ) : AgentActionDecision()
}
