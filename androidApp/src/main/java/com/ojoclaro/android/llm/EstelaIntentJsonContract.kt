package com.ojoclaro.android.llm

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

object EstelaIntentJsonContract {

    fun proxyRequestToJson(
        request: EstelaIntentRequest,
        model: String = EstelaIntentConfig.MODEL
    ): String = buildJsonObject {
        put("model", JsonPrimitive(model))
        put("system_prompt_id", JsonPrimitive(EstelaIntentConfig.SYSTEM_PROMPT_ID))
        put("input", request.toJsonObject())
    }.toString()

    fun inputToJsonObject(request: EstelaIntentRequest): JsonObject =
        request.toJsonObject()

    private fun EstelaIntentRequest.toJsonObject(): JsonObject = buildJsonObject {
        put("user_text", JsonPrimitive(userText))
        put("conversation_state", JsonPrimitive(conversationState))
        pendingAction?.let { pending ->
            put(
                "pending_action",
                buildJsonObject {
                    put("intent", JsonPrimitive(pending.intent))
                    put("params", pending.params.toJsonObject())
                }
            )
        } ?: put("pending_action", JsonNull)
        put("installed_apps", installedApps.toJsonArray())
        put("memory_contacts", memoryContacts.toJsonArray())
        activeApp?.let { put("active_app", JsonPrimitive(it)) } ?: put("active_app", JsonNull)
        put(
            "permissions_granted",
            JsonObject(permissionsGranted.mapValues { (_, value) -> JsonPrimitive(value) })
        )
    }

    private fun Iterable<String>.toJsonArray(): JsonArray =
        JsonArray(map { JsonPrimitive(it) })

    private fun Map<String, Any?>.toJsonObject(): JsonObject =
        JsonObject(entries.associate { (key, value) -> key to value.toJsonElement() })

    private fun Any?.toJsonElement(): JsonElement =
        when (this) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is String -> JsonPrimitive(this)
            is Map<*, *> -> JsonObject(
                entries.associate { entry ->
                    entry.key.toString() to entry.value.toJsonElement()
                }
            )
            is Iterable<*> -> JsonArray(map { it.toJsonElement() })
            else -> JsonPrimitive(toString())
        }
}
