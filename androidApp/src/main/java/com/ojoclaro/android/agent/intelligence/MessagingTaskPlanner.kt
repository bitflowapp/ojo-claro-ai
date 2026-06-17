package com.ojoclaro.android.agent.intelligence

import java.text.Normalizer

/**
 * V2.2 — Planificador SEGURO de mensajería. Dado un objetivo ("mandale a Marco
 * Luna por WhatsApp que X"), el modelo de pantalla y la resolución de contacto,
 * arma un plan con frenos: abrir app, abrir chat, VERIFICAR que el chat es el
 * destino, preparar texto, y pedir confirmación FUERTE. Nunca envía solo.
 *
 * Componente PURO. Encarna las reglas duras de la misión:
 *  - "sí"/"dale"/"ok" NUNCA envían.
 *  - solo "mandalo"/"enviar mensaje"/"confirmo enviar"/"mandalo prueba autorizada".
 *  - nunca enviar si el chat actual no coincide con el destino.
 *  - pagos/tarjetas/audio/llamada a la vista → no enviar.
 *  - ante la duda → Ask (pregunta), nunca acción.
 *
 * NOTA V2.2: código real SIN COMPILAR/SIN TESTEAR (disco C: crítico; ver
 * docs/V22_WHATSAPP_INSTAGRAM_REAL_SMOKE.md).
 */
data class MessagingGoal(
    val app: TargetApp,
    val contactQuery: String,
    val messageText: String?,
)

enum class PlanStep {
    OPEN_APP, RESOLVE_CONTACT, OPEN_CHAT, VERIFY_CHAT_MATCHES, PREPARE_TEXT, ASK_STRONG_CONFIRMATION
}

sealed class MessagingPlan {
    data class Steps(
        val resolved: ResolvedContact,
        val targetApp: TargetApp,
        val steps: List<PlanStep>,
        val spoken: String,
    ) : MessagingPlan()

    data class Ask(val question: String) : MessagingPlan()
    data class Refuse(val reason: String) : MessagingPlan()
}

enum class ConfirmationVerdict { STRONG_SEND, WEAK_REJECTED, CANCEL, OTHER }

object MessagingTaskPlanner {

    private val STRONG_CONFIRM = setOf(
        "mandalo", "mandalo ya", "envialo", "enviar mensaje", "envia el mensaje",
        "confirmo enviar", "confirmo envio", "confirmo", "mandalo prueba autorizada"
    )
    private val WEAK_AFFIRM = setOf("si", "dale", "ok", "okey", "oka", "sip", "obvio", "claro")
    private val CANCEL = setOf(
        "cancelar", "cancela", "no", "no mandes nada", "cancela eso", "borra eso",
        "cerra todo", "no envies", "no lo mandes", "mejor no"
    )
    private val SENSITIVE_MSG = setOf(
        "clave", "contrasena", "password", "cvv", "cbu", "pin", "tarjeta", "token", "codigo de seguridad"
    )

