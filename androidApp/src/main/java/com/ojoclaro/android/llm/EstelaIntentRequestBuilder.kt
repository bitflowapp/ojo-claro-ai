package com.ojoclaro.android.llm

import ai.ojoclaro.resolver.InstalledAppResolver
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.ojoclaro.android.agent.AgentConversationManager

class EstelaIntentRequestBuilder(
    private val installedAppsProvider: () -> List<String> = { emptyList() },
    private val memoryContactsProvider: () -> List<String> = { emptyList() },
    private val activeAppProvider: () -> String? = { null },
    private val permissionsProvider: () -> Map<String, Boolean> = { emptyMap() }
) {

    fun build(
        userText: String,
        conversationManager: AgentConversationManager
    ): EstelaIntentRequest {
        val snapshot = conversationManager.llmSnapshot()
        return EstelaIntentRequest(
            // Barrera de egreso: sanitizar SIEMPRE en el borde (defensa H1).
            userText = LlmInputSanitizer.sanitize(userText.trim()),
            conversationState = snapshot.conversationState,
            pendingAction = snapshot.pendingAction?.let { pending ->
                EstelaPendingAction(
                    intent = pending.intent,
                    params = pending.params
                )
            },
            installedApps = installedAppsProvider().normalizedStrings(),
            memoryContacts = memoryContactsProvider().normalizedStrings(),
            activeApp = activeAppProvider()?.trim()?.takeIf(String::isNotEmpty),
            permissionsGranted = permissionsProvider()
        )
    }

    private fun List<String>.normalizedStrings(): List<String> =
        map { value -> value.trim() }
            .filter { value -> value.isNotEmpty() }
            .distinct()

    companion object {
        fun fromAndroid(
            context: Context,
            memoryContactsProvider: () -> List<String> = { emptyList() },
            activeAppProvider: () -> String? = { null }
        ): EstelaIntentRequestBuilder {
            val appContext = context.applicationContext
            return EstelaIntentRequestBuilder(
                installedAppsProvider = { InstalledAppResolver.getInstalledAppKeys(appContext) },
                memoryContactsProvider = memoryContactsProvider,
                activeAppProvider = activeAppProvider,
                permissionsProvider = { appContext.estelaPermissions() }
            )
        }

        private fun Context.estelaPermissions(): Map<String, Boolean> =
            mapOf(
                "fine_location" to hasPermission(Manifest.permission.ACCESS_FINE_LOCATION),
                "camera" to hasPermission(Manifest.permission.CAMERA)
            )

        private fun Context.hasPermission(permission: String): Boolean =
            checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
    }
}
