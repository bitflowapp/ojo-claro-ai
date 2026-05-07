package com.ojoclaro.android.llm

import com.ojoclaro.android.BuildConfig

data class LlmAgentClientConfig(
    val baseUrl: String,
    val model: String = DEFAULT_MODEL,
    val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS,
    val maxInputChars: Int = DEFAULT_MAX_INPUT_CHARS,
    val maxMemoryChars: Int = DEFAULT_MAX_MEMORY_CHARS,
    val maxRetries: Int = DEFAULT_MAX_RETRIES,
    val enabled: Boolean = baseUrl.isNotBlank()
) {
    val normalizedBaseUrl: String = baseUrl.trimEnd('/')
    val interpretUrl: String = if (normalizedBaseUrl.isBlank()) "" else "$normalizedBaseUrl/v1/interpret"

    fun isConfigured(): Boolean = enabled && normalizedBaseUrl.startsWith("http")

    companion object {
        const val DEFAULT_MODEL = "gpt-5.4-mini"
        const val DEFAULT_TIMEOUT_MILLIS = 12_000L
        const val DEFAULT_MAX_INPUT_CHARS = 1_200
        const val DEFAULT_MAX_MEMORY_CHARS = 800
        const val DEFAULT_MAX_RETRIES = 1

        fun fromBuildConfig(baseUrl: String = BuildConfig.ASSISTANT_BASE_URL): LlmAgentClientConfig =
            LlmAgentClientConfig(
                baseUrl = baseUrl,
                model = DEFAULT_MODEL,
                timeoutMillis = DEFAULT_TIMEOUT_MILLIS,
                maxInputChars = DEFAULT_MAX_INPUT_CHARS,
                maxMemoryChars = DEFAULT_MAX_MEMORY_CHARS,
                maxRetries = DEFAULT_MAX_RETRIES,
                enabled = baseUrl.isNotBlank()
            )
    }
}

