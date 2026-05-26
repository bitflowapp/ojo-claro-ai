package com.ojoclaro.android.voice

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class EstelaVoiceProfileTest {

    @Test
    fun speechRateIsCalmButNotExtremelySlow() {
        assertTrue(
            EstelaVoiceProfile.SPEECH_RATE in 0.75f..0.90f,
            "speech rate was ${EstelaVoiceProfile.SPEECH_RATE}"
        )
    }

    @Test
    fun pitchStaysProfessionalAndNatural() {
        assertTrue(
            EstelaVoiceProfile.PITCH in 0.90f..1.00f,
            "pitch was ${EstelaVoiceProfile.PITCH}"
        )
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
