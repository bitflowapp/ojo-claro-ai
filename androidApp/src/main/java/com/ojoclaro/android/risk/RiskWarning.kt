package com.ojoclaro.android.risk

data class RiskWarning(
    val type: RiskType,
    val spokenText: String,
    val severity: Int,
    val requiresConfirmation: Boolean
)
