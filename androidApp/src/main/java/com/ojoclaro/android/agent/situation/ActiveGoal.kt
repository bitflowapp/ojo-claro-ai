package com.ojoclaro.android.agent.situation

/**
 * Objetivo de tarea activo: la memoria operativa corta del Situation Brain.
 *
 * Sobrevive entre turnos y entre handoffs externos, pero NO persiste a disco y
 * vence por TTL. Sirve para que el robot recuerde "qué estaba intentando hacer"
 * sin guardar conversaciones largas ni PII cruda más allá de los slots vivos.
 *
 * @param description descripción humana corta del objetivo ("avisarle a Sofi que llego tarde").
 * @param intent intención de alto nivel asociada.
 * @param createdAt epoch millis en que se creó el objetivo.
 * @param ttlMillis tiempo de vida; pasado eso, el objetivo se considera expirado.
 * @param slotsFilled slots ya resueltos (contacto, mensaje, destino...).
 * @param slotsMissing slots que todavía faltan para poder ejecutar.
 * @param planId id del AgentPlan asociado si el objetivo escaló a multi-paso.
 */
data class ActiveGoal(
    val description: String,
    val intent: SituationIntent,
    val createdAt: Long,
    val ttlMillis: Long = 300_000L,
    val slotsFilled: Map<String, String> = emptyMap(),
    val slotsMissing: Set<String> = emptySet(),
    val planId: String? = null
) {
    init {
        require(description.isNotBlank()) { "description must not be blank" }
        require(ttlMillis > 0L) { "ttlMillis must be > 0" }
        require(createdAt >= 0L) { "createdAt must be >= 0" }
        require(slotsFilled.size <= MAX_SLOTS) {
            "slotsFilled must not exceed $MAX_SLOTS entries"
        }
        require(slotsFilled.keys.none { it.isBlank() }) {
            "slotsFilled keys must not be blank"
        }
        require(slotsFilled.values.none { it.length > MAX_SLOT_VALUE_CHARS }) {
            "slotsFilled values must not exceed $MAX_SLOT_VALUE_CHARS characters"
        }
        require(slotsMissing.none { it.isBlank() }) {
            "slotsMissing entries must not be blank"
        }
    }

    /** True si ya pasó el TTL desde [createdAt]. */
    fun isExpired(now: Long): Boolean = now - createdAt > ttlMillis

    /** True si no faltan slots por completar. */
    fun isComplete(): Boolean = slotsMissing.isEmpty()

    /** True si todavía hay slots por completar. */
    fun hasMissingSlots(): Boolean = slotsMissing.isNotEmpty()

    /**
     * Devuelve una copia con el slot [key] = [value], removiéndolo de
     * [slotsMissing]. Valida que key/value no estén en blanco y que el value
     * respete el límite de caracteres.
     */
    fun withSlotFilled(key: String, value: String): ActiveGoal {
        require(key.isNotBlank()) { "slot key must not be blank" }
        require(value.isNotBlank()) { "slot value must not be blank" }
        require(value.length <= MAX_SLOT_VALUE_CHARS) {
            "slot value must not exceed $MAX_SLOT_VALUE_CHARS characters"
        }
        return copy(
            slotsFilled = slotsFilled + (key to value),
            slotsMissing = slotsMissing - key
        )
    }

    /** Devuelve una copia sin [key] en [slotsMissing]. */
    fun withoutMissingSlot(key: String): ActiveGoal =
        copy(slotsMissing = slotsMissing - key)

    companion object {
        const val MAX_SLOTS = 10
        const val MAX_SLOT_VALUE_CHARS = 240
    }
}
