package com.ojoclaro.android.llm

import java.net.HttpURLConnection
import java.net.URL

/**
 * Estado del proxy local de IA observado por el ultimo healthcheck.
 *
 *  - [Unknown]: nunca probamos o el chequeo aun no termino.
 *  - [Available]: `/health` devolvio 200 y `hasApiKey=true`.
 *  - [Disconnected]: `/health` no respondio, error de red, o devolvio mal.
 *
 * El tipo es chico a propósito: solo se usa para mostrar
 * "GPT mini: disponible" / "GPT mini: sin conexión" en el panel de
 * diagnóstico. Nunca expone modelo ni endpoint a la UI principal.
 */
sealed class ProxyHealthState {
    data object Unknown : ProxyHealthState()
    data class Available(val model: String) : ProxyHealthState()
    data object Disconnected : ProxyHealthState()
}

/**
 * Probe minimalista de `/health` del proxy local.
 *
 * No bloquea ni reintenta agresivamente. Si el proxy no responde, devolvemos
 * `Disconnected` y la UI degrada con copy humano. Nunca loguea API keys ni el
 * cuerpo HTTP completo (solo la primera linea ya parseada).
 */
class ProxyHealthProbe(
    private val baseUrl: String,
    private val timeoutMillis: Int = 1_500,
    private val openConnection: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    fun check(): ProxyHealthState {
        if (baseUrl.isBlank()) return ProxyHealthState.Disconnected
        val normalized = baseUrl.trimEnd('/')
        val url = try {
            URL("$normalized/health")
        } catch (_: Throwable) {
            return ProxyHealthState.Disconnected
        }
        return runCatching {
            val conn = openConnection(url).apply {
                requestMethod = "GET"
                connectTimeout = timeoutMillis
                readTimeout = timeoutMillis
                useCaches = false
                instanceFollowRedirects = false
                setRequestProperty("ngrok-skip-browser-warning", "true")
            }
            try {
                if (conn.responseCode != 200) return@runCatching ProxyHealthState.Disconnected
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseHealth(body)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { ProxyHealthState.Disconnected }
    }

    /**
     * Parser tolerante: extraemos `model` y `hasApiKey` del JSON sin levantar
     * dependencias. Si el shape cambia o no parsea, decimos Disconnected.
     */
    internal fun parseHealth(body: String): ProxyHealthState {
        val ok = OK_REGEX.find(body)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) ?: false
        val hasKey = HAS_API_KEY_REGEX.find(body)?.groupValues?.getOrNull(1)?.equals("true", ignoreCase = true) ?: false
        if (ok && body.contains("\"service\"") && body.contains("ojo-claro-backend")) {
            return ProxyHealthState.Available(model = "backend")
        }
        if (!hasKey) return ProxyHealthState.Disconnected
        val model = MODEL_REGEX.find(body)?.groupValues?.getOrNull(1).orEmpty()
        if (model.isBlank()) return ProxyHealthState.Disconnected
        return ProxyHealthState.Available(model = model)
    }

    companion object {
        private val MODEL_REGEX: Regex = Regex("\"model\"\\s*:\\s*\"([^\"]+)\"")
        private val HAS_API_KEY_REGEX: Regex = Regex("\"hasApiKey\"\\s*:\\s*(true|false)")
        private val OK_REGEX: Regex = Regex("\"ok\"\\s*:\\s*(true|false)")
    }
}

/**
 * Etiqueta amigable para el panel de diagnóstico. NUNCA muestra la URL del
 * proxy ni la API key. "configurado" / "sin configurar" cuando todavía no
 * hay healthcheck; "disponible" / "sin conexión" cuando ya hubo uno.
 */
fun describeProxyHealth(
    state: ProxyHealthState,
    assistantBaseUrlConfigured: Boolean
): String = when (state) {
    is ProxyHealthState.Available -> "GPT mini: disponible"
    ProxyHealthState.Disconnected -> "GPT mini: sin conexión"
    ProxyHealthState.Unknown -> if (assistantBaseUrlConfigured) "GPT mini: verificando" else "GPT mini: sin configurar"
}
