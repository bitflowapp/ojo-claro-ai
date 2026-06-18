package com.ojoclaro.android.global

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * WhatsApp Anxiety Hardening — contrato de seguridad/privacidad por inspección
 * de fuente (I.10 del sprint). Garantiza que:
 *  1. el handler de ansiedad corre ANTES de los pendientes (orientación
 *     "desde cualquier estado");
 *  2. todos los logs nuevos del sprint existen y son sanitizados (solo flags
 *     y conteos, jamás contenido);
 *  3. el handler de ansiedad es read-only: nunca envía, nunca limpia un
 *     pendiente, nunca escribe un borrador.
 */
class WhatsAppAnxietyHardeningContractTest {

    private val service: String =
        File("src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt").readText()

    @Test
    fun anxietyHandlerRunsBeforePendingConfirmation() {
        val idxAnxiety = service.indexOf("handleWhatsAppAnxietyCommand(text)")
        // lastIndexOf = la llamada PRINCIPAL de confirmación (hay una previa del
        // micro-fix de cancelación prioritaria, que es ruta especial de cancel).
        val idxPending = service.lastIndexOf("handlePendingWhatsAppReplyConfirmation(text)")
        assertTrue(idxAnxiety > 0, "no se encontró la llamada al handler de ansiedad")
        assertTrue(
            idxAnxiety < idxPending,
            "el handler de ansiedad debe rutearse antes que la confirmación pendiente"
        )
    }

    @Test
    fun allNewSanitizedLogsArePresent() {
        listOf(
            "WHATSAPP_ANXIETY_MODE_ENABLED",
            "WHATSAPP_SAFE_MODE_ENABLED",
            "WHATSAPP_EMERGENCY_CANCEL",
            "WHATSAPP_STATE_QUERY_REQUESTED",
            "WHATSAPP_STATE_QUERY_RESULT",
            "WHATSAPP_RECOVERY_MESSAGE_SPOKEN",
            "WHATSAPP_REPEAT_LAST_RESPONSE",
            "WHATSAPP_HELP_CONTEXTUAL",
            "WHATSAPP_AMBIGUOUS_CONFIRMATION_BLOCKED",
            "WHATSAPP_NOT_SENT_REASSURANCE",
            "ROUTING_AUDIT handler=whatsapp_anxiety_hardening"
        ).forEach { assertTrue(service.contains(it), "falta el log del sprint: $it") }
    }

    @Test
    fun stateQueryResultLogInterpolatesOnlyFlagsAndCounts() {
        // Ventana del log de resultado de estado: cada interpolación debe ser un
        // flag booleano o un conteo del snapshot, nunca texto/contenido.
        val window = service.substringAfter("WHATSAPP_STATE_QUERY_RESULT").take(400)
        val allowed = Regex(
            "\\$\\{s\\.(isOpen|isInChat|isUnknown|hasPendingReply|pendingStep|" +
                "hasPendingSendDraft|recentNotificationCount|safeMode)\\}"
        )
        val interpolations = Regex("\\$\\{[^}]*\\}").findAll(window).map { it.value }.toList()
        assertTrue(interpolations.isNotEmpty(), "el log de estado debería interpolar flags")
        interpolations.forEach { interp ->
            assertTrue(allowed.matches(interp), "el log de estado interpola un valor no permitido: $interp")
        }
    }

    @Test
    fun anxietyHandlerIsReadOnly() {
        val body = service
            .substringAfter("private fun handleWhatsAppAnxietyCommand(text: String): Boolean {")
            .substringBefore("private fun buildAnxietySnapshot")
        assertFalse(body.contains("tapWhatsAppSend"), "el handler de ansiedad jamás debe tocar enviar")
        assertFalse(body.contains("pendingWhatsAppReply = null"), "no debe limpiar el pendiente de respuesta")
        assertFalse(body.contains("pendingWhatsAppSendDraft = null"), "no debe limpiar el borrador pendiente")
        assertFalse(body.contains("setWhatsAppDraft"), "no debe escribir borradores")
    }

    @Test
    fun safeModeFlagLivesInProcessMemory() {
        assertTrue(
            service.contains("var whatsAppSafeMode: Boolean = false"),
            "el modo seguro debe ser una bandera de proceso con default seguro (false)"
        )
    }

    @Test
    fun whatsAppCancelHasPriorityOverGlobalStop() {
        // Microfix WA-5: con un envío pendiente, una cancelación debe rutear al
        // cancel WA-5 (limpia borrador + "Cancelado. No envié nada.") ANTES del
        // STOP global (que solo silencia y no limpia el campo).
        val guardIdx = service.indexOf("pendingWhatsAppReply != null && WhatsAppReplyPhrases.isCancel(text)")
        val stopIdx = service.indexOf("isStopModeCommand(text) ->")
        assertTrue(guardIdx > 0, "debe existir el guard de cancelación prioritaria")
        assertTrue(guardIdx < stopIdx, "el cancel WA-5 debe correr ANTES del STOP global")
    }
}
