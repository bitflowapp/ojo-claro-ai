package com.ojoclaro.android.agent

import com.ojoclaro.android.memory.PersonalAgentMemory
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.PersonalMemoryType

data class SuggestionContext(
    val originalText: String,
    val normalizedText: String,
    val currentTimeMillis: Long,
    val agentState: AgentState,
    val appState: com.ojoclaro.android.model.AppState,
    val memorySnapshot: PersonalMemorySnapshot,
    val externalApp: String? = null,
    val suggestionsEnabled: Boolean = true
)

class ContextualSuggestionEngine(
    private val cooldownStore: SuggestionCooldownStore
) {
    constructor() : this(SharedPreferencesSuggestionCooldownStoreFallback())

    fun evaluate(context: SuggestionContext): List<AgentSuggestion> {
        if (!context.suggestionsEnabled) return emptyList()

        val suggestions = buildList {
            maybeSuggestWorkRoute(context)?.let(::add)
            maybeSuggestPendingTask(context)?.let(::add)
            maybeSuggestMedication(context)?.let(::add)
        }

        suggestions.forEach { suggestion ->
            cooldownStore.markShown(
                suggestion.cooldownKey,
                SuggestionPolicy.defaultCooldownMillis(suggestion.type),
                context.currentTimeMillis
            )
        }
        return suggestions
    }

    private fun maybeSuggestWorkRoute(context: SuggestionContext): AgentSuggestion? {
        val normalized = context.normalizedText.lowercase()
        val hasLaburo = context.memorySnapshot.places.any { place ->
            place.value.contains("laburo", ignoreCase = true) ||
                place.label.contains("laburo", ignoreCase = true) ||
                place.label.contains("trabajo", ignoreCase = true)
        }
        val timeMatches = currentHour(context.currentTimeMillis) in setOf(7, 8)
        val intentMatches = normalized.contains("me voy al laburo") ||
            normalized.contains("voy al laburo") ||
            normalized.contains("me voy a trabajar") ||
            normalized.contains("trabajo")
        if (!hasLaburo || (!timeMatches && !intentMatches)) return null

        val suggestion = AgentSuggestion(
            id = "route-work-${context.currentTimeMillis}",
            type = SuggestionType.ROUTINE,
            text = "Son las 8. ¿Querés que abra la ruta al trabajo?",
            proposedIntent = AgentIntent.NAVIGATE_TO_DESTINATION,
            requiredConfirmation = true,
            cooldownKey = "work_route"
        )
        return if (SuggestionPolicy.canShow(suggestion, context.currentTimeMillis, cooldownStore)) suggestion else null
    }

    private fun maybeSuggestPendingTask(context: SuggestionContext): AgentSuggestion? {
        val pending = context.memorySnapshot.pendingTasks.firstOrNull() ?: return null
        if (context.normalizedText.contains("que tengo pendiente")) {
            return AgentSuggestion(
                id = "pending-${pending.id}",
                type = SuggestionType.PENDING_TASK,
                text = "Tenes pendiente ${pending.value}. ¿Querés preparar un mensaje?",
                proposedIntent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
                requiredConfirmation = true,
                cooldownKey = "pending_${pending.id}"
            ).takeIf { SuggestionPolicy.canShow(it, context.currentTimeMillis, cooldownStore) }
        }

        return AgentSuggestion(
            id = "pending-${pending.id}",
            type = SuggestionType.PENDING_TASK,
            text = "Tenes pendiente ${pending.value}. ¿Querés preparar un mensaje?",
            proposedIntent = AgentIntent.COMPOSE_WHATSAPP_MESSAGE,
            requiredConfirmation = true,
            cooldownKey = "pending_${pending.id}"
        ).takeIf { SuggestionPolicy.canShow(it, context.currentTimeMillis, cooldownStore) }
    }

    private fun maybeSuggestMedication(context: SuggestionContext): AgentSuggestion? {
        val medication = context.memorySnapshot.routines.firstOrNull {
            it.label.contains("medic", ignoreCase = true) ||
                it.value.contains("medic", ignoreCase = true)
        } ?: return null

        val suggestion = AgentSuggestion(
            id = "med-${medication.id}",
            type = SuggestionType.MEDICATION,
            text = "Tenes guardado un recordatorio de medicación. ¿Querés marcarlo como tomado?",
            proposedIntent = null,
            requiredConfirmation = true,
            cooldownKey = "med_${medication.id}"
        )
        return suggestion.takeIf { SuggestionPolicy.canShow(it, context.currentTimeMillis, cooldownStore) }
    }

    private fun currentHour(nowMillis: Long): Int {
        val hour = ((nowMillis / (60 * 60 * 1000L)) % 24).toInt()
        return hour
    }
}

private class SharedPreferencesSuggestionCooldownStoreFallback : SuggestionCooldownStore {
    private val map = linkedMapOf<String, Long>()
    override fun isOnCooldown(key: String, nowMillis: Long): Boolean = (map[key] ?: 0L) > nowMillis
    override fun markShown(key: String, cooldownMillis: Long, nowMillis: Long) { map[key] = nowMillis + cooldownMillis }
    override fun clear(key: String) { map.remove(key) }
    override fun clearAll() { map.clear() }
}

