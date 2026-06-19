package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression coverage for the shared WhatsApp phrase normalizer (alias
 * canonicalization + leading-filler strip) used by the read/chat-list/read-aloud
 * recognizers. Locks the behavior added by the voice-fuzz routing fixes.
 */
class WhatsAppPhraseNormalizerTest {

    @Test
    fun canonicalizesSpokenWhatsAppAliasesToWhatsapp() {
        listOf(
            "abrí guasap", "abrí guasa", "abrí wasa", "abrí wasá", "abrí wasap",
            "abrí wasat", "abrí watsap", "abrí what sap", "abrí whats app",
            "abrí guat sap", "abrí wsp", "abrí wp", "abrí wpp"
        ).forEach { phrase ->
            assertEquals(
                "abri whatsapp", WhatsAppPhraseNormalizer.normalize(phrase),
                "alias should canonicalize to whatsapp: \"$phrase\""
            )
        }
    }

    @Test
    fun stripsLeadingFillersWithoutChangingIntent() {
        listOf(
            "che Estela leé los chats", "porfa leé los chats", "eh leé los chats",
            "mmm leé los chats", "a ver leé los chats", "dale leé los chats",
            "bueno leé los chats", "ok leé los chats"
        ).forEach { phrase ->
            assertEquals(
                "lee los chats", WhatsAppPhraseNormalizer.normalize(phrase),
                "leading filler should be stripped: \"$phrase\""
            )
        }
    }

    @Test
    fun keepsNonAliasNonFillerContentIntact() {
        assertEquals("lee los chats", WhatsAppPhraseNormalizer.normalize("leé los chats"))
        assertEquals(
            "abri el chat de ana prueba",
            WhatsAppPhraseNormalizer.normalize("abrí el chat de Ana Prueba")
        )
        // "wasabi" no debe canonizarse (no es alias; boundary-safe).
        assertEquals("quiero wasabi", WhatsAppPhraseNormalizer.normalize("quiero wasabi"))
    }
}
