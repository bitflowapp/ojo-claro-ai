package com.ojoclaro.android.agent.core.screen

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DeterministicScreenSummarizerTest {

    private val summarizer = DeterministicScreenSummarizer()

    @Test
    fun nullSnapshotProducesLimitedSummary() {
        val s = summarizer.summarize(null, ScreenSummaryMode.SHORT)
        assertTrue(s.isLimited)
        assertTrue(s.spokenText.contains("No tengo lectura", ignoreCase = true))
    }

    @Test
    fun emptySnapshotProducesLimitedSummary() {
        val s = summarizer.summarize(
            ScreenSnapshot(text = "   ", capturedAtMillis = 0L),
            ScreenSummaryMode.SHORT
        )
        assertTrue(s.isLimited)
    }

    @Test
    fun bankingScreenIsNotRead() {
        val snapshot = ScreenSnapshot(
            packageName = "com.bbva.app",
            text = "Saldo de tu cuenta bancaria 1.250.000 pesos",
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.SHORT)
        assertFalse(s.isSafeToRead)
        assertTrue(s.spokenText.contains("banc", ignoreCase = true))
        assertFalse(
            s.spokenText.contains("1.250.000"),
            "no debe leer el saldo en voz alta"
        )
    }

    @Test
    fun passwordFieldDetectedFromElementsBlocksRead() {
        val snapshot = ScreenSnapshot(
            text = "Ingresá tu contraseña",
            elements = listOf(
                ScreenElement(
                    label = "Contraseña",
                    role = ScreenElementRole.EDIT_TEXT,
                    isInteractive = true,
                    isPassword = true
                )
            ),
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.SHORT)
        assertFalse(s.isSafeToRead)
        assertTrue(s.spokenText.contains("contraseña", ignoreCase = true))
    }

    @Test
    fun shortSummaryUsesHeadingWhenPresent() {
        val snapshot = ScreenSnapshot(
            text = "Bienvenido al chat con Sofi. Mensaje: estoy llegando.",
            elements = listOf(
                ScreenElement(
                    label = "Chat con Sofi",
                    role = ScreenElementRole.HEADING,
                    isInteractive = false
                )
            ),
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.SHORT)
        assertTrue(s.spokenText.contains("Chat con Sofi"))
    }

    @Test
    fun shortSummaryMentionsAppActionAndAtMostThreeImportantElements() {
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Chats",
            elements = listOf(
                ScreenElement("Chats", ScreenElementRole.HEADING, isInteractive = false),
                ScreenElement("Buscar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Nuevo chat", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Comunidades", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Ajustes", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.SHORT)

        assertTrue(s.spokenText.contains("App detectada: WhatsApp"))
        assertTrue(s.spokenText.contains("principal", ignoreCase = true))
        assertTrue(s.spokenText.contains("Buscar"))
        assertTrue(s.spokenText.contains("Nuevo chat"))
        assertFalse(s.spokenText.contains("Comunidades"))
        assertFalse(s.spokenText.contains("Ajustes"))
    }

    @Test
    fun whatsappSummaryDoesNotReadCompleteMessages() {
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Marco: te paso el dato completo. Sofi: estoy llegando en cinco.",
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.SHORT)

        assertTrue(s.spokenText.contains("WhatsApp"))
        assertTrue(s.spokenText.contains("no leo mensajes completos", ignoreCase = true))
        assertFalse(s.spokenText.contains("estoy llegando", ignoreCase = true))
        assertFalse(s.spokenText.contains("te paso el dato completo", ignoreCase = true))
    }

    @Test
    fun whatCanIDoListsInteractiveElements() {
        val snapshot = ScreenSnapshot(
            text = "x",
            elements = listOf(
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Adjuntar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Texto inerte", ScreenElementRole.TEXT, isInteractive = false),
                ScreenElement("Contraseña", ScreenElementRole.EDIT_TEXT, isInteractive = true, isPassword = true)
            ),
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.WHAT_CAN_I_DO)
        assertTrue(s.spokenText.contains("Enviar"))
        assertTrue(s.spokenText.contains("Adjuntar"))
        assertFalse(
            s.spokenText.contains("Contraseña"),
            "campo de contraseña no debe listarse como acción"
        )
        assertFalse(s.spokenText.contains("Texto inerte"))
    }

    @Test
    fun whereAmIDescribesPackageAndCounts() {
        val snapshot = ScreenSnapshot(
            packageName = "com.example.app",
            text = "Hola",
            elements = listOf(
                ScreenElement("Tab principal", ScreenElementRole.HEADING, isInteractive = false),
                ScreenElement("Aceptar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Cancelar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Email", ScreenElementRole.EDIT_TEXT, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.WHERE_AM_I)
        assertTrue(s.spokenText.contains("com.example.app"))
        assertTrue(s.spokenText.contains("2 botones"))
        assertTrue(s.spokenText.contains("1 campo"))
    }

    @Test
    fun detailedSummaryClampsText() {
        val longText = "a".repeat(2000)
        val snapshot = ScreenSnapshot(text = longText, capturedAtMillis = 0L)
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.DETAILED)
        assertTrue(s.spokenText.length < 1000)
        assertTrue(s.spokenText.contains("…"))
    }

    @Test
    fun summarizerNeverInventsContent() {
        val snapshot = ScreenSnapshot(text = "Texto exacto.", capturedAtMillis = 0L)
        val s = summarizer.summarize(snapshot, ScreenSummaryMode.SHORT)
        // no debe agregar contexto que no aparece en el snapshot
        assertFalse(s.spokenText.contains("WhatsApp"))
        assertFalse(s.spokenText.contains("Sofi"))
    }
}

class ScreenQueryPhrasesTest {

    @Test
    fun recognizesWhereAmI() {
        assertEquals(ScreenSummaryMode.WHERE_AM_I, ScreenQueryPhrases.classify("dónde estoy"))
        assertEquals(ScreenSummaryMode.WHERE_AM_I, ScreenQueryPhrases.classify("¿En qué pantalla estoy?"))
    }

    @Test
    fun recognizesWhatCanIDo() {
        assertEquals(ScreenSummaryMode.WHAT_CAN_I_DO, ScreenQueryPhrases.classify("qué puedo hacer acá"))
        assertEquals(ScreenSummaryMode.WHAT_CAN_I_DO, ScreenQueryPhrases.classify("qué botones hay"))
    }

    @Test
    fun recognizesSummarize() {
        assertEquals(ScreenSummaryMode.SHORT, ScreenQueryPhrases.classify("resumí la pantalla"))
        assertEquals(ScreenSummaryMode.SHORT, ScreenQueryPhrases.classify("qué hay en pantalla"))
    }

    @Test
    fun recognizesImportant() {
        assertEquals(ScreenSummaryMode.IMPORTANT, ScreenQueryPhrases.classify("leeme lo importante"))
    }

    @Test
    fun returnsNullForUnrelatedText() {
        assertNull(ScreenQueryPhrases.classify("mandale un mensaje a Sofi"))
        assertNull(ScreenQueryPhrases.classify("abrí WhatsApp"))
    }

    @Test
    fun recognizesDetailedScreen() {
        assertNotNull(ScreenQueryPhrases.classify("contame los detalles de la pantalla"))
    }
}
