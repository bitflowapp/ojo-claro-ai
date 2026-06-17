package com.ojoclaro.android.agent.core.screen

/**
 * Paquete 5E — Memoria pura de anuncios y aplicación de cooldown.
 *
 * Diseño:
 *  - Thread-safe vía `synchronized`. La carga es mínima: a lo sumo unas pocas
 *    keys por sesión.
 *  - El cooldown se aplica por `semanticKey`. Si el caller no respeta el
 *    cooldown (anuncia igual), igual debe llamar a [rememberAnnouncement]
 *    para que la siguiente evaluación lo tenga en cuenta.
 *  - El [lastAppPackage] sirve para detectar "mismo package anunciado dos
 *    veces seguidas" (regla del spec: no anunciar APP_CHANGED dos veces al
 *    mismo packageName).
 *  - [shouldAllow] puede sortear el cooldown solo si la importancia es
 *    CRITICAL **y** cambia el `reasonKey` (ej. de "password" a "banking").
 *
 * Sin Android APIs. 100% testeable inyectando el clock al engine.
 */
class ScreenChangeMemory {

    private data class Entry(
        val announcedAtMillis: Long,
        val reasonKey: String?
    )

    private val lock = Any()
    private val byKey: MutableMap<String, Entry> = HashMap()

    @Volatile
    private var lastAppPackageInternal: String? = null

    /**
     * Decide si un anuncio con [semanticKey] y [importance] puede emitirse
     * en este instante. Considera cooldown y, para CRITICAL, escalado por
     * cambio de motivo.
     */
    fun shouldAllow(
        semanticKey: String,
        importance: ScreenChangeImportance,
        reasonKey: String?,
        cooldownMs: Long,
        nowMillis: Long
    ): Boolean {
        if (cooldownMs <= 0L) return true
        val previous = synchronized(lock) { byKey[semanticKey] } ?: return true
        val withinCooldown = nowMillis - previous.announcedAtMillis < cooldownMs
        if (!withinCooldown) return true
        if (importance == ScreenChangeImportance.CRITICAL &&
            !reasonKey.isNullOrBlank() &&
            reasonKey != previous.reasonKey
        ) {
            return true
        }
        return false
    }

    /** Marca un anuncio como emitido. Llamar SOLO si el caller efectivamente habló. */
    fun rememberAnnouncement(
        semanticKey: String,
        reasonKey: String?,
        nowMillis: Long
    ) {
        synchronized(lock) {
            byKey[semanticKey] = Entry(
                announcedAtMillis = nowMillis,
                reasonKey = reasonKey
            )
        }
    }

    fun lastAppPackage(): String? = lastAppPackageInternal

    fun rememberAppPackage(packageName: String?) {
        // Null/blank no resetea — preserva el último valor útil para que un
        // listener errático no pierda contexto.
        val sanitized = packageName?.takeIf { it.isNotBlank() } ?: return
        lastAppPackageInternal = sanitized
    }

    fun reset() {
        synchronized(lock) {
            byKey.clear()
        }
        lastAppPackageInternal = null
    }
}
