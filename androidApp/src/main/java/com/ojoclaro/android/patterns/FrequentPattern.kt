package com.ojoclaro.android.patterns

data class FrequentPattern(
    val id: String,
    val commandType: String,
    val normalizedCommand: String,
    val count: Int,
    val firstSeenMillis: Long,
    val lastSeenMillis: Long,
    val lastAppPackage: String?,
    val isSensitive: Boolean,
    val userApprovedForSuggestions: Boolean
)
