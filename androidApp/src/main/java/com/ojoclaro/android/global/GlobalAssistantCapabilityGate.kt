package com.ojoclaro.android.global

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

data class GlobalAssistantCapability(
    val foregroundServiceReady: Boolean,
    val notificationReady: Boolean,
    val overlayReady: Boolean,
    val microphoneContinuationReady: Boolean,
    val fallbackReturnReady: Boolean,
    val canSafelyContinueOutsideApp: Boolean,
    val reason: String?
)

class GlobalAssistantCapabilityGate(
    private val context: Context? = null,
    private val overrideCapability: GlobalAssistantCapability? = null
) {
    fun evaluate(): GlobalAssistantCapability {
        overrideCapability?.let { return it }
        val appContext = context?.applicationContext
            ?: return unavailable("Sin contexto Android.")

        val foregroundServiceReady =
            hasManifestPermission(appContext, Manifest.permission.FOREGROUND_SERVICE) &&
                (
                    Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE ||
                        hasManifestPermission(appContext, Manifest.permission.FOREGROUND_SERVICE_MICROPHONE)
                    )
        val notificationReady = canPostNotifications(appContext)
        val overlayReady = canDrawOverlay(appContext)
        val microphonePermissionReady = hasRuntimePermission(appContext, Manifest.permission.RECORD_AUDIO)
        val microphoneContinuationReady =
            foregroundServiceReady && notificationReady && overlayReady && microphonePermissionReady
        val fallbackReturnReady = foregroundServiceReady && notificationReady
        val canSafelyContinueOutsideApp =
            foregroundServiceReady &&
                notificationReady &&
                overlayReady &&
                microphoneContinuationReady
        val reason = when {
            canSafelyContinueOutsideApp -> null
            !foregroundServiceReady -> "foreground_service_unavailable"
            !notificationReady -> "notification_unavailable"
            !overlayReady -> "overlay_unavailable"
            !microphonePermissionReady -> "microphone_permission_missing"
            else -> "microphone_continuation_unavailable"
        }

        return GlobalAssistantCapability(
            foregroundServiceReady = foregroundServiceReady,
            notificationReady = notificationReady,
            overlayReady = overlayReady,
            microphoneContinuationReady = microphoneContinuationReady,
            fallbackReturnReady = fallbackReturnReady,
            canSafelyContinueOutsideApp = canSafelyContinueOutsideApp,
            reason = reason
        )
    }

    private fun hasRuntimePermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun canPostNotifications(context: Context): Boolean {
        val runtimeReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasRuntimePermission(context, Manifest.permission.POST_NOTIFICATIONS)
        if (!runtimeReady) return false
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    private fun canDrawOverlay(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context)

    private fun hasManifestPermission(context: Context, permission: String): Boolean {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
        }
        return packageInfo.requestedPermissions?.contains(permission) == true
    }

    companion object {
        fun unavailable(reason: String = "global_assistant_unavailable"): GlobalAssistantCapability =
            GlobalAssistantCapability(
                foregroundServiceReady = false,
                notificationReady = false,
                overlayReady = false,
                microphoneContinuationReady = false,
                fallbackReturnReady = false,
                canSafelyContinueOutsideApp = false,
                reason = reason
            )

        fun fromFlags(
            foregroundServiceReady: Boolean = true,
            notificationReady: Boolean = true,
            overlayReady: Boolean = true,
            microphoneContinuationReady: Boolean = true,
            fallbackReturnReady: Boolean = true
        ): GlobalAssistantCapability {
            val canContinue = foregroundServiceReady &&
                notificationReady &&
                overlayReady &&
                microphoneContinuationReady
            return GlobalAssistantCapability(
                foregroundServiceReady = foregroundServiceReady,
                notificationReady = notificationReady,
                overlayReady = overlayReady,
                microphoneContinuationReady = microphoneContinuationReady,
                fallbackReturnReady = fallbackReturnReady,
                canSafelyContinueOutsideApp = canContinue,
                reason = if (canContinue) null else "test_unavailable"
            )
        }
    }
}
