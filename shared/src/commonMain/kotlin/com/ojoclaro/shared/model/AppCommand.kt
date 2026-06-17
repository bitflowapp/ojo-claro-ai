package com.ojoclaro.shared.model

import kotlinx.serialization.Serializable

@Serializable
enum class AppCommandType {
    DESCRIBE_SCENE,
    READ_TEXT,
    READ_DOCUMENT,
    IDENTIFY_PRODUCT,
    EMERGENCY_HELP,
    CURRENT_LOCATION,
    UNKNOWN
}

@Serializable
data class AppCommand(
    val type: AppCommandType,
    val originalText: String,
    val locale: String = "es-AR",
    val requiresCamera: Boolean = false,
    val requiresLocation: Boolean = false,
    val highRisk: Boolean = false
)
