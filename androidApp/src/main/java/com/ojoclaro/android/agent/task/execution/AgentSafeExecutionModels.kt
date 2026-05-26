package com.ojoclaro.android.agent.task.execution

import com.ojoclaro.android.agent.apps.SafeAppLaunchPlan
import com.ojoclaro.android.agent.task.AgentTaskPlan
import com.ojoclaro.android.agent.task.action.AgentControlledActionProposal
import com.ojoclaro.android.agent.task.action.AgentControlledActionType

/**
 * Paquete 6F -- Safe Execution Gate v1.
 *
 * La puerta de ejecucion segura distingue de forma explicita entre:
 *  1. PROPONER  -- describir una accion (paquete 6E).
 *  2. PREPARAR  -- dejar contenido listo en memoria, sin tocar apps externas.
 *  3. EJECUTAR  -- realizar una accion segura y permitida (abrir app).
 *  4. BLOQUEAR  -- frenar una accion sensible.
 *  5. CONFIRMAR -- pedir una confirmacion fuerte para acciones futuras.
 *
 * En 6F SOLO se ejecuta o se confirma como ejecutable:
 *  - OPEN_APP (si ya esta soportado por SafeAppLauncher).
 *  - Preparar texto / guion de audio / query en memoria.
 *  - Consultar estado, cancelar propuesta, esperar al usuario.
 *
 * NUNCA se ejecuta: enviar mensaje / audio, grabar audio, pedir taxi, pagar,
 * transferir, comprar, borrar, llamar, tocar boton de confirmar, escribir en
 * apps externas, click / gesture / accessibility action.
 */
enum class AgentSafeExecutionStatus {
    /** La accion es segura y permitida: se puede ejecutar ahora. */
    ALLOW_SAFE_EXECUTION,
    /** Hay una confirmacion pendiente que resolver antes de ejecutar. */
    REQUIRE_CONFIRMATION,
    /** La accion es sensible: se bloquea, no se ejecuta. */
    BLOCK_SENSITIVE_ACTION,
    /** Solo se puede preparar / orientar, no ejecutar. */
    PREPARE_ONLY,
    /** Falta un dato del usuario para poder avanzar. */
    WAITING_FOR_USER,
    /** No hay una propuesta de accion preparada. */
    NO_ACTIVE_PROPOSAL,
    /** No se reconoce una accion segura: se falla de forma segura. */
    FAILED_SAFE
}

/** Entrada para la puerta de ejecucion segura. */
data class AgentSafeExecutionRequest(
    val plan: AgentTaskPlan?,
    val proposal: AgentControlledActionProposal?,
    val userCommand: String,
    val hasPendingExternalConfirmation: Boolean = false,
    val userConfirmedStrongly: Boolean = false
)

/** Decision pura de la puerta de ejecucion segura. No tiene efectos. */
data class AgentSafeExecutionDecision(
    val status: AgentSafeExecutionStatus,
    val spokenText: String,
    val reason: String,
    /** Tipo de accion ejecutable cuando [status] es ALLOW_SAFE_EXECUTION. */
    val executableType: AgentControlledActionType? = null
) {
    val isExecutable: Boolean
        get() = status == AgentSafeExecutionStatus.ALLOW_SAFE_EXECUTION
}

/** Registro de auditoria de un intento de ejecucion. Sin datos sensibles. */
data class AgentExecutionAuditEntry(
    val taskId: String?,
    val proposalId: String?,
    val actionType: AgentControlledActionType?,
    val status: AgentSafeExecutionStatus,
    val executed: Boolean,
    val detail: String,
    val timestampMillis: Long
)

/** Resultado de pasar por la puerta de ejecucion segura. */
data class AgentSafeExecutionResult(
    val status: AgentSafeExecutionStatus,
    val spokenText: String,
    /** true solo si se realizo una accion segura (abrir app o preparar). */
    val executed: Boolean,
    /** Contenido que quedo preparado en memoria, si corresponde. */
    val preparedText: String? = null,
    /** Plan de apertura segura para handoff legacy, si no se lanzo directo. */
    val launchPlan: SafeAppLaunchPlan? = null,
    /** true si se abrio una app directamente vvia SafeAppLauncher. */
    val launchedDirectly: Boolean = false,
    val proposal: AgentControlledActionProposal? = null,
    val audit: AgentExecutionAuditEntry
)
