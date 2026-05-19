package com.ojoclaro.android.agent.core.screen

/**
 * Advisor puro de "qué hago ahora" para usuarios ciegos.
 *
 * **Qué hace:**
 *  - Toma un [StructuredScreenSnapshot] (puede ser null) + un
 *    [NextStepQueryKind] y devuelve un [NextStepAdvice] orientativo.
 *  - Habla en términos de UNA acción a la vez si es posible. Si hay más de
 *    una, lista las top N y aclara que el usuario elige.
 *  - Sobre hot zones (banca, password, OTP) NUNCA enumera elementos: solo
 *    advierte y sugiere salir.
 *  - Cuando hay un campo editable faltante (formulario), prioriza llenar
 *    antes de tocar botón.
 *
 * **Qué NO hace:**
 *  - No ejecuta nada. Ni performClick, ni dispatchGesture, ni Intent.
 *  - No afirma haber ejecutado una acción. Solo recomienda al usuario tocar.
 *  - No envía datos al cloud ni a LLM.
 *  - No persiste.
 *
 * Sin Android APIs. Sin coroutines. 100% testeable.
 */
class NextStepAdvisor {

    fun advise(
        snapshot: StructuredScreenSnapshot?,
        kind: NextStepQueryKind
    ): NextStepAdvice {
        if (snapshot == null || snapshot.isEmpty) {
            return NextStepAdvice.NoSnapshot()
        }

        // 1. Safety primero: hot zones bloquean cualquier enumeración.
        val safetyAdvice = checkSafety(snapshot)
        if (safetyAdvice != null) return safetyAdvice

        val appLabel = appLabelForSpeech(snapshot)

        // 2. Si la pregunta es "dónde está el botón" y hay un focusedLabel
        //    que parece botón, lo nombramos directo.
        if (kind == NextStepQueryKind.WHERE_IS_BUTTON) {
            val focused = snapshot.focusedLabel?.trim()?.takeIf { it.isNotBlank() }
            if (focused != null && snapshot.buttons.any { it.equals(focused, ignoreCase = true) }) {
                return NextStepAdvice.SingleAction(
                    buttonLabel = focused.take(MAX_LABEL_CHARS),
                    appLabel = appLabel,
                    spokenText = buildWhereIsButtonText(focused, appLabel)
                )
            }
        }

        // 3. Si hay campos editables y NO tienen contenido del foco, sugerir
        //    completarlos. Es la heurística "formulario primero, botón después".
        val emptyField = firstUnfilledEditable(snapshot)
        if (emptyField != null) {
            return NextStepAdvice.FormFillNeeded(
                fieldLabel = emptyField.take(MAX_LABEL_CHARS),
                appLabel = appLabel,
                spokenText = buildFormFillText(emptyField, appLabel)
            )
        }

        // 4. Botones disponibles.
        val buttons = snapshot.buttons
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(MAX_BUTTONS)

        return when {
            buttons.isEmpty() -> NextStepAdvice.NoActionsDetected()
            buttons.size == 1 -> NextStepAdvice.SingleAction(
                buttonLabel = buttons.first().take(MAX_LABEL_CHARS),
                appLabel = appLabel,
                spokenText = buildSingleActionText(buttons.first(), appLabel, kind)
            )
            else -> NextStepAdvice.MultipleActions(
                buttonLabels = buttons.map { it.take(MAX_LABEL_CHARS) },
                appLabel = appLabel,
                spokenText = buildMultipleActionsText(buttons, appLabel, kind)
            )
        }
    }

    private fun checkSafety(snapshot: StructuredScreenSnapshot): NextStepAdvice.SafetyBlocked? {
        val signals = snapshot.signals
        return when {
            signals.hasPasswordField -> NextStepAdvice.SafetyBlocked(
                reasonKey = "password_field",
                spokenText = "Veo un campo de contraseña. " +
                    "Por seguridad no te digo qué tocar acá. Llenalo vos y después pedime ayuda."
            )
            signals.isBankingApp || signals.hasPaymentOrTransferSignals -> NextStepAdvice.SafetyBlocked(
                reasonKey = "banking_or_payment",
                spokenText = "Esta pantalla parece de pago o banco. " +
                    "No te oriento acá por seguridad. Si querés salir, decime volver atrás."
            )
            signals.hasVerificationCode -> NextStepAdvice.SafetyBlocked(
                reasonKey = "verification_code",
                spokenText = "Veo un código de verificación. " +
                    "No te leo este contenido. Cuando lo escribas, pedime el próximo paso."
            )
            else -> null
        }
    }

    private fun firstUnfilledEditable(snapshot: StructuredScreenSnapshot): String? {
        // Un campo editable sin label útil no aporta; lo descartamos.
        return snapshot.editableFields
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() && it.length <= MAX_LABEL_CHARS * 2 }
    }

    private fun appLabelForSpeech(snapshot: StructuredScreenSnapshot): String? {
        val explicit = snapshot.appLabel?.trim()?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit.take(MAX_APP_LABEL_CHARS)
        val pkg = snapshot.packageName?.lowercase().orEmpty()
        return when {
            "whatsapp" in pkg -> "WhatsApp"
            "settings" in pkg || "ajustes" in pkg -> "Ajustes"
            "chrome" in pkg || "browser" in pkg -> "el navegador"
            "ojoclaro" in pkg -> "Ojo Claro"
            else -> null
        }
    }

    private fun buildSingleActionText(
        label: String,
        appLabel: String?,
        kind: NextStepQueryKind
    ): String {
        val truncated = label.take(MAX_LABEL_CHARS)
        val appPart = appLabel?.let { " en $it" }.orEmpty()
        val verb = when (kind) {
            NextStepQueryKind.WHERE_IS_BUTTON -> "El botón principal$appPart parece ser: $truncated."
            NextStepQueryKind.HELP_WITH_SCREEN ->
                "En esta pantalla$appPart la acción principal parece: $truncated. Vos decidís si tocarlo."
            NextStepQueryKind.WHAT_NOW ->
                "Podés tocar: $truncated$appPart. Si no querés, pedime resumen o cancelar."
        }
        return verb
    }

    private fun buildMultipleActionsText(
        labels: List<String>,
        appLabel: String?,
        kind: NextStepQueryKind
    ): String {
        val list = labels.joinToString(", ")
        val appPart = appLabel?.let { " en $it" }.orEmpty()
        return when (kind) {
            NextStepQueryKind.WHERE_IS_BUTTON ->
                "Los botones disponibles$appPart son: $list. Decime cuál tocar."
            NextStepQueryKind.HELP_WITH_SCREEN ->
                "Veo varias opciones$appPart: $list. Decime cuál querés y te oriento."
            NextStepQueryKind.WHAT_NOW ->
                "Podés tocar uno de estos$appPart: $list. Vos elegís."
        }
    }

    private fun buildFormFillText(fieldLabel: String, appLabel: String?): String {
        val truncated = fieldLabel.take(MAX_LABEL_CHARS)
        val appPart = appLabel?.let { " en $it" }.orEmpty()
        return "Hay un campo para completar$appPart: $truncated. " +
            "Llenalo primero y después pedime el próximo paso."
    }

    private fun buildWhereIsButtonText(focused: String, appLabel: String?): String {
        val truncated = focused.take(MAX_LABEL_CHARS)
        val appPart = appLabel?.let { " en $it" }.orEmpty()
        return "El botón enfocado$appPart es: $truncated."
    }

    companion object {
        private const val MAX_BUTTONS = 4
        private const val MAX_LABEL_CHARS = 60
        private const val MAX_APP_LABEL_CHARS = 30
    }
}
