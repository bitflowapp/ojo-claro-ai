package com.ojoclaro.android.agent.core.emergency

import com.ojoclaro.android.privacy.PrivacyGuard
import java.text.Normalizer
import java.util.Locale

/**
 * Política central del modo emergencia.
 *
 * Reglas de seguridad:
 *  - NUNCA decir "la ayuda fue enviada". Decimos "abrí el marcador" / "preparé el mensaje".
 *  - NUNCA iniciar la llamada automáticamente. Usamos ACTION_DIAL siempre.
 *  - NO contactamos servicios de emergencia oficiales sin diseño legal claro.
 *  - Si es simulacro (isDrill=true), siempre confirmar normalmente.
 *  - Nunca usar countdown ni prometer una acción futura automática.
 *  - Si no hay contacto, ofrecer abrir el marcador vacío y avisar.
 *  - El mensaje de emergencia es texto fijo, opcionalmente con coords; no se
 *    inventa contenido del usuario.
 */
class EmergencyPolicy {

    fun isEmergencyPhrase(rawText: String): Boolean {
        val key = normalize(rawText)
        if (key.isBlank()) return false
        return EMERGENCY_PHRASES.any { key.contains(it) }
    }

    fun safeOfferText(): String =
        "Modo emergencia. Puedo abrir Teléfono, abrir WhatsApp, preparar un mensaje de ayuda con confirmación o cancelar. " +
            "No llamo ni envío mensajes solo."

    fun buildPlan(
        emergencyContact: EmergencyContact?,
        isDrill: Boolean = false,
        includeWhatsApp: Boolean = true,
        location: EmergencyLocation? = null
    ): EmergencyActionPlan {
        val countdown = 0
        val primary: EmergencyPrimaryAction = when {
            emergencyContact != null -> EmergencyPrimaryAction.OpenDialerForContact(emergencyContact)
            else -> EmergencyPrimaryAction.OpenDialerNoNumber
        }

        val secondaries = if (includeWhatsApp && emergencyContact != null) {
            val locationText = location?.spokenSafeText()?.takeIf { it.isNotBlank() }
            val message = buildString {
                append("Mensaje de emergencia de Estela. Estoy pidiendo ayuda.")
                if (!locationText.isNullOrBlank()) {
                    append(" Ubicación aproximada: ").append(locationText).append(".")
                }
            }
            val safeMessage = if (PrivacyGuard.isSafeMessagePayload(message)) message else null
            if (safeMessage != null) {
                listOf(
                    EmergencySecondaryAction.PrepareWhatsAppMessage(emergencyContact, safeMessage)
                )
            } else {
                emptyList()
            }
        } else {
            emptyList()
        }

        val intro = when {
            isDrill -> "Simulacro de emergencia. " + describePrimary(primary) +
                " ¿Querés continuar? Decí: confirmar."
            else -> "Modo emergencia. " + describePrimary(primary) +
                " Decí: confirmar para continuar."
        }

        return EmergencyActionPlan(
            primaryAction = primary,
            secondaryActions = secondaries,
            spokenIntroduction = intro,
            countdownSeconds = countdown,
            isDrill = isDrill
        )
    }

    /**
     * Frase honesta de "ya actué" — IMPORTANTE: nunca afirma que la llamada
     * se completó, solo que se abrió la app.
     */
    fun spokenAfterActing(plan: EmergencyActionPlan): String {
        val parts = mutableListOf<String>()
        parts += when (val p = plan.primaryAction) {
            is EmergencyPrimaryAction.OpenDialerForContact ->
                "Abrí el marcador con ${p.contact.displayName}. La llamada la disparás vos."
            is EmergencyPrimaryAction.OpenDialerForNumber ->
                "Abrí el marcador con el número configurado. La llamada la disparás vos."
            EmergencyPrimaryAction.OpenDialerNoNumber ->
                "Abrí el marcador. No tenías contacto configurado, marcá el número."
        }
        if (plan.secondaryActions.isNotEmpty()) {
            parts += "También preparé un mensaje. Yo no lo envío automáticamente."
        }
        parts += "Si querés cancelar todo, decí: cancelar."
        return parts.joinToString(" ")
    }

    private fun describePrimary(primary: EmergencyPrimaryAction): String = when (primary) {
        is EmergencyPrimaryAction.OpenDialerForContact ->
            "Voy a abrir el marcador con ${primary.contact.displayName}."
        is EmergencyPrimaryAction.OpenDialerForNumber ->
            "Voy a abrir el marcador con el número configurado."
        EmergencyPrimaryAction.OpenDialerNoNumber ->
            "No tengo contacto de emergencia configurado. Voy a abrir el marcador vacío."
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    companion object {
        private val EMERGENCY_PHRASES = listOf(
            "emergencia",
            "necesito ayuda",
            "ayuda urgente",
            "estoy en problemas",
            "ayudame",
            "auxilio",
            "estoy en peligro"
        )
    }
}

/**
 * Ubicación opcional para mensajes de emergencia. Solo se incluye el texto
 * legible que devuelve [spokenSafeText]; no se persiste el dato crudo.
 */
data class EmergencyLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Double? = null
) {
    fun spokenSafeText(): String {
        val acc = accuracyMeters?.let { " con precisión aproximada de ${it.toInt()} metros" } ?: ""
        // Locale.US para no depender del locale del JVM (en es-AR "%.4f" usa coma).
        val lat = String.format(Locale.US, "%.4f", latitude)
        val lon = String.format(Locale.US, "%.4f", longitude)
        return "lat $lat, lon $lon$acc"
    }
}
