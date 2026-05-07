package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.AgentState
import org.json.JSONArray
import org.json.JSONObject

object LlmAgentJsonContract {
    fun requestToJson(request: LlmAgentRequest): String = requestToJsonObject(request).toString()

    fun responseToJson(response: LlmAgentResponse): String = responseToJsonObject(response).toString()

    fun requestFromJson(json: String): LlmAgentRequest = requestFromJsonObject(JSONObject(json))

    fun responseFromJson(json: String): LlmAgentResponse = responseFromJsonObject(JSONObject(json))

    fun requestToJsonObject(request: LlmAgentRequest): JSONObject = JSONObject().apply {
        put("originalText", request.originalText)
        put("normalizedText", request.normalizedText)
        put("locale", request.locale)
        put("agentState", request.agentState.name)
        put("externalApp", request.externalApp)
        put("memorySummary", request.memorySummary)
        put("knownSafeContacts", JSONArray(request.knownSafeContacts))
        put("knownPlaces", JSONArray(request.knownPlaces))
        put("activePendingTasks", JSONArray(request.activePendingTasks))
        put("allowedIntents", JSONArray(request.allowedIntents.map { it.name }))
        put("forbiddenActions", JSONArray(request.forbiddenActions))
    }

    fun responseToJsonObject(response: LlmAgentResponse): JSONObject = JSONObject().apply {
        put("intent", response.intent?.name)
        put("confidence", response.confidence)
        put("contactName", response.contactName)
        put("messageText", response.messageText)
        put("proposedMessage", response.proposedMessage)
        put("destination", response.destination)
        put("locationAlias", response.locationAlias)
        put("routineName", response.routineName)
        put("pendingTask", response.pendingTask)
        put("missingSlots", JSONArray(response.missingSlots))
        put("userFacingQuestion", response.userFacingQuestion)
        put("suggestionText", response.suggestionText)
        put("requiresConfirmation", response.requiresConfirmation)
        put("shouldExecuteImmediately", response.shouldExecuteImmediately)
        put("safetyNotes", response.safetyNotes)
    }

    fun requestFromJsonObject(json: JSONObject): LlmAgentRequest =
        LlmAgentRequest(
            originalText = json.optString("originalText"),
            normalizedText = json.optString("normalizedText"),
            locale = json.optString("locale", "es-AR"),
            agentState = AgentState.valueOf(json.optString("agentState", AgentState.IDLE.name)),
            externalApp = json.optString("externalApp").takeIf { it.isNotBlank() },
            memorySummary = json.optString("memorySummary"),
            knownSafeContacts = json.optJSONArray("knownSafeContacts").toStringList(),
            knownPlaces = json.optJSONArray("knownPlaces").toStringList(),
            activePendingTasks = json.optJSONArray("activePendingTasks").toStringList(),
            allowedIntents = json.optJSONArray("allowedIntents").toIntentList(),
            forbiddenActions = json.optJSONArray("forbiddenActions").toStringList()
        )

    fun responseFromJsonObject(json: JSONObject): LlmAgentResponse =
        LlmAgentResponse(
            intent = json.optString("intent").takeIf { it.isNotBlank() }?.let { AgentIntent.valueOf(it) },
            confidence = json.optDouble("confidence", 0.0).toFloat(),
            contactName = json.optString("contactName").takeIf { it.isNotBlank() },
            messageText = json.optString("messageText").takeIf { it.isNotBlank() },
            proposedMessage = json.optString("proposedMessage").takeIf { it.isNotBlank() },
            destination = json.optString("destination").takeIf { it.isNotBlank() },
            locationAlias = json.optString("locationAlias").takeIf { it.isNotBlank() },
            routineName = json.optString("routineName").takeIf { it.isNotBlank() },
            pendingTask = json.optString("pendingTask").takeIf { it.isNotBlank() },
            missingSlots = json.optJSONArray("missingSlots").toStringList(),
            userFacingQuestion = json.optString("userFacingQuestion").takeIf { it.isNotBlank() },
            suggestionText = json.optString("suggestionText").takeIf { it.isNotBlank() },
            requiresConfirmation = json.optBoolean("requiresConfirmation", false),
            shouldExecuteImmediately = json.optBoolean("shouldExecuteImmediately", false),
            safetyNotes = json.optString("safetyNotes").takeIf { it.isNotBlank() }
        )

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                add(optString(i))
            }
        }.filter { it.isNotBlank() }
    }

    private fun JSONArray?.toIntentList(): List<AgentIntent> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val value = optString(i)
                if (value.isNotBlank()) {
                    runCatching { add(AgentIntent.valueOf(value)) }
                }
            }
        }
    }
}

