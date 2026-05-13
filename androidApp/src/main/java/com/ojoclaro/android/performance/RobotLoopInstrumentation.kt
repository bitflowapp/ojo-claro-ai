package com.ojoclaro.android.performance

/**
 * Local-only timing probes for the voice robot loop.
 *
 * Contract:
 *  - records metric names and durations only;
 *  - never accepts screen text, snapshots, labels, commands, or spoken output;
 *  - keeps a small in-memory ring buffer only;
 *  - optional log sinks are disabled by default and receive the same safe event.
 */
object RobotLoopInstrumentation {

    private const val MAX_EVENTS: Int = 64
    private const val MAX_SAFE_LOGS: Int = 128
    private const val MAX_PACKAGE_CHARS: Int = 80
    private const val MAX_SAFE_LABEL_CHARS: Int = 48

    @Volatile
    var enabled: Boolean = true

    @Volatile
    var localLogSink: ((RobotLoopMetricEvent) -> Unit)? = null

    @Volatile
    var safeLogsEnabled: Boolean = true

    @Volatile
    var localSafeLogSink: ((RobotLoopSafeLogEvent) -> Unit)? = null

    private val lock = Any()
    private val events = ArrayDeque<RobotLoopMetricEvent>()
    private val safeLogs = ArrayDeque<RobotLoopSafeLogEvent>()

    fun <T> measure(metric: RobotLoopMetric, block: () -> T): T {
        if (!enabled) return block()
        val start = System.nanoTime()
        try {
            return block()
        } finally {
            recordElapsedNanos(metric, System.nanoTime() - start)
        }
    }

    fun recordElapsedNanos(metric: RobotLoopMetric, elapsedNanos: Long) {
        if (!enabled) return
        val safeElapsed = elapsedNanos.coerceAtLeast(0L)
        val event = RobotLoopMetricEvent(
            metric = metric,
            durationMillis = safeElapsed / NANOS_PER_MILLI,
            durationNanos = safeElapsed
        )
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
            }
            events.addLast(event)
        }
        localLogSink?.invoke(event)
    }

    fun recordSafeLog(event: RobotLoopSafeLogEvent) {
        if (!safeLogsEnabled) return
        val safeEvent = event.copy(
            packageName = sanitizePackageName(event.packageName),
            robotState = sanitizeSafeLabel(event.robotState),
            handler = sanitizeSafeLabel(event.handler),
            reasonCode = sanitizeSafeLabel(event.reasonCode),
            appState = sanitizeSafeLabel(event.appState),
            proxyHealth = sanitizeSafeLabel(event.proxyHealth),
            modelExpected = sanitizeSafeLabel(event.modelExpected),
            whitelistIntent = sanitizeSafeLabel(event.whitelistIntent)
        )
        synchronized(lock) {
            if (safeLogs.size >= MAX_SAFE_LOGS) {
                safeLogs.removeFirst()
            }
            safeLogs.addLast(safeEvent)
        }
        localSafeLogSink?.invoke(safeEvent)
    }

    fun snapshot(): List<RobotLoopMetricEvent> =
        synchronized(lock) { events.toList() }

    fun safeLogSnapshot(): List<RobotLoopSafeLogEvent> =
        synchronized(lock) { safeLogs.toList() }

    fun clear() {
        synchronized(lock) {
            events.clear()
            safeLogs.clear()
        }
    }

    private fun sanitizePackageName(packageName: String?): String? {
        if (packageName.isNullOrBlank()) return null
        val sanitized = packageName
            .filter { it.isLetterOrDigit() || it == '.' || it == '_' }
            .take(MAX_PACKAGE_CHARS)
        return sanitized.ifBlank { null }
    }

    private fun sanitizeSafeLabel(label: String?): String? {
        if (label.isNullOrBlank()) return null
        val sanitized = label
            .filter { it.isLetterOrDigit() || it == '_' || it == '-' || it == '.' }
            .take(MAX_SAFE_LABEL_CHARS)
        return sanitized.ifBlank { null }
    }

    private const val NANOS_PER_MILLI: Long = 1_000_000L
}

enum class RobotLoopMetric {
    SCREEN_SNAPSHOT,
    ACCESSIBILITY_NODE_TRAVERSAL,
    ACCESSIBILITY_NODE_MAPPING,
    SCREEN_UNDERSTANDING,
    SCREEN_SUMMARIZER,
    WHATSAPP_DETECTOR,
    WHATSAPP_GUIDED_WORKFLOW,
    VISIBLE_CHATS_READER,
    ROUTINE_PREFERENCE_APPLIER,
    VOICE_COMMAND_TO_SPOKEN_TEXT
}

