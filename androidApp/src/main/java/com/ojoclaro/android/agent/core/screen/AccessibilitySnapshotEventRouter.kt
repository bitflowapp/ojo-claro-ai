package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.performance.RobotLoopLogResult
import com.ojoclaro.android.performance.RobotLoopLogStage
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import com.ojoclaro.android.performance.RobotLoopSafeLogEvent

/**
 * Router puro que une [AccessibilityEventClassifier], [ScreenContextCollectionPolicy]
 * y [ScreenContextCollector].
 *
 * **Qué hace:**
 *  - Recibe el `eventType: Int` proveniente del AccessibilityService.
 *  - Pasa por el classifier → policy → si COLLECT, invoca collector.
 *  - Captura excepciones del collector para que el service NUNCA crashee.
 *  - Loguea via [RobotLoopInstrumentation.recordSafeLog] (sin texto ni PII).
 *  - Expone [onServiceDisconnected] para limpiar el repository.
 *
 * **Qué NO hace:**
 *  - No invoca `performClick`, `dispatchGesture` ni ninguna acción de
 *    accesibilidad. **Read-only por contrato.**
 *  - No mantiene estado más allá de delegar al collector (que sí tiene
 *    throttle interno).
 *  - No accede directamente al `AccessibilityNodeInfo` — eso queda en
 *    [com.ojoclaro.android.agent.runtime.screen.AndroidAccessibilityScreenContextProvider].
 *
 * Thread-safety: completamente delegada al collector.
 */
class AccessibilitySnapshotEventRouter(
    private val collector: ScreenContextCollector,
    private val flags: () -> AgentCoreFeatureFlags = { AgentCoreFeatureFlags.DISABLED },
    private val classifier: (Int) -> AccessibilityEventClassifier.Relevance = {
        AccessibilityEventClassifier.classify(it)
    },
    private val instrumentation: (RobotLoopSafeLogEvent) -> Unit = {
        RobotLoopInstrumentation.recordSafeLog(it)
    }
) {

    /**
     * Punto de entrada principal. Se debe invocar desde `onAccessibilityEvent`
     * del service. Devuelve la acción tomada para que el caller pueda testear
     * y para logging interno.
     */
    fun onEvent(eventType: Int): RouterAction {
        val relevance = classifier(eventType)
        val decision = ScreenContextCollectionPolicy.decide(flags(), relevance)
        return when (decision) {
            ScreenContextCollectionPolicy.Decision.SKIP_FLAG_OFF -> {
                // Defensivo: si el flag se apagó desde la última vez, el
                // collector ya limpia internamente. No log para no spamear.
                RouterAction.Skipped(reason = "flag_disabled")
            }
            ScreenContextCollectionPolicy.Decision.SKIP_IRRELEVANT_EVENT -> {
                RouterAction.Skipped(reason = "event_irrelevant_type_$eventType")
            }
            ScreenContextCollectionPolicy.Decision.COLLECT -> {
                val outcome = runCatching { collector.collect() }
                    .getOrElse { CollectOutcome.Skipped(reason = "collector_threw") }
                logOutcome(outcome)
                RouterAction.Collected(outcome)
            }
        }
    }

    /**
     * Limpia el snapshot publicado. Para invocar desde `onUnbind`/`onDestroy`
     * del service o cuando el usuario apaga el modo asistido.
     */
    fun onServiceDisconnected(): RouterAction {
        runCatching { collector.reset() }
        instrumentation(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.STRUCTURED_SCREEN_SNAPSHOT,
                result = RobotLoopLogResult.RESET
            )
        )
        return RouterAction.Cleared
    }

    private fun logOutcome(outcome: CollectOutcome) {
        val (result, packageName, elementCount, buttonCount, fieldCount) = when (outcome) {
            is CollectOutcome.Published -> {
                val s = outcome.snapshot
                LogShape(
                    result = RobotLoopLogResult.OK,
                    packageName = s.packageName,
                    elementCount = s.totalNodes,
                    buttonCount = s.buttons.size,
                    fieldCount = s.editableFields.size
                )
            }
            is CollectOutcome.Throttled -> {
                val s = outcome.cached
                LogShape(
                    result = RobotLoopLogResult.DROPPED,
                    packageName = s.packageName,
                    elementCount = s.totalNodes,
                    buttonCount = s.buttons.size,
                    fieldCount = s.editableFields.size
                )
            }
            CollectOutcome.NoSnapshot -> LogShape(result = RobotLoopLogResult.NO_SNAPSHOT)
            is CollectOutcome.Skipped -> LogShape(result = RobotLoopLogResult.DROPPED)
        }
        instrumentation(
            RobotLoopSafeLogEvent(
                stage = RobotLoopLogStage.STRUCTURED_SCREEN_SNAPSHOT,
                result = result,
                packageName = packageName,
                elementCount = elementCount,
                buttonCount = buttonCount,
                fieldCount = fieldCount
            )
        )
    }

    private data class LogShape(
        val result: RobotLoopLogResult,
        val packageName: String? = null,
        val elementCount: Int? = null,
        val buttonCount: Int? = null,
        val fieldCount: Int? = null
    )
}

sealed class RouterAction {
    data class Collected(val outcome: CollectOutcome) : RouterAction()
    data class Skipped(val reason: String) : RouterAction()
    data object Cleared : RouterAction()
}
