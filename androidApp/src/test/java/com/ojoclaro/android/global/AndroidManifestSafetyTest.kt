package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AndroidManifestSafetyTest {

    private val manifestText: String
        get() = File("src/main/AndroidManifest.xml").readText()

    @Test
    fun declaresVisibleForegroundAndOverlayPermissions() {
        val text = manifestText

        assertTrue(text.contains("android.permission.FOREGROUND_SERVICE"))
        assertTrue(text.contains("android.permission.FOREGROUND_SERVICE_MICROPHONE"))
        assertTrue(text.contains("android.permission.SYSTEM_ALERT_WINDOW"))
        // V1.13 Camera Assist: el asistente declara microphone|camera; el
        // tipo camera solo se ACTIVA mientras la cámara de Estela está
        // abierta (patrón on-demand, igual que Outdoor Describir).
        assertTrue(text.contains("android:foregroundServiceType=\"microphone|camera\""))
        assertTrue(text.contains("android.permission.FOREGROUND_SERVICE_CAMERA"))
    }

    @Test
    fun declaresEstelaQuickSettingsTile() {
        val text = manifestText

        assertTrue(text.contains(".voice.OjoClaroQuickTileService"))
        assertTrue(text.contains("android.service.quicksettings.action.QS_TILE"))
        assertTrue(text.contains("android.permission.BIND_QUICK_SETTINGS_TILE"))
        assertTrue(text.contains("@string/quick_tile_label"))
    }

    @Test
    fun outdoorGuidanceUsesForegroundLocationWithoutBackgroundLocation() {
        val text = manifestText

        // Fase 3B: navegación exterior con foreground service tipo location.
        // Android 14+ exige además el tipo camera para la captura bajo demanda.
        assertTrue(text.contains("android.permission.FOREGROUND_SERVICE_LOCATION"))
        assertTrue(text.contains("android.permission.FOREGROUND_SERVICE_CAMERA"))
        assertTrue(text.contains("android:foregroundServiceType=\"location|camera\""))
        assertTrue(text.contains(".outdoor.OutdoorForegroundService"))
        // ...pero JAMÁS ubicación en background (regla de la misión).
        assertFalse(text.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
    }

    @Test
    fun doesNotAddForbiddenPermissionsOrCallAction() {
        val text = manifestText

        assertFalse(text.contains("android.permission.READ_CONTACTS"))
        assertFalse(text.contains("android.permission.CALL_PHONE"))
        assertFalse(text.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertFalse(text.contains("android.intent.action.CALL"))
    }

    @Test
    fun debugSubmitTextIsNotDeclaredInMainManifest() {
        val text = manifestText

        assertFalse(text.contains("com.ojoclaro.DEBUG_SUBMIT_TEXT"))
        assertFalse(text.contains("com.ojoclaro.DEBUG_RUN_SCREEN_DIAGNOSTIC"))
        assertFalse(text.contains("com.ojoclaro.DEBUG_ASK_SCREEN"))
    }

    @Test
    fun debugSubmitTextReceiverIsRuntimeDebugOnlyAndSanitized() {
        val text = File("src/main/java/com/ojoclaro/android/MainActivity.kt").readText()

        assertTrue(text.contains("if (!BuildConfig.DEBUG || debugSubmitTextReceiver != null) return"))
        assertTrue(text.contains("debugSubmitTextDecision("))
        assertTrue(text.contains("DEBUG_SUBMIT_TEXT_MAX_CHARS"))
        assertTrue(text.contains("ContextCompat.RECEIVER_NOT_EXPORTED"))
    }

    @Test
    fun debugScreenDiagnosticReceiverIsRuntimeDebugOnlyForAdb() {
        val text = File("src/main/java/com/ojoclaro/android/MainActivity.kt").readText()

        assertTrue(text.contains("DEBUG_RUN_SCREEN_DIAGNOSTIC_ACTION"))
        assertTrue(text.contains("if (!BuildConfig.DEBUG || debugScreenDiagnosticReceiver != null) return"))
        assertTrue(text.contains("debugScreenDiagnosticRequests.tryEmit(Unit)"))
        assertTrue(text.contains("ContextCompat.RECEIVER_EXPORTED"))
    }

    @Test
    fun accessibilityMetadataRequestsButtonAndShortcutSpokenFeedback() {
        val text = File("src/main/res/xml/ojo_claro_accessibility_service.xml").readText()

        assertTrue(text.contains("flagRequestAccessibilityButton"))
        assertTrue(text.contains("flagRequestShortcutWarningDialogSpokenFeedback"))
        assertFalse(text.contains("flagRequestFilterKeyEvents"))
    }

    @Test
    fun debugAskScreenReceiverIsRuntimeDebugOnlyForAdb() {
        val text = File("src/main/java/com/ojoclaro/android/MainActivity.kt").readText()

        assertTrue(text.contains("DEBUG_ASK_SCREEN_ACTION"))
        assertTrue(text.contains("if (!BuildConfig.DEBUG || debugScreenQuestionReceiver != null) return"))
        assertTrue(text.contains("debugScreenQuestionRequests.tryEmit(text)"))
        assertTrue(text.contains("ContextCompat.RECEIVER_EXPORTED"))
    }

    @Test
    fun mainManifestDoesNotAllowGlobalCleartext() {
        val text = manifestText

        assertFalse(text.contains("usesCleartextTraffic=\"true\""))
    }

    @Test
    fun releaseNetworkSecurityBlocksCleartextButDebugAllowsLocalProxy() {
        val mainConfig = File("src/main/res/xml/network_security_config.xml").readText()
        val debugConfig = File("src/debug/res/xml/network_security_config.xml").readText()
        val debugManifest = File("src/debug/AndroidManifest.xml").readText()

        assertTrue(mainConfig.contains("cleartextTrafficPermitted=\"false\""))
        assertTrue(debugManifest.contains("usesCleartextTraffic=\"true\""))
        assertTrue(debugConfig.contains("cleartextTrafficPermitted=\"true\""))
        assertTrue(debugConfig.contains("10.0.2.2"))
        assertTrue(debugConfig.contains("127.0.0.1"))
        assertTrue(debugConfig.contains("localhost"))
    }
}
