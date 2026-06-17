# Arquitectura AI-Ready — Ojo Claro AI

Fecha: 2026-05-04

---

## 1. Vista general

```
Usuario (voz / toque)
        │
        ▼
   HomeScreen (Compose)
        │  delega acciones
        ▼
   HomeViewModel
        │  comandos externos ──► AssistantOrchestrator
        │  DESCRIBIR / texto libre ──► AssistantApi (backend/fallback)
        │
        ▼
   AssistantOrchestrator
        │
        ├── CommandRouter  (parsea comandos externos WhatsApp/pantalla)
        ├── CapabilityRegistry  (¿qué puede hacer la app ahora mismo?)
        ├── LocalRuleBasedAiProvider  (reglas locales 100% on-device)
        └── FutureCloudAiProvider  (placeholder, nunca llama APIs reales)
```

---

## 2. Cómo fluye un comando

### "leeme este mensaje"

1. `HomeViewModel.submitVoiceText("leeme este mensaje")`
2. `commandRouterRecognizes(text)` → true (es comando externo)
3. `orchestrator.process(...)` async en `viewModelScope`
4. `CommandRouter.parse` → `READ_VISIBLE_SCREEN`
5. `CapabilityRegistry.status(ACCESSIBILITY_SERVICE)` → isAvailable?
   - No → Outcome(PERMISSION_REQUIRED, "Necesito activar el permiso de accesibilidad para leer la pantalla.")
   - Sí → Outcome(PROCESSING, externalEvent = ReadVisibleScreen)
6. `HomeScreen` colecta `externalActionEvents` → `AccessibilityScreenReader.readVisibleScreen(context)`
7. Resultado → `viewModel.onExternalCommandResult(result)` → publicado por voz.

### "mandale a un contacto: estoy llegando"

1. `commandRouterRecognizes` → true
2. `orchestrator.process` → `CommandRouter.parse` → `COMPOSE_WHATSAPP_MESSAGE`
3. Orquestador verifica `WHATSAPP` capability antes de pedir confirmación.
   - Si falta: Outcome(ERROR, "No encontré WhatsApp instalado.") — el usuario no pasa por confirmación.
   - Si OK: `CommandRouter.route` → `NeedsConfirmation` → Outcome(WAITING_CONFIRMATION, pendingConfirmation)
4. UI habla la confirmación. Usuario dice "confirmar".
5. Segunda llamada: `CommandRouter.route("confirmar", pending)` → `Success` con el comando original.
6. `handleExternalSuccess` → Outcome(PROCESSING, externalEvent = ComposeWhatsAppMessage)
7. `WhatsAppIntentHelper.composeMessage` abre WhatsApp con texto prellenado. El usuario toca Enviar manualmente.

### "callar"

1. `HomeScreen` botón Callar → `speechController.stop()` + `viewModel.onStopSpeechRequested()`
2. ViewModel: `mutedThroughRequestId = activeRequestId`, `pendingExternalConfirmation = null`, `appState = IDLE`
3. Ningún `SpeechEvent` activo se ejecuta; requestId > mutedThrough será falso para respuestas viejas.

---

## 3. Cómo se conectará IA futura

### Dónde conectar OpenAI / Gemini / Claude

1. Crear `CloudAiProvider` que implemente `AiProvider`.
2. Inyectarlo en `AssistantOrchestrator` como `cloudAiProvider`.
3. En `handleAiTask`, usar `cloudAiProvider.process(task, context)` cuando `context.allowCloud == true` y `PrivacyGuard.canSendToCloud(...)` devuelva `true`.
4. Activar `cloudAiEnabled = true` en `CapabilityRegistry` solo cuando el usuario haya consentido.

### Datos que la IA puede usar (del AiContext)

