package com.ojoclaro.android.agent.task

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.fail

class AgentTaskPlannerTest {

    private var now = 1_000L
    private val planner = AgentTaskPlanner(
        clock = { now },
        idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
    )

    @Test
    fun pedimeUnTaxiCreatesRequestRidePlan() {
        val plan = planner.plan("pedime un taxi")

        assertEquals(AgentTaskType.REQUEST_RIDE, plan.type)
        assertEquals("Pedir viaje", plan.title)
        assertEquals(AgentTaskRiskLevel.CRITICAL, plan.riskLevel)
        assertEquals(6, plan.tickets.size)
    }

    @Test
    fun pediUnUberDetectsUberHint() {
        val plan = planner.plan("pedi un Uber")

        val appTicket = plan.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals("Uber", appTicket.appPackageHint)
    }

    @Test
    fun buscameUnCabifyDetectsCabifyHint() {
        val plan = planner.plan("buscame un Cabify")

        val appTicket = plan.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals("Cabify", appTicket.appPackageHint)
    }

    @Test
    fun ridePlanAlwaysRequiresFinalConfirmation() {
        val plan = planner.plan("conseguime un viaje")

        assertTrue(plan.requiresFinalConfirmation)
        assertTrue(
            plan.tickets.any {
                it.title == "Confirmacion final para solicitar viaje" &&
                    it.confirmationRequired
            }
        )
    }

    @Test
    fun rideRequestTicketIsNeverSafeForAutomation() {
        val plan = planner.plan("pedime un auto")

        val finalTicket = plan.tickets.first { it.title == "Confirmacion final para solicitar viaje" }
        assertFalse(finalTicket.safeForAutomation)
    }

    @Test
    fun paymentMethodHasHighRisk() {
        val plan = planner.plan("pedime un taxi")

        val paymentTicket = plan.tickets.first { it.title == "Revisar metodo de pago" }
        assertEquals(AgentTaskRiskLevel.HIGH, paymentTicket.riskLevel)
        assertTrue(paymentTicket.confirmationRequired)
    }

    @Test
    fun finalConfirmationHasCriticalRisk() {
        val plan = planner.plan("pedime un taxi")

        val finalTicket = plan.tickets.first { it.title == "Confirmacion final para solicitar viaje" }
        assertEquals(AgentTaskRiskLevel.CRITICAL, finalTicket.riskLevel)
        assertTrue(finalTicket.confirmationRequired)
    }

