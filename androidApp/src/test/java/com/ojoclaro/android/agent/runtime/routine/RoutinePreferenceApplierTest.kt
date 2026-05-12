package com.ojoclaro.android.agent.runtime.routine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RoutinePreferenceApplierTest {

    private val short: HumanResponseStyle = HumanResponseStyle.DEFAULT.copy(
        length = ResponseLength.SHORT
    )
    private val clear: HumanResponseStyle = HumanResponseStyle.DEFAULT.copy(
        clarity = ResponseClarity.CLEAR
    )
    private val normal: HumanResponseStyle = HumanResponseStyle.DEFAULT

    // ===== Default style (no transformation) =====

    @Test
    fun normalStyleDoesNotModifyText() {
        val text = "Veo estos chats visibles: Marco, Sofi y Mamá. No leo mensajes completos."
        assertEquals(
            text,
            RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, normal)
        )
    }

    @Test
    fun blankInputReturnedAsIs() {
        assertEquals("", RoutinePreferenceApplier.apply("", RoutineResponseKind.GENERIC, short))
    }

    // ===== VISIBLE_CHATS_LIST short =====

    @Test
    fun visibleChatsListShortDropsPreambleAndSafetyDisclaimer() {
        val text = "Veo estos chats visibles: Marco, Sofi y Mamá. No leo mensajes completos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, short)
        assertEquals("Veo: Marco, Sofi y Mamá.", out)
    }

    @Test
    fun visibleChatsListShortHandlesSingleChat() {
        val text = "Veo estos chats visibles: Marco. No leo mensajes completos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, short)
        assertEquals("Veo: Marco.", out)
    }

    // ===== VISIBLE_CHATS_INSIDE short — preserve safety =====

    @Test
    fun visibleChatsInsideShortPreservesSafetyDisclaimer() {
        val text = "Estás dentro de un chat. No leo mensajes completos sin que me lo pidas."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_INSIDE, short)
        // Mantenemos el aviso "no leo mensajes" — es safety.
        assertTrue(
            out.contains("No leo mensajes", ignoreCase = true),
            "safety disclaimer must be preserved (was: '$out')"
        )
        assertTrue(out.length < text.length, "should still be shorter than original")
    }

    // ===== WHATSAPP_GUIDED short — preserve safety =====

    @Test
    fun whatsAppGuidedShortPreservesNeverSendPhoto() {
        val text = "Para mandar una foto: buscá el botón de cámara al lado del campo de mensaje. " +
            "Tocá dos veces para abrir la cámara, sacá la foto y después tocá enviar. Yo nunca envío la foto por vos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.WHATSAPP_GUIDED, short)
        assertTrue(
            out.contains("Yo nunca envío la foto por vos", ignoreCase = true),
            "safety promise about photo must be preserved"
        )
        assertTrue(out.startsWith("Foto: "), "should shorten preamble (was: '$out')")
    }

    @Test
    fun whatsAppGuidedShortPreservesNeverSendLocation() {
        val text = "Para mandar tu ubicación en WhatsApp: tocá el botón de adjuntar al lado del campo de mensaje, " +
            "después elegí Ubicación y compartí. Yo no envío la ubicación por mi cuenta."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.WHATSAPP_GUIDED, short)
        assertTrue(out.contains("Yo no envío la ubicación", ignoreCase = true))
        assertTrue(out.startsWith("Ubicación: "))
    }

    @Test
    fun whatsAppGuidedShortPreservesNeverSendMessage() {
        val text = "Tocá dos veces sobre el campo de mensaje y dictá lo que querés escribir. " +
            "Después tocá el botón enviar. Yo nunca envío el mensaje por vos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.WHATSAPP_GUIDED, short)
        assertTrue(out.contains("Yo nunca envío el mensaje", ignoreCase = true))
        assertTrue(out.length < text.length)
        assertTrue(out.contains("Tocá dos veces el campo de mensaje y dictá."))
        assertTrue(out.contains("Después: enviar."))
    }

    @Test
    fun whatsAppGuidedShortShortensWhatCanIDoListing() {
        val text = "Veo el campo de mensaje, está en la parte inferior. veo un botón de cámara. " +
            "veo un botón de enviar. Yo no toco la app por vos: te guío para que toques vos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.WHATSAPP_GUIDED, short)
        assertTrue(out.contains("Campo de mensaje abajo"))
        assertTrue(out.contains("botón cámara"))
        assertTrue(out.contains("botón enviar"))
        assertTrue(out.contains("No toco la app."))
        assertTrue(out.length < text.length)
    }

    @Test
    fun whatsAppGuidedShortShortensAmIInWhatsApp() {
        val text = "Sí, estás en WhatsApp y veo un chat abierto."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.WHATSAPP_GUIDED, short)
        assertEquals("Sí. WhatsApp con chat.", out)
    }

    // ===== SCREEN_SUMMARY short — preserve safety on hot zones =====

    @Test
    fun screenSummaryShortRemovesPreambleOnSafeScreen() {
        val text = "Estás en: Lista de tareas."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.SCREEN_SUMMARY, short)
        assertEquals("Lista de tareas.", out)
    }

    @Test
    fun screenSummaryShortDoesNotModifyBankingAdvisory() {
        val text = "Esta pantalla puede contener datos bancarios. Por seguridad no la leo."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.SCREEN_SUMMARY, short)
        // El advisory de pantalla bancaria se preserva intacto.
        assertEquals(text, out)
    }

    @Test
    fun screenSummaryShortDoesNotModifyPasswordAdvisory() {
        val text = "Veo un campo de contraseña. Por seguridad no lo leo."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.SCREEN_SUMMARY, short)
        assertEquals(text, out)
    }

    // ===== GENERIC short =====

    @Test
    fun genericShortDoesNotModify() {
        val text = "Listo, guardé esa rutina."
        assertEquals(
            text,
            RoutinePreferenceApplier.apply(text, RoutineResponseKind.GENERIC, short)
        )
    }

    @Test
    fun notInWhatsAppMessageStaysIntactGenericKind() {
        val text = "No estás en WhatsApp. Abrílo primero y volvé a pedirme."
        assertEquals(
            text,
            RoutinePreferenceApplier.apply(text, RoutineResponseKind.GENERIC, short)
        )
    }

    // ===== CLEAR clarity (v1: no-op transform, exposed for TTS pending) =====

    @Test
    fun clearIsNoOpInV1AndDoesNotAlterText() {
        // V1: la preferencia se acepta y se expone, pero el applier NO toca
        // el texto. Las pausas reales son trabajo del SpeechController (TBD).
        val text = "Veo el campo, está abajo"
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.GENERIC, clear)
        assertEquals(text, out)
    }

    @Test
    fun clearDoesNotAlterCommaInsideEnumeration() {
        val text = "Veo: Marco, Sofi y Mamá."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, clear)
        assertEquals(text, out)
    }

    // ===== SHORT + CLEAR combined =====

    @Test
    fun shortPlusClearAppliesBoth() {
        val combined = HumanResponseStyle(
            length = ResponseLength.SHORT,
            speed = ResponseSpeed.NORMAL,
            clarity = ResponseClarity.CLEAR
        )
        val text = "Veo estos chats visibles: Marco, Sofi y Mamá. No leo mensajes completos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, combined)
        // SHORT collapses preamble + drops disclaimer; CLEAR doesn't touch the comma in the names list.
        assertEquals("Veo: Marco, Sofi y Mamá.", out)
    }

    // ===== Speed has no effect on text =====

    @Test
    fun slowSpeedDoesNotAlterText() {
        val slow = HumanResponseStyle(
            length = ResponseLength.NORMAL,
            speed = ResponseSpeed.SLOW,
            clarity = ResponseClarity.NORMAL
        )
        val text = "Veo estos chats visibles: Marco, Sofi y Mamá. No leo mensajes completos."
        assertEquals(
            text,
            RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, slow)
        )
    }

    // ===== Safety phrases as a stronger guarantee =====

    @Test
    fun shortNeverDropsAutoSendDisclaimerInGuidedHowTo() {
        val safetyPhrases = listOf(
            "Yo nunca envío la foto por vos.",
            "Yo no envío la ubicación por mi cuenta.",
            "Yo nunca envío el mensaje por vos."
        )
        safetyPhrases.forEach { safety ->
            val text = "Para mandar una foto: foo bar. $safety"
            val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.WHATSAPP_GUIDED, short)
            assertTrue(
                out.contains(safety),
                "safety phrase '$safety' must survive shortening (got: '$out')"
            )
        }
    }

    @Test
    fun shortNeverDropsScreenAdvisoryAboutSensitiveContent() {
        val advisories = listOf(
            "Esta pantalla puede contener datos bancarios. Por seguridad no la leo.",
            "Veo un campo de contraseña. Por seguridad no lo leo.",
            "Esta pantalla parece sensible. No la leo sin tu confirmación."
        )
        advisories.forEach { advisory ->
            val out = RoutinePreferenceApplier.apply(advisory, RoutineResponseKind.SCREEN_SUMMARY, short)
            assertEquals(advisory, out, "screen advisory must survive shortening")
        }
    }

    @Test
    fun shortDoesNotIntroduceNewContent() {
        // Defensa: el applier nunca debe añadir contenido (palabras nuevas que
        // no estén en el original). Comparamos cantidad de palabras.
        val text = "Veo estos chats visibles: Marco. No leo mensajes completos."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.VISIBLE_CHATS_LIST, short)
        val outWords = out.split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        val inputWords = text.split(Regex("\\W+")).filter { it.isNotBlank() }.toSet()
        // "Veo" sigue, "Marco" sigue. "estos", "chats", "visibles", "No", "leo",
        // "mensajes", "completos" pueden no estar — eso es OK. Lo que NO puede
        // pasar es que aparezca una palabra que no estaba.
        val newWords = outWords - inputWords
        assertTrue(
            newWords.isEmpty(),
            "shortening must not introduce new words; new words found: $newWords"
        )
    }

    @Test
    fun shortNoOpOnGenericNotInWhatsAppMessage() {
        val text = "No estás en WhatsApp. Abrílo primero y volvé a pedirme."
        val out = RoutinePreferenceApplier.apply(text, RoutineResponseKind.GENERIC, short)
        assertEquals(text, out)
        assertFalse(out.isBlank())
    }
}
