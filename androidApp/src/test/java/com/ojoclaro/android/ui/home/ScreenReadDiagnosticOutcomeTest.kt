package com.ojoclaro.android.ui.home

import com.ojoclaro.android.agent.core.screen.ScreenElement
import com.ojoclaro.android.agent.core.screen.ScreenElementRole
import com.ojoclaro.android.agent.core.screen.ScreenRiskAssessment
import com.ojoclaro.android.agent.core.screen.ScreenSnapshot
import com.ojoclaro.android.agent.core.screen.ScreenSummary
import com.ojoclaro.android.agent.core.screen.ScreenSummaryMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Invariante fuerte del botón diagnóstico DIRECTO de lectura de pantalla.
 *
 * [screenReadDiagnosticOutcome] es puro: solo recibe (enabled, connected,
 * snapshot, summarize). Por construcción NO puede llamar a /intent, al pipeline
 * legacy ni al AgentTaskOrchestrator, y NUNCA debe devolver "no entendí" ni un
 * ejemplo demo (Sofi). Estos tests fijan ese contrato.
 */
class ScreenReadDiagnosticOutcomeTest {

    private fun snapshot(
        pkg: String? = "com.ojoclaro.android",
        text: String = "Pantalla de Estela",
        elements: List<ScreenElement> = listOf(
            ScreenElement(label = "Estela", role = ScreenElementRole.HEADING, isInteractive = false)
        )
    ): ScreenSnapshot = ScreenSnapshot(
        packageName = pkg,
        text = text,
        elements = elements,
        capturedAtMillis = 0L
    )

    private fun safeSummary(spoken: String): ScreenSummary = ScreenSummary(
        mode = ScreenSummaryMode.SHORT,
        spokenText = spoken,
        risk = ScreenRiskAssessment.SAFE,
        isLimited = false
    )

    private fun blockedSummary(): ScreenSummary = ScreenSummary(
        mode = ScreenSummaryMode.SHORT,
        spokenText = "Esta pantalla parece sensible.",
        risk = ScreenRiskAssessment(
            warnings = emptyList(),
            isBanking = true,
            containsPasswordField = false,
            containsVerificationCode = false,
            allowedToReadAloud = false
        ),
        isLimited = true
    )

    /** summarize que falla el test si se invoca cuando no debería. */
    private val mustNotSummarize: (ScreenSnapshot) -> ScreenSummary = {
        throw AssertionError("summarize no debe llamarse antes de validar Accessibility/snapshot")
    }

    private fun assertNeverDemoNorFallback(outcome: ScreenReadDiagnosticOutcome) {
        assertFalse(outcome.spokenText.contains("Sofi", ignoreCase = true), "no debe mencionar Sofi")
        assertFalse(outcome.spokenText.contains("no entend", ignoreCase = true), "no debe decir 'no entendí'")
        assertFalse(outcome.spokenText.contains("mandale", ignoreCase = true), "no debe proponer mensajería demo")
        assertEquals(SCREEN_READ_DIAGNOSTIC_SOURCE, outcome.source, "source debe ser el snapshot real")
        assertFalse(outcome.fallbackUsed, "fallbackUsed siempre false en la ruta directa")
    }

    @Test
    fun serviceOffReturnsHonestTechnicalErrorWithoutReading() {
        val outcome = screenReadDiagnosticOutcome(
            enabled = false,
            connected = false,
            snapshot = null,
            summarize = mustNotSummarize
        )

        assertEquals(SCREEN_READ_DIAG_SERVICE_OFF, outcome.spokenText)
        assertEquals("ACCESSIBILITY_DISABLED", outcome.error)
        assertFalse(outcome.success)
        assertFalse(outcome.accessibilityEnabled)
        assertNeverDemoNorFallback(outcome)
    }

    @Test
    fun enabledButDisconnectedReturnsHonestTechnicalErrorWithoutReading() {
        val outcome = screenReadDiagnosticOutcome(
            enabled = true,
            connected = false,
            snapshot = null,
            summarize = mustNotSummarize
        )

        assertEquals(SCREEN_READ_DIAG_DISCONNECTED, outcome.spokenText)
        assertEquals("ACCESSIBILITY_NOT_CONNECTED", outcome.error)
        assertTrue(outcome.accessibilityEnabled)
        assertFalse(outcome.accessibilityConnected)
        assertFalse(outcome.success)
        assertNeverDemoNorFallback(outcome)
    }

    @Test
    fun nullSnapshotReportsPackageEmpty() {
        val outcome = screenReadDiagnosticOutcome(
            enabled = true,
            connected = true,
            snapshot = null,
            summarize = mustNotSummarize
        )

        assertEquals(SCREEN_READ_DIAG_PACKAGE_EMPTY, outcome.spokenText)
        assertEquals("PACKAGE_EMPTY", outcome.error)
        assertFalse(outcome.success)
        assertNeverDemoNorFallback(outcome)
    }

    @Test
    fun blankPackageReportsPackageEmpty() {
        val outcome = screenReadDiagnosticOutcome(
            enabled = true,
            connected = true,
            snapshot = snapshot(pkg = "  "),
            summarize = mustNotSummarize
        )

        assertEquals(SCREEN_READ_DIAG_PACKAGE_EMPTY, outcome.spokenText)
        assertEquals("PACKAGE_EMPTY", outcome.error)
        assertNeverDemoNorFallback(outcome)
    }

    @Test
    fun zeroNodesReportsNoAccessibleContent() {
        val outcome = screenReadDiagnosticOutcome(
            enabled = true,
            connected = true,
            snapshot = snapshot(elements = emptyList()),
            summarize = mustNotSummarize
        )

        assertEquals(SCREEN_READ_DIAG_ZERO_NODES, outcome.spokenText)
        assertEquals("ZERO_NODES", outcome.error)
        assertEquals(0, outcome.visibleNodeCount)
        assertFalse(outcome.success)
        assertNeverDemoNorFallback(outcome)
    }

    @Test
    fun privacyBlockedScreenReportsSecurityWithoutLeakingContent() {
        val outcome = screenReadDiagnosticOutcome(
            enabled = true,
            connected = true,
            snapshot = snapshot(),
            summarize = { blockedSummary() }
        )

        assertEquals(SCREEN_READ_DIAG_PRIVACY_BLOCKED, outcome.spokenText)
        assertEquals("PRIVACY_BLOCKED", outcome.error)
        assertFalse(outcome.success)
        assertNeverDemoNorFallback(outcome)
    }

    @Test
    fun successReadsRealSnapshotContentNotADemo() {
        val realReading = "Estás en: Estela."
        var summarizedSnapshotPackage: String? = null
        val outcome = screenReadDiagnosticOutcome(
            enabled = true,
            connected = true,
            snapshot = snapshot(pkg = "com.ojoclaro.android"),
            summarize = { snap ->
                summarizedSnapshotPackage = snap.packageName
                safeSummary(realReading)
            }
        )

        assertTrue(outcome.success)
        assertEquals(realReading, outcome.spokenText, "el éxito debe leer el contenido real del snapshot")
        assertEquals("lectura real OK", outcome.step)
        assertEquals("com.ojoclaro.android", outcome.packageName)
        assertEquals(1, outcome.visibleNodeCount)
        assertEquals("com.ojoclaro.android", summarizedSnapshotPackage, "debe resumir el snapshot real recibido")
        assertNeverDemoNorFallback(outcome)
    }
}
