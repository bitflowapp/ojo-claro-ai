package com.ojoclaro.android.agent.runtime.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HumanRoutineLearningPolicyTest {

    private val policy = HumanRoutineLearningPolicy()

    @Test
    fun acceptsValidPreference() {
        val r = policy.evaluatePreference(
            key = RoutinePreferenceKeys.RESPONSE_LENGTH,
            value = RoutinePreferenceValues.LENGTH_SHORT
        )
        assertEquals(HumanRoutineSafetyDecision.Accept, r)
    }

    @Test
    fun rejectsPreferenceKeyOutsideWhitelist() {
        val r = policy.evaluatePreference(
            key = "password",
            value = "hunter2"
        )
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
        r as HumanRoutineSafetyDecision.Reject
        assertEquals("preference_key_not_whitelisted", r.reason)
    }

    @Test
    fun rejectsPreferenceValueOutsideWhitelist() {
        val r = policy.evaluatePreference(
            key = RoutinePreferenceKeys.RESPONSE_LENGTH,
            value = "muy muy muy largo y custom"
        )
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun acceptsCleanContactName() {
        assertEquals(
            HumanRoutineSafetyDecision.Accept,
            policy.evaluateContactName("Sofi", isPrimary = true)
        )
        assertEquals(
            HumanRoutineSafetyDecision.Accept,
            policy.evaluateContactName("Marco García", isPrimary = false)
        )
    }

    @Test
    fun rejectsContactNameWithDigits() {
        val r = policy.evaluateContactName("Sofi 1234", isPrimary = false)
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun rejectsContactNameWithBankingTokens() {
        val r = policy.evaluateContactName("CBU bancario", isPrimary = false)
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun rejectsBlankContactName() {
        val r = policy.evaluateContactName("   ", isPrimary = false)
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun rejectsTooLongContactName() {
        val r = policy.evaluateContactName("a".repeat(100), isPrimary = false)
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun acceptsCleanQuickMessage() {
        assertEquals(
            HumanRoutineSafetyDecision.Accept,
            policy.evaluateQuickMessage("estoy yendo")
        )
        assertEquals(
            HumanRoutineSafetyDecision.Accept,
            policy.evaluateQuickMessage("estoy llegando")
        )
    }

    @Test
    fun rejectsQuickMessageWithPassword() {
        val r = policy.evaluateQuickMessage("mi contraseña es asd123")
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
        r as HumanRoutineSafetyDecision.Reject
        assertTrue(r.spokenText.contains("información sensible", ignoreCase = true))
    }

    @Test
    fun rejectsQuickMessageWithCbu() {
        val r = policy.evaluateQuickMessage("mi CBU es 1234567890123456789012")
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun rejectsQuickMessageWithCardNumber() {
        val r = policy.evaluateQuickMessage("4111 1111 1111 1111")
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun rejectsTooLongQuickMessage() {
        val r = policy.evaluateQuickMessage("a".repeat(500))
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun rejectsBlankQuickMessage() {
        val r = policy.evaluateQuickMessage("   ")
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }

    @Test
    fun acceptsCleanObservation() {
        val r = policy.evaluateObservation(
            HumanRoutineObservation(
                kind = "compose_message_to_contact",
                labelHint = "mensaje a Marco",
                observedAtMillis = 0L
            )
        )
        assertEquals(HumanRoutineSafetyDecision.Accept, r)
    }

    @Test
    fun rejectsObservationWithBannedKind() {
        listOf(
            "screen_text_full",
            "ocr_text_full",
            "chat_message_content",
            "password_field",
            "verification_code",
            "card_number",
            "screen_capture"
        ).forEach { kind ->
            val r = policy.evaluateObservation(
                HumanRoutineObservation(
                    kind = kind,
                    labelHint = "irrelevant",
                    observedAtMillis = 0L
                )
            )
            assertTrue(
                r is HumanRoutineSafetyDecision.Reject,
                "kind '$kind' must be banned"
            )
        }
    }

    @Test
    fun rejectsObservationWithFinancialLabelHint() {
        val r = policy.evaluateObservation(
            HumanRoutineObservation(
                kind = "compose_message_to_contact",
                labelHint = "saldo bancario CBU",
                observedAtMillis = 0L
            )
        )
        assertTrue(r is HumanRoutineSafetyDecision.Reject)
    }
}
