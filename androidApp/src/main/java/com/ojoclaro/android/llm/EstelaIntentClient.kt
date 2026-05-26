package com.ojoclaro.android.llm

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

interface EstelaIntentClient {
    suspend fun classifyIntent(request: EstelaIntentRequest): Result<String>
}

class HttpEstelaIntentClient(
    private val config: LlmAgentClientConfig = LlmAgentClientConfig.fromBuildConfig(),
    private val networkClient: LlmAgentNetworkClient = HttpUrlConnectionLlmAgentNetworkClient()
) : EstelaIntentClient {

    override suspend fun classifyIntent(request: EstelaIntentRequest): Result<String> {
        if (!config.isConfigured()) {
            return Result.failure(EstelaIntentNetworkException("intent_proxy_not_configured"))
        }

        val payload = EstelaIntentJsonContract.proxyRequestToJson(
            request = request,
            model = config.model
        )

        return try {
            val response = withTimeout(config.timeoutMillis) {
                networkClient.postJson(
                    url = config.intentUrl,
                    jsonBody = payload,
                    timeoutMillis = config.timeoutMillis
                )
            }
            val body = response.body.trim()
            if (response.statusCode !in 200..299 || body.isBlank()) {
                Result.failure(
                    EstelaIntentNetworkException("intent_proxy_http_${response.statusCode}")
                )
            } else {
                Result.success(body)
            }
        } catch (_: TimeoutCancellationException) {
            Result.failure(EstelaIntentTimeoutException())
        } catch (error: Throwable) {
            Result.failure(
                EstelaIntentNetworkException(error.message ?: "intent_proxy_request_failed")
            )
        }
    }
}

open class EstelaIntentNetworkException(message: String) : RuntimeException(message)

class EstelaIntentTimeoutException : EstelaIntentNetworkException("intent_proxy_timeout")
