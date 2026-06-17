package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePhraseNormalizerTest {

    @Test
    fun normalizaWhatsAppConMuletillas() {
        assertEquals("abrir whatsapp", VoicePhraseNormalizer.normalizeForParser("che abrí wp"))
        assertEquals("abrir whatsapp", VoicePhraseNormalizer.normalizeForParser("abrime el wp"))
    }

    @Test
    fun normalizaBuscarChatConDaleSinConfirmar() {
        val normalized = VoicePhraseNormalizer.normalizeForParser("dale buscame el chat de Marco")

        assertEquals("buscar chat Marco", normalized)
        assertFalse(VoicePhraseNormalizer.isStrictConfirm("dale"))
        assertTrue(VoicePhraseNormalizer.isNeverConfirm("dale"))
    }

    @Test
    fun normalizaComposeConVoseoYConservaContactoDemoYMensaje() {
        val normalized = VoicePhraseNormalizer.normalizeForParser("eh mandale a ContactoDemo que estoy llegando")

        assertEquals("mandar a ContactoDemo que estoy llegando", normalized)
    }

    @Test
    fun ruidoAfirmativoNoConfirma() {
        assertTrue(VoicePhraseNormalizer.isAffirmativeNoise("sí sí"))
        assertTrue(VoicePhraseNormalizer.isAffirmativeNoise("este este"))
        assertTrue(VoicePhraseNormalizer.isAffirmativeNoise("dale dale"))
        assertFalse(VoicePhraseNormalizer.isStrictConfirm("sí"))
        assertFalse(VoicePhraseNormalizer.isStrictConfirm("dale"))
    }

    @Test
    fun confirmacionEstrictaSigueValida() {
        assertTrue(VoicePhraseNormalizer.isStrictConfirm("confirmar"))
        assertTrue(VoicePhraseNormalizer.isStrictConfirm("confirmo"))
        assertTrue(VoicePhraseNormalizer.isStrictConfirm("aceptar"))
    }

    @Test
    fun normalizaUbicacionArgentina() {
        assertEquals("donde estoy", VoicePhraseNormalizer.normalizeForParser("dónde ando"))
        assertEquals("donde estoy", VoicePhraseNormalizer.normalizeForParser("ubicame"))
    }
}
