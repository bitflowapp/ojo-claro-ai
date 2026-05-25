package ai.ojoclaro.adapter

import ai.ojoclaro.consent.ConsentPhraseResolver
import ai.ojoclaro.router.IntentRouteRequest
import ai.ojoclaro.router.IntentRouteResult
import ai.ojoclaro.router.IntentRouter
import com.ojoclaro.android.agent.AgentConversationManager
import com.ojoclaro.android.agent.AgentOutcome
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class LlmIntentAdapter(
    private val intentRouter: IntentRouter,
    private val conversationManager: AgentConversationManager
) {
    fun adapt(rawJson: String): LlmIntentAdapterResult {
        val parsed = rawJson.toMapOrNull()
            ?: return LlmIntentAdapterResult.SafeFallback(reason = "malformed_json")

        val params = parsed["params"].toStringKeyMap()
        val rawText = parsed.stringOrNull("raw_text")
        val intent = parsed.stringOrNull("intent")
        val confidence = parsed.doubleOrDefault("confidence")
        val safetyLevel = parsed.stringOrNull("safety_level") ?: SafetyLevelBlocked
        val voiceResponseTemplate = parsed.stringOrNull("voice_response_template")
        val literalVoiceResponse = parsed.stringOrNull("voice_response")

        if (intent == SlotFillIntent) {
            if (confidence < MinimumConfidence) {
                return routeParsedIntent(
                    intent = UnknownIntent,
                    confidence = confidence,
                    params = params,
                    safetyLevel = safetyLevel,
                    voiceResponse = literalVoiceResponse,
                    voiceResponseTemplate = voiceResponseTemplate,
                    rawText = rawText
                )
            }

            if (parsed.stringOrNull("safety_level") == null ||
                safetyLevel == SafetyLevelBlocked ||
                safetyLevel == SafetyLevelRequiresConfirm ||
                safetyLevel !in SlotFillAllowedSafetyLevels
            ) {
                return LlmIntentAdapterResult.SafeFallback(
                    reason = "unsafe_slot_fill",
                    intent = intent,
                    safetyLevel = safetyLevel,
                    rawText = rawText,
                    voiceResponse = literalVoiceResponse
                )
            }

            return handleSlotFill(
                params = params,
                safetyLevel = safetyLevel,
                rawText = rawText,
                voiceResponse = literalVoiceResponse
            )
        }

        val voiceResponseResolution = resolveVoiceResponse(
            template = voiceResponseTemplate,
            params = params,
            safetyLevel = safetyLevel,
            fallbackVoiceResponse = literalVoiceResponse
        )
        if (voiceResponseResolution.isBlockedFallback) {
            return LlmIntentAdapterResult.SafeFallback(
                reason = "unknown_voice_response_template",
                intent = intent,
                safetyLevel = safetyLevel,
                rawText = rawText,
                voiceResponse = literalVoiceResponse
            )
        }
        val voiceResponse = voiceResponseResolution.voiceResponse

        val routedIntent = when {
            intent.isNullOrBlank() -> UnknownIntent
            confidence < MinimumConfidence -> UnknownIntent
            else -> intent
        }
        return routeParsedIntent(
            intent = routedIntent,
            confidence = confidence,
            params = params,
            safetyLevel = safetyLevel,
            voiceResponse = voiceResponse,
            voiceResponseTemplate = voiceResponseTemplate,
            rawText = rawText
        )
    }

    private fun handleSlotFill(
        params: Map<String, Any?>,
        safetyLevel: String,
        rawText: String?,
        voiceResponse: String?
    ): LlmIntentAdapterResult {
        val slot = params.stringOrNull("slot")
            ?: return LlmIntentAdapterResult.SafeFallback(
                reason = "missing_slot",
                intent = SlotFillIntent,
                safetyLevel = safetyLevel,
                rawText = rawText,
                voiceResponse = voiceResponse
            )
        val value = params.stringOrNull("value")
            ?: return LlmIntentAdapterResult.SafeFallback(
                reason = "missing_value",
                intent = SlotFillIntent,
                safetyLevel = safetyLevel,
                rawText = rawText,
                voiceResponse = voiceResponse
            )

        return LlmIntentAdapterResult.SlotFilled(
            slot = slot,
            value = value,
            outcome = conversationManager.handleSlotFill(slot, value)
        )
    }

    private fun routeParsedIntent(
        intent: String,
        confidence: Double,
        params: Map<String, Any?>,
        safetyLevel: String,
        voiceResponse: String?,
        voiceResponseTemplate: String?,
        rawText: String?
    ): LlmIntentAdapterResult {
        val routedParams = rawText
            ?.let { text -> params.withDefaultRawText(text) }
            ?: params
        val routedJson = mapOf(
            "intent" to intent,
            "confidence" to confidence,
            "params" to routedParams,
            "safety_level" to safetyLevel,
            "voice_response" to voiceResponse,
            "voice_response_template" to voiceResponseTemplate,
            "raw_text" to rawText.orEmpty()
        )

        return LlmIntentAdapterResult.Routed(
            request = IntentRouteRequest.from(routedJson),
            routeResult = intentRouter.routeAndCollect(routedJson)
        )
    }

    private fun resolveVoiceResponse(
        template: String?,
        params: Map<String, Any?>,
        safetyLevel: String,
        fallbackVoiceResponse: String?
    ): VoiceResponseResolution {
        if (template == null) return VoiceResponseResolution(fallbackVoiceResponse)

        val resolved = ConsentPhraseResolver.resolve(template, params)
        if (resolved != null) return VoiceResponseResolution(resolved)

        return when (safetyLevel) {
            SafetyLevelAllowSafe -> VoiceResponseResolution(fallbackVoiceResponse)
            else -> VoiceResponseResolution(voiceResponse = null, isBlockedFallback = true)
        }
    }

    private fun Map<String, Any?>.withDefaultRawText(rawText: String): Map<String, Any?> =
        if ("raw_text" in this) this else this + ("raw_text" to rawText)

    private fun Map<String, Any?>.doubleOrDefault(key: String): Double =
        when (val value = this[key]) {
            is Number -> value.toDouble()
            is String -> value.toDoubleOrNull() ?: 0.0
            else -> 0.0
        }

    private companion object {
        const val MinimumConfidence = 0.6
        const val SafetyLevelAllowSafe = "allow_safe"
        const val SafetyLevelPrepareOnly = "prepare_only"
        const val SafetyLevelRequiresConfirm = "requires_confirm"
        const val SafetyLevelBlocked = "blocked_sensitive"
        const val SlotFillIntent = "slot_fill"
        const val UnknownIntent = "unknown"
        val SlotFillAllowedSafetyLevels = setOf(SafetyLevelAllowSafe, SafetyLevelPrepareOnly)
    }
}

