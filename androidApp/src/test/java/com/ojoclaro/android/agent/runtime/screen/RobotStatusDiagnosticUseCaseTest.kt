package com.ojoclaro.android.agent.runtime.screen

import com.ojoclaro.android.agent.core.screen.ScreenContextProvider
import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.performance.RobotLoopInstrumentation
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RobotStatusDiagnosticUseCaseTest {

    @AfterTest
    fun tearDown() {
        RobotLoopInstrumentation.clear()
        RobotLoopInstrumentation.safeLogsEnabled = true
        RobotLoopInstrumentation.localSafeLogSink = null
    }

    private fun useCase(
        snapshot: ScreenSnapshot? = null,
        isReady: Boolean = true
    ): RobotStatusDiagnosticUseCase =
        RobotStatusDiagnosticUseCase(
            provider = ScreenContextProvider { snapshot },
            isAccessibilityReady = { isReady }
        )

    @Test
    fun diagnosticDoesNotIncludePrivateScreenContent() {
        val snapshot = ScreenSnapshot(
            packageName = "com.whatsapp",
            text = "Sofi: estoy llegando tarde. Mi clave es 1234.",
            elements = listOf(
                ScreenElement("Sofi: estoy llegando tarde", ScreenElementRole.TEXT, isInteractive = false),
                ScreenElement("Mensaje", ScreenElementRole.EDIT_TEXT, isInteractive = true),
                ScreenElement("Enviar", ScreenElementRole.BUTTON, isInteractive = true),
                ScreenElement("Camara", ScreenElementRole.BUTTON, isInteractive = true)
            ),
            capturedAtMillis = 0L
        )

        val result = useCase(snapshot = snapshot).handle("estado del robot")

        val spoken = result as RobotStatusDiagnosticResult.Spoken
        assertTrue(spoken.spokenText.contains("Accesibilidad activa"))
        assertTrue(spoken.spokenText.contains("App detectada: WhatsApp"))
        assertTrue(spoken.spokenText.contains("Elementos: 4"))
        assertFalse(spoken.spokenText.contains("Sofi", ignoreCase = true))
        assertFalse(spoken.spokenText.contains("estoy llegando", ignoreCase = true))
        assertFalse(spoken.spokenText.contains("1234"))
    }

    @Test
    fun diagnosticReportsAccessibilityServiceOff() {
        val result = useCase(isReady = false).handle("diagnostico")

        val spoken = result as RobotStatusDiagnosticResult.Spoken
        assertEquals(RobotStatusDiagnosticUseCase.ACCESSIBILITY_OFF_TEXT, spoken.spokenText)
    }

    @Test
    fun unrelatedPhraseIsNotDiagnosticCommand() {
        assertEquals(
            RobotStatusDiagnosticResult.NotADiagnosticCommand,
            useCase().handle("repeti")
        )
    }
}
