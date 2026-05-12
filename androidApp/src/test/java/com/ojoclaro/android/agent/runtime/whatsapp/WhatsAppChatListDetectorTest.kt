package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WhatsAppChatListDetectorTest {

    private val detector = WhatsAppChatListDetector()

    private fun snapshot(
        packageName: String? = "com.whatsapp",
        elements: List<ScreenElement> = emptyList()
    ) = ScreenSnapshot(
        packageName = packageName,
        text = "x",
        elements = elements,
        capturedAtMillis = 0L
    )

    private fun textName(name: String) = ScreenElement(
        label = name,
        role = ScreenElementRole.TEXT,
        isInteractive = false
    )

    @Test
    fun nullSnapshotProducesEmpty() {
        assertTrue(detector.extractChats(null).isEmpty())
    }

    @Test
    fun snapshotWithoutElementsProducesEmpty() {
        assertTrue(detector.extractChats(snapshot()).isEmpty())
    }

    @Test
    fun extractsSimpleChatNames() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("Sofi"),
                    textName("Mamá")
                )
            )
        )
        assertEquals(3, chats.size)
        assertEquals(listOf("Marco", "Sofi", "Mamá"), chats.map { it.displayName })
    }

    @Test
    fun capsAtFiveVisibleChats() {
        val chats = detector.extractChats(
            snapshot(
                elements = (1..10).map { textName("Contacto$it") }
            )
        )
        assertEquals(5, chats.size)
    }

    @Test
    fun rejectsMessagePreviewWithColonPattern() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("Sofi: estoy llegando tarde, te aviso"),
                    textName("Mamá: te llamo en un rato"),
                    textName("Hermano")
                )
            )
        )
        // Solo nombres limpios.
        val names = chats.map { it.displayName }
        assertTrue("Marco" in names)
        assertTrue("Hermano" in names)
        assertFalse(names.any { it.contains("estoy llegando", ignoreCase = true) })
        assertFalse(names.any { it.contains("te llamo", ignoreCase = true) })
    }

    @Test
    fun rejectsTimeStrings() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("14:30"),
                    textName("ayer"),
                    textName("hoy"),
                    textName("lun"),
                    textName("Sofi")
                )
            )
        )
        val names = chats.map { it.displayName }
        assertEquals(listOf("Marco", "Sofi"), names)
    }

    @Test
    fun rejectsNumericOnlyAndPhoneLikeStrings() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("5"),
                    textName("12"),
                    textName("+5491100000000"),
                    textName("011 4567 8901"),
                    textName("Sofi")
                )
            )
        )
        val names = chats.map { it.displayName }
        assertTrue("Marco" in names)
        assertTrue("Sofi" in names)
        assertFalse(names.any { it.startsWith("+") })
        assertFalse("5" in names)
        assertFalse("12" in names)
    }

    @Test
    fun rejectsUiKeywordsAsChatNames() {
        val uiLabels = listOf("Cámara", "Enviar", "Adjuntar", "Buscar", "Chats",
            "Estado", "Comunidades", "Llamadas", "Configuración", "WhatsApp")
        val chats = detector.extractChats(
            snapshot(
                elements = uiLabels.map { textName(it) } + textName("Marco")
            )
        )
        val names = chats.map { it.displayName }
        assertEquals(listOf("Marco"), names)
    }

    @Test
    fun rejectsButtonsThatLookLikeUiKeywords() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    ScreenElement("Buscar", ScreenElementRole.BUTTON, isInteractive = true),
                    ScreenElement("Cámara", ScreenElementRole.BUTTON, isInteractive = true),
                    textName("Marco")
                )
            )
        )
        assertEquals(listOf("Marco"), chats.map { it.displayName })
    }

    @Test
    fun ignoresNonTextRoles() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    ScreenElement("foto perfil", ScreenElementRole.IMAGE, isInteractive = false),
                    ScreenElement("texto editable", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                    ScreenElement("checkbox", ScreenElementRole.CHECKBOX, isInteractive = true),
                    textName("Marco")
                )
            )
        )
        assertEquals(listOf("Marco"), chats.map { it.displayName })
    }

    @Test
    fun dedupesIdenticalNames() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("Marco"),
                    textName("MARCO"),
                    textName("Sofi")
                )
            )
        )
        assertEquals(listOf("Marco", "Sofi"), chats.map { it.displayName })
    }

    @Test
    fun rejectsLongStringsLikelyToBeAggregatedRowDescriptions() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco. último mensaje recibido a las 14:30. Hola, ¿cómo estás?"),
                    textName("Sofi")
                )
            )
        )
        assertEquals(listOf("Sofi"), chats.map { it.displayName })
    }

    @Test
    fun rejectsTruncatedPreviews() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("Hola cómo estás te llamo en…"),
                    textName("Otro mensaje cortado..."),
                    textName("Sofi")
                )
            )
        )
        val names = chats.map { it.displayName }
        assertEquals(listOf("Marco", "Sofi"), names)
    }

    @Test
    fun rejectsSensitiveFinancialContent() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName("Marco"),
                    textName("CBU 1234567890"),
                    textName("Saldo banco"),
                    textName("Sofi")
                )
            )
        )
        val names = chats.map { it.displayName }
        assertEquals(listOf("Marco", "Sofi"), names)
    }

    @Test
    fun acceptsClickableButtonRowsWithChatNameLabel() {
        // Algunas variantes de WhatsApp clasifican la fila como BUTTON con
        // contentDescription = nombre. Si el label es corto y no es UI keyword,
        // lo aceptamos.
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    ScreenElement("Marco", ScreenElementRole.BUTTON, isInteractive = true),
                    ScreenElement("Sofi", ScreenElementRole.BUTTON, isInteractive = true)
                )
            )
        )
        assertEquals(listOf("Marco", "Sofi"), chats.map { it.displayName })
    }

    @Test
    fun rejectsBlankAndTooShortLabels() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    textName(""),
                    textName(" "),
                    textName("M"),     // 1 char → too short
                    textName("Marco")
                )
            )
        )
        assertEquals(listOf("Marco"), chats.map { it.displayName })
    }

    @Test
    fun ignoresPasswordFlaggedElements() {
        val chats = detector.extractChats(
            snapshot(
                elements = listOf(
                    ScreenElement(
                        label = "Contraseña",
                        role = ScreenElementRole.TEXT,
                        isInteractive = false,
                        isPassword = true
                    ),
                    textName("Marco")
                )
            )
        )
        assertEquals(listOf("Marco"), chats.map { it.displayName })
    }

    @Test
    fun detectorDoesNotScanPastElementCap() {
        val manyInvalidRows = List(WhatsAppChatListDetector.MAX_ELEMENTS_TO_SCAN) {
            textName("Buscar")
        }
        val chats = detector.extractChats(
            snapshot(
                elements = manyInvalidRows + textName("Marco")
            )
        )

        assertTrue(chats.isEmpty())
    }
}
