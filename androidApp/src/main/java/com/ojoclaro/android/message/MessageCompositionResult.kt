package com.ojoclaro.android.message

data class MessageCompositionResult(
    val proposedMessage: String,
    val spokenProposal: String,
    val styleUsed: MessageStyle,
    val requiresConfirmation: Boolean,
    val shouldSendAutomatically: Boolean,
    val safetyNotes: String? = null,
    val blockedReason: String? = null
)

