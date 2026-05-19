package com.ojoclaro.android.agent.contacts

data class ContactCandidate(
    val displayName: String,
    val phoneNumber: String?,
    val source: ContactSource
) {
    init {
        require(displayName.isNotBlank()) { "displayName must not be blank" }
    }
}

enum class ContactSource {
    DEVICE_CONTACTS,
    WHATSAPP_VISIBLE_SCREEN,
    USER_TYPED,
    UNKNOWN
}
