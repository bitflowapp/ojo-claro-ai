package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentScreenContext

/** De dónde vino la entrada del turno actual. */
enum class InputSource {
    VOICE,
    DEBUG_INJECT,
    EXTERNAL_RETURN,
    SYSTEM
}

/** Rol de un turno en la memoria operativa corta. */
enum class TurnRole {
    USER,
    ASSISTANT
}

/** Estado de cancelación de la tarea actual. */
enum class CancellationState {
    NONE,
    SOFT_CANCEL,
    HARD_CANCEL
}

/**
 * Acción esperando confirmación humana explícita.
 *
 * @param intentName nombre del AgentIntent asociado (string para no acoplar el
 *   modelo puro a la enum de intents de runtime).
 * @param originalCommand comando crudo que originó la acción, para poder
 *   ejecutarla tras la confirmación sin depender del "sí" del turno siguiente.
 * @param target objetivo simple extraído del comando (app, contacto). Puede ir
 *   en blanco si no se pudo extraer.
 * @param payload metadata segura adicional. Nunca texto de pantalla, OCR ni
 *   dumps de accesibilidad. No persiste a disco.
 */
data class PendingAction(
    val label: String,
    val intentName: String,
    val riskLevel: AgentRiskLevel,
    val confirmationPrompt: String,
    val expiresAt: Long,
    val originalCommand: String = "",
    val target: String = "",
    val payload: Map<String, String> = emptyMap()
) {
    init {
        require(label.isNotBlank()) { "label must not be blank" }
        require(confirmationPrompt.isNotBlank()) { "confirmationPrompt must not be blank" }
        require(originalCommand.length <= MAX_ORIGINAL_COMMAND_CHARS) {
            "originalCommand must not exceed $MAX_ORIGINAL_COMMAND_CHARS characters"
        }
        require(target.length <= MAX_TARGET_CHARS) {
            "target must not exceed $MAX_TARGET_CHARS characters"
        }
        require(payload.size <= MAX_PAYLOAD_ENTRIES) {
            "payload must not exceed $MAX_PAYLOAD_ENTRIES entries"
        }
        require(payload.keys.none { it.isBlank() }) {
            "payload keys must not be blank"
        }
        require(payload.values.none { it.length > MAX_PAYLOAD_VALUE_CHARS }) {
            "payload values must not exceed $MAX_PAYLOAD_VALUE_CHARS characters"
        }
    }

    companion object {
        const val MAX_ORIGINAL_COMMAND_CHARS = 240
        const val MAX_TARGET_CHARS = 120
        const val MAX_PAYLOAD_ENTRIES = 10
        const val MAX_PAYLOAD_VALUE_CHARS = 240
    }
}

/**
 * Devuelve el comando que la capa de aplicación debe usar para ejecutar esta
 * acción confirmada:
 *  1. [PendingAction.originalCommand] si no está en blanco.
 *  2. Si no, reconstruye un comando mínimo a partir de intentName + target.
 *  3. Si no puede reconstruir, devuelve "".
 *
 * Resuelve la limitación de la Fase 6: el "sí" del turno de confirmación ya no
 * se usa para ejecutar — se usa el comando original que la acción recordó.
 */
fun PendingAction.commandForExecution(): String {
    if (originalCommand.isNotBlank()) return originalCommand
    if (target.isBlank()) return ""
    return when (situationIntentFromPendingAction(this)) {
        SituationIntent.OPEN_APP -> "abrí $target"
        SituationIntent.CALL_CONTACT -> "llamá a $target"
        else -> ""
    }
}

/**
 * Comando legacy para preparar un mensaje luego de confirmar WRITE_MESSAGE.
 *
 * No envía: solo reconstruye la frase que el router viejo ya sabe transformar
 * en flujo de composición/confirmación seguro.
 */
fun PendingAction.messageCommandForExecution(): String {
    if (originalCommand.isNotBlank()) return originalCommand
    val contact = payload["contact"]?.trim().orEmpty()
    val message = payload["message"]?.trim().orEmpty()
    if (contact.isBlank() || message.isBlank()) return ""
    return "avisale a $contact que $message"
}

/**
 * Resumen de un turno de conversación. Texto truncado, sin PII cruda larga.
 */
data class TurnSummary(
    val role: TurnRole,
    val shortText: String,
    val intent: SituationIntent,
    val timestamp: Long
) {
    init {
        require(shortText.length <= MAX_SHORT_TEXT_CHARS) {
            "shortText must not exceed $MAX_SHORT_TEXT_CHARS characters"
        }
    }

    companion object {
        const val MAX_SHORT_TEXT_CHARS = 240
    }
}

