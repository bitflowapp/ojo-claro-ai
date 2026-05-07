package com.ojoclaro.android.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class LlmHttpResponse(
    val statusCode: Int,
    val body: String
)

interface LlmAgentNetworkClient {
    suspend fun postJson(
        url: String,
        jsonBody: String,
        timeoutMillis: Long,
        headers: Map<String, String> = emptyMap()
    ): LlmHttpResponse
}

class HttpUrlConnectionLlmAgentNetworkClient : LlmAgentNetworkClient {
    override suspend fun postJson(
        url: String,
        jsonBody: String,
        timeoutMillis: Long,
        headers: Map<String, String>
    ): LlmHttpResponse = withContext(Dispatchers.IO) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = timeoutMillis.toInt().coerceAtLeast(1_000)
            readTimeout = timeoutMillis.toInt().coerceAtLeast(1_000)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            headers.forEach { (key, value) ->
                setRequestProperty(key, value)
            }
        }

        try {
            connection.outputStream.use { output ->
                output.write(jsonBody.toByteArray(Charsets.UTF_8))
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBody = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
            LlmHttpResponse(statusCode = status, body = responseBody)
        } finally {
            connection.disconnect()
        }
    }
}

