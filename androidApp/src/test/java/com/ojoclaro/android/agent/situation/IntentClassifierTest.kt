package com.ojoclaro.android.agent.situation

import kotlin.test.Test
import kotlin.test.assertEquals

class IntentClassifierTest {

    private val classifier = IntentClassifier()

    private fun classify(command: String, goal: ActiveGoal? = null): SituationIntent =
        classifier.classify(rawCommand = command, activeGoal = goal)

    private fun writeMessageGoal(): ActiveGoal = ActiveGoal(
        description = "avisarle a Sofi que llego tarde",
        intent = SituationIntent.WRITE_MESSAGE,
        createdAt = 0L
    )

    @Test
    fun callate_es_emergency_stop() {
        assertEquals(SituationIntent.EMERGENCY_STOP, classify("callate"))
    }

    @Test
    fun cancela_es_control() {
        assertEquals(SituationIntent.CONTROL, classify("cancelá"))
        assertEquals(SituationIntent.CONTROL, classify("cancela"))
    }

    @Test
    fun estoy_trabajando_ayudame_es_help_me_work() {
        assertEquals(SituationIntent.HELP_ME_WORK, classify("estoy trabajando ayudame"))
    }

    @Test
    fun leeme_la_pantalla_es_read_screen() {
        assertEquals(SituationIntent.READ_SCREEN, classify("leeme la pantalla"))
    }

    @Test
    fun resumime_la_pantalla_es_summarize_screen() {
        assertEquals(SituationIntent.SUMMARIZE_SCREEN, classify("resumime la pantalla"))
    }

    @Test
    fun que_estoy_viendo_es_explain_what_i_see() {
        assertEquals(SituationIntent.EXPLAIN_WHAT_I_SEE, classify("qué estoy viendo"))
    }

    @Test
    fun abri_whatsapp_es_open_app() {
        assertEquals(SituationIntent.OPEN_APP, classify("abrí WhatsApp"))
    }

    @Test
    fun avisale_a_sofi_es_write_message() {
        assertEquals(SituationIntent.WRITE_MESSAGE, classify("avisale a Sofi que llego tarde"))
    }

    @Test
    fun llama_a_sofi_es_call_contact() {
        assertEquals(SituationIntent.CALL_CONTACT, classify("llamá a Sofi"))
    }

    @Test
    fun guiame_con_whatsapp_es_guide_user() {
        assertEquals(SituationIntent.GUIDE_USER, classify("guiame con WhatsApp"))
    }

    @Test
    fun recorda_que_sofi_es_manage_memory() {
        assertEquals(
            SituationIntent.MANAGE_MEMORY,
            classify("recordá que Sofi es mi contacto principal")
        )
    }

    @Test
    fun transferir_plata_del_banco_es_unsafe_request() {
        assertEquals(SituationIntent.UNSAFE_REQUEST, classify("transferí plata desde el banco"))
    }

    @Test
    fun texto_desconocido_es_unknown() {
        assertEquals(SituationIntent.UNKNOWN, classify("banana azul volador"))
    }

    @Test
    fun continuacion_de_objetivo_devuelve_intent_del_goal() {
        assertEquals(
            SituationIntent.WRITE_MESSAGE,
            classify("decile que llego en 15", goal = writeMessageGoal())
        )
    }

    @Test
    fun confirmacion_corta_con_objetivo_activo_es_control() {
        assertEquals(SituationIntent.CONTROL, classify("sí", goal = writeMessageGoal()))
    }

    @Test
    fun ayudame_a_usar_es_guide_user_no_help_me_work() {
        // "ayudame a usar" debe ganarle al "ayudame" débil de modo compañero.
        assertEquals(SituationIntent.GUIDE_USER, classify("ayudame a usar WhatsApp"))
    }

    @Test
    fun comando_vacio_es_unknown() {
        assertEquals(SituationIntent.UNKNOWN, classify("   "))
    }
}