data class RobotLoopMetricEvent(
    val metric: RobotLoopMetric,
    val durationMillis: Long,
    val durationNanos: Long
)

enum class RobotLoopLogStage {
    STRUCTURED_SCREEN_SNAPSHOT,
    SCREEN_UNDERSTANDING,
    WHATSAPP_GUIDED_WORKFLOW,
    WHATSAPP_VISIBLE_CHATS_READER,
    HUMAN_ROUTINE_PREFERENCES,
    TTS_SPOKEN_EVENT,
    ROBOT_STATUS_DIAGNOSTIC,
    VOICE_COMMAND,
    ROUTING_AUDIT,
    VOICE_LOOP,
    SAFE_AI_FALLBACK
}

enum class RobotLoopLogResult {
    OK,
    NOT_A_COMMAND,
    ACCESSIBILITY_OFF,
    NO_SNAPSHOT,
    NOT_IN_WHATSAPP,
    STATE_NOT_CONFIDENT,
    INSIDE_CHAT,
    LISTED,
    NO_CHATS_VISIBLE,
    BLOCKED_BY_SAFETY,
    SAVED,
    FORGOTTEN,
    LEARNING_ENABLED,
    LEARNING_DISABLED,
    SPOKEN,
    UNDERSTOOD,
    NOT_UNDERSTOOD,
    RESET
}

enum class RobotLoopBlockReason {
    NONE,
    ACCESSIBILITY_OFF,
    BANKING_SCREEN,
    PASSWORD_FIELD,
    VERIFICATION_CODE,
    SENSITIVE_SCREEN,
    SENSITIVE_ROUTINE
}

data class RobotLoopSafeLogEvent(
    val stage: RobotLoopLogStage,
    val result: RobotLoopLogResult,
    val durationMillis: Long? = null,
    val packageName: String? = null,
    val elementCount: Int? = null,
    val buttonCount: Int? = null,
    val fieldCount: Int? = null,
    val whatsappDetected: Boolean? = null,
    val chatOpen: Boolean? = null,
    val visibleChatCount: Int? = null,
    val blocked: Boolean? = null,
    val blockReason: RobotLoopBlockReason? = null,
    val forceSpeak: Boolean? = null,
    val robotState: String? = null,
    val commandRedacted: Boolean? = null,
    val handler: String? = null,
    val understood: Boolean? = null,
    val requestId: Long? = null,
    val consumed: Boolean? = null,
    val reasonCode: String? = null,
    val appState: String? = null,
    val micActive: Boolean? = null,
    val requestSent: Boolean? = null,
    val proxyConfigured: Boolean? = null,
    val proxyHealth: String? = null,
    val modelExpected: String? = null,
    val sensitiveScreen: Boolean? = null,
    val pendingConfirmation: Boolean? = null,
    val whitelistIntent: String? = null,
    val whitelistPass: Boolean? = null
) {
    fun toLogLine(): String = buildString {
        append("stage=").append(stage.name)
        append(" result=").append(result.name)
        requestId?.let { append(" requestId=").append(it) }
        durationMillis?.let { append(" durationMs=").append(it) }
        packageName?.let { append(" package=").append(it) }
        elementCount?.let { append(" elements=").append(it) }
        buttonCount?.let { append(" buttons=").append(it) }
        fieldCount?.let { append(" fields=").append(it) }
        whatsappDetected?.let { append(" whatsapp=").append(it) }
        chatOpen?.let { append(" chatOpen=").append(it) }
        visibleChatCount?.let { append(" visibleChats=").append(it) }
        blocked?.let { append(" blocked=").append(it) }
        blockReason?.let { append(" blockReason=").append(it.name) }
        forceSpeak?.let { append(" forceSpeak=").append(it) }
        robotState?.let { append(" robotState=").append(it) }
        commandRedacted?.let { append(" commandRedacted=").append(it) }
        handler?.let { append(" handler=").append(it) }
        understood?.let { append(" understood=").append(it) }
        consumed?.let { append(" consumed=").append(it) }
        reasonCode?.let { append(" reason=").append(it) }
        appState?.let { append(" appState=").append(it) }
        micActive?.let { append(" mic=").append(if (it) "active" else "paused") }
        requestSent?.let { append(" requestSent=").append(it) }
        proxyConfigured?.let { append(" proxyConfigured=").append(it) }
        proxyHealth?.let { append(" proxyHealth=").append(it) }
        modelExpected?.let { append(" modelExpected=").append(it) }
        sensitiveScreen?.let { append(" sensitiveScreen=").append(it) }
        pendingConfirmation?.let { append(" pendingConfirmation=").append(it) }
        whitelistIntent?.let { append(" whitelistIntent=").append(it) }
        whitelistPass?.let { append(" whitelistPass=").append(it) }
    }
}
