package com.ojoclaro.android.agent.task.screen

import com.ojoclaro.android.agent.apps.AppCapabilityRegistry
import com.ojoclaro.android.agent.core.screen.ScreenSignals
import com.ojoclaro.android.agent.core.screen.StructuredScreenSnapshot
import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskRequiredData
import com.ojoclaro.android.agent.task.AgentTaskTicketStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AgentTaskScreenObserverTest {

    private var now = 2_000L
    private val planner = AgentTaskPlanner(
        clock = { now },
        idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
    )
    private val observer = AgentTaskScreenObserver(clock = { now })

    @Test
    fun noActivePlanReturnsNoActivePlan() {
        val result = observer.observe(null, snapshot(packageName = AppCapabilityRegistry.UBER_PACKAGE))

        assertEquals(AgentTaskScreenObservationType.NO_ACTIVE_PLAN, result.observation.type)
        assertTrue(result.spokenText!!.contains("No hay una tarea activa"))
    }

    @Test
    fun activePlanWithNullSnapshotReturnsNoSnapshot() {
        val plan = planner.plan("pedime un taxi")

        val result = observer.observe(plan, null)

        assertEquals(AgentTaskScreenObservationType.NO_SNAPSHOT, result.observation.type)
        assertTrue(result.spokenText!!.contains("lectura de pantalla"))
    }

    @Test
    fun uberSnapshotCompletesRideAppTicket() {
        val plan = planner.plan("pedime un taxi")

        val result = observer.observe(
            plan,
            snapshot(packageName = AppCapabilityRegistry.UBER_PACKAGE, appLabel = "Uber")
        )

        val appTicket = result.updatedPlan!!.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals(AgentTaskTicketStatus.COMPLETED, appTicket.status)
        assertTrue(result.spokenText!!.contains("Ya estamos en Uber"))
    }

    @Test
    fun cabifySnapshotCompletesRideAppTicket() {
        val plan = planner.plan("pedime un taxi")

        val result = observer.observe(
            plan,
            snapshot(packageName = AppCapabilityRegistry.CABIFY_PACKAGE, appLabel = "Cabify")
        )

        val appTicket = result.updatedPlan!!.tickets.first { it.title == "Buscar app de transporte" }
        assertEquals(AgentTaskTicketStatus.COMPLETED, appTicket.status)
        assertTrue(result.spokenText!!.contains("Cabify"))
    }

    @Test
    fun destinationFieldVisibleMarksDestinationWaitingForUser() {
        val plan = planner.plan("pedime un taxi")

        val result = observer.observe(
            plan,
            snapshot(
                packageName = AppCapabilityRegistry.UBER_PACKAGE,
                editableFields = listOf("Destino")
            )
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.DESTINATION)
        }
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, ticket.status)
        assertEquals(AgentTaskScreenObservationType.RIDE_DESTINATION_FIELD_VISIBLE, result.observation.type)
    }

    @Test
    fun paymentVisibleMarksPaymentRequiresConfirmation() {
        val plan = planner.plan("quiero ir a casa")

        val result = observer.observe(
            plan,
            snapshot(
                packageName = AppCapabilityRegistry.UBER_PACKAGE,
                textLines = listOf("Metodo de pago", "Efectivo")
            )
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.PAYMENT_METHOD)
        }
        assertEquals(AgentTaskTicketStatus.REQUIRES_CONFIRMATION, ticket.status)
        assertTrue(result.spokenText!!.contains("No voy a tocar"))
    }

    @Test
    fun priceOrDriverVisibleMarksPriceRequiresConfirmation() {
        val plan = planner.plan("quiero ir a casa")

        val result = observer.observe(
            plan,
            snapshot(
                packageName = AppCapabilityRegistry.UBER_PACKAGE,
                textLines = listOf("$ 4500", "Conductor Juan")
            )
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.PRICE_AND_DRIVER)
        }
        assertEquals(AgentTaskTicketStatus.REQUIRES_CONFIRMATION, ticket.status)
        assertTrue(result.spokenText!!.contains("precio"))
    }

    @Test
    fun requestRideButtonMarksFinalConfirmationRequired() {
        val plan = planner.plan("quiero ir a casa")

        val result = observer.observe(
            plan,
            snapshot(
                packageName = AppCapabilityRegistry.UBER_PACKAGE,
                buttons = listOf("Solicitar viaje")
            )
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.FINAL_RIDE_CONFIRMATION)
        }
        assertEquals(AgentTaskTicketStatus.REQUIRES_CONFIRMATION, ticket.status)
        assertTrue(result.spokenText!!.contains("No voy a pedirlo"))
    }

    @Test
    fun sensitiveScreenBlocksEnumeration() {
        val plan = planner.plan("quiero ir a casa")

        val result = observer.observe(
            plan,
            snapshot(
                packageName = AppCapabilityRegistry.BANCO_GALICIA_PACKAGE,
                textLines = listOf("Saldo", "Clave"),
                signals = ScreenSignals(isBankingApp = true, hasPasswordField = true)
            )
        )

        assertEquals(AgentTaskScreenObservationType.SENSITIVE_SCREEN_BLOCKED, result.observation.type)
        assertTrue(result.blocked)
        assertTrue(result.updates.isEmpty())
    }

    @Test
    fun rideAppOpenedMessageIsNotRepeatedWhenTicketAlreadyCompleted() {
        val plan = planner.plan("pedime un taxi")
        val first = observer.observe(plan, snapshot(packageName = AppCapabilityRegistry.UBER_PACKAGE))

        val second = observer.observe(
            first.updatedPlan,
            snapshot(packageName = AppCapabilityRegistry.UBER_PACKAGE)
        )

        assertFalse(second.spokenText!!.contains("Ya estamos en Uber"))
        assertEquals(AgentTaskScreenObservationType.TASK_SCREEN_UNCHANGED, second.observation.type)
    }

    @Test
    fun whatsappOpenedCompletesOpenTicket() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        val result = observer.observe(plan, whatsappSnapshot())

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.WHATSAPP_OPENED)
        }
        assertEquals(AgentTaskTicketStatus.COMPLETED, ticket.status)
        assertTrue(result.spokenText!!.contains("WhatsApp"))
    }

    @Test
    fun whatsappSearchVisibleActivatesSearchTicket() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        val result = observer.observe(
            plan,
            whatsappSnapshot(editableFields = listOf("Buscar"))
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.CONTACT_NAME)
        }
        assertEquals(AgentTaskTicketStatus.ACTIVE, ticket.status)
        assertEquals(AgentTaskScreenObservationType.WHATSAPP_SEARCH_VISIBLE, result.observation.type)
    }

    @Test
    fun whatsappContactVisibleMarksChatConfirmation() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        val result = observer.observe(
            plan,
            whatsappSnapshot(textLines = listOf("Sofi", "ultimo mensaje"))
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.WHATSAPP_CHAT_CONFIRMED)
        }
        assertEquals(AgentTaskTicketStatus.REQUIRES_CONFIRMATION, ticket.status)
        assertTrue(result.spokenText!!.contains("Sofi"))
    }

    @Test
    fun whatsappMessageBoxVisibleActivatesPrepareContent() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        val result = observer.observe(
            plan,
            whatsappSnapshot(editableFields = listOf("Mensaje"))
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.MESSAGE_TEXT)
        }
        assertEquals(AgentTaskTicketStatus.ACTIVE, ticket.status)
        assertEquals(AgentTaskScreenObservationType.WHATSAPP_MESSAGE_BOX_VISIBLE, result.observation.type)
    }

    @Test
    fun whatsappSendOrMicVisibleMarksFinalConfirmation() {
        val plan = planner.plan("mandale un mensaje a Sofi diciendo hola")

        val result = observer.observe(
            plan,
            whatsappSnapshot(buttons = listOf("Enviar"))
        )

        val ticket = result.updatedPlan!!.tickets.first {
            it.requiredData.contains(AgentTaskRequiredData.FINAL_MESSAGE_CONFIRMATION)
        }
        assertEquals(AgentTaskTicketStatus.REQUIRES_CONFIRMATION, ticket.status)
        assertTrue(result.spokenText!!.contains("No voy a enviar"))
    }

    @Test
    fun audioTaskNeverRecordsOrSends() {
        val plan = planner.plan("mandale un audio a Sofi diciendo llego en 10")

        val result = observer.observe(
            plan,
            whatsappSnapshot(buttons = listOf("Microfono", "Enviar"))
        )

        assertTrue(result.spokenText!!.contains("No voy a grabar ni enviar"))
        assertFalse(result.spokenText!!.contains("audio " + "enviado", ignoreCase = true))
    }

    private fun whatsappSnapshot(
        textLines: List<String> = emptyList(),
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList()
    ): StructuredScreenSnapshot = snapshot(
        packageName = AppCapabilityRegistry.WHATSAPP_PACKAGE,
        appLabel = "WhatsApp",
        textLines = textLines,
        buttons = buttons,
        editableFields = editableFields,
        signals = ScreenSignals(isMessagingApp = true)
    )

    private fun snapshot(
        packageName: String?,
        appLabel: String? = null,
        textLines: List<String> = emptyList(),
        buttons: List<String> = emptyList(),
        editableFields: List<String> = emptyList(),
        focusedLabel: String? = null,
        signals: ScreenSignals = ScreenSignals()
    ): StructuredScreenSnapshot = StructuredScreenSnapshot(
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
}
