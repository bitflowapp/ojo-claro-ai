package com.ojoclaro.android.voice

interface SpeechInputEngine {
    var listener: Listener?
    val isListening: Boolean

    fun startListening()
    fun stopListening()
    fun destroy()

    fun setListeningMode(mode: SpeechListeningMode) = Unit

    interface Listener {
        fun onReady()
        fun onPartialText(text: String)
        fun onFinalText(text: String)
        fun onError(errorCode: Int?)
    }
}
