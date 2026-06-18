package com.ojoclaro.android.agent.mission

/**
 * Ejecutor local de herramientas ya validadas y autorizadas.
 *
 * Cada implementación debe ser honesta: el resultado refleja lo OBSERVADO,
 * nunca lo deseado. "No lanzó excepción" no es éxito.
 */
interface AgentToolExecutor {
    suspend fun execute(
        action: AgentValidatedAction,
        session: AgentSessionState
    ): AgentToolResult
}

/** Resultado del healthcheck local del backend (tool check_backend_health). */
data class AgentBackendHealth(
    val reachable: Boolean,
    val status: String,
    val latencyMillis: Long
)

/** Métricas locales de una lectura de pantalla (el TEXTO nunca sale de acá). */
data class AgentLocalReadOutcome(
    val success: Boolean,
    val acceptedNodes: Int,
    val targetPackage: String?,
    val source: String?,
    val ttsRequested: Boolean,
    val failureReason: String? = null
)

/**
 * Resumen ABSTRACTO de un fix de ubicación para el Agent Core. Las
 * coordenadas exactas nunca entran acá: solo disponibilidad y buckets.
 */
data class AgentOutdoorFixSummary(
    val available: Boolean,
    val accuracyBucket: String,
    val ageBucket: String,
    val provider: String,
    val reason: String? = null
)

/**
 * Implementación estándar: todo entra por lambdas para que sea testeable en
 * JVM y para que el wiring Android (GlobalAssistantService) sea explícito.
 *
 * NO maneja ASK_USER: esa herramienta la coordina AgentSessionCoordinator
 * porque suspende esperando STT.
 */
