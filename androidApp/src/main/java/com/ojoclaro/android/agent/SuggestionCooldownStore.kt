package com.ojoclaro.android.agent

import android.content.Context
import android.content.SharedPreferences

interface SuggestionCooldownStore {
    fun isOnCooldown(key: String, nowMillis: Long = System.currentTimeMillis()): Boolean
    fun markShown(key: String, cooldownMillis: Long, nowMillis: Long = System.currentTimeMillis())
    fun clear(key: String)
    fun clearAll()
}

class SharedPreferencesSuggestionCooldownStore(
    private val preferences: SharedPreferences,
) : SuggestionCooldownStore {

    constructor(context: Context) : this(
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    override fun isOnCooldown(key: String, nowMillis: Long): Boolean {
        val until = preferences.getLong(cooldownKey(key), 0L)
        return until > nowMillis
    }

    override fun markShown(key: String, cooldownMillis: Long, nowMillis: Long) {
        preferences.edit()
            .putLong(cooldownKey(key), nowMillis + cooldownMillis.coerceAtLeast(0L))
            .apply()
    }

    override fun clear(key: String) {
        preferences.edit().remove(cooldownKey(key)).apply()
    }

    override fun clearAll() {
        preferences.edit().clear().apply()
    }

    private fun cooldownKey(key: String) = "cooldown.$key"

    companion object {
        const val PREFS_NAME = "ojo_claro_suggestion_cooldowns"
    }
}

