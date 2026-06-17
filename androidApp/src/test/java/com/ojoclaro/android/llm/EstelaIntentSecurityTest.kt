package com.ojoclaro.android.llm

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class EstelaIntentSecurityTest {

    @Test
    fun androidSourcesDoNotContainOpenAiKeyMaterial() {
        val root = locateRepoRoot()
        val secretEnvName = "OPENAI" + "_API" + "_KEY"
        val keyPrefix = "sk" + "-"
        val files = listOf(
            File(root, "androidApp/src/main"),
            File(root, "androidApp/src/test")
        ).flatMap { directory -> directory.walkTextFiles() }

        val affected = files
            .filter { file ->
                val text = file.readText()
                text.contains(secretEnvName) || text.contains(keyPrefix)
            }
            .map { file -> file.relativeTo(root).invariantSeparatorsPath }

        assertFalse(
            affected.isNotEmpty(),
            "Android files must not contain OpenAI key material: ${affected.joinToString()}"
        )
    }

    private fun File.walkTextFiles(): List<File> {
        if (!exists()) return emptyList()
        if (isFile) return listOf(this)
        val allowedExtensions = setOf("kt", "kts", "xml", "json", "md")
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
