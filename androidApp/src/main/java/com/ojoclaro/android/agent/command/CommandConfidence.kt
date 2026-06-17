package com.ojoclaro.android.agent.command

enum class CommandConfidence(val score: Float) {
    HIGH(0.95f),
    MEDIUM(0.7f),
    LOW(0.25f)
}
