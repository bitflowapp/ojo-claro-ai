package com.ojoclaro.android.agent.apps

import com.ojoclaro.android.external.CommandResult

fun SafeAppLaunchResult.toCommandResult(): CommandResult =
    when (this) {
        is SafeAppLaunchResult.Launched -> CommandResult.Success(spokenText)
        is SafeAppLaunchResult.NotInstalled -> CommandResult.Failed(
            spokenText = spokenText,
            recoverable = true
        )
        is SafeAppLaunchResult.RequiresConfirmation -> CommandResult.NeedsConfirmation(
            spokenText = spokenText,
            confirmationId = "safe-app-open:${capability.packageName}"
        )
        is SafeAppLaunchResult.BlockedSensitiveApp -> CommandResult.Failed(
            spokenText = spokenText,
            recoverable = true
        )
        is SafeAppLaunchResult.Failed -> CommandResult.Failed(
            spokenText = spokenText,
            recoverable = true
        )
    }
