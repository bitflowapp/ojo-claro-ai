package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

class OpenAiProxyAgentInterpreter(
    private val config: LlmAgentClientConfig = LlmAgentClientConfig.fromBuildConfig(),
    private val networkClient: LlmAgentNetworkClient = HttpUrlConnectionLlmAgentNetworkClient()
) : LlmAgentInterpreter {

    override suspend fun interpret(request: LlmAgentRequest): LlmAgentResponse {
        if (!config.isConfigured()) {
            return unavailableResponse("Proxy deshabilitado.")
        }

        val payload = buildProxyPayload(request)
        return try {
            val httpResponse = withTimeout(config.timeoutMillis) {
                networkClient.postJson(
                    url = config.interpretUrl,
                    jsonBody = payload,
                    timeoutMillis = config.timeoutMillis
                )
            }
            if (httpResponse.body.isBlank()) {
                return unavailableResponse("HTTP ${httpResponse.statusCode}")
            }
            val proxyResponse = runCatching {
                LlmAgentJsonContract.responseFromJson(httpResponse.body)
            }.getOrElse {
                return unavailableResponse("JSON inválido del proxy.")
            }
            val coerced = LlmSafetyPolicy.coerce(proxyResponse)
            if (httpResponse.statusCode in 200..299) {
                coerced
            } else {
                coerced.copy(
                    safetyNotes = coerced.safetyNotes ?: "Proxy devolvió HTTP ${httpResponse.statusCode}"
                )
            }
        } catch (_: TimeoutCancellationException) {
            unavailableResponse("Timeout del proxy.")
        } catch (error: Throwable) {
            unavailableResponse(error.message ?: "Error del proxy.")
        }
    }

    private fun buildProxyPayload(request: LlmAgentRequest): String {
        val safeRequest = request.copy(
            originalText = request.originalText.take(config.maxInputChars),
            normalizedText = request.normalizedText.take(config.maxInputChars),
            memorySummary = request.memorySummary.take(config.maxMemoryChars)
        )
        return LlmAgentJsonContract.requestToJson(safeRequest)
    }

    private fun unavailableResponse(reason: String): LlmAgentResponse =
        LlmAgentResponse(
            intent = null,
            confidence = 0f,
            contactName = null,
            messageText = null,
            proposedMessage = null,
            destination = null,
            locationAlias = null,
            routineName = null,
            pendingTask = null,
            missingSlots = emptyList(),
            userFacingQuestion = "No uso la IA ahora. Probá decirlo más simple.",
            suggestionText = null,
            requiresConfirmation = false,
            shouldExecuteImmediately = false,
            safetyNotes = reason
        )
}