    @Test
    fun missingDestinationLeavesDestinationTicketWaitingForUser() {
        val plan = planner.plan("pedime un taxi")

        val destinationTicket = plan.tickets.first { it.title == "Confirmar destino" }
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, destinationTicket.status)
        assertTrue(destinationTicket.requiredData.contains(AgentTaskRequiredData.DESTINATION))
    }

    @Test
    fun destinationInCommandIsCapturedAsResolvedData() {
        val plan = planner.plan("quiero ir a casa")

        val destinationTicket = plan.tickets.first { it.title == "Confirmar destino" }
        assertEquals(
            "casa",
            destinationTicket.resolvedData[AgentTaskRequiredData.DESTINATION]
        )
        assertFalse(destinationTicket.missingData.contains(AgentTaskRequiredData.DESTINATION))
    }

    @Test
    fun plannerRecognizesRequiredRidePhrases() {
        val phrases = listOf(
            "pedime un taxi",
            "llamame un taxi",
            "pedi un uber",
            "conseguime un viaje",
            "quiero ir a casa",
            "pedime un auto",
            "buscame un cabify"
        )

        phrases.forEach { phrase ->
            assertEquals(AgentTaskType.REQUEST_RIDE, planner.plan(phrase).type, phrase)
        }
    }

    @Test
    fun requestRidePlanDoesNotStoreSensitiveValues() {
        val plan = planner.plan(
            "pedime un taxi a mi casa con tarjeta 4111 1111 1111 1111 otp 445566 y mi clave es secreto123"
        )
        val serialized = plan.toString()

        assertFalse(serialized.contains("4111 1111 1111 1111"))
        assertFalse(serialized.contains("445566"))
        assertFalse(serialized.contains("secreto123"))
        val destinationTicket = plan.tickets.first { it.title == "Confirmar destino" }
        assertNull(destinationTicket.resolvedData[AgentTaskRequiredData.DESTINATION])
    }

    @Test
    fun safeSpeechNeverClaimsRideWasRequested() {
        val plan = planner.plan("pedime un taxi")
        val speech = plan.safeSummaryForSpeech.lowercase()

        assertFalse(speech.contains("taxi " + "pedido"))
        assertFalse(speech.contains("viaje " + "solicitado"))
        assertTrue(speech.contains("confirmacion final"))
    }

    @Test
    fun taskPackageDoesNotInvokeDangerousAccessibilityActions() {
        val baseDir = listOf(
            File("src/main/java/com/ojoclaro/android/agent/task"),
            File("androidApp/src/main/java/com/ojoclaro/android/agent/task")
        ).firstOrNull { it.exists() }
            ?: fail("could not locate agent task source dir")
        val forbidden = listOf(
            Regex("\\bperformClick\\s*\\("),
            Regex("\\bdispatchGesture\\s*\\("),
            Regex("\\bperformGlobalAction\\s*\\(")
        )

        val offenders = baseDir.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "java") }
            .flatMap { file ->
                val text = file.readText()
                forbidden.mapNotNull { regex ->
                    if (regex.containsMatchIn(text)) file.name else null
                }
            }
            .toList()

        assertTrue(offenders.isEmpty(), "forbidden action invocations: $offenders")
    }

    @Test
    fun unknownCommandReturnsUnknownBlockedPlan() {
        val plan = planner.plan("contame un chiste")

        assertEquals(AgentTaskType.UNKNOWN, plan.type)
        assertEquals(AgentTaskState.BLOCKED, plan.status)
    }

    @Test
    fun knownAppPackageCanBackDetectedHint() {
        val plan = planner.plan(
            rawUserCommand = "pedi un uber",
            knownApps = listOf(AgentTaskKnownApp(displayName = "Uber", packageName = "com.ubercab"))
        )

        val appTicket = plan.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals("com.ubercab", appTicket.appPackageHint)
    }

    @Test
    fun whatsappMessageCommandCreatesMessageTask() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        assertEquals(AgentTaskType.SEND_WHATSAPP_MESSAGE, plan.type)
        assertTrue(plan.safeSummaryForSpeech.contains("No voy a enviarlo"))
    }

    @Test
    fun whatsappAudioCommandCreatesAudioTask() {
        val plan = planner.plan("mandale un audio a Sofi diciendo llego en 10")

        assertEquals(AgentTaskType.SEND_WHATSAPP_AUDIO, plan.type)
        assertTrue(plan.safeSummaryForSpeech.contains("No voy a grabarlo"))
    }

    @Test
    fun whatsappTaskExtractsContactName() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        val contactTicket = plan.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME)
        }
        assertEquals("Sofi", contactTicket.resolvedData[AgentTaskRequiredData.CONTACT_NAME])
    }

    @Test
    fun whatsappTaskExtractsMessageText() {
        val plan = planner.plan("escribile a Juan que ya sali")

        val messageTicket = plan.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT)
        }
        assertEquals("ya sali", messageTicket.resolvedData[AgentTaskRequiredData.MESSAGE_TEXT])
    }

    @Test
    fun whatsappTaskWaitsWhenContactIsMissing() {
        val plan = planner.plan("anda a WhatsApp")

        val contactTicket = plan.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME)
        }
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, contactTicket.status)
        assertTrue(plan.safeStatusSummary().contains("contacto"))
    }

    @Test
    fun whatsappTaskWaitsWhenContentIsMissing() {
        val plan = planner.plan("prepara un mensaje para papa")

        val messageTicket = plan.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT)
        }
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, messageTicket.status)
    }

    @Test
    fun whatsappSpeechNeverClaimsMessageOrAudioWasSent() {
        val message = planner.plan("mandale un mensaje a Sofi diciendo hola")
        val audio = planner.plan("mandale un audio a Sofi diciendo hola")
        val speech = "${message.safeSummaryForSpeech} ${audio.safeSummaryForSpeech}".lowercase()

        assertFalse(speech.contains("mensaje " + "enviado"))
        assertFalse(speech.contains("audio " + "enviado"))
    }
}
