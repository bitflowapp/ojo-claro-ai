package com.ojoclaro.android.agent.situation

/**
 * Pista de "en qué entorno / app está el usuario ahora mismo".
 *
 * El Situation Brain la usa para decidir si necesita un handoff externo, si ya
 * está en la app correcta, o si el usuario cambió de app a mitad de una tarea.
 *
 *  - IN_OJOCLARO: dentro de la propia app Ojo Claro.
 *  - IN_WHATSAPP: WhatsApp normal o WhatsApp Business.
 *  - IN_BROWSER: un navegador común.
 *  - IN_MAPS: una app de mapas / navegación.
 *  - HOME_SCREEN: el launcher / pantalla de inicio.
 *  - OTHER_APP: cualquier otra app conocida pero no relevante.
 *  - UNKNOWN: no se pudo determinar (package nulo o vacío).
 */
enum class EnvironmentHint {
    IN_OJOCLARO,
    IN_WHATSAPP,
    IN_BROWSER,
    IN_MAPS,
    HOME_SCREEN,
    OTHER_APP,
    UNKNOWN
}

private val WHATSAPP_PACKAGES = setOf(
    "com.whatsapp",
    "com.whatsapp.w4b"
)

private val MAPS_PACKAGES = setOf(
    "com.google.android.apps.maps",
    "com.waze"
)

private val BROWSER_PACKAGES = setOf(
    "com.android.chrome",
    "org.mozilla.firefox",
    "com.opera.browser",
    "com.brave.browser",
    "com.microsoft.emmx",
    "com.sec.android.app.sbrowser"
)

private val HOME_SCREEN_PACKAGES = setOf(
    "com.google.android.apps.nexuslauncher",
    "com.android.launcher",
    "com.android.launcher3",
    "com.sec.android.app.launcher",
    "com.miui.home"
)

/**
 * Mapea un package name a un [EnvironmentHint]. Función pura: no usa APIs de
 * Android, solo compara strings.
 *
 * Reglas:
 *  - null o blank -> UNKNOWN
 *  - package de Ojo Claro -> IN_OJOCLARO
 *  - WhatsApp normal o business -> IN_WHATSAPP
 *  - Maps / Waze -> IN_MAPS
 *  - navegadores comunes -> IN_BROWSER
 *  - launchers comunes -> HOME_SCREEN
 *  - cualquier otro -> OTHER_APP
 */
fun environmentHintFor(packageName: String?): EnvironmentHint {
    val pkg = packageName?.trim()?.lowercase()
    if (pkg.isNullOrBlank()) return EnvironmentHint.UNKNOWN

    return when {
        pkg == "com.ojoclaro.android" || pkg.startsWith("com.ojoclaro.") -> EnvironmentHint.IN_OJOCLARO
        pkg in WHATSAPP_PACKAGES -> EnvironmentHint.IN_WHATSAPP
        pkg in MAPS_PACKAGES -> EnvironmentHint.IN_MAPS
        pkg in BROWSER_PACKAGES -> EnvironmentHint.IN_BROWSER
        pkg in HOME_SCREEN_PACKAGES -> EnvironmentHint.HOME_SCREEN
        else -> EnvironmentHint.OTHER_APP
    }
}
