package com.ojoclaro.android.agent.task

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AgentTaskMemoryTest {

    private var now = 1_000L
    private val planner = AgentTaskPlanner(
        clock = { now },
        idFactory = { fixedNow, suffix -> "plan-$fixedNow-$suffix" }
    )
    private val memory = AgentTaskMemory(clock = { now })

    @Test
    fun memoryStoresCurrentPlan() {
        val plan = planner.plan("pedime un taxi")

        memory.startPlan(plan)

        assertEquals(plan.id, memory.currentPlan()?.id)
        assertEquals(AgentTaskType.REQUEST_RIDE, memory.currentPlan()?.type)
    }

    @Test
    fun cancelCurrentPlanClearsCurrentPlan() {
        memory.startPlan(planner.plan("pedime un taxi"))

        val cancelled = memory.cancelCurrentPlan("user_cancelled")

        assertEquals(AgentTaskState.CANCELLED, cancelled?.status)
        assertNull(memory.currentPlan())
    }

    @Test
    fun completeTicketMarksTicketCompletedAndActivatesNextPending() {
        val plan = memory.startPlan(planner.plan("quiero ir a casa"))
        val firstTicket = plan.tickets.first()
        now += 500L

        val updated = memory.completeTicket(firstTicket.id)

        val completed = updated?.ticketById(firstTicket.id)
        assertEquals(AgentTaskTicketStatus.COMPLETED, completed?.status)
        assertEquals(now, completed?.completedAt)
        assertTrue(updated?.tickets?.any { it.status == AgentTaskTicketStatus.ACTIVE } == true)
    }

    @Test
    fun markWaitingForUserAddsMissingData() {
        val plan = memory.startPlan(planner.plan("quiero ir a casa"))
        val locationTicket = plan.tickets.first { it.title == "Revisar ubicacion actual" }

        val updated = memory.markWaitingForUser(
            ticketId = locationTicket.id,
            missingData = AgentTaskRequiredData.CURRENT_LOCATION
        )

        val ticket = updated?.ticketById(locationTicket.id)
        assertEquals(AgentTaskTicketStatus.WAITING_FOR_USER, ticket?.status)
        assertTrue(ticket?.requiredData?.contains(AgentTaskRequiredData.CURRENT_LOCATION) == true)
        assertEquals(AgentTaskState.WAITING_FOR_USER, updated?.status)
    }
}