    fun plan(goal: MessagingGoal, screen: ScreenModel, resolution: ContactResolution): MessagingPlan {
        // 1) Bloqueos duros primero.
        if (goal.messageText != null && SENSITIVE_MSG.any { normalize(goal.messageText).contains(it) }) {
            return MessagingPlan.Refuse("El mensaje parece tener un dato sensible. Escribilo y enviálo vos a mano.")
        }

        val targetApp = if (goal.app != TargetApp.UNKNOWN) goal.app else when (screen.activeApp) {
            ActiveApp.WHATSAPP, ActiveApp.WHATSAPP_BUSINESS -> TargetApp.WHATSAPP
            ActiveApp.INSTAGRAM -> TargetApp.INSTAGRAM
            else -> TargetApp.UNKNOWN
        }
        if (targetApp == TargetApp.UNKNOWN) {
            return MessagingPlan.Ask("¿Por dónde se lo mando, WhatsApp o Instagram?")
        }

        // 2) Resolución de contacto.
        when (resolution) {
            is ContactResolution.Unsafe -> return MessagingPlan.Refuse(resolution.reason)
            is ContactResolution.Ambiguous -> {
                val names = resolution.candidates.take(3).joinToString(", ") { it.primaryText }
                return MessagingPlan.Ask("Hay varios que coinciden: $names. ¿A cuál?")
            }
            is ContactResolution.NotFound -> return MessagingPlan.Ask(
                "No encontré a \"${goal.contactQuery}\". Abrí el chat vos y decime \"leé los mensajes\", o probá con el nombre completo."
            )
            is ContactResolution.Resolved -> {
                val r = resolution.match
                val steps = mutableListOf<PlanStep>()
                val onTargetApp = when (targetApp) {
                    TargetApp.WHATSAPP -> screen.activeApp == ActiveApp.WHATSAPP || screen.activeApp == ActiveApp.WHATSAPP_BUSINESS
                    TargetApp.INSTAGRAM -> screen.activeApp == ActiveApp.INSTAGRAM
                    TargetApp.UNKNOWN -> false
                }
                if (!onTargetApp) steps.add(PlanStep.OPEN_APP)
                steps.add(PlanStep.RESOLVE_CONTACT)
                val alreadyInChat = screen.screenType == ScreenType.CONVERSATION &&
                    screen.currentChatTitle != null && sameName(screen.currentChatTitle, r.name)
                if (!alreadyInChat) steps.add(PlanStep.OPEN_CHAT)
                steps.add(PlanStep.VERIFY_CHAT_MATCHES)
                steps.add(PlanStep.PREPARE_TEXT)
                steps.add(PlanStep.ASK_STRONG_CONFIRMATION)
                val via = when (targetApp) {
                    TargetApp.WHATSAPP -> " por WhatsApp"
                    TargetApp.INSTAGRAM -> " por Instagram"
                    TargetApp.UNKNOWN -> ""
                }
                val spoken = "Tengo preparado un mensaje para ${r.name}$via. " +
                    "Para enviarlo decí: mandalo. Si no, decí cancelar."
                return MessagingPlan.Steps(r, targetApp, steps, spoken)
            }
        }
    }

    /** "sí"/"dale"/"ok" → WEAK_REJECTED; solo frases fuertes → STRONG_SEND. */
    fun classifyConfirmation(text: String): ConfirmationVerdict {
        val t = normalize(text)
        return when {
            t in CANCEL -> ConfirmationVerdict.CANCEL
            t in STRONG_CONFIRM -> ConfirmationVerdict.STRONG_SEND
            t in WEAK_AFFIRM -> ConfirmationVerdict.WEAK_REJECTED
            else -> ConfirmationVerdict.OTHER
        }
    }

    /**
     * Compuerta FINAL de envío. true SOLO si: confirmación fuerte + chat
     * verificado + texto coincide con el campo + estamos en una conversación
     * cuyo título coincide con el destino + no hay pago/tarjeta a la vista.
     */
    fun canSend(
        confirmation: String,
        resolved: ResolvedContact,
        screen: ScreenModel,
        chatVerified: Boolean,
        preparedTextMatchesField: Boolean,
    ): Boolean {
        if (classifyConfirmation(confirmation) != ConfirmationVerdict.STRONG_SEND) return false
        if (!chatVerified || !preparedTextMatchesField) return false
        if (screen.screenType != ScreenType.CONVERSATION) return false
        if (screen.currentChatTitle != null && !sameName(screen.currentChatTitle, resolved.name)) return false
        if (RiskyControl.PAYMENT in screen.riskyControls || RiskyControl.CARD in screen.riskyControls) return false
        return true
    }

    private fun sameName(a: String, b: String): Boolean {
        val na = normalize(a); val nb = normalize(b)
        if (na == nb) return true
        val ta = na.split(" ").filter { it.isNotBlank() }
        val tb = nb.split(" ").filter { it.isNotBlank() }
        return ta.isNotEmpty() && tb.isNotEmpty() && ta[0] == tb[0]
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
        return stripped.replace(Regex("[¿?¡!.,;:]"), " ").replace(Regex("\\s+"), " ").trim()
    }
}
