package com.ojoclaro.android.patterns

import android.content.SharedPreferences
import java.text.Normalizer
import java.util.Locale

class FrequentPatternTracker(
    private val preferences: SharedPreferences? = null,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {

    fun recordCommand(
        rawCommand: String,
        commandType: String,
        appPackage: String? = null
    ): FrequentPattern {
        val metadata = metadataFor(rawCommand, commandType, appPackage)
        val existing = loadPattern(metadata.id)
        val now = nowMillis()

        val updated = if (existing == null) {
            FrequentPattern(
                id = metadata.id,
                commandType = commandType,
                normalizedCommand = metadata.normalizedCommand,
                count = 1,
                firstSeenMillis = now,
                lastSeenMillis = now,
                lastAppPackage = appPackage,
                isSensitive = metadata.isSensitive,
                // Patrones no sensibles se aprueban por defecto para sugerencias.
                // Patrones sensibles requieren aprobación explícita del usuario.
                userApprovedForSuggestions = !metadata.isSensitive
            )
        } else {
            existing.copy(
                count = existing.count + 1,
                lastSeenMillis = now,
                lastAppPackage = appPackage ?: existing.lastAppPackage,
                isSensitive = existing.isSensitive || metadata.isSensitive
            )
        }

        savePattern(updated)
        return updated
    }

    fun getTopPatterns(limit: Int): List<FrequentPattern> =
        loadAllPatterns()
            .sortedWith(compareByDescending<FrequentPattern> { it.count }.thenByDescending { it.lastSeenMillis })
            .take(limit.coerceAtLeast(0))

    fun shouldSuggestShortcut(commandType: String): Boolean {
        val candidate = loadAllPatterns()
            .filter { it.commandType == commandType }
            .maxWithOrNull(compareBy<FrequentPattern> { it.count }.thenBy { it.lastSeenMillis })
            ?: return false

        if (candidate.count < SUGGESTION_THRESHOLD) return false
        if (candidate.isSensitive && !candidate.userApprovedForSuggestions) return false
        return true
    }

    fun clearPatterns() {
        if (preferences == null) {
            memoryPatterns.clear()
            return
        }

        val edit = preferences.edit()
        patternIds().forEach { id ->
            PATTERN_FIELDS.forEach { field -> edit.remove(key(id, field)) }
        }
        edit.remove(KEY_IDS).apply()
    }

    fun forgetPattern(id: String) {
        if (preferences == null) {
            memoryPatterns.remove(id)
            return
        }

        val ids = patternIds().toMutableSet()
        ids.remove(id)
        val edit = preferences.edit().putStringSet(KEY_IDS, ids)
        PATTERN_FIELDS.forEach { field -> edit.remove(key(id, field)) }
        edit.apply()
    }

    private fun savePattern(pattern: FrequentPattern) {
        if (preferences == null) {
            memoryPatterns[pattern.id] = pattern
            return
        }

        val ids = patternIds().toMutableSet()
        ids.add(pattern.id)
        preferences.edit()
            .putStringSet(KEY_IDS, ids)
            .putString(key(pattern.id, FIELD_COMMAND_TYPE), pattern.commandType)
            .putString(key(pattern.id, FIELD_NORMALIZED_COMMAND), pattern.normalizedCommand)
            .putInt(key(pattern.id, FIELD_COUNT), pattern.count)
            .putLong(key(pattern.id, FIELD_FIRST_SEEN), pattern.firstSeenMillis)
            .putLong(key(pattern.id, FIELD_LAST_SEEN), pattern.lastSeenMillis)
            .putString(key(pattern.id, FIELD_LAST_APP_PACKAGE), pattern.lastAppPackage)
            .putBoolean(key(pattern.id, FIELD_IS_SENSITIVE), pattern.isSensitive)
            .putBoolean(key(pattern.id, FIELD_APPROVED), pattern.userApprovedForSuggestions)
            .apply()
    }

    private fun loadAllPatterns(): List<FrequentPattern> {
        if (preferences == null) return memoryPatterns.values.toList()
        return patternIds().mapNotNull(::loadPattern)
    }

    private fun loadPattern(id: String): FrequentPattern? {
        if (preferences == null) return memoryPatterns[id]

        val commandType = preferences.getString(key(id, FIELD_COMMAND_TYPE), null) ?: return null
        val normalized = preferences.getString(key(id, FIELD_NORMALIZED_COMMAND), null) ?: return null

        return FrequentPattern(
            id = id,
            commandType = commandType,
            normalizedCommand = normalized,
            count = preferences.getInt(key(id, FIELD_COUNT), 0),
            firstSeenMillis = preferences.getLong(key(id, FIELD_FIRST_SEEN), 0L),
            lastSeenMillis = preferences.getLong(key(id, FIELD_LAST_SEEN), 0L),
            lastAppPackage = preferences.getString(key(id, FIELD_LAST_APP_PACKAGE), null),
            isSensitive = preferences.getBoolean(key(id, FIELD_IS_SENSITIVE), false),
            userApprovedForSuggestions = preferences.getBoolean(key(id, FIELD_APPROVED), false)
        )
    }

    private fun patternIds(): Set<String> =
        preferences?.getStringSet(KEY_IDS, emptySet()).orEmpty().toSet()

    private fun key(id: String, field: String): String = "pattern.$id.$field"

    private fun metadataFor(
        rawCommand: String,
        commandType: String,
        appPackage: String?
    ): PatternMetadata {
        val type = commandType.uppercase(Locale.US)
        val normalizedCommand = when {
            type.contains("READ_VISIBLE_SCREEN") -> "read_visible_screen"
            type.contains("READ_TEXT") -> "read_text"
            type.contains("OPEN_WHATSAPP") -> "open_whatsapp"
            type.contains("COMPOSE_WHATSAPP") -> "compose_whatsapp_message"
            type.contains("CANCEL") -> "cancel_pending_action"
            type.contains("CONFIRM") -> "confirm_pending_action"
            type.contains("REMEMBER") -> "remember_safe_memory"
            type.contains("LIST_MEMORY") -> "list_safe_memory"
            type.contains("CLEAR_MEMORY") -> "clear_local_memory"
            else -> "command:${sanitizeType(commandType)}"
        }
        val sensitive = type.contains("WHATSAPP") ||
            appPackage?.contains("whatsapp", ignoreCase = true) == true ||
            looksSensitive(rawCommand)

        return PatternMetadata(
            id = "${sanitizeType(commandType)}|$normalizedCommand|${appPackage.orEmpty()}",
            normalizedCommand = normalizedCommand,
            isSensitive = sensitive
        )
    }

    private fun looksSensitive(rawCommand: String): Boolean {
        val normalized = normalize(rawCommand)
        return sensitiveTokens.any(normalized::contains) || Regex("\\b\\d{4,}\\b").containsMatchIn(normalized)
    }

    private fun sanitizeType(commandType: String): String =
        commandType.lowercase(Locale.US)
            .replace(Regex("[^a-z0-9_:-]+"), "_")
            .trim('_')
            .ifBlank { "unknown" }

    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private data class PatternMetadata(
        val id: String,
        val normalizedCommand: String,
        val isSensitive: Boolean
    )

    private val memoryPatterns = linkedMapOf<String, FrequentPattern>()

    companion object {
        const val PREFS_NAME = "ojo_claro_frequent_patterns"

        private const val KEY_IDS = "pattern_ids"
        private const val FIELD_COMMAND_TYPE = "command_type"
        private const val FIELD_NORMALIZED_COMMAND = "normalized_command"
        private const val FIELD_COUNT = "count"
        private const val FIELD_FIRST_SEEN = "first_seen"
        private const val FIELD_LAST_SEEN = "last_seen"
        private const val FIELD_LAST_APP_PACKAGE = "last_app_package"
        private const val FIELD_IS_SENSITIVE = "is_sensitive"
        private const val FIELD_APPROVED = "approved"
        private const val SUGGESTION_THRESHOLD = 3

        private val PATTERN_FIELDS = listOf(
            FIELD_COMMAND_TYPE,
            FIELD_NORMALIZED_COMMAND,
            FIELD_COUNT,
            FIELD_FIRST_SEEN,
            FIELD_LAST_SEEN,
            FIELD_LAST_APP_PACKAGE,
            FIELD_IS_SENSITIVE,
            FIELD_APPROVED
        )
        private val diacriticRegex = Regex("\\p{Mn}+")
        private val sensitiveTokens = listOf(
            "contrasena",
            "password",
            "codigo",
            "verificacion",
            "token",
            "tarjeta",
            "dni",
            "saldo",
            "cbu",
            "cvu"
        )
    }
}