| Campo | Descripción | Se envía a cloud? |
|-------|-------------|-------------------|
| `rawCommand` | Texto del usuario | Sí, si allowCloud |
| `ocrText` | Texto detectado con cámara local | Solo con consentimiento |
| `visibleScreenText` | Texto leído por AccessibilityService | **Nunca** — alta sensibilidad |
| `imageBase64` | Foto tomada por cámara | Solo sin safetyMode + allowCloud |
| `targetContact` | Nombre del contacto | No en MVP |
| `message` | Texto del mensaje a preparar | No en MVP |

### Datos que la IA NO debe tocar

- `visibleScreenText` con saldos bancarios, contraseñas, conversaciones privadas.
- Cualquier campo procesado cuando `safetyMode = true` y `hasImage = true`.
- Historial de conversaciones (no existe en esta versión).

Punto de control: `PrivacyGuard.canSendToCloud(allowCloud, safetyMode, hasImage)`.

---

## 4. Cómo funcionan las confirmaciones

Solo las acciones en `COMPOSE_WHATSAPP_MESSAGE` y futuras acciones sensibles pasan por confirmación.

Flujo:
```
UNSUPPORTED → handled locally
OPEN_WHATSAPP → verifica capability → OK → externalEvent
COMPOSE_WHATSAPP_MESSAGE → verifica WhatsApp → NeedsConfirmation → WAITING_CONFIRMATION
CONFIRM_PENDING_ACTION → CommandRouter resuelve el comando original → externalEvent
CANCEL_PENDING_ACTION → limpia pending → SPEAKING "Acción cancelada."
```

TTL de confirmación: 2 minutos (`confirmationTtlMillis` en `CommandRouter`). Después: "La acción pendiente venció. Volvé a pedirla."

"Confirmar" sin pending → "No hay ninguna acción pendiente para confirmar." (nunca silencio).

---

## 5. Cómo testear

### Unit tests (sin dispositivo)

```bash
.\gradlew.bat :androidApp:testDebugUnitTest
.\gradlew.bat :shared:allTests
```

Cubre: `CommandRouterTest`, `StableTextDetectorTest`, `CapabilityRegistryTest`, `LocalRuleBasedAiProviderTest`, `AssistantOrchestratorTest`, `PrivacyGuardTest`, `CommandParserTest`.

Para testear `CapabilityRegistry` sin Context:
```kotlin
CapabilityRegistry(
    context = null,
    availabilityOverrides = mapOf(Capability.WHATSAPP to true)
)
```

### Integración con emulador

```powershell
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb shell am start -n com.ojoclaro.android/.MainActivity
adb logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro"
```

### Integración con dispositivo físico

Ver `docs/ANDROID_REAL_DEVICE_QA.md`.

---

## 6. Árbol de archivos de arquitectura AI-Ready

```
androidApp/src/main/java/com/ojoclaro/android/
├── ai/
│   ├── AiTask.kt              enum de tareas IA
│   ├── AiContext.kt           contexto seguro de entrada
│   ├── AiResult.kt            resultado normalizado
│   ├── AiProvider.kt          interfaz
│   ├── LocalRuleBasedAiProvider.kt   on-device, sin red
│   └── FutureCloudAiProvider.kt      placeholder, responde "no configurado"
├── capabilities/
│   ├── Capability.kt          enum + mensajes
│   └── CapabilityRegistry.kt  estado real del sistema, testeable sin Context
├── commands/
│   └── ParsedCommand.kt       comando parseado (para uso futuro)
├── domain/
│   ├── AssistantOrchestrator.kt  orquestador central
│   └── OrchestratorResult.kt     OrchestratorOutcome (plano, sin UI)
├── help/
│   └── VoiceHelpCenter.kt     frases de ayuda cortas
├── onboarding/
│   ├── OnboardingState.kt     estado y pasos del onboarding
│   ├── OnboardingPreferences.kt  persistencia SharedPreferences
│   └── OnboardingScreen.kt    pantalla accesible TalkBack-ready
└── privacy/
    └── PrivacyGuard.kt        reglas centrales de privacidad testeables
```
