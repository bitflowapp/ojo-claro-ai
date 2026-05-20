package com.ojoclaro.android.agent.task

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.FakeInstalledAppResolver
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTaskOrchestratorTest {

    private var now = 1_000L
    private val memory = AgentTaskMemory(clock = { now })
    private val orchestrator = AgentTaskOrchestrator(
        planner = AgentTaskPlanner(
            clock = { now },
            idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
        ),
        memory = memory
    )

    @Test
    fun rideCommandStartsPlanAndAsksForDestinationWhenMissing() {
        val result = orchestrator.handle("pedime un taxi")

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null, "Expected handled result, got $result")
        assertEquals(AgentTaskOrchestratorResultKind.PLAN_STARTED, handled.kind)
        assertTrue(handled.spokenText.contains("A donde queres ir?"))
        assertEquals(AgentTaskType.REQUEST_RIDE, memory.currentPlan()?.type)
    }

    @Test
    fun whatAreYouDoingReturnsSafeTaskSummary() {
        orchestrator.handle("pedime un taxi")

        val result = orchestrator.handle("que estas haciendo")

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskOrchestratorResultKind.STATUS, handled.kind)
        assertEquals(
            "Estoy preparando la tarea Pedir viaje. Falta confirmar destino.",
            handled.spokenText
        )
    }

    @Test
    fun cancelTaskClearsActivePlan() {
        orchestrator.handle("pedime un taxi")

        val result = orchestrator.handle("cancelar tarea")

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskOrchestratorResultKind.CANCELLED, handled.kind)
        assertNull(memory.currentPlan())
    }

    @Test
    fun pendingBridgeConfirmationBlocksNewTask() {
        val result = orchestrator.handle(
            rawUserCommand = "pedime un taxi",
            hasPendingBridgeConfirmation = true
        )

        val handled = result as? AgentTaskOrchestratorResult.Handled
        assertTrue(handled != null)
        assertEquals(AgentTaskOrchestratorResultKind.BLOCKED_BY_PENDING_CONFIRMATION, handled.kind)
        assertTrue(handled.spokenText.contains("confirmar o cancelar", ignoreCase = true))
        assertNull(memory.currentPlan())
    }

    @Test
    fun unsupportedCommandFallsBackToLegacy() {
        val result = orchestrator.handle("contame un chiste")

        assertTrue(result is AgentTaskOrchestratorResult.NotHandled)
    }

    @Test
    fun statusTextDoesNotClaimRideWasRequested() {
        orchestrator.handle("pedime un taxi")

        val status = orchestrator.handle("que falta") as AgentTaskOrchestratorResult.Handled

        assertFalse(status.spokenText.contains("taxi " + "pedido", ignoreCase = true))
        assertFalse(status.spokenText.contains("viaje " + "solicitado", ignoreCase = true))
    }

    @Test
    fun requestRideWithUberInstalledCompletesSearchTicket() {
        val memory = AgentTaskMemory(clock = { now })
        val orchestrator = taskOrchestrator(
            memory = memory,
            installedPackages = setOf(AppCapabilityRegistry.UBER_PACKAGE)
        )

        val result = orchestrator.handle("pedime un taxi") as AgentTaskOrchestratorResult.Handled

        val plan = result.plan!!
        val search = plan.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals(AgentTaskTicketStatus.COMPLETED, search.status)
        assertEquals("Uber", search.resolvedData[AgentTaskRequiredData.RIDE_APP])
        assertEquals(AppCapabilityRegistry.UBER_PACKAGE, search.appPackageHint)
        assertTrue(result.spokenText.contains("Encontre Uber"))
    }

    @Test
    fun requestRideWithoutRideAppsBlocksSearchTicket() {
        val memory = AgentTaskMemory(clock = { now })
        val orchestrator = taskOrchestrator(memory = memory, installedPackages = emptySet())

        val result = orchestrator.handle("pedime un taxi") as AgentTaskOrchestratorResult.Handled

        val search = result.plan!!.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals(AgentTaskTicketStatus.BLOCKED, search.status)
        assertTrue(result.spokenText.contains("No encontre una app de transporte instalada"))
    }

    @Test
    fun openUberWithoutActivePlanProducesSafeLaunchPlan() {
        val orchestrator = taskOrchestrator(
            memory = AgentTaskMemory(clock = { now }),
            installedPackages = setOf(AppCapabilityRegistry.UBER_PACKAGE)
        )

        val result = orchestrator.handle("abri Uber") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.APP_LAUNCH_READY, result.kind)
        assertEquals(AppCapabilityRegistry.UBER_PACKAGE, result.launchPlan?.packageName)
        assertFalse(result.spokenText.contains("viaje " + "solicitado", ignoreCase = true))
    }

    @Test
    fun useUberWithActivePlanAddsOpenTicket() {
        val memory = AgentTaskMemory(clock = { now })
        val orchestrator = taskOrchestrator(
            memory = memory,
            installedPackages = setOf(AppCapabilityRegistry.UBER_PACKAGE)
        )
        orchestrator.handle("pedime un taxi")

        val result = orchestrator.handle("usa Uber") as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.APP_LAUNCH_READY, result.kind)
        assertEquals(AppCapabilityRegistry.UBER_PACKAGE, result.launchPlan?.packageName)
        assertTrue(
            result.plan!!.tickets.any {
                it.title == "Abrir Uber" &&
                    it.status == AgentTaskTicketStatus.ACTIVE
            }
        )
    }

    @Test
    fun successfulAppLaunchCompletesOpenTicketWithoutCompletingRide() {
        val memory = AgentTaskMemory(clock = { now })
        val orchestrator = taskOrchestrator(
            memory = memory,
            installedPackages = setOf(AppCapabilityRegistry.UBER_PACKAGE)
        )
        orchestrator.handle("pedime un taxi")
        orchestrator.handle("usa Uber")

        val updated = orchestrator.onSafeAppLaunchResult(
            packageName = AppCapabilityRegistry.UBER_PACKAGE,
            launched = true
        )

        val openTicket = updated!!.tickets.first { it.title == "Abrir Uber" }
        assertEquals(AgentTaskTicketStatus.COMPLETED, openTicket.status)
        assertEquals(AgentTaskState.WAITING_FOR_USER, updated.status)
        assertTrue(updated.requiresFinalConfirmation)
    }

    @Test
    fun pendingConfirmationBlocksNewAppOpening() {
        val orchestrator = taskOrchestrator(
            memory = AgentTaskMemory(clock = { now }),
            installedPackages = setOf(AppCapabilityRegistry.UBER_PACKAGE)
        )

        val result = orchestrator.handle(
            rawUserCommand = "abri Uber",
            hasPendingBridgeConfirmation = true
        ) as AgentTaskOrchestratorResult.Handled

        assertEquals(AgentTaskOrchestratorResultKind.BLOCKED_BY_PENDING_CONFIRMATION, result.kind)
        assertTrue(result.launchPlan == null)
    }

    @Test
    fun launchSpeechDoesNotClaimTaxiRequestedOrDriverNotified() {
        val orchestrator = taskOrchestrator(
            memory = AgentTaskMemory(clock = { now }),
            installedPackages = setOf(AppCapabilityRegistry.UBER_PACKAGE)
        )

        val result = orchestrator.handle("abri Uber") as AgentTaskOrchestratorResult.Handled

        assertFalse(result.spokenText.contains("taxi " + "pedido", ignoreCase = true))
        assertFalse(result.spokenText.contains("viaje " + "solicitado", ignoreCase = true))
        assertFalse(result.spokenText.contains("conductor " + "avisado", ignoreCase = true))
    }

    private fun taskOrchestrator(
        memory: AgentTaskMemory,
        installedPackages: Set<String>
    ): AgentTaskOrchestrator =
        AgentTaskOrchestrator(
            planner = AgentTaskPlanner(
                clock = { now },
                idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
            ),
            memory = memory,
            installedAppResolver = FakeInstalledAppResolver(installedPackages),
            clock = { now }
        )
}
