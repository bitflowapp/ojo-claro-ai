package com.ojoclaro.android.domain

import com.ojoclaro.android.consent.PendingSensitiveAction
import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.external.ExternalActionEvent
import com.ojoclaro.android.external.PendingConfirmation
import com.ojoclaro.android.model.AppState

/**
 * Resultado de procesar una entrada de usuario por el AssistantOrchestrator.
 *
 * Este tipo es deliberadamente plano: incluye todo lo que la UI necesita
 * disparar en un único objeto. La UI no toma decisiones, solo aplica efectos.
 *
 * Hay dos tipos de "pending":
 *  - newPending / clearsPending: confirmaciones de comandos externos (WhatsApp compose).
 *  - newPendingConsent / clearsPendingConsent: confirmaciones de acciones sensibles
 *    (lectura de pantalla, etc.) gobernadas por ConsentManager.
 *
 * Son disjuntos en la práctica: un mismo Outcome no setea ambos.
 */
data class OrchestratorOutcome(
    val spokenText: String,
    val displayText: String = spokenText,
    val targetState: AppState,
    val externalEvent: ExternalActionEvent? = null,
    val newPending: PendingConfirmation? = null,
    val clearsPending: Boolean = false,
    val newPendingConsent: PendingSensitiveAction? = null,
    val clearsPendingConsent: Boolean = false,
    val isError: Boolean = false,
    val forceSpeak: Boolean = false,
    val safetyNotice: String? = null,
    val agentState: AgentState? = null,
    val decisionDebugLabel: String = ""
)
