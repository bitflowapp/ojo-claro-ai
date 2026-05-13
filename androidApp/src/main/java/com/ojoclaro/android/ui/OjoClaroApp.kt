package com.ojoclaro.android.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.ojoclaro.android.onboarding.OnboardingPreferences
import com.ojoclaro.android.onboarding.OnboardingScreen
import com.ojoclaro.android.speech.SpeechController
import com.ojoclaro.android.ui.home.HomeScreen
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Composable
fun OjoClaroApp(
    listeningTriggers: StateFlow<Long> = MutableStateFlow(0L),
    stopSpeechTriggers: StateFlow<Long> = MutableStateFlow(0L),
    debugTextSubmissions: Flow<String> = emptyFlow()
) {
    val context = LocalContext.current
    val prefs = remember { OnboardingPreferences(context) }
    var onboardingDone by remember { mutableStateOf(prefs.isCompleted()) }

    MaterialTheme {
        if (!onboardingDone) {
            val speechController = remember { SpeechController(context) }
            OnboardingScreen(
                speechController = speechController,
                onComplete = {
                    speechController.shutdown()
                    prefs.markCompleted()
                    onboardingDone = true
                },
                onSkip = {
                    speechController.shutdown()
                    prefs.markCompleted()
                    onboardingDone = true
                }
            )
        } else {
            HomeScreen(
                listeningTriggers = listeningTriggers,
                stopSpeechTriggers = stopSpeechTriggers,
                debugTextSubmissions = debugTextSubmissions
            )
        }
    }
}
