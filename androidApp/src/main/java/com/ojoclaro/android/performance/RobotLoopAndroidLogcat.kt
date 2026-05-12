package com.ojoclaro.android.performance

import android.util.Log

/**
 * Android-only bridge for QA logcat.
 *
 * The payload comes from [RobotLoopSafeLogEvent], which contains only bounded
 * counters, booleans, enum results, durations, and package names.
 */
object RobotLoopAndroidLogcat {

    private const val TAG = "OjoClaroRobotLoop"

    @Volatile
    private var installed: Boolean = false

    fun install(enabled: Boolean) {
        if (!enabled || installed) return
        installed = true
        RobotLoopInstrumentation.localSafeLogSink = { event ->
            Log.d(TAG, event.toLogLine())
        }
    }
}
