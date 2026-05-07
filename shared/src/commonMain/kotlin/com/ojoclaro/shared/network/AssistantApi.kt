package com.ojoclaro.shared.network

import com.ojoclaro.shared.model.AssistRequest
import com.ojoclaro.shared.model.AssistResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class AssistantApi private constructor(
    baseUrl: String,
    private val client: HttpClient = defaultClient()
) {
    constructor(baseUrl: String) : this(baseUrl, defaultClient())

    private val normalizedBaseUrl = baseUrl.trimEnd('/')

    suspend fun assist(request: AssistRequest): AssistResponse {
        return client.post("$normalizedBaseUrl/api/v1/assist") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.body()
    }

    companion object {
        private fun defaultClient(): HttpClient {
            return HttpClient {
                install(ContentNegotiation) {
                    json(Json {
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
                install(HttpTimeout) {
                    requestTimeoutMillis = 20_000
                    connectTimeoutMillis = 10_000
                    socketTimeoutMillis = 20_000
                }
            }
        }
    }
}
