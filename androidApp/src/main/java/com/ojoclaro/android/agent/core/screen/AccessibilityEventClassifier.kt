package com.ojoclaro.android.agent.core.screen

import android.view.accessibility.AccessibilityEvent

/**
 * Clasificador puro de eventos de accesibilidad.
 *
 * Recibe el `eventType: Int` y decide qué relevancia tiene para el pipeline
 * de Structured Screen Snapshot v1. NO toca AccessibilityNodeInfo, NO lee
 * pantalla, NO mantiene estado.
 *
 * Diseño:
 *  - Los tipos relevantes son los que el XML
 *    `res/xml/ojo_claro_accessibility_service.xml` ya tiene suscritos
 *    (typeWindowStateChanged | typeWindowContentChanged | typeViewTextChanged).
 *    Si en el futuro se agregan otros tipos al XML, este classifier ya los
 *    contempla — el wiring funcional sigue dependiendo del XML.
 *  - `RELEVANT_NOW`: cambio de ventana / foco / texto que probablemente
 *    representa una pantalla nueva. Pedimos collect inmediato; el collector
 *    decidirá si throttle.
 *  - `IRRELEVANT`: eventos de scroll/animación/click/etc. No vale la pena
 *    re-leer el árbol de accesibilidad.
 */
object AccessibilityEventClassifier {

    enum class Relevance {
        /** Vale la pena pedir un collect al collector (que respeta throttle). */
        RELEVANT_NOW,

        /** Ignorar. No re-leer el árbol por este evento. */
        IRRELEVANT
    }

    /**
     * Acepta el `eventType` plano (no el `AccessibilityEvent` completo) para
     * que el classifier sea trivial de testear sin instanciar objetos del
     * framework.
     */
    fun classify(eventType: Int): Relevance = when (eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
        AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
        AccessibilityEvent.TYPE_VIEW_FOCUSED,
        AccessibilityEvent.TYPE_WINDOWS_CHANGED -> Relevance.RELEVANT_NOW

        else -> Relevance.IRRELEVANT
    }
}
