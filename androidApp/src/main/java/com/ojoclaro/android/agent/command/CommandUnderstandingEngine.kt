package com.ojoclaro.android.agent.command

class CommandUnderstandingEngine {

    fun parse(rawInput: String): ParsedCommand {
        val normalizedInput = CommandNormalizer.normalize(rawInput)
        val sensitiveReason = findSensitiveReason(normalizedInput)

        if (normalizedInput.isBlank()) {
            return ParsedCommand(
                rawInput = rawInput,
                normalizedInput = normalizedInput,
                intent = CommandIntent.UNKNOWN,
                confidence = CommandConfidence.LOW,
                debugReason = "blank_input"
            )
        }

        val parsed = parseStructured(rawInput, normalizedInput)
        val sensitivityDebug = sensitiveReason?.let { "sensitive:$it" }

        return parsed.copy(
            isPotentiallySensitive = sensitiveReason != null,
            debugReason = listOfNotNull(parsed.debugReason, sensitivityDebug)
                .filter { it.isNotBlank() }
                .joinToString(separator = "; ")
        )
    }

    private fun parseStructured(rawInput: String, normalizedInput: String): ParsedCommand {
        parsePrepareMessage(rawInput, normalizedInput)?.let { return it }
        parseOpenApp(rawInput, normalizedInput)?.let { return it }

        exactRules.firstOrNull { rule -> normalizedInput in rule.phrases }?.let { rule ->
            return ParsedCommand(
                rawInput = rawInput,
                normalizedInput = normalizedInput,
                intent = rule.intent,
                confidence = rule.confidence,
                requiresContext = rule.intent.requiresContext(),
                debugReason = rule.debugReason
            )
        }

        return ParsedCommand(
            rawInput = rawInput,
            normalizedInput = normalizedInput,
            intent = CommandIntent.UNKNOWN,
            confidence = CommandConfidence.LOW,
            debugReason = "no_rule_matched"
        )
    }

    private fun parseOpenApp(rawInput: String, normalizedInput: String): ParsedCommand? {
        val tokens = CommandNormalizer.tokenize(rawInput)
        if (tokens.isEmpty()) return null
        if (tokens.first().normalized !in openAppVerbs) return null

        val appTokens = tokens
            .drop(1)
            .dropWhile { it.normalized in articleTokens }

        if (appTokens.isEmpty()) return null

        val appName = CommandNormalizer.cleanSlotValue(appTokens)
        if (appName.isBlank()) return null

        return ParsedCommand(
            rawInput = rawInput,
            normalizedInput = normalizedInput,
            intent = CommandIntent.OPEN_APP,
            confidence = CommandConfidence.HIGH,
            slots = listOf(
                CommandSlot(
                    name = CommandSlotName.APP_NAME,
                    value = appName,
                    confidence = CommandConfidence.HIGH
                )
            ),
            requiresContext = false,
            debugReason = "open_app_verb_with_app_name"
        )
    }

    private fun parsePrepareMessage(rawInput: String, normalizedInput: String): ParsedCommand? {
        val tokens = CommandNormalizer.tokenize(rawInput)
        if (tokens.isEmpty()) return null

        val contactStart = when {
            tokens.first().normalized in prepareMessageVerbs ->
                contactStartAfterPrepareMessage(tokens)

            tokens.first().normalized in directMessageVerbs ->
                contactStartAfterDirectMessageVerb(tokens)

            else -> null
        } ?: return null

        val contactTokens = tokens
            .drop(contactStart)
            .takeWhile { it.normalized !in contactStopWords }

        if (contactTokens.isEmpty()) return null

        val contactName = CommandNormalizer.cleanSlotValue(contactTokens)
        if (contactName.isBlank()) return null

        return ParsedCommand(
            rawInput = rawInput,
            normalizedInput = normalizedInput,
            intent = CommandIntent.PREPARE_MESSAGE,
            confidence = CommandConfidence.HIGH,
            slots = listOf(
                CommandSlot(
                    name = CommandSlotName.CONTACT_NAME,
                    value = contactName,
                    confidence = CommandConfidence.HIGH
                )
            ),
            requiresContext = false,
            debugReason = "message_verb_with_contact_name"
        )
    }

    private fun contactStartAfterPrepareMessage(tokens: List<CommandToken>): Int? {
        var index = 1
        if (tokens.getOrNull(index)?.normalized in articleTokens) index += 1
        if (tokens.getOrNull(index)?.normalized != "mensaje") return null
        index += 1
        if (tokens.getOrNull(index)?.normalized !in contactPrepositions) return null
        return index + 1
    }

    private fun contactStartAfterDirectMessageVerb(tokens: List<CommandToken>): Int? {
        var index = 1
        if (tokens.getOrNull(index)?.normalized in articleTokens) index += 1
        if (tokens.getOrNull(index)?.normalized == "mensaje") index += 1
        if (tokens.getOrNull(index)?.normalized !in contactPrepositions) return null
        return index + 1
    }

