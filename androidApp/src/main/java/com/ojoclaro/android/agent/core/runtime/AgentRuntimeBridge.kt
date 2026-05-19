package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.IntentInterpreter
import com.ojoclaro.android.agent.LocalIntentParser
import com.ojoclaro.android.agent.core.AgentAction
import com.ojoclaro.android.agent.core.AgentActionDecision
import com.ojoclaro.android.agent.core.AgentActionEvaluator
import com.ojoclaro.android.agent.core.AgentContextSnapshot
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.AgentToolId
import com.ojoclaro.android.consent.ConfirmationManager
import com.ojoclaro.android.consent.ConfirmationOutcome
import com.ojoclaro.android.consent.PendingAgentAction

/**
 * Puente mínimo entre el flujo legacy y el Agent Core moderno.
 *
 * **Qué hace:**
 *  - Recibe un comando crudo + un [AgentContextSnapshot] ya armado por el caller.
 *  - Lo pasa por [IntentInterpreter] → [AgentActionEvaluator] → [ConfirmationManager].
 *  - Devuelve un [BridgeOutcome] cerrado que el caller puede mapear a TTS/UI.
 *  - Mantiene UN solo pending action en memoria volátil (la UI guarda más
 *    si necesita, pero el bridge se asume single-user-single-task).
 *
 * **Qué NO hace:**
 *  - No habla (no llama TTS).
 *  - No lanza Intents Android.
 *  - No persiste nada.
 *  - No interfiere con [com.ojoclaro.android.domain.AssistantOrchestrator]
 *    legacy: si el caller no lo invoca, el flujo viejo sigue intacto.
 *
 * **Feature flag:**
 *  Por default ([AgentCoreFeatureFlags.typedConfirmationEnabled] = false), el
 *  bridge devuelve [BridgeOutcome.Skipped] sin hacer trabajo. La UI puede
 *  llamarlo siempre, sin preocuparse — solo "responde" cuando el flag está on.
 *
 * **Hilos:**
 *  Diseñada para ser invocada desde la coroutine principal del ViewModel. El
 *  pending se guarda con @Volatile + bloqueo simple. El método [submit] es
 *  idempotente respecto a flag-off (no muta estado).
 *
 * **Wiring sugerido (paquete 3+):**
 *  ```
 *  val bridge = AgentRuntimeBridge(flags = { config.featureFlags })
 *  val context = AgentContext.build(...)
 *  when (val out = bridge.submit(userInput, context)) {
 *      is BridgeOutcome.Skipped -> dispatchLegacy(userInput)
 *      is BridgeOutcome.Pending -> speak(out.spokenPrompt); ui.showPending(out.uiState())
 *      is BridgeOutcome.Confirmed -> executeAction(out.action); speak(out.spokenText)
 *      is BridgeOutcome.Ready -> executeAction(out.action); speak(out.spokenText)
 *      is BridgeOutcome.Cancelled -> speak(out.spokenText)
 *      is BridgeOutcome.Rejected -> speak(out.spokenText); ui.showRejection(out.reason)
 *      is BridgeOutcome.NeedsSlot -> speak(out.spokenPrompt)
 *      is BridgeOutcome.NoPending -> speak(out.spokenText)
 *      is BridgeOutcome.Expired -> speak(out.spokenText)
 *  }
 *  ```
 */
