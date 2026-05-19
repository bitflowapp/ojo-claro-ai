package com.ojoclaro.android.agent.core.screen

import java.util.Locale

/**
 * Paquete 5E — Engine puro de detección de cambios relevantes de pantalla.
 *
 * Reglas (prioridad: la primera que matchea gana):
 *  1. Pantalla anterior tenía contenido, la actual quedó vacía → SCREEN_BECAME_EMPTY (LOW).
 *  2. Hot zone aparece (password / banca-pago / OTP) → safety warning CRITICAL.
 *  3. Pantalla restaurada (prev empty, current con contenido) → SCREEN_RESTORED (LOW).
 *  4. packageName cambió → APP_CHANGED (NORMAL).
 *  5. Apareció formulario (editableFields ↑) → FORM_SCREEN_ENTERED (NORMAL).
 *  6. Apareció pantalla de mensajería (messagingApp ↑) → CHAT_SCREEN_ENTERED (NORMAL).
 *  7. Diálogo/alerta (botones tipo Aceptar/Cancelar/Permitir) → DIALOG_OR_ALERT_APPEARED (HIGH).
 *  8. Conjunto de botones cambió drásticamente → IMPORTANT_BUTTONS_CHANGED (LOW).
 *  9. Si no aplica nada → NONE.
 *
 * Seguridad:
 *  - NUNCA cita `redactedTextLines`, `focusedLabel` ni button labels en el
 *    spokenText de safety warnings.
 *  - Sobre hot zones, las advertencias mencionan la categoría ("pantalla
 *    bancaria") y no el contenido.
 *
 * Sin Android APIs. Sin coroutines. 100% testeable.
 */
