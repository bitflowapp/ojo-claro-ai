package com.ojoclaro.android.llm

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * V1.7 — cliente de conversación libre (POST /conversation).
 *
 * Baja prioridad por contrato: el caller SOLO llama acá cuando la frase no
 * es comando local ni pending action. Falla cerrado a null para que el
 * caller hable un fallback honesto.
 */
class EstelaConversationClient(
    private val config: LlmAgentClientConfig,
    private val networkClient: LlmAgentNetworkClient,
    private val timeoutMillis: Long = 12_000L
) {

    suspend fun converse(
        userText: String,
        shortMemory: List<String>,
        activeApp: String?,
        routeActive: Boolean,
        whatsappPending: Boolean
    ): String? {
        if (!config.isConfigured() || config.normalizedBaseUrl.isBlank()) return null
        val payload = buildJsonObject {
            // Barrera de egreso: sanitizar SIEMPRE en el borde, no depender de que
            // el caller lo haya hecho (defensa en profundidad contra H1).
            put("user_text", LlmInputSanitizer.sanitize(userText).take(300))
            put(
                "conversation_state",
                buildJsonObject {
                    put("active_app", activeApp?.take(40) ?: "")
                    put("route_active", routeActive)
                    put("whatsapp_pending", whatsappPending)
                    put(
                        "short_memory",
                        buildJsonArray { shortMemory.takeLast(5).forEach { add(it.take(160)) } }
                    )
                }
            )
        }.toString()

        return try {
            val response = withTimeout(timeoutMillis) {
                networkClient.postJson(
                    url = "${config.normalizedBaseUrl}/conversation",
                    jsonBody = payload,
                    timeoutMillis = timeoutMillis
                )
            }
            if (response.statusCode !in 200..299 || response.body.isBlank()) null
            else parseReply(response.body)
        } catch (_: TimeoutCancellationException) {
            null
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    internal fun parseReply(body: String): String? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true) return null
        val reply = (root["reply"] as? JsonPrimitive)
            ?.takeIf { it !is JsonNull && it.isString }
            ?.content?.trim()?.take(320)
        return reply?.takeIf { it.isNotBlank() }
    }
}