class AgentRuntimeBridge(
    private val parser: IntentInterpreter = LocalIntentParser(),
    private val evaluator: AgentActionEvaluator = AgentActionEvaluator(),
    private val confirmationManager: ConfirmationManager = ConfirmationManager(),
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private val lock = Any()

    @Volatile
    private var pending: PendingAgentAction? = null

    /**
     * Punto de entrada único. El caller solo necesita el comando crudo del
     * usuario y el contexto del agente.
     *
     * Reglas de orden:
     *  1. Si el flag está off → Skipped (no muta nada).
     *  2. Si el input está vacío → Skipped (blank).
     *  3. Si el comando es CONFIRM y hay pending → consumir.
     *  4. Si el comando es CANCEL y hay pending → cancelar.
     *  5. Cualquier otro comando "nuevo" pisa el pending anterior:
     *     se descarta sin ejecutar y se procesa el nuevo intent.
     */
    fun submit(rawInput: String, context: AgentContextSnapshot): BridgeOutcome {
        val activeFlags = flags()
        if (!activeFlags.typedConfirmationEnabled) {
            return BridgeOutcome.Skipped(reason = "typed_confirmation_disabled")
        }

        val clean = rawInput.trim()
        if (clean.isBlank()) {
            return BridgeOutcome.Skipped(reason = "blank_input")
        }

        val parsed = parser.parse(clean)
        val now = clock()

        if (parsed.intent == AgentIntent.CONFIRM) {
            return handleConfirm(now)
        }
        if (parsed.intent == AgentIntent.CANCEL) {
            return handleCancel()
        }

        // Cualquier comando nuevo descarta el pending viejo. Es lo opuesto a
        // ejecutarlo: la prioridad es seguridad, no continuidad. La UI puede
        // notificar "abandoné lo anterior" si lo necesita.
        val abandoned = synchronized(lock) {
            val previous = pending
            if (previous != null) {
                pending = null
                true
            } else {
                false
            }
        }

        val decision = evaluator.evaluate(parsed, context, now)
        val outcome = route(decision)

        return if (abandoned) {
            BridgeOutcome.PreviousAbandoned(replacement = outcome)
        } else {
            outcome
        }
    }

    /**
     * Mira el pending sin tocarlo. Útil para que la UI consulte y arme estado
     * reactivo sin necesidad de un Flow nuevo.
     */
    fun currentPending(): PendingAgentAction? = pending

    /**
     * Estado plano útil para una capa Compose simple. No es la única forma de
     * consumir el bridge — es un atajo para el caso "necesito un BridgeUiState
     * sin armar un mapper".
     */
    fun uiState(): BridgeUiState {
        val snapshot = pending
        return if (snapshot == null) {
            BridgeUiState.Idle
        } else {
            BridgeUiState.AwaitingConfirmation(
                actionId = snapshot.id,
                toolId = snapshot.action.toolId,
                spokenPreview = snapshot.action.spokenPreview,
                confirmationPrompt = snapshot.action.confirmationPrompt
                    ?: AgentRuntimeBridgeFeedback.GENERIC_PENDING,
                createdAtMillis = snapshot.createdAtMillis
            )
        }
    }

    /**
     * Limpia el pending. Se ofrece para casos donde la UI quiere descartar
     * por timeout/cambio de pantalla. NO habla, NO ejecuta.
     */
    fun reset() {
        synchronized(lock) { pending = null }
    }

    private fun handleConfirm(nowMillis: Long): BridgeOutcome {
        val snapshot = synchronized(lock) {
            val current = pending
            pending = null
            current
        }
        return when (val outcome = confirmationManager.consume(snapshot, nowMillis)) {
            is ConfirmationOutcome.Confirmed -> BridgeOutcome.Confirmed(
                action = outcome.action,
                spokenText = AgentRuntimeBridgeFeedback.confirmed(outcome.action)
            )
            is ConfirmationOutcome.Expired -> BridgeOutcome.Expired(outcome.spokenText)
            is ConfirmationOutcome.Cancelled -> BridgeOutcome.Cancelled(outcome.spokenText)
            is ConfirmationOutcome.NoPending -> BridgeOutcome.NoPending(outcome.spokenText)
            is ConfirmationOutcome.Rejected -> BridgeOutcome.Rejected(
                spokenText = outcome.spokenText,
                reason = "confirmation_rejected"
            )
            is ConfirmationOutcome.Allowed ->
                // Defensivo: consume nunca debería devolver Allowed simple,
                // pero si lo hace, ejecutamos como Confirmed.
                BridgeOutcome.Confirmed(
                    action = outcome.action,
                    spokenText = outcome.spokenText
                )
            is ConfirmationOutcome.NeedsConfirmation ->
                // Defensivo: si consume devuelve NeedsConfirmation, restauramos
                // el pending para que el usuario pueda reintentar.
                synchronized(lock) {
                    pending = outcome.pending
                    BridgeOutcome.Pending(
                        pending = outcome.pending,
                        spokenPrompt = outcome.spokenText
                    )
                }
        }
    }

    private fun handleCancel(): BridgeOutcome {
        val snapshot = synchronized(lock) {
            val current = pending
            pending = null
            current
        }
        if (snapshot == null) {
            return BridgeOutcome.NoPending(AgentRuntimeBridgeFeedback.NO_PENDING_CANCELLATION)
        }
        val outcome = confirmationManager.cancel(snapshot)
        val spoken = (outcome as? ConfirmationOutcome.Cancelled)?.spokenText
            ?: AgentRuntimeBridgeFeedback.CANCELLED
        return BridgeOutcome.Cancelled(spoken)
    }

    private fun route(decision: AgentActionDecision): BridgeOutcome = when (decision) {
        is AgentActionDecision.Allowed -> BridgeOutcome.Ready(
            action = decision.action,
            spokenText = decision.action.spokenPreview
        )
        is AgentActionDecision.NeedsConfirmation -> registerPending(decision)
        is AgentActionDecision.NeedsSlot -> BridgeOutcome.NeedsSlot(
            toolId = decision.toolId,
            slot = decision.slot,
            spokenPrompt = decision.spokenPrompt
        )
        is AgentActionDecision.Rejected -> BridgeOutcome.Rejected(
            spokenText = decision.spokenText,
            reason = decision.reason
        )
    }

    private fun registerPending(decision: AgentActionDecision.NeedsConfirmation): BridgeOutcome {
        val outcome = confirmationManager.requireConfirmation(decision.action, clock())
        return when (outcome) {
            is ConfirmationOutcome.NeedsConfirmation -> {
                synchronized(lock) { pending = outcome.pending }
                BridgeOutcome.Pending(
                    pending = outcome.pending,
                    spokenPrompt = outcome.spokenText.ifBlank {
                        AgentRuntimeBridgeFeedback.GENERIC_PENDING
                    }
                )
            }
            is ConfirmationOutcome.Allowed -> BridgeOutcome.Ready(
                action = outcome.action,
                spokenText = outcome.spokenText.ifBlank { outcome.action.spokenPreview }
            )
            is ConfirmationOutcome.Rejected -> BridgeOutcome.Rejected(
                spokenText = outcome.spokenText,
                reason = "consent_rejected"
            )
            is ConfirmationOutcome.NoPending,
            is ConfirmationOutcome.Cancelled,
            is ConfirmationOutcome.Confirmed,
            is ConfirmationOutcome.Expired ->
                // Casos teóricos imposibles aquí (estamos REGISTRANDO un pending,
                // no consumiéndolo). Cubrimos por exhaustividad y devolvemos
                // un rechazo defensivo.
                BridgeOutcome.Rejected(
                    spokenText = AgentRuntimeBridgeFeedback.GENERIC_PENDING,
                    reason = "unexpected_register_outcome"
                )
        }
    }
}

