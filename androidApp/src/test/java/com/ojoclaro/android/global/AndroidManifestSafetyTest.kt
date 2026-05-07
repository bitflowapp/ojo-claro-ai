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
}
