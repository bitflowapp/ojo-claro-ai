package com.ojoclaro.android.agent

data class AgentSlot(
    val name: String,
    val value: String,
    val confidence: Float,
    val isSensitive: Boolean = false
)

object AgentSlotName {
    const val CONTACT_NAME = "contactName"
    const val MESSAGE_TEXT = "messageText"
    const val PHONE_NUMBER = "phoneNumber"
    const val CONTACT_TYPE = "contactType"
    const val APP_NAME = "appName"
    const val DESTINATION = "destination"
    const val REMINDER_TEXT = "reminderText"
    const val DATE = "date"
    const val TIME = "time"
    const val RECURRENCE = "recurrence"
    const val LOCATION_ALIAS = "locationAlias"
    const val LATITUDE = "latitude"
    const val LONGITUDE = "longitude"
    const val WHATSAPP_ACTION = "whatsAppAction"
    const val RAW_COMMAND = "rawCommand"
}
