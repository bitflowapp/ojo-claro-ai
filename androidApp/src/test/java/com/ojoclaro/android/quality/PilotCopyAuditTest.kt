package com.ojoclaro.android.quality

import com.ojoclaro.android.agent.AgentIntent
import com.ojoclaro.android.agent.userActionLabel
import com.ojoclaro.android.agent.runtime.conversation.ConversationalRepair
import com.ojoclaro.android.agent.runtime.conversation.EstelaCompanionPhrases
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppMediaCallRefusalPhrases
import com.ojoclaro.android.help.VoiceHelpCenter
import com.ojoclaro.android.help.VoiceHelpContext
import com.ojoclaro.android.voice.SpeechErrorCategory
import com.ojoclaro.android.voice.humanLabel
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * FASE 5 (doc) — Auditoría READ-ONLY del COPY hablable para usuario.
 *
 * La persona no vidente jamás debe escuchar jerga técnica. La misión prohíbe
 * exponer: NO_MATCH, blocked, handler, intent, runtime, not_found, critical
 * guard, fallback, internal error. Este test recolecta el copy de usuario REAL
 * (ayuda, compañía, reparación, negativas, etiquetas humanizadas que agregué en
 * FASE 1) y asevera que está limpio y que el tono es tranquilizador.
 *
 * Si apareciera jerga, es un fix de COPY permitido (FASE 6), no de seguridad.
 */
class PilotCopyAuditTest {

    // Word-boundary para no chocar con español legítimo ("intentar" != "intent").
    private val forbidden = listOf(
        Regex("NO_MATCH"),
        Regex("not[_ ]?found", RegexOption.IGNORE_CASE),
        Regex("\\bblocked\\b", RegexOption.IGNORE_CASE),
        Regex("\\bhandler\\b", RegexOption.IGNORE_CASE),
        Regex("\\bintent\\b", RegexOption.IGNORE_CASE),
        Regex("\\bruntime\\b", RegexOption.IGNORE_CASE),
        Regex("\\bfallback\\b", RegexOption.IGNORE_CASE),
        Regex("critical guard", RegexOption.IGNORE_CASE),
        Regex("internal error", RegexOption.IGNORE_CASE),
        Regex("\\bnull\\b", RegexOption.IGNORE_CASE),
        Regex("\\bexception\\b", RegexOption.IGNORE_CASE),
        Regex("\\bno_?match\\b", RegexOption.IGNORE_CASE)
    )

