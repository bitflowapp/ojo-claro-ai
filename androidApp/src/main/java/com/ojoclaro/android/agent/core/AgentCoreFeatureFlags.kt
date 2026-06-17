package com.ojoclaro.android.agent.core

/**
 * Feature flags del Agent Core v1.
 *
 * Por default TODO está apagado. La integración del Agent Core con HomeViewModel
 * y la UI quedará detrás de estos flags, así no se rompe el flujo actual de
 * WhatsApp / Maps / Phone / REPEAT_LAST / callback post-handoff.
 *
 * Cuando se quiera empezar a probar el planner real, se construye un
 * AgentCoreFeatureFlags(plannerEnabled = true, ...) en el sitio de integración
 * y se pasa por dependency injection.
 *
 * Hoy solo se usa para tests y para que las nuevas piezas existan como
 * "scaffold listo para enchufar".
 */
data class AgentCoreFeatureFlags(
    val plannerEnabled: Boolean = false,
    val chainedActionsEnabled: Boolean = false,
    val screenSummarizationEnabled: Boolean = false,
    val llmFallbackEnabled: Boolean = false,
    val preferenceLearningEnabled: Boolean = false,
    val emergencyModeEnabled: Boolean = false,
    val genericAppExecutionEnabled: Boolean = false,
    /**
     * Activa el [com.ojoclaro.android.agent.core.runtime.AgentRuntimeBridge].
     *
     * Cuando está en false (default de producción), el bridge devuelve siempre
     * [com.ojoclaro.android.agent.core.runtime.BridgeOutcome.Skipped] y la
     * app sigue corriendo por el orquestador legacy intacto.
     *
     * Cuando se prende, el caller puede usar el bridge para evaluar comandos
     * vía [com.ojoclaro.android.agent.core.AgentActionEvaluator] y registrar
     * pendientes en [com.ojoclaro.android.consent.ConfirmationManager]. NO
     * implica que toda la app pase al planner — el flag es específico de la
     * capa de safety/confirmation.
     */
    val typedConfirmationEnabled: Boolean = false,
    /**
     * Activa el cableado del Structured Screen Snapshot v1 desde
     * [com.ojoclaro.android.agent.core.screen.ScreenContextCollector] hacia el
     * [com.ojoclaro.android.agent.core.screen.ScreenContextRepository].
     *
     * Cuando está en false (default), el repository nunca recibe escritura ni
     * publica snapshots — `current()` devuelve null. La capa de accesibilidad
     * existente y el flujo legacy siguen intactos.
     *
     * Cuando se prende, el caller (típicamente un coroutine job o evento del
     * AccessibilityService) puede invocar `collector.collect()` para que el
     * repository publique un snapshot estructurado, redactado y clasificado,
     * disponible para [com.ojoclaro.android.agent.core.AgentContext] sin
     * tocar el provider legacy.
     */
    val accessibilityRuntimeContextEnabled: Boolean = false,
    /**
     * Paquete 5E — Habilita el [com.ojoclaro.android.agent.core.screen.ScreenChangeAwarenessCoordinator].
     *
     * Cuando está OFF (default de producción), el coordinator no emite
     * anuncios aunque reciba snapshots. La observación es no-op. Cuando ON,
     * el coordinator evalúa cada snapshot publicado por el repository y
     * emite anuncios controlados (cooldown semántico, hot zones avisadas
     * sin contenido sensible).
     *
     * Independiente de [accessibilityRuntimeContextEnabled]: si éste está
     * OFF, no llegan snapshots al repository y no hay evaluación. Si está
     * ON pero `screenChangeAwarenessEnabled` está OFF, hay snapshots pero
     * el coordinator no anuncia.
     */
    val screenChangeAwarenessEnabled: Boolean = false,
    /**
     * Paquete 6D -- habilita el follow-up automatico de tareas activas ante
     * snapshots nuevos. Cuando esta OFF, el observer de tareas sigue disponible
     * solo por comando manual ("revisa la tarea", "segui con la tarea") y el
     * flujo legacy queda intacto.
     *
     * Cuando esta ON, la capa de UI puede conectar el StateFlow del
     * ScreenContextRepository con AgentTaskFollowUpCoordinator. El coordinator
     * solo observa y habla de forma controlada: no ejecuta acciones, no escribe
     * texto y no confirma operaciones.
     */
    val taskAutoFollowUpEnabled: Boolean = false
) {
    val anyEnabled: Boolean
        get() = plannerEnabled || chainedActionsEnabled || screenSummarizationEnabled ||
            llmFallbackEnabled || preferenceLearningEnabled || emergencyModeEnabled ||
            genericAppExecutionEnabled || typedConfirmationEnabled ||
            accessibilityRuntimeContextEnabled || screenChangeAwarenessEnabled ||
            taskAutoFollowUpEnabled

    companion object {
        /** Todo apagado. Estado de la app en producción hoy. */
        val DISABLED: AgentCoreFeatureFlags = AgentCoreFeatureFlags()

        /**
         * Bandera segura para QA: todo habilitado MENOS ejecución genérica sobre
         * apps de terceros. Útil para validar el scaffolding sin tocar la
         * superficie de seguridad más grande.
         */
        val QA_PREVIEW: AgentCoreFeatureFlags = AgentCoreFeatureFlags(
            plannerEnabled = true,
            chainedActionsEnabled = true,
            screenSummarizationEnabled = true,
            llmFallbackEnabled = false, // sigue OFF hasta config segura
            preferenceLearningEnabled = true,
            emergencyModeEnabled = true,
            genericAppExecutionEnabled = false,
            typedConfirmationEnabled = true,
            accessibilityRuntimeContextEnabled = true,
            screenChangeAwarenessEnabled = true,
            taskAutoFollowUpEnabled = true
        )
    }
}
