package com.ojoclaro.android.agent

import com.ojoclaro.android.memory.PersonalAgentMemory
import com.ojoclaro.android.memory.PersonalMemorySnapshot
import com.ojoclaro.android.memory.PersonalMemoryType
import com.ojoclaro.android.model.AppState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ContextualSuggestionEngineTest {

    @Test
    fun workRouteSuggestionUsesCooldown() {
        val cooldownStore = InMemoryCooldownStore()
        val engine = ContextualSuggestionEngine(cooldownStore)
        val context = baseContext(
            originalText = "me voy al laburo",
            normalizedText = "me voy al laburo",
            currentTimeMillis = 8 * HOUR_MILLIS,
            memories = listOf(
                memory(
                    id = "place-1",
                    type = PersonalMemoryType.PLACE,
                    label = "laburo",
                    value = "laburo"
                )
            )
        )

        val first = engine.evaluate(context)
        assertEquals(1, first.size)
        assertEquals(SuggestionType.ROUTINE, first.first().type)
        assertTrue(first.first().requiredConfirmation)
        assertTrue(first.first().text.contains("ruta", ignoreCase = true))

        val second = engine.evaluate(context)
        assertTrue(second.isEmpty())
    }

    @Test
    fun pendingTaskSuggestionRequiresConfirmation() {
        val engine = ContextualSuggestionEngine(InMemoryCooldownStore())
        val context = baseContext(
            originalText = "que tengo pendiente",
            normalizedText = "que tengo pendiente",
            memories = listOf(
                memory(
                    id = "pending-1",
                    type = PersonalMemoryType.PENDING_TASK,
                    label = "Marco",
                    value = "Responderle a Marco"
                )
            )
        )

        val suggestion = engine.evaluate(context).single()
        assertEquals(SuggestionType.PENDING_TASK, suggestion.type)
        assertEquals(AgentIntent.COMPOSE_WHATSAPP_MESSAGE, suggestion.proposedIntent)
        assertTrue(suggestion.requiredConfirmation)
        assertTrue(suggestion.text.contains("pendiente", ignoreCase = true))
    }

    @Test
    fun suggestionsCanBeDisabled() {
        val engine = ContextualSuggestionEngine(InMemoryCooldownStore())
        val context = baseContext(
            originalText = "me voy al laburo",
            normalizedText = "me voy al laburo",
            suggestionsEnabled = false,
            memories = listOf(
                memory(
                    id = "place-1",
                    type = PersonalMemoryType.PLACE,
                    label = "laburo",
                    value = "laburo"
                )
            )
        )

        assertTrue(engine.evaluate(context).isEmpty())
        assertFalse(context.suggestionsEnabled)
    }

    private fun baseContext(
        originalText: String,
        normalizedText: String,
        currentTimeMillis: Long = 8 * HOUR_MILLIS,
        suggestionsEnabled: Boolean = true,
        memories: List<PersonalAgentMemory> = emptyList()
    ): SuggestionContext =
        SuggestionContext(
            originalText = originalText,
            normalizedText = normalizedText,
            currentTimeMillis = currentTimeMillis,
            agentState = AgentState.IDLE,
            appState = AppState.IDLE,
            memorySnapshot = PersonalMemorySnapshot(memories),
            externalApp = null,
            suggestionsEnabled = suggestionsEnabled
        )

    private fun memory(
        id: String,
        type: PersonalMemoryType,
        label: String,
        value: String
    ): PersonalAgentMemory =
        PersonalAgentMemory(
            id = id,
            type = type,
            label = label,
            value = value,
            createdAtMillis = 1_000L,
            updatedAtMillis = 1_000L,
            userApproved = true
        )

    private class InMemoryCooldownStore : SuggestionCooldownStore {
        private val until = linkedMapOf<String, Long>()

        override fun isOnCooldown(key: String, nowMillis: Long): Boolean =
            (until[key] ?: 0L) > nowMillis

        override fun markShown(key: String, cooldownMillis: Long, nowMillis: Long) {
            until[key] = nowMillis + cooldownMillis
        }

        override fun clear(key: String) {
            until.remove(key)
        }

        override fun clearAll() {
            until.clear()
        }
    }

    private companion object {
        private const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}
