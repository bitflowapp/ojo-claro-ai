package com.ojoclaro.android.agent.apps

import com.ojoclaro.android.agent.task.AgentTaskRiskLevel

enum class AppCapabilityType {
    RIDE_HAILING,
    MESSAGING,
    MAPS,
    SETTINGS,
    BROWSER,
    PHONE,
    PAYMENTS,
    UNKNOWN
}

data class AppCapability(
    val appName: String,
    val packageName: String,
    val type: AppCapabilityType,
    val riskLevel: AgentTaskRiskLevel,
    val canOpenSafely: Boolean,
    val requiresConfirmationToOpen: Boolean,
    val forbiddenActionsDescription: String
) {
    init {
        require(appName.isNotBlank()) { "appName must not be blank" }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
        require(forbiddenActionsDescription.isNotBlank()) {
            "forbiddenActionsDescription must not be blank"
        }
        if (riskLevel >= AgentTaskRiskLevel.HIGH) {
            require(requiresConfirmationToOpen || !canOpenSafely) {
                "high-risk apps must require confirmation or be blocked"
            }
        }
    }
}

data class SafeAppLaunchIntentSpec(
    val action: String = ACTION_MAIN,
    val category: String = CATEGORY_LAUNCHER,
    val packageName: String
) {
    init {
        require(action == ACTION_MAIN) { "safe app launch only supports ACTION_MAIN" }
        require(category == CATEGORY_LAUNCHER) {
            "safe app launch only supports CATEGORY_LAUNCHER"
        }
        require(packageName.isNotBlank()) { "packageName must not be blank" }
    }

    companion object {
        const val ACTION_MAIN: String = "android.intent.action.MAIN"
        const val CATEGORY_LAUNCHER: String = "android.intent.category.LAUNCHER"
    }
}

data class SafeAppLaunchPlan(
    val capability: AppCapability,
    val intentSpec: SafeAppLaunchIntentSpec = SafeAppLaunchIntentSpec(
        packageName = capability.packageName
    ),
    val userConfirmed: Boolean = false
) {
    val appName: String
        get() = capability.appName

    val packageName: String
        get() = capability.packageName
}

sealed class SafeAppLaunchResult {
    data class Launched(
        val plan: SafeAppLaunchPlan,
        override val spokenText: String
    ) : SafeAppLaunchResult()

    data class NotInstalled(
        val capability: AppCapability,
        override val spokenText: String
    ) : SafeAppLaunchResult()

    data class RequiresConfirmation(
        val capability: AppCapability,
        override val spokenText: String
    ) : SafeAppLaunchResult()

    data class BlockedSensitiveApp(
        val capability: AppCapability,
        override val spokenText: String
    ) : SafeAppLaunchResult()

    data class Failed(
        val capability: AppCapability,
        override val spokenText: String
    ) : SafeAppLaunchResult()

    abstract val spokenText: String
}
