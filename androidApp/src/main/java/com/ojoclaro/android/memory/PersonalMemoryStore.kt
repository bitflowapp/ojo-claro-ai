package com.ojoclaro.android.memory

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

interface PersonalMemoryStore {
    fun save(memory: PersonalAgentMemory)
    fun getByType(type: PersonalMemoryType): List<PersonalAgentMemory>
    fun findRelevant(query: String): List<PersonalAgentMemory>
    fun delete(id: String)
    fun clearAll()
    fun snapshot(): PersonalMemorySnapshot
    fun listAllSafeSummaries(): List<String>
}

class SharedPreferencesPersonalMemoryStore(
    private val preferences: SharedPreferences,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) : PersonalMemoryStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun save(memory: PersonalAgentMemory) {
        if (!PersonalMemoryPolicy.canStore(memory)) return
        val ids = ids().toMutableSet().apply { add(memory.id) }
        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(key(memory.id), memory.toJson().toString())
            .apply()
    }

    override fun getByType(type: PersonalMemoryType): List<PersonalAgentMemory> =
        activeMemories().filter { it.type == type }.sortedByDescending { it.updatedAtMillis }

    override fun findRelevant(query: String): List<PersonalAgentMemory> {
        val normalized = PersonalMemoryPolicy.normalize(query)
        if (normalized.isBlank()) return activeMemories().sortedByDescending { it.updatedAtMillis }

        return activeMemories()
            .filter { memory ->
                val searchable = PersonalMemoryPolicy.normalize("${memory.type.name} ${memory.label} ${memory.value}")
                normalized.split(' ').filter(String::isNotBlank).all(searchable::contains)
            }
            .sortedByDescending { it.updatedAtMillis }
    }

    override fun delete(id: String) {
        val ids = ids().toMutableSet().apply { remove(id) }
        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .remove(key(id))
            .apply()
    }

    override fun clearAll() {
        val edit = preferences.edit()
        ids().forEach { id -> edit.remove(key(id)) }
        edit.remove(KEY_IDS).apply()
    }

    override fun snapshot(): PersonalMemorySnapshot = PersonalMemorySnapshot(activeMemories())

    override fun listAllSafeSummaries(): List<String> =
        activeMemories()
            .filter { it.userApproved && !it.isSensitive && it.canBeSpoken }
            .sortedBy { it.type.ordinal }
            .map(PersonalMemoryPolicy::safeSummary)

    private fun activeMemories(): List<PersonalAgentMemory> {
        val now = nowMillis()
        return ids().mapNotNull(::read).filter { memory ->
            val expired = memory.expiresAtMillis?.let { it <= now } == true
            if (expired) delete(memory.id)
            !expired
        }
    }

    private fun read(id: String): PersonalAgentMemory? {
        val raw = preferences.getString(key(id), null) ?: return null
        return runCatching { JSONObject(raw).toMemory() }.getOrNull()
    }

    private fun ids(): Set<String> =
        preferences.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

    private fun key(id: String): String = "personal_memory.$id"

    private fun PersonalAgentMemory.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("type", type.name)
        put("label", label)
        put("value", value)
        put("createdAtMillis", createdAtMillis)
        put("updatedAtMillis", updatedAtMillis)
        put("expiresAtMillis", expiresAtMillis)
        put("isSensitive", isSensitive)
        put("userApproved", userApproved)
        put("canBeSpoken", canBeSpoken)
        put("canBeUsedForSuggestions", canBeUsedForSuggestions)
        put("requiresConfirmationBeforeUse", requiresConfirmationBeforeUse)
    }

    private fun JSONObject.toMemory(): PersonalAgentMemory =
        PersonalAgentMemory(
            id = getString("id"),
            type = PersonalMemoryType.valueOf(getString("type")),
            label = getString("label"),
            value = getString("value"),
            createdAtMillis = getLong("createdAtMillis"),
            updatedAtMillis = getLong("updatedAtMillis"),
            expiresAtMillis = if (has("expiresAtMillis") && !isNull("expiresAtMillis")) getLong("expiresAtMillis") else null,
            isSensitive = getBoolean("isSensitive"),
            userApproved = getBoolean("userApproved"),
            canBeSpoken = getBoolean("canBeSpoken"),
            canBeUsedForSuggestions = getBoolean("canBeUsedForSuggestions"),
            requiresConfirmationBeforeUse = getBoolean("requiresConfirmationBeforeUse")
        )

    companion object {
        const val PREFS_NAME = "ojo_claro_personal_memory"
        private const val KEY_IDS = "personal_memory_ids"
    }
}

