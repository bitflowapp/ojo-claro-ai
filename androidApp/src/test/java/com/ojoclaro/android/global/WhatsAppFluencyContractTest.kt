package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Fluency (#7.9, #7.10) — contrato por inspección de fuente:
 *  - durante el TTS el micrófono se PAUSA (no se procesa basura ni se envía);
 *  - el handler de fluidez corre antes de los pendientes y es read-only;
 *  - el micrófono se RECUPERA (no queda mudo): retry en errores recuperables;
 *  - logs nuevos sanitizados.
 */
class WhatsAppFluencyContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    private val voiceController: String =
        File("src/main/java/com/ojoclaro/android/voice/VoiceCommandController.kt").readText()

    @Test
    fun ttsPausesMicrophoneSoNothingIsProcessedWhileSpeaking() {
        assertTrue(service.contains("STT_PAUSED_FOR_TTS"), "debe loguear la pausa del mic en TTS")
        // En el callback de inicio de TTS, el mic se pausa.
        val onStart = service.substringAfter("onSpeechStarted").substringBefore("onSpeechFinished")
        assertTrue(onStart.contains("pauseForSpeech"), "onSpeechStarted debe pausar el micrófono")
        // Y se reanuda tras hablar (mic no queda muerto).
        assertTrue(service.contains("STT_RESUMED_AFTER_TTS"), "debe reanudar el mic tras el TTS")
        assertTrue(service.contains("resumeAfterSpeech"), "debe reanudar la escucha")
    }

    @Test
    fun fluencyHandlerRunsBeforePendingAndIsReadOnly() {
        val idxFluency = service.indexOf("handleVoiceFluencyCommand(text)")
        // lastIndexOf = la llamada PRINCIPAL (hay una previa del micro-fix de
        // cancelación prioritaria WA-5, ruta especial solo para cancelaciones).
        val idxPending = service.lastIndexOf("handlePendingWhatsAppReplyConfirmation(text)")
        assertTrue(idxFluency in 1 until idxPending, "fluencia debe correr antes de la confirmación pendiente")

        val body = service
            .substringAfter("private fun handleVoiceFluencyCommand(text: String): Boolean {")
            .substringBefore("private fun handleWhatsAppAnxietyCommand")
        assertFalse(body.contains("tapWhatsAppSend"), "fluencia jamás debe tocar enviar")
        assertFalse(body.contains("pendingWhatsAppReply = null"), "fluencia no debe limpiar el pendiente")
        assertFalse(body.contains("pendingWhatsAppSendDraft = null"), "fluencia no debe limpiar el borrador")
        assertFalse(body.contains("setWhatsAppDraft"), "fluencia no debe escribir borradores")
    }

    @Test
    fun microphoneRecoversOnRecoverableErrors() {
        // El controller reintenta ante errores recuperables: el mic no queda mudo.
        assertTrue(voiceController.contains("scheduleRetry"), "debe reprogramar la escucha")
        assertTrue(voiceController.contains("WAITING_RETRY"), "debe tener estado de reintento")
        assertTrue(voiceController.contains("resumeAfterSpeech"), "debe poder reanudar tras hablar")
    }

    @Test
    fun newFluencyLogsArePresentAndSanitized() {
        listOf(
            "VOICE_HOLD_ACKNOWLEDGED",
            "VOICE_REENGAGE",
            "WHATSAPP_REPLY_INCOMPLETE",
            "ROUTING_AUDIT handler=voice_fluency"
        ).forEach { assertTrue(service.contains(it), "falta log: $it") }
        // El log de respuesta incompleta no interpola contenido (solo un reason).
        val incompleteLog = service.substringAfter("WHATSAPP_REPLY_INCOMPLETE").take(40)
        assertFalse(incompleteLog.contains("\$message"), "no debe loguear el texto del mensaje")
    }
}
