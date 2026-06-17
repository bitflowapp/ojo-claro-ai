package com.ojoclaro.android.agent.mission

/**
 * Modelos puros del Estela Agent Core v1.
 *
 * GPT propone → AgentPolicyGate autoriza → Android ejecuta → el observador
 * verifica. Todo lo de este paquete es Kotlin puro y testeable en JVM:
 * cero referencias a Android framework.
 */

/** Whitelist de herramientas. Espejo exacto del registro del backend. */
enum class AgentToolName(val wire: String) {
    CHECK_ACCESSIBILITY_STATUS("check_accessibility_status"),
    CHECK_MICROPHONE_PERMISSION("check_microphone_permission"),
    CHECK_BACKEND_HEALTH("check_backend_health"),
    REMEMBER_ORIGIN_APP("remember_origin_app"),
    OPEN_APP("open_app"),
    RETURN_TO_ORIGIN("return_to_origin"),
    READ_CURRENT_SCREEN_LOCAL("read_current_screen_local"),
    LIST_VISIBLE_ACTIONS_LOCAL("list_visible_actions_local"),
    ACTIVATE_VISIBLE_ACTION("activate_visible_action"),
    GO_BACK("go_back"),
    SCROLL_ACCESSIBILITY("scroll_accessibility"),
    SPEAK("speak"),
    ASK_USER("ask_user"),
    FINISH("finish"),
    FAIL("fail"),

    // Outdoor Guidance v1 (Fase 3B)
    CHECK_LOCATION_PERMISSION("check_location_permission"),
    CHECK_LOCATION_SERVICES_ENABLED("check_location_services_enabled"),
    GET_CURRENT_LOCATION("get_current_location"),
    DESCRIBE_CURRENT_LOCATION("describe_current_location"),
    START_OUTDOOR_GUIDANCE("start_outdoor_guidance"),
    GET_ROUTE_PROGRESS("get_route_progress"),
    STOP_OUTDOOR_GUIDANCE("stop_outdoor_guidance"),
    DESCRIBE_SCENE_ON_DEMAND("describe_scene_on_demand");

    companion object {
        private val byWire: Map<String, AgentToolName> = entries.associateBy { it.wire }

        fun fromWire(raw: String?): AgentToolName? = raw?.let { byWire[it.trim()] }

        val allWireNames: List<String> = entries.map { it.wire }
    }
}

enum class AgentMissionStatus {
    CONTINUE,
    NEED_USER,
    COMPLETED,
    FAILED,
    CANCELLED;

    companion object {
        fun fromWire(raw: String?): AgentMissionStatus? =
            entries.firstOrNull { it.name == raw?.trim()?.uppercase() }
    }
}

enum class AgentPrivacyClass {
    PUBLIC_UI,
    PRIVATE_APP,
    SENSITIVE_APP,
    UNKNOWN
}

enum class AgentToolResultStatus { SUCCESS, FAILED, BLOCKED }

/** Política de seguridad por acción (clasificación previa a ejecutar). */
enum class AgentActionPolicy { SAFE_READ, SAFE_NAVIGATION, USER_CONFIRMATION, BLOCKED }

/** Máquina de estados de la misión. Las transiciones tienen causa logueada. */
enum class AgentMissionState {
    IDLE,
    LISTENING_FOR_GOAL,
    PLANNING,
    VALIDATING_ACTION,
    EXECUTING_ACTION,
    WAITING_FOR_OBSERVATION,
    SPEAKING_PROGRESS,
    WAITING_FOR_USER,
    COMPLETED,
    FAILED,
    CANCELLED
}

/** Acción propuesta por el planner (todavía sin validar). */
data class AgentPlannedAction(
    val tool: AgentToolName,
    val arguments: Map<String, Any?> = emptyMap()
)

