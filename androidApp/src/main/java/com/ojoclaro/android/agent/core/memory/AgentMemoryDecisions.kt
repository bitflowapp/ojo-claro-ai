package com.ojoclaro.android.agent.core.memory

/**
 * Decisión de escritura sobre memoria personal del agente.
 *
 * El planner / UI consultan AgentPersonalMemoryPolicy con un MemoryWriteRequest
 * y reciben un MemoryWriteDecision. Solo Allowed o NeedsConfirmation deberían
 * provocar persistencia.
 */
sealed class AgentMemoryWriteDecision {

    /** Se puede guardar sin pregunta extra (el usuario lo dijo explícitamente). */
    data class Allowed(
        val spokenAck: String
    ) : AgentMemoryWriteDecision()

    /**
     * Se infirió desde patrones. NO guardar todavía. UI debe preguntar al
     * usuario; si confirma, se hace una nueva Allowed con USER_CONFIRMED.
     */
    data class NeedsConfirmation(
        val spokenAck: String,
        val confirmationPrompt: String
    ) : AgentMemoryWriteDecision()

    /** Bloqueado por contenido sensible o porque el usuario opted-out. */
    data class Rejected(
        val reason: String,
        val spokenText: String
    ) : AgentMemoryWriteDecision()
}

/**
 * Decisión de lectura sobre memoria personal del agente.
 */
sealed class AgentMemoryReadDecision {
    /** Permite leer/responder con el valor. */
    data class Allowed(val value: String) : AgentMemoryReadDecision()

    /** Está, pero requiere confirmación antes de leer. */
    data class NeedsConfirmation(val spokenPrompt: String) : AgentMemoryReadDecision()

    /** No existe / es sensible / fue olvidado. */
    data class Missing(val spokenText: String) : AgentMemoryReadDecision()
}
