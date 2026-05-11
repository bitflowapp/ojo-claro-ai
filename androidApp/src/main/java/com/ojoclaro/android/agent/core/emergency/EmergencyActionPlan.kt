package com.ojoclaro.android.agent.core.emergency

/**
 * Plan declarativo de qué hace el modo emergencia.
 *
 * Principal: abrir el marcador (DIAL) con el contacto/numero — el usuario toca
 * llamar manualmente. Ojo Claro NUNCA inicia la llamada por su cuenta.
 *
 * Secundario opcional: preparar mensaje de emergencia para WhatsApp/SMS,
 * incluyendo ubicación si hay permiso y disponibilidad. NO se envía solo.
 */
data class EmergencyActionPlan(
    val primaryAction: EmergencyPrimaryAction,
    val secondaryActions: List<EmergencySecondaryAction> = emptyList(),
    val spokenIntroduction: String,
    val countdownSeconds: Int = 0,
    val isDrill: Boolean = false
) {
    init {
        require(spokenIntroduction.isNotBlank())
        require(countdownSeconds in 0..MAX_COUNTDOWN_SECONDS)
    }

    companion object {
        const val MAX_COUNTDOWN_SECONDS = 10
    }
}

sealed class EmergencyPrimaryAction {
    data class OpenDialerForContact(val contact: EmergencyContact) : EmergencyPrimaryAction()
    data class OpenDialerForNumber(val phoneE164: String) : EmergencyPrimaryAction()
    object OpenDialerNoNumber : EmergencyPrimaryAction()
}

sealed class EmergencySecondaryAction {
    data class PrepareWhatsAppMessage(
        val contact: EmergencyContact,
        val message: String
    ) : EmergencySecondaryAction()

    data class PrepareSmsMessage(
        val phoneE164: String,
        val message: String
    ) : EmergencySecondaryAction()
}
