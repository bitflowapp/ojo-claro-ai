package com.ojoclaro.android.agent.situation

/**
 * Feature flag interno del Situation Brain.
 *
 * Fase 3: apagado por defecto. Con [ENABLED] en `false`, la ruta experimental
 * del Situation Brain en HomeViewModel NUNCA se ejecuta y el comportamiento de
 * producción es idéntico al de antes de esta fase.
 *
 * Se mantiene como constante Kotlin (no BuildConfig) a propósito, para no tocar
 * Gradle. Cuando el cableado esté maduro se podrá migrar a BuildConfig.
 */
object SituationBrainFeatureFlag {
    const val ENABLED: Boolean = false
}
