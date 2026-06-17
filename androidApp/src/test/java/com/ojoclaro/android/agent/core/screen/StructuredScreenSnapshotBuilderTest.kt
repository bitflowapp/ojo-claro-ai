package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.risk.RiskType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StructuredScreenSnapshotBuilderTest {

    private val builder = StructuredScreenSnapshotBuilder()
    private val now = 1_700_000_000_000L

    @Test
    fun `null raw snapshot returns empty limited`() {
        val structured = builder.build(snapshot = null, capturedAtMillis = now)

        assertTrue(structured.isEmpty)
        assertTrue(structured.isLimited)
        assertNull(structured.packageName)
        assertEquals(now, structured.capturedAtMillis)
    }

    @Test
    fun `raw snapshot with text produces redacted lines`() {
        val raw = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Hola Mama\nMi password es secreto\nChau",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertEquals(3, structured.redactedTextLines.size)
        assertTrue(structured.redactedTextLines.contains("Hola Mama"))
        assertTrue(structured.redactedTextLines.contains(ScreenTextSanitizer.PASSWORD_PLACEHOLDER))
        assertTrue(structured.redactedTextLines.contains("Chau"))
    }

    @Test
    fun `placeholder-only lines are kept but never crude content`() {
        // Esta verificación es opuesta: queremos QUE el placeholder aparezca
        // (no se filtra), pero asegurarnos de que el contenido original NUNCA
        // sale en redactedTextLines.
        val raw = ScreenSnapshot(
            packageName = "com.example",
            text = "Tu codigo de verificacion es 999888",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.redactedTextLines.contains(ScreenTextSanitizer.CODE_PLACEHOLDER))
        assertFalse(structured.redactedTextLines.any { it.contains("999888") })
    }

    @Test
    fun `buttons are extracted from interactive BUTTON elements`() {
        val raw = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "",
            elements = listOf(
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Cancelar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Header", ScreenElementRole.HEADING, isInteractive = false)
            ),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertEquals(listOf("Enviar", "Cancelar"), structured.buttons)
    }

    @Test
    fun `editable fields are extracted and password fields excluded`() {
        val raw = ScreenSnapshot(
            packageName = "com.example.bank",
            text = "",
            elements = listOf(
                ScreenElement("Usuario", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Contraseña", ScreenElementRole.EDIT_TEXT, isInteractive = true, isPassword = true),
                ScreenElement("Email", ScreenElementRole.EDIT_TEXT, isInteractive = true)
            ),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertEquals(listOf("Usuario", "Email"), structured.editableFields)
    }

    @Test
    fun `password element is reflected in signals`() {
        val raw = ScreenSnapshot(
            packageName = "com.example",
            text = "Login",
            elements = listOf(
                ScreenElement("Contraseña", ScreenElementRole.EDIT_TEXT, isInteractive = true, isPassword = true)
            ),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.signals.hasPasswordField)
        assertTrue(structured.shouldBlockGeneralActions)
    }

    @Test
    fun `banking package name triggers isBankingApp signal and warning`() {
        val raw = ScreenSnapshot(
            packageName = "ar.com.galicia",
            text = "Bienvenido",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.signals.isBankingApp)
        assertTrue(structured.warnings.any { it.type == RiskType.BANKING_SCREEN })
        assertTrue(structured.shouldBlockGeneralActions)
    }

    @Test
    fun `payment-related text triggers payment signal`() {
        val raw = ScreenSnapshot(
            packageName = "com.example",
            text = "Transferencia de 5000 pesos al alias",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.signals.hasPaymentOrTransferSignals)
    }

    @Test
    fun `verification code text triggers code signal`() {
        val raw = ScreenSnapshot(
            packageName = "com.example",
            text = "Tu codigo de verificacion es 123456",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.signals.hasVerificationCode)
    }

    @Test
    fun `messaging package triggers messaging signal`() {
        val raw = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Chat",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.signals.isMessagingApp)
        assertFalse(structured.signals.isBankingApp)
    }

    @Test
    fun `many elements triggers scrollable signal`() {
        val elements = (1..15).map {
            ScreenElement("Item $it", ScreenElementRole.LIST_ITEM, isInteractive = false)
        }
        val raw = ScreenSnapshot(
            packageName = "com.example",
            text = "",
            elements = elements,
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.signals.hasScrollableContent)
        assertEquals(15, structured.totalNodes)
    }

    @Test
    fun `app label is mapped for known packages`() {
        val raw = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "",
            elements = emptyList(),
            capturedAtMillis = now
        )

        assertEquals("WhatsApp", builder.build(raw).appLabel)

        val rawBank = ScreenSnapshot(
            packageName = "ar.com.galicia",
            text = "",
            elements = emptyList(),
            capturedAtMillis = now
        )
        assertEquals("Banco Galicia", builder.build(rawBank).appLabel)
    }

    @Test
    fun `unknown package returns null app label`() {
        val raw = ScreenSnapshot(
            packageName = "com.unknown.app",
            text = "",
            elements = emptyList(),
            capturedAtMillis = now
        )

        assertNull(builder.build(raw).appLabel)
    }

    @Test
    fun `focused label prefers heading then first editable`() {
        val withHeading = ScreenSnapshot(
            packageName = null,
            text = "",
            elements = listOf(
                ScreenElement("Mi panel", ScreenElementRole.HEADING, isInteractive = false),
                ScreenElement("Nombre", ScreenElementRole.EDIT_TEXT, isInteractive = true)
            ),
            capturedAtMillis = now
        )
        assertEquals("Mi panel", builder.build(withHeading).focusedLabel)

        val onlyEditable = ScreenSnapshot(
            packageName = null,
            text = "",
            elements = listOf(
                ScreenElement("Nombre", ScreenElementRole.EDIT_TEXT, isInteractive = true)
            ),
            capturedAtMillis = now
        )
        assertEquals("Nombre", builder.build(onlyEditable).focusedLabel)
    }

    @Test
    fun `buttons list is deduplicated and capped`() {
        val elements = (1..20).map {
            // 20 buttons with the same label → dedup to 1.
            ScreenElement("Continuar", ScreenElementRole.BUTTON, isInteractive = true)
        }
        val raw = ScreenSnapshot(
            packageName = null,
            text = "",
            elements = elements,
            capturedAtMillis = now
        )

        val structured = builder.build(raw)
        assertEquals(1, structured.buttons.size)
        assertEquals("Continuar", structured.buttons.first())
    }

    @Test
    fun `warnings are sorted by severity descending`() {
        val raw = ScreenSnapshot(
            packageName = "com.whatsapp",
            // Mezclamos urgente (sev 2), código (sev 3), money (sev 2)
            text = "Urgente! Tu codigo de verificacion es 999888 transferi 5000",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)

        assertTrue(structured.warnings.isNotEmpty())
        // El primero es el de mayor severidad (3).
        assertEquals(3, structured.warnings.first().severity)
        assertTrue(structured.warnings.all { it.severity <= structured.warnings.first().severity })
    }

    @Test
    fun `empty raw snapshot returns isLimited true`() {
        val raw = ScreenSnapshot(
            packageName = null,
            text = "",
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)
        assertTrue(structured.isLimited)
    }

    @Test
    fun `redacted lines are capped at MAX_TEXT_LINES`() {
        val lines = (1..40).joinToString("\n") { "linea $it" }
        val raw = ScreenSnapshot(
            packageName = null,
            text = lines,
            elements = emptyList(),
            capturedAtMillis = now
        )

        val structured = builder.build(raw)
        assertTrue(structured.redactedTextLines.size <= 24)
    }

    @Test
    fun `personal data request triggers personal data signal`() {
        val raw = ScreenSnapshot(
            packageName = "com.example",
            text = "Pasame tu DNI y tu direccion exacta",
            elements = emptyList(),
            capturedAtMillis = now
        )
        val structured = builder.build(raw)
        assertTrue(structured.signals.hasPersonalDataRequest)
    }

    @Test
    fun `has form fields signal counts editable elements`() {
        val raw = ScreenSnapshot(
            packageName = null,
            text = "",
            elements = listOf(
                ScreenElement("Nombre", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Apellido", ScreenElementRole.EDIT_TEXT, isInteractive = true)
            ),
            capturedAtMillis = now
        )
        val structured = builder.build(raw)
        assertTrue(structured.signals.hasFormFields)
    }
}
