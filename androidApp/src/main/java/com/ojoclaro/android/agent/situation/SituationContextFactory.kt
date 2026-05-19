package com.ojoclaro.android.agent.situation

import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.AgentRiskLevel

/**
 * Construye un [SituationContext] a partir de los datos disponibles en
 * HomeViewModel más la memoria runtime del Situation Brain.
 *
 * Fase 4: el contexto ya no nace amnésico. activeGoal, pendingAction,
 * companionModeActive, recentTurns, situationState y mutedThroughRequestId
 * pueden venir de [SituationRuntimeSnapshot].
 *
 * Sigue siendo conservador: screenContext = null, isPrivacyHotZone = false.
 */
class SituationContextFactory {

    fun fromVoiceCommand(
        rawCommand: String,
        currentStateName: String?,
        currentAppPackage: String?,
        activeRequestId: Long,
        mutedThroughRequestId: Long,
        lastAssistantMessage: String,
        runtimeSnapshot: SituationRuntimeSnapshot = SituationRuntimeSnapshot.empty(),
        timestamp: Long = System.currentTimeMillis()
    ): SituationContext {
        val resolvedLastMessage = if (lastAssistantMessage.isNotBlank()) {
            lastAssistantMessage
        } else {
            runtimeSnapshot.lastAssistantMessage
        }
        return SituationContext(
            rawCommand = rawCommand,
            normalizedCommand = rawCommand.trim().lowercase(),
            source = InputSource.VOICE,
            // No hay confidence real disponible en esta fase: asumimos 1f.
            confidence = 1f,
            timestamp = timestamp,
            situationIntent = SituationIntent.UNKNOWN,
            activeGoal = runtimeSnapshot.activeGoal,
            pendingAction = runtimeSnapshot.pendingAction,
            currentAppPackage = currentAppPackage,
            environmentHint = environmentHintFor(currentAppPackage),
            screenContext = null,
            riskLevel = AgentRiskLevel.NONE,
            needsConfirmation = runtimeSnapshot.pendingAction != null,
            isPrivacyHotZone = false,
            lastAssistantMessage = resolvedLastMessage,
            recentTurns = runtimeSnapshot.recentTurns,
            situationState = resolveState(currentStateName, runtimeSnapshot),
            activeRequestId = activeRequestId,
            // El corte de respuestas viejas nunca debe retroceder.
            mutedThroughRequestId = maxOf(
                mutedThroughRequestId,
                runtimeSnapshot.mutedThroughRequestId
            ),
            cancellationState = CancellationState.NONE,
            userMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
            companionModeActive = runtimeSnapshot.companionModeActive
        )
    }

    /**
     * Resuelve el estado inicial del contexto.
     *
     * Si la memoria runtime ya tiene contenido (no está vacía), su estado manda:
     * representa el progreso real del Brain entre comandos. Si la memoria está
     * vacía, se intenta mapear [currentStateName]; si no se puede mapear, IDLE.
     */
    private fun resolveState(
        currentStateName: String?,
        runtimeSnapshot: SituationRuntimeSnapshot
    ): SituationState {
        val mappedFromName = currentStateName
            ?.takeIf { it.isNotBlank() }
            ?.let { runCatching { SituationState.valueOf(it) }.getOrNull() }
        return when {
            !runtimeSnapshot.isEmpty() -> runtimeSnapshot.situationState
            mappedFromName != null -> mappedFromName
            else -> SituationState.IDLE
        }
    }
}
