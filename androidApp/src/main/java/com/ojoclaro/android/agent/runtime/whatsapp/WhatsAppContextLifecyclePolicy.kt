package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Política PURA del lifecycle del contexto operativo de WhatsApp (M3).
 *
 * El contexto operativo ([WhatsAppConversationContext]: con quién era el último
 * chat, qué acción quedaba, largo del borrador) sirve para la continuidad dentro
 * de UNA conversación sana, pero NO debe sobrevivir a una cancelación, a volver al
 * Home, a que WhatsApp pierda el foreground, a un cambio de chat ni al teardown.
 * Tampoco un callback tardío puede restaurarlo, ni una pregunta conceptual crearlo.
 *
 * Esta política es el núcleo VERIFICABLE en JVM: recibe un evento y devuelve una
 * decisión. El Service (GlobalAssistantService) es solo el adaptador Android que
 * traduce sus eventos reales a [WhatsAppContextEvent] y aplica la decisión vía
 * [WhatsAppContextLifecycle]. Sin Android, sin estado, sin IO.
 */
enum class WhatsAppContextDecision {
    /** Continuidad sana: mismo chat/foreground, o pregunta conceptual. No tocar. */
    PRESERVE,

    /** Limpiar destinatario/acción/borrador operativos; conservar memoria general. */
    CLEAR_OPERATIONAL_CONTEXT,

    /** Teardown/olvido explícito: limpiar todo el contexto operativo. */
    CLEAR_ALL,

    /** Evento tardío de una generación vieja: descartar, jamás restaurar. */
    IGNORE_STALE_EVENT
}

sealed interface WhatsAppContextEvent {
    /** A — mismo chat, mismo foreground, turno conversacional: preservar. */
    object SameChatTurn : WhatsAppContextEvent

    /** B — cancelación explícita (cancelá/no mandes nada/abortá/frená todo/...). */
    object ExplicitCancel : WhatsAppContextEvent

    /** C — Home / otra app / WhatsApp deja de estar en foreground. */
    object LeftWhatsAppForeground : WhatsAppContextEvent

    /** D — cambió el chat visible: invalidar el destinatario anterior. */
    object ChatChanged : WhatsAppContextEvent

    /** Teardown/destroy del servicio. */
    object ServiceTeardown : WhatsAppContextEvent

    /** "olvidá el contexto" explícito. */
    object ExplicitForget : WhatsAppContextEvent

    /** F — pregunta conceptual: no necesita contexto operativo y no debe crearlo. */
    object ConceptualQuestion : WhatsAppContextEvent

    /** E — callback tardío: obsoleto si su generación no es la actual. */
    data class LateCallback(val callbackEpoch: Long, val currentEpoch: Long) : WhatsAppContextEvent

    /** C-retorno — volvió WhatsApp al foreground: sin snapshot nuevo, no reusar. */
    data class ReturnedToWhatsAppForeground(val hasFreshSnapshot: Boolean) : WhatsAppContextEvent
}

object WhatsAppContextLifecyclePolicy {
    fun decide(event: WhatsAppContextEvent): WhatsAppContextDecision = when (event) {
        WhatsAppContextEvent.SameChatTurn,
        WhatsAppContextEvent.ConceptualQuestion -> WhatsAppContextDecision.PRESERVE

        WhatsAppContextEvent.ExplicitCancel,
        WhatsAppContextEvent.LeftWhatsAppForeground,
        WhatsAppContextEvent.ChatChanged -> WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT

        WhatsAppContextEvent.ServiceTeardown,
        WhatsAppContextEvent.ExplicitForget -> WhatsAppContextDecision.CLEAR_ALL

        is WhatsAppContextEvent.LateCallback ->
            if (event.callbackEpoch != event.currentEpoch) {
                WhatsAppContextDecision.IGNORE_STALE_EVENT
            } else {
                WhatsAppContextDecision.PRESERVE
            }

        is WhatsAppContextEvent.ReturnedToWhatsAppForeground ->
            if (event.hasFreshSnapshot) {
                WhatsAppContextDecision.PRESERVE
            } else {
                WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT
            }
    }
}

/**
 * Adaptador delgado: decide con [WhatsAppContextLifecyclePolicy] y aplica al
 * [WhatsAppConversationContext] real. CLEAR_* limpian el contexto operativo de
 * WhatsApp; PRESERVE e IGNORE_STALE_EVENT no lo tocan (la memoria conversacional
 * general vive aparte y NO se borra acá). Devuelve la decisión para poder loguear/
 * testear sin volver a derivarla.
 */
object WhatsAppContextLifecycle {
    fun handle(event: WhatsAppContextEvent): WhatsAppContextDecision {
        val decision = WhatsAppContextLifecyclePolicy.decide(event)
        when (decision) {
            WhatsAppContextDecision.CLEAR_OPERATIONAL_CONTEXT,
            WhatsAppContextDecision.CLEAR_ALL -> WhatsAppConversationContext.clear()
            WhatsAppContextDecision.PRESERVE,
            WhatsAppContextDecision.IGNORE_STALE_EVENT -> Unit
        }
        return decision
    }
}
