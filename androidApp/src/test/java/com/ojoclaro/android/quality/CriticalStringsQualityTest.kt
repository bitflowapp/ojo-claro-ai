package com.ojoclaro.android.quality

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class CriticalStringsQualityTest {

    @Test
    fun criticalTextFilesDoNotContainCommonMojibakeMarkers() {
        val root = locateRepoRoot()
        val rootsToScan = listOf(
            File(root, "androidApp/src/main"),
            File(root, "androidApp/src/test"),
            File(root, "shared/src"),
            File(root, "tools/ojo_claro_ai_proxy"),
            File(root, "docs"),
            File(root, "scripts")
        )
        val markers = listOf("\u00c3", "\u00c2", "\u00e2", "\ufffd")
        val affected = rootsToScan
            .flatMap { it.walkTextFiles() }
            .filter { file -> markers.any { marker -> file.readText().contains(marker) } }
            .map { it.relativeTo(root).invariantSeparatorsPath }

        assertFalse(
            affected.isNotEmpty(),
            "Text files contain likely mojibake markers: ${affected.joinToString()}"
        )
    }

    @Test
    fun androidSourcesDoNotContainOpenAiSecrets() {
        val root = locateRepoRoot()
        val apiKeyEnvName = "OPENAI" + "_API" + "_KEY"
        val keyPrefix = "sk" + "-"
        val filesToScan = listOf(
            File(root, "androidApp/src/main"),
            File(root, "androidApp/src/test"),
            File(root, "androidApp/build.gradle.kts")
        ).flatMap { it.walkTextFiles() }
        val affected = filesToScan
            .filter { file ->
                val text = file.readText()
                text.contains(apiKeyEnvName) || text.contains(keyPrefix)
            }
            .map { it.relativeTo(root).invariantSeparatorsPath }

        assertFalse(
            affected.isNotEmpty(),
            "Android files must not contain AI service secrets or key-like tokens: ${affected.joinToString()}"
        )
    }

    private fun File.walkTextFiles(): List<File> {
        if (!exists()) return emptyList()
        if (isFile) return listOf(this)
        val allowedExtensions = setOf("kt", "kts", "md", "mjs", "js", "ps1", "xml", "json", "html", "css")
        return walkTopDown()
            .filter { file ->
                file.isFile &&
                    file.extension.lowercase() in allowedExtensions &&
                    !file.invariantSeparatorsPath.contains("/build/")
            }
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
