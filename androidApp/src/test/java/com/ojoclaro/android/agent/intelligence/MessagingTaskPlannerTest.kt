package com.ojoclaro.android.agent.intelligence

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * V2.2 — planificador seguro de mensajería. PURO. NO ejecutado (disco C: crítico;
 * ver docs/V22_WHATSAPP_INSTAGRAM_REAL_SMOKE.md).
 */
class MessagingTaskPlannerTest {

    private fun convScreen(title: String?, app: ActiveApp = ActiveApp.WHATSAPP, risky: List<RiskyControl> = emptyList()) =
        ScreenModel(app, ScreenType.CONVERSATION, title, emptyList(), 2, true, true, risky, false, Confidence.HIGH, "")

    private fun listScreen(app: ActiveApp = ActiveApp.WHATSAPP) =
        ScreenModel(app, ScreenType.CHAT_LIST, null, emptyList(), 0, false, false, emptyList(), false, Confidence.HIGH, "")

    private val marco = ResolvedContact("Marco Luna", TargetApp.WHATSAPP, ResolutionSource.EXACT_VISIBLE, 0)

    @Test
    fun planForWhatsappMarcoLuna() {
        val plan = MessagingTaskPlanner.plan(
            MessagingGoal(TargetApp.WHATSAPP, "Marco Luna", "hola probando"),
            listScreen(ActiveApp.WHATSAPP),
            ContactResolution.Resolved(marco),
        )
        assertTrue(plan is MessagingPlan.Steps, "got $plan")
        plan as MessagingPlan.Steps
        assertTrue(PlanStep.OPEN_CHAT in plan.steps)
        assertTrue(PlanStep.VERIFY_CHAT_MATCHES in plan.steps)
        assertTrue(PlanStep.ASK_STRONG_CONFIRMATION in plan.steps)
        assertTrue(plan.spoken.contains("Marco Luna"))
        assertTrue(plan.spoken.contains("mandalo"))
    }

    @Test
    fun planForInstagramSofi() {
        val sofi = ResolvedContact("Sofía", TargetApp.INSTAGRAM, ResolutionSource.PARTIAL_VISIBLE, 0)
        val plan = MessagingTaskPlanner.plan(
            MessagingGoal(TargetApp.INSTAGRAM, "Sofi", "hola"),
            listScreen(ActiveApp.INSTAGRAM),
            ContactResolution.Resolved(sofi),
        )
        assertTrue(plan is MessagingPlan.Steps, "got $plan")
        assertTrue((plan as MessagingPlan.Steps).spoken.contains("Instagram"))
    }

    @Test
    fun ambiguousResolutionAsks() {
        val cands = listOf(
            ContactCandidate("Marco Luna", index = 0, app = TargetApp.WHATSAPP),
            ContactCandidate("Marco Pérez", index = 1, app = TargetApp.WHATSAPP),
        )
        val plan = MessagingTaskPlanner.plan(
            MessagingGoal(TargetApp.WHATSAPP, "Marco", "hola"),
            listScreen(), ContactResolution.Ambiguous(cands),
        )
        assertTrue(plan is MessagingPlan.Ask, "got $plan")
    }

    @Test
    fun notFoundAsks() {
        val plan = MessagingTaskPlanner.plan(
            MessagingGoal(TargetApp.WHATSAPP, "Pedro", "hola"),
            listScreen(), ContactResolution.NotFound("no está"),
        )
        assertTrue(plan is MessagingPlan.Ask, "got $plan")
    }

    @Test
    fun sensitiveMessageIsRefused() {
        val plan = MessagingTaskPlanner.plan(
            MessagingGoal(TargetApp.WHATSAPP, "Marco", "mi cvv es 123"),
            listScreen(), ContactResolution.Resolved(marco),
        )
        assertTrue(plan is MessagingPlan.Refuse, "got $plan")
    }

    @Test
    fun confirmationClassification() {
        assertEquals(ConfirmationVerdict.WEAK_REJECTED, MessagingTaskPlanner.classifyConfirmation("sí"))
        assertEquals(ConfirmationVerdict.WEAK_REJECTED, MessagingTaskPlanner.classifyConfirmation("dale"))
        assertEquals(ConfirmationVerdict.WEAK_REJECTED, MessagingTaskPlanner.classifyConfirmation("ok"))
        assertEquals(ConfirmationVerdict.STRONG_SEND, MessagingTaskPlanner.classifyConfirmation("mandalo"))
        assertEquals(ConfirmationVerdict.STRONG_SEND, MessagingTaskPlanner.classifyConfirmation("mandalo prueba autorizada"))
        assertEquals(ConfirmationVerdict.CANCEL, MessagingTaskPlanner.classifyConfirmation("cancelar"))
        assertEquals(ConfirmationVerdict.CANCEL, MessagingTaskPlanner.classifyConfirmation("no mandes nada"))
    }

    @Test
    fun canSendOnlyWithStrongConfirmAndVerifiedChat() {
        assertTrue(
            MessagingTaskPlanner.canSend("mandalo", marco, convScreen("Marco Luna"), chatVerified = true, preparedTextMatchesField = true)
        )
    }

    @Test
    fun weakAffirmativeNeverSends() {
        assertFalse(
            MessagingTaskPlanner.canSend("sí", marco, convScreen("Marco Luna"), chatVerified = true, preparedTextMatchesField = true)
        )
    }

    @Test
    fun neverSendsToWrongChat() {
        // pending para Marco Luna, pero el chat actual es Sofía → NO enviar.
        assertFalse(
            MessagingTaskPlanner.canSend("mandalo", marco, convScreen("Sofía"), chatVerified = true, preparedTextMatchesField = true)
        )
    }

    @Test
    fun neverSendsOutsideConversation() {
        assertFalse(
            MessagingTaskPlanner.canSend("mandalo", marco, listScreen(), chatVerified = true, preparedTextMatchesField = true)
        )
    }

    @Test
    fun neverSendsWithPaymentControlsVisible() {
        assertFalse(
            MessagingTaskPlanner.canSend("mandalo", marco, convScreen("Marco Luna", risky = listOf(RiskyControl.PAYMENT)), chatVerified = true, preparedTextMatchesField = true)
        )
    }

    @Test
    fun neverSendsWhenTextDoesNotMatchField() {
        assertFalse(
            MessagingTaskPlanner.canSend("mandalo", marco, convScreen("Marco Luna"), chatVerified = true, preparedTextMatchesField = false)
        )
    }
}
