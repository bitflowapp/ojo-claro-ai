package com.ojoclaro.android.agent.task.execution

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.FakeInstalledAppResolver
import com.ojoclaro.android.agent.apps.SafeAppLaunchIntentSpec
import com.ojoclaro.android.agent.apps.SafeAppLauncher
import com.ojoclaro.android.agent.apps.SafeAppStarter
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskMemory
import com.ojoclaro.android.agent.task.AgentTaskOrchestrator
import com.ojoclaro.android.agent.task.AgentTaskOrchestratorResult
import com.ojoclaro.android.agent.task.AgentTaskOrchestratorResultKind
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Paquete 6F -- integracion del Safe Execution Gate en el orquestador.
 */
class AgentSafeExecutionOrchestratorTest {

    private var now = 9_000L

    @Test
    fun executeWithoutProposalReportsNoPreparedAction() {
        val orchestrator = orchestrator(installed = emptySet())

        val result = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTION_BLOCKED, result.kind)
        assertTrue(result.spokenText.contains("No hay una accion preparada"))
    }

    @Test
    fun confirmoWithoutProposalFallsBackToLegacy() {
        val orchestrator = orchestrator(installed = emptySet())

        val result = orchestrator.handle("confirmo")

        assertTrue(result is AgentTaskOrchestratorResult.NotHandled)
    }

    @Test
    fun openAppExecutionUsesSafeAppLauncher() {
        val captured = mutableListOf<SafeAppLaunchIntentSpec>()
        val launcher = SafeAppLauncher(
            resolver = FakeInstalledAppResolver(setOf(AppCapabilityRegistry.UBER_PACKAGE)),
            starter = SafeAppStarter { spec -> captured += spec; true }
        )
        val orchestrator = orchestrator(
            installed = setOf(AppCapabilityRegistry.UBER_PACKAGE),
            safeAppLauncher = launcher
        )
        orchestrator.handle("pedime un taxi para ir a casa")
        orchestrator.handle("que vas a hacer ahora")

        val result = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTED, result.kind)
        assertEquals(1, captured.size)
    }

    @Test
    fun openAppExecutionNeverTouchesInternalButtons() {
        val captured = mutableListOf<SafeAppLaunchIntentSpec>()
        val launcher = SafeAppLauncher(
            resolver = FakeInstalledAppResolver(setOf(AppCapabilityRegistry.UBER_PACKAGE)),
            starter = SafeAppStarter { spec -> captured += spec; true }
        )
        val orchestrator = orchestrator(
            installed = setOf(AppCapabilityRegistry.UBER_PACKAGE),
            safeAppLauncher = launcher
        )
        orchestrator.handle("pedime un taxi para ir a casa")
        orchestrator.handle("que vas a hacer ahora")
        orchestrator.handle("hacelo")

        // Solo se permite un intent de launcher: ACTION_MAIN + CATEGORY_LAUNCHER.
        // Nunca una accion interna de la app.
        assertTrue(captured.isNotEmpty())
        captured.forEach { spec ->
            assertEquals(SafeAppLaunchIntentSpec.ACTION_MAIN, spec.action)
            assertEquals(SafeAppLaunchIntentSpec.CATEGORY_LAUNCHER, spec.category)
        }
    }

    @Test
    fun prepareMessageExecutionStoresTextButNeverSends() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un mensaje a Sofi diciendo llego en 10")
        orchestrator.handle("prepara el mensaje")

        val result = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTED, result.kind)
        assertEquals("llego en 10", orchestrator.currentActionProposal()?.preparedText)
        assertTrue(result.spokenText.contains("Deje preparado el mensaje"))
        assertNull(result.launchPlan)
        assertFalse(result.spokenText.contains("mensaje " + "enviado", ignoreCase = true))
    }

    @Test
    fun prepareAudioExecutionStoresScriptButNeverRecordsOrSends() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un audio a Sofi diciendo llego en 10")
        orchestrator.handle("prepara el audio")

        val result = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTED, result.kind)
        assertEquals("llego en 10", orchestrator.currentActionProposal()?.preparedText)
        assertTrue(result.spokenText.contains("guion del audio"))
        val text = result.spokenText.lowercase()
        assertTrue(text.contains("no voy a grabarlo"))
        assertFalse(text.contains("audio " + "enviado"))
    }

    @Test
    fun prepareSearchExecutionStoresQueryButNeverTypes() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("busca el chat de Sofi")
        orchestrator.handle("busca el chat")

        val result = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTED, result.kind)
        assertEquals("Sofi", orchestrator.currentActionProposal()?.preparedText)
        assertTrue(result.spokenText.contains("no voy a escribir", ignoreCase = true))
    }

    @Test
    fun confirmoOnCriticalActionDoesNotExecuteIt() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un mensaje a Sofi diciendo hola")
        orchestrator.handle(
            rawUserCommand = "que vas a hacer ahora",
            currentScreenSnapshot = snapshot(
                packageName = AppCapabilityRegistry.WHATSAPP_PACKAGE,
                buttons = listOf("Enviar")
            )
        )

        val result = orchestrator.handle("confirmo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTION_BLOCKED, result.kind)
        assertNull(result.launchPlan)
        assertFalse(result.spokenText.contains("mensaje " + "enviado", ignoreCase = true))
    }

    @Test
    fun pendingExternalConfirmationBlocksExecution() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un mensaje a Sofi diciendo hola")
        orchestrator.handle("prepara el mensaje")

        val result = orchestrator.handle(
            rawUserCommand = "hacelo",
            hasPendingBridgeConfirmation = true
        ) as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTION_BLOCKED, result.kind)
    }

    @Test
    fun finalConfirmRideExecutionIsBlocked() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("pedime un taxi para ir a casa")
        orchestrator.handle(
            rawUserCommand = "que vas a hacer ahora",
            currentScreenSnapshot = snapshot(
                packageName = AppCapabilityRegistry.UBER_PACKAGE,
                buttons = listOf("Solicitar viaje")
            )
        )

        val result = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.ACTION_EXECUTION_BLOCKED, result.kind)
        val text = result.spokenText.lowercase()
        assertFalse(text.contains("taxi " + "pedido"))
        assertFalse(text.contains("viaje " + "solicitado"))
    }

    @Test
    fun executionResponsesNeverClaimCompletedSensitiveActions() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un audio a Sofi diciendo hola")
        orchestrator.handle("prepara el audio")
        val prepared = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled
        orchestrator.handle(
            rawUserCommand = "que vas a hacer ahora",
            currentScreenSnapshot = snapshot(
                packageName = AppCapabilityRegistry.WHATSAPP_PACKAGE,
                buttons = listOf("Enviar")
            )
        )
        val blocked = orchestrator.handle("hacelo") as AgentTaskOrchestratorResult.Handled

        val haystack = "${prepared.spokenText} ${blocked.spokenText}".lowercase()
        assertFalse(haystack.contains("mensaje " + "enviado"))
        assertFalse(haystack.contains("audio " + "enviado"))
        assertFalse(haystack.contains("taxi " + "pedido"))
        assertFalse(haystack.contains("viaje " + "solicitado"))
    }

    @Test
    fun executionIsRecordedInAuditLog() {
        val orchestrator = orchestrator(installed = setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un mensaje a Sofi diciendo hola")
        orchestrator.handle("prepara el mensaje")
        orchestrator.handle("hacelo")

        val audits = orchestrator.recentExecutionAudits()
        assertTrue(audits.isNotEmpty())
        assertTrue(audits.last().executed)
    }

    private fun orchestrator(
        installed: Set<String>,
        safeAppLauncher: SafeAppLauncher? = null
    ): AgentTaskOrchestrator = AgentTaskOrchestrator(
        planner = AgentTaskPlanner(
            clock = { now },
            idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
        ),
        memory = AgentTaskMemory(clock = { now }),
        installedAppResolver = FakeInstalledAppResolver(installed),
        clock = { now },
        safeAppLauncher = safeAppLauncher
    )

    private fun snapshot(
        packageName: String?,
        buttons: List<String> = emptyList(),
        textLines: List<String> = emptyList()
    ): StructuredScreenSnapshot = StructuredScreenSnapshot(
        packageName = packageName,
        appLabel = null,
        capturedAtMillis = now,
        redactedTextLines = textLines,
        buttons = buttons,
        editableFields = emptyList(),
        focusedLabel = null,
        totalNodes = buttons.size + textLines.size,
        signals = ScreenSignals(),
        warnings = emptyList(),
        isLimited = false
    )
}
