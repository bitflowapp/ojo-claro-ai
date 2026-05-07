package com.ojoclaro.android.consent

/**
 * Acción sensible pendiente de confirmación del usuario.
 *
 * El payload se guarda como Map<String, String> para no acoplar la capa de consent a un tipo
 * de comando concreto y para evitar guardar datos sensibles innecesarios — solo strings cortos
 * (nombre de contacto, paquete, etc.).
 *
 * NUNCA guardar acá: contraseñas, contenido de chats, OCR completo, imágenes, audio.
 */
data class PendingSensitiveAction(
    val id: String,
    val type: SensitiveActionType,
    val spokenExplanation: String,
    val createdAtMillis: Long,
    val expiresAtMillis: Long,
    val requiresConsentLevel: ConsentLevel,
    val payload: Map<String, String> = emptyMap()
) {
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
}
