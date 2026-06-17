package com.ojoclaro.android.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

/**
 * Scan estatico para garantizar AI Experience Polish v1.
 *
 * Reglas:
 *  - Ninguna fuente bajo androidApp/src/main puede contener strings al usuario
 *    como "No estoy usando la IA", "No uso la IA", "IA flexible", "probá decirlo
 *    más simple" o referencias a "proxy" como label visible.
 *  - El test ignora el campo `safetyNotes` (debug interno) y este propio archivo
 *    de quality, pero NO ignora strings de UI/voz.
 */
class AiCopyPolicyTest {

    private val forbiddenPhrases: List<String> = listOf(
        "No estoy usando la IA",
        "No uso la IA",
        "IA flexible",
        "Probá decirlo más simple",
        "Proba decirlo mas simple"
    )

    /**
     * Archivos que intencionalmente nombran las frases prohibidas para:
     *  - documentar la regla en comentarios.
     *  - listarlas en un detector de leaks (DEBUG_TOKENS).
     * No son user-facing copy. Estos archivos quedan excluidos del scan.
     */
    private val defensiveFiles: Set<String> = setOf(
        "androidApp/src/main/java/com/ojoclaro/android/llm/SafeAiFallbackCopy.kt",
        "androidApp/src/main/java/com/ojoclaro/android/llm/SafeAiFallbackGuard.kt"
    )

    @Test
    fun mainSourcesContainNoForbiddenAiDebugPhrases() {
        val root = locateRepoRoot()
        val mainSources = File(root, "androidApp/src/main")
        val offenders = mainSources.walkKotlinFiles()
            .filter { file ->
                file.relativeTo(root).invariantSeparatorsPath !in defensiveFiles
            }
            .flatMap { file ->
                val text = file.readText()
                val relevantLines = text.lines().filter { line ->
                    // Skip comment-only lines: kdoc/star comments and // comments.
                    val trimmed = line.trim()
                    !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                }
                val joinedCode = relevantLines.joinToString("\n")
                forbiddenPhrases.mapNotNull { phrase ->
                    if (joinedCode.contains(phrase, ignoreCase = true)) {
                        "${file.relativeTo(root).invariantSeparatorsPath} :: \"$phrase\""
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertFalse(
            offenders.isNotEmpty(),
            "Forbidden AI debug copy leaked into user-facing sources:\n  ${offenders.joinToString("\n  ")}"
        )
    }

    @Test
    fun mainUiSourcesDoNotShowProxyLabelToUser() {
        val root = locateRepoRoot()
        val uiHome = File(root, "androidApp/src/main/java/com/ojoclaro/android/ui/home")
        val offenders = uiHome.walkKotlinFiles()
            .flatMap { file ->
                val text = file.readText()
                val phrases = listOf("IA flexible/proxy", "proxy: ", "Proxy LAN")
                phrases.mapNotNull { phrase ->
                    if (text.contains(phrase, ignoreCase = true)) {
                        "${file.relativeTo(root).invariantSeparatorsPath} :: \"$phrase\""
                    } else {
                        null
                    }
                }
            }
            .toList()
        assertFalse(
            offenders.isNotEmpty(),
            "UI surface should not expose 'proxy' as a user-visible label:\n  ${offenders.joinToString("\n  ")}"
        )
    }

    @Test
    fun mainSourcesDoNotClaimAutomaticSendOrPrivateDelivery() {
        val root = locateRepoRoot()
        val mainSources = File(root, "androidApp/src/main")
        val forbiddenDeliveryClaims = listOf(
            "mensaje enviado",
            "foto enviada",
            "ubicacion enviada",
            "ubicación enviada",
            "enviado automaticamente",
            "enviado automáticamente",
            "envie el mensaje",
            "envié el mensaje"
        )
        val offenders = mainSources.walkKotlinFiles()
            .flatMap { file ->
                val codeOnly = file.readText()
                    .lines()
                    .filter { line ->
                        val trimmed = line.trim()
                        !trimmed.startsWith("*") && !trimmed.startsWith("//") && !trimmed.startsWith("/*")
                    }
                    .joinToString("\n")
                forbiddenDeliveryClaims.mapNotNull { phrase ->
                    if (codeOnly.contains(phrase, ignoreCase = true)) {
                        "${file.relativeTo(root).invariantSeparatorsPath} :: \"$phrase\""
                    } else {
                        null
                    }
                }
            }
            .toList()

        assertFalse(
            offenders.isNotEmpty(),
            "User-facing copy must not claim auto-send/private delivery:\n  ${offenders.joinToString("\n  ")}"
        )
    }

    private fun File.walkKotlinFiles(): List<File> {
        if (!exists()) return emptyList()
        return walkTopDown()
            .filter { it.isFile && it.extension == "kt" && !it.invariantSeparatorsPath.contains("/build/") }
            .toList()
    }

    private fun locateRepoRoot(): File {
        var current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (true) {
            if (File(current, "settings.gradle.kts").exists()) return current
            val parent = current.parentFile ?: break
            current = parent
        }
        return File(".").absoluteFile
    }
}
