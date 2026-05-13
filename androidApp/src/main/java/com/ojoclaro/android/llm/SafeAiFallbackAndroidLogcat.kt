package com.ojoclaro.android.llm

import android.util.Log

object SafeAiFallbackAndroidLogcat {
    private const val TAG = "SafeAiFallback"

    @Volatile
    private var installed: Boolean = false

    fun install(enabled: Boolean) {
        if (!enabled || installed) return
        installed = true
        SafeAiFallbackLogger.sink = { line ->
            Log.d(TAG, redactSecrets(line))
        }
    }
}