private data class VoiceResponseResolution(
    val voiceResponse: String?,
    val isBlockedFallback: Boolean = false
)

sealed class LlmIntentAdapterResult {
    data class Routed(
        val request: IntentRouteRequest,
        val routeResult: IntentRouteResult
    ) : LlmIntentAdapterResult()

    data class SlotFilled(
        val slot: String,
        val value: String,
        val outcome: AgentOutcome
    ) : LlmIntentAdapterResult()

    data class SafeFallback(
        val reason: String,
        val intent: String? = null,
        val safetyLevel: String = "blocked_sensitive",
        val rawText: String? = null,
        val voiceResponse: String? = null
    ) : LlmIntentAdapterResult()
}

private val LlmIntentJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private fun String.toMapOrNull(): Map<String, Any?>? =
    runCatching {
        val value = LlmIntentJson.parseToJsonElement(this).toAnyValue()
        if (value is Map<*, *>) value.toStringKeyMap() else null
    }
        .getOrNull()

private fun JsonElement.toAnyValue(): Any? =
    when (this) {
        is JsonNull -> null
        is JsonObject -> entries.associate { (key, value) -> key to value.toAnyValue() }
        is JsonArray -> map { element -> element.toAnyValue() }
        is JsonPrimitive -> toPrimitiveValue()
    }

private fun JsonPrimitive.toPrimitiveValue(): Any? {
    if (isString) return content
    booleanOrNull?.let { return it }
    longOrNull?.let { value ->
        return if (value in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) {
            value.toInt()
        } else {
            value
        }
    }
    doubleOrNull?.let { return it }
    return contentOrNull
}

private fun Any?.toStringKeyMap(): Map<String, Any?> =
    (this as? Map<*, *>)
        ?.entries
        ?.associate { entry -> entry.key.toString() to entry.value }
        ?: emptyMap()

private fun Map<String, Any?>.stringOrNull(key: String): String? =
    (this[key] as? String)?.trim()?.takeIf { value -> value.isNotEmpty() }
