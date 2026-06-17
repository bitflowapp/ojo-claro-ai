package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags

/**
 * Política pura que decide si un evento de accesibilidad amerita invocar
 * el [ScreenContextCollector].
 *
 * Razón de existir como capa separada:
 *  - Independiza la decisión del runtime Android (testeable sin servicio).
 *  - Permite agregar reglas extra a futuro (ej: pausar collect mientras
 *    hay TTS hablando) sin modificar el classifier ni el router.
 *
 * Reglas hoy:
 *  1. Si `accessibilityRuntimeContextEnabled` está OFF → no colectar.
 *  2. Si el evento es `IRRELEVANT` (scroll, click, etc.) → no colectar.
 *  3. Si es `RELEVANT_NOW` → colectar.
 */
object ScreenContextCollectionPolicy {

    enum class Decision {
        COLLECT,
        SKIP_FLAG_OFF,
        SKIP_IRRELEVANT_EVENT
    }

    fun decide(
        flags: AgentCoreFeatureFlags,
        relevance: AccessibilityEventClassifier.Relevance
    ): Decision {
        if (!flags.accessibilityRuntimeContextEnabled) return Decision.SKIP_FLAG_OFF
        return when (relevance) {
            AccessibilityEventClassifier.Relevance.RELEVANT_NOW -> Decision.COLLECT
            AccessibilityEventClassifier.Relevance.IRRELEVANT -> Decision.SKIP_IRRELEVANT_EVENT
        }
    }
}
