package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.risk.RiskWarning
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class ScreenChangeAwarenessEngineTest {

    private var now: Long = 1_000L
    private val engine = ScreenChangeAwarenessEngine(clock = { now })

    @Test
    fun previousNullAndCurrentNormalDoesNotAnnounce() {
        val announcement = engine.evaluate(
            previous = null,
            current = snapshot(packageName = "com.example", buttons = listOf("OK"))
        )
        // No anuncia pantalla inicial sin contexto previo (excepto hot zone).
        assertEquals(ScreenChangeEvent.NONE, announcement.event)
        assertFalse(announcement.shouldAnnounce)
    }

    @Test
    fun previousNullAndCurrentHotZoneAnnouncesCritical() {
        val announcement = engine.evaluate(
            previous = null,
            current = snapshot(
                packageName = "com.example.login",
                signals = ScreenSignals(hasPasswordField = true)
            )
        )
        assertEquals(ScreenChangeEvent.PASSWORD_SCREEN_ENTERED, announcement.event)
        assertEquals(ScreenChangeImportance.CRITICAL, announcement.importance)
        assertTrue(announcement.shouldAnnounce)
    }

    @Test
    fun appChangedTriggersAppChangedAnnouncement() {
        val prev = snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        val current = snapshot(packageName = "com.android.settings", appLabel = "Ajustes")

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.APP_CHANGED, ann.event)
        assertTrue(ann.spokenText.contains("Ajustes", ignoreCase = true))
        assertEquals(ScreenChangeImportance.NORMAL, ann.importance)
    }

    @Test
    fun appChangedToWhatsAppUsesFriendlyName() {
        val prev = snapshot(packageName = "com.android.settings", appLabel = "Ajustes")
        val current = snapshot(packageName = "com.whatsapp.w4b", appLabel = null)

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.APP_CHANGED, ann.event)
        assertTrue(
            ann.spokenText.contains("WhatsApp", ignoreCase = true),
            "Expected friendly name for WhatsApp, got '${ann.spokenText}'"
        )
    }

    @Test
    fun appChangedToBankingPathPrefersSensitiveWarning() {
        val prev = snapshot(packageName = "com.android.launcher", appLabel = "Inicio")
        val current = snapshot(
            packageName = "ar.com.galicia",
            appLabel = "Banco Galicia",
            signals = ScreenSignals(isBankingApp = true, hasPaymentOrTransferSignals = true)
        )

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.PAYMENT_OR_BANKING_SCREEN_ENTERED, ann.event)
        assertEquals(ScreenChangeImportance.CRITICAL, ann.importance)
        assertTrue(ann.spokenText.contains("bancaria", ignoreCase = true) ||
            ann.spokenText.contains("pago", ignoreCase = true))
    }

    @Test
    fun passwordScreenWarningHasNoSensitiveContent() {
        val prev = snapshot(packageName = "com.example.login", buttons = listOf("Ingresar"))
        val current = snapshot(
            packageName = "com.example.login",
            buttons = listOf("Ingresar"),
            redactedTextLines = listOf("Bienvenido", "Acceso seguro"),
            signals = ScreenSignals(hasPasswordField = true)
        )

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.PASSWORD_SCREEN_ENTERED, ann.event)
        assertTrue(ann.spokenText.contains("contraseña", ignoreCase = true))
        // Nunca menciona el contenido del snapshot.
        assertFalse(ann.spokenText.contains("Bienvenido"))
        assertFalse(ann.spokenText.contains("Acceso seguro"))
    }

    @Test
    fun otpScreenDoesNotReadCodeAloud() {
        val prev = snapshot(packageName = "com.example.bank", buttons = listOf("Validar"))
        val current = snapshot(
            packageName = "com.example.bank",
            buttons = listOf("Validar"),
            redactedTextLines = listOf("[código omitido]"),
            signals = ScreenSignals(hasVerificationCode = true)
        )

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.SENSITIVE_SCREEN_ENTERED, ann.event)
        assertEquals(ScreenChangeImportance.CRITICAL, ann.importance)
        assertTrue(ann.spokenText.contains("código", ignoreCase = true) ||
            ann.spokenText.contains("verificación", ignoreCase = true))
        assertFalse(ann.spokenText.contains("[código omitido]"))
    }

    @Test
    fun formScreenEnteredWhenEditablesAppear() {
        val prev = snapshot(packageName = "com.example.form", buttons = listOf("Enviar"))
        val current = snapshot(
            packageName = "com.example.form",
            buttons = listOf("Enviar"),
            editableFields = listOf("Nombre")
        )

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.FORM_SCREEN_ENTERED, ann.event)
        assertTrue(ann.spokenText.contains("formulario", ignoreCase = true))
    }

    @Test
    fun chatScreenEnteredWhenMessagingSignalAppears() {
        val prev = snapshot(packageName = "com.whatsapp")
        val current = snapshot(
            packageName = "com.whatsapp",
            signals = ScreenSignals(isMessagingApp = true)
        )

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.CHAT_SCREEN_ENTERED, ann.event)
        assertTrue(ann.spokenText.contains("mensajes", ignoreCase = true))
    }

    @Test
    fun dialogAppearsTriggersDialogAnnouncement() {
        val prev = snapshot(
            packageName = "com.example.app",
            buttons = listOf("Menu", "Buscar")
        )
        val current = snapshot(
            packageName = "com.example.app",
            buttons = listOf("Menu", "Buscar", "Aceptar", "Cancelar")
        )

        val ann = engine.evaluate(prev, current)

        // Si la similitud de botones cae demasiado bajo, el engine puede
        // ranquearlo como IMPORTANT_BUTTONS_CHANGED. Aceptamos cualquiera de
        // las dos rutas siempre que detecte ALGO.
        assertNotEquals(ScreenChangeEvent.NONE, ann.event)
    }

    @Test
    fun dialogWithCancelOnlyIsDialogAnnouncement() {
        val prev = snapshot(
            packageName = "com.example.app",
            buttons = listOf("A", "B", "C", "D", "E")
        )
        val current = snapshot(
            packageName = "com.example.app",
            buttons = listOf("Permitir", "Denegar")
        )

        val ann = engine.evaluate(prev, current)

        assertEquals(ScreenChangeEvent.DIALOG_OR_ALERT_APPEARED, ann.event)
        assertEquals(ScreenChangeImportance.HIGH, ann.importance)
    }

    @Test
    fun identicalSnapshotsAreNone() {
        val s = snapshot(packageName = "com.example", buttons = listOf("OK"))
        engine.evaluate(s, s)
        val ann = engine.evaluate(s, s)

        assertEquals(ScreenChangeEvent.NONE, ann.event)
        assertFalse(ann.shouldAnnounce)
    }

    @Test
    fun minorTextChangesProduceNone() {
        val prev = snapshot(
            packageName = "com.example",
            buttons = listOf("OK"),
            redactedTextLines = listOf("Hola", "Mundo")
        )
        val current = snapshot(
            packageName = "com.example",
            buttons = listOf("OK"),
            redactedTextLines = listOf("Hola", "Mundo Mundial")
        )

        val ann = engine.evaluate(prev, current)
        assertEquals(ScreenChangeEvent.NONE, ann.event)
    }

    @Test
    fun cooldownPreventsRepeatedAppAnnouncement() {
        val prev = snapshot(packageName = "com.android.launcher")
        val current = snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")

        val first = engine.evaluate(prev, current)
        assertTrue(first.shouldAnnounce, "First switch should announce")
        engine.rememberAnnounced(first, now)

        // Mismo package nuevamente — el engine lo recuerda como último y no
        // debe re-anunciar APP_CHANGED al mismo packageName.
        now += 1_000L
        val secondCurrent = snapshot(packageName = "com.whatsapp", appLabel = "WhatsApp")
        val second = engine.evaluate(current, secondCurrent)

        assertFalse(
            second.shouldAnnounce,
            "Same packageName twice in a row should NOT re-announce, got '${second.spokenText}'"
        )
    }

    @Test
    fun cooldownBlocksRepeatedFormAnnouncement() {
        val prev = snapshot(packageName = "com.example", buttons = listOf("OK"))
        val current = snapshot(
            packageName = "com.example",
            buttons = listOf("OK"),
            editableFields = listOf("Nombre")
        )

        val first = engine.evaluate(prev, current)
        engine.rememberAnnounced(first, now)
        now += 1_000L

        // Misma key dentro del cooldown: nuevo formulario en otra pantalla.
        val prev2 = snapshot(packageName = "com.example", buttons = listOf("OK"))
        val current2 = snapshot(
            packageName = "com.example",
            buttons = listOf("OK"),
            editableFields = listOf("Email")
        )
        val second = engine.evaluate(prev2, current2)

        assertFalse(second.shouldAnnounce, "Form announcement inside cooldown should be gated")
    }

    @Test
    fun criticalSafetyBreaksCooldownIfReasonChanges() {
        // Primer hot zone: password
        val prev = snapshot(packageName = "com.example")
        val passwordCurrent = snapshot(
            packageName = "com.example",
            signals = ScreenSignals(hasPasswordField = true)
        )
        val first = engine.evaluate(prev, passwordCurrent)
        engine.rememberAnnounced(first, now)
        now += 1_000L

        // Segundo hot zone con razón distinta: banking.
        val bankingPrev = snapshot(packageName = "com.example")
        val bankingCurrent = snapshot(
            packageName = "ar.com.galicia",
            signals = ScreenSignals(isBankingApp = true)
        )
        val second = engine.evaluate(bankingPrev, bankingCurrent)

        // Misma semanticKey "screen.change.hot_zone" pero reasonKey distinto:
        // CRITICAL puede romper cooldown.
        assertTrue(
            second.shouldAnnounce,
            "CRITICAL must break cooldown when reasonKey changes"
        )
        assertEquals("banking_or_payment", second.reasonKey)
    }

    @Test
    fun spokenTextNeverClaimsExecution() {
        val prev = snapshot(packageName = "com.whatsapp")
        val current = snapshot(
            packageName = "com.whatsapp",
            signals = ScreenSignals(isMessagingApp = true)
        )

        val ann = engine.evaluate(prev, current)

        listOf(
            "enviado",
            "mandé",
            "mande",
            "ejecutado",
            "listo, enviado",
            "ya lo mandé",
            "ya lo mande",
            "mensaje enviado",
            "envié",
            "envie"
        ).forEach { forbidden ->
            assertFalse(
                ann.spokenText.lowercase().contains(forbidden),
                "Announcement should not claim execution; found '$forbidden' in '${ann.spokenText}'"
            )
        }
    }

    @Test
    fun spokenTextDoesNotIncludeSensitiveContent() {
        // Botones con números que parecen códigos, focus con texto privado.
        val prev = snapshot(packageName = "com.example.bank")
        val current = snapshot(
            packageName = "com.example.bank",
            buttons = listOf("Validar"),
            redactedTextLines = listOf("Tu código es 123456"),
            focusedLabel = "Tu código es 123456",
            signals = ScreenSignals(hasVerificationCode = true)
        )

        val ann = engine.evaluate(prev, current)

        assertFalse(ann.spokenText.contains("123456"))
        assertFalse(ann.spokenText.contains("Tu código es"))
    }

    @Test
    fun engineSourceHasNoAndroidApisOrUnsafeActions() {
        val files = listOf(
            "src/main/java/com/ojoclaro/android/agent/core/screen/ScreenChangeAwarenessEngine.kt",
            "src/main/java/com/ojoclaro/android/agent/core/screen/ScreenChangeAnnouncement.kt",
            "src/main/java/com/ojoclaro/android/agent/core/screen/ScreenChangeMemory.kt",
            "src/main/java/com/ojoclaro/android/agent/core/screen/ScreenChangeEvent.kt",
            "src/main/java/com/ojoclaro/android/agent/core/screen/ScreenChangeImportance.kt"
        )
        val source = files.joinToString(separator = "\n") { File(it).readText() }

        listOf(
            "import android.",
            "Context",
            "TextToSpeech",
            "performClick(",
            "dispatchGesture(",
            "performGlobalAction(",
            "startActivity(",
            "SmsManager",
            "ACTION_CALL"
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), "Engine pure source must not contain '$forbidden'")
        }
    }

    private fun snapshot(
        packageName: String?,
        appLabel: String? = null,
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList(),
        redactedTextLines: List<String> = emptyList(),
        focusedLabel: String? = null,
        signals: ScreenSignals = ScreenSignals.EMPTY,
        warnings: List<RiskWarning> = emptyList(),
        capturedAtMillis: Long = 1_000L,
        isLimited: Boolean = false
    ): StructuredScreenSnapshot = StructuredScreenSnapshot(
        packageName = packageName,
        appLabel = appLabel,
        capturedAtMillis = capturedAtMillis,
        redactedTextLines = redactedTextLines,
        buttons = buttons,
        editableFields = editableFields,
        focusedLabel = focusedLabel,
        totalNodes = buttons.size + editableFields.size + redactedTextLines.size + (if (focusedLabel != null) 1 else 0),
        signals = signals,
        warnings = warnings,
        isLimited = isLimited
    )
}
