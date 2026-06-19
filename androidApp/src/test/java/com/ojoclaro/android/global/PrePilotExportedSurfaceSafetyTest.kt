package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 2 (pre-piloto) — Bloqueo de regresión de la SUPERFICIE EXPUESTA.
 *
 * Audité el manifest y la config de red y son seguros HOY. Este test fija ese
 * estado para que un cambio futuro NO exponga superficie peligrosa sin que falle
 * el build: un componente interno marcado exported, un BIND permission borrado,
 * allowBackup encendido, cleartext habilitado o un permiso peligroso agregado.
 * Complementa [ManifestSafetyTest] (que sólo niega 4 permisos).
 *
 * Es un test de inspección de fuente (read-only); no toca el dispositivo.
 */
class PrePilotExportedSurfaceSafetyTest {

    private fun locate(relative: String): File {
        var current = File(System.getProperty("user.dir"))
        while (true) {
            val direct = File(current, "src/main/$relative")
            if (direct.exists()) return direct
            val module = File(current, "androidApp/src/main/$relative")
            if (module.exists()) return module
            val parent = current.parentFile ?: break
            current = parent
        }
        return File("androidApp/src/main/$relative")
    }

    private val manifest: String by lazy { locate("AndroidManifest.xml").readText() }

    /** Trozo del `<service ...>` que contiene [nameSuffix], hasta el próximo `<service`. */
    private fun serviceChunk(nameSuffix: String): String =
        manifest.split("<service").firstOrNull { it.contains(nameSuffix) }
            ?: error("service $nameSuffix no encontrado en el manifest")

    @Test
    fun applicationDisablesBackupAndUsesNetworkSecurityConfig() {
        assertTrue(manifest.contains("android:allowBackup=\"false\""), "allowBackup debe ser false")
        assertTrue(manifest.contains("android:networkSecurityConfig="), "debe referenciar networkSecurityConfig")
    }

    @Test
    fun internalServicesAreNotExported() {
        assertTrue(
            serviceChunk(".global.GlobalAssistantService").contains("android:exported=\"false\""),
            "GlobalAssistantService NUNCA debe estar exported"
        )
        assertTrue(
            serviceChunk(".outdoor.OutdoorForegroundService").contains("android:exported=\"false\""),
            "OutdoorForegroundService NUNCA debe estar exported"
        )
    }

    @Test
    fun exportedSystemBoundServicesCarryTheirBindPermission() {
        // Cada servicio exported existe SOLO para que el SO lo vincule; debe estar
        // protegido por su BIND permission para que ninguna app de terceros lo use.
        val a11y = serviceChunk(".accessibility.OjoClaroAccessibilityService")
        assertTrue(a11y.contains("android:exported=\"true\""), "AccessibilityService es bound por el SO")
        assertTrue(a11y.contains("android.permission.BIND_ACCESSIBILITY_SERVICE"), "falta BIND_ACCESSIBILITY_SERVICE")

        val tile = serviceChunk(".voice.OjoClaroQuickTileService")
        assertTrue(tile.contains("android.permission.BIND_QUICK_SETTINGS_TILE"), "falta BIND_QUICK_SETTINGS_TILE")

        val notif = serviceChunk(".notifications.OjoClaroWhatsAppNotificationListenerService")
        assertTrue(notif.contains("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"), "falta BIND_NOTIFICATION_LISTENER_SERVICE")
    }

    @Test
    fun noContentProviderIsDeclared() {
        assertFalse(manifest.contains("<provider"), "no debe declararse ningún ContentProvider")
    }

    @Test
    fun noDangerousPermissionsAreDeclared() {
        listOf(
            "READ_SMS", "SEND_SMS", "RECEIVE_SMS", "READ_CALL_LOG", "WRITE_CALL_LOG",
            "READ_CONTACTS", "WRITE_CONTACTS", "CALL_PHONE", "READ_PHONE_STATE",
            "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE", "ACCESS_BACKGROUND_LOCATION",
            "REQUEST_INSTALL_PACKAGES", "RECEIVE_BOOT_COMPLETED"
        ).forEach { perm ->
            assertFalse(
                manifest.contains("android.permission.$perm"),
                "permiso peligroso declarado: $perm"
            )
        }
        // Tampoco la acción de llamada directa.
        assertFalse(manifest.contains("android.intent.action.CALL"))
    }

    @Test
    fun networkConfigForbidsCleartextTraffic() {
        val cfg = locate("res/xml/network_security_config.xml").readText()
        assertTrue(
            cfg.contains("cleartextTrafficPermitted=\"false\""),
            "el tráfico en claro debe estar prohibido"
        )
    }
}
