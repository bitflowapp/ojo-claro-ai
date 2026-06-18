package com.ojoclaro.android.outdoor

import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentNetworkClient
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

/**
 * Descripción de escena BAJO DEMANDA (Fase 4 de Outdoor Guidance).
 *
 * Contrato duro:
 *  - solo por acción explícita del usuario (explicitUserRequest=true);
 *  - aviso sonoro ANTES de capturar (lo emite el capturer real);
 *  - la imagen vive solo en memoria: base64 → request → descarte;
 *  - NUNCA se persiste ni se loguea;
 *  - la respuesta pasa SIEMPRE por OutdoorSafetyPolicy (lenguaje prudente,
 *    jamás "camino libre" / "es seguro").
 */
interface OutdoorSceneCapturer {
    /** Captura única en memoria (JPEG). null = fallo o timeout honesto. */
    suspend fun captureSingleJpeg(timeoutMillis: Long): ByteArray?

    fun hasCameraPermission(): Boolean
}

interface OutdoorSceneDescriber {
    suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome
}

/**
 * Implementación estándar: capturer inyectado (CameraX en runtime real) +
 * backend /api/vision ya existente para la descripción.
 */
class BackendSceneDescriber(
    private val capturer: OutdoorSceneCapturer,
    private val config: LlmAgentClientConfig,
    private val networkClient: LlmAgentNetworkClient,
    private val encodeBase64: (ByteArray) -> String,
    private val captureTimeoutMillis: Long = OutdoorBudgets.SCENE_CAPTURE_TIMEOUT_MILLIS,
    private val describeTimeoutMillis: Long = OutdoorBudgets.SCENE_DESCRIBE_TIMEOUT_MILLIS,
    /** Log debug seguro: solo flags y buckets, jamás contenido visual. */
    private val log: (String) -> Unit = {}
) : OutdoorSceneDescriber {

    private val visionUrl: String =
        if (config.normalizedBaseUrl.isBlank()) "" else "${config.normalizedBaseUrl}/api/vision"

    override suspend fun describeAhead(explicitUserRequest: Boolean): OutdoorSceneOutcome {
        // Nunca capturar silenciosamente ni por iniciativa del sistema.
        if (!explicitUserRequest) {
            return OutdoorSceneOutcome.Error("not_explicit_request")
        }
        if (!runCatching { capturer.hasCameraPermission() }.getOrDefault(false)) {
            return OutdoorSceneOutcome.CameraPermissionMissing
        }
        if (!config.isConfigured() || visionUrl.isBlank()) {
            return OutdoorSceneOutcome.Error("vision_backend_not_configured")
        }

        log("captureStarted=true")
        var jpeg: ByteArray? = withTimeoutOrNull(captureTimeoutMillis + 2_000L) {
            capturer.captureSingleJpeg(captureTimeoutMillis)
        }
        if (jpeg == null) {
            log("captureCompleted=false reason=timeout_or_null")
            return OutdoorSceneOutcome.Timeout
        }
        if (jpeg.isEmpty()) {
            log("captureCompleted=false reason=empty")
            return OutdoorSceneOutcome.CaptureFailed
        }
        log("captureCompleted=true jpegBytesBucket=${sizeBucket(jpeg.size)}")

        return try {
            val encoded = encodeBase64(jpeg)
            val payload = buildJsonObject {
                put("imageBase64", encoded)
                put("mimeType", "image/jpeg")
                put("prompt", SCENE_PROMPT)
            }.toString()
            // Liberar el buffer apenas está serializado: no se conserva.
            jpeg = null
            log(
                "imageBufferReleased=true base64CharsBucket=${sizeBucket(encoded.length)} " +
                    "visionRequestStarted=true"
            )

            val response = withTimeout(describeTimeoutMillis) {
                networkClient.postJson(
                    url = visionUrl,
                    jsonBody = payload,
                    timeoutMillis = describeTimeoutMillis,
                    headers = mapOf("ngrok-skip-browser-warning" to "true")
                )
            }
            log(
                "visionHttpStatus=${response.statusCode} " +
                    "visionResponsePresent=${response.body.isNotBlank()}"
            )
            if (response.statusCode !in 200..299 || response.body.isBlank()) {
                OutdoorSceneOutcome.Error("vision_http_${response.statusCode}")
            } else {
                val answer = parseAnswer(response.body)
                if (answer.isBlank()) {
                    OutdoorSceneOutcome.Error("vision_empty_answer")
                } else {
                    OutdoorSceneOutcome.Described(
                        OutdoorSafetyPolicy.sanitizeSceneDescription(answer)
                    )
                }
            }
        } catch (_: TimeoutCancellationException) {
            OutdoorSceneOutcome.Timeout
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (_: Throwable) {
            OutdoorSceneOutcome.Error("vision_network_error")
        }
    }

    private fun parseAnswer(body: String): String =
        runCatching {
            (Json.parseToJsonElement(body).jsonObject["answer"] as? JsonPrimitive)
                ?.content.orEmpty()
        }.getOrDefault("")

    /** Bucket de tamaño para logs (nunca el valor exacto ni el contenido). */
    private fun sizeBucket(size: Int): String = when {
        size < 100_000 -> "lt100k"
        size < 500_000 -> "lt500k"
        size < 2_000_000 -> "lt2m"
        else -> "gte2m"
    }

    companion object {
        /**
         * Prompt prudente fijo: pide descripción breve y prohíbe afirmaciones
         * de seguridad. La política local re-sanitiza igual (defensa doble).
         */
        const val SCENE_PROMPT: String =
            "Describí brevemente (máximo 3 frases) lo que se ve adelante para una persona ciega " +
                "que camina por la vereda. Mencioná posibles obstáculos con lenguaje prudente " +
                "('posible obstáculo', 'posible desnivel'). PROHIBIDO decir que el camino está " +
                "libre, que es seguro cruzar o avanzar, o dar órdenes de movimiento. No inventes " +
                "distancias precisas. Español rioplatense."
    }
}

/**
 * Prototipo EXPERIMENTAL de detección local de peligros (Fase 5).
 *
 * DESACTIVADO POR DEFECTO ([ENABLED_BY_DEFAULT]=false) y NO integrado a la
 * guía productiva. NO es una función de seguridad y no detecta pozos de
 * forma confiable. Existe solo para preparar la interfaz de un modelo
 * TensorFlow Lite propio futuro, alimentado por frames de CameraX con
 * ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST. Nunca envía frames a GPT.
 */
object OutdoorHazardPrototype {

    const val ENABLED_BY_DEFAULT: Boolean = false

    data class FrameStats(
        val width: Int,
        val height: Int,
        /** Luma promedio del tercio central inferior (zona de marcha). */
        val centerBottomLuma: Int,
        /** Luma promedio del resto del frame. */
        val surroundLuma: Int
    )

    data class HazardObservation(
        val possibleObstacleAhead: Boolean,
        /** Siempre lenguaje prudente; nunca una afirmación de seguridad. */
        val cautiousText: String?
    )

    /** Interfaz lista para un clasificador TFLite local futuro. */
    fun interface FrameClassifier {
        fun classify(stats: FrameStats): HazardObservation
    }

    /**
     * Heurística mínima de contraste: una masa oscura/clara dominante en la
     * zona central de marcha PUEDE ser un obstáculo. Solo dice "posible".
     */
    val defaultClassifier: FrameClassifier = FrameClassifier { stats ->
        val contrast = kotlin.math.abs(stats.centerBottomLuma - stats.surroundLuma)
        if (contrast >= 60) {
            HazardObservation(
                possibleObstacleAhead = true,
                cautiousText = "Detecté un posible obstáculo en la zona central. " +
                    OutdoorSafetyPolicy.SCENE_DISCLAIMER
            )
        } else {
            HazardObservation(possibleObstacleAhead = false, cautiousText = null)
        }
    }

    fun analyze(
        stats: FrameStats,
        enabled: Boolean = ENABLED_BY_DEFAULT,
        classifier: FrameClassifier = defaultClassifier
    ): HazardObservation {
        if (!enabled) {
            return HazardObservation(possibleObstacleAhead = false, cautiousText = null)
        }
        val observation = classifier.classify(stats)
        val text = observation.cautiousText ?: return observation
        // Defensa final: ni el prototipo puede emitir afirmaciones de seguridad.
        return observation.copy(cautiousText = OutdoorSafetyPolicy.sanitizeForSpeech(text))
    }
}
