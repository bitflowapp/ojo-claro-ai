# Ojo Claro AI — Voice-first demo readiness

Fecha: 2026-05-05
Estado: **Lista para demo de voz. Un único bug real corregido (TTS llamando a SpeechRecognizer.cancel() fuera del main thread). Todo lo demás ya estaba bien y no se tocó.**

---

## 1. Estado actual

| Capa | Estado |
|------|--------|
| `:androidApp:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `:androidApp:testDebugUnitTest` | ✅ 264 tests, 0 fail |
| `:shared:allTests` | ✅ BUILD SUCCESSFUL |
| APK debug | ✅ `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (~57 MB, regenerado tras fix) |
| Cold launch en `emulator-5554` | ✅ +1s900ms, sin FATAL EXCEPTION, sin W Binder de la app |
| `R5CW22SMWDM` | ❌ No conectado en este equipo (`adb devices` solo lista `emulator-5554`) |

El emulador no tiene micrófono real, por lo cual no se puede correr el guion completo (saludo → "qué puedo decir" → "callar" → ...) con voz humana, pero sí se confirmó que:

- la app inicia limpio,
- pide RECORD_AUDIO al primer arranque,
- al conceder el permiso, llama a `RecognitionService` con `locale: es-AR` (sin pegar a la nube),
- el callback `onSpeechStarted` del TTS ya no genera el `W Binder` que cancelaba el SpeechRecognizer fuera del main thread.

---

## 2. Comandos que entiende (verificados por tests)

Todos están cubiertos por `CommandRouterTest`, `AssistantOrchestratorTest`,
`VoiceCommandDispatcherTest` y `VoiceCommandControllerTest`:

1. `qué puedo decir` → `VoiceCommandDispatcher.isHelpCommand` → `VoiceHelpCenter.SPOKEN_HELP`
2. `callar` (también `callate`, `silencio`, `para`, "callar por favor" en parcial) → corta TTS al toque vía `stopForCommandAndResume()`
3. `cancelar` (también `cancela`, `no`, `anular`) → cancela el pending
4. `confirmar` (también `confirmo`, `aceptar`) → confirma el pending. **No** confirma con `sí` / `si` / `dale`
5. `leer texto` → abre OCR cámara (`startScanning`)
6. `qué dice la pantalla` → pide consent, después llama a `AccessibilityScreenReader`
7. `leeme este mensaje` → mismo flujo de consent
8. `abrí WhatsApp` (con o sin acento; aliases: `wp`, `wsp`, `wpp`, `wasap`, `guasap`, `watsap`, `whasap`, `whats app`) → emite `OpenWhatsApp`
9. `mandale un mensaje a Sofi que estoy llegando` → `COMPOSE_WHATSAPP_MESSAGE`(Sofi, "estoy llegando") → confirmación
10. `mandale un WhatsApp a Sofi que estoy llegando` → idem
11. `escribile a Sofi por WhatsApp que estoy llegando` → idem
12. `decile a Sofi que estoy llegando` → idem
13. `mandale a mi novia que estoy llegando` → `COMPOSE_WHATSAPP_MESSAGE`("mi novia", "estoy llegando")
14. `recordá que prefiero respuestas cortas` → `REMEMBER_MEMORY` con consent
15. `qué recordás de mí` → lista memoria segura
16. `borrá tu memoria` → consent + `CLEAR_MEMORY`

Variantes naturales bonus que también pasan:
- `mandale un mensaje` (sin contacto) → "¿A quién querés mandarle el mensaje?"
- `mandale a Sofi` (sin mensaje) → "¿Qué mensaje querés mandarle?"
- `en WhatsApp mandale a Sofi que ...` / `abrí WhatsApp y mandale a Sofi que ...` → COMPOSE
- `mandale mensaje a mamá diciendo que estoy bien` → COMPOSE

---

## 3. Qué se corrigió (un solo cambio)

### `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt`

