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
        assertTrue(text.contains("android:foregroundServiceType=\"microphone\""))
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
    }

    @Test
    fun debugSubmitTextReceiverIsRuntimeDebugOnlyAndSanitized() {
        val text = File("src/main/java/com/ojoclaro/android/MainActivity.kt").readText()

        assertTrue(text.contains("if (!BuildConfig.DEBUG || debugSubmitTextReceiver != null) return"))
        assertTrue(text.contains("debugSubmitTextDecision("))
        assertTrue(text.contains("DEBUG_SUBMIT_TEXT_MAX_CHARS"))
        assertTrue(text.contains("ContextCompat.RECEIVER_NOT_EXPORTED"))
        assertFalse(text.contains("ContextCompat.RECEIVER_EXPORTED"))
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
