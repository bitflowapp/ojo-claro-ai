package com.ojoclaro.android.agent.runtime.routine

/**
 * Almacenamiento in-memory de Human Routine Learning v1.
 *
 * Reglas:
 *  - NO persiste a disco. Si el proceso muere, se pierde. Es preferible a
 *    persistir contenido sensible por error en v1.
 *  - Las observaciones acumulan ocurrencias por kind. NO guardan los labels
 *    múltiples — solo el más reciente como labelHint.
 *  - Las sugerencias se guardan por confirmationId; consumirlas las elimina.
 *  - El consent state default es UNSET.
 */
class HumanRoutineMemoryStore {

    private val preferences: MutableMap<String, RoutinePreference> = mutableMapOf()
    private val frequentContacts: MutableMap<String, RoutineContactEntry> = mutableMapOf()
    private val quickMessages: ArrayDeque<RoutineQuickMessage> = ArrayDeque()
    private val observations: MutableMap<String, HumanRoutineCandidate> = mutableMapOf()
    private val pendingSuggestions: MutableMap<String, HumanRoutineSuggestion> = mutableMapOf()
    private var consent: RoutineLearningConsentState = RoutineLearningConsentState.UNSET

    // ===== Preferences =====

    fun setPreference(preference: RoutinePreference): RoutinePreference {
        preferences[preference.key] = preference
        return preference
    }

    fun preference(key: String): RoutinePreference? = preferences[key]

    fun allPreferences(): List<RoutinePreference> = preferences.values.toList()

    fun clearAllPreferences() {
        preferences.clear()
    }

    // ===== Frequent contacts =====

    fun setFrequentContact(name: String, isPrimary: Boolean, nowMillis: Long): RoutineContactEntry {
        // Si llega un primary nuevo, los otros pasan a no-primary.
        if (isPrimary) {
            for ((key, entry) in frequentContacts) {
                if (entry.isPrimary && !nameMatches(entry.name, name)) {
                    frequentContacts[key] = entry.copy(isPrimary = false, updatedAtMillis = nowMillis)
                }
            }
        }
        val key = normalizeName(name)
        val entry = RoutineContactEntry(
            name = name.trim(),
            isPrimary = isPrimary,
            updatedAtMillis = nowMillis
        )
        frequentContacts[key] = entry
        return entry
    }

    fun forgetFrequentContact(name: String): Boolean {
        val key = normalizeName(name)
        return frequentContacts.remove(key) != null
    }

    fun frequentContact(name: String): RoutineContactEntry? =
        frequentContacts[normalizeName(name)]

    fun allFrequentContacts(): List<RoutineContactEntry> = frequentContacts.values.toList()

    fun clearAllFrequentContacts() {
        frequentContacts.clear()
    }

    // ===== Quick messages =====

    fun addQuickMessage(text: String, nowMillis: Long): RoutineQuickMessage {
        val entry = RoutineQuickMessage(text = text.trim(), createdAtMillis = nowMillis)
        quickMessages.addLast(entry)
        // Tope para no inflar memoria.
        while (quickMessages.size > MAX_QUICK_MESSAGES) {
            quickMessages.removeFirst()
        }
        return entry
    }

    fun lastQuickMessage(): RoutineQuickMessage? = quickMessages.lastOrNull()

    fun forgetLastQuickMessage(): RoutineQuickMessage? {
        if (quickMessages.isEmpty()) return null
        return quickMessages.removeLast()
    }

    fun allQuickMessages(): List<RoutineQuickMessage> = quickMessages.toList()

    fun clearAllQuickMessages() {
        quickMessages.clear()
    }

    // ===== Observations =====

    fun recordObservation(
        observation: HumanRoutineObservation
    ): HumanRoutineCandidate {
        val existing = observations[observation.kind]
        val updated = if (existing == null) {
            HumanRoutineCandidate(
                kind = observation.kind,
                labelHint = observation.labelHint,
                occurrenceCount = 1,
                firstObservedAtMillis = observation.observedAtMillis,
                lastObservedAtMillis = observation.observedAtMillis
            )
        } else {
            existing.copy(
                labelHint = observation.labelHint,
                occurrenceCount = existing.occurrenceCount + 1,
                lastObservedAtMillis = observation.observedAtMillis
            )
        }
        observations[observation.kind] = updated
        return updated
    }

    fun candidate(kind: String): HumanRoutineCandidate? = observations[kind]

    fun forgetCandidate(kind: String) {
        observations.remove(kind)
    }

    fun clearAllObservations() {
        observations.clear()
    }

    // ===== Suggestions =====

    fun putSuggestion(suggestion: HumanRoutineSuggestion) {
        pendingSuggestions[suggestion.confirmationId] = suggestion
    }

    fun popSuggestion(confirmationId: String): HumanRoutineSuggestion? =
        pendingSuggestions.remove(confirmationId)

    fun pendingSuggestion(confirmationId: String): HumanRoutineSuggestion? =
        pendingSuggestions[confirmationId]

    fun allPendingSuggestions(): List<HumanRoutineSuggestion> =
        pendingSuggestions.values.toList()

    fun clearAllSuggestions() {
        pendingSuggestions.clear()
    }

    // ===== Consent =====

    fun consentState(): RoutineLearningConsentState = consent

    fun setConsentState(state: RoutineLearningConsentState) {
        consent = state
    }

    /**
     * Limpia TODO. Llamado cuando el usuario dice "olvidá mis preferencias" o
     * "no guardes preferencias".
     */
    fun clearEverything() {
        preferences.clear()
        frequentContacts.clear()
        quickMessages.clear()
        observations.clear()
        pendingSuggestions.clear()
    }

    private fun normalizeName(name: String): String =
        name.trim().lowercase()

    private fun nameMatches(a: String, b: String): Boolean =
        normalizeName(a) == normalizeName(b)

    companion object {
        const val MAX_QUICK_MESSAGES: Int = 8
    }
}

data class RoutineContactEntry(
    val name: String,
    val isPrimary: Boolean,
    val updatedAtMillis: Long
)

data class RoutineQuickMessage(
    val text: String,
    val createdAtMillis: Long
)
