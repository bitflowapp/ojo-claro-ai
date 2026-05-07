package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ArgentineSpanishLexiconTest {

    @Test
    fun whatsappAliasesIncluyenVariantesArgentinas() {
        listOf("wp", "wsp", "wpp", "wasap", "guasap", "watsap", "whasap", "whatsapp").forEach {
            assertTrue(it in ArgentineSpanishLexicon.WHATSAPP_ALIASES, it)
        }
    }

    @Test
    fun confirmacionesSonEstrictas() {
        assertTrue("confirmar" in ArgentineSpanishLexicon.STRICT_CONFIRM_PHRASES)
        assertTrue("confirmo" in ArgentineSpanishLexicon.STRICT_CONFIRM_PHRASES)
        assertTrue("aceptar" in ArgentineSpanishLexicon.STRICT_CONFIRM_PHRASES)

        listOf("si", "sí", "dale", "ok", "bueno", "de una").forEach {
            assertTrue(it in ArgentineSpanishLexicon.NEVER_CONFIRM_PHRASES, it)
            assertFalse(it in ArgentineSpanishLexicon.STRICT_CONFIRM_PHRASES, it)
        }
    }

    @Test
    fun muletillasIncluyenDalePeroNoConfirman() {
        assertTrue("dale" in ArgentineSpanishLexicon.MULETILLA_TOKENS)
        assertFalse(VoicePhraseNormalizer.isStrictConfirm("dale"))
        assertTrue(VoicePhraseNormalizer.isAffirmativeNoise("dale dale"))
    }

    @Test
    fun voseoSeCentralizaEnElLexicon() {
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("abrime el wp").startsWith("abrir"))
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("buscame el chat").startsWith("buscar"))
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("mandale a Sofi").startsWith("mandar"))
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("decile a Sofi").startsWith("decir"))
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("escribile a Sofi").startsWith("escribir"))
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("llamame a mamá").startsWith("llamar"))
        assertTrue(VoicePhraseNormalizer.normalizeVoseo("llevame a casa").startsWith("llevar"))
    }
}
