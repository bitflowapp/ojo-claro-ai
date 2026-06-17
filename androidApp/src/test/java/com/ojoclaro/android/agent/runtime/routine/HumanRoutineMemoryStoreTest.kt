package com.ojoclaro.android.agent.runtime.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HumanRoutineMemoryStoreTest {

    @Test
    fun preferencesAreSetAndCleared() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(
            RoutinePreference(
                key = RoutinePreferenceKeys.RESPONSE_LENGTH,
                value = RoutinePreferenceValues.LENGTH_SHORT,
                updatedAtMillis = 0L
            )
        )
        assertEquals(
            RoutinePreferenceValues.LENGTH_SHORT,
            store.preference(RoutinePreferenceKeys.RESPONSE_LENGTH)?.value
        )
        store.clearAllPreferences()
        assertNull(store.preference(RoutinePreferenceKeys.RESPONSE_LENGTH))
    }

    @Test
    fun setPrimaryContactDemotesPreviousPrimary() {
        val store = HumanRoutineMemoryStore()
        store.setFrequentContact("Sofi", isPrimary = true, nowMillis = 1L)
        store.setFrequentContact("Marco", isPrimary = true, nowMillis = 2L)
        val all = store.allFrequentContacts().sortedBy { it.name }
        val sofi = all.first { it.name == "Sofi" }
        val marco = all.first { it.name == "Marco" }
        assertEquals(false, sofi.isPrimary)
        assertEquals(true, marco.isPrimary)
    }

    @Test
    fun forgetContactRemovesIt() {
        val store = HumanRoutineMemoryStore()
        store.setFrequentContact("Marco", isPrimary = false, nowMillis = 1L)
        assertTrue(store.forgetFrequentContact("Marco"))
        assertNull(store.frequentContact("Marco"))
    }

    @Test
    fun quickMessagesAreAddedAndCapped() {
        val store = HumanRoutineMemoryStore()
        repeat(15) { i ->
            store.addQuickMessage("msg $i", nowMillis = i.toLong())
        }
        assertTrue(store.allQuickMessages().size <= HumanRoutineMemoryStore.MAX_QUICK_MESSAGES)
    }

    @Test
    fun forgetLastQuickMessageRemovesLatest() {
        val store = HumanRoutineMemoryStore()
        store.addQuickMessage("uno", 1L)
        store.addQuickMessage("dos", 2L)
        val removed = store.forgetLastQuickMessage()
        assertNotNull(removed)
        assertEquals("dos", removed!!.text)
        assertEquals("uno", store.lastQuickMessage()?.text)
    }

    @Test
    fun observationCountIncrementsByKind() {
        val store = HumanRoutineMemoryStore()
        repeat(3) {
            store.recordObservation(
                HumanRoutineObservation(
                    kind = "compose_to_marco",
                    labelHint = "mensaje a Marco",
                    observedAtMillis = it.toLong()
                )
            )
        }
        val candidate = store.candidate("compose_to_marco")
        assertNotNull(candidate)
        assertEquals(3, candidate!!.occurrenceCount)
    }

    @Test
    fun consentDefaultIsUnsetAndCanBeChanged() {
        val store = HumanRoutineMemoryStore()
        assertEquals(RoutineLearningConsentState.UNSET, store.consentState())
        store.setConsentState(RoutineLearningConsentState.OPTED_IN)
        assertEquals(RoutineLearningConsentState.OPTED_IN, store.consentState())
    }

    @Test
    fun clearEverythingWipesAllStores() {
        val store = HumanRoutineMemoryStore()
        store.setPreference(RoutinePreference(
            key = RoutinePreferenceKeys.RESPONSE_LENGTH,
            value = RoutinePreferenceValues.LENGTH_SHORT,
            updatedAtMillis = 0L
        ))
        store.setFrequentContact("Marco", isPrimary = false, nowMillis = 1L)
        store.addQuickMessage("hola", 0L)
        store.recordObservation(HumanRoutineObservation(
            kind = "k", labelHint = "h", observedAtMillis = 0L
        ))
        store.putSuggestion(HumanRoutineSuggestion(
            confirmationId = "c1",
            candidate = HumanRoutineCandidate(
                kind = "k", labelHint = "h", occurrenceCount = 3,
                firstObservedAtMillis = 0L, lastObservedAtMillis = 0L
            ),
            spokenPrompt = "x"
        ))

        store.clearEverything()

        assertTrue(store.allPreferences().isEmpty())
        assertTrue(store.allFrequentContacts().isEmpty())
        assertTrue(store.allQuickMessages().isEmpty())
        assertNull(store.candidate("k"))
        assertTrue(store.allPendingSuggestions().isEmpty())
    }

    @Test
    fun popSuggestionRemovesIt() {
        val store = HumanRoutineMemoryStore()
        val s = HumanRoutineSuggestion(
            confirmationId = "c1",
            candidate = HumanRoutineCandidate(
                kind = "k", labelHint = "h", occurrenceCount = 3,
                firstObservedAtMillis = 0L, lastObservedAtMillis = 0L
            ),
            spokenPrompt = "x"
        )
        store.putSuggestion(s)
        assertEquals(s, store.popSuggestion("c1"))
        assertNull(store.popSuggestion("c1"))
    }

    @Test
    fun forgetContactReturnsFalseWhenNotPresent() {
        val store = HumanRoutineMemoryStore()
        assertFalse(store.forgetFrequentContact("Nadie"))
    }
}
