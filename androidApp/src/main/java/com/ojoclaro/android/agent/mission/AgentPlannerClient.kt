package com.ojoclaro.android.agent.mission

import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentNetworkClient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Cliente de /agent/next.
 *
 * Reusa el mismo stack HTTP que /intent (LlmAgentNetworkClient). Envía SOLO el
 * estado mínimo de sesión; la observación viaja abstracta para pantallas no
 * públicas. Nunca incluye texto de pantalla ni el contenido leído localmente.
 */
interface AgentPlannerClient {
    suspend fun next(request: AgentPlannerRequest): AgentPlannerDecision
}

/** Estado mínimo que viaja al backend (shape del contrato /agent/next). */
data class AgentPlannerRequest(
    val sessionId: String,
    val goal: String,
    val stepIndex: Int,
    val originPackage: String?,
    val observation: AgentObservationSnapshot,
    val completedSteps: List<AgentCompletedStep>,
    val lastToolResult: AgentToolResult?,
    val remainingStepBudget: Int,
    val replanCount: Int
)

object AgentPlannerJsonContract {

    fun requestToJson(request: AgentPlannerRequest): String = buildJsonObject {
        put("session_id", request.sessionId)
        put("goal", request.goal.take(500))
        put("step_index", request.stepIndex)
        put("origin_package", request.originPackage ?: "")
        put("current_observation", observationToJson(request.observation))
        put(
            "completed_steps",
            buildJsonArray {
                request.completedSteps.takeLast(16).forEach { step ->
                    add(
                        buildJsonObject {
                            put("tool", step.tool.wire)
                            put("status", step.status.name)
                        }
                    )
                }
            }
        )
        request.lastToolResult?.let { result ->
            put("last_tool_result", toolResultToJson(result))
        } ?: put("last_tool_result", JsonNull)
        put("remaining_step_budget", request.remainingStepBudget.coerceIn(0, 16))
        put("replan_count", request.replanCount.coerceIn(0, 8))
    }.toString()

    private fun observationToJson(observation: AgentObservationSnapshot): JsonObject =
        buildJsonObject {
            put("package_name", observation.packageName ?: "")
            put("screen_class", observation.screenClass.take(48))
            put("privacy_class", observation.privacyClass.name)
            put(
                "available_tools",
                buildJsonArray {
                    observation.availableTools.forEach { add(JsonPrimitive(it.wire)) }
                }
            )
            // Solo PUBLIC_UI puede publicar etiquetas; el resto viaja vacío.
            put(
                "public_actions",
                buildJsonArray {
                    if (observation.privacyClass == AgentPrivacyClass.PUBLIC_UI) {
                        observation.publicActions.take(12).forEach { action ->
                            add(
                                buildJsonObject {
                                    put("action_id", action.actionId.take(64))
                                    put("label", action.label.take(60))
                                }
                            )
                        }
                    }
                }
            )
            put("visible_item_count", observation.visibleItemCount.coerceIn(0, 500))
            put("can_open_by_ordinal", observation.canOpenByOrdinal)
            put("can_read_locally", observation.canReadLocally)
        }

    private fun toolResultToJson(result: AgentToolResult): JsonObject = buildJsonObject {
        put("tool", result.tool.wire)
        put("status", result.status.name)
        put("postcondition_verified", result.postconditionVerified)
        put("failure_reason", result.failureReason?.take(160) ?: "")
        put(
            "observed",
            buildJsonObject {
                result.observed.entries.take(12).forEach { (key, value) ->
                    when (value) {
                        is Boolean -> put(key.take(48), value)
                        is Int -> put(key.take(48), value)
                        is Long -> put(key.take(48), value.toInt())
                        is String -> put(key.take(48), value.take(120))
                        else -> Unit
                    }
                }
            }
        )
        result.userReply?.let { put("user_reply", it.take(240)) }
    }

    fun parseDecision(body: String): AgentPlannerDecision {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }
            .getOrNull()
            ?: return AgentPlannerDecision.clientError("invalid_backend_json")

        val ok = root.boolean("ok") ?: false
        val statusRaw = root.string("status")
        val status = AgentMissionStatus.fromWire(statusRaw)
            ?: return AgentPlannerDecision.clientError("invalid_status")

        val actionJson = root["action"] as? JsonObject
        val action = actionJson?.let { json ->
            val tool = AgentToolName.fromWire(json.string("tool"))
                ?: return AgentPlannerDecision.clientError("unknown_tool")
            AgentPlannedAction(tool = tool, arguments = json.argumentsMap())
        }

        return AgentPlannerDecision(
            ok = ok,
            status = status,
            action = action,
            spokenProgress = root.string("spoken_progress")?.takeIf { it.isNotBlank() },
            expectedPostcondition = (root["expected_postcondition"] as? JsonObject)
                ?.string("type"),
            reasoningSummary = root.string("reasoning_summary")?.take(160),
            model = root.string("model").orEmpty(),
            errorCode = root.string("error_code")?.takeIf { it.isNotBlank() }
        )
    }

    private fun JsonObject.argumentsMap(): Map<String, Any?> {
        val argsJson = this["arguments"] as? JsonObject ?: return emptyMap()
        return argsJson.entries.associate { (key, element) ->
            val primitive = element as? JsonPrimitive
            val value: Any? = when {
                primitive == null -> null
                primitive.isString -> primitive.content
                primitive.booleanOrNull != null -> primitive.booleanOrNull
                primitive.intOrNull != null -> primitive.intOrNull
                else -> primitive.content
            }
            key to value
        }
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

    private fun JsonObject.boolean(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}

class HttpAgentPlannerClient(
    private val config: LlmAgentClientConfig,
    private val networkClient: LlmAgentNetworkClient,
    private val timeoutMillis: Long = AgentMissionBudgets.PLANNER_TIMEOUT_MILLIS
) : AgentPlannerClient {

    private val agentNextUrl: String =
        if (config.normalizedBaseUrl.isBlank()) "" else "${config.normalizedBaseUrl}/agent/next"

    override suspend fun next(request: AgentPlannerRequest): AgentPlannerDecision {
        if (!config.isConfigured() || agentNextUrl.isBlank()) {
            return AgentPlannerDecision.clientError("agent_backend_not_configured")
        }
        val payload = AgentPlannerJsonContract.requestToJson(request)
        return try {
            val response = withTimeout(timeoutMillis) {
                networkClient.postJson(
                    url = agentNextUrl,
                    jsonBody = payload,
                    timeoutMillis = timeoutMillis,
                    headers = mapOf("ngrok-skip-browser-warning" to "true")
                )
            }
            when {
                response.statusCode == 401 -> AgentPlannerDecision.clientError("agent_http_401")
                response.statusCode == 429 -> AgentPlannerDecision.clientError("agent_http_429")
                response.statusCode >= 500 -> AgentPlannerDecision.clientError("agent_http_5xx")
                response.statusCode !in 200..299 ->
                    AgentPlannerDecision.clientError("agent_http_${response.statusCode}")
                response.body.isBlank() -> AgentPlannerDecision.clientError("agent_empty_body")
                else -> AgentPlannerJsonContract.parseDecision(response.body)
            }
        } catch (_: TimeoutCancellationException) {
            AgentPlannerDecision.clientError("agent_timeout")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            AgentPlannerDecision.clientError("agent_network_error")
        }
    }
}
