package com.ojoclaro.android.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ojoclaro.android.speech.SpeechController

@Composable
fun OnboardingScreen(
    speechController: SpeechController,
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    var state by remember { mutableStateOf(OnboardingState()) }
    val scrollState = rememberScrollState()

    LaunchedEffect(state.currentStep, state.completed) {
        if (!state.completed) {
            speechController.speak(state.currentStep.spoken, force = true)
        }
    }

    LaunchedEffect(state.completed) {
        if (state.completed) {
            speechController.stop()
            onComplete()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Bienvenida a Estela",
                color = Color.White,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                modifier = Modifier.semantics { heading() }
            )

            Text(
                text = state.currentStep.spoken,
                color = Color.White,
                fontSize = 22.sp,
                lineHeight = 30.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = state.currentStep.spoken
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { state = state.next() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 88.dp)
                    .semantics {
                        contentDescription = if (state.currentStep == OnboardingStep.STOP_ANYTIME) {
                            "Empezar a usar la app."
                        } else {
                            "Siguiente paso del tutorial."
                        }
                    }
            ) {
                Text(
                    text = if (state.currentStep == OnboardingStep.STOP_ANYTIME) "EMPEZAR" else "SIGUIENTE",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Black
                )
            }

            OutlinedButton(
                onClick = {
                    speechController.stop()
                    speechController.speak(state.currentStep.spoken, force = true)
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics { contentDescription = "Repetir explicación." }
            ) {
                Text(
                    text = "Repetir explicación",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = {
                    speechController.stop()
                    onSkip()
                },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                border = BorderStroke(2.dp, Color.White),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .semantics { contentDescription = "Saltar tutorial." }
            ) {
                Text(
                    text = "Saltar",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
