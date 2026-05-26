package com.ojoclaro.android.llm

import ai.ojoclaro.adapter.LlmIntentAdapter
import ai.ojoclaro.adapter.LlmIntentAdapterResult
import ai.ojoclaro.router.IntentRouteResult
import com.ojoclaro.android.agent.AgentConversationManager

class EstelaIntentEngine(
    private val client: EstelaIntentClient,
    private val requestBuilder: EstelaIntentRequestBuilder,
    private val conversationManager: AgentConversationManager,
    private val adapter: LlmIntentAdapter
) {

    suspend fun classifyAndRoute(userText: String): EstelaIntentEngineResult {
        val request = requestBuilder.build(
            userText = userText,
            conversationManager = conversationManager
        )
        val rawJson = client.classifyIntent(request).getOrElse { error ->
            return EstelaIntentEngineResult(
                request = request,
                rawJson = null,
                adapterResult = null,
                spokenText = fallbackTextFor(error),
                fallbackReason = error.message ?: "intent_client_failure"
            )
        }

        val adapterResult = adapter.adapt(rawJson)
        return EstelaIntentEngineResult(
            request = request,
            rawJson = rawJson,
            adapterResult = adapterResult,
            spokenText = adapterResult.spokenText(),
            fallbackReason = (adapterResult as? LlmIntentAdapterResult.SafeFallback)?.reason
        )
    }

    private fun fallbackTextFor(error: Throwable): String =
        if (error is EstelaIntentTimeoutException) {
            EstelaIntentFallbackPhrases.TIMEOUT
        } else {
            EstelaIntentFallbackPhrases.NETWORK_ERROR
        }

    private fun LlmIntentAdapterResult.spokenText(): String =
        when (this) {
            is LlmIntentAdapterResult.Routed ->
                request.voiceResponse
                    ?: routeResult.spokenTextFallback()
                    ?: EstelaIntentFallbackPhrases.SAFE_FALLBACK

            is LlmIntentAdapterResult.SlotFilled ->
                outcome.spokenText.ifBlank { EstelaIntentFallbackPhrases.SAFE_FALLBACK }

            is LlmIntentAdapterResult.SafeFallback ->
                voiceResponse ?: EstelaIntentFallbackPhrases.SAFE_FALLBACK
        }

    private fun IntentRouteResult.spokenTextFallback(): String? =
        when (this) {
            is IntentRouteResult.Conversation -> when (action.type) {
                ai.ojoclaro.router.ConversationActionType.UNKNOWN ->
                    "No te entendí bien. ¿Podés repetirlo?"
                ai.ojoclaro.router.ConversationActionType.BLOCKED ->
                    "No puedo hacer esa acción."
                else -> null
            }
            IntentRouteResult.InvalidConfirmation ->
                "Para avanzar, necesito que digas confirmar."
            else -> null
        }
}

data class EstelaIntentEngineResult(
    val request: EstelaIntentRequest,
    val rawJson: String?,
    val adapterResult: LlmIntentAdapterResult?,
    val spokenText: String,
    val fallbackReason: String? = null
)

object EstelaIntentFallbackPhrases {
    const val NETWORK_ERROR: String =
        "No pude conectarme para interpretar eso. Probá de nuevo en un momento."
    const val TIMEOUT: String =
        "No pude procesarlo a tiempo. ¿Podés repetirlo?"
    const val SAFE_FALLBACK: String =
        "No lo pude procesar con seguridad. ¿Podés repetirlo?"
}
