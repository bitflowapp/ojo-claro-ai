package com.ojoclaro.android.agent.task.followup

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.apps.FakeInstalledAppResolver
import com.ojoclaro.android.agent.core.AgentCoreFeatureFlags
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskMemory
import com.ojoclaro.android.agent.task.AgentTaskOrchestrator
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskRequiredData
import com.ojoclaro.android.agent.task.AgentTaskTicketStatus
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObservation
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenObservationType
import com.ojoclaro.android.agent.task.screen.AgentTaskScreenUpdateResult
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentTaskFollowUpCoordinatorTest {

    private var now = 10_000L

    @Test
    fun noActiveTaskDoesNothingAndDoesNotObserve() {
        var observeCalls = 0
        val decision = coordinator().onSnapshot(
            currentPlan = null,
            currentSnapshot = uberSnapshot(),
            observeScreenForCurrentTask = {
                observeCalls += 1
                noChangeResult()
            }
        )

        assertEquals(AgentTaskFollowUpAction.NO_OP, decision.action)
        assertEquals(AgentTaskFollowUpSuppressReason.NO_ACTIVE_TASK, decision.suppressReason)
        assertEquals(0, observeCalls)
    }

    @Test
    fun activeRideTaskChangingToUberObservesAndSpeaksArrival() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("pedime un taxi")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(orchestrator, uberSnapshot())

        assertEquals(AgentTaskFollowUpAction.SPEAK, decision.action)
        assertTrue(decision.spokenText!!.contains("Ya estamos en Uber"))
        val openTicket = orchestrator.currentPlan()!!.tickets.first { it.title == "Abrir Uber" }
        assertEquals(AgentTaskTicketStatus.COMPLETED, openTicket.status)
    }

    @Test
    fun repeatedUberFollowUpWithinCooldownIsSuppressedBySemanticKey() {
        val plan = AgentTaskPlanner(
            clock = { now },
            idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
        ).plan("pedime un taxi")
        val coordinator = coordinator()
        val event = AgentTaskFollowUpEvent(
            currentPlan = plan,
            previousSnapshot = launcherSnapshot(),
            currentSnapshot = uberSnapshot(),
            nowMillis = now
        )
        val observation = changedObservation(
            type = AgentTaskScreenObservationType.RIDE_APP_OPENED,
            text = "Ya estamos en Uber."
        )

        val first = coordinator.decide(event) { observation }
        now += 100L
        val second = coordinator.decide(event.copy(nowMillis = now)) { observation }

        assertEquals(AgentTaskFollowUpAction.SPEAK, first.action)
        assertEquals(AgentTaskFollowUpAction.SUPPRESS, second.action)
        assertEquals(AgentTaskFollowUpSuppressReason.COOLDOWN, second.suppressReason)
    }

    @Test
    fun rideDestinationFieldUpdatesTicketAndSpeaksGuidance() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("quiero ir a casa")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(
            orchestrator,
            uberSnapshot(editableFields = listOf("Destino"))
        )

        val ticket = orchestrator.currentPlan()!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.DESTINATION)
        }
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, ticket.status)
        assertEquals(AgentTaskFollowUpAction.SPEAK, decision.action)
        assertTrue(decision.spokenText!!.contains("campo para destino"))
    }

    @Test
    fun rideRequestButtonRequiresFinalConfirmation() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("quiero ir a casa")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(
            orchestrator,
            uberSnapshot(buttons = listOf("Solicitar viaje"))
        )

        val ticket = orchestrator.currentPlan()!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.FINAL_RIDE_CONFIRMATION)
        }
        assertEquals(AgentTaskTicketStatus.REQUIRES_CONFIRMATION, ticket.status)
        assertEquals(AgentTaskFollowUpAction.SPEAK, decision.action)
        assertTrue(decision.spokenText!!.contains("No voy a pedirlo"))
    }

    @Test
    fun whatsappOpenedUpdatesTicketAndSpeaksArrival() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un audio a Sofi diciendo llego en 10")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(orchestrator, whatsappSnapshot())

        val ticket = orchestrator.currentPlan()!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.WHATSAPP_OPENED)
        }
        assertEquals(AgentTaskTicketStatus.COMPLETED, ticket.status)
        assertEquals(AgentTaskFollowUpAction.SPEAK, decision.action)
        assertTrue(decision.spokenText!!.contains("Ya estamos en WhatsApp"))
    }

    @Test
    fun whatsappMessageBoxMakesPrepareContentActive() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        orchestrator.handle("mandale un audio a Sofi diciendo llego en 10")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        coordinator.follow(orchestrator, whatsappSnapshot())
        now += 100L
        val decision = coordinator.follow(
            orchestrator,
            whatsappSnapshot(editableFields = listOf("Mensaje"))
        )

        val ticket = orchestrator.currentPlan()!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT)
        }
        assertEquals(AgentTaskTicketStatus.ACTIVE, ticket.status)
        assertEquals(AgentTaskFollowUpAction.SPEAK, decision.action)
        assertTrue(decision.spokenText!!.contains("Veo el campo para escribir"))
        assertNoForbiddenCompletionClaims(decision.spokenText!!)
    }

    @Test
    fun pendingConfirmationSuppressesNormalAnnouncementButStillUpdatesMemory() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("pedime un taxi")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(
            orchestrator = orchestrator,
            snapshot = uberSnapshot(),
            pending = true
        )

        assertEquals(AgentTaskFollowUpAction.SUPPRESS, decision.action)
        assertEquals(AgentTaskFollowUpSuppressReason.PENDING_CONFIRMATION, decision.suppressReason)
        val openTicket = orchestrator.currentPlan()!!.tickets.first { it.title == "Abrir Uber" }
        assertEquals(AgentTaskTicketStatus.COMPLETED, openTicket.status)
    }

    @Test
    fun pendingConfirmationAllowsCriticalSensitiveWarning() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("pedime un taxi")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(
            orchestrator = orchestrator,
            snapshot = bankingSnapshot(),
            pending = true
        )

        assertEquals(AgentTaskFollowUpAction.SPEAK, decision.action)
        assertEquals(AgentTaskFollowUpImportance.CRITICAL, decision.importance)
        assertTrue(decision.spokenText!!.contains("datos sensibles"))
    }

    @Test
    fun bankingPasswordOrOtpScreenDoesNotEnumerateSensitiveText() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("pedime un taxi")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val decision = coordinator.follow(
            orchestrator,
            bankingSnapshot(textLines = listOf("Clave 1234", "Saldo 999999", "OTP 777777"))
        )

        val spoken = decision.spokenText!!
        assertFalse(spoken.contains("1234"))
        assertFalse(spoken.contains("999999"))
        assertFalse(spoken.contains("777777"))
        assertFalse(spoken.contains("Clave", ignoreCase = true))
    }

    @Test
    fun minimalTextChangeDoesNotObserveOrSpeak() {
        val plan = AgentTaskPlanner(
            clock = { now },
            idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
        ).plan("mandale un mensaje a Sofi diciendo hola")
        var observeCalls = 0

        val decision = coordinator().decide(
            AgentTaskFollowUpEvent(
                currentPlan = plan,
                previousSnapshot = whatsappSnapshot(textLines = listOf("mensaje anterior")),
                currentSnapshot = whatsappSnapshot(textLines = listOf("mensaje nuevo")),
                nowMillis = now
            )
        ) {
            observeCalls += 1
            noChangeResult()
        }

        assertEquals(AgentTaskFollowUpAction.NO_OP, decision.action)
        assertEquals(AgentTaskFollowUpSuppressReason.NO_RELEVANT_CHANGE, decision.suppressReason)
        assertEquals(0, observeCalls)
    }

    @Test
    fun flagOffPreservesManualLegacyObserverOnly() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("pedime un taxi")
        val coordinator = coordinator(enabled = false)

        val decision = coordinator.follow(orchestrator, uberSnapshot())

        assertEquals(AgentTaskFollowUpAction.NO_OP, decision.action)
        assertEquals(AgentTaskFollowUpSuppressReason.FLAG_DISABLED, decision.suppressReason)
        val openTicket = orchestrator.currentPlan()!!.tickets.first { it.title == "Abrir Uber" }
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, openTicket.status)
    }

    @Test
    fun followUpSourceDoesNotInvokeDangerousAccessibilityActions() {
        val forbiddenInvocations = listOf(
            Regex("\\bperform" + "Click\\s*\\("),
            Regex("\\bdispatch" + "Gesture\\s*\\("),
            Regex("\\bperform" + "GlobalAction\\s*\\(")
        )
        val relativePath = "src/main/java/com/ojoclaro/android/agent/task/followup"
        val candidates = listOf(
            File(relativePath),
            File("androidApp/$relativePath"),
            File(System.getProperty("user.dir") ?: ".", relativePath),
            File(System.getProperty("user.dir") ?: ".", "androidApp/$relativePath")
        )
        val baseDir = candidates.firstOrNull { it.exists() }
            ?: fail("could not locate follow-up source dir")
        val offenders = baseDir.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val content = file.readText()
                forbiddenInvocations
                    .filter { it.containsMatchIn(content) }
                    .map { "${file.name} matches ${it.pattern}" }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "forbidden invocations detected: $offenders")
    }

    @Test
    fun automaticSpeechNeverClaimsSensitiveActionsCompleted() {
        val orchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.UBER_PACKAGE))
        orchestrator.handle("quiero ir a casa")
        val coordinator = coordinator()

        coordinator.follow(orchestrator, launcherSnapshot())
        now += 100L
        val rideFinal = coordinator.follow(
            orchestrator,
            uberSnapshot(buttons = listOf("Solicitar viaje"))
        )

        val whatsAppOrchestrator = taskOrchestrator(setOf(AppCapabilityRegistry.WHATSAPP_PACKAGE))
        whatsAppOrchestrator.handle("mandale un audio a Sofi diciendo llego en 10")
        val whatsAppCoordinator = coordinator()
        whatsAppCoordinator.follow(whatsAppOrchestrator, launcherSnapshot())
        now += 100L
        val whatsAppSend = whatsAppCoordinator.follow(
            whatsAppOrchestrator,
            whatsappSnapshot(buttons = listOf("Microfono", "Enviar"))
        )

        assertNoForbiddenCompletionClaims("${rideFinal.spokenText} ${whatsAppSend.spokenText}")
    }

    private fun coordinator(enabled: Boolean = true): AgentTaskFollowUpCoordinator =
        AgentTaskFollowUpCoordinator(
            flags = {
                AgentCoreFeatureFlags(
                    accessibilityRuntimeContextEnabled = true,
                    screenChangeAwarenessEnabled = true,
                    taskAutoFollowUpEnabled = enabled
                )
            },
            clock = { now }
        )

    private fun AgentTaskFollowUpCoordinator.follow(
        orchestrator: AgentTaskOrchestrator,
        snapshot: StructuredScreenSnapshot,
        pending: Boolean = false
    ): AgentTaskFollowUpDecision =
        onSnapshot(
            currentPlan = orchestrator.currentPlan(),
            currentSnapshot = snapshot,
            hasPendingConfirmation = pending,
            observeScreenForCurrentTask = orchestrator::observeScreenForCurrentTask
        )

    private fun taskOrchestrator(
        installedPackages: Set<String>
    ): AgentTaskOrchestrator {
        val memory = AgentTaskMemory(clock = { now })
        return AgentTaskOrchestrator(
            planner = AgentTaskPlanner(
                clock = { now },
                idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
            ),
            memory = memory,
            installedAppResolver = FakeInstalledAppResolver(installedPackages),
            clock = { now }
        )
    }

    private fun changedObservation(
        type: AgentTaskScreenObservationType,
        text: String
    ): AgentTaskScreenUpdateResult =
        AgentTaskScreenUpdateResult(
            observation = AgentTaskScreenObservation(
                type = type,
                packageName = AppCapabilityRegistry.UBER_PACKAGE,
                appLabel = "Uber"
            ),
            updatedPlan = AgentTaskPlanner(
                clock = { now },
                idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
            ).plan("pedime un taxi"),
            spokenText = text,
            safeStatusText = "status",
            changed = true
        )

    private fun noChangeResult(): AgentTaskScreenUpdateResult =
        AgentTaskScreenUpdateResult(
            observation = AgentTaskScreenObservation(AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED),
            updatedPlan = null,
            spokenText = null,
            safeStatusText = ""
        )

    private fun launcherSnapshot(): StructuredScreenSnapshot =
        snapshot(
            packageName = "com.android.launcher",
            appLabel = "Launcher"
        )

    private fun uberSnapshot(
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList()
    ): StructuredScreenSnapshot =
        snapshot(
            packageName = AppCapabilityRegistry.UBER_PACKAGE,
            appLabel = "Uber",
            buttons = buttons,
            editableFields = editableFields
        )

    private fun whatsappSnapshot(
        textLines: List<String> = emptyList(),
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList()
    ): StructuredScreenSnapshot =
        snapshot(
            packageName = AppCapabilityRegistry.WHATSAPP_PACKAGE,
            appLabel = "WhatsApp",
            textLines = textLines,
            buttons = buttons,
            editableFields = editableFields,
            signals = ScreenSignals(isMessagingApp = true)
        )

    private fun bankingSnapshot(
        textLines: List<String> = listOf("Clave 1234", "OTP 777777")
    ): StructuredScreenSnapshot =
        snapshot(
            packageName = AppCapabilityRegistry.BANCO_GALICIA_PACKAGE,
            appLabel = "Banco",
            textLines = textLines,
            signals = ScreenSignals(
                isBankingApp = true,
                hasPasswordField = true,
                hasVerificationCode = true
            )
        )

    private fun snapshot(
        packageName: String?,
        appLabel: String? = null,
        textLines: List<String> = emptyList(),
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList(),
        focusedLabel: String? = null,
        signals: ScreenSignals = ScreenSignals()
    ): StructuredScreenSnapshot =
        StructuredScreenSnapshot(
            packageName = packageName,
            appLabel = appLabel,
            capturedAtMillis = now,
            redactedTextLines = textLines,
            buttons = buttons,
            editableFields = editableFields,
            focusedLabel = focusedLabel,
            totalNodes = textLines.size + buttons.size + editableFields.size,
            signals = signals,
            warnings = emptyList(),
            isLimited = false
        )

    private fun assertNoForbiddenCompletionClaims(text: String) {
        val normalized = AgentTaskPlanner.normalize(text)
        val forbidden = listOf(
            "taxi " + "pedido",
            "viaje " + "solicitado",
            "mensaje " + "enviado",
            "audio " + "enviado"
        )
        forbidden.forEach { phrase ->
            assertFalse(
                normalized.contains(phrase),
                "speech must not claim completed sensitive action: $phrase"
            )
        }
    }
}
