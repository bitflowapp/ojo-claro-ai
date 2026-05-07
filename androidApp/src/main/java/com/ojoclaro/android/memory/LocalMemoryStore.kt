package com.ojoclaro.android.memory

import android.content.Context
import android.content.SharedPreferences
import com.ojoclaro.android.privacy.PrivacyGuard

class LocalMemoryStore(
    private val preferences: SharedPreferences,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : MemoryStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun save(memory: UserMemory) {
        if (!PrivacyGuard.canStoreMemory(memory)) return

        val ids = memoryIds().toMutableSet()
        ids.add(memory.id)

        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(key(memory.id, FIELD_TYPE), memory.type.name)
            .putString(key(memory.id, FIELD_LABEL), memory.label)
            .putString(key(memory.id, FIELD_VALUE), memory.value)
            .putLong(key(memory.id, FIELD_CREATED_AT), memory.createdAtMillis)
            .putLong(key(memory.id, FIELD_UPDATED_AT), memory.updatedAtMillis)
            .putLong(key(memory.id, FIELD_EXPIRES_AT), memory.expiresAtMillis ?: NO_EXPIRY)
            .putBoolean(key(memory.id, FIELD_IS_SENSITIVE), memory.isSensitive)
            .putBoolean(key(memory.id, FIELD_USER_APPROVED), memory.userApproved)
            .apply()
    }

    override fun getByType(type: MemoryType): List<UserMemory> =
        activeMemories()
            .filter { it.type == type }
            .sortedByDescending { it.updatedAtMillis }

    override fun findRelevant(query: String): List<UserMemory> {
        val normalizedQuery = MemoryPolicy.normalize(query)
        return activeMemories()
            .filter { memory ->
                if (normalizedQuery.isBlank()) {
                    true
                } else {
                    val searchable = MemoryPolicy.normalize(
                        "${memory.type.name} ${spanishKeywordsFor(memory.type)} ${memory.label} ${memory.value}"
                    )
                    normalizedQuery.split(' ').filter(String::isNotBlank).all(searchable::contains)
                }
            }
            .sortedByDescending { it.updatedAtMillis }
    }

    private fun spanishKeywordsFor(type: MemoryType): String = when (type) {
        MemoryType.TRUSTED_CONTACT -> "contacto confianza"
        MemoryType.EMERGENCY_CONTACT -> "contacto emergencia"
        MemoryType.LOCATION_ALIAS -> "ubicacion lugar alias mapa mapas"
        MemoryType.USER_PREFERENCE -> "preferencia preferencias"
        MemoryType.SAFETY_RULE -> "regla seguridad"
        MemoryType.FREQUENT_COMMAND -> "comando frecuente"
        MemoryType.APP_SENSITIVITY -> "app sensible aplicacion"
        MemoryType.WARNING_KEYWORD -> "alerta aviso advertencia"
        MemoryType.ROUTINE_PATTERN -> "rutina patron"
    }

    override fun delete(id: String) {
        val ids = memoryIds().toMutableSet()
        ids.remove(id)

        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(key(id, FIELD_TYPE))
            .remove(key(id, FIELD_LABEL))
            .remove(key(id, FIELD_VALUE))
            .remove(key(id, FIELD_CREATED_AT))
            .remove(key(id, FIELD_UPDATED_AT))
            .remove(key(id, FIELD_EXPIRES_AT))
            .remove(key(id, FIELD_IS_SENSITIVE))
            .remove(key(id, FIELD_USER_APPROVED))
            .apply()
    }

    override fun clearAll() {
        val edit = preferences.edit()
        memoryIds().forEach { id ->
            edit.remove(key(id, FIELD_TYPE))
                .remove(key(id, FIELD_LABEL))
                .remove(key(id, FIELD_VALUE))
                .remove(key(id, FIELD_CREATED_AT))
                .remove(key(id, FIELD_UPDATED_AT))
                .remove(key(id, FIELD_EXPIRES_AT))
                .remove(key(id, FIELD_IS_SENSITIVE))
                .remove(key(id, FIELD_USER_APPROVED))
        }
        edit.remove(KEY_IDS).apply()
    }

    override fun listAllSafeSummaries(): List<String> =
        activeMemories()
            .filter { !it.isSensitive && it.userApproved && PrivacyGuard.canStoreMemory(it) }
            .sortedBy { it.type.ordinal }
            .map(::safeSummary)

    private fun activeMemories(): List<UserMemory> {
        val now = nowMillis()
        return memoryIds().mapNotNull(::readMemory)
            .filter { memory ->
                val expired = memory.expiresAtMillis?.let { it <= now } == true
                if (expired) delete(memory.id)
                !expired
            }
    }

    private fun readMemory(id: String): UserMemory? {
        val typeName = preferences.getString(key(id, FIELD_TYPE), null) ?: return null
        val type = runCatching { MemoryType.valueOf(typeName) }.getOrNull() ?: return null
        val label = preferences.getString(key(id, FIELD_LABEL), null) ?: return null
        val value = preferences.getString(key(id, FIELD_VALUE), null) ?: return null
        val expiresAt = preferences.getLong(key(id, FIELD_EXPIRES_AT), NO_EXPIRY)

        return UserMemory(
            id = id,
            type = type,
            label = label,
            value = value,
            createdAtMillis = preferences.getLong(key(id, FIELD_CREATED_AT), 0L),
            updatedAtMillis = preferences.getLong(key(id, FIELD_UPDATED_AT), 0L),
            expiresAtMillis = expiresAt.takeUnless { it == NO_EXPIRY },
            isSensitive = preferences.getBoolean(key(id, FIELD_IS_SENSITIVE), true),
            userApproved = preferences.getBoolean(key(id, FIELD_USER_APPROVED), false)
        )
    }

    private fun memoryIds(): Set<String> =
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

    private fun safeSummary(memory: UserMemory): String = when (memory.type) {
        MemoryType.TRUSTED_CONTACT ->
            "Recuerdo que ${memory.label} es contacto de confianza."
        MemoryType.EMERGENCY_CONTACT ->
            "Recuerdo que ${memory.label} es contacto de emergencia."
        MemoryType.LOCATION_ALIAS ->
            "Recuerdo la ubicación ${memory.label}."
        MemoryType.USER_PREFERENCE ->
            "Recuerdo que preferís ${memory.value}."
        MemoryType.SAFETY_RULE ->
            "Recuerdo una regla de seguridad: ${memory.value}."
        MemoryType.FREQUENT_COMMAND ->
            "Recuerdo que usás seguido ${memory.label}."
        MemoryType.APP_SENSITIVITY ->
            "Recuerdo tratar ${memory.label} como app sensible."
        MemoryType.WARNING_KEYWORD ->
            "Recuerdo avisarte si aparece ${memory.value}."
        MemoryType.ROUTINE_PATTERN ->
            "Recuerdo un patrón de uso no sensible: ${memory.value}."
    }

    private fun key(id: String, field: String): String = "memory.$id.$field"

    companion object {
        const val PREFS_NAME = "ojo_claro_safe_memory"

        private const val KEY_IDS = "memory_ids"
        private const val FIELD_TYPE = "type"
        private const val FIELD_LABEL = "label"
        private const val FIELD_VALUE = "value"
        private const val FIELD_CREATED_AT = "created_at"
        private const val FIELD_UPDATED_AT = "updated_at"
        private const val FIELD_EXPIRES_AT = "expires_at"
        private const val FIELD_IS_SENSITIVE = "is_sensitive"
        private const val FIELD_USER_APPROVED = "user_approved"
        private const val NO_EXPIRY = Long.MAX_VALUE
    }
}
