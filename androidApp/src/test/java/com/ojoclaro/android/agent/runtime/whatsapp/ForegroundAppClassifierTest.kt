package com.ojoclaro.android.agent.runtime.whatsapp

import kotlin.test.Test
import kotlin.test.assertEquals

/** #3 — clasificación de foreground para el rastro de contexto WhatsApp. */
class ForegroundAppClassifierTest {

    private val own = "com.ojoclaro.android"
    private fun c(pkg: String?) = ForegroundAppClassifier.classify(pkg, own)

    @Test
    fun whatsAppPackagesAreWhatsApp() {
        assertEquals(ForegroundAppClassifier.Kind.WHATSAPP, c("com.whatsapp"))
        assertEquals(ForegroundAppClassifier.Kind.WHATSAPP, c("com.whatsapp.w4b"))
        assertEquals(ForegroundAppClassifier.Kind.WHATSAPP, c("COM.WHATSAPP"))
    }

    @Test
    fun ownImeAndSystemUiAreIgnored() {
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c(own))
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c("com.ojoclaro.android.debug"))
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c("com.google.android.inputmethod.latin"))
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c("com.android.systemui"))
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c("android"))
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c(null))
        assertEquals(ForegroundAppClassifier.Kind.IGNORE, c(""))
    }

    @Test
    fun otherRealAppsIncludingLauncherResetTheTrail() {
        // un app real (o ir a Home) significa que ya NO estamos en WhatsApp
        assertEquals(ForegroundAppClassifier.Kind.OTHER_APP, c("com.instagram.android"))
        assertEquals(ForegroundAppClassifier.Kind.OTHER_APP, c("com.motorola.launcher3"))
        assertEquals(ForegroundAppClassifier.Kind.OTHER_APP, c("com.ubercab"))
    }
}
