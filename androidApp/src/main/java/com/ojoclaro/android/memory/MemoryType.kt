package com.ojoclaro.android.memory

enum class MemoryType(
    val spokenLabel: String,
    val description: String,
    val canBeListedSafely: Boolean,
    val canSuggestActions: Boolean
) {
    TRUSTED_CONTACT(
        spokenLabel = "contacto de confianza",
        description = "Persona que el usuario reconoce como contacto seguro.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    EMERGENCY_CONTACT(
        spokenLabel = "contacto de emergencia",
        description = "Persona que el usuario reconoce como contacto de emergencia.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    LOCATION_ALIAS(
        spokenLabel = "ubicación guardada",
        description = "Alias de ubicación aprobado por el usuario, como casa o trabajo.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    USER_PREFERENCE(
        spokenLabel = "preferencia",
        description = "Preferencia del usuario, como respuestas cortas o voz más lenta.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    SAFETY_RULE(
        spokenLabel = "regla de seguridad",
        description = "Regla creada por el usuario para recibir advertencias.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    FREQUENT_COMMAND(
        spokenLabel = "comando frecuente",
        description = "Acción que el usuario usa seguido, sin guardar contenido privado.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    APP_SENSITIVITY(
        spokenLabel = "app sensible",
        description = "App que debe tratarse con más cuidado, como banco o billetera virtual.",
        canBeListedSafely = true,
        canSuggestActions = false
    ),

    WARNING_KEYWORD(
        spokenLabel = "palabra de advertencia",
        description = "Palabra o tema sobre el que el usuario quiere recibir aviso.",
        canBeListedSafely = true,
        canSuggestActions = true
    ),

    ROUTINE_PATTERN(
        spokenLabel = "patrón de uso",
        description = "Rutina no sensible detectada por repetición.",
        canBeListedSafely = true,
        canSuggestActions = true
    );

    companion object {
        fun fromName(value: String): MemoryType? {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        }
    }
}
