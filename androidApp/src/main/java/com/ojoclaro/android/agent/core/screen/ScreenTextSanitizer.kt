package com.ojoclaro.android.agent.core.screen

import com.ojoclaro.android.memory.MemoryPolicy
import java.text.Normalizer
import java.util.Locale

/**
 * Sanitizer determinista que redacta texto visible de pantalla antes de que
 * salga del proceso de captura.
 *
 * Diferencias con [com.ojoclaro.android.privacy.PrivacyGuard.redactSensitiveText]:
 *  - Acá los placeholders son los que el contrato del paquete 3 pide
 *    (`[CONTRASEÑA OCULTA]`, `[CÓDIGO OCULTO]`, `[NÚMERO SENSIBLE OCULTO]`,
 *    `[DATO PRIVADO OCULTO]`), no las frases del PrivacyGuard ("[contraseña:
 *    contenido omitido]"). El sanitizer es el contrato visible del Structured
 *    Screen Snapshot.
 *  - Aplica redacción línea por línea, devolviendo lista de líneas saneadas
 *    listas para mostrar/leer.
 *  - No fall-through silencioso: si una línea entera entra en el matcher de
 *    una categoría, la línea se reemplaza por el placeholder.
 *  - No mantiene estado entre invocaciones.
 *
 * NO se usa para `MESSAGE_TEXT` de WhatsApp (eso lo cubre PrivacyGuard). Acá
 * el objeto es texto crudo de pantalla — etiquetas, valores visibles, OCR.
 */
object ScreenTextSanitizer {

    const val PASSWORD_PLACEHOLDER = "[CONTRASEÑA OCULTA]"
    const val CODE_PLACEHOLDER = "[CÓDIGO OCULTO]"
    const val SENSITIVE_NUMBER_PLACEHOLDER = "[NÚMERO SENSIBLE OCULTO]"
    const val PRIVATE_DATA_PLACEHOLDER = "[DATO PRIVADO OCULTO]"

    private const val MIN_SECRET_LENGTH = 16
    private const val MAX_LINE_CHARS = 200

    private val diacriticRegex = Regex("\\p{Mn}+")

    private val passwordAssignmentRegex = Regex(
        "\\b(?:mi\\s+)?(?:contrasena|password|clave|pin)\\s*(?:es|:|=)?\\s*\\S*",
        RegexOption.IGNORE_CASE
    )
    private val passwordLabelRegex = Regex(
        "\\b(?:contrasena|password|clave|pin)\\b",
        RegexOption.IGNORE_CASE
    )
    private val verificationKeywordRegex = Regex(
        "\\b(?:codigo|cod|verificacion|2fa|otp)\\b"
    )
    private val verificationDigitsRegex = Regex("\\b\\d{4,8}\\b")
    private val cardCandidateRegex = Regex("(?:\\d[ -]?){13,19}")
    private val longDocumentNumberRegex = Regex("\\b\\d{7,}\\b")

    /**
     * Sanitiza una línea individual. Aplica las redacciones en orden
     * descendente de severidad: contraseña > código > tarjeta > documento
     * > secreto largo. La primera que matchea reemplaza la línea entera.
     *
     * Limita a [MAX_LINE_CHARS] caracteres después de redactar.
     */
    fun sanitizeLine(line: String): String {
        if (line.isBlank()) return line.trim()
        val normalized = normalize(line)

        // Password: si la línea menciona "password/contrasena/clave/pin"
        // como label o asignación, redactamos la línea entera. El usuario no
        // quiere oír "mi password es secreto" leído por TTS.
        if (passwordAssignmentRegex.containsMatchIn(normalized)) return PASSWORD_PLACEHOLDER
        if (passwordLabelRegex.containsMatchIn(normalized) && line.contains(":")) {
            return PASSWORD_PLACEHOLDER
        }

        // Código de verificación: keyword + dígitos cerca.
        if (verificationKeywordRegex.containsMatchIn(normalized) &&
            verificationDigitsRegex.containsMatchIn(line)
        ) {
            return CODE_PLACEHOLDER
        }

        var output = line
        // Tarjetas: 13-19 dígitos seguidos (con guiones/espacios).
        output = cardCandidateRegex.replace(output) { match ->
            val digits = match.value.filter(Char::isDigit)
            if (digits.length in 13..19) SENSITIVE_NUMBER_PLACEHOLDER else match.value
        }

        // Documento/largo número (DNI/CUIL/CUIT) — si quedan dígitos largos
        // sueltos. Aplica solo a runs ≥ 7 dígitos para no marcar
        // teléfonos cortos.
        output = longDocumentNumberRegex.replace(output) { match ->
            if (match.value.length >= 7) SENSITIVE_NUMBER_PLACEHOLDER else match.value
        }

        // Heurística secreto largo (token API, hash) — palabra sin espacios
        // con dígitos + letras y ≥ 16 chars.
        output = output.split(' ').joinToString(" ") { word ->
            if (looksLikeSecret(word)) PRIVATE_DATA_PLACEHOLDER else word
        }

        return output.take(MAX_LINE_CHARS).trim()
    }

    fun sanitizeLines(lines: List<String>): List<String> =
        lines.map { sanitizeLine(it) }.filter { it.isNotBlank() }

    /**
     * Sanitiza texto multi-línea, partiendo por '\n' y descartando líneas
     * que quedan vacías. La salida preserva el orden original.
     */
    fun sanitizeText(text: String): List<String> {
        if (text.isBlank()) return emptyList()
        return sanitizeLines(text.lineSequence().toList())
    }

    private fun looksLikeSecret(word: String): Boolean {
        val trimmed = word.trim()
        if (trimmed.length < MIN_SECRET_LENGTH) return false
        if (trimmed.contains(' ')) return false
        val digits = trimmed.count { it.isDigit() }
        val letters = trimmed.count { it.isLetter() }
        return digits > 0 && letters > 0
    }

    private fun normalize(text: String): String {
        val lower = text.lowercase(Locale("es", "AR"))
        return Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(diacriticRegex, "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    /**
     * Detecta financial-sensitive directamente, reutilizando [MemoryPolicy]
     * para mantener consistencia con el resto de la app.
     */
    fun looksFinanciallySensitive(text: String): Boolean {
        if (text.isBlank()) return false
        return MemoryPolicy.containsCardLikeNumber(text)
    }
}
