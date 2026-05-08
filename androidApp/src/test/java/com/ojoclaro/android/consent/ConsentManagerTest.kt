package com.ojoclaro.android.consent

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertIs

class ConsentManagerTest {

    private val ttl = 60_000L
    private val now = 1_000_000L

    private fun manager() = ConsentManager(
        ttlMillis = ttl,
        idFactory = { "fixed-id" }
    )

    @Test
    fun openExternalAppDoesNotRequireConfirmation() {
        val decision = manager().requestAction(
            type = SensitiveActionType.OPEN_EXTERNAL_APP,
            spokenExplanation = "Abriendo WhatsApp.",
            nowMillis = now
        )
        assertIs<ConsentDecision.AllowedImmediately>(decision)
    }

    @Test
    fun composeMessageRequiresSimpleConfirmation() {
        val decision = manager().requestAction(
            type = SensitiveActionType.COMPOSE_MESSAGE,
            spokenExplanation = "Voy a preparar un mensaje para ContactoDemo que dice: estoy llegando.",
            payload = mapOf("contact" to "ContactoDemo", "message" to "estoy llegando"),
            nowMillis = now
        )
        val needs = assertIs<ConsentDecision.NeedsConfirmation>(decision)
        assertEquals(SensitiveActionType.COMPOSE_MESSAGE, needs.pending.type)
        assertEquals(ConsentLevel.SIMPLE_CONFIRMATION, needs.pending.requiresConsentLevel)
        assertEquals("ContactoDemo", needs.pending.payload["contact"])
        assertEquals("estoy llegando", needs.pending.payload["message"])
        assertEquals(now + ttl, needs.pending.expiresAtMillis)
    }

    @Test
    fun readVisibleMessageRequiresSimpleConfirmation() {
        val decision = manager().requestAction(
            type = SensitiveActionType.READ_VISIBLE_MESSAGE,
            spokenExplanation = ConsentPhrases.READ_VISIBLE_MESSAGE,
            nowMillis = now
        )
        val needs = assertIs<ConsentDecision.NeedsConfirmation>(decision)
        assertEquals(ConsentLevel.SIMPLE_CONFIRMATION, needs.pending.requiresConsentLevel)
    }

    @Test
    fun readPasswordFieldIsRejectedAlways() {
        val decision = manager().requestAction(
            type = SensitiveActionType.READ_PASSWORD_FIELD,
            spokenExplanation = "ignored",
            nowMillis = now
        )
        val rejected = assertIs<ConsentDecision.Rejected>(decision)
        assertEquals(ConsentPhrases.READ_PASSWORD_FIELD_REJECTED, rejected.spokenText)
    }

    @Test
    fun readBankingScreenIsRejectedWhileBiometricNotImplemented() {
        val decision = manager().requestAction(
            type = SensitiveActionType.READ_BANKING_SCREEN,
            spokenExplanation = "ignored",
            nowMillis = now
        )
        val rejected = assertIs<ConsentDecision.Rejected>(decision)
        assertEquals(ConsentPhrases.READ_BANKING_SCREEN, rejected.spokenText)
    }

    @Test
    fun confirmSimpleWithoutPendingRespondsClearly() {
        val decision = manager().confirmSimple(pending = null, nowMillis = now)
        val noPending = assertIs<ConsentDecision.NoPending>(decision)
        assertEquals(ConsentPhrases.NO_PENDING_CONFIRMATION, noPending.spokenText)
    }

    @Test
    fun confirmSimpleWithExpiredPendingRespondsExpired() {
        val mgr = manager()
        val pending = (mgr.requestAction(
            type = SensitiveActionType.COMPOSE_MESSAGE,
            spokenExplanation = "x",
            nowMillis = now
        ) as ConsentDecision.NeedsConfirmation).pending

        val decision = mgr.confirmSimple(pending = pending, nowMillis = now + ttl + 1)
        val expired = assertIs<ConsentDecision.Expired>(decision)
        assertEquals(ConsentPhrases.EXPIRED_ACTION, expired.spokenText)
    }

    @Test
    fun confirmSimpleWithValidPendingReturnsConfirmed() {
        val mgr = manager()
        val pending = (mgr.requestAction(
            type = SensitiveActionType.COMPOSE_MESSAGE,
            spokenExplanation = "x",
            nowMillis = now
        ) as ConsentDecision.NeedsConfirmation).pending

        val decision = mgr.confirmSimple(pending = pending, nowMillis = now + 100)
        val confirmed = assertIs<ConsentDecision.Confirmed>(decision)
        assertEquals(SensitiveActionType.COMPOSE_MESSAGE, confirmed.pending.type)
    }

    @Test
    fun cancelWithoutPendingRespondsClearly() {
        val decision = manager().cancel(pending = null)
        val noPending = assertIs<ConsentDecision.NoPending>(decision)
        assertEquals(ConsentPhrases.NO_PENDING_CANCELLATION, noPending.spokenText)
    }

    @Test
    fun cancelWithPendingRespondsCancelled() {
        val mgr = manager()
        val pending = (mgr.requestAction(
            type = SensitiveActionType.COMPOSE_MESSAGE,
            spokenExplanation = "x",
            nowMillis = now
        ) as ConsentDecision.NeedsConfirmation).pending

        val decision = mgr.cancel(pending = pending)
        val cancelled = assertIs<ConsentDecision.Cancelled>(decision)
        assertEquals(ConsentPhrases.ACTION_CANCELLED, cancelled.spokenText)
    }

    @Test
    fun expireIfNeededDropsExpiredPending() {
        val mgr = manager()
        val pending = (mgr.requestAction(
            type = SensitiveActionType.COMPOSE_MESSAGE,
            spokenExplanation = "x",
            nowMillis = now
        ) as ConsentDecision.NeedsConfirmation).pending

        assertNull(mgr.expireIfNeeded(pending, nowMillis = now + ttl + 1))
        assertEquals(pending, mgr.expireIfNeeded(pending, nowMillis = now + 100))
    }

    @Test
    fun unknownSensitiveDefaultsToSimpleConfirmation() {
        val decision = manager().requestAction(
            type = SensitiveActionType.UNKNOWN_SENSITIVE,
            spokenExplanation = "Confirmá para continuar.",
            nowMillis = now
        )
        assertIs<ConsentDecision.NeedsConfirmation>(decision)
    }

    @Test
    fun composeMessageHelperFormatsCorrectly() {
        val text = ConsentPhrases.composeMessage("ContactoDemo", "estoy llegando")
        assertTrue(text.contains("ContactoDemo"))
        assertTrue(text.contains("estoy llegando"))
        assertTrue(text.contains("No lo envío automáticamente"))
    }
}
