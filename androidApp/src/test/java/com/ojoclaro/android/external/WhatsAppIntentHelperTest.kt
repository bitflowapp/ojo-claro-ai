package com.ojoclaro.android.external

import android.content.Intent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WhatsAppIntentHelperTest {

    @Test
    fun openChatSpecUsaActionView() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "+5491123456789",
            isWhatsAppInstalled = { true }
        )

        assertNotNull(spec)
        assertEquals(Intent.ACTION_VIEW, spec.action)
    }

    @Test
    fun openChatSpecUsaWaMeSinSigno() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "+5491123456789",
            isWhatsAppInstalled = { true }
        )

        assertNotNull(spec)
        assertEquals("https://wa.me/5491123456789", spec.dataUri)
        assertFalse(spec.dataUri.contains("+"))
    }

    @Test
    fun openChatSpecAplicaPackageWhatsAppCuandoEstaInstalado() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "5491123456789",
            isWhatsAppInstalled = { true }
        )

        assertEquals("com.whatsapp", spec?.packageName)
    }

    @Test
    fun openChatSpecPackageNullSiNoEstaInstalado() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "5491123456789",
            isWhatsAppInstalled = { false }
        )

        assertNotNull(spec)
        assertNull(spec.packageName)
    }

    @Test
    fun openChatSpecNoIncluyeTextNiSendNiMensaje() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "5491123456789",
            isWhatsAppInstalled = { true }
        )

        assertNotNull(spec)
        // El URI nunca debe contener "?text=" ni "send" — esta capa abre el chat vacío.
        assertFalse(spec.dataUri.contains("?text=", ignoreCase = true))
        assertFalse(spec.dataUri.contains("text=", ignoreCase = true))
        assertFalse(spec.dataUri.contains("send", ignoreCase = true))
    }

    @Test
    fun openChatSpecNoUsaActionSend() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "5491123456789",
            isWhatsAppInstalled = { true }
        )

        // No envía: solo abre el chat. ACTION_SEND es del flujo COMPOSE.
        assertNotNull(spec)
        assertEquals(Intent.ACTION_VIEW, spec.action)
        assertTrue(spec.action != Intent.ACTION_SEND)
    }

    @Test
    fun openChatSpecRechazaNumeroInvalidoCorto() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "+12",
            isWhatsAppInstalled = { true }
        )

        assertNull(spec)
    }

    @Test
    fun openChatSpecRechazaNumeroVacio() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "",
            isWhatsAppInstalled = { true }
        )

        assertNull(spec)
    }

    @Test
    fun openChatSpecToleraEspaciosYGuiones() {
        val spec = WhatsAppIntentHelper.buildOpenChatIntentSpec(
            phoneE164 = "+54 9 11 2345-6789",
            isWhatsAppInstalled = { true }
        )

        assertNotNull(spec)
        assertEquals("https://wa.me/5491123456789", spec.dataUri)
    }
}
