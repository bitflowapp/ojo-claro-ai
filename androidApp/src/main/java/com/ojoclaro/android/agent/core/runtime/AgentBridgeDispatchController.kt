package com.ojoclaro.android.agent.core.runtime

import com.ojoclaro.android.agent.AgentState
import com.ojoclaro.android.agent.core.AgentContext
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.AgentExecutionMode
import com.ojoclaro.android.agent.core.screen.ScreenContextRepository

/**
 * Controlador puro que decide si un comando del usuario lo maneja el
 * Agent Core (vía [AgentRuntimeBridge]) o si se delega al pipeline legacy.
 *
 * **Diseño:**
 *  - Es una capa fina entre `HomeViewModel.submitVoiceText` y el bridge.
 *  - Encapsula:
 *    1. el chequeo de feature flag (typedConfirmationEnabled),
 *    2. la construcción del [AgentContextSnapshot] tomando el snapshot
 *       estructurado del [ScreenContextRepository] (puede ser null),
 *    3. la traducción del [BridgeOutcome] crudo a un
 *       [BridgeDispatchOutcome] orientado a UI/voz.
 *  - 100% testeable con fakes. Sin Android, sin coroutines, sin TTS.
 *
 * **Reglas duras:**
 *  - Si `typedConfirmationEnabled == false` → [BridgeDispatchOutcome.FallbackToLegacy].
 *    El caller debe seguir su flujo normal.
 *  - Si el bridge devuelve [BridgeOutcome.Skipped] → también FallbackToLegacy.
 *    Esto significa "el bridge no puede manejar esto" (input vacío, flag
 *    interno off, etc.) — el legacy es la red de seguridad.
 *  - Para cualquier otro outcome → [BridgeDispatchOutcome.Handled] con
 *    `speakText` y metadata de pending. El caller **NO** debe ejecutar el
 *    legacy en este caso, para evitar doble ejecución.
 *
 * **NO ejecuta acciones reales:** ni envíos, ni llamadas, ni clicks. El
 * `Confirmed` solo significa "el usuario autorizó la acción"; la ejecución
 * real queda para paquetes posteriores.
 */