/**
 * Contexto de situación: el estado vivo y único del Situation Brain.
 *
 * Inmutable. Cada turno produce una copia nueva. Vive durante una tarea (no un
 * comando) y NO persiste a disco. Reúne en un solo objeto lo que hoy está
 * disperso entre AgentSessionMemory, RobotShortTermContext y tres state machines.
 *
 * Fase 1: solo el modelo. No está cableado al flujo real todavía.
 */
data class SituationContext(
    val rawCommand: String,
    val normalizedCommand: String,
    val source: InputSource,
    val confidence: Float,
    val timestamp: Long,
    val situationIntent: SituationIntent,
    val activeGoal: ActiveGoal?,
    val pendingAction: PendingAction?,
    val currentAppPackage: String?,
    val environmentHint: EnvironmentHint,
    val screenContext: AgentScreenContext?,
    val riskLevel: AgentRiskLevel,
    val needsConfirmation: Boolean,
    val isPrivacyHotZone: Boolean,
    val lastAssistantMessage: String,
    val recentTurns: List<TurnSummary>,
    val situationState: SituationState,
    val activeRequestId: Long,
    val mutedThroughRequestId: Long,
    val cancellationState: CancellationState,
    val userMode: AgentExecutionMode,
    val companionModeActive: Boolean
) {
    init {
        require(confidence in 0f..1f) { "confidence must be in [0,1]" }
        require(timestamp >= 0L) { "timestamp must be >= 0" }
        require(activeRequestId >= 0L) { "activeRequestId must be >= 0" }
        require(mutedThroughRequestId >= 0L) { "mutedThroughRequestId must be >= 0" }
        require(recentTurns.size <= MAX_RECENT_TURNS) {
            "recentTurns must not exceed $MAX_RECENT_TURNS elements"
        }
    }

    /** Devuelve una copia con un nuevo [situationState]. */
    fun withState(newState: SituationState): SituationContext =
        copy(situationState = newState)

    /** Devuelve una copia con un [activeGoal] nuevo (o null para limpiarlo). */
    fun withGoal(goal: ActiveGoal?): SituationContext =
        copy(activeGoal = goal)

    /** Devuelve una copia con una [pendingAction] nueva (o null para limpiarla). */
    fun withPendingAction(action: PendingAction?): SituationContext =
        copy(pendingAction = action)

    /**
     * Agrega un turno a la memoria operativa corta, manteniendo como máximo
     * [MAX_RECENT_TURNS], descartando los más viejos.
     */
    fun withRecentTurn(turn: TurnSummary): SituationContext =
        copy(recentTurns = (recentTurns + turn).takeLast(MAX_RECENT_TURNS))

    /**
     * Limpia el contexto tras una cancelación dura del usuario ("callate",
     * "cancelá"). Borra objetivo y acción pendiente, sube el corte de respuestas
     * viejas y deja el estado en CANCELLED.
     */
    fun clearedForCancellation(now: Long): SituationContext =
        copy(
            activeGoal = null,
            pendingAction = null,
            cancellationState = CancellationState.HARD_CANCEL,
            situationState = SituationState.CANCELLED,
            mutedThroughRequestId = activeRequestId,
            needsConfirmation = false,
            companionModeActive = false,
            timestamp = now
        )

    companion object {
        const val MAX_RECENT_TURNS = 5
    }
}

/**
 * Agrega a la memoria operativa corta un [TurnSummary] del rol USER construido
 * desde el comando actual. El texto se trunca a [TurnSummary.MAX_SHORT_TEXT_CHARS]
 * para no exceder el límite ni guardar PII cruda larga. Mantiene como máximo
 * [SituationContext.MAX_RECENT_TURNS] turnos.
 *
 * Si [rawCommand] está en blanco, devuelve el contexto sin cambios: no tiene
 * sentido registrar un turno vacío.
 */
fun SituationContext.withUserTurnFromCurrentCommand(): SituationContext {
    if (rawCommand.isBlank()) return this
    return withRecentTurn(
        TurnSummary(
            role = TurnRole.USER,
            shortText = rawCommand.take(TurnSummary.MAX_SHORT_TEXT_CHARS),
            intent = situationIntent,
            timestamp = timestamp
        )
    )
}
