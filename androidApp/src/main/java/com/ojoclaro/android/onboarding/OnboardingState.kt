package com.ojoclaro.android.onboarding

/**
 * Pasos de onboarding accesible. Cada paso es una frase corta y completa,
 * pensada para escucharse a velocidad 1x ó 2x de TalkBack/TTS.
 */
enum class OnboardingStep(val spoken: String) {
    INTRO("Soy Ojo Claro. Puedo ayudarte a leer texto, describir acciones básicas y preparar mensajes."),
    NO_AUTO_SEND("No envío mensajes sin que los confirmes."),
    NO_STORAGE("No guardo tus chats ni tus imágenes."),
    CAMERA_PERMISSION("Para leer texto necesito permiso de cámara."),
    ACCESSIBILITY_PERMISSION("Para leer la pantalla necesito que actives accesibilidad."),
    STOP_ANYTIME("Podés tocar Callar en cualquier momento.")
}

data class OnboardingState(
    val currentStep: OnboardingStep = OnboardingStep.INTRO,
    val completed: Boolean = false
) {
    fun next(): OnboardingState {
        val steps = OnboardingStep.values()
        val nextIndex = currentStep.ordinal + 1
        return if (nextIndex >= steps.size) {
            copy(completed = true)
        } else {
            copy(currentStep = steps[nextIndex])
        }
    }

    fun reset(): OnboardingState = OnboardingState()
}
