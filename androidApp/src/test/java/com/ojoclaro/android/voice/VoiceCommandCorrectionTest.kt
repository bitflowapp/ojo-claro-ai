package com.ojoclaro.android.voice

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoiceCommandCorrectionTest {

    @Test
    fun samsungWhatsAppMisrecognitionsResolveOnlyToOpenWhatsApp() {
        val samsung = VoiceCommandCorrection.correct("abrir ure Max")

        assertEquals(VoiceCommandTargetIntent.OPEN_WHATSAPP, samsung.targetIntent)
        assertTrue(
            samsung.confidence in setOf(
                VoiceCommandCorrectionConfidence.HIGH,
                VoiceCommandCorrectionConfidence.MEDIUM
            )
        )
        assertTrue(samsung.correctedText.contains("WhatsApp"))
        assertTrue(samsung.targetIntent.isLowRiskExecutable)

        listOf(
            "abrir guasap",
            "abrir wasá",
            "abrir wasap",
            "abrir wpp",
            "abrir wp"
        ).forEach { phrase ->
            val correction = VoiceCommandCorrection.correct(phrase)

            assertEquals(VoiceCommandTargetIntent.OPEN_WHATSAPP, correction.targetIntent, phrase)
            assertEquals(VoiceCommandCorrectionConfidence.HIGH, correction.confidence, phrase)
            assertFalse(correction.requiresConfirmation, phrase)
            assertTrue(correction.shouldAutoExecute, phrase)
        }
    }

    @Test
    fun realSamsungNoiseDoesNotOpenWhatsApp() {
        listOf(
            "canción de Marco Antonio",
            "android",
            "sí Aurelio"
        ).forEach { phrase ->
            val correction = VoiceCommandCorrection.correct(phrase)

            assertEquals(VoiceCommandTargetIntent.NONE, correction.targetIntent, phrase)
            assertFalse(correction.shouldAutoExecute, phrase)
            assertTrue(VoiceCommandCorrection.isKnownRecognizerNoise(phrase), phrase)
        }
    }

    @Test
    fun lowRiskScreenAndChatCommandsAreNormalized() {
        assertTarget("qué hay en la pantalla", VoiceCommandTargetIntent.READ_VISIBLE_SCREEN)
        assertTarget("que hay en pantalla", VoiceCommandTargetIntent.READ_VISIBLE_SCREEN)
        assertTarget("qué puedo hacer", VoiceCommandTargetIntent.WHAT_CAN_I_DO)
        assertTarget("que chat ves", VoiceCommandTargetIntent.READ_VISIBLE_CHATS)
        assertTarget("qué chats ves", VoiceCommandTargetIntent.READ_VISIBLE_CHATS)
    }

    @Test
    fun lowRiskControlCommandsAreNormalized() {
        assertTarget("repetir", VoiceCommandTargetIntent.REPEAT_LAST)
        assertTarget("repite", VoiceCommandTargetIntent.REPEAT_LAST)
        assertTarget("repetí", VoiceCommandTargetIntent.REPEAT_LAST)
        assertTarget("resetea", VoiceCommandTargetIntent.RESET_FLOW)
        assertTarget("resetear", VoiceCommandTargetIntent.RESET_FLOW)
        assertTarget("volver al inicio", VoiceCommandTargetIntent.RESET_FLOW)
        assertTarget("cállate", VoiceCommandTargetIntent.STOP_SPEAKING)
        assertTarget("callate", VoiceCommandTargetIntent.STOP_SPEAKING)
        assertTarget("silencio", VoiceCommandTargetIntent.STOP_SPEAKING)
        assertTarget("pausar robot", VoiceCommandTargetIntent.PAUSE_ROBOT)
        assertTarget("encender robot", VoiceCommandTargetIntent.ENABLE_ROBOT)
    }

    @Test
    fun sensitiveOrIrreversibleCommandsRejectFuzzyCorrection() {
        listOf(
            "mandale mensaje a Marco",
            "enviar ubicación",
            "enviar foto",
            "llamar a Marco",
            "pagar",
            "banco",
            "clave",
            "contraseña",
            "cbu",
            "cvu",
            "tarjeta"
        ).forEach { phrase ->
            val correction = VoiceCommandCorrection.correct(phrase)

            assertEquals(VoiceCommandCorrectionType.REJECTED_SENSITIVE, correction.correctionType, phrase)
            assertEquals(VoiceCommandTargetIntent.NONE, correction.targetIntent, phrase)
            assertFalse(correction.shouldAutoExecute, phrase)
            assertFalse(correction.canBeConfirmedSafely, phrase)
        }
    }

    @Test
    fun mediumSimilarityRequiresConfirmationBeforeRouting() {
        val correction = VoiceCommandCorrection.correct("abrir whats")

        assertEquals(VoiceCommandTargetIntent.OPEN_WHATSAPP, correction.targetIntent)
        assertEquals(VoiceCommandCorrectionConfidence.MEDIUM, correction.confidence)
        assertTrue(correction.requiresConfirmation)
        assertFalse(correction.shouldAutoExecute)
        assertTrue(correction.canBeConfirmedSafely)
        assertEquals("¿Quisiste decir abrir WhatsApp?", correction.confirmationPrompt())
    }

    @Test
    fun confirmationWordsAreStrictAndDoNotAcceptNoise() {
        assertEquals(
            VoiceCommandConfirmationResponse.CONFIRM,
            VoiceCommandCorrection.confirmationResponse("sí")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.CONFIRM,
            VoiceCommandCorrection.confirmationResponse("confirmar")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.CONFIRM,
            VoiceCommandCorrection.confirmationResponse("correcto")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.CANCEL,
            VoiceCommandCorrection.confirmationResponse("no")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.CANCEL,
            VoiceCommandCorrection.confirmationResponse("cancelar")
        )
        assertEquals(
            VoiceCommandConfirmationResponse.NONE,
            VoiceCommandCorrection.confirmationResponse("sí Aurelio")
        )
    }

    @Test
    fun longRecognizerInputIsCappedBeforeFuzzyWork() {
        val correction = VoiceCommandCorrection.correct("android " + "x ".repeat(5_000))

        assertEquals(VoiceCommandCorrectionType.NO_CORRECTION, correction.correctionType)
        assertEquals(VoiceCommandTargetIntent.NONE, correction.targetIntent)
        assertFalse(correction.shouldAutoExecute)
        assertTrue(correction.originalText.length <= 240)
    }

    @Test
    fun pendingCorrectionExpiresAndResetTargetIsSafe() {
        val correction = VoiceCommandCorrection.correct("abrir whats")
        val pending = PendingVoiceCommandCorrection(
            correction = correction,
            createdAtMillis = 1_000L,
            expiresAtMillis = 1_000L + VoiceCommandCorrection.CONFIRMATION_TTL_MILLIS
        )

        assertFalse(pending.isExpired(1_500L))
        assertTrue(pending.isExpired(1_000L + VoiceCommandCorrection.CONFIRMATION_TTL_MILLIS))
        assertTarget("resetea", VoiceCommandTargetIntent.RESET_FLOW)
    }

    private fun assertTarget(
        phrase: String,
        target: VoiceCommandTargetIntent
    ) {
        val correction = VoiceCommandCorrection.correct(phrase)

        assertEquals(target, correction.targetIntent, phrase)
        assertEquals(VoiceCommandCorrectionConfidence.HIGH, correction.confidence, phrase)
        assertFalse(correction.requiresConfirmation, phrase)
        assertTrue(correction.shouldAutoExecute, phrase)
    }
}
