package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManifestSafetyTest {

    @Test
    fun manifestDoesNotDeclareForbiddenPermissionsOrCallAction() {
        val manifestFile = locateManifest()
        assertTrue(manifestFile.exists(), "AndroidManifest.xml not found")

        val text = manifestFile.readText()
        assertFalse(text.contains("android.permission.READ_CONTACTS"))
        assertFalse(text.contains("android.permission.CALL_PHONE"))
        assertFalse(text.contains("android.permission.ACCESS_BACKGROUND_LOCATION"))
        assertFalse(text.contains("android.intent.action.CALL"))
    }

    private fun locateManifest(): File {
        var current = File(System.getProperty("user.dir"))
        while (true) {
            val direct = File(current, "src/main/AndroidManifest.xml")
            if (direct.exists()) return direct

            val module = File(current, "androidApp/src/main/AndroidManifest.xml")
            if (module.exists()) return module

            val parent = current.parentFile ?: break
            current = parent
        }
        return File("androidApp/src/main/AndroidManifest.xml")
    }
}
