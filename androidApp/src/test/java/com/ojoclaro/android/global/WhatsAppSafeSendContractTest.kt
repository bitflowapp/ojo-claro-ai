package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * V1.2 — contrato de seguridad del envío de WhatsApp por voz.
 *
 * Garantías (por inspección de fuente):
 *  1. El toque de enviar SOLO existe dentro de la rama de confirmación
 *     explícita del envío pendiente.
 *  2. Antes de tocar enviar se re-verifica que el campo diga EXACTAMENTE lo
 *     que la persona escuchó (FieldMismatch si cambió).
 *  3. El borrador pendiente muere al cerrar el turno o al pedir silencio.
 *  4. El contenido del borrador jamás se loguea: solo longitudes.
 */
class WhatsAppSafeSendContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val accessibility: String =
        File("src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt")
            .readText()

    @Test
    fun sendTapOnlyHappensInsideConfirmedPendingReply() {
        val tapCalls = Regex("tapWhatsAppSend\\(").findAll(service).count()
        assertTrue(tapCalls == 1, "tapWhatsAppSend debe invocarse en UN solo lugar (hay $tapCalls)")
        val confirmBranch = service.substringAfter("WhatsAppVoiceSendPhrases.isConfirmSend(text) ->")
            .substringBefore("VoicePhraseNormalizer.isNeverConfirm(text)")
        assertTrue(
            confirmBranch.contains("tapWhatsAppSend"),
            "el toque de enviar debe vivir solo dentro de la rama de confirmación"
        )
    }

    @Test
    fun fieldMustMatchSpokenDraftBeforeTapping() {
        val sendInternal = accessibility.substringAfter("fun tapWhatsAppSendInternal")
            .substringBefore("fun playVisibleWhatsAppAudioInternal")
        val mismatchIdx = sendInternal.indexOf("FieldMismatch")
        val buttonIdx = sendInternal.indexOf("findWhatsAppSendButton")
        assertTrue(
            mismatchIdx in 1 until buttonIdx,
            "la verificación del campo debe ocurrir ANTES de buscar el botón de enviar"
        )
        assertTrue(
            accessibility.contains("sameDraftText(fieldText, expectedMessage)"),
            "el campo debe compararse contra el texto confirmado"
        )
        assertTrue(
            sendInternal.contains("packageNameLooksLikeWhatsApp"),
            "el envío solo puede ocurrir con WhatsApp en primer plano"
        )
    }

    @Test
    fun weakAffirmativeGuardExistsInPendingReply() {
        assertTrue(
            service.contains("VoicePhraseNormalizer.isNeverConfirm(text) ->"),
            "\"sí\"/\"dale\" a secas deben rechazarse explícitamente en el envío pendiente"
        )
    }

    @Test
    fun pendingDraftDiesWithTurnOrSilence() {
        val clears = Regex("pendingWhatsAppSendDraft = null").findAll(service).count()
        assertTrue(
            clears >= 4,
            "el borrador pendiente debe limpiarse al cancelar, confirmar, " +
                "silenciar y cerrar turno (hay $clears limpiezas)"
        )
    }

    @Test
    fun draftContentNeverLogged() {
        // Los logs del flujo solo llevan longitudes/outcomes, nunca el texto.
        listOf(service, accessibility).forEach { source ->
            Regex("log\\w*\\([^)]*draft\\.text[^)]*\\)", RegexOption.IGNORE_CASE)
                .findAll(source)
                .forEach { match ->
                    assertTrue(
                        match.value.contains("draft.text.length"),
                        "log con contenido de borrador detectado: ${match.value}"
                    )
                }
        }
    }

    @Test
    fun sensitiveContentBlockHappensBeforePending() {
        val draftBranch = service.substringAfter("is WhatsAppDraftReadResult.Draft ->")
            .substringBefore("WhatsAppDraftReadResult.EmptyDraft")
        val sensibleIdx = draftBranch.indexOf("looksSensitive")
        val pendingIdx = draftBranch.indexOf("pendingWhatsAppSendDraft = draft.text")
        assertTrue(
            sensibleIdx in 0 until pendingIdx,
            "el chequeo de contenido sensible debe ocurrir antes de armar el pending"
        )
    }
}
