package com.ojoclaro.android.agent.runtime.whatsapp

import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.Capability
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.Inputs
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.NextStep
import com.ojoclaro.android.agent.runtime.whatsapp.WhatsAppCapabilityMatrix.Verdict

/**
 * Narración CALMA y CORTA de la matriz de capacidades. Nunca deja al usuario
 * perdido: dice qué puede hacer, qué falta (un paso) y por qué. Sin contenido
 * de chats; solo estado de capacidades.
 */
object WhatsAppCapabilityNarrator {

    /** "¿Qué puedo hacer ahora?" — capacidades disponibles + el próximo paso. */
    fun whatCanIDoNow(verdict: Verdict): String {
        val abilities = verdict.can.map(::friendly)
        val head = if (abilities.isEmpty()) {
            "Ahora mismo no puedo hacer mucho en WhatsApp."
        } else {
            "Ahora puedo: ${joinNatural(abilities)}."
        }
        val step = nextStepAsk(verdict.nextStep)
        return if (step.isBlank()) head else "$head $step"
    }

    /** "¿Qué falta?" — el único próximo paso accionable. */
    fun whatIsMissing(verdict: Verdict): String = when (verdict.nextStep) {
        NextStep.READY -> "No falta nada: está todo listo."
        else -> nextStepAsk(verdict.nextStep)
    }

    /** "¿Por qué no funciona?" — el primer bloqueo, en una frase. */
    fun whyNotWorking(inputs: Inputs): String = when {
        !inputs.whatsappInstalled -> "WhatsApp no está instalado en este teléfono."
        !inputs.accessibilityBound ->
            "Accesibilidad de Estela no está activa, así que todavía no puedo leer la pantalla."
        !inputs.inWhatsApp -> "WhatsApp no está adelante. Abrilo y volvé."
        !inputs.inChat -> "No hay un chat abierto. Abrí uno o decime un número conocido."
        else -> "Está todo en orden. Decime qué querés hacer."
    }

    /**
     * Respuesta cuando piden leer/responder pero falta Accesibilidad vinculada.
     * Honesta sobre el límite y sobre lo que SÍ se puede (notificaciones).
     */
    fun accessibilityGatedRead(inputs: Inputs): String {
        val base = "Todavía no puedo leer la pantalla porque falta activar Accesibilidad."
        val tail = if (inputs.notificationListener) {
            " Sí puedo revisar tus notificaciones de WhatsApp si querés."
        } else {
            " Decí: activar Estela, y te guío para activarla."
        }
        return base + tail
    }

    private fun nextStepAsk(step: NextStep): String = when (step) {
        NextStep.INSTALL_WHATSAPP -> "Necesitás instalar WhatsApp."
        NextStep.ENABLE_ACCESSIBILITY ->
            "Para leer la pantalla y responder, falta activar Accesibilidad. Decí: activar Estela."
        NextStep.OPEN_WHATSAPP -> "Abrí WhatsApp y volvé."
        NextStep.OPEN_CHAT -> "Abrí un chat o decime un número conocido."
        NextStep.GRANT_NOTIFICATIONS ->
            "Para avisarte de mensajes nuevos, dame acceso a notificaciones."
        NextStep.READY -> ""
    }

    private fun friendly(capability: Capability): String = when (capability) {
        Capability.CHECK_NOTIFICATIONS -> "revisar tus notificaciones"
        Capability.OPEN_WHATSAPP -> "abrir WhatsApp"
        Capability.READ_SCREEN -> "leer la pantalla"
        Capability.PREPARE_REPLY_DRY_RUN -> "preparar una respuesta para que confirmes"
    }

    private fun joinNatural(items: List<String>): String = when (items.size) {
        0 -> ""
        1 -> items[0]
        else -> items.dropLast(1).joinToString(", ") + " y " + items.last()
    }
}