    private fun userFacingCopy(): List<Pair<String, String>> {
        val out = mutableListOf<Pair<String, String>>()
        // Centro de ayuda de voz.
        out += "VoiceHelpCenter.SPOKEN_HELP" to VoiceHelpCenter.SPOKEN_HELP
        out += "VoiceHelpCenter.MEMORY_HELP" to VoiceHelpCenter.MEMORY_HELP
        out += "VoiceHelpCenter.SAFETY_HELP" to VoiceHelpCenter.SAFETY_HELP
        out += "VoiceHelpCenter.spokenHelp(true)" to VoiceHelpCenter.spokenHelp(true)
        out += "VoiceHelpCenter.spokenHelp(false)" to VoiceHelpCenter.spokenHelp(false)
        VoiceHelpContext.values().forEach { ctx ->
            out += "contextualSpokenHelp($ctx)" to VoiceHelpCenter.contextualSpokenHelp(ctx)
        }
        (VoiceHelpCenter.CORE_EXAMPLES + VoiceHelpCenter.MEMORY_EXAMPLES + VoiceHelpCenter.SAFETY_EXAMPLES)
            .forEachIndexed { i, e -> out += "VoiceHelpCenter.example[$i]" to e }
        // Compañía conversacional.
        out += "NOT_UNDERSTOOD_WARM" to EstelaCompanionPhrases.NOT_UNDERSTOOD_WARM
        out += "SILENT_CLOSE" to EstelaCompanionPhrases.SILENT_CLOSE
        listOf(
            "hola estela", "no se que hacer", "no entiendo", "ayuda", "gracias",
            "como estas", "que puedo hacer con whatsapp", "charlemos", "estoy nervioso",
            "explicame", "me siento perdido", "que haces"
        ).forEach { k -> EstelaCompanionPhrases.respond(k)?.let { out += "respond($k)" to it } }
        // Reparación conversacional (todo lo que se le habla al usuario).
        out += "Repair.NOT_HEARD" to ConversationalRepair.NOT_HEARD
        out += "Repair.NOISE" to ConversationalRepair.NOISE
        out += "Repair.SECOND_FAILURE" to ConversationalRepair.SECOND_FAILURE
        out += "Repair.THIRD_FAILURE" to ConversationalRepair.THIRD_FAILURE
        out += "Repair.WAITING_WHATSAPP" to ConversationalRepair.WAITING_WHATSAPP
        out += "Repair.SENSITIVE_SCREEN" to ConversationalRepair.SENSITIVE_SCREEN
        out += "Repair.SAFE_AI_UNAVAILABLE" to ConversationalRepair.SAFE_AI_UNAVAILABLE
        out += "Repair.CONFIRMATION_UNCLEAR" to ConversationalRepair.CONFIRMATION_UNCLEAR
        out += "Repair.CONFIRMATION_CANCELLED" to ConversationalRepair.CONFIRMATION_CANCELLED
        out += "Repair.ROBOT_OFF" to ConversationalRepair.ROBOT_OFF
        out += "Repair.NORMAL_SUGGESTIONS" to ConversationalRepair.NORMAL_SUGGESTIONS
        out += "Repair.WHATSAPP_OPEN_SUGGESTIONS" to ConversationalRepair.WHATSAPP_OPEN_SUGGESTIONS
        // Negativas de seguridad (calmas, por tipo).
        WhatsAppMediaCallRefusalPhrases.Kind.values().forEach { k ->
            out += "refusal($k)" to WhatsAppMediaCallRefusalPhrases.refusal(k)
        }
        // Etiquetas humanizadas que agregué en FASE 1.
        SpeechErrorCategory.values().forEach { c -> out += "SpeechError.$c" to c.humanLabel() }
        AgentIntent.values().forEach { i -> out += "Intent.$i" to i.userActionLabel() }
        return out
    }

    @Test
    fun userFacingCopyHasNoTechnicalJargon() {
        userFacingCopy().forEach { (label, text) ->
            forbidden.forEach { rx ->
                assertFalse(
                    rx.containsMatchIn(text),
                    "$label expone jerga /${rx.pattern}/: \"$text\""
                )
            }
        }
    }

    @Test
    fun userFacingCopyIsNeverBlank() {
        userFacingCopy().forEach { (label, text) ->
            assertTrue(text.isNotBlank(), "$label está vacío")
        }
    }

    @Test
    fun safetyRefusalsAreCalmAndExplainTheLimit() {
        WhatsAppMediaCallRefusalPhrases.Kind.values().forEach { k ->
            val r = WhatsAppMediaCallRefusalPhrases.refusal(k)
            assertTrue(r.contains("seguridad", ignoreCase = true), "negativa debe enmarcar en seguridad: \"$r\"")
            assertTrue(r.contains("no puedo", ignoreCase = true), "negativa debe ser explícita y calma: \"$r\"")
        }
    }

    @Test
    fun cancellationCopyIsReassuring() {
        val cancelled = ConversationalRepair.CONFIRMATION_CANCELLED.lowercase()
        assertTrue(
            cancelled.contains("no hago nada") || cancelled.contains("listo"),
            "el copy de cancelación debe tranquilizar: \"${ConversationalRepair.CONFIRMATION_CANCELLED}\""
        )
    }

    @Test
    fun helpStatesTheWhatsAppSafetyLimit() {
        val help = VoiceHelpCenter.SPOKEN_HELP.lowercase()
        assertTrue(
            help.contains("no envio") || help.contains("no envío") || help.contains("confirma"),
            "la ayuda debe aclarar el límite de WhatsApp (prepara, no envía): \"${VoiceHelpCenter.SPOKEN_HELP}\""
        )
    }
}
