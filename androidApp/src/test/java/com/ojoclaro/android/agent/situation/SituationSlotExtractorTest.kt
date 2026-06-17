package com.ojoclaro.android.agent.situation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SituationSlotExtractorTest {

    // --- OPEN_APP ------------------------------------------------------------

    @Test
    fun extract_target_open_app_abri_whatsapp() {
        assertEquals(
            "WhatsApp",
            SituationSlotExtractor.extractTarget("abrí WhatsApp", SituationIntent.OPEN_APP)
        )
    }

    @Test
    fun extract_target_open_app_abre_spotify() {
        assertEquals(
            "Spotify",
            SituationSlotExtractor.extractTarget("abre Spotify", SituationIntent.OPEN_APP)
        )
    }

    @Test
    fun extract_target_open_app_abrir_mapas() {
        assertEquals(
            "mapas",
            SituationSlotExtractor.extractTarget("abrir mapas", SituationIntent.OPEN_APP)
        )
    }

    @Test
    fun extract_target_open_app_anda_a_chrome() {
        assertEquals(
            "Chrome",
            SituationSlotExtractor.extractTarget("andá a Chrome", SituationIntent.OPEN_APP)
        )
    }

    // --- CALL_CONTACT --------------------------------------------------------

    @Test
    fun extract_target_call_contact_llama_a_sofi() {
        assertEquals(
            "Sofi",
            SituationSlotExtractor.extractTarget("llamá a Sofi", SituationIntent.CALL_CONTACT)
        )
    }

    @Test
    fun extract_target_call_contact_llama_a_mama() {
        assertEquals(
            "mamá",
            SituationSlotExtractor.extractTarget("llama a mamá", SituationIntent.CALL_CONTACT)
        )
    }

    @Test
    fun extract_target_call_contact_llamar_a_juan() {
        assertEquals(
            "Juan",
            SituationSlotExtractor.extractTarget("llamar a Juan", SituationIntent.CALL_CONTACT)
        )
    }

    // --- Casos sin target ----------------------------------------------------

    @Test
    fun extract_target_comando_desconocido_devuelve_blank() {
        assertEquals("", SituationSlotExtractor.extractTarget("banana azul", SituationIntent.OPEN_APP))
    }

    @Test
    fun extract_target_intent_no_soportado_devuelve_blank() {
        assertEquals(
            "",
            SituationSlotExtractor.extractTarget("avisale a Sofi", SituationIntent.WRITE_MESSAGE)
        )
    }

    @Test
    fun extract_target_solo_verbo_sin_target_devuelve_blank() {
        assertEquals("", SituationSlotExtractor.extractTarget("abrí", SituationIntent.OPEN_APP))
    }

    // --- buildPendingPayload -------------------------------------------------

    @Test
    fun build_payload_open_app_incluye_intent_original_command_y_target() {
        val payload = SituationSlotExtractor.buildPendingPayload(
            "abrí WhatsApp",
            SituationIntent.OPEN_APP
        )
        assertEquals("OPEN_APP", payload["intent"])
        assertEquals("abrí WhatsApp", payload["originalCommand"])
        assertEquals("WhatsApp", payload["target"])
    }

    @Test
    fun build_payload_call_contact_incluye_target() {
        val payload = SituationSlotExtractor.buildPendingPayload(
            "llamá a Sofi",
            SituationIntent.CALL_CONTACT
        )
        assertEquals("CALL_CONTACT", payload["intent"])
        assertEquals("Sofi", payload["target"])
    }

    @Test
    fun build_payload_sin_target_no_incluye_clave_target() {
        val payload = SituationSlotExtractor.buildPendingPayload(
            "banana azul",
            SituationIntent.OPEN_APP
        )
        assertEquals("OPEN_APP", payload["intent"])
        assertTrue(!payload.containsKey("target"))
    }

    @Test
    fun build_payload_respeta_limites_de_pending_action() {
        val payload = SituationSlotExtractor.buildPendingPayload(
            "abrí WhatsApp",
            SituationIntent.OPEN_APP
        )
        assertTrue(payload.size <= PendingAction.MAX_PAYLOAD_ENTRIES)
        assertTrue(payload.keys.none { it.isBlank() })
        assertTrue(payload.values.none { it.length > PendingAction.MAX_PAYLOAD_VALUE_CHARS })
    }

    // --- Fase 8: extractContact ---------------------------------------------

    @Test
    fun extract_contact_avisale_a_sofi() {
        assertEquals("Sofi", SituationSlotExtractor.extractContact("avisale a Sofi"))
    }

    @Test
    fun extract_contact_decile_a_sofi_que_llego_tarde() {
        assertEquals(
            "Sofi",
            SituationSlotExtractor.extractContact("decile a Sofi que llego tarde")
        )
    }

    @Test
    fun extract_contact_mandale_mensaje_a_sofi() {
        assertEquals("Sofi", SituationSlotExtractor.extractContact("mandale mensaje a Sofi"))
    }

    @Test
    fun extract_contact_escribile_a_sofi() {
        assertEquals("Sofi", SituationSlotExtractor.extractContact("escribile a Sofi"))
    }

    @Test
    fun extract_contact_llama_a_sofi() {
        assertEquals("Sofi", SituationSlotExtractor.extractContact("llamá a Sofi"))
    }

    @Test
    fun extract_contact_llama_a_mama_preserva_acento() {
        assertEquals("mamá", SituationSlotExtractor.extractContact("llama a mamá"))
    }

    @Test
    fun extract_contact_comando_sin_prefijo_devuelve_blank() {
        assertEquals("", SituationSlotExtractor.extractContact("Sofi"))
    }

    // --- Fase 8: extractMessageForContact -----------------------------------

    @Test
    fun extract_message_decile_a_sofi_que_llego_tarde() {
        assertEquals(
            "llego tarde",
            SituationSlotExtractor.extractMessageForContact(
                "decile a Sofi que llego tarde",
                "Sofi"
            )
        )
    }

    @Test
    fun extract_message_avisale_a_sofi_que_estoy_yendo() {
        assertEquals(
            "estoy yendo",
            SituationSlotExtractor.extractMessageForContact(
                "avisale a Sofi que estoy yendo",
                "Sofi"
            )
        )
    }

    @Test
    fun extract_message_mandale_mensaje_a_sofi_que_llego_en_15() {
        assertEquals(
            "llego en 15",
            SituationSlotExtractor.extractMessageForContact(
                "mandale mensaje a Sofi que llego en 15",
                "Sofi"
            )
        )
    }

    @Test
    fun extract_message_sin_que_devuelve_blank() {
        assertEquals(
            "",
            SituationSlotExtractor.extractMessageForContact("avisale a Sofi", "Sofi")
        )
    }

    @Test
    fun extract_message_contact_blank_devuelve_blank() {
        assertEquals(
            "",
            SituationSlotExtractor.extractMessageForContact("decile que llego", "")
        )
    }

    // --- Fase 8: looksLikeMessageContinuation -------------------------------

    @Test
    fun looks_like_continuation_que_llego_en_15() {
        assertTrue(SituationSlotExtractor.looksLikeMessageContinuation("que llego en 15"))
    }

    @Test
    fun looks_like_continuation_llego_en_15() {
        assertTrue(SituationSlotExtractor.looksLikeMessageContinuation("llego en 15"))
    }

    @Test
    fun looks_like_continuation_estoy_yendo() {
        assertTrue(SituationSlotExtractor.looksLikeMessageContinuation("estoy yendo"))
    }

    @Test
    fun looks_like_continuation_si_es_false() {
        assertEquals(false, SituationSlotExtractor.looksLikeMessageContinuation("sí"))
    }

    @Test
    fun looks_like_continuation_cancela_es_false() {
        assertEquals(false, SituationSlotExtractor.looksLikeMessageContinuation("cancelá"))
    }

    @Test
    fun looks_like_continuation_abri_whatsapp_es_false() {
        assertEquals(false, SituationSlotExtractor.looksLikeMessageContinuation("abrí WhatsApp"))
    }
}