/**
 * Resultado público del bridge. Sealed para forzar manejo exhaustivo en la UI.
 *
 * Texto y razones diseñados para ser pasados directamente a TTS sin
 * reformateo. La UI puede ignorar los que no le interesen.
 */
sealed class BridgeOutcome {

    /** Bridge desactivado o input vacío. La app debe seguir su flujo legacy. */
    data class Skipped(val reason: String) : BridgeOutcome()

    /** Acción lista para ejecutarse sin necesidad de confirmación. */
    data class Ready(val action: AgentAction, val spokenText: String) : BridgeOutcome()

    /** Acción pendiente de confirmación. La UI guarda el pending y habla el prompt. */
    data class Pending(val pending: PendingAgentAction, val spokenPrompt: String) : BridgeOutcome()

    /** El usuario dijo "confirmar". La acción quedó autorizada para ejecutarse. */
    data class Confirmed(val action: AgentAction, val spokenText: String) : BridgeOutcome()

    /** Cancelado por el usuario. Nada se ejecutó. */
    data class Cancelled(val spokenText: String) : BridgeOutcome()

    /** El pending expiró. La UI debe pedir el comando original de nuevo. */
    data class Expired(val spokenText: String) : BridgeOutcome()

    /** Acción rechazada por seguridad/safety. No se va a ejecutar bajo ningún caso. */
    data class Rejected(val spokenText: String, val reason: String) : BridgeOutcome()

    /** Falta un slot. La UI debe preguntar. */
    data class NeedsSlot(
        val toolId: AgentToolId,
        val slot: String,
        val spokenPrompt: String
    ) : BridgeOutcome()

    /** El usuario dijo confirmar/cancelar pero no había pending. */
    data class NoPending(val spokenText: String) : BridgeOutcome()

    /**
     * Caso especial: llegó un nuevo comando mientras había un pending. El
     * pending viejo se descartó (no ejecutado). [replacement] es el outcome
     * del nuevo comando. La UI puede informar "cambié de tema" si lo desea.
     */
    data class PreviousAbandoned(val replacement: BridgeOutcome) : BridgeOutcome() {
        init {
            require(replacement !is PreviousAbandoned) {
                "PreviousAbandoned.replacement must not be another PreviousAbandoned"
            }
        }
    }
}

/**
 * Vista mínima del estado del bridge para una UI simple (Compose o no).
 * Pensada para ser inmutable y trivial de mapear a un StateFlow.
 */
sealed class BridgeUiState {
    data object Idle : BridgeUiState()

    data class AwaitingConfirmation(
        val actionId: String,
        val toolId: AgentToolId,
        val spokenPreview: String,
        val confirmationPrompt: String,
        val createdAtMillis: Long
    ) : BridgeUiState()
}
