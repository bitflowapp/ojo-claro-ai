package com.ojoclaro.android.capabilities

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.ojoclaro.android.accessibility.AccessibilityScreenReader

/**
 * Devuelve el estado actual de cada capability del sistema.
 *
 * El registry no recuerda estado entre llamadas: siempre pregunta al sistema operativo,
 * para evitar caches obsoletos cuando el usuario activa permisos en Ajustes.
 *
 * Para tests unitarios: usá el constructor principal con [availabilityOverrides].
 * Las capabilities no sobreescritas usan false como valor seguro cuando no hay Context.
 */
class CapabilityRegistry(
    private val context: Context?,
    private val whatsAppPackage: String = WHATSAPP_PACKAGE,
    private val cloudAiEnabled: Boolean = false,
    private val availabilityOverrides: Map<Capability, Boolean> = emptyMap()
) {
    constructor(context: Context) : this(
        context = context,
        whatsAppPackage = WHATSAPP_PACKAGE,
        cloudAiEnabled = false,
        availabilityOverrides = emptyMap()
    )

    fun snapshot(): List<CapabilityStatus> = Capability.entries.map(::status)

    fun status(capability: Capability): CapabilityStatus {
        availabilityOverrides[capability]?.let { overrideValue ->
            return buildStatus(capability, overrideValue)
        }

        val safeContext = context ?: return buildStatus(capability, available = false)

        return when (capability) {
            Capability.CAMERA -> cameraStatus(safeContext)

            Capability.ACCESSIBILITY_SERVICE -> buildStatus(
                capability = capability,
                available = AccessibilityScreenReader.isServiceEnabled(safeContext)
            )

            Capability.WHATSAPP -> buildStatus(
                capability = capability,
                available = installedWhatsAppPackage(safeContext) != null
            )

            Capability.TEXT_TO_SPEECH -> buildStatus(
                capability = capability,
                available = true
            )

            Capability.OCR_LOCAL -> buildStatus(
                capability = capability,
                available = hasCameraHardware(safeContext)
            )

            Capability.CLOUD_AI -> buildStatus(
                capability = capability,
                available = cloudAiEnabled
            )
        }
    }

    fun isAvailable(capability: Capability): Boolean = status(capability).isAvailable

    private fun cameraStatus(context: Context): CapabilityStatus {
        if (!hasCameraHardware(context)) {
            return CapabilityStatus(
                capability = Capability.CAMERA,
                isAvailable = false,
                userMessageWhenMissing = "Este teléfono no tiene una cámara compatible para leer texto.",
                canRequestUserAction = false
            )
        }

        if (!isCameraGranted(context)) {
            return CapabilityStatus(
                capability = Capability.CAMERA,
                isAvailable = false,
                userMessageWhenMissing = Capability.MSG_CAMERA_MISSING,
                canRequestUserAction = true
            )
        }

        return CapabilityStatus(
            capability = Capability.CAMERA,
            isAvailable = true,
            userMessageWhenMissing = Capability.MSG_CAMERA_MISSING,
            canRequestUserAction = false
        )
    }

    private fun buildStatus(
        capability: Capability,
        available: Boolean
    ): CapabilityStatus {
        val (message, canRequest) = when (capability) {
            Capability.CAMERA ->
                Capability.MSG_CAMERA_MISSING to true

            Capability.ACCESSIBILITY_SERVICE ->
                Capability.MSG_ACCESSIBILITY_MISSING to true

            Capability.WHATSAPP ->
                Capability.MSG_WHATSAPP_MISSING to false

            Capability.TEXT_TO_SPEECH ->
                Capability.MSG_TTS_MISSING to false

            Capability.OCR_LOCAL ->
                Capability.MSG_OCR_MISSING to false

            Capability.CLOUD_AI ->
                Capability.MSG_CLOUD_AI_MISSING to false
        }

        return CapabilityStatus(
            capability = capability,
            isAvailable = available,
            userMessageWhenMissing = message,
            canRequestUserAction = canRequest && !available
        )
    }

    private fun isCameraGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasCameraHardware(context: Context): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)
    }

    private fun installedWhatsAppPackage(context: Context): String? {
        return whatsAppPackages.firstOrNull { packageName ->
            isPackageInstalled(context, packageName)
        }
    }

    private fun isPackageInstalled(
        context: Context,
        packageName: String
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    private val whatsAppPackages: List<String> = listOf(
        whatsAppPackage,
        WHATSAPP_BUSINESS_PACKAGE
    ).distinct()

    companion object {
        const val WHATSAPP_PACKAGE = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE = "com.whatsapp.w4b"
    }
}
