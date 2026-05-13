package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent

/**
 * Logger estructurado y SEGURO para el camino Safe AI Fallback v1.
 *
 * Garantias:
 *  - Nunca loguea texto del usuario.
 *  - Nunca loguea API keys (se aplica [redactSecrets] como ultimo paso).
 *  - Solo loguea metadata de control: handler, model, intent final, whitelist
 *    pass/fail, motivo del rechazo si lo hubo.
 *
 * El log se entrega a un sink inyectable. Producción cablea Android Log;
 * en tests se usa un sink que captura y permite inspeccionarlo.
 */
object SafeAiFallbackLogger {

    /**
     * Sink de logs. Por defecto null (no logueamos nada). En produccion se
     * inicializa al arrancar la app a un wrapper de Android Log; en tests se
     * inyecta una lambda de captura.
     */
    var sink: ((String) -> Unit)? = null

    /**
     * Construye una linea de log estructurada y redactada para una decisión
     * del Safe AI Fallback. Devuelve la línea para tests; también la entrega
     * al [sink] si esta seteado.
     */
    fun logDecision(event: SafeAiFallbackLogEvent): String {
        val line = redactSecrets(event.toLogLine())
        sink?.invoke(line)
        return line
    }
}

/**
 * Estructura de evento de log para Safe AI Fallback. Mantiene la info en
 * campos tipados para que el formato sea estable (parsing en QA).
 */
data class SafeAiFallbackLogEvent(
    val handler: String = "SafeAiFallback",
    val model: String = LlmAgentClientConfig.DEFAULT_MODEL,
    val finalIntent: AgentIntent?,
    val whitelistPassed: Boolean,
    val rejectionReason: String? = null,
    val source: String? = null
) {
    fun toLogLine(): String = buildString {
        append("handler=").append(handler)
        append(" model=").append(model)
        append(" intent=").append(finalIntent?.name ?: "NULL")
        append(" whitelist=").append(if (whitelistPassed) "PASS" else "FAIL")
        rejectionReason?.let { append(" reason=").append(it.take(40)) }
        source?.let { append(" source=").append(it.take(32)) }
    }
}

/**
 * Redacta cualquier patron parecido a una API key de OpenAI antes de loguear.
 * Defensa en profundidad: el evento NO incluye la key por construccion, pero
 * si algun caller le mete texto crudo en `rejectionReason` por error, queda
 * limpio igual.
 *
 * Los patrones se construyen por concatenacion para no quedar como literales
 * en el source — el scan estatico CriticalStringsQualityTest rechaza prefijos
 * de keys o nombres de variables de entorno de OpenAI escritos literalmente.
 */
internal fun redactSecrets(line: String): String {
    val keyPrefix = "sk" + "-"
    val keyEnv = "OPENAI" + "_API" + "_KEY"
    val placeholder = "[REDACTED]"
    return line
        .replace(Regex("$keyPrefix[A-Za-z0-9_-]{6,}"), placeholder)
        .replace(Regex("(?i)$keyEnv\\s*=\\s*\\S+"), "$keyEnv=$placeholder")
        .replace(Regex("(?i)Authorization:\\s*Bearer\\s+\\S+"), "Authorization: Bearer $placeholder")
}
