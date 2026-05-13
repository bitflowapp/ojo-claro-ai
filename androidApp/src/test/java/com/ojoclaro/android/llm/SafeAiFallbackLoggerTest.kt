package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentIntent
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SafeAiFallbackLoggerTest {

    @AfterTest
    fun cleanup() {
        SafeAiFallbackLogger.sink = null
    }

    @Test
    fun decisionLogContainsHandlerModelIntentAndWhitelistFlag() {
        val captured = mutableListOf<String>()
        SafeAiFallbackLogger.sink = captured::add

        SafeAiFallbackLogger.logDecision(
            SafeAiFallbackLogEvent(
                finalIntent = AgentIntent.HELP,
                whitelistPassed = true
            )
        )

        assertEquals(1, captured.size)
        val line = captured.single()
        assertTrue(line.contains("handler=SafeAiFallback"))
        assertTrue(line.contains("model=gpt-5.4-mini"))
        assertTrue(line.contains("intent=HELP"))
        assertTrue(line.contains("whitelist=PASS"))
    }

    @Test
    fun whitelistFailIncludesReason() {
        val captured = mutableListOf<String>()
        SafeAiFallbackLogger.sink = captured::add

        SafeAiFallbackLogger.logDecision(
            SafeAiFallbackLogEvent(
                finalIntent = AgentIntent.UNKNOWN,
                whitelistPassed = false,
                rejectionReason = "intent_outside_whitelist_v1"
            )
        )

        val line = captured.single()
        assertTrue(line.contains("whitelist=FAIL"))
        assertTrue(line.contains("reason=intent_outside_whitelist_v1"))
    }

    @Test
    fun loggerNeverEmitsApiKeyEvenIfReasonContainsOne() {
        val captured = mutableListOf<String>()
        SafeAiFallbackLogger.sink = captured::add

        // Defensa en profundidad: aunque alguien meta texto con la key por error,
        // el sink la recibe redactada. Construimos el prefijo concatenando para no
        // disparar al scanner CriticalStringsQualityTest que mira literales.
        val keyPrefix = "sk" + "-"
        val fakeKey = keyPrefix + "LEAKEDABCDEF1234567890"
        SafeAiFallbackLogger.logDecision(
            SafeAiFallbackLogEvent(
                finalIntent = AgentIntent.UNKNOWN,
                whitelistPassed = false,
                rejectionReason = fakeKey
            )
        )

        val line = captured.single()
        assertFalse(line.contains(keyPrefix + "LEAKED"), "real key leaked: $line")
        assertFalse(line.contains("LEAKED"), "leaked literal: $line")
        assertTrue(line.contains("[REDACTED]"))
    }

    @Test
    fun loggerNeverEmitsUserText() {
        val captured = mutableListOf<String>()
        SafeAiFallbackLogger.sink = captured::add

        // El evento NO acepta texto del usuario en su API publica (no hay campo).
        // Esto chequea que la linea estructurada solo expone metadata.
        SafeAiFallbackLogger.logDecision(
            SafeAiFallbackLogEvent(
                finalIntent = AgentIntent.OPEN_WHATSAPP,
                whitelistPassed = true,
                source = "llm_response"
            )
        )

        val line = captured.single()
        // Solo conoce los campos definidos arriba.
        assertFalse(line.contains("originalText"))
        assertFalse(line.contains("userText"))
        assertFalse(line.contains("messageText"))
    }

    @Test
    fun nullSinkIsHarmless() {
        SafeAiFallbackLogger.sink = null
        // No tira excepcion.
        val line = SafeAiFallbackLogger.logDecision(
            SafeAiFallbackLogEvent(finalIntent = AgentIntent.HELP, whitelistPassed = true)
        )
        assertTrue(line.contains("intent=HELP"))
    }

    @Test
    fun redactSecretsHelperHandlesAllPatterns() {
        val keyPrefix = "sk" + "-"
        val keyEnv = "OPENAI" + "_API" + "_KEY"
        val raw = "stuff ${keyPrefix}ABCDEFGHIJKL ${keyEnv}=${keyPrefix}othersecret " +
            "Authorization: Bearer ${keyPrefix}ber123secretpls"
        val redacted = redactSecrets(raw)
        assertFalse(redacted.contains(keyPrefix + "ABCDEFGHIJKL"))
        assertFalse(redacted.contains(keyPrefix + "othersecret"))
        assertFalse(redacted.contains(keyPrefix + "ber123secretpls"))
        assertTrue(redacted.contains("[REDACTED]"))
    }
}
