package com.ojoclaro.android.agent.situation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ActiveGoalTest {

    private fun goal(
        createdAt: Long = 1_000L,
        ttlMillis: Long = 300_000L
    ): ActiveGoal = ActiveGoal(
        description = "avisarle a Sofi que llego tarde",
        intent = SituationIntent.WRITE_MESSAGE,
        createdAt = createdAt,
        ttlMillis = ttlMillis
    )

    @Test
    fun goal_no_expirado_antes_del_ttl() {
        val g = goal(createdAt = 1_000L, ttlMillis = 300_000L)
        assertFalse(g.isExpired(now = 1_000L))
        assertFalse(g.isExpired(now = 301_000L)) // exactamente en el límite, no supera
    }

    @Test
    fun goal_expirado_despues_del_ttl() {
        val g = goal(createdAt = 1_000L, ttlMillis = 300_000L)
        assertTrue(g.isExpired(now = 301_001L))
    }

    @Test
    fun description_blank_lanza_error() {
        assertFailsWith<IllegalArgumentException> {
            ActiveGoal(
                description = "   ",
                intent = SituationIntent.WRITE_MESSAGE,
                createdAt = 0L
            )
        }
    }

    @Test
    fun ttl_menor_o_igual_a_cero_lanza_error() {
        assertFailsWith<IllegalArgumentException> {
            ActiveGoal(
                description = "x",
                intent = SituationIntent.WRITE_MESSAGE,
                createdAt = 0L,
                ttlMillis = 0L
            )
        }
        assertFailsWith<IllegalArgumentException> {
            ActiveGoal(
                description = "x",
                intent = SituationIntent.WRITE_MESSAGE,
                createdAt = 0L,
                ttlMillis = -1L
            )
        }
    }

    @Test
    fun created_at_negativo_lanza_error() {
        assertFailsWith<IllegalArgumentException> {
            ActiveGoal(
                description = "x",
                intent = SituationIntent.WRITE_MESSAGE,
                createdAt = -1L
            )
        }
    }

    // --- Fase 8: helpers de slots --------------------------------------------

    @Test
    fun is_complete_true_cuando_slots_missing_vacio() {
        val g = goal()
        assertTrue(g.isComplete())
        assertFalse(g.hasMissingSlots())
    }

    @Test
    fun has_missing_slots_true_cuando_faltan_slots() {
        val g = goal().copy(slotsMissing = setOf("message"))
        assertTrue(g.hasMissingSlots())
        assertFalse(g.isComplete())
    }

    @Test
    fun with_slot_filled_agrega_slot_y_lo_quita_de_missing() {
        val g = goal().copy(slotsMissing = setOf("contact", "message"))
        val updated = g.withSlotFilled("contact", "Sofi")
        assertEquals("Sofi", updated.slotsFilled["contact"])
        assertFalse("contact" in updated.slotsMissing)
        assertTrue("message" in updated.slotsMissing)
    }

    @Test
    fun with_slot_filled_value_largo_lanza() {
        assertFailsWith<IllegalArgumentException> {
            goal().withSlotFilled("k", "x".repeat(ActiveGoal.MAX_SLOT_VALUE_CHARS + 1))
        }
    }

    @Test
    fun with_slot_filled_key_blank_lanza() {
        assertFailsWith<IllegalArgumentException> {
            goal().withSlotFilled("   ", "Sofi")
        }
    }

    @Test
    fun with_slot_filled_value_blank_lanza() {
        assertFailsWith<IllegalArgumentException> {
            goal().withSlotFilled("contact", "   ")
        }
    }

    @Test
    fun without_missing_slot_remueve_clave() {
        val g = goal().copy(slotsMissing = setOf("contact", "message"))
        val updated = g.withoutMissingSlot("message")
        assertEquals(setOf("contact"), updated.slotsMissing)
    }

    @Test
    fun slots_filled_demasiados_lanza() {
        assertFailsWith<IllegalArgumentException> {
            ActiveGoal(
                description = "x",
                intent = SituationIntent.WRITE_MESSAGE,
                createdAt = 0L,
                slotsFilled = (1..ActiveGoal.MAX_SLOTS + 1).associate { "k$it" to "v$it" }
            )
        }
    }

    @Test
    fun slots_filled_value_demasiado_largo_lanza() {
        assertFailsWith<IllegalArgumentException> {
            ActiveGoal(
                description = "x",
                intent = SituationIntent.WRITE_MESSAGE,
                createdAt = 0L,
                slotsFilled = mapOf("k" to "x".repeat(ActiveGoal.MAX_SLOT_VALUE_CHARS + 1))
            )
        }
    }
}