class AgentBridgeDispatchController(
    private val bridge: AgentRuntimeBridge,
    private val screenRepository: ScreenContextRepository,
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED },
    private val agentExecutionMode: AgentExecutionMode = AgentExecutionMode.ACCESSIBILITY_VOICE,
    private val agentStateProvider: () -> AgentState = { AgentState.IDLE },
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Punto de entrada único. El caller (típicamente HomeViewModel) llama esto
     * con el texto crudo del usuario y decide qué hacer con el resultado.
     */
    fun dispatch(rawInput: String): BridgeDispatchOutcome {
        if (!flags().typedConfirmationEnabled) {
            return BridgeDispatchOutcome.FallbackToLegacy(reason = "typed_confirmation_disabled")
        }
        val now = clock()
        // Si el repository no tiene snapshot (flag de pantalla off o aún sin
        // captura), igual armamos un context — el bridge funciona bien con
        // screen=null, solo perdemos clasificación de pantalla.
        val structured = runCatching { screenRepository.current() }.getOrNull()
        val context = AgentContext.buildFromStructured(
            mode = agentExecutionMode,
            agentState = agentStateProvider(),
            nowMillis = now,
            structured = structured,
            commandRawText = rawInput
        )
        val outcome = bridge.submit(rawInput, context)
        return interpret(outcome)
    }

    /**
     * Snapshot del estado UI sugerido por el bridge. Si no hay pending, es
     * [BridgeUiState.Idle]. Útil para que el VM exponga estado reactivo sin
     * crear su propio flow.
     */
    fun currentUiState(): BridgeUiState = bridge.uiState()

    /** Limpia el pending del bridge sin hablar ni ejecutar. */
    fun reset() = bridge.reset()

    private fun interpret(outcome: BridgeOutcome): BridgeDispatchOutcome {
        val unwrapped = unwrap(outcome)
        return when (unwrapped) {
            is BridgeOutcome.Skipped ->
                BridgeDispatchOutcome.FallbackToLegacy(reason = unwrapped.reason)

            is BridgeOutcome.Pending -> BridgeDispatchOutcome.Handled(
                speakText = BridgeDispatchSpeech.PENDING_CONFIRMATION,
                pendingPrompt = unwrapped.spokenPrompt.ifBlank {
                    AgentRuntimeBridgeFeedback.GENERIC_PENDING
                },
                hasPending = true,
                kind = BridgeDispatchKind.PENDING
            )

            is BridgeOutcome.Confirmed -> BridgeDispatchOutcome.Handled(
                speakText = BridgeDispatchSpeech.CONFIRMED_AUTHORIZED,
                pendingPrompt = null,
                hasPending = false,
                kind = BridgeDispatchKind.CONFIRMED
            )

            is BridgeOutcome.Cancelled -> BridgeDispatchOutcome.Handled(
                speakText = BridgeDispatchSpeech.CANCELLED,
                pendingPrompt = null,
                hasPending = false,
                kind = BridgeDispatchKind.CANCELLED
            )

            is BridgeOutcome.Rejected -> BridgeDispatchOutcome.Handled(
                speakText = unwrapped.spokenText,
                pendingPrompt = null,
                hasPending = false,
                kind = BridgeDispatchKind.REJECTED
            )

            is BridgeOutcome.Ready ->
                // Acción autorizada sin necesidad de confirmación. Por ahora el
                // bridge NO ejecuta nada — el caller solo habla la preview.
                // Cuando exista un executor real (paquetes 4C+), acá se invoca.
                BridgeDispatchOutcome.Handled(
                    speakText = unwrapped.spokenText.ifBlank { unwrapped.action.spokenPreview },
                    pendingPrompt = null,
                    hasPending = false,
                    kind = BridgeDispatchKind.READY
                )

            is BridgeOutcome.NeedsSlot -> BridgeDispatchOutcome.Handled(
                speakText = unwrapped.spokenPrompt,
                pendingPrompt = unwrapped.spokenPrompt,
                hasPending = false,
                kind = BridgeDispatchKind.NEEDS_SLOT
            )

            is BridgeOutcome.NoPending -> BridgeDispatchOutcome.Handled(
                speakText = unwrapped.spokenText,
                pendingPrompt = null,
                hasPending = false,
                kind = BridgeDispatchKind.NO_PENDING
            )

            is BridgeOutcome.Expired -> BridgeDispatchOutcome.Handled(
                speakText = unwrapped.spokenText.ifBlank { AgentRuntimeBridgeFeedback.EXPIRED },
                pendingPrompt = null,
                hasPending = false,
                kind = BridgeDispatchKind.EXPIRED
            )

            is BridgeOutcome.PreviousAbandoned ->
                // Defensivo: ya lo desenvolvió `unwrap`. Si llegara acá, lo
                // tratamos como rechazo neutro.
                BridgeDispatchOutcome.Handled(
                    speakText = AgentRuntimeBridgeFeedback.GENERIC_PENDING,
                    pendingPrompt = null,
                    hasPending = false,
                    kind = BridgeDispatchKind.REJECTED
                )
        }
    }

    private fun unwrap(outcome: BridgeOutcome): BridgeOutcome =
        if (outcome is BridgeOutcome.PreviousAbandoned) outcome.replacement else outcome
}

/**
 * Resultado del dispatch que el HomeViewModel consume.
 *
 * El VM solo necesita dos decisiones:
 *  - ¿Sigo con el flujo legacy? → [FallbackToLegacy].
 *  - ¿Ya está manejado? → [Handled], y uso `speakText` para TTS y
 *    `hasPending` + `pendingPrompt` para el estado UI.
 */
sealed class BridgeDispatchOutcome {

    /** El caller debe ejecutar el flujo legacy normal. [reason] para logs. */
    data class FallbackToLegacy(val reason: String) : BridgeDispatchOutcome()

    /**
     * El bridge ya tomó la decisión. El caller debe:
     *  - hablar [speakText],
     *  - actualizar UI con [hasPending] y [pendingPrompt],
     *  - NO invocar el legacy.
     */
    data class Handled(
        val speakText: String,
        val pendingPrompt: String?,
        val hasPending: Boolean,
        val kind: BridgeDispatchKind
    ) : BridgeDispatchOutcome()
}

/** Tipo de resultado del bridge, para que el caller pueda discriminar sin abrir el outcome. */
enum class BridgeDispatchKind {
    PENDING,
    CONFIRMED,
    CANCELLED,
    REJECTED,
    READY,
    NEEDS_SLOT,
    NO_PENDING,
    EXPIRED
}

object BridgeDispatchSpeech {
    const val PENDING_CONFIRMATION: String =
        "Esta acción requiere confirmación. Decime confirmar o cancelar."

    const val CONFIRMED_AUTHORIZED: String =
        "Confirmado. La acción quedó autorizada."

    const val CANCELLED: String =
        "Cancelado."
}