`SpeechController.onSpeechStarted` recibía un callback que llamaba a
`voiceController.pauseForSpeech()` directamente desde el thread interno del
TextToSpeech (`UtteranceProgressListener.onStart`). `pauseForSpeech()` termina
invocando `SpeechRecognizer.cancel()`, que **debe ejecutarse en el main
thread**. En logcat real esto se veía como:

```
W Binder  : 	at com.ojoclaro.android.voice.AndroidSpeechInputEngine.stopListening(AndroidSpeechInputEngine.kt:78)
W Binder  : 	at com.ojoclaro.android.voice.VoiceCommandController.pauseForSpeech(VoiceCommandController.kt:72)
W Binder  : 	at com.ojoclaro.android.ui.home.HomeScreenKt.HomeScreen$lambda$12$lambda$9(HomeScreen.kt:97)
W Binder  : 	at com.ojoclaro.android.speech.SpeechController$utteranceListener$1.onStart(SpeechController.kt:40)
```

Síntoma para el usuario: el mic podía quedar abierto durante una fracción de
segundo mientras el TTS empezaba, y el agente se podía escuchar a sí mismo
("TTS hearing itself" del prompt). Las callbacks `onSpeechFinished` y
`onSpeechStopped` ya estaban envueltas en `scope.launch { ... }`; sólo
`onSpeechStarted` faltaba.

Fix:

```kotlin
onSpeechStarted = {
    scope.launch {
        voiceControllerHolder[0]?.pauseForSpeech()
    }
}
```

Verificación post-fix: re-instalo APK, force-stop, cold launch, espero 8s.
Logcat ya no muestra `W Binder` desde ningún `com.ojoclaro.android.voice` ni
`com.ojoclaro.android.speech`. El `RecognitionServiceImpl` arranca con
`locale: es-AR` y la actividad se muestra en 1.9s.

---

## 4. Qué ya estaba bien (y no se tocó)

### `VoiceCommandController.kt`
- Estado interno con lock + `consecutiveRecoverableErrors` con backoff (400/800/1200/2000 ms).
- Hace `keepSilentRecovery = true` por default y solo dice "Sigo escuchando." después de 4 fallos seguidos.
- `stopForCommandAndResume()` tiene FAST_RESTART_DELAY_MILLIS = 300ms para callar/comando rápidos.
- No re-arranca si `STOPPED_BY_USER`, `SPEAKING` o `PROCESSING`.
- `consecutiveRecoverableErrors = 0` se resetea en `onFinalText` y `resumeAfterSpeech` y `stopForCommandAndResume`.

### `VoiceCommandDispatcher.kt`
- `isStopCommand` matchea `callar`, `callate`, `silencio`, `para`, `parar` y también `"callar por favor"` y `"... callar"` parcial.
- `onPartialText` corta TTS al primer match parcial — no espera a final.
- `isHelpCommand` y `isReadTextCommand` con normalización (acentos, mayúsculas, puntuación).

### `SpeechController.kt`
- `DEDUP_WINDOW_MILLIS = 5_000L`: no repite la misma frase si llega dos veces en 5 segundos (a menos que `force = true`).
- `generation` aumenta en cada `speak` y `stop`: callbacks viejos no disparan `onSpeechFinished` después de un cambio.
- `stop()` notifica `onSpeechStopped` solo si había algo activo. No genera ruido si no hay nada.
- `configureLocale` cae en `es_AR` → `es_US` → `es` para no romper en dispositivos sin español argentino.

### `CommandRouter.kt`
- Confirmaciones estrictas: `confirmar`, `confirmo`, `aceptar`. **No** `sí`/`dale`.
- Cancelar: `cancelar`, `cancela`, `no`, `anular`.
- Detector de intent de mensaje (regex con `mandale|mandarle|escribile|...|enviar mensaje|escribile por whatsapp`).
- Aliases WhatsApp (`whats app`, `wp`, `wsp`, `wpp`, `wasap`, `guasap`, `watsap`, `whasap`).
- Si hay intent de mensaje pero falta contacto → "¿A quién querés mandarle el mensaje?".
- Si hay intent de mensaje pero falta el mensaje → "¿Qué mensaje querés mandarle?".
- Antes de devolver `NeedsConfirmation`, valida con `PrivacyGuard.isSafeMessagePayload` y bloquea con razón.
- `isOpenWhatsAppCommand` excluye explícitamente comandos con intent de mensaje, para evitar caer en "abrir WhatsApp" cuando en realidad pidió mandar uno.

