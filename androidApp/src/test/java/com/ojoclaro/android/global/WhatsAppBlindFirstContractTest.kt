package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Contrato (por inspección de fuente) de las rutas WhatsApp Blind-First.
 *
 * Garantías:
 *  1. La ruta ciega corre ANTES de abrir-genérico, reply, compose y del LLM
 *     (rutas críticas antes del fallback).
 *  2. Las rutas ciegas NUNCA tocan enviar ni arman un envío pendiente.
 *  3. La apertura usa deep link wa.me SIN texto (no escribe nada).
 *  4. Se usa el destino redactado para los logs (sin PII).
 */
class WhatsAppBlindFirstContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val blindSection: String =
        service
            .substringAfter("private fun handleWhatsAppBlindFirstCommand")
            .substringBefore("Sprint WhatsApp: \"abrí el primer")

    @Test
    fun blindRouteRunsBeforeGenericOpenReplyComposeAndFallback() {
        val idxBlind = service.indexOf("if (handleWhatsAppBlindFirstCommand(text)) return")
        val idxGenericOpen = service.indexOf("if (handleWhatsAppFirstControlCommand(text)) return")
        val idxReply = service.indexOf("if (handleWhatsAppReplyCommand(text)) return")
        val idxCompose = service.indexOf("if (handleSmartCompose(text)) return")
        val idxFallback = service.indexOf("orchestrator.process(")

        assertTrue(idxBlind > 0, "la ruta ciega debe estar cableada en el dispatch")
        assertTrue(idxBlind < idxGenericOpen, "ciega antes de abrir-genérico")
        assertTrue(idxBlind < idxReply, "ciega antes del reply WA-5")
        assertTrue(idxBlind < idxCompose, "ciega antes del smart compose")
        assertTrue(idxBlind < idxFallback, "ciega antes del orquestador/LLM")
    }

    @Test
    fun blindRouteNeverTapsSendOrArmsPendingSend() {
        assertFalse(
            blindSection.contains("tapWhatsAppSend"),
            "la ruta ciega jamás toca enviar"
        )
        assertFalse(
            blindSection.contains("pendingWhatsAppSendDraft ="),
            "la ruta ciega jamás arma un envío pendiente"
        )
    }

    @Test
    fun blindOpenUsesDeepLinkWithoutPrefilledText() {
        assertTrue(
            blindSection.contains("whatsAppIntentHelper.openChat("),
            "abre por deep link wa.me reutilizando openChat (sin texto)"
        )
        assertFalse(
            blindSection.contains("?text="),
            "la apertura ciega no precarga texto en el chat"
        )
    }

    @Test
    fun blindRouteReadsNotificationStoreAndRedactsLogs() {
        assertTrue(
            blindSection.contains("WhatsAppNotificationStore.recent()"),
            "reply-a-notificación lee el store WA-3 (no la red ni el LLM)"
        )
        assertTrue(
            blindSection.contains("redactedForLog()"),
            "los logs del destino/notificación van redactados"
        )
    }

    @Test
    fun tapWhatsAppSendStillHasSingleCallSite() {
        // No introdujimos nuevos call-sites de envío: sigue habiendo exactamente uno.
        val tapCalls = Regex("tapWhatsAppSend\\(").findAll(service).count()
        assertTrue(tapCalls == 1, "tapWhatsAppSend debe seguir en UN solo lugar (hay $tapCalls)")
    }
}
