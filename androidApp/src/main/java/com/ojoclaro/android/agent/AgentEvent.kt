package com.ojoclaro.android.agent

sealed interface AgentEvent {
    data class VoiceTextReceived(val text: String) : AgentEvent
    data class PartialVoiceTextReceived(val text: String) : AgentEvent
    data object SpeechStarted : AgentEvent
    data object SpeechFinished : AgentEvent
    data class PermissionGranted(val permission: String) : AgentEvent
    data class PermissionDenied(val permission: String) : AgentEvent
    data object ConfirmationReceived : AgentEvent
    data object CancellationReceived : AgentEvent
    data object Timeout : AgentEvent
    data class RecoverableError(val message: String) : AgentEvent
    data class FatalError(val message: String) : AgentEvent
}