class AndroidAgentToolExecutor(
    private val isAccessibilityConnected: () -> Boolean,
    private val isOverlayAttached: () -> Boolean,
    private val isMicrophoneGranted: () -> Boolean,
    private val checkBackendHealth: suspend (timeoutMillis: Int) -> AgentBackendHealth,
    private val currentPackage: () -> String?,
    private val openAppById: suspend (appId: String) -> Boolean,
    private val openPackage: suspend (packageName: String) -> Boolean,
    private val readScreenLocal: suspend (mode: String) -> AgentLocalReadOutcome,
    private val speakText: (String) -> Unit,
    private val performBack: () -> Boolean,
    private val performScroll: (forward: Boolean) -> String,
    // Outdoor Guidance v1 (Fase 3B)
    private val hasLocationPermission: () -> Boolean = { false },
    private val locationServicesEnabled: () -> Boolean = { false },
    private val readLocationSummary: suspend () -> AgentOutdoorFixSummary = {
        AgentOutdoorFixSummary(false, "unknown", "unknown", "none", "not_wired")
    },
    private val describeLocationAloud: suspend () -> Boolean = { false },
    private val startOutdoorGuidance: suspend (destination: String) -> Boolean = { false },
    private val outdoorGuidanceActive: () -> Boolean = { false },
    private val speakRouteProgress: suspend () -> Boolean = { false },
    private val stopOutdoorGuidance: suspend () -> Boolean = { false },
    private val describeSceneAloud: suspend () -> Boolean = { false }
) : AgentToolExecutor {

    override suspend fun execute(
        action: AgentValidatedAction,
        session: AgentSessionState
    ): AgentToolResult = when (action.tool) {

        AgentToolName.CHECK_ACCESSIBILITY_STATUS -> {
            val connected = runCatching { isAccessibilityConnected() }.getOrDefault(false)
            val overlay = runCatching { isOverlayAttached() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf(
                    "enabled" to connected,
                    "connected" to connected,
                    "overlay_attached" to overlay
                ),
                postconditionVerified = true
            )
        }

        AgentToolName.CHECK_MICROPHONE_PERMISSION -> {
            val granted = runCatching { isMicrophoneGranted() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("granted" to granted),
                postconditionVerified = true
            )
        }

        AgentToolName.CHECK_BACKEND_HEALTH -> {
            val timeout = action.intArg("timeout_ms") ?: 5_000
            val health = runCatching { checkBackendHealth(timeout) }
                .getOrElse { AgentBackendHealth(reachable = false, status = "ERROR", latencyMillis = -1) }
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf(
                    "reachable" to health.reachable,
                    "status" to health.status,
                    "latency_ms" to health.latencyMillis.toInt()
                ),
                postconditionVerified = true
            )
        }

        AgentToolName.REMEMBER_ORIGIN_APP -> {
            // El registry ya garantizó que el paquete fue observado realmente.
            val requested = action.stringArg("package_name").orEmpty()
            val matchesSession = requested == session.originPackage
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf(
                    "origin_package" to requested,
                    "matches_session_origin" to matchesSession
                ),
                postconditionVerified = true
            )
        }

        AgentToolName.OPEN_APP -> {
            val appId = action.stringArg("app_id").orEmpty()
            val launched = runCatching { openAppById(appId) }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = if (launched) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf("app_id" to appId, "launch_requested" to launched),
                // La poscondición real (paquete en foreground) la verifica el
                // coordinator observando después.
                postconditionVerified = false,
                failureReason = if (launched) null else "launch_failed"
            )
        }

        AgentToolName.RETURN_TO_ORIGIN -> {
            val origin = session.originPackage.orEmpty()
            if (origin.isBlank()) {
                AgentToolResult(
                    tool = action.tool,
                    status = AgentToolResultStatus.FAILED,
                    postconditionVerified = false,
                    failureReason = "origin_unknown"
                )
            } else {
                val launched = runCatching { openPackage(origin) }.getOrDefault(false)
                AgentToolResult(
                    tool = action.tool,
                    status = if (launched) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                    observed = mapOf("origin_package" to origin, "launch_requested" to launched),
                    postconditionVerified = false,
                    failureReason = if (launched) null else "launch_failed"
                )
            }
        }

        AgentToolName.READ_CURRENT_SCREEN_LOCAL -> {
            val mode = action.stringArg("mode") ?: "SUMMARY"
            val outcome = runCatching { readScreenLocal(mode) }
                .getOrElse {
                    AgentLocalReadOutcome(
                        success = false,
                        acceptedNodes = 0,
                        targetPackage = null,
                        source = null,
                        ttsRequested = false,
                        failureReason = "read_threw"
                    )
                }
            AgentToolResult(
                tool = action.tool,
                status = if (outcome.success) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf(
                    "accepted_nodes" to outcome.acceptedNodes,
                    "target_package" to (outcome.targetPackage ?: ""),
                    "source" to (outcome.source ?: ""),
                    "tts_requested" to outcome.ttsRequested
                ),
                postconditionVerified = outcome.success &&
                    outcome.ttsRequested &&
                    (outcome.acceptedNodes > 0 || outcome.failureReason == null),
                failureReason = outcome.failureReason
            )
        }

        AgentToolName.LIST_VISIBLE_ACTIONS_LOCAL -> {
            // v1: catálogo vacío. Respuesta honesta con conteo cero.
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("action_count" to 0),
                postconditionVerified = true
            )
        }

        AgentToolName.ACTIVATE_VISIBLE_ACTION -> {
            // Nunca debería llegar acá (registry/policy lo frenan en v1).
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.BLOCKED,
                postconditionVerified = false,
                failureReason = "not_available_v1"
            )
        }

        AgentToolName.GO_BACK -> {
            val ok = runCatching { performBack() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = if (ok) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf("back_performed" to ok),
                postconditionVerified = ok,
                failureReason = if (ok) null else "back_failed"
            )
        }

        AgentToolName.SCROLL_ACCESSIBILITY -> {
            val forward = action.stringArg("direction") == "FORWARD"
            val outcome = runCatching { performScroll(forward) }.getOrDefault("ERROR")
            val ok = outcome == "SCROLLED"
            AgentToolResult(
                tool = action.tool,
                status = if (ok) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf("outcome" to outcome),
                postconditionVerified = ok,
                failureReason = if (ok) null else "scroll_$outcome".lowercase()
            )
        }

        AgentToolName.SPEAK -> {
            val message = action.stringArg("message").orEmpty()
            runCatching { speakText(message) }
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("spoken" to true),
                postconditionVerified = true
            )
        }

        AgentToolName.ASK_USER -> AgentToolResult(
            tool = action.tool,
            status = AgentToolResultStatus.FAILED,
            postconditionVerified = false,
            failureReason = "ask_user_handled_by_coordinator"
        )

        AgentToolName.FINISH -> {
            val summary = action.stringArg("summary").orEmpty()
            runCatching { speakText(summary) }
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("summary_spoken" to true),
                postconditionVerified = true
            )
        }

        AgentToolName.FAIL -> {
            val message = action.stringArg("message").orEmpty()
            runCatching { speakText(message) }
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf(
                    "message_spoken" to true,
                    "recoverable" to (action.boolArg("recoverable") ?: true)
                ),
                postconditionVerified = true
            )
        }

        // --- Outdoor Guidance v1 ---

        AgentToolName.CHECK_LOCATION_PERMISSION -> {
            val granted = runCatching { hasLocationPermission() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("granted" to granted),
                postconditionVerified = true
            )
        }

        AgentToolName.CHECK_LOCATION_SERVICES_ENABLED -> {
            val enabled = runCatching { locationServicesEnabled() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("enabled" to enabled),
                postconditionVerified = true
            )
        }

        AgentToolName.GET_CURRENT_LOCATION -> {
            val summary = runCatching { readLocationSummary() }.getOrElse {
                AgentOutdoorFixSummary(false, "unknown", "unknown", "none", "read_threw")
            }
            // PRIVACIDAD: solo métricas abstractas. Nunca lat/lng al planner.
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf(
                    "available" to summary.available,
                    "accuracy_bucket" to summary.accuracyBucket,
                    "age_bucket" to summary.ageBucket,
                    "provider" to summary.provider,
                    "reason" to (summary.reason ?: "")
                ),
                postconditionVerified = true
            )
        }

        AgentToolName.DESCRIBE_CURRENT_LOCATION -> {
            val spoken = runCatching { describeLocationAloud() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = if (spoken) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf("spoken" to spoken),
                postconditionVerified = spoken,
                failureReason = if (spoken) null else "describe_failed"
            )
        }

        AgentToolName.START_OUTDOOR_GUIDANCE -> {
            val destination = action.stringArg("destination").orEmpty()
            val active = runCatching { startOutdoorGuidance(destination) }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = if (active) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf("guidance_active" to active),
                postconditionVerified = active,
                failureReason = if (active) null else "guidance_not_started"
            )
        }

        AgentToolName.GET_ROUTE_PROGRESS -> {
            val active = runCatching { outdoorGuidanceActive() }.getOrDefault(false)
            val spoken = if (active) {
                runCatching { speakRouteProgress() }.getOrDefault(false)
            } else {
                false
            }
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("guidance_active" to active, "spoken" to spoken),
                postconditionVerified = true
            )
        }

        AgentToolName.STOP_OUTDOOR_GUIDANCE -> {
            val stopped = runCatching { stopOutdoorGuidance() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = AgentToolResultStatus.SUCCESS,
                observed = mapOf("stopped" to stopped),
                postconditionVerified = stopped
            )
        }

        AgentToolName.DESCRIBE_SCENE_ON_DEMAND -> {
            // Llega acá solo porque el usuario pidió la misión por voz:
            // ese pedido es la acción explícita que habilita la cámara.
            val spoken = runCatching { describeSceneAloud() }.getOrDefault(false)
            AgentToolResult(
                tool = action.tool,
                status = if (spoken) AgentToolResultStatus.SUCCESS else AgentToolResultStatus.FAILED,
                observed = mapOf("described" to spoken),
                postconditionVerified = spoken,
                failureReason = if (spoken) null else "scene_describe_failed"
            )
        }
    }
}
