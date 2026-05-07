package com.ojoclaro.android.ai

import com.ojoclaro.android.consent.ConsentLevel
import com.ojoclaro.android.model.AppState

/**
 * Contexto seguro para procesar una acción del agente.
 *
 * Regla principal:
 * este objeto NO debe usarse para persistir datos sensibles.
 * Solo vive durante una acción puntual del usuario.
 */
data class AiContext(
    val rawCommand: String,

    // Texto local detectado por OCR. No debe guardarse por defecto.
    val ocrText: String? = null,

    // Texto visible leído por AccessibilityService bajo pedido del usuario.
    // No debe guardarse ni enviarse al backend por defecto.
    val visibleScreenText: String? = null,

    // App objetivo, por ejemplo WhatsApp o navegador.
    val targetAppPackage: String? = null,

    // Datos de intención para acciones externas.
    val targetContact: String? = null,
    val message: String? = null,

    // Estado actual del agente.
    val appState: AppState = AppState.IDLE,

    // Preferencias del usuario.
    val locale: String = "es-AR",
    val preferShortAnswers: Boolean = false,

    // Seguridad cloud.
    val allowCloud: Boolean = false,
    val safetyMode: Boolean = true,

    // Consentimiento de la acción actual.
    val consentGranted: Boolean = false,
    val consentLevel: ConsentLevel = ConsentLevel.NONE,

    // Memoria segura: solo resúmenes no sensibles.
    val memorySummaries: List<String> = emptyList(),

    // Riesgos detectados previamente por RiskDetector.
    val riskSummaries: List<String> = emptyList(),

    // Si true, la IA/proveedor debe evitar repetir datos sensibles completos.
    val shouldRedactSensitiveText: Boolean = true,

    // Placeholder futuro. No usar en producción sin consentimiento explícito.
    // No loguear, no persistir y no enviar a cloud salvo allowCloud + consentimiento explícito.
    val imageBase64: String? = null
)
