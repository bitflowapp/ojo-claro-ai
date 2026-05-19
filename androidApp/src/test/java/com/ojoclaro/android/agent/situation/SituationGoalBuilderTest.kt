package com.ojoclaro.android.agent.situation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SituationGoalBuilderTest {

    private val now: Long = 1_000L

    // --- goalFromCommand ----------------------------------------------------

    @Test
    fun goal_from_avisale_a_sofi_es_write_message_con_contact_y_falta_message() {
        val g = SituationGoalBuilder.goalFromCommand(
            "avisale a Sofi",
            SituationIntent.WRITE_MESSAGE,
            now
        )!!
        assertEquals(SituationIntent.WRITE_MESSAGE, g.intent)
        assertEquals("Sofi", g.slotsFilled["contact"])
        assertEquals(setOf("message"), g.slotsMissing)
        assertFalse(g.isComplete())
    }

    @Test
    fun goal_from_decile_a_sofi_que_llego_tarde_completo() {
        val g = SituationGoalBuilder.goalFromCommand(
            "decile a Sofi que llego tarde",
            SituationIntent.WRITE_MESSAGE,
            now
        )!!
        assertEquals("Sofi", g.slotsFilled["contact"])
        assertEquals("llego tarde", g.slotsFilled["message"])
        assertTrue(g.isComplete())
    }

    @Test
    fun goal_from_llama_a_es_call_contact_falta_contact() {
        val g = SituationGoalBuilder.goalFromCommand(
            "llamá a",
            SituationIntent.CALL_CONTACT,
            now
        )!!
        assertEquals(SituationIntent.CALL_CONTACT, g.intent)
        assertEquals(setOf("contact"), g.slotsMissing)
    }

    @Test
    fun goal_from_llama_a_sofi_es_call_contact_completo() {
        val g = SituationGoalBuilder.goalFromCommand(
            "llamá a Sofi",
            SituationIntent.CALL_CONTACT,
            now
        )!!
        assertEquals("Sofi", g.slotsFilled["contact"])
        assertTrue(g.isComplete())
    }

    @Test
    fun goal_from_abri_es_open_app_falta_target() {
        val g = SituationGoalBuilder.goalFromCommand(
            "abrí",
            SituationIntent.OPEN_APP,
            now
        )!!
        assertEquals(setOf("target"), g.slotsMissing)
    }

    @Test
    fun goal_from_abri_whatsapp_es_open_app_completo() {
        val g = SituationGoalBuilder.goalFromCommand(
            "abrí WhatsApp",
            SituationIntent.OPEN_APP,
            now
        )!!
        assertEquals("WhatsApp", g.slotsFilled["target"])
        assertTrue(g.isComplete())
    }

    @Test
    fun goal_from_intent_no_soportado_devuelve_null() {
        assertNull(
            SituationGoalBuilder.goalFromCommand(
                "leeme la pantalla",
                SituationIntent.READ_SCREEN,
                now
            )
        )
    }

    // --- continueGoal -------------------------------------------------------

    @Test
    fun continue_call_contact_con_sofi_completa_contact() {
        val initial = SituationGoalBuilder.goalFromCommand(
            "llamá a",
            SituationIntent.CALL_CONTACT,
            now
        )!!
        val continued = SituationGoalBuilder.continueGoal(initial, "Sofi", now)
        assertEquals("Sofi", continued.slotsFilled["contact"])
        assertTrue(continued.isComplete())
    }

    @Test
    fun continue_open_app_con_whatsapp_completa_target() {
        val initial = SituationGoalBuilder.goalFromCommand(
            "abrí",
            SituationIntent.OPEN_APP,
            now
        )!!
        val continued = SituationGoalBuilder.continueGoal(initial, "WhatsApp", now)
        assertEquals("WhatsApp", continued.slotsFilled["target"])
        assertTrue(continued.isComplete())
    }

    @Test
    fun continue_write_message_con_que_llego_en_15_completa_message() {
        val initial = SituationGoalBuilder.goalFromCommand(
            "avisale a Sofi",
            SituationIntent.WRITE_MESSAGE,
            now
        )!!
        val continued = SituationGoalBuilder.continueGoal(initial, "que llego en 15", now)
        assertEquals("llego en 15", continued.slotsFilled["message"])
        assertTrue(continued.isComplete())
    }

    @Test
    fun continue_no_completa_slot_con_si() {
        val initial = SituationGoalBuilder.goalFromCommand(
            "llamá a",
            SituationIntent.CALL_CONTACT,
            now
        )!!
        val continued = SituationGoalBuilder.continueGoal(initial, "sí", now)
        // El slot sigue faltando.
        assertTrue("contact" in continued.slotsMissing)
        assertNull(continued.slotsFilled["contact"])
    }

    @Test
    fun continue_no_completa_slot_con_cancela() {
        val initial = SituationGoalBuilder.goalFromCommand(
            "llamá a",
            SituationIntent.CALL_CONTACT,
            now
        )!!
        val continued = SituationGoalBuilder.continueGoal(initial, "cancelá", now)
        assertTrue("contact" in continued.slotsMissing)
    }

    @Test
    fun continue_no_completa_message_con_si() {
        val initial = SituationGoalBuilder.goalFromCommand(
            "avisale a Sofi",
            SituationIntent.WRITE_MESSAGE,
            now
        )!!
        val continued = SituationGoalBuilder.continueGoal(initial, "sí", now)
        assertTrue("message" in continued.slotsMissing)
    }
}
