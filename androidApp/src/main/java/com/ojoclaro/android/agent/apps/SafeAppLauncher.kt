package com.ojoclaro.android.agent.apps

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log

fun interface SafeAppStarter {
    fun start(spec: SafeAppLaunchIntentSpec): Boolean
}

class SafeAppLauncher(
    private val resolver: InstalledAppResolver,
    private val starter: SafeAppStarter
) {

    fun launch(
        capability: AppCapability,
        userConfirmed: Boolean = false
    ): SafeAppLaunchResult {
        if (!resolver.isPackageInstalled(capability.packageName)) {
            if (capability.packageName.isWhatsAppPackage()) {
                Log.e(TAG, "safeLaunch failed reason=not_installed package=${capability.packageName}")
            }
            return SafeAppLaunchResult.NotInstalled(
                capability = capability,
                spokenText = "No encontre ${capability.appName} instalada."
            )
        }

        if (!capability.canOpenSafely && !userConfirmed) {
            return SafeAppLaunchResult.BlockedSensitiveApp(
                capability = capability,
                spokenText = "${capability.appName} es una app sensible. Necesito confirmacion antes de abrirla."
            )
        }

        if (capability.requiresConfirmationToOpen && !userConfirmed) {
            return SafeAppLaunchResult.RequiresConfirmation(
                capability = capability,
                spokenText = "Para abrir ${capability.appName}, necesito confirmacion."
            )
        }

        val plan = SafeAppLaunchPlan(
            capability = capability,
            userConfirmed = userConfirmed
        )
        return if (starter.start(plan.intentSpec)) {
            SafeAppLaunchResult.Launched(
                plan = plan,
                spokenText = launchedSpeech(capability)
            )
        } else {
            SafeAppLaunchResult.Failed(
                capability = capability,
                spokenText = "No pude abrir ${capability.appName}. Intentala abrir manualmente."
            )
        }
    }

    private fun launchedSpeech(capability: AppCapability): String =
        when (capability.type) {
            AppCapabilityType.RIDE_HAILING ->
                "Abri ${capability.appName}. Todavia no solicite ningun viaje."
            AppCapabilityType.MESSAGING ->
                "Abri ${capability.appName}. No envie ningun mensaje."
            AppCapabilityType.PHONE ->
                "Abri ${capability.appName}. No inicie ninguna llamada."
            AppCapabilityType.PAYMENTS ->
                "Abri ${capability.appName}. No hice pagos ni transferencias."
            else ->
                "Abri ${capability.appName}. No complete ninguna accion automaticamente."
        }
}

class AndroidSafeAppStarter(
    context: Context
) : SafeAppStarter {
    private val safeContext = context
    private val useNewTask = context !is Activity

    override fun start(spec: SafeAppLaunchIntentSpec): Boolean {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
            setPackage(spec.packageName)
            if (useNewTask) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent.resolveActivity(safeContext.packageManager) == null) {
            if (spec.packageName.isWhatsAppPackage()) {
                Log.e(TAG, "safeLaunch failed reason=resolve_null package=${spec.packageName}")
            }
            return false
        }
        return try {
            safeContext.startActivity(intent)
            if (spec.packageName.isWhatsAppPackage()) {
                Log.i(TAG, "safeLaunch success package=${spec.packageName}")
            }
            true
        } catch (_: ActivityNotFoundException) {
            if (spec.packageName.isWhatsAppPackage()) {
                Log.e(TAG, "safeLaunch failed reason=ActivityNotFound package=${spec.packageName}")
            }
            false
        } catch (_: SecurityException) {
            if (spec.packageName.isWhatsAppPackage()) {
                Log.e(TAG, "safeLaunch failed reason=SecurityException package=${spec.packageName}")
            }
            false
        } catch (_: RuntimeException) {
            if (spec.packageName.isWhatsAppPackage()) {
                Log.e(TAG, "safeLaunch failed reason=RuntimeException package=${spec.packageName}")
            }
            false
        }
    }
}

private const val TAG = "EstelaWhatsApp"

private fun String.isWhatsAppPackage(): Boolean =
    equals(AppCapabilityRegistry.WHATSAPP_PACKAGE, ignoreCase = true) ||
        equals(AppCapabilityRegistry.WHATSAPP_BUSINESS_PACKAGE, ignoreCase = true)