/** Acción ya validada por el registry: argumentos normalizados y tipados. */
data class AgentValidatedAction(
    val tool: AgentToolName,
    val arguments: Map<String, Any?> = emptyMap()
) {
    fun stringArg(key: String): String? = arguments[key] as? String
    fun intArg(key: String): Int? = (arguments[key] as? Number)?.toInt()
    fun boolArg(key: String): Boolean? = arguments[key] as? Boolean

    /** Firma estable para detección de no-progreso. */
    val signature: String
        get() = tool.wire + "|" + arguments.entries
            .sortedBy { it.key }
            .joinToString(",") { "${it.key}=${it.value}" }
}

/** Decisión del planner remoto ya parseada y saneada. */
data class AgentPlannerDecision(
    val ok: Boolean,
    val status: AgentMissionStatus,
    val action: AgentPlannedAction?,
    val spokenProgress: String? = null,
    val expectedPostcondition: String? = null,
    val reasoningSummary: String? = null,
    val model: String = "",
    val errorCode: String? = null
) {
    companion object {
        fun clientError(code: String): AgentPlannerDecision = AgentPlannerDecision(
            ok = false,
            status = AgentMissionStatus.FAILED,
            action = null,
            errorCode = code
        )
    }
}

/** Resultado real de ejecutar una herramienta, con verificación local. */
data class AgentToolResult(
    val tool: AgentToolName,
    val status: AgentToolResultStatus,
    val observed: Map<String, Any?> = emptyMap(),
    val postconditionVerified: Boolean = false,
    val failureReason: String? = null,
    val userReply: String? = null
)

/**
 * Observación local inmutable. Para PRIVATE_APP/SENSITIVE_APP/UNKNOWN nunca
 * contiene texto visible ni etiquetas: solo capacidades abstractas.
 */
data class AgentObservationSnapshot(
    val packageName: String?,
    val screenClass: String,
    val privacyClass: AgentPrivacyClass,
    val availableTools: List<AgentToolName>,
    val publicActions: List<AgentPublicAction> = emptyList(),
    val visibleItemCount: Int = 0,
    val canOpenByOrdinal: Boolean = false,
    val canReadLocally: Boolean = true
) {
    /** Huella para detectar pantallas repetidas / IDs vencidos. */
    val fingerprint: String
        get() = "${packageName.orEmpty()}|$screenClass|$privacyClass|$visibleItemCount"
}

/** Acción pública sanitizada (solo PUBLIC_UI). */
data class AgentPublicAction(
    val actionId: String,
    val label: String
)

/** Paso completado, en el shape mínimo que viaja al backend. */
data class AgentCompletedStep(
    val tool: AgentToolName,
    val status: AgentToolResultStatus
)

/** Estado de sesión en memoria. Nunca se persiste. */
data class AgentSessionState(
    val sessionId: String,
    val goal: String,
    val status: AgentMissionStatus = AgentMissionStatus.CONTINUE,
    val originPackage: String?,
    val currentPackage: String?,
    val stepIndex: Int = 0,
    val maxSteps: Int = AgentMissionBudgets.MAX_STEPS,
    val replanCount: Int = 0,
    val startedAtElapsedRealtime: Long,
    val operationGeneration: Long,
    val completedSteps: List<AgentCompletedStep> = emptyList(),
    val pendingConfirmation: String? = null,
    val lastObservationFingerprint: String? = null,
    val lastToolResult: AgentToolResult? = null,
    val cancellationRequested: Boolean = false
)

/** Resultado final de la misión, para el caller (el service). */
data class AgentMissionOutcome(
    val status: AgentMissionStatus,
    val spokenSummary: String,
    val stepsExecuted: Int,
    val replans: Int,
    val errorCode: String? = null
)

object AgentMissionBudgets {
    const val MAX_STEPS: Int = 8
    const val MAX_REPLANS: Int = 2
    const val MAX_DURATION_MILLIS: Long = 90_000L
    const val PLANNER_TIMEOUT_MILLIS: Long = 20_000L
    const val ASK_USER_TIMEOUT_MILLIS: Long = 30_000L
    const val POSTCONDITION_POLL_MILLIS: Long = 300L
    const val POSTCONDITION_MAX_WAIT_MILLIS: Long = 3_600L
    const val MAX_SPOKEN_CHARS: Int = 280
}
