package com.ojoclaro.android.agent.core.memory

import com.ojoclaro.android.agent.core.AgentPreferenceKeys
import com.ojoclaro.android.agent.core.AgentPreferenceSource
import com.ojoclaro.android.agent.core.AgentUserPreference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AgentPersonalMemoryPolicyTest {

    private val policy = AgentPersonalMemoryPolicy()

    @Test
    fun explicitUserMemoryIsAllowed() {
        val decision = policy.evaluateWrite(
            request = AgentMemoryWriteRequest(
                label = "Sofi",
                value = "es mi contacto principal",
                source = AgentMemoryWriteSource.USER_EXPLICIT
            ),
            learningOptedIn = false
        )
        assertTrue(decision is AgentMemoryWriteDecision.Allowed)
    }

    @Test
    fun inferredWithoutOptInIsSilentlyRejected() {
        val decision = policy.evaluateWrite(
            request = AgentMemoryWriteRequest(
                label = "casa",
                value = "destino frecuente",
                source = AgentMemoryWriteSource.INFERRED
            ),
            learningOptedIn = false
        )
        assertTrue(decision is AgentMemoryWriteDecision.Rejected)
        (decision as AgentMemoryWriteDecision.Rejected).let {
            assertEquals("learning_opt_out", it.reason)
            assertEquals("", it.spokenText)
        }
    }

    @Test
    fun inferredWithOptInRequiresConfirmation() {
        val decision = policy.evaluateWrite(
            request = AgentMemoryWriteRequest(
                label = "Usás Maps mucho para volver a casa",
                value = "destino casa",
                source = AgentMemoryWriteSource.INFERRED
            ),
            learningOptedIn = true
        )
        assertTrue(decision is AgentMemoryWriteDecision.NeedsConfirmation)
        val confirmation = decision as AgentMemoryWriteDecision.NeedsConfirmation
        assertTrue(confirmation.confirmationPrompt.contains("recuerde", ignoreCase = true))
        assertTrue(confirmation.confirmationPrompt.contains("confirmar", ignoreCase = true))
    }

    @Test
    fun blockedTokensRejected() {
        val rejected = listOf(
            "tarjeta", "contraseña", "clave", "pin", "banco", "cbu",
            "dni", "obra social", "diagnostico", "api key"
        )
        rejected.forEach { token ->
            val decision = policy.evaluateWrite(
                request = AgentMemoryWriteRequest(
                    label = "x",
                    value = "esto incluye $token sensible",
                    source = AgentMemoryWriteSource.USER_EXPLICIT
                ),
                learningOptedIn = true
            )
            assertTrue(
                decision is AgentMemoryWriteDecision.Rejected,
                "should reject memory with token: $token"
            )
        }
    }

    @Test
    fun longValueRejected() {
        val decision = policy.evaluateWrite(
            request = AgentMemoryWriteRequest(
                label = "x",
                value = "a".repeat(2000),
                source = AgentMemoryWriteSource.USER_EXPLICIT
            ),
            learningOptedIn = false
        )
        assertTrue(decision is AgentMemoryWriteDecision.Rejected)
    }

    @Test
    fun emptyRejected() {
        val decision = policy.evaluateWrite(
            request = AgentMemoryWriteRequest(
                label = "",
                value = "x",
                source = AgentMemoryWriteSource.USER_EXPLICIT
            ),
            learningOptedIn = false
        )
        assertTrue(decision is AgentMemoryWriteDecision.Rejected)
    }

    @Test
    fun readMissingReturnsHonestMessage() {
        val decision = policy.evaluateRead(
            key = AgentPreferenceKeys.HOME_DESTINATION,
            currentPreferences = emptyList()
        )
        assertTrue(decision is AgentMemoryReadDecision.Missing)
    }

    @Test
    fun readPendingInferredAsksConfirmationBeforeReading() {
        val decision = policy.evaluateRead(
            key = AgentPreferenceKeys.HOME_DESTINATION,
            currentPreferences = listOf(
                AgentUserPreference(
                    key = AgentPreferenceKeys.HOME_DESTINATION,
                    value = "Avenida Corrientes 1234",
                    source = AgentPreferenceSource.INFERRED_PENDING_CONFIRMATION,
                    updatedAtMillis = 0L
                )
            )
        )
        assertTrue(decision is AgentMemoryReadDecision.NeedsConfirmation)
    }

    @Test
    fun readApplicablePreferenceAllowed() {
        val decision = policy.evaluateRead(
            key = AgentPreferenceKeys.RESPONSE_LENGTH,
            currentPreferences = listOf(
                AgentUserPreference(
                    key = AgentPreferenceKeys.RESPONSE_LENGTH,
                    value = "short",
                    source = AgentPreferenceSource.USER_EXPLICIT,
                    updatedAtMillis = 0L
                )
            )
        )
        assertTrue(decision is AgentMemoryReadDecision.Allowed)
        assertEquals("short", (decision as AgentMemoryReadDecision.Allowed).value)
    }

    @Test
    fun proposeFromObservationWaitsForMinimumOccurrences() {
        val decision = policy.proposeFromObservation(
            observation = AgentLearningObservation(
                label = "casa",
                value = "destino frecuente",
                occurrenceCount = 1,
                minimumOccurrencesToPropose = 3
            ),
            learningOptedIn = true
        )
        assertTrue(decision is AgentMemoryWriteDecision.Rejected)
        (decision as AgentMemoryWriteDecision.Rejected).let {
            assertEquals("not_enough_observations", it.reason)
        }
    }

    @Test
    fun proposeFromObservationWithEnoughOccurrencesAsksConfirmation() {
        val decision = policy.proposeFromObservation(
            observation = AgentLearningObservation(
                label = "Usás Maps para volver a casa",
                value = "casa",
                occurrenceCount = 5,
                minimumOccurrencesToPropose = 3
            ),
            learningOptedIn = true
        )
        assertTrue(decision is AgentMemoryWriteDecision.NeedsConfirmation)
    }

    @Test
    fun proposeFromObservationRespectsOptOut() {
        val decision = policy.proposeFromObservation(
            observation = AgentLearningObservation(
                label = "x",
                value = "y",
                occurrenceCount = 10
            ),
            learningOptedIn = false
        )
        assertTrue(decision is AgentMemoryWriteDecision.Rejected)
    }

    @Test
    fun mapWriteDecisionToPreferenceSource() {
        assertEquals(
            AgentPreferenceSource.USER_EXPLICIT,
            AgentMemoryWriteDecision.Allowed("ok").toPreferenceSource()
        )
        assertEquals(
            AgentPreferenceSource.INFERRED_PENDING_CONFIRMATION,
            AgentMemoryWriteDecision.NeedsConfirmation("", "").toPreferenceSource()
        )
    }
}
