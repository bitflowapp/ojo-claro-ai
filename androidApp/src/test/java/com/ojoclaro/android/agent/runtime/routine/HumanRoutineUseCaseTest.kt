package com.ojoclaro.android.agent.runtime.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HumanRoutineUseCaseTest {

    private fun newUseCase(): HumanRoutineUseCase {
        // Reloj fijo para tests deterministas; min observaciones = 3.
        var t = 0L
        return HumanRoutineUseCase(
            store = HumanRoutineMemoryStore(),
            policy = HumanRoutineLearningPolicy(
                minimumOccurrencesToSuggest = 3,
                nowMillis = { ++t }
            ),
            nowMillis = { ++t }
        )
    }

    // ====== Preferencias explícitas ======

    @Test
    fun savesShortResponsePreference() {
        val u = newUseCase()
        val r = u.handleVoice("hablame más corto")
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        val pref = u.memoryStore.preference(RoutinePreferenceKeys.RESPONSE_LENGTH)
        assertNotNull(pref)
        assertEquals(RoutinePreferenceValues.LENGTH_SHORT, pref!!.value)
    }

    @Test
    fun savesSlowSpeedPreference() {
        val u = newUseCase()
        val r = u.handleVoice("hablame más lento")
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        assertEquals(
            RoutinePreferenceValues.SPEED_SLOW,
            u.memoryStore.preference(RoutinePreferenceKeys.RESPONSE_SPEED)?.value
        )
    }

    @Test
    fun disableLearningClearsAllAndChangesConsent() {
        val u = newUseCase()
        u.handleVoice("hablame más corto")
        u.handleVoice("recordá que Sofi es mi contacto principal")

        val r = u.handleVoice("no guardes mis preferencias")
        assertTrue(r is HumanRoutineMemoryResult.LearningDisabled)
        assertEquals(RoutineLearningConsentState.OPTED_OUT, u.memoryStore.consentState())
        assertTrue(u.memoryStore.allPreferences().isEmpty())
        assertTrue(u.memoryStore.allFrequentContacts().isEmpty())
    }

    // ====== Contactos frecuentes ======

    @Test
    fun savesPrimaryContact() {
        val u = newUseCase()
        val r = u.handleVoice("recordá que Sofi es mi contacto principal")
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        val saved = u.memoryStore.frequentContact("Sofi")
        assertNotNull(saved)
        assertTrue(saved!!.isPrimary)
    }

    @Test
    fun savesFrequentContact() {
        val u = newUseCase()
        val r = u.handleVoice("recordá que Marco es contacto frecuente")
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        val saved = u.memoryStore.frequentContact("Marco")
        assertNotNull(saved)
        assertEquals(false, saved!!.isPrimary)
    }

    @Test
    fun forgetFrequentContactRemovesIt() {
        val u = newUseCase()
        u.handleVoice("recordá que Marco es contacto frecuente")
        val r = u.handleVoice("olvidá a Marco como contacto frecuente")
        assertTrue(r is HumanRoutineMemoryResult.Forgotten)
        assertNull(u.memoryStore.frequentContact("Marco"))
    }

    // ====== Mensajes rápidos ======

    @Test
    fun savesQuickMessage() {
        val u = newUseCase()
        val r = u.handleVoice("recordá este mensaje rápido: estoy yendo")
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        assertEquals("estoy yendo", u.memoryStore.lastQuickMessage()?.text)
    }

    @Test
    fun savesQuotedQuickMessage() {
        val u = newUseCase()
        val r = u.handleVoice("guardá 'estoy llegando' como mensaje rápido")
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        assertEquals("estoy llegando", u.memoryStore.lastQuickMessage()?.text)
    }

    @Test
    fun forgetLastQuickMessageRemovesIt() {
        val u = newUseCase()
        u.handleVoice("recordá este mensaje rápido: estoy yendo")
        val r = u.handleVoice("olvidá ese mensaje rápido")
        assertTrue(r is HumanRoutineMemoryResult.Forgotten)
        assertNull(u.memoryStore.lastQuickMessage())
    }

    // ====== Bloqueo de sensible ======

    @Test
    fun blocksQuickMessageWithPassword() {
        val u = newUseCase()
        val r = u.handleVoice("recordá este mensaje rápido: mi contraseña es asd123")
        assertTrue(r is HumanRoutineMemoryResult.BlockedBySafety)
        val blocked = r as HumanRoutineMemoryResult.BlockedBySafety
        assertTrue(blocked.spokenText.contains("información sensible", ignoreCase = true))
        assertNull(u.memoryStore.lastQuickMessage())
    }

    @Test
    fun blocksQuickMessageWithCbu() {
        val u = newUseCase()
        val r = u.handleVoice("recordá este mensaje rápido: mi CBU es 1234567890123456789012")
        assertTrue(r is HumanRoutineMemoryResult.BlockedBySafety)
        assertNull(u.memoryStore.lastQuickMessage())
    }

    @Test
    fun blocksContactNameWithBankingTokens() {
        val u = newUseCase()
        val r = u.handleVoice("recordá que CBU es contacto frecuente")
        assertTrue(r is HumanRoutineMemoryResult.BlockedBySafety)
        assertTrue(u.memoryStore.allFrequentContacts().isEmpty())
    }

    @Test
    fun blocksContactNameWithDigits() {
        val u = newUseCase()
        val r = u.handleVoice("recordá que Marco 1234 es contacto frecuente")
        assertTrue(r is HumanRoutineMemoryResult.BlockedBySafety)
    }

    // ====== Inferencia / sugerencias ======

    @Test
    fun observationDoesNotEmitSuggestionWithoutOptIn() {
        val u = newUseCase()
        // consent default = UNSET → allowsInference = false
        repeat(5) {
            val s = u.recordObservation(
                HumanRoutineObservation(
                    kind = "compose_to_marco",
                    labelHint = "mensaje a Marco",
                    observedAtMillis = it.toLong()
                )
            )
            assertNull(s, "no suggestion should be emitted before opt-in")
        }
        assertTrue(u.memoryStore.allPendingSuggestions().isEmpty())
    }

    @Test
    fun observationEmitsSuggestionAfterOptInAndThreshold() {
        val u = newUseCase()
        u.handleVoice("podés aprender de mí")
        val s1 = u.recordObservation(obs(1))
        val s2 = u.recordObservation(obs(2))
        val s3 = u.recordObservation(obs(3))
        assertNull(s1)
        assertNull(s2)
        assertNotNull(s3)
        assertTrue(s3!!.spokenPrompt.contains("¿Querés que lo recuerde", ignoreCase = true))
        assertTrue(u.memoryStore.allPendingSuggestions().isNotEmpty())
    }

    @Test
    fun confirmSuggestionRemovesItAndAcks() {
        val u = newUseCase()
        u.handleVoice("podés aprender de mí")
        val s = run {
            u.recordObservation(obs(1))
            u.recordObservation(obs(2))
            u.recordObservation(obs(3))!!
        }
        val r = u.confirmSuggestion(s.confirmationId)
        assertTrue(r is HumanRoutineMemoryResult.Saved)
        assertNull(u.memoryStore.popSuggestion(s.confirmationId))
    }

    @Test
    fun discardSuggestionRemovesItAndAcks() {
        val u = newUseCase()
        u.handleVoice("podés aprender de mí")
        val s = run {
            u.recordObservation(obs(1))
            u.recordObservation(obs(2))
            u.recordObservation(obs(3))!!
        }
        val r = u.discardSuggestion(s.confirmationId)
        assertTrue(r is HumanRoutineMemoryResult.Forgotten)
        assertNull(u.memoryStore.popSuggestion(s.confirmationId))
    }

    @Test
    fun bannedObservationKindNeverEmitsSuggestion() {
        val u = newUseCase()
        u.handleVoice("podés aprender de mí")
        repeat(5) {
            val s = u.recordObservation(
                HumanRoutineObservation(
                    kind = "screen_text_full",
                    labelHint = "lo que sea",
                    observedAtMillis = it.toLong()
                )
            )
            assertNull(s)
        }
        assertTrue(u.memoryStore.allPendingSuggestions().isEmpty())
    }

    @Test
    fun observationDoesNotEmitSuggestionAfterOptOut() {
        val u = newUseCase()
        u.handleVoice("podés aprender de mí")
        u.recordObservation(obs(1))
        u.recordObservation(obs(2))
        u.handleVoice("no guardes mis preferencias")
        // Después del opt-out, todo se limpió y consent = OPTED_OUT.
        val s = u.recordObservation(obs(3))
        assertNull(s)
    }

    // ====== Comandos NO consumidos ======

    @Test
    fun doesNotConsumeRepeatLast() {
        val u = newUseCase()
        assertEquals(
            HumanRoutineMemoryResult.NotARoutineCommand,
            u.handleVoice("repetí")
        )
    }

    @Test
    fun doesNotConsumeScreenUnderstandingPhrases() {
        val u = newUseCase()
        listOf("qué hay en pantalla", "resumí la pantalla", "dónde estoy").forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryResult.NotARoutineCommand,
                u.handleVoice(phrase)
            )
        }
    }

    @Test
    fun doesNotConsumeWhatsAppPhrases() {
        val u = newUseCase()
        listOf("qué chats ves", "abrí WhatsApp", "mandale a Marco").forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryResult.NotARoutineCommand,
                u.handleVoice(phrase)
            )
        }
    }

    @Test
    fun doesNotConsumeControlPhrases() {
        val u = newUseCase()
        listOf("callate", "cancelar", "confirmar", "ayuda").forEach { phrase ->
            assertEquals(
                HumanRoutineMemoryResult.NotARoutineCommand,
                u.handleVoice(phrase)
            )
        }
    }

    // ====== Anti-leakage de chat / OCR ======

    @Test
    fun longLabelHintInObservationIsRejectedOrCappedByDataClass() {
        // El propio data class capa el largo. Verificamos que crear un
        // observation con label muy largo tira require().
        val tooLong = "a".repeat(500)
        try {
            HumanRoutineObservation(kind = "k", labelHint = tooLong, observedAtMillis = 0L)
            error("should have thrown")
        } catch (_: IllegalArgumentException) {
            // ok
        }
    }

    @Test
    fun chatContentInLabelHintBlockedByPolicy() {
        val u = newUseCase()
        u.handleVoice("podés aprender de mí")
        // Label hint con CBU dentro: el policy lo rechaza.
        val s = u.recordObservation(
            HumanRoutineObservation(
                kind = "compose_to_marco",
                labelHint = "saldo cbu cuenta",
                observedAtMillis = 0L
            )
        )
        assertNull(s)
        // El observation NI se trackea porque el policy lo rechaza antes.
        assertNull(u.memoryStore.candidate("compose_to_marco"))
    }

    @Test
    fun forgetAllPreferencesKeepsContactsAndQuickMessagesByDefault() {
        // "olvidá mis preferencias" SOLO limpia preferencias. Los demás stores
        // tienen su propio comando (olvidá X / olvidá ese mensaje rápido).
        val u = newUseCase()
        u.handleVoice("hablame más corto")
        u.handleVoice("recordá que Marco es contacto frecuente")
        u.handleVoice("recordá este mensaje rápido: estoy yendo")
        u.handleVoice("olvidá mis preferencias")
        assertTrue(u.memoryStore.allPreferences().isEmpty())
        assertFalse(u.memoryStore.allFrequentContacts().isEmpty())
        assertFalse(u.memoryStore.allQuickMessages().isEmpty())
    }

    private fun obs(t: Long): HumanRoutineObservation =
        HumanRoutineObservation(
            kind = "compose_to_marco",
            labelHint = "mensaje a Marco",
            observedAtMillis = t
        )
}