    private fun findSensitiveReason(normalizedInput: String): String? {
        if (normalizedInput.isBlank()) return null

        return dangerousPhraseGroups.firstNotNullOfOrNull { group ->
            group.phrases.firstOrNull { phrase ->
                normalizedInput.containsNormalizedPhrase(phrase)
            }?.let { matchedPhrase -> "${group.reason}:$matchedPhrase" }
        }
    }

    private fun String.containsNormalizedPhrase(phrase: String): Boolean {
        val paddedInput = " $this "
        val paddedPhrase = " $phrase "
        return paddedInput.contains(paddedPhrase)
    }

    private fun CommandIntent.requiresContext(): Boolean =
        this in contextRequiredIntents

    private data class ExactIntentRule(
        val intent: CommandIntent,
        val confidence: CommandConfidence,
        val phrases: Set<String>,
        val debugReason: String
    )

    private data class DangerousPhraseGroup(
        val reason: String,
        val phrases: Set<String>
    )

    private companion object {
        private val contextRequiredIntents = setOf(
            CommandIntent.READ_SCREEN,
            CommandIntent.SUMMARIZE_SCREEN,
            CommandIntent.EXPLAIN_SCREEN,
            CommandIntent.CONFIRM,
            CommandIntent.CANCEL,
            CommandIntent.REPEAT,
            CommandIntent.SHORTER,
            CommandIntent.MORE_DETAIL,
            CommandIntent.RISK_CHECK
        )

        private val exactRules = listOf(
            ExactIntentRule(
                intent = CommandIntent.READ_SCREEN,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "lee la pantalla",
                    "lee esto",
                    "que dice aca",
                    "leeme lo que aparece",
                    "leer pantalla"
                ),
                debugReason = "read_screen_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.SUMMARIZE_SCREEN,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "resumime la pantalla",
                    "haceme un resumen",
                    "decime lo importante",
                    "que esta pasando aca"
                ),
                debugReason = "summarize_screen_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.EXPLAIN_SCREEN,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "explicame esta pantalla",
                    "que tengo que hacer aca",
                    "que boton tengo que tocar",
                    "ayudame con esto"
                ),
                debugReason = "explain_screen_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.CONFIRM,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "confirmar",
                    "si confirmo",
                    "dale",
                    "continuar",
                    "acepto"
                ),
                debugReason = "confirm_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.CANCEL,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "cancelar",
                    "no",
                    "para",
                    "detener",
                    "olvidalo"
                ),
                debugReason = "cancel_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.REPEAT,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "repeti",
                    "decilo de nuevo",
                    "otra vez"
                ),
                debugReason = "repeat_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.SHORTER,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "mas corto",
                    "resumilo",
                    "decilo mas simple"
                ),
                debugReason = "shorter_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.MORE_DETAIL,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "mas detalle",
                    "explicame mejor",
                    "amplia"
                ),
                debugReason = "more_detail_phrase"
            ),
            ExactIntentRule(
                intent = CommandIntent.RISK_CHECK,
                confidence = CommandConfidence.HIGH,
                phrases = setOf(
                    "hay algo peligroso aca",
                    "esto es seguro",
                    "me pueden estafar",
                    "revisa si hay riesgo"
                ),
                debugReason = "risk_check_phrase"
            )
        )

        private val openAppVerbs = setOf(
            "abri",
            "abrir",
            "abre",
            "abrime",
            "abreme"
        )

        private val prepareMessageVerbs = setOf(
            "prepara",
            "preparar"
        )

        private val directMessageVerbs = setOf(
            "escribile",
            "escribirle",
            "mandale",
            "mandarle",
            "enviale",
            "enviarle",
            "envia",
            "enviar"
        )

        private val articleTokens = setOf("un", "una", "el", "la", "los", "las")
        private val contactPrepositions = setOf("a", "para")
        private val contactStopWords = setOf("que")

        private val dangerousPhraseGroups = listOf(
            DangerousPhraseGroup(
                reason = "dangerous_send_now",
                phrases = setOf(
                    "enviar ya",
                    "envia ya",
                    "envialo ya",
                    "mandar ya",
                    "manda ya",
                    "mandalo ya"
                )
            ),
            DangerousPhraseGroup(
                reason = "dangerous_financial",
                phrases = setOf(
                    "pagar",
                    "paga",
                    "pagame",
                    "pagale",
                    "transferir",
                    "transferi",
                    "transferime",
                    "transferile",
                    "comprar",
                    "compra",
                    "comprame",
                    "comprale"
                )
            ),
            DangerousPhraseGroup(
                reason = "dangerous_destructive",
                phrases = setOf(
                    "borrar",
                    "borra",
                    "borrame",
                    "borralo",
                    "borrala",
                    "eliminar",
                    "elimina",
                    "eliminalo",
                    "eliminala",
                    "cerrar cuenta",
                    "cerra cuenta",
                    "cerrame la cuenta"
                )
            ),
            DangerousPhraseGroup(
                reason = "dangerous_call",
                phrases = setOf(
                    "llamar",
                    "llama",
                    "llamame",
                    "llamalo",
                    "llamala"
                )
            ),
            DangerousPhraseGroup(
                reason = "dangerous_purchase_acceptance",
                phrases = setOf(
                    "aceptar compra",
                    "acepta compra"
                )
            )
        )
    }
}
