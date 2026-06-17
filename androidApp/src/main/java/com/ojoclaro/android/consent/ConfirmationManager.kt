package com.ojoclaro.android.consent

import com.ojoclaro.android.agent.core.AgentAction
import com.ojoclaro.android.agent.core.AgentRiskLevel
import com.ojoclaro.android.agent.core.AgentToolId
import java.util.UUID

/**
 * Capa fina sobre [ConsentManager] orientada a las nuevas [AgentAction].
 *
 * Por qué existe:
 *  - [ConsentManager] expone una API genérica con `payload: Map<String,String>`
 *    y `SensitiveActionType`. Funciona, pero la UI nueva del agent-core
 *    razona en términos de [AgentAction], no de mapas anónimos.
 *  - Acá ofrecemos un par de funciones cortas que toman una [AgentAction]
 *    y devuelven un [ConfirmationOutcome] tipado. Sin duplicar el motor.
 *  - NO persiste, NO habla, NO ejecuta. Solo clasifica y mantiene un pending
 *    en memoria del caller (la UI guarda el pending igual que hoy).
 *
 * El [ConsentManager] subyacente se conserva intacto: los flows previos
 * (compose WhatsApp, save memory, etc.) siguen pasando por él.
 */
class ConfirmationManager(
    private val consentManager: ConsentManager = ConsentManager(),
    private val idFactory: () -> String = { UUID.randomUUID().toString() }
) {

    /**
     * Decide si una acción se ejecuta sola o necesita confirmación.
     *
     * Reglas:
     *  - Si la action declara `requiresConfirmation = false` y su risk es
     *    NONE o LOW → se autoriza directamente.
     *  - Cualquier otro caso → se delega a [ConsentManager.requestAction]
     *    con un [SensitiveActionType] equivalente al tool, y se envuelve la
     *    decisión en un [PendingAgentAction].
     */
    fun requireConfirmation(
        action: AgentAction,
        nowMillis: Long
    ): ConfirmationOutcome {
        if (!action.requiresConfirmation && action.risk.ordinal <= AgentRiskLevel.LOW.ordinal) {
            return ConfirmationOutcome.Allowed(action = action, spokenText = action.spokenPreview)
        }

        val sensitiveType = sensitiveTypeFor(action.toolId)
        val spoken = action.confirmationPrompt ?: action.spokenPreview
        val decision = consentManager.requestAction(
            type = sensitiveType,
            spokenExplanation = spoken,
            payload = action.slots,
            nowMillis = nowMillis
        )

        return when (decision) {
            is ConsentDecision.AllowedImmediately ->
                ConfirmationOutcome.Allowed(action = action, spokenText = decision.spokenText)

            is ConsentDecision.NeedsConfirmation -> ConfirmationOutcome.NeedsConfirmation(
                pending = PendingAgentAction(
                    id = idFactory(),
                    action = action,
                    underlying = decision.pending,
                    createdAtMillis = nowMillis
                ),
                spokenText = decision.spokenText
            )

            is ConsentDecision.Rejected ->
                ConfirmationOutcome.Rejected(spokenText = decision.spokenText)

            is ConsentDecision.Confirmed,
            is ConsentDecision.Cancelled,
            is ConsentDecision.NoPending,
            is ConsentDecision.Expired ->
                // requestAction nunca devuelve estos, pero la sealed class
                // exige cobertura exhaustiva. Mapeamos como rechazo defensivo.
                ConfirmationOutcome.Rejected(
                    spokenText = "No pude preparar esa acción. Probá de nuevo."
                )
        }
    }

    /**
     * Llamado cuando el usuario dijo "confirmá" sobre una acción pendiente.
     * Si la pendiente está vigente, devuelve la [AgentAction] lista para
     * ejecutarse. Si venció o no existe, devuelve un outcome informativo.
     */
    fun consume(
        pending: PendingAgentAction?,
        nowMillis: Long
    ): ConfirmationOutcome {
        if (pending == null) {
            return ConfirmationOutcome.NoPending(
                spokenText = ConsentPhrases.NO_PENDING_CONFIRMATION
            )
        }
        return when (val decision = consentManager.confirmSimple(pending.underlying, nowMillis)) {
            is ConsentDecision.Confirmed ->
                ConfirmationOutcome.Confirmed(action = pending.action)
            is ConsentDecision.Expired ->
                ConfirmationOutcome.Expired(spokenText = decision.spokenText)
            is ConsentDecision.Rejected ->
                ConfirmationOutcome.Rejected(spokenText = decision.spokenText)
            is ConsentDecision.NoPending ->
                ConfirmationOutcome.NoPending(spokenText = decision.spokenText)
            is ConsentDecision.Cancelled ->
                ConfirmationOutcome.Cancelled(spokenText = decision.spokenText)
            is ConsentDecision.AllowedImmediately ->
                ConfirmationOutcome.Allowed(
                    action = pending.action,
                    spokenText = decision.spokenText
                )
            is ConsentDecision.NeedsConfirmation ->
                // confirmSimple no debería pedir más confirmación; defensivo.
                ConfirmationOutcome.NeedsConfirmation(
                    pending = pending,
                    spokenText = decision.spokenText
                )
        }
    }

    /**
     * Cancela el pending. Idempotente.
     */
    fun cancel(pending: PendingAgentAction?): ConfirmationOutcome {
        if (pending == null) {
            return ConfirmationOutcome.NoPending(
                spokenText = ConsentPhrases.NO_PENDING_CANCELLATION
            )
        }
        return when (val decision = consentManager.cancel(pending.underlying)) {
            is ConsentDecision.Cancelled ->
                ConfirmationOutcome.Cancelled(spokenText = decision.spokenText)
            is ConsentDecision.NoPending ->
                ConfirmationOutcome.NoPending(spokenText = decision.spokenText)
            else ->
                // ConsentManager.cancel solo devuelve los dos de arriba hoy,
                // pero por exhaustividad mapeamos como cancelled.
                ConfirmationOutcome.Cancelled(spokenText = ConsentPhrases.ACTION_CANCELLED)
        }
    }

    private fun sensitiveTypeFor(toolId: AgentToolId): SensitiveActionType = when (toolId) {
        AgentToolId.WHATSAPP -> SensitiveActionType.COMPOSE_MESSAGE
        AgentToolId.PHONE -> SensitiveActionType.OPEN_EXTERNAL_APP
        AgentToolId.MAPS -> SensitiveActionType.OPEN_EXTERNAL_APP
        AgentToolId.SCREEN_READER -> SensitiveActionType.READ_VISIBLE_MESSAGE
        AgentToolId.OCR -> SensitiveActionType.READ_VISIBLE_MESSAGE
        AgentToolId.MEMORY -> SensitiveActionType.SAVE_MEMORY
        AgentToolId.PREFERENCE -> SensitiveActionType.SAVE_MEMORY
        AgentToolId.EMERGENCY -> SensitiveActionType.OPEN_EXTERNAL_APP
        AgentToolId.REPEAT_LAST -> SensitiveActionType.UNKNOWN_SENSITIVE
        AgentToolId.GENERIC_APP -> SensitiveActionType.UNKNOWN_SENSITIVE
    }
}

/**
 * Pendiente tipado de una [AgentAction]. La UI lo guarda y lo pasa de vuelta
 * al confirmar/cancelar.
 */
data class PendingAgentAction(
    val id: String,
    val action: AgentAction,
    val underlying: PendingSensitiveAction,
    val createdAtMillis: Long
)

sealed class ConfirmationOutcome {
    data class Allowed(val action: AgentAction, val spokenText: String) : ConfirmationOutcome()
    data class NeedsConfirmation(
        val pending: PendingAgentAction,
        val spokenText: String
    ) : ConfirmationOutcome()
    data class Confirmed(val action: AgentAction) : ConfirmationOutcome()
    data class Cancelled(val spokenText: String) : ConfirmationOutcome()
    data class Expired(val spokenText: String) : ConfirmationOutcome()
    data class Rejected(val spokenText: String) : ConfirmationOutcome()
    data class NoPending(val spokenText: String) : ConfirmationOutcome()
}
