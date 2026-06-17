# Revisión "Premium Native Android" — Ojo Claro AI

Fecha: 2026-05-05

Este documento traduce la visión del producto a criterios concretos de calidad de software nativo Android para una app blind-first.

---

## Qué buscamos

> Sobria, predecible, sin ruido, sin loops de voz, sin permisos molestos, sin acciones peligrosas, con botón Callar siempre disponible.

No buscamos animaciones lindas. Buscamos **confiabilidad**.

---

## Capas

| Capa | Hoy | Premium |
|------|-----|---------|
| UI | Compose, Material3, dark, fuentes ≥ 22sp | ✅ |
| Botón Callar | Fijo en BottomCenter, fuera del scroll, navigationBarsPadding | ✅ |
| TalkBack | contentDescription en todos los botones, headings semánticos | ✅ |
| TTS | Google TTS español, fallback chain | ✅ |
| Cámara | CameraX + ML Kit OCR local | ✅ |
| AccessibilityService | Solo lectura, ignora `isPassword` | ✅ |
| WhatsApp | Intent prellenado, nunca envía | ✅ |
| Privacidad | `PrivacyGuard` central, redact heurístico | ✅ |
| Consent | `ConsentManager` con 4 niveles, password rejection absoluto | ✅ Listo, integración pendiente |
| Cloud AI | `FutureCloudAiProvider` placeholder seguro | ✅ |

---

## Reglas de diseño en código

### Botón Callar

- Fuera de cualquier `verticalScroll`.
- `Modifier.align(Alignment.BottomCenter)` en un `Box`.
- `Modifier.navigationBarsPadding()` para respetar gestos.
- `heightIn(min = 72.dp)` → touch target accesible.
- `contentDescription = "Callar la voz."` constante.
- `padding(bottom = 16.dp)` adicional para no quedar pegado al borde.
- El contenido scrolleable debe reservar `bottom = 136.dp` para no taparse.

Las mismas reglas aplican a "Callar y volver" en `TextScanScreen` (con `navigationBarsPadding()` ya aplicado en el overlay) y a los botones del `OnboardingScreen` (root con `navigationBarsPadding()` y contenido scrolleable).

### Lenguaje de errores y permisos

Verboten:
- "Permiso denegado"
- "No autorizado"
- "Falta permiso X"
- "Error de permiso"
- "No puedo acceder"

Plantilla aceptada:
- *"Para [intención del usuario], [acción concreta que puede hacer]."*
- *"[Lo que SÍ podemos hacer ahora.]"*
- *"[Promesa concreta de privacidad.]"*

Ejemplo: `"Para leer texto, activá la cámara. Solo la uso cuando vos me lo pedís."`

### Estados (`AppState`)

Cada estado tiene texto humano en `statusText()`:

- IDLE → "Listo"
- LISTENING → "Escuchando"
- SCANNING → "Leyendo texto"
- PROCESSING → "Procesando"
- SPEAKING → "Hablando"
- WAITING_CONFIRMATION → "Esperando confirmación"
- PERMISSION_REQUIRED → "Activá un permiso" *(no "PERMISO REQUERIDO")*
- ERROR → "Aviso" *(no "ERROR")*

### Loops de voz

Prevención:
- `replay = 0`, `extraBufferCapacity = 4`, `BufferOverflow.DROP_OLDEST` en `_speechEvents`.
- `mutedThroughRequestId` en `HomeViewModel` para descartar respuestas viejas después de Callar.
- `greeted` flag en `greetIfFirstTime()` (idempotente).
- `OnboardingScreen` habla cada paso una sola vez vía `LaunchedEffect(state.currentStep)`.
- `Callar` siempre llama a `speechController.stop()` antes de cualquier otro side-effect.

### Confirmaciones

Para WhatsApp y futuras acciones sensibles, el flujo es:

1. Usuario pide acción → orquestador reconoce intención.
2. Orquestador clasifica con `ConsentManager.classify()`.
3. Si `NeedsConfirmation`: estado pasa a `WAITING_CONFIRMATION`, voz dice la explicación canónica.
4. Usuario dice "confirmar" → orquestador llama `confirmSimple()` y ejecuta.
5. Usuario dice "cancelar" o toca `Callar` → `cancel()`, limpiar pending, volver a `IDLE`.
6. TTL: 2 minutos. Después → "La acción pendiente venció. Volvé a pedirla."

Si el usuario dice "confirmar" sin haber nada pendiente: voz dice "No hay ninguna acción pendiente para confirmar." **Nunca silencio.**

### Privacidad

`PrivacyGuard` es el único lugar donde se decide:

- Qué texto recortar antes de hablar.
- Qué se puede mandar al backend (`canSendToCloud`).
- Qué nodo de Accessibility se puede leer (`isSafeToRead`).
- Qué redactar como secreto en OCR (`redactSensitiveText`).
- Qué mensaje de WhatsApp se puede enviar (`isSafeMessagePayload`).

Garantías escritas en código:

- `NO_AUTO_SEND_GUARANTEE`
- `NO_STORE_GUARANTEE`
- `NO_PASSWORD_GUARANTEE`
- `NO_BACKGROUND_LISTENING_GUARANTEE`

---

## Lo que NO se hace y NO se va a hacer

- Login.
- Pagos.
- Telemetría que identifique al usuario.
- IA cloud real conectada hoy.
- API keys hardcodeadas.
- Flutter.
- iOS.
- Envío automático de WhatsApp.
- Guardar chats, OCR, imágenes, audios.
- Subir pantalla al backend.
- Leer password fields.
- Saltar protecciones de apps bancarias.
- Prometer guía en la calle.

---

## Próximos pasos para "premium" real

1. **Integrar `ConsentManager` en el orquestador para `READ_VISIBLE_SCREEN`.** Hoy lee directo; pasaría por confirmación porque puede haber mensajes privados.
2. **Pruebas con persona ciega real.** El criterio de éxito de la app es que la persona no vidente pueda usarla, no que el código compile.
3. **Política de privacidad publicada.** Antes de Play Store.
4. **Keystore de release y eliminar `usesCleartextTraffic`.**
5. **Tests instrumentales mínimos** que verifiquen el botón Callar visible en Compose UI con texto largo (hoy se valida vía smoke test ADB).