class ScreenChangeAwarenessEngine(
    private val memory: ScreenChangeMemory = ScreenChangeMemory(),
    private val clock: () -> Long = { System.currentTimeMillis() }
) {

    /**
     * Evalúa la transición. NO emite voz. NO toca acciones. Si el caller
     * decide hablar, debe llamar a [rememberAnnounced] para registrar el
     * anuncio en la memoria.
     */
    fun evaluate(
        previous: StructuredScreenSnapshot?,
        current: StructuredScreenSnapshot?,
        nowMillis: Long = clock()
    ): ScreenChangeAnnouncement {
        // Caso 1: pantalla quedó vacía
        if ((current == null || current.isEmpty) && previous != null && !previous.isEmpty) {
            return gate(
                announcement = ScreenChangeAnnouncement(
                    event = ScreenChangeEvent.SCREEN_BECAME_EMPTY,
                    importance = ScreenChangeImportance.LOW,
                    semanticKey = "screen.change.empty",
                    spokenText = "Perdí la lectura de la pantalla.",
                    reasonKey = "empty",
                    shouldAnnounce = true,
                    safeForSpeech = true,
                    cooldownMs = ScreenChangeAnnouncement.LOW_NOISE_COOLDOWN_MS
                ),
                nowMillis = nowMillis
            )
        }

        if (current == null || current.isEmpty) {
            return ScreenChangeAnnouncement.NONE
        }

        // Caso 2: hot zone aparece (o es la primera lectura ya en hot zone)
        val prevHot = previous?.signals?.isHotZone == true
        val currentHot = current.signals.isHotZone
        if (!prevHot && currentHot) {
            return gate(hotZoneAnnouncement(current), nowMillis)
        }

        // Si la pantalla anterior estaba vacía/null y ahora hay contenido sin
        // hot zone: SCREEN_RESTORED. Solo notable si había un previous explícito.
        if (previous != null && previous.isEmpty) {
            return gate(
                announcement = ScreenChangeAnnouncement(
                    event = ScreenChangeEvent.SCREEN_RESTORED,
                    importance = ScreenChangeImportance.LOW,
                    semanticKey = "screen.change.restored",
                    spokenText = "Volví a tener lectura de la pantalla.",
                    reasonKey = "restored",
                    shouldAnnounce = true,
                    safeForSpeech = true,
                    cooldownMs = ScreenChangeAnnouncement.LOW_NOISE_COOLDOWN_MS
                ),
                nowMillis = nowMillis
            )
        }

        // Caso 4: packageName cambió.
        val prevPkg = previous?.packageName?.takeIf { it.isNotBlank() }
        val currentPkg = current.packageName?.takeIf { it.isNotBlank() }
        if (previous != null && prevPkg != currentPkg && currentPkg != null) {
            return gate(appChangeAnnouncement(current), nowMillis)
        }

        // Caso 5: formulario apareció (mismo package, antes sin editables, ahora sí)
        if (previous != null &&
            previous.editableFields.isEmpty() &&
            current.editableFields.isNotEmpty()
        ) {
            return gate(
                announcement = ScreenChangeAnnouncement(
                    event = ScreenChangeEvent.FORM_SCREEN_ENTERED,
                    importance = ScreenChangeImportance.NORMAL,
                    semanticKey = "screen.change.form",
                    spokenText = "Apareció un formulario. Podés pedirme " +
                        "\"qué hago ahora\" para orientarte.",
                    reasonKey = "form_appeared",
                    shouldAnnounce = true,
                    safeForSpeech = true
                ),
                nowMillis = nowMillis
            )
        }

        // Caso 6: chat/mensajería apareció (en el mismo package)
        if (previous != null &&
            !previous.signals.isMessagingApp &&
            current.signals.isMessagingApp
        ) {
            return gate(
                announcement = ScreenChangeAnnouncement(
                    event = ScreenChangeEvent.CHAT_SCREEN_ENTERED,
                    importance = ScreenChangeImportance.NORMAL,
                    semanticKey = "screen.change.chat",
                    spokenText = "Parece una pantalla de mensajes.",
                    reasonKey = "messaging",
                    shouldAnnounce = true,
                    safeForSpeech = true
                ),
                nowMillis = nowMillis
            )
        }

        // Caso 7: diálogo o alerta apareció
        if (previous != null && dialogAppeared(previous, current)) {
            return gate(
                announcement = ScreenChangeAnnouncement(
                    event = ScreenChangeEvent.DIALOG_OR_ALERT_APPEARED,
                    importance = ScreenChangeImportance.HIGH,
                    semanticKey = "screen.change.dialog",
                    spokenText = "Apareció una opción de confirmación.",
                    reasonKey = "dialog",
                    shouldAnnounce = true,
                    safeForSpeech = true
                ),
                nowMillis = nowMillis
            )
        }

        // Caso 8: cambio drástico de botones.
        if (previous != null && importantButtonsChanged(previous, current)) {
            return gate(
                announcement = ScreenChangeAnnouncement(
                    event = ScreenChangeEvent.IMPORTANT_BUTTONS_CHANGED,
                    importance = ScreenChangeImportance.LOW,
                    semanticKey = "screen.change.buttons",
                    spokenText = "Cambiaron las opciones disponibles.",
                    reasonKey = "buttons_changed",
                    shouldAnnounce = true,
                    safeForSpeech = true,
                    cooldownMs = ScreenChangeAnnouncement.LOW_NOISE_COOLDOWN_MS
                ),
                nowMillis = nowMillis
            )
        }

        return ScreenChangeAnnouncement.NONE
    }

    /**
     * Registra un anuncio efectivamente emitido en la memoria. El caller
     * llama acá DESPUÉS de hablar para que el cooldown empiece a correr.
     */
    fun rememberAnnounced(announcement: ScreenChangeAnnouncement, nowMillis: Long = clock()) {
        if (!announcement.shouldAnnounce) return
        memory.rememberAnnouncement(
            semanticKey = announcement.semanticKey,
            reasonKey = announcement.reasonKey,
            nowMillis = nowMillis
        )
        if (announcement.event == ScreenChangeEvent.APP_CHANGED) {
            // El reasonKey de APP_CHANGED contiene el package detectado.
            memory.rememberAppPackage(announcement.reasonKey)
        }
    }

    fun reset() {
        memory.reset()
    }

    private fun hotZoneAnnouncement(current: StructuredScreenSnapshot): ScreenChangeAnnouncement {
        val signals = current.signals
        return when {
            signals.hasPasswordField -> ScreenChangeAnnouncement(
                event = ScreenChangeEvent.PASSWORD_SCREEN_ENTERED,
                importance = ScreenChangeImportance.CRITICAL,
                semanticKey = "screen.change.hot_zone",
                spokenText = "Hay un campo de contraseña. No voy a decir su contenido.",
                reasonKey = "password",
                shouldAnnounce = true,
                safeForSpeech = true,
                cooldownMs = ScreenChangeAnnouncement.DEFAULT_COOLDOWN_MS
            )
            signals.isBankingApp || signals.hasPaymentOrTransferSignals -> ScreenChangeAnnouncement(
                event = ScreenChangeEvent.PAYMENT_OR_BANKING_SCREEN_ENTERED,
                importance = ScreenChangeImportance.CRITICAL,
                semanticKey = "screen.change.hot_zone",
                spokenText = "Esta parece una pantalla bancaria o de pago. " +
                    "No voy a leer datos sensibles.",
                reasonKey = "banking_or_payment",
                shouldAnnounce = true,
                safeForSpeech = true,
                cooldownMs = ScreenChangeAnnouncement.DEFAULT_COOLDOWN_MS
            )
            signals.hasVerificationCode -> ScreenChangeAnnouncement(
                event = ScreenChangeEvent.SENSITIVE_SCREEN_ENTERED,
                importance = ScreenChangeImportance.CRITICAL,
                semanticKey = "screen.change.hot_zone",
                spokenText = "Veo un código o verificación. No voy a leerlo en voz alta.",
                reasonKey = "verification_code",
                shouldAnnounce = true,
                safeForSpeech = true,
                cooldownMs = ScreenChangeAnnouncement.DEFAULT_COOLDOWN_MS
            )
            else -> ScreenChangeAnnouncement(
                event = ScreenChangeEvent.SENSITIVE_SCREEN_ENTERED,
                importance = ScreenChangeImportance.CRITICAL,
                semanticKey = "screen.change.hot_zone",
                spokenText = "Esta pantalla parece sensible. " +
                    "No voy a leer datos sensibles.",
                reasonKey = "sensitive",
                shouldAnnounce = true,
                safeForSpeech = true,
                cooldownMs = ScreenChangeAnnouncement.DEFAULT_COOLDOWN_MS
            )
        }
    }

    private fun appChangeAnnouncement(current: StructuredScreenSnapshot): ScreenChangeAnnouncement {
        val pkg = current.packageName?.takeIf { it.isNotBlank() }
        // No anunciar APP_CHANGED dos veces seguidas al mismo packageName.
        if (pkg != null && memory.lastAppPackage()?.equals(pkg, ignoreCase = true) == true) {
            return ScreenChangeAnnouncement.NONE
        }
        val friendly = appLabelForSpeech(current)
        val text = if (friendly != null) {
            "Cambiaste a $friendly."
        } else {
            "Cambiaste a otra app."
        }
        return ScreenChangeAnnouncement(
            event = ScreenChangeEvent.APP_CHANGED,
            importance = ScreenChangeImportance.NORMAL,
            semanticKey = "screen.change.app",
            spokenText = text,
            reasonKey = pkg,
            shouldAnnounce = true,
            safeForSpeech = true
        )
    }

    private fun gate(
        announcement: ScreenChangeAnnouncement,
        nowMillis: Long
    ): ScreenChangeAnnouncement {
        if (!announcement.shouldAnnounce) return announcement
        val allowed = memory.shouldAllow(
            semanticKey = announcement.semanticKey,
            importance = announcement.importance,
            reasonKey = announcement.reasonKey,
            cooldownMs = announcement.cooldownMs,
            nowMillis = nowMillis
        )
        return if (allowed) {
            announcement
        } else {
            // Devolvemos un announcement consistente pero `shouldAnnounce = false`
            // para que el caller pueda loguear/inspeccionar la decisión.
            announcement.copy(shouldAnnounce = false, safeForSpeech = announcement.safeForSpeech)
        }
    }

    private fun appLabelForSpeech(snapshot: StructuredScreenSnapshot): String? {
        val explicit = snapshot.appLabel?.trim()?.takeIf { it.isNotBlank() }
        if (explicit != null) return explicit.take(MAX_APP_LABEL_CHARS)
        val pkg = snapshot.packageName?.lowercase(Locale.ROOT).orEmpty()
        return when {
            "whatsapp" in pkg -> "WhatsApp"
            "settings" in pkg || "ajustes" in pkg -> "Ajustes"
            "chrome" in pkg || "browser" in pkg -> "el navegador"
            "ojoclaro" in pkg -> "Ojo Claro"
            else -> null
        }
    }

    private fun dialogAppeared(
        previous: StructuredScreenSnapshot,
        current: StructuredScreenSnapshot
    ): Boolean {
        val prevButtons = previous.buttons.map { it.lowercase(Locale.ROOT).trim() }.toSet()
        val currentButtons = current.buttons.map { it.lowercase(Locale.ROOT).trim() }.toSet()
        val newDialogButtons = currentButtons - prevButtons
        if (newDialogButtons.isEmpty()) return false
        return newDialogButtons.any { it in DIALOG_BUTTON_KEYWORDS } &&
            current.buttons.size <= MAX_DIALOG_BUTTONS
    }

    private fun importantButtonsChanged(
        previous: StructuredScreenSnapshot,
        current: StructuredScreenSnapshot
    ): Boolean {
        val prevSet = previous.buttons.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        val currentSet = current.buttons.map { it.trim() }.filter { it.isNotBlank() }.toSet()
        if (prevSet.isEmpty() && currentSet.isEmpty()) return false
        val intersection = prevSet.intersect(currentSet).size
        val union = prevSet.union(currentSet).size
        if (union == 0) return false
        val similarity = intersection.toDouble() / union.toDouble()
        return similarity < BUTTON_CHANGE_SIMILARITY_THRESHOLD
    }

    companion object {
        private const val MAX_APP_LABEL_CHARS = 30
        private const val MAX_DIALOG_BUTTONS = 5

        // Si la similitud (intersection / union) entre sets de botones es
        // menor a este umbral, consideramos "cambio drástico". Calibrado
        // para no disparar por cambios menores (un botón nuevo entre cinco).
        private const val BUTTON_CHANGE_SIMILARITY_THRESHOLD = 0.4

        private val DIALOG_BUTTON_KEYWORDS: Set<String> = setOf(
            "aceptar",
            "cancelar",
            "permitir",
            "denegar",
            "allow",
            "deny",
            "ok",
            "confirmar",
            "continuar",
            "rechazar",
            "no permitir",
            "no, gracias",
            "si, permitir"
        )
    }
}
