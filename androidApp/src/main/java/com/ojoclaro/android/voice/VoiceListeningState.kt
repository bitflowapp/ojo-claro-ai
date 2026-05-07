package com.ojoclaro.android.voice

enum class VoiceListeningState {
    IDLE,
    LISTENING,
    PROCESSING,
    SPEAKING,
    WAITING_RETRY,
    STOPPED_BY_USER,
    ERROR
}
