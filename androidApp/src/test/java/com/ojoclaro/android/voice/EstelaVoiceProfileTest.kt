package com.ojoclaro.android.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EstelaVoiceProfileTest {

    @Test
    fun speechRateIsInPremiumHumanRange() {
        // V1.6: 0.82 sonaba arrastrado; el rango premium pedido es 0.92-1.02.
        assertTrue(
            EstelaVoiceProfile.SPEECH_RATE in 0.92f..1.02f,
            "speech rate was ${EstelaVoiceProfile.SPEECH_RATE}"
        )
    }

    @Test
    fun pitchStaysProfessionalAndNatural() {
        assertTrue(
            EstelaVoiceProfile.PITCH in 0.95f..1.05f,
            "pitch was ${EstelaVoiceProfile.PITCH}"
        )
    }

    @Test
    fun controllerSelectsPremiumSpanishVoiceOfflineOnly() {
        val source = File(
            "src/main/java/com/ojoclaro/android/speech/SpeechController.kt"
        ).readText()
        assertTrue(source.contains("selectPremiumVoice(engine)"))
        assertTrue(
            source.contains("!voice.isNetworkConnectionRequired"),
            "solo voces offline: sin dependencia de red ni latencia"
        )
        assertTrue(
            source.contains("locale.country == \"AR\" -> 4"),
            "es-AR debe tener prioridad máxima"
        )
        assertTrue(source.contains("voiceSelected="), "log solo nombre/calidad/locale")
    }

    @Test
    fun pausesAndChunkLimitAreReasonable() {
        assertTrue(EstelaVoiceProfile.PAUSE_SHORT_MS in 120L..240L)
        assertTrue(EstelaVoiceProfile.PAUSE_MEDIUM_MS in 250L..420L)
        assertTrue(EstelaVoiceProfile.MAX_CHUNK_LENGTH in 160..260)
    }

    @Test
    fun speechControllerAppliesProfileConstants() {
        val source = File(
            "src/main/java/com/ojoclaro/android/speech/SpeechController.kt"
        ).readText()

        assertTrue(source.contains("configureVoiceProfile(engine)"))
        assertTrue(source.contains("setSpeechRate(EstelaVoiceProfile.SPEECH_RATE)"))
        assertTrue(source.contains("setPitch(EstelaVoiceProfile.PITCH)"))
    }
}
