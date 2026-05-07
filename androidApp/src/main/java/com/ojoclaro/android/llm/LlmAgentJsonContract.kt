package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object LlmAgentJsonContract {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun requestToJson(request: LlmAgentRequest): String = requestToJsonObject(request).toString()

    fun responseToJson(response: LlmAgentResponse): String = responseToJsonObject(response).toString()

    fun requestFromJson(json: String): LlmAgentRequest =
        requestFromJsonObject(json.parseObject())

    fun responseFromJson(json: String): LlmAgentResponse =
        responseFromJsonObject(json.parseObject())

    fun requestToJsonObject(request: LlmAgentRequest): JsonObject = buildJsonObject {
        put("originalText", JsonPrimitive(request.originalText))
        put("normalizedText", JsonPrimitive(request.normalizedText))
        put("locale", JsonPrimitive(request.locale))
        put("agentState", JsonPrimitive(request.agentState.name))
        request.externalApp?.let { put("externalApp", JsonPrimitive(it)) }
        put("memorySummary", JsonPrimitive(request.memorySummary))
        put("knownSafeContacts", request.knownSafeContacts.toJsonArray())
        put("knownPlaces", request.knownPlaces.toJsonArray())
        put("activePendingTasks", request.activePendingTasks.toJsonArray())
        put("allowedIntents", request.allowedIntents.map { it.name }.toJsonArray())
        put("forbiddenActions", request.forbiddenActions.toJsonArray())
    }

    fun responseToJsonObject(response: LlmAgentResponse): JsonObject = buildJsonObject {
        response.intent?.let { put("intent", JsonPrimitive(it.name)) }
        put("confidence", JsonPrimitive(response.confidence))
        response.contactName?.let { put("contactName", JsonPrimitive(it)) }
        response.messageText?.let { put("messageText", JsonPrimitive(it)) }
        response.proposedMessage?.let { put("proposedMessage", JsonPrimitive(it)) }
        response.destination?.let { put("destination", JsonPrimitive(it)) }
        response.locationAlias?.let { put("locationAlias", JsonPrimitive(it)) }
        response.routineName?.let { put("routineName", JsonPrimitive(it)) }
        response.pendingTask?.let { put("pendingTask", JsonPrimitive(it)) }
        put("missingSlots", response.missingSlots.toJsonArray())
        response.userFacingQuestion?.let { put("userFacingQuestion", JsonPrimitive(it)) }
        response.suggestionText?.let { put("suggestionText", JsonPrimitive(it)) }
        put("requiresConfirmation", JsonPrimitive(response.requiresConfirmation))
        put("shouldExecuteImmediately", JsonPrimitive(response.shouldExecuteImmediately))
        response.safetyNotes?.let { put("safetyNotes", JsonPrimitive(it)) }
    }

    fun requestFromJsonObject(json: JsonObject): LlmAgentRequest =
        LlmAgentRequest(
            originalText = json.stringValue("originalText"),
            normalizedText = json.stringValue("normalizedText"),
            locale = json.stringValue("locale", "es-AR"),
            agentState = runCatching {
                AgentState.valueOf(json.stringValue("agentState", AgentState.IDLE.name))
            }.getOrDefault(AgentState.IDLE),
            externalApp = json.stringValueOrNull("externalApp"),
            memorySummary = json.stringValue("memorySummary"),
            knownSafeContacts = json.stringList("knownSafeContacts"),
            knownPlaces = json.stringList("knownPlaces"),
            activePendingTasks = json.stringList("activePendingTasks"),
            allowedIntents = json.intentList("allowedIntents"),
            forbiddenActions = json.stringList("forbiddenActions")
        )

    fun responseFromJsonObject(json: JsonObject): LlmAgentResponse =
        LlmAgentResponse(
            intent = json.stringValueOrNull("intent")?.let { runCatching { AgentIntent.valueOf(it) }.getOrNull() },
            confidence = json["confidence"]?.jsonPrimitive?.doubleOrNull?.toFloat() ?: 0f,
            contactName = json.stringValueOrNull("contactName"),
            messageText = json.stringValueOrNull("messageText"),
            proposedMessage = json.stringValueOrNull("proposedMessage"),
            destination = json.stringValueOrNull("destination"),
            locationAlias = json.stringValueOrNull("locationAlias"),
            routineName = json.stringValueOrNull("routineName"),
            pendingTask = json.stringValueOrNull("pendingTask"),
            missingSlots = json.stringList("missingSlots"),
            userFacingQuestion = json.stringValueOrNull("userFacingQuestion"),
            suggestionText = json.stringValueOrNull("suggestionText"),
            requiresConfirmation = json.booleanValue("requiresConfirmation"),
            shouldExecuteImmediately = json.booleanValue("shouldExecuteImmediately"),
            safetyNotes = json.stringValueOrNull("safetyNotes")
        )

    private fun String.parseObject(): JsonObject =
        json.parseToJsonElement(this).jsonObject

    private fun JsonObject.stringValue(key: String, default: String = ""): String =
        stringValueOrNull(key) ?: default

    private fun JsonObject.stringValueOrNull(key: String): String? =
        this[key]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.booleanValue(key: String): Boolean =
        this[key]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull() ?: false

    private fun JsonObject.stringList(key: String): List<String> {
        val array = this[key]?.jsonArray ?: return emptyList()
        return array.mapNotNull { it.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }
    }

    private fun JsonObject.intentList(key: String): List<AgentIntent> {
        val array = this[key]?.jsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val value = element.jsonPrimitive.contentOrNull ?: return@mapNotNull null
            runCatching { AgentIntent.valueOf(value) }.getOrNull()
        }
    }

    private fun Iterable<String>.toJsonArray(): JsonArray = JsonArray(map { JsonPrimitive(it) })
}
