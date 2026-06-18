package com.ojoclaro.android.agent.runtime.screen

import java.text.Normalizer

/**
 * Comando determinista de navegación segura sobre la pantalla visible.
 *
 * Fase 2A: solo movimientos NO destructivos y reversibles:
 *  - Scroll hacia abajo / arriba sobre un contenedor scrollable visible.
 *  - Back global (volver a la pantalla anterior).
 *
 * NUNCA representa enviar, borrar, llamar, compartir, comprar ni tocar
 * "Enviar". Esas acciones no existen en esta capa por diseño.
 */
sealed class ScreenNavigationCommand {
    object ScrollDown : ScreenNavigationCommand()
    object ScrollUp : ScreenNavigationCommand()
    object Back : ScreenNavigationCommand()
}

/**
 * Parser puro (sin Android) de comandos de navegación.
 *
 * Reglas estrictas:
 *  - Set fijo de frases, set-membership exacta tras normalizar (acentos,
 *    puntuación, espacios). Evita falsos positivos contra otros comandos.
 *  - "volvé arriba" => ScrollUp; "volvé" / "atrás" => Back. La membership
 *    exacta resuelve la ambigüedad sin heurísticas frágiles.
 *  - Devuelve null si no reconoce: el caller sigue su flujo normal.
 */
object ScreenNavigationCommandParser {

    fun parse(rawText: String): ScreenNavigationCommand? {
        val key = normalize(rawText)
        if (key.isBlank()) return null
        return when (key) {
            in SCROLL_DOWN -> ScreenNavigationCommand.ScrollDown
            in SCROLL_UP -> ScreenNavigationCommand.ScrollUp
            in BACK -> ScreenNavigationCommand.Back
            else -> null
        }
    }

    private val SCROLL_DOWN: Set<String> = setOf(
        "baja",
        "bajar",
        "baja la pantalla",
        "baja un poco",
        "bajame",
        "bajame la pantalla",
        "mas abajo",
        "un poco mas abajo",
        "scroll abajo",
        "desplaza hacia abajo",
        "desplazate hacia abajo",
        "desliza hacia abajo",
        "desliza para abajo",
        "segui bajando",
        // Sprint WhatsApp: "seguí leyendo"/"leé más abajo"/"scrolleá" = avanzar.
        "segui leyendo",
        "seguir leyendo",
        "scrollea",
        "scrolea",
        "scrollea para abajo",
        "lee mas abajo",
        "leer mas abajo",
        "leeme mas abajo"
    )

    private val SCROLL_UP: Set<String> = setOf(
        "sube",
        "subi",
        "subir",
        "sube la pantalla",
        "subi la pantalla",
        "subi un poco",
        "subime",
        "mas arriba",
        "un poco mas arriba",
        "scroll arriba",
        "desplaza hacia arriba",
        "desplazate hacia arriba",
        "desliza hacia arriba",
        "desliza para arriba",
        "volve arriba",
        "segui subiendo",
        // Sprint WhatsApp: "leé más arriba"/"mensajes anteriores" = retroceder.
        "lee mas arriba",
        "leer mas arriba",
        "leeme mas arriba",
        "scrollea para arriba",
        "mensajes anteriores",
        "busca mensajes anteriores",
        "buscame los mensajes anteriores",
        "buscame mensajes anteriores"
    )

    private val BACK: Set<String> = setOf(
        "volver",
        "volve",
        "atras",
        "ir atras",
        "anda atras",
        "andate atras",
        "volve atras",
        "volver atras",
        "pantalla anterior",
        "regresar",
        "retroceder",
        "back",
        "boton atras",
        "boton de atras",
        // Anxiety hardening: "salí (de acá)" como volver atrás reversible.
        "sali",
        "salir",
        "salite",
        "sacame de aca"
    )

    private fun normalize(text: String): String {
        val lower = text.lowercase()
        val stripped = Normalizer.normalize(lower, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return stripped
            .replace(Regex("[¿?¡!.,;:]"), "")
            .replace(Regex("\\s+"), " ")
            .trim()
    }
}
