package com.ojoclaro.android.voice

fun interface VoiceRetryScheduler {
    fun schedule(delayMillis: Long, action: () -> Unit): VoiceRetryHandle
}

fun interface VoiceRetryHandle {
    fun cancel()
}
