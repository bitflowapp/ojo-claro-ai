package com.ojoclaro.android.global

import com.ojoclaro.android.agent.runtime.instagram.InstagramFeatureFlags
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Fix pre-merge PR #3 — las acciones REALES de Instagram (envío de texto y
 * videollamada) deben estar DESACTIVADAS por defecto, igual que WhatsApp.
 *
 * Mezcla defaults del value object + inspección de fuente: el gate por flag
 * precede al toque real y hay un único call-site por toque. Sin device, "no
 * llama tapInstagram* cuando el gate está en false" se prueba demostrando que
 * el gate `!instagramFlags.*Enabled` está cableado ANTES del tap.
 */
class InstagramRealActionGateContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    @Test
    fun defaultInstagramFlagsDisableSendAndVideoCall() {
        val off = InstagramFeatureFlags.DISABLED
        assertFalse(off.realSendEnabled, "Instagram real send debe ser false por defecto")
        assertFalse(off.videoCallEnabled, "Instagram videollamada debe ser false por defecto")
        // construcción por defecto = todo lo peligroso desactivado
        assertFalse(InstagramFeatureFlags().realSendEnabled)
        assertFalse(InstagramFeatureFlags().videoCallEnabled)
    }

    @Test
    fun serviceUsesDisabledInstagramFlagsByDefault() {
        assertTrue(
            service.contains("InstagramFeatureFlags.DISABLED"),
            "GAS debe inicializar los flags de Instagram en DISABLED"
        )
    }

    @Test
    fun instagramTextSendIsFlagGatedBeforeTapping() {
        val section = service
            .substringAfter("private fun handlePendingInstagramSendReply")
            .substringBefore("private suspend fun handleInstagramVideoCallRequest")
        val gateIdx = section.indexOf("if (!instagramFlags.realSendEnabled)")
        val tapIdx = section.indexOf("tapInstagramSend(")
        assertTrue(gateIdx >= 0, "el envío real de Instagram debe estar gateado por flag")
        assertTrue(gateIdx < tapIdx, "el gate del flag debe preceder a tapInstagramSend")
        assertEquals(
            1, Regex("tapInstagramSend\\(").findAll(service).count(),
            "tapInstagramSend debe tener UN solo call-site"
        )
    }

    @Test
    fun instagramVideoCallIsFlagGatedAtRequestAndBeforeTapping() {
        // 1) bloqueo desde el pedido: el gate precede a armar el pending sensible.
        val request = service
            .substringAfter("private suspend fun handleInstagramVideoCallRequest")
            .substringBefore("private fun handlePendingInstagramVideoCallReply")
        val reqGateIdx = request.indexOf("if (!instagramFlags.videoCallEnabled)")
        val armIdx = request.indexOf("pendingInstagramVideoCallArmedAt = SystemClock.elapsedRealtime()")
        assertTrue(reqGateIdx >= 0, "la videollamada debe bloquearse desde el pedido")
        assertTrue(reqGateIdx < armIdx, "el gate debe preceder a armar el pending de videollamada")

        // 2) defensa en profundidad: el gate precede al toque en la confirmación.
        val reply = service
            .substringAfter("private fun handlePendingInstagramVideoCallReply")
            .substringBefore("private fun speakInstagramAudioGuide")
        val gateIdx = reply.indexOf("if (!instagramFlags.videoCallEnabled)")
        val tapIdx = reply.indexOf("tapInstagramVideoCall(")
        assertTrue(gateIdx >= 0, "la confirmación de videollamada debe estar gateada por flag")
        assertTrue(gateIdx < tapIdx, "el gate del flag debe preceder a tapInstagramVideoCall")
        assertEquals(
            1, Regex("tapInstagramVideoCall\\(").findAll(service).count(),
            "tapInstagramVideoCall debe tener UN solo call-site"
        )
    }
}
