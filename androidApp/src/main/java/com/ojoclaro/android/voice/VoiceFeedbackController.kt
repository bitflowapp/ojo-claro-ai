package com.ojoclaro.android.voice

import java.util.Locale

/**
 * Pure semantic feedback gate for speech output.
 *
 * It has no Android dependency and never executes user actions. The optional
 * [speakNow] callback is only invoked after the controller has decided to
 * speak a text.
 */
class VoiceFeedbackController(
    private val speakNow: (String) -> Unit = {},
    private val stopSpeaking: () -> Unit = {},
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    private data class LastSpoken(
        val semanticKey: String,
        val text: String,
        val timestampMs: Long,
        val alternateIndex: Int
    )

    private val lock = Any()
    private var lastSpoken: LastSpoken? = null

    fun emit(feedback: SpokenFeedback): VoiceFeedbackDecision {
        val decision = decide(feedback)
        if (decision is VoiceFeedbackDecision.Speak) {
            speakNow(decision.text)
        }
        return decision
    }

    fun decide(feedback: SpokenFeedback): VoiceFeedbackDecision {
        val now = clock()
        return synchronized(lock) {
            decideLocked(feedback, now)
        }
    }

    fun repeatLast(): VoiceFeedbackDecision {
        val snapshot = synchronized(lock) { lastSpoken }
            ?: return VoiceFeedbackDecision.Suppress(reason = "no_history")
        speakNow(snapshot.text)
        return VoiceFeedbackDecision.Speak(text = snapshot.text, reason = "repeat_last")
    }

    fun shutUp() {
        synchronized(lock) {
            lastSpoken = null
        }
        stopSpeaking()
    }

    fun resetMemory() {
        synchronized(lock) {
            lastSpoken = null
        }
    }

    private fun decideLocked(
        feedback: SpokenFeedback,
        now: Long
    ): VoiceFeedbackDecision {
        if (feedback.force) {
            remember(feedback, feedback.text, now, alternateIndex = 0)
            return VoiceFeedbackDecision.Speak(text = feedback.text, reason = "forced")
        }

        if (feedback.priority == SpokenFeedbackPriority.CRITICAL) {
            remember(feedback, feedback.text, now, alternateIndex = 0)
            return VoiceFeedbackDecision.Speak(text = feedback.text, reason = "critical")
        }

        val previous = lastSpoken
        if (previous == null) {
            remember(feedback, feedback.text, now, alternateIndex = 0)
            return VoiceFeedbackDecision.Speak(text = feedback.text, reason = "fresh")
        }

        val withinWindow = feedback.dedupWindowMs > 0L &&
            now - previous.timestampMs < feedback.dedupWindowMs
        val sameText = normalize(previous.text) == normalize(feedback.text)
        val sameSemantic = previous.semanticKey == feedback.semanticKey

        if (withinWindow && (sameText || sameSemantic)) {
            val alternate = nextAlternate(feedback, previous)
            if (alternate != null) {
                val nextIndex = previous.alternateIndex + 1
                remember(feedback, alternate, now, nextIndex)
                return VoiceFeedbackDecision.Speak(
                    text = alternate,
                    reason = "alternate_rotated"
                )
            }

            val reason = if (sameText) {
                "duplicate_text_within_window"
            } else {
                "duplicate_semantic_key_within_window"
            }
            return VoiceFeedbackDecision.Suppress(reason = reason)
        }

        remember(feedback, feedback.text, now, alternateIndex = 0)
        return VoiceFeedbackDecision.Speak(text = feedback.text, reason = "fresh")
    }

    private fun remember(
        feedback: SpokenFeedback,
        text: String,
        now: Long,
        alternateIndex: Int
    ) {
        lastSpoken = LastSpoken(
            semanticKey = feedback.semanticKey,
            text = text,
            timestampMs = now,
            alternateIndex = alternateIndex
        )
    }

    private fun nextAlternate(
        feedback: SpokenFeedback,
        previous: LastSpoken
    ): String? {
        val candidates = feedback.alternates
            .filter { normalize(it) != normalize(feedback.text) }
            .filter { normalize(it) != normalize(previous.text) }
        if (candidates.isEmpty()) return null
        val index = previous.alternateIndex.coerceAtLeast(0) % candidates.size
        return candidates[index]
    }

    private fun normalize(text: String): String =
        text.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")
}

sealed class VoiceFeedbackDecision {
    data class Speak(val text: String, val reason: String) : VoiceFeedbackDecision()
    data class Suppress(val reason: String) : VoiceFeedbackDecision()
}
