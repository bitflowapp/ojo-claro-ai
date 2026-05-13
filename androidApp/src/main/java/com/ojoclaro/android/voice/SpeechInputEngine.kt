package com.ojoclaro.android.voice

interface SpeechInputEngine {
    var listener: Listener?
    val isListening: Boolean
    val speechEngine: VoiceSpeechEngine
        get() = VoiceSpeechEngine.PLATFORM_DEFAULT

    fun startListening()
    fun stopListening()
    fun resetRecognizer() = Unit
    fun destroy()

    fun setListeningMode(mode: SpeechListeningMode) = Unit

    interface Listener {
        fun onReady()
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onError(errorCode: Int?)
    }
}
