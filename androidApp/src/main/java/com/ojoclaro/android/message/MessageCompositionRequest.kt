package com.ojoclaro.android.message

import com.ojoclaro.android.memory.PersonalMemorySnapshot

enum class MessageStyle {
    BRIEF,
    WARM,
    FORMAL,
    CALM,
    PROFESSIONAL,
    NEUTRAL
}

data class MessageCompositionRequest(
    val originalText: String,
    val contactName: String,
    val messageHint: String,
    val style: MessageStyle = MessageStyle.NEUTRAL,
    val memorySnapshot: PersonalMemorySnapshot = PersonalMemorySnapshot(),
    val locale: String = "es-AR"
)

