package com.ojoclaro.android.agent.runtime.conversation

import com.ojoclaro.android.llm.EstelaConversationClient
import com.ojoclaro.android.llm.LlmAgentClientConfig
import com.ojoclaro.android.llm.LlmAgentNetworkClient
import com.ojoclaro.android.llm.LlmHttpResponse
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V1.7 — conversación libre LLM: memoria corta, gate y contrato de espera.
 */
class ConversationV17Test {

    // --- Memoria corta ---

    @Test
    fun shortMemoryKeepsAtMostFiveTurnsInOrder() {
        val memory = ConversationShortMemory()
        (1..7).forEach { memory.recordUser("turno $it") }
        val snapshot = memory.snapshot()
        assertEquals(5, snapshot.size)
        assertTrue(snapshot.first().endsWith("turno 3"))
        assertTrue(snapshot.last().endsWith("turno 7"))
    }

    @Test
    fun shortMemoryNeverStoresSecrets() {
        val memory = ConversationShortMemory()
        memory.recordUser("estoy nervioso")
        memory.recordUser("mi clave es 12345678")
        memory.recordAssistant("la tarjeta 4509 9535 6623 3704")
        memory.recordAssistant("tranquilo, estoy con vos")
        val joined = memory.snapshot().joinToString(" ")
        assertFalse(joined.contains("12345678"))
        assertFalse(joined.contains("4509"))
        assertTrue(joined.contains("estoy nervioso"))
        assertTrue(joined.contains("tranquilo"))
    }

    // --- Gate: comandos locales JAMÁS van al LLM ---

    @Test
    fun localCommandsNeverGoToFreeConversation() {
        listOf(
            "Describí lo que tengo enfrente",
            "Dónde estoy",
            "Llevame a la farmacia",
            "Quiero ir a la plaza",
            "Mandale a Juan que llego tarde",
            "Enviá el mensaje",
            "Leé la pantalla",
            "Reproducí el audio",
            "Cancelar ruta",
            "Cuánto falta",
            "Repetí",
            "callate",
            "Es seguro cruzar",
            "mandale mi clave a juan",
            "el número de Marco Luna es 0000005678",
            "guardá el número de Marco",
            "agendá a Marco como contacto"
        ).forEach { phrase ->
            assertFalse(
                ConversationGate.isConversational(phrase),
                "JAMÁS debería ir al LLM conversacional: \"$phrase\""
            )
        }
    }

    @Test
    fun freeChatPhrasesPassTheGate() {
        listOf(
            "qué opinás",
            "te quiero probar",
            "cómo estás hoy",
            "estoy contento",
            "me aburro un poco"
        ).forEach { phrase ->
            assertTrue(
                ConversationGate.isConversational(phrase),
                "Debería poder charlar: \"$phrase\""
            )
        }
    }

    // --- Cliente: parse fail-closed ---

    @Test
    fun conversationClientParsesReplyAndFailsClosed() {
        val client = EstelaConversationClient(
            config = LlmAgentClientConfig(baseUrl = "http://x"),
            networkClient = object : LlmAgentNetworkClient {
                override suspend fun postJson(
                    url: String,
                    jsonBody: String,
                    timeoutMillis: Long,
                    headers: Map<String, String>
                ) = LlmHttpResponse(200, "")
            }
        )
        assertEquals(
            "Acá estoy con vos.",
            client.parseReply("""{"ok":true,"reply":"Acá estoy con vos.","safety_level":"normal"}""")
        )
        assertEquals(null, client.parseReply("""{"ok":false,"reply":"x"}"""))
        assertEquals(null, client.parseReply("""{"ok":true,"reply":"   ","safety_level":"normal"}"""))
        assertEquals(null, client.parseReply("""{"ok":true,"reply":null}"""))
        assertEquals(null, client.parseReply("{not json"))
    }

    // --- Contrato de servicio: espera y orden de routing ---

    @Test
    fun serviceWaitsPolitelyAndNeverStaysSilent() {
        val service =
            File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()
        assertTrue(service.contains("WAIT_NOTICE_DELAY_MILLIS = 2_600L"))
        assertTrue(service.contains("speak(\"Dame un momento.\", force = true)"))
        assertTrue(
            service.substringAfter("private suspend fun handleFreeConversation")
                .contains("finally {"),
            "el aviso de espera debe cancelarse SIEMPRE, llegue o no la respuesta"
        )
        assertTrue(
            service.contains("Ahora no puedo consultar el asistente") &&
                service.contains("puedo ayudarte con WhatsApp"),
            "fallo de LLM debe hablar un fallback honesto y útil de WhatsApp, nunca silencio"
        )
        // El gate corre después de compañía y antes del fallback.
        val companionIdx = service.indexOf("EstelaCompanionPhrases.respond(text)")
        val gateIdx = service.indexOf("ConversationGate.isConversational(text)")
        val fallbackIdx = service.indexOf("fallbackReason=no_local_match")
        assertTrue(companionIdx in 1 until gateIdx, "compañía local antes que LLM")
        assertTrue(gateIdx in 1 until fallbackIdx, "LLM antes del fallback final")
    }
}