### `AssistantOrchestrator.kt`
- Si llega un comando que no es `confirmar`/`cancelar` con un consent pendiente, lo limpia (`abandonedConsent`) para no quedar colgado.
- Verifica capabilities antes de prometer (Camera, Accessibility, WhatsApp).
- `riskAwareVisibleScreenOutcome` redacta passwords/códigos/tarjetas y agrega advertencias de RiskDetector antes de hablar.

### `PrivacyGuard.kt`
- `isSafeMessagePayload`: bloquea blank, demasiado largo, contraseña, código de verificación, tarjeta, alias bancario, saldo, home banking.
- `canStorePattern`: requiere `userApprovedForSuggestions = true` y contenido no prohibido.
- `redactSensitiveText`: encadena passwords → códigos → tarjetas → secrets-de-aspecto.

### `HomeViewModel.kt`
- Saludo se emite **una sola vez** (`greeted`).
- `onStopSpeechRequested` limpia tanto `pendingExternalConfirmation` como `pendingConsentAction` — callar abandona TODO.
- `mutedThroughRequestId` evita que respuestas viejas del backend hablen después de "callar".
- Fallback al backend usa mensajes locales si la red falla.

### `HomeScreen.kt`
- Lifecycle: `ON_RESUME` con permiso de mic → `voiceController.startListening()`. `ON_PAUSE` → `stopListening()`.
- Reactivo a `appState`: `IDLE` o `WAITING_CONFIRMATION` → start; `SCANNING`/`PROCESSING` → pause; `PERMISSION_REQUIRED` → stop.
- Botón "Callar" llama `voiceController.stopForCommandAndResume()` + `speechController.stop()` + `viewModel.onStopSpeechRequested()`.

---

## 5. Resultado tests

```
LocalRuleBasedAiProviderTest    11 ✓
StableTextDetectorTest          10 ✓
CapabilityRegistryTest          13 ✓
ConsentManagerTest              13 ✓
AssistantOrchestratorTest       34 ✓
CommandRouterTest               28 ✓
LocalMemoryStoreTest            17 ✓
MemoryPolicyTest                39 ✓
FrequentPatternTrackerTest      13 ✓
PrivacyGuardTest                40 ✓
RiskDetectorTest                27 ✓
VoiceCommandControllerTest      11 ✓
VoiceCommandDispatcherTest       8 ✓
                              ----
TOTAL                          264 ✓
```

`:shared:allTests` → BUILD SUCCESSFUL (JVM/Android tests verdes; iOS targets
skipped en Windows).

`:androidApp:assembleDebug` → BUILD SUCCESSFUL.

---

## 6. APK final

```
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
~57 MB, regenerado con el fix de threading aplicado.
```

---

## 7. Resultado físico / logcat

`R5CW22SMWDM` no estaba conectado, por lo que no se pudo correr el guion en
device físico. Lo que sí se hizo:

- **Pre-fix** (en `emulator-5554`): cold launch OK + `RecognitionService` con
  `es-AR` + `W Binder` con stack que incluía
  `AndroidSpeechInputEngine.stopListening` + `pauseForSpeech` +
  `SpeechController.utteranceListener.onStart`. Bug real.
- **Post-fix** (mismo emulador): cold launch en 1.9s, `RecognitionService`
  arranca con `es-AR`, **sin `W Binder` desde ningún paquete `ojoclaro`**,
  sin `FATAL EXCEPTION`, sin `AndroidRuntime`.

Para correr el guion completo en device físico:

```
adb -s R5CW22SMWDM install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb -s R5CW22SMWDM logcat -c
adb -s R5CW22SMWDM shell am start -n com.ojoclaro.android/.MainActivity
adb -s R5CW22SMWDM logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro"
```

---

## 8. Comandos listos para demo

Orden sugerido para la persona que mira/escucha:

1. Abrir la app. **No tocar nada.** Esperá el saludo (~1s después del foreground).
2. Decir **"qué puedo decir"** → debería leer la lista de comandos.
3. Mientras habla, decir **"callar"** → debería cortarse en menos de 1s.
4. Decir **"qué dice la pantalla"** → debería pedir consent ("Voy a leer
   texto visible. No lo guardo ni lo envío. Confirmá para continuar.").
5. Decir **"cancelar"** → "Acción cancelada.".
6. Decir **"mandale un mensaje a Sofi que estoy llegando"** → debería
   anunciar el mensaje y "No lo envío automáticamente. Confirmá para
   continuar.".
7. Decir **"cancelar"**.
8. Esperar 10 segundos en silencio. Después decir **"qué puedo decir"** →
   debería responder de nuevo (validando que el auto-relisten siguió vivo).
9. Decir **"recordá que prefiero respuestas cortas"** → consent → **"confirmar"**
   → "Lo voy a recordar.".
10. Decir **"qué recordás de mí"** → "Recuerdo que preferís respuestas
    cortas.".
11. Decir **"borrá tu memoria"** → consent → **"confirmar"** → "Borré mi
    memoria local.".
12. Decir **"sí"** después de un consent (negativo) → no debe ejecutar.

---

## 9. Cosas que todavía pueden fallar

- **Reconocimiento offline**: si el dispositivo no tiene paquete de voz
  `es-AR` instalado, el SpeechRecognizer puede caer a online o fallar.
  `humanMessageFor(ERROR_NETWORK)` lo cubre, pero el usuario va a oír
  "No pude escuchar bien".
- **Latencia del primer "callar"**: depende del TTS engine. En dispositivos
  con TTS lento, el `engine.stop()` puede tomar 200-400 ms. La app ya hace
  todo lo correcto pero no controla la latencia del engine.
- **Frase ambigua sin "que" ni ":"**: `"mandale a Sofi estoy llegando"` (sin
  conector) cae en `missingMessageRegex` → "¿Qué mensaje querés mandarle?".
  Esto es intencional pero puede confundir al usuario.
- **Hablar muy seguido**: si el usuario habla **mientras** Ojo Claro habla,
  el partial text "callar" se procesa primero → corta. Pero si dice otra
  cosa, el SpeechRecognizer está pausado y no la escucha hasta que TTS
  termine.
- **TalkBack activo**: TalkBack también lee la pantalla. El control de
  feedback puede solapar en eventos como `appState` cambiando. La app no
  duplica el saludo (`greeted`), pero TalkBack puede leer lo que hay en
  `state.spokenText` además del TTS de Ojo Claro.
- **Strong consent (BIOMETRIC)** sigue rechazado. Lectura de pantalla
  bancaria nunca se ejecuta hasta que se implemente `BiometricPrompt`.
- **Pending consent** vive solo en memoria del ViewModel — si Android mata
  la app en background, se pierde.

---

## 10. Próximo paso recomendado

1. **Probar en el device físico R5CW22SMWDM** con la app instalada y voz
   real. Seguir el guion de la sección 8. Confirmar que:
   - el saludo se escucha una sola vez,
   - "callar" corta en <1s,
   - el auto-relisten dura los 10 segundos de silencio.
2. Si pasa, demo lista. Si no:
   - capturar `adb logcat` durante el flujo,
   - revisar `RecognitionServiceImpl` y `TextToSpeech` events,
   - chequear timings reales del callback `onDone`/`onStop` del TTS en ese
     teléfono específico.
3. Si todo va bien y querés ir un paso más allá: cubrir el flujo de
   permiso del Servicio de Accesibilidad activado por voz (no solo por
   botón), para que "qué dice la pantalla" funcione full sin tocar Ajustes.
