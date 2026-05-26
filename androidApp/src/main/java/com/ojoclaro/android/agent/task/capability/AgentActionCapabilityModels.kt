package com.ojoclaro.android.agent.task.capability

/**
 * Paquete 6G -- Android Safe Action Capability Audit.
 *
 * Esta capa es PURA y describe, de forma seria y auditada, que acciones de
 * Android puede ejecutar Estela hoy de forma realmente segura para una
 * persona ciega, y cuales no.
 *
 * No ejecuta nada. No llama a performClick / dispatchGesture /
 * performGlobalAction. Solo clasifica capacidades para que la puerta de
 * ejecucion segura ([com.ojoclaro.android.agent.task.execution.AgentSafeExecutionGate])
 * pueda consultar una fuente de verdad unica antes de permitir cualquier
 * ejecucion.
 */
enum class AgentActionCapabilityType {
    /** Abrir una app por su intent de launcher. */
    OPEN_APP,
    /** Abrir la pantalla de ajustes de accesibilidad del sistema. */
    OPEN_ACCESSIBILITY_SETTINGS,
    /** Abrir la pantalla de ajustes de una app. */
    OPEN_APP_SETTINGS,
    /** Preparar texto de mensaje en memoria propia de Estela. */
    PREPARE_TEXT_IN_MEMORY,
    /** Preparar guion de audio en memoria propia de Estela. */
    PREPARE_AUDIO_SCRIPT_IN_MEMORY,
    /** Preparar query de busqueda en memoria propia de Estela. */
    PREPARE_SEARCH_QUERY_IN_MEMORY,
    /** Leer y dictar el estado de la tarea activa. */
    READ_TASK_STATE,
    /** Leer y dictar un resumen seguro y redactado de la pantalla. */
    READ_SAFE_SCREEN_SUMMARY,
    /** Enfocar un campo dentro de una app externa. */
    FOCUS_FIELD,
    /** Escribir texto dentro de una app externa. */
    WRITE_TEXT_EXTERNAL_APP,
    /** Hacer scroll dentro de una app externa. */
    SCROLL,
    /** Volver atras (accion global del sistema). */
    BACK,
    /** Tocar un boton dentro de una app externa. */
    CLICK_BUTTON,
    /** Enviar un mensaje. */
    SEND_MESSAGE,
    /** Grabar o enviar un audio. */
    SEND_AUDIO,
    /** Solicitar un viaje. */
    REQUEST_RIDE,
    /** Confirmar un pago, una compra o un viaje. */
    CONFIRM_PAYMENT,
    /** Iniciar una llamada telefonica. */
    PLACE_CALL
}

/** Riesgo de una capacidad de accion. */
enum class AgentActionCapabilityRisk {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}

/** Decision de auditoria sobre una capacidad. */
enum class AgentActionCapabilityDecision {
    /** Se puede ejecutar de forma segura ahora. */
    SUPPORTED_SAFE,
    /** Se puede ejecutar, pero requiere confirmacion explicita. */
    SUPPORTED_REQUIRES_CONFIRMATION,
    /** No esta soportada: hace falta investigar una via segura. */
    UNSUPPORTED_NEEDS_RESEARCH,
    /** Bloqueada por tocar datos o pantallas sensibles. */
    BLOCKED_SENSITIVE,
    /** Bloqueada por ser peligrosa o irreversible. */
    BLOCKED_DANGEROUS,
    /** Necesita instrumented test en Android real antes de habilitarse. */
    INSTRUMENTED_TEST_REQUIRED
}

/** Que hace falta para habilitar (o ejecutar) una capacidad. */
enum class AgentActionCapabilityRequirement {
    /** Nada: ya es segura. */
    NONE,
    /** Confirmacion explicita del usuario. */
    USER_CONFIRMATION,
    /** Instrumented test en Android real / emulador. */
    INSTRUMENTED_TEST,
    /** Una API de Android segura que todavia no esta investigada. */
    SAFE_ANDROID_API,
    /** Una capa de seguridad dedicada (confirmacion fuerte + auditoria). */
    DEDICATED_SAFETY_LAYER
}

/**
 * Capacidad de accion auditada. Tipo valor inmutable, sin Android, seguro
 * para cruzar capas.
 */
data class AgentActionCapability(
    val type: AgentActionCapabilityType,
    val decision: AgentActionCapabilityDecision,
    val risk: AgentActionCapabilityRisk,
    val requirement: AgentActionCapabilityRequirement,
    val safeDescription: String
) {
    init {
        require(safeDescription.isNotBlank()) { "safeDescription must not be blank" }
    }

    /** true solo si la accion se puede ejecutar de forma segura ahora mismo. */
    val isSafeToExecuteNow: Boolean
        get() = decision == AgentActionCapabilityDecision.SUPPORTED_SAFE

    /** true si la capacidad esta bloqueada (sensible o peligrosa). */
    val isBlocked: Boolean
        get() = decision == AgentActionCapabilityDecision.BLOCKED_SENSITIVE ||
            decision == AgentActionCapabilityDecision.BLOCKED_DANGEROUS
}
