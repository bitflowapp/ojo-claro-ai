package com.ojoclaro.android.agent.runtime.conversation

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * V1.9 — conversación más humana sin perder seguridad:
 *  - la memoria corta deriva ánimo y eventos, sin secretos ni coordenadas;
 *  - las frases emocionales van al LLM y los comandos siguen locales;
 *  - los earcons siguen sobrios (cortos, sin loops, apagables).
 */
class ConversationV19Test {

    // --- memoria corta más útil ---

    @Test
    fun moodHintDetectsRecentEmotionalState() {
        val memory = ConversationShortMemory()
        memory.recordUser("Estoy nervioso")
        memory.recordAssistant("Tranqui, vamos paso a paso.")
        assertEquals("nervioso", memory.moodHint())

        val tired = ConversationShortMemory()
        tired.recordUser("estoy muy cansado hoy")
        assertEquals("cansado", tired.moodHint())

        val neutral = ConversationShortMemory()
        neutral.recordUser("qué hora es")
        assertNull(neutral.moodHint())
    }

    @Test
    fun contextLineCombinesMoodAndRecentEvents() {
        val memory = ConversationShortMemory()
        memory.recordUser("estoy nervioso")
        memory.noteContext("inició una ruta a pie")
        memory.noteContext("falló la conexión de conversación")
        val line = memory.contextLine()
        assertTrue(line != null && line.startsWith("Contexto: "))
        assertTrue(line!!.contains("nervioso"))
        assertTrue(line.contains("inició una ruta a pie"))

        val snapshot = memory.snapshotWithContext()
        assertEquals(line, snapshot.last())
        // El snapshot clásico no cambia (compatibilidad V1.7).
        assertFalse(memory.snapshot().any { it.startsWith("Contexto:") })
    }

    @Test
    fun contextNotesAreBoundedAndDeduplicated() {
        val memory = ConversationShortMemory()
        repeat(5) { memory.noteContext("consultó su ubicación") }
        memory.noteContext("inició una ruta a pie")
        memory.noteContext("canceló la ruta a pie")
        memory.noteContext("envió un mensaje de WhatsApp confirmado")
        val line = memory.contextLine().orEmpty()
        // Dedup consecutivo + máximo 3 eventos.
        assertFalse(line.contains("consultó su ubicación"))
        assertTrue(line.contains("envió un mensaje de WhatsApp confirmado"))
    }

    @Test
    fun secretsAndExactCoordinatesNeverEnterMemory() {
        val memory = ConversationShortMemory()
        memory.recordUser("mi clave es 12345678")
        memory.recordUser("estoy en -34.603722, -58.381592")
        memory.recordAssistant("Estás cerca de San Martín 500. La precisión es de unos 12 metros.")
        val joined = memory.snapshotWithContext().joinToString(" ")
        assertFalse(joined.contains("12345678"))
        assertFalse(joined.contains("-34.6037"))
        // Lo hablable sin coordenadas sí queda (la calle no es un secreto).
        assertTrue(joined.contains("San Martín 500"))
    }

    @Test
    fun memoryNeverPersistsMoreThanFiveTurns() {
        val memory = ConversationShortMemory()
        repeat(9) { index -> memory.recordUser("turno número $index") }
        assertEquals(5, memory.snapshot().size)
    }

    // --- routing: charla vs comandos ---

    @Test
    fun emotionalAndOpenPhrasesGoToConversation() {
        listOf(
            "me siento perdido",
            "estoy nervioso",
            "hablame",
            "qué opinás",
            "no sé qué hacer",
            "me salió mal",
            "tengo miedo",
            "me da vergüenza",
            "hablemos un rato",
            "acompañame"
        ).forEach { phrase ->
            assertTrue(
                ConversationGate.isConversational(phrase),
                "Debería poder ir a conversación: \"$phrase\""
            )
        }
    }

    @Test
    fun commandsStayLocalEvenIfTheySoundHuman() {
        // Nota: "estoy perdido" se mantiene local por ORDEN de routing (outdoor
        // corre antes del gate); su split se prueba en OutdoorV19GuidanceTest.
        listOf(
            "Dónde estoy",
            "Llevame a la plaza",
            "Cuánto falta",
            "Cancelar ruta",
            "Recalculá",
            "Mandale a Marco Luna que llego en diez",
            "Enviá",
            "Leé la pantalla",
            "Reproducí el audio",
            "Callate"
        ).forEach { phrase ->
            assertFalse(
                ConversationGate.isConversational(phrase),
                "JAMÁS debería ir al LLM: \"$phrase\""
            )
        }
    }

    // --- earcons sobrios (contrato por inspección de fuente) ---

    @Test
    fun earconsStayShortQuietAndLoopFree() {
        val source = File(
            "src/main/java/com/ojoclaro/android/speech/EstelaEarcons.kt"
        ).readText()
        // Duraciones de tono declaradas: todas < 300 ms.
        val durations = Regex("play\\(ToneGenerator\\.[A-Z_]+, (\\d+)\\)")
            .findAll(source)
            .map { it.groupValues[1].toInt() }
            .toList()
        assertTrue(durations.isNotEmpty())
        assertTrue(durations.all { it < 300 }, "earcons largos: $durations")
        // Sin loops y con apagado disponible.
        assertFalse(source.contains("while"), "un earcon jamás puede loopear")
        assertTrue(source.contains("var enabled"), "debe poder desactivarse")
        // Respeta el volumen del sistema (stream de notificación, no alarma).
        assertTrue(source.contains("STREAM_NOTIFICATION"))
        assertFalse(source.contains("STREAM_ALARM"))
    }
}
