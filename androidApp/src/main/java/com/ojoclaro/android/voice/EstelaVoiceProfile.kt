package com.ojoclaro.android.voice

object EstelaVoiceProfile {
    // V1.6 — perfil premium: 0.82 sonaba arrastrado/robótico. Rango humano:
    // rate 0.92-1.02, pitch 0.95-1.05.
    const val SPEECH_RATE: Float = 0.97f
    const val PITCH: Float = 1.0f
    const val PAUSE_SHORT_MS: Long = 180L
    const val PAUSE_MEDIUM_MS: Long = 320L
    const val MAX_CHUNK_LENGTH: Int = 220
}
