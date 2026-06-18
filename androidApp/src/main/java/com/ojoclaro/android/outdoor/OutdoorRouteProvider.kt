package com.ojoclaro.android.outdoor

import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentNetworkClient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Abstracción de rutas peatonales. La clave de Google vive SOLO en el
 * backend ([BackendOutdoorRouteProvider] llama a /outdoor/route); el APK
 * jamás incluye claves de Maps.
 */
interface OutdoorRouteProvider {
    suspend fun walkingRoute(
        originLatitude: Double,
        originLongitude: Double,
        destination: String
    ): OutdoorRouteOutcome

    /**
     * V1.4 — calle/barrio aproximados para "¿dónde estoy?". null si el
     * backend no puede resolverlo: el caller cae al texto honesto.
     * El label nunca se loguea.
     */
    suspend fun reverseLabel(latitude: Double, longitude: Double): String? = null
}

class BackendOutdoorRouteProvider(
    private val config: LlmAgentClientConfig,
    private val networkClient: LlmAgentNetworkClient,
    private val timeoutMillis: Long = 15_000L
) : OutdoorRouteProvider {

    private val routeUrl: String =
        if (config.normalizedBaseUrl.isBlank()) "" else "${config.normalizedBaseUrl}/outdoor/route"

    override suspend fun walkingRoute(
        originLatitude: Double,
        originLongitude: Double,
        destination: String
    ): OutdoorRouteOutcome {
        if (!config.isConfigured() || routeUrl.isBlank()) {
            return OutdoorRouteOutcome.Unconfigured
        }
        val payload = buildJsonObject {
            put("origin_latitude", originLatitude)
            put("origin_longitude", originLongitude)
            put("destination", destination.take(200))
        }.toString()

        return try {
            val response = withTimeout(timeoutMillis) {
                networkClient.postJson(
                    url = routeUrl,
                    jsonBody = payload,
                    timeoutMillis = timeoutMillis,
                    headers = mapOf("ngrok-skip-browser-warning" to "true")
                )
            }
            if (response.statusCode !in 200..299 || response.body.isBlank()) {
                OutdoorRouteOutcome.Error("route_http_${response.statusCode}")
            } else {
                parseRoute(response.body)
            }
        } catch (_: TimeoutCancellationException) {
            OutdoorRouteOutcome.Error("route_timeout")
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            OutdoorRouteOutcome.Error("route_network_error")
        }
    }

    override suspend fun reverseLabel(latitude: Double, longitude: Double): String? {
        if (!config.isConfigured() || config.normalizedBaseUrl.isBlank()) return null
        val payload = buildJsonObject {
            put("latitude", latitude)
            put("longitude", longitude)
        }.toString()
        return try {
            val response = withTimeout(REVERSE_TIMEOUT_MILLIS) {
                networkClient.postJson(
                    url = "${config.normalizedBaseUrl}/outdoor/reverse",
                    jsonBody = payload,
                    timeoutMillis = REVERSE_TIMEOUT_MILLIS,
                    headers = mapOf("ngrok-skip-browser-warning" to "true")
                )
            }
            if (response.statusCode !in 200..299 || response.body.isBlank()) null
            else parseReverseLabel(response.body)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            null
        }
    }

    internal fun parseReverseLabel(body: String): String? {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return null
        if ((root["ok"] as? JsonPrimitive)?.booleanOrNull != true) return null
        // JsonNull es JsonPrimitive y su content es la cadena "null".
        val labelPrimitive = (root["label"] as? JsonPrimitive)
            ?.takeIf { it.isString }
        return labelPrimitive?.content
            ?.trim()
            ?.take(120)
            ?.takeIf { it.isNotBlank() }
    }

    internal fun parseRoute(body: String): OutdoorRouteOutcome {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }.getOrNull()
            ?: return OutdoorRouteOutcome.Error("route_invalid_json")

        val configured = (root["configured"] as? JsonPrimitive)?.booleanOrNull ?: true
        if (!configured) return OutdoorRouteOutcome.Unconfigured

        val status = (root["status"] as? JsonPrimitive)?.content.orEmpty()
        if (status == "NOT_FOUND") return OutdoorRouteOutcome.NotFound
        // V1.10.2 — destino real pero fuera de alcance peatonal (ORS 2004).
        if (status == "TOO_FAR") return OutdoorRouteOutcome.TooFar
        if (status != "ROUTE") {
            val code = (root["error_code"] as? JsonPrimitive)?.content ?: "route_${status.lowercase()}"
            return OutdoorRouteOutcome.Error(code)
        }

        val steps = (root["steps"] as? JsonArray).orEmpty().mapNotNull { element ->
            val step = element as? JsonObject ?: return@mapNotNull null
            val instruction = (step["instruction"] as? JsonPrimitive)?.content?.trim().orEmpty()
            val distance = (step["distance_meters"] as? JsonPrimitive)?.intOrNull ?: 0
            if (instruction.isBlank()) null else OutdoorRouteStep(instruction, distance)
        }
        if (steps.isEmpty()) return OutdoorRouteOutcome.NotFound

        return OutdoorRouteOutcome.Route(
            OutdoorRouteData(
                destinationName = (root["destination_name"] as? JsonPrimitive)?.content
                    ?.takeIf { it.isNotBlank() } ?: "tu destino",
                totalDistanceMeters = (root["total_distance_meters"] as? JsonPrimitive)?.intOrNull ?: 0,
                totalDurationSeconds = (root["total_duration_seconds"] as? JsonPrimitive)?.intOrNull ?: 0,
                steps = steps
            )
        )
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray(emptyList())

    private companion object {
        /** Reverse geocode corto: si tarda, la respuesta honesta no espera. */
        private const val REVERSE_TIMEOUT_MILLIS = 6_000L
    }
}
