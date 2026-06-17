package com.ojoclaro.android.agent.runtime.whatsapp

/**
 * Parser determinista para comandos contextuales sobre la pantalla visible.
 *
 * V1 solo soporta abrir un chat visible de WhatsApp. No interpreta enviar,
 * llamar, borrar, archivar ni acciones sensibles.
 */
object VisibleScreenCommandParser {

    fun parse(rawText: String): VisibleScreenCommand? {
        val text = rawText.trim()
        if (text.isBlank()) return null

        for (regex in OPEN_VISIBLE_CHAT_REGEXES) {
            val match = regex.matchEntire(text) ?: continue
            val target = cleanTargetName(match.groupValues[1])
            if (target == null) return null
            return VisibleScreenCommand.OpenVisibleChat(targetName = target)
        }

        return null
    }

    private fun cleanTargetName(rawValue: String): String? {
        val cleaned = rawValue
            .replace(TRAILING_POLITE_WORDS, "")
            .trim()
            .trim('.', ',', ';', ':', '?', '!', '¿', '¡')
            .replace(Regex("\\s+"), " ")

        if (cleaned.length < MIN_TARGET_CHARS) return null
        if (cleaned.length > MAX_TARGET_CHARS) return null
        if (!cleaned.any { it.isLetter() }) return null
        if (WhatsAppVisibleChatMatcher.isSensitiveActionLabel(cleaned)) return null

        val normalized = WhatsAppVisibleChatMatcher.normalizeName(cleaned)
        if (normalized in BLOCKED_TARGETS) return null
        if (normalized.split(" ").any { it in BLOCKED_TARGET_TOKENS }) return null

        return cleaned
    }

    private const val MIN_TARGET_CHARS = 2
    private const val MAX_TARGET_CHARS = 60

    private val TRAILING_POLITE_WORDS = Regex(
        "\\s+(?:por favor|gracias)\\s*$",
        RegexOption.IGNORE_CASE
    )

    private val OPEN_VISIBLE_CHAT_REGEXES = listOf(
        Regex(
            "^\\s*(?:estela\\s*,?\\s*)?(?:ahora\\s+)?(?:toc[aá]|tocar|seleccion[aá]|seleccionar)\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^\\s*(?:estela\\s*,?\\s*)?(?:ahora\\s+)?(?:abr[ií]|abrir|abre|toc[aá]|tocar|seleccion[aá]|seleccionar|entr[aá]|entrar)\\s+" +
                "(?:a\\s+)?(?:el\\s+|la\\s+)?(?:chat|contacto|conversaci[oó]n)?\\s*" +
                "(?:que\\s+ves(?:\\s+en\\s+pantalla)?|visible|en\\s+pantalla)?\\s*" +
                "(?:de|con|a)?\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        ),
        Regex(
            "^\\s*(?:estela\\s*,?\\s*)?(?:ahora\\s+)?(?:abr[ií]|abrir|abre)\\s+" +
                "(?:el\\s+)?chat\\s+(?:de|con)\\s+(.+?)\\s*$",
            RegexOption.IGNORE_CASE
        )
    )

    private val BLOCKED_TARGETS = setOf(
        "ese",
        "esa",
        "eso",
        "este",
        "esta",
        "enviar",
        "llamar",
        "borrar",
        "archivar",
        "pagar"
    )

    private val BLOCKED_TARGET_TOKENS = setOf(
        "enviar",
        "send",
        "llamar",
        "call",
        "borrar",
        "eliminar",
        "delete",
        "archivar",
        "archive",
        "pagar",
        "pay"
    )
}

sealed class VisibleScreenCommand {
    data class OpenVisibleChat(val targetName: String) : VisibleScreenCommand()
}
