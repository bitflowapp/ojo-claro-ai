package com.ojoclaro.android.llm

import com.ojoclaro.android.agent.AgentConversationManager
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * H1 (red team) — ningún secreto ETIQUETADO debe cruzar el borde hacia el LLM.
 *
 * No prueba solo el regex: ejercita el PAYLOAD real que cruza cada egreso —
 * el JSON de `/intent` (vía [EstelaIntentJsonContract.proxyRequestToJson] sobre
 * el request que arma [EstelaIntentRequestBuilder]) y el JSON de `/conversation`
 * (capturado con un [LlmAgentNetworkClient] espía sobre [EstelaConversationClient]
 * real). [LlmInputSanitizer] es la barrera central.
 *
 * Clases de producción atravesadas: LlmInputSanitizer, EstelaIntentRequestBuilder,
 * EstelaIntentJsonContract, EstelaConversationClient. No es tautológico: el espía
 * solo registra lo que el cliente real construye; la mutación que desactiva la
 * barrera (FASE 4) hace fallar estos tests.
 */
class RedTeamPrivacyEgressTest {

    // Bypasses reportados por el red team (conectores raros, ":" / "=", valor corto).
    private val labeledSecrets = listOf(
        "mi pin vale 1234" to "1234",
        "el código vale 445566" to "445566",
        "el otp nuevo resulta 654321" to "654321",
        "mi pin es: 1234" to "1234",
        "código=445566" to "445566",
        "mi clave resulta azul123" to "azul123",
        // Ya cubiertos por reglas previas, deben seguir cubiertos:
        "mi teléfono vale 2991234567" to "2991234567",
        "mandale mi código 445566" to "445566",
        "repetí mi pin 1234" to "1234"
    )

    @Test
    fun sanitizerRedactsLabeledSecretsWithVariedConnectors() {
        labeledSecrets.forEach { (phrase, secret) ->
            val out = LlmInputSanitizer.sanitize(phrase)
            assertFalse(out.contains(secret), "secreto sobrevive al sanitizador: \"$phrase\" -> \"$out\"")
        }
    }

    @Test
    fun intentEgressPayloadHasNoRawSecret() {
        val builder = EstelaIntentRequestBuilder()
        labeledSecrets.forEach { (phrase, secret) ->
            val req = builder.build(userText = phrase, conversationManager = AgentConversationManager())
            val json = EstelaIntentJsonContract.proxyRequestToJson(req, LlmAgentClientConfig.DEFAULT_MODEL)
            assertFalse(json.contains(secret), "secreto en payload /intent: \"$phrase\" -> $json")
        }
    }

    @Test
    fun conversationEgressPayloadHasNoRawSecret() = runBlocking {
        labeledSecrets.forEach { (phrase, secret) ->
            val spy = RecordingNetworkClient()
            val client = EstelaConversationClient(
                config = LlmAgentClientConfig(baseUrl = "http://localhost:0"),
                networkClient = spy,
                timeoutMillis = 1_000L
            )
            client.converse(
                userText = phrase,
                shortMemory = emptyList(),
                activeApp = null,
                routeActive = false,
                whatsappPending = false
            )
            assertNotNull(spy.lastBody, "no se capturó payload para \"$phrase\"")
            assertFalse(spy.lastBody!!.contains(secret), "secreto en payload /conversation: \"$phrase\" -> ${spy.lastBody}")
        }
    }

    @Test
    fun benignNumbersWithoutCredentialKeywordAreNotOverRedacted() {
        // Precisión: sin palabra-clave de credencial, los números benignos quedan.
        listOf("vivo en la calle 1234", "nos vemos a las 1030", "el colectivo 60").forEach { p ->
            val out = LlmInputSanitizer.sanitize(p)
            val digits = p.filter(Char::isDigit)
            assertFalse(out.contains("[dato]"), "no debe redactar número benigno: \"$p\" -> \"$out\"")
            // y el número benigno corto (<7) se conserva
            if (digits.length < 7) {
                assertFalse(out.contains("[número]"), "no debe redactar número corto benigno: \"$p\" -> \"$out\"")
            }
        }
    }

    private class RecordingNetworkClient : LlmAgentNetworkClient {
        var lastBody: String? = null
        override suspend fun postJson(
            url: String,
            jsonBody: String,
            timeoutMillis: Long,
            headers: Map<String, String>
        ): LlmHttpResponse {
            lastBody = jsonBody
            return LlmHttpResponse(statusCode = 200, body = "{\"ok\":true,\"reply\":\"ok\"}")
        }
    }
}
