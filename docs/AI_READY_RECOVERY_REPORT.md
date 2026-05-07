# Informe de recuperación — Refactor AI-Ready

Fecha: 2026-05-04
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

---

## 1. Qué quedó a medio aplicar al momento del corte

El contexto anterior (Claude Opus) fue interrumpido justo después de escribir `HomeViewModel.kt`. El archivo estaba en estado funcional pero con los siguientes problemas latentes:

- `HomeViewModel` extendía `AndroidViewModel(Application)` correctamente, pero la UI (`HomeScreen`, `OjoClaroApp`) no había sido actualizada para manejar:
  - Los nuevos valores del enum `AppState` (`WAITING_CONFIRMATION`, `PERMISSION_REQUIRED`)
  - El botón "Pedir ayuda" que todavía llamaba `submitVoiceText("necesito ayuda")` en lugar de `requestHelp()`
  - El onboarding no estaba conectado a `OjoClaroApp`
  - El saludo inicial vía `greetIfFirstTime()` era llamado antes del subscriber del SharedFlow

---

## 2. Archivos en riesgo al momento del corte

| Archivo | Riesgo | Estado |
|---------|--------|--------|
| `HomeScreen.kt` | `statusText()` no cubría nuevos estados → **error de compilación garantizado** | Corregido |
| `OjoClaroApp.kt` | No integraba `OnboardingScreen` | Actualizado |
| `HomeViewModel.kt` | `greetIfFirstTime()` antes del subscriber del SharedFlow | Reordenado |
| `AssistantOrchestrator.kt` | No verificaba WhatsApp en fase `NeedsConfirmation` | Corregido |
| `CapabilityRegistry.kt` | No era testeable sin Context real | Refactorizado con `availabilityOverrides` |

---

## 3. Correcciones aplicadas

### HomeScreen.kt

- `statusText()`: agregados casos `WAITING_CONFIRMATION → "Esperando confirmación"` y `PERMISSION_REQUIRED → "Permiso necesario"`.
- Botón "Pedir ayuda" → "¿Qué puedo decir?" llamando a `viewModel.requestHelp()`.
- `greetIfFirstTime()` movido a un `LaunchedEffect` separado con `yield()` para dar tiempo al collector del SharedFlow.

### OjoClaroApp.kt

- Integración completa del onboarding: muestra `OnboardingScreen` la primera vez (chequeado via `OnboardingPreferences`).
- Al completar o saltar, marca el onboarding como completado y muestra `HomeScreen`.
- Cada instancia de `SpeechController` del onboarding se hace `shutdown()` al salir.

### AssistantOrchestrator.kt

- Verifica `Capability.WHATSAPP` en la fase `NeedsConfirmation` de `COMPOSE_WHATSAPP_MESSAGE`.
- Si WhatsApp no está instalado, devuelve error directo en lugar de pedir confirmación para una acción que nunca podría ejecutarse.

### CapabilityRegistry.kt

- Refactorizado con constructor secundario y `availabilityOverrides: Map<Capability, Boolean>`.
- Permite tests sin Context Android (sin Robolectric).
- `context` es ahora `Context?` internamente; el constructor público `(Context)` mantiene compatibilidad.

---

## 4. Partes del refactor AI-Ready activas

| Componente | Estado | Notas |
|------------|--------|-------|
| `CapabilityRegistry` | ✅ Activo | Testeable, 6 capabilities |
| `AiTask` / `AiContext` / `AiResult` | ✅ Activo | Modelos alineados con spec |
| `AiProvider` interface | ✅ Activo | — |
| `LocalRuleBasedAiProvider` | ✅ Activo | 8 AiTasks, on-device |
| `FutureCloudAiProvider` | ✅ Activo | Placeholder seguro |
| `AssistantOrchestrator` | ✅ Activo | Integrado en HomeViewModel |
| `OnboardingState` / `OnboardingPreferences` / `OnboardingScreen` | ✅ Activo | Flujo completo |
| `VoiceHelpCenter` | ✅ Activo | Integrado en `requestHelp()` |
| `PrivacyGuard` | ✅ Activo | Reforzado, testeable |
| `AppState` con nuevos estados | ✅ Activo | WAITING_CONFIRMATION, PERMISSION_REQUIRED |
| `OrchestratorOutcome` | ✅ Activo | Reemplaza el sealed class anterior |
| `HomeViewModel` adelgazado | ✅ Activo | Delega al Orchestrator para externos |

---

## 5. Partes aisladas o pendientes

| Pendiente | Motivo |
|-----------|--------|
| `FutureCloudAiProvider` conectado a API real | Excluido por diseño — no hay claves, no hay consentimiento |
| Tests instrumentales (Compose UI) | Requieren emulador y añaden fragigibilidad sin ROI claro ahora |
| `ParsedCommand` (commands/) | Existe como placeholder; sin consumidor activo todavía |
| Smoke test en emulador | Emulador no estaba disponible en este ciclo |

---

## 6. Resultado de builds y tests

```
.\gradlew.bat :androidApp:assembleDebug        → BUILD SUCCESSFUL (82 tareas)
.\gradlew.bat :androidApp:testDebugUnitTest    → BUILD SUCCESSFUL (62 tests, 0 failed)
.\gradlew.bat :shared:allTests                 → BUILD SUCCESSFUL
```

Tests nuevos ejecutados:
- `CapabilityRegistryTest` (7 tests)
- `LocalRuleBasedAiProviderTest` (10 tests)
- `AssistantOrchestratorTest` (11 tests)
- `PrivacyGuardTest` (13 tests)
- `CommandRouterTest` (3 tests nuevos agregados: confirmar sin pendiente, cancelar sin pendiente, TTL vencido)

---

## 7. Prueba en emulador

No realizada: no había emulador disponible al momento de esta sesión.

Pendiente:
```powershell
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb shell am start -n com.ojoclaro.android/.MainActivity
adb logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro"
```

---

## 8. Ruta del APK

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

---

## 9. Riesgos pendientes

| Riesgo | Impacto | Mitigación |
|--------|---------|------------|
| Saludo inicial puede perderse si yield() no es suficiente | Baja — el usuario ve el texto en pantalla | TalkBack anuncia estado al ganar foco |
| `usesCleartextTraffic="true"` en Manifest | Solo debug | Documentado, no bloqueante para MVP |
| DESCRIBIR dice "No pude conectar" si backend caído | Media confusión | Fallback claro, documentado en BLIND_FIRST_PRODUCT_REVIEW |
| Onboarding se saltea con "Saltar" sin aceptar privacidad | Legal | Pendiente política de privacidad formal |
| Sin prueba real con persona ciega | Crítico para producto | Requiere intervención humana |

---

## 10. Próximo paso recomendado

```
Prueba supervisada en dispositivo físico:

1. Instalar APK en dispositivo Android físico.
2. Activar TalkBack.
3. Completar el onboarding con voz activa.
4. Probar: Leer texto → cámara → OCR.
5. Probar: "mandale a [nombre]: [mensaje]" → confirmar → WhatsApp abre con texto.
6. Probar: "qué dice la pantalla" con y sin accesibilidad activa.
7. Probar: "¿qué puedo decir?" → escuchar ayuda de voz.
8. Probar: Callar en cada estado.
9. Registrar frases que confunden al usuario.

El criterio de éxito no es "no crashea" — es "la persona ciega entiende qué hacer".
```
