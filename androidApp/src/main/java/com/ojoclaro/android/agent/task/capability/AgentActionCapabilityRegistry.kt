package com.ojoclaro.android.agent.task.capability

/**
 * Paquete 6G -- Safe Action Capability Registry.
 *
 * Fuente unica de verdad sobre que acciones de Android puede ejecutar Estela
 * hoy de forma segura. Es PURA: solo clasifica. La puerta de ejecucion segura
 * la consulta antes de permitir cualquier ejecucion.
 *
 * Regla de oro de 6G: una capacidad que no sea [AgentActionCapabilityDecision.SUPPORTED_SAFE]
 * no se ejecuta, sin importar lo que diga la propuesta.
 *
 * El registro acepta [overrides] solo para tests; en produccion se usa la
 * tabla auditada por defecto.
 */
class AgentActionCapabilityRegistry(
    private val overrides: Map<AgentActionCapabilityType, AgentActionCapability> = emptyMap()
) {

    /** Capacidad auditada para [type]. */
    fun capability(type: AgentActionCapabilityType): AgentActionCapability =
        overrides[type] ?: DEFAULT.getValue(type)

    /** Decision de auditoria para [type]. */
    fun decision(type: AgentActionCapabilityType): AgentActionCapabilityDecision =
        capability(type).decision

    /** true si [type] se puede ejecutar de forma segura ahora. */
    fun isSafeToExecuteNow(type: AgentActionCapabilityType): Boolean =
        capability(type).isSafeToExecuteNow

    /** Todas las capacidades, en orden de declaracion. */
    fun all(): List<AgentActionCapability> =
        AgentActionCapabilityType.values().map { capability(it) }

    companion object {
        private fun capability(
            type: AgentActionCapabilityType,
            decision: AgentActionCapabilityDecision,
            risk: AgentActionCapabilityRisk,
            requirement: AgentActionCapabilityRequirement,
            safeDescription: String
        ): Pair<AgentActionCapabilityType, AgentActionCapability> =
            type to AgentActionCapability(
                type = type,
                decision = decision,
                risk = risk,
                requirement = requirement,
                safeDescription = safeDescription
            )

        /**
         * Tabla auditada de capacidades (Paquete 6G).
         *
         * Solo cinco acciones son SUPPORTED_SAFE hoy: abrir app, abrir
         * ajustes, y las tres preparaciones en memoria, mas leer estado y
         * resumen seguro de pantalla. Todo lo que toca una app externa de
         * forma activa queda bloqueado o pendiente de instrumented test.
         */
        val DEFAULT: Map<AgentActionCapabilityType, AgentActionCapability> = mapOf(
            capability(
                type = AgentActionCapabilityType.OPEN_APP,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Abrir una app por su intent de launcher. No toca nada adentro."
            ),
            capability(
                type = AgentActionCapabilityType.OPEN_ACCESSIBILITY_SETTINGS,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Abrir los ajustes de accesibilidad del sistema."
            ),
            capability(
                type = AgentActionCapabilityType.OPEN_APP_SETTINGS,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Abrir la pantalla de ajustes de una app."
            ),
            capability(
                type = AgentActionCapabilityType.PREPARE_TEXT_IN_MEMORY,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Preparar el texto de un mensaje en memoria propia. No escribe en la app."
            ),
            capability(
                type = AgentActionCapabilityType.PREPARE_AUDIO_SCRIPT_IN_MEMORY,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Preparar el guion de un audio en memoria propia. No graba ni envia."
            ),
            capability(
                type = AgentActionCapabilityType.PREPARE_SEARCH_QUERY_IN_MEMORY,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Preparar una query de busqueda en memoria propia. No escribe en la app."
            ),
            capability(
                type = AgentActionCapabilityType.READ_TASK_STATE,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.LOW,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Leer y dictar el estado de la tarea activa."
            ),
            capability(
                type = AgentActionCapabilityType.READ_SAFE_SCREEN_SUMMARY,
                decision = AgentActionCapabilityDecision.SUPPORTED_SAFE,
                risk = AgentActionCapabilityRisk.MEDIUM,
                requirement = AgentActionCapabilityRequirement.NONE,
                safeDescription = "Dictar un resumen seguro y redactado de la pantalla. No lee datos sensibles completos."
            ),
            capability(
                type = AgentActionCapabilityType.FOCUS_FIELD,
                decision = AgentActionCapabilityDecision.INSTRUMENTED_TEST_REQUIRED,
                risk = AgentActionCapabilityRisk.MEDIUM,
                requirement = AgentActionCapabilityRequirement.INSTRUMENTED_TEST,
                safeDescription = "Enfocar un campo en una app externa. Necesita instrumented test antes de habilitarse."
            ),
            capability(
                type = AgentActionCapabilityType.WRITE_TEXT_EXTERNAL_APP,
                decision = AgentActionCapabilityDecision.INSTRUMENTED_TEST_REQUIRED,
                risk = AgentActionCapabilityRisk.HIGH,
                requirement = AgentActionCapabilityRequirement.INSTRUMENTED_TEST,
                safeDescription = "Escribir texto en una app externa. Bloqueada hasta instrumented test y capa de confirmacion."
            ),
            capability(
                type = AgentActionCapabilityType.SCROLL,
                decision = AgentActionCapabilityDecision.INSTRUMENTED_TEST_REQUIRED,
                risk = AgentActionCapabilityRisk.MEDIUM,
                requirement = AgentActionCapabilityRequirement.INSTRUMENTED_TEST,
                safeDescription = "Hacer scroll en una app externa. Necesita instrumented test antes de habilitarse."
            ),
            capability(
                type = AgentActionCapabilityType.BACK,
                decision = AgentActionCapabilityDecision.UNSUPPORTED_NEEDS_RESEARCH,
                risk = AgentActionCapabilityRisk.MEDIUM,
                requirement = AgentActionCapabilityRequirement.SAFE_ANDROID_API,
                safeDescription = "Volver atras necesita una accion global del sistema; no hay via segura todavia."
            ),
            capability(
                type = AgentActionCapabilityType.CLICK_BUTTON,
                decision = AgentActionCapabilityDecision.BLOCKED_DANGEROUS,
                risk = AgentActionCapabilityRisk.HIGH,
                requirement = AgentActionCapabilityRequirement.DEDICATED_SAFETY_LAYER,
                safeDescription = "Tocar un boton en una app externa. Bloqueada: puede disparar acciones peligrosas."
            ),
            capability(
                type = AgentActionCapabilityType.SEND_MESSAGE,
                decision = AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
                risk = AgentActionCapabilityRisk.CRITICAL,
                requirement = AgentActionCapabilityRequirement.DEDICATED_SAFETY_LAYER,
                safeDescription = "Enviar un mensaje. Bloqueada: accion sensible e irreversible."
            ),
            capability(
                type = AgentActionCapabilityType.SEND_AUDIO,
                decision = AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
                risk = AgentActionCapabilityRisk.CRITICAL,
                requirement = AgentActionCapabilityRequirement.DEDICATED_SAFETY_LAYER,
                safeDescription = "Grabar o enviar un audio. Bloqueada: accion sensible e irreversible."
            ),
            capability(
                type = AgentActionCapabilityType.REQUEST_RIDE,
                decision = AgentActionCapabilityDecision.BLOCKED_SENSITIVE,
                risk = AgentActionCapabilityRisk.CRITICAL,
                requirement = AgentActionCapabilityRequirement.DEDICATED_SAFETY_LAYER,
                safeDescription = "Solicitar un viaje. Bloqueada: accion sensible con costo real."
            ),
            capability(
                type = AgentActionCapabilityType.CONFIRM_PAYMENT,
                decision = AgentActionCapabilityDecision.BLOCKED_DANGEROUS,
                risk = AgentActionCapabilityRisk.CRITICAL,
                requirement = AgentActionCapabilityRequirement.DEDICATED_SAFETY_LAYER,
                safeDescription = "Confirmar un pago, compra o viaje. Bloqueada: accion peligrosa e irreversible."
            ),
            capability(
                type = AgentActionCapabilityType.PLACE_CALL,
                decision = AgentActionCapabilityDecision.BLOCKED_DANGEROUS,
                risk = AgentActionCapabilityRisk.HIGH,
                requirement = AgentActionCapabilityRequirement.DEDICATED_SAFETY_LAYER,
                safeDescription = "Iniciar una llamada. Bloqueada hasta una politica de confirmacion dedicada."
            )
        )
    }
}
