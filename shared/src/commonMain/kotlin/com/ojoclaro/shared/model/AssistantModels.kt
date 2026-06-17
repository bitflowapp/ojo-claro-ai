package com.ojoclaro.shared.model

import kotlinx.serialization.Serializable

@Serializable
data class AssistRequest(
    val command: AppCommand,
    val userMessage: String,
    val imageBase64: String? = null,
    val location: GeoPoint? = null,
    val deviceLocale: String = "es-AR",
    val accessibilityMode: AccessibilityMode = AccessibilityMode.VOICE_FIRST
)

@Serializable
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null
)

@Serializable
enum class AccessibilityMode {
    VOICE_FIRST,
    LOW_VISION_HIGH_CONTRAST,
    FAMILY_SETUP
}

@Serializable
data class AssistResponse(
    val spokenText: String,
    val shortText: String,
    val confidence: ConfidenceLevel,
    val category: ResponseCategory,
    val safetyNotice: String? = null,
    val suggestedActions: List<SuggestedAction> = emptyList()
)

@Serializable
enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW
}

@Serializable
enum class ResponseCategory {
    TEXT_READING,
    SCENE_DESCRIPTION,
    PRODUCT_IDENTIFICATION,
    DOCUMENT_SUMMARY,
    LOCATION_HELP,
    EMERGENCY,
    SYSTEM
}

@Serializable
data class SuggestedAction(
    val id: String,
    val label: String,
    val commandType: AppCommandType
)
