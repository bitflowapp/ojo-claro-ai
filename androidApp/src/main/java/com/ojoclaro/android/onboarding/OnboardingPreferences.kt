package com.ojoclaro.android.onboarding

import android.content.Context
import android.content.SharedPreferences

/**
 * Persiste si el usuario ya completó el onboarding.
 * Usa SharedPreferences nativo para no agregar dependencias.
 * No guarda contenido sensible: solo un flag boolean.
 */
class OnboardingPreferences(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isCompleted(): Boolean = prefs.getBoolean(KEY_COMPLETED, false)

    fun markCompleted() {
        prefs.edit().putBoolean(KEY_COMPLETED, true).apply()
    }

    fun reset() {
        prefs.edit().remove(KEY_COMPLETED).apply()
    }

    companion object {
        private const val PREFS_NAME = "ojo_claro_onboarding"
        private const val KEY_COMPLETED = "onboarding_completed"
    }
}
