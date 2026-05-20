package com.ojoclaro.android.agent.apps

import com.ojoclaro.android.agent.task.AgentTaskPlanner
import com.ojoclaro.android.agent.task.AgentTaskRiskLevel

class AppCapabilityRegistry(
    private val knownCapabilities: List<AppCapability> = DEFAULT_CAPABILITIES
) {

    fun allKnown(): List<AppCapability> = knownCapabilities

    fun capabilitiesForType(type: AppCapabilityType): List<AppCapability> =
        knownCapabilities.filter { it.type == type }

    fun findByPackageName(packageName: String): AppCapability? =
        knownCapabilities.firstOrNull { it.packageName.equals(packageName, ignoreCase = true) }

    fun findByAppName(appName: String): AppCapability? {
        val normalized = normalizeAppName(appName)
        return knownCapabilities.firstOrNull { capability ->
            normalizeAppName(capability.appName) == normalized
        }
    }

    fun installedCapabilities(resolver: InstalledAppResolver): List<AppCapability> =
        knownCapabilities.filter { resolver.isPackageInstalled(it.packageName) }

    fun installedCapabilitiesForType(
        type: AppCapabilityType,
        resolver: InstalledAppResolver
    ): List<AppCapability> =
        capabilitiesForType(type).filter { resolver.isPackageInstalled(it.packageName) }

    fun firstInstalledRideApp(
        resolver: InstalledAppResolver,
        preferredHint: String? = null
    ): AppCapability? {
        val installed = installedCapabilitiesForType(AppCapabilityType.RIDE_HAILING, resolver)
        if (installed.isEmpty()) return null
        val normalizedHint = preferredHint?.let(::normalizeAppName)
        return installed.firstOrNull { capability ->
            normalizedHint != null &&
                (normalizeAppName(capability.appName) == normalizedHint ||
                    capability.packageName.equals(preferredHint, ignoreCase = true))
        } ?: installed.first()
    }

    companion object {
        const val UBER_PACKAGE: String = "com.ubercab"
        const val CABIFY_PACKAGE: String = "com.cabify.rider"
        const val DIDI_PACKAGE: String = "com.didiglobal.passenger"
        const val WHATSAPP_PACKAGE: String = "com.whatsapp"
        const val WHATSAPP_BUSINESS_PACKAGE: String = "com.whatsapp.w4b"
        const val TELEGRAM_PACKAGE: String = "org.telegram.messenger"
        const val GOOGLE_MESSAGES_PACKAGE: String = "com.google.android.apps.messaging"
        const val GOOGLE_MAPS_PACKAGE: String = "com.google.android.apps.maps"
        const val ANDROID_SETTINGS_PACKAGE: String = "com.android.settings"
        const val CHROME_PACKAGE: String = "com.android.chrome"
        const val GOOGLE_DIALER_PACKAGE: String = "com.google.android.dialer"
        const val ANDROID_DIALER_PACKAGE: String = "com.android.dialer"
        const val MERCADO_PAGO_PACKAGE: String = "com.mercadopago.wallet"
        const val BANCO_GALICIA_PACKAGE: String = "com.galicia.mobile"
        const val SANTANDER_AR_PACKAGE: String = "ar.com.santander.rio.mbanking"
        const val BBVA_AR_PACKAGE: String = "com.bbva.nxt_argentina"
        const val ICBC_AR_PACKAGE: String = "com.icbc.mobile.abroad"
        const val BANCO_NACION_PACKAGE: String = "ar.com.bna"
        const val BANCO_PROVINCIA_PACKAGE: String = "ar.com.bapro.bip"

        val DEFAULT_CAPABILITIES: List<AppCapability> = listOf(
            ride("Uber", UBER_PACKAGE),
            ride("Cabify", CABIFY_PACKAGE),
            ride("DiDi", DIDI_PACKAGE),
            messaging("WhatsApp", WHATSAPP_PACKAGE),
            messaging("WhatsApp Business", WHATSAPP_BUSINESS_PACKAGE),
            messaging("Telegram", TELEGRAM_PACKAGE),
            messaging("Messages", GOOGLE_MESSAGES_PACKAGE),
            AppCapability(
                appName = "Google Maps",
                packageName = GOOGLE_MAPS_PACKAGE,
                type = AppCapabilityType.MAPS,
                riskLevel = AgentTaskRiskLevel.LOW,
                canOpenSafely = true,
                requiresConfirmationToOpen = false,
                forbiddenActionsDescription = "No inicia navegacion ni comparte ubicacion automaticamente."
            ),
            AppCapability(
                appName = "Android Settings",
                packageName = ANDROID_SETTINGS_PACKAGE,
                type = AppCapabilityType.SETTINGS,
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                canOpenSafely = true,
                requiresConfirmationToOpen = true,
                forbiddenActionsDescription = "No cambia permisos ni ajustes automaticamente."
            ),
            AppCapability(
                appName = "Chrome",
                packageName = CHROME_PACKAGE,
                type = AppCapabilityType.BROWSER,
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                canOpenSafely = true,
                requiresConfirmationToOpen = true,
                forbiddenActionsDescription = "No compra, envia formularios ni confirma acciones web."
            ),
            phone("Phone", GOOGLE_DIALER_PACKAGE),
            phone("Android Dialer", ANDROID_DIALER_PACKAGE),
            payment("Mercado Pago", MERCADO_PAGO_PACKAGE),
            payment("Banco Galicia", BANCO_GALICIA_PACKAGE),
            payment("Santander", SANTANDER_AR_PACKAGE),
            payment("BBVA", BBVA_AR_PACKAGE),
            payment("ICBC", ICBC_AR_PACKAGE),
            payment("Banco Nacion", BANCO_NACION_PACKAGE),
            payment("Banco Provincia", BANCO_PROVINCIA_PACKAGE)
        )

        fun normalizeAppName(value: String): String =
            AgentTaskPlanner.normalize(value)

        private fun ride(appName: String, packageName: String): AppCapability =
            AppCapability(
                appName = appName,
                packageName = packageName,
                type = AppCapabilityType.RIDE_HAILING,
                riskLevel = AgentTaskRiskLevel.LOW,
                canOpenSafely = true,
                requiresConfirmationToOpen = false,
                forbiddenActionsDescription = "No solicita viajes, no confirma precio y no toca pagos."
            )

        private fun messaging(appName: String, packageName: String): AppCapability =
            AppCapability(
                appName = appName,
                packageName = packageName,
                type = AppCapabilityType.MESSAGING,
                riskLevel = AgentTaskRiskLevel.MEDIUM,
                canOpenSafely = true,
                requiresConfirmationToOpen = false,
                forbiddenActionsDescription = "No envia mensajes ni toca chats automaticamente."
            )

        private fun phone(appName: String, packageName: String): AppCapability =
            AppCapability(
                appName = appName,
                packageName = packageName,
                type = AppCapabilityType.PHONE,
                riskLevel = AgentTaskRiskLevel.HIGH,
                canOpenSafely = true,
                requiresConfirmationToOpen = true,
                forbiddenActionsDescription = "No inicia llamadas automaticamente."
            )

        private fun payment(appName: String, packageName: String): AppCapability =
            AppCapability(
                appName = appName,
                packageName = packageName,
                type = AppCapabilityType.PAYMENTS,
                riskLevel = AgentTaskRiskLevel.HIGH,
                canOpenSafely = false,
                requiresConfirmationToOpen = true,
                forbiddenActionsDescription = "No paga, no transfiere, no compra y no lee datos financieros completos."
            )
    }
}
