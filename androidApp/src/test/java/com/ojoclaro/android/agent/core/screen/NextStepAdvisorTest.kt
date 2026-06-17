package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.risk.RiskWarning
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class NextStepAdvisorTest {

    private val advisor = NextStepAdvisor()

    @Test
    fun nullSnapshotReturnsNoSnapshot() {
        val advice = advisor.advise(null, NextStepQueryKind.WHAT_NOW)

        assertTrue(advice is NextStepAdvice.NoSnapshot)
        assertTrue(advice.spokenText.contains("lectura", ignoreCase = true))
    }

    @Test
    fun emptySnapshotReturnsNoSnapshot() {
        val advice = advisor.advise(
            snapshot = StructuredScreenSnapshot.empty(1_000L),
            kind = NextStepQueryKind.WHAT_NOW
        )

        assertTrue(advice is NextStepAdvice.NoSnapshot)
    }

    @Test
    fun bankingScreenReturnsSafetyBlocked() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "ar.com.galicia",
                appLabel = "Banco Galicia",
                buttons = listOf("Transferir", "Pagar"),
                signals = ScreenSignals(isBankingApp = true, hasPaymentOrTransferSignals = true)
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val safety = advice as? NextStepAdvice.SafetyBlocked
        assertNotNull(safety)
        assertEquals("banking_or_payment", safety.reasonKey)
        // No debe enumerar botones de banca.
        assertFalse(safety.spokenText.contains("Transferir", ignoreCase = true))
        assertFalse(safety.spokenText.contains("Pagar", ignoreCase = true))
    }

    @Test
    fun passwordFieldReturnsSafetyBlocked() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example.login",
                buttons = listOf("Ingresar"),
                signals = ScreenSignals(hasPasswordField = true)
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val safety = advice as? NextStepAdvice.SafetyBlocked
        assertNotNull(safety)
        assertEquals("password_field", safety.reasonKey)
        assertFalse(safety.spokenText.contains("Ingresar", ignoreCase = true))
    }

    @Test
    fun verificationCodeReturnsSafetyBlocked() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example.bank",
                buttons = listOf("Validar"),
                signals = ScreenSignals(hasVerificationCode = true)
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val safety = advice as? NextStepAdvice.SafetyBlocked
        assertNotNull(safety)
        assertEquals("verification_code", safety.reasonKey)
    }

    @Test
    fun singleButtonReturnsSingleAction() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                buttons = listOf("Continuar")
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val single = advice as? NextStepAdvice.SingleAction
        assertNotNull(single)
        assertEquals("Continuar", single.buttonLabel)
        assertEquals("WhatsApp", single.appLabel)
        assertTrue(single.spokenText.contains("Continuar"))
        assertTrue(single.spokenText.contains("tocar", ignoreCase = true))
    }

    @Test
    fun multipleButtonsReturnMultipleActions() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example.shop",
                appLabel = "Tienda",
                buttons = listOf("Comprar", "Agregar al carrito", "Volver")
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val multi = advice as? NextStepAdvice.MultipleActions
        assertNotNull(multi)
        assertEquals(3, multi.buttonLabels.size)
        assertTrue(multi.spokenText.contains("Comprar"))
        assertTrue(multi.spokenText.contains("Volver"))
    }

    @Test
    fun emptyFormReturnsNoActionsDetected() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example",
                buttons = emptyList(),
                editableFields = emptyList(),
                redactedTextLines = listOf("Bienvenido")
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        assertTrue(advice is NextStepAdvice.NoActionsDetected, "Got $advice")
    }

    @Test
    fun editableFieldPriorityOverButton() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example.form",
                appLabel = "Form",
                buttons = listOf("Enviar"),
                editableFields = listOf("Nombre")
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val form = advice as? NextStepAdvice.FormFillNeeded
        assertNotNull(form, "Expected FormFillNeeded, got $advice")
        assertEquals("Nombre", form.fieldLabel)
        assertTrue(form.spokenText.contains("completar", ignoreCase = true))
    }

    @Test
    fun whereIsButtonUsesFocusedLabelIfAvailable() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example",
                appLabel = "Demo",
                buttons = listOf("Confirmar", "Cancelar"),
                focusedLabel = "Confirmar"
            ),
            kind = NextStepQueryKind.WHERE_IS_BUTTON
        )

        val single = advice as? NextStepAdvice.SingleAction
        assertNotNull(single)
        assertEquals("Confirmar", single.buttonLabel)
        assertTrue(single.spokenText.contains("enfocado", ignoreCase = true) ||
            single.spokenText.contains("principal", ignoreCase = true))
    }

    @Test
    fun adviceNeverClaimsExecution() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.whatsapp",
                appLabel = "WhatsApp",
                buttons = listOf("Enviar")
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

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
                advice.spokenText.lowercase().contains(forbidden),
                "Advice should not claim execution; found '$forbidden' in '${advice.spokenText}'"
            )
        }
    }

    @Test
    fun adviceTextSafelyTruncatesLongLabels() {
        val veryLongLabel = "A".repeat(500)
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.example",
                buttons = listOf(veryLongLabel)
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val single = advice as? NextStepAdvice.SingleAction
        assertNotNull(single)
        assertTrue(
            single.buttonLabel.length <= 60,
            "Long button labels must be truncated, got ${single.buttonLabel.length} chars"
        )
    }

    @Test
    fun whatNowToleratesUnknownPackage() {
        val advice = advisor.advise(
            snapshot = snapshot(
                packageName = "com.unknown.app",
                appLabel = null,
                buttons = listOf("Aceptar")
            ),
            kind = NextStepQueryKind.WHAT_NOW
        )

        val single = advice as? NextStepAdvice.SingleAction
        assertNotNull(single)
        // appLabel puede ser null si el package no se reconoce. Aceptamos eso.
        assertEquals("Aceptar", single.buttonLabel)
    }

    @Test
    fun advisorSourceHasNoAndroidApisOrUnsafeActions() {
        val source = File(
            "src/main/java/com/ojoclaro/android/agent/core/screen/NextStepAdvisor.kt"
        ).readText()

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
            assertFalse(source.contains(forbidden), forbidden)
        }
    }

    @Test
    fun advisorSourceNeverClaimsExecution() {
        val source = File(
            "src/main/java/com/ojoclaro/android/agent/core/screen/NextStepAdvisor.kt"
        ).readText().lowercase()

        listOf(
            "mensaje enviado",
            "ya lo mande",
            "ya lo mandé",
            "listo, enviado",
            "accion ejecutada",
            "acción ejecutada"
        ).forEach { forbidden ->
            assertFalse(source.contains(forbidden), forbidden)
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
        totalNodes = buttons.size + editableFields.size + redactedTextLines.size,
        signals = signals,
        warnings = warnings,
        isLimited = isLimited
    )
}
