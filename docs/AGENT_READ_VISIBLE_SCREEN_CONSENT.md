# Lectura de pantalla con consentimiento — Ojo Claro AI

Fecha: 2026-05-05  
Estado: integrado, cubierto por tests, smoke test en emulador OK.

---

## Por qué importa

Una persona ciega que dice "qué dice la pantalla" puede estar en una conversación privada de WhatsApp. Leer ese texto sin avisar es un error grave de privacidad: aunque sea **pedido por el usuario**, la app debe explicar qué va a hacer antes de hacerlo.

Con esta integración, la lectura de pantalla pasa de "acción inmediata" a "acción de agente con consentimiento".

---

## Flujo implementado

```
Usuario: "qué dice la pantalla"
         │ (también: "leeme este mensaje", "leer pantalla", "leer este mensaje", …)
         ▼
   AssistantOrchestrator.process(...)
         │
         ▼
   CommandRouter parsea → READ_VISIBLE_SCREEN
         │
         ▼
   Verificar AccessibilityService
         │
   ┌─────┴─────┐
   │           │
INACTIVO    ACTIVO
   │           │
   ▼           ▼
PERMISSION_   ConsentManager.requestAction(
REQUIRED        READ_VISIBLE_MESSAGE,
+ guía         ConsentPhrases.READ_VISIBLE_MESSAGE)
humana          │
                ▼
          NeedsConfirmation(pending)
                │
                ▼
   OrchestratorOutcome:
     state = WAITING_CONFIRMATION
     newPendingConsent = pending
     speak = "Voy a leer texto visible de la pantalla.
              No lo guardo ni lo envío.
              Confirmá para continuar."

Usuario: "confirmar"
         │
         ▼
   AssistantOrchestrator.process(rawInput="confirmar", pendingConsent=pending)
         │
         ▼
   ConsentManager.confirmSimple(pending, now)
         │
   ┌─────┴────────────────┐
   │                      │
   ▼                      ▼
EXPIRED            CONFIRMED
   │                      │
   ▼                      ▼
clearsPendingConsent   Re-verificar AccessibilityService
+ "venció. Volvé        (puede haber cambiado en el medio)
   a pedirla."          │
                  ┌─────┴─────┐
                  ▼           ▼
              INACTIVO     ACTIVO
                  │           │
                  ▼           ▼
              PERMISSION_  externalEvent = ReadVisibleScreen
              REQUIRED     state = PROCESSING
                           clearsPendingConsent = true

Usuario: "cancelar"
         │
         ▼
   ConsentManager.cancel(pending)
         │
         ▼
   "Acción cancelada."
   state = IDLE
   clearsPendingConsent = true

Usuario: tap "Callar"
         │
         ▼
   HomeViewModel.onStopSpeechRequested():
     - speechController.stop()
     - mutedThroughRequestId = activeRequestId
     - pendingExternalConfirmation = null
     - pendingConsentAction = null    ← cancela el consent
     - state = IDLE
```

---

## Qué confirma el usuario

La confirmación cubre **leer texto visible en pantalla en este momento**. Cada vez que el usuario lo pide, se crea un nuevo pendiente con TTL de 2 minutos. No es un "permiso permanente": al cerrar el ciclo (cancelar / expirar / Callar / confirmar y leer), el pending se borra.

---

## Qué datos se leen

- Solo `node.text` y `node.contentDescription` de la jerarquía de Accessibility de la ventana activa.
- Limitado a `MAX_TEXT_ITEMS = 24` y `MAX_SINGLE_TEXT_LENGTH = 280` para no leer minutos.
- `OjoClaroAccessibilityService` ignora cualquier nodo con `node.isPassword == true` y nodos no visibles.

## Qué datos NO se guardan ni se envían

- ❌ El texto leído **no** se persiste en SharedPreferences ni en archivos.
- ❌ El texto leído **no** se envía al backend.
- ❌ La acción pendiente **no** guarda contenido de la pantalla — solo metadata (id, tipo, timestamps). El payload mapa<String,String> está reservado para datos chicos y seguros (un nombre de contacto, no un mensaje).
- ❌ `PrivacyGuard.canSendToCloud` sigue requiriendo `allowCloud=true`, y hoy ningún flujo lo pone en true.

---

## Qué pasa si falta accesibilidad

- Antes de pedir consent: si `Capability.ACCESSIBILITY_SERVICE` no está disponible, el orchestrator devuelve `PERMISSION_REQUIRED` con `Capability.MSG_ACCESSIBILITY_MISSING`:  
  *"Para leer esta pantalla, activá Ojo Claro en Accesibilidad. Solo leo lo que tenés delante."*
- Si el usuario activó pero el servicio aún no se conectó (caso `OjoClaroAccessibilityService.isConnected()==false`), la lectura responde:  
  *"Activaste el permiso. Esperá un segundo y volvé a pedírmelo."*
- Si el usuario desactiva accesibilidad **mientras** hay un consent pending, al confirmar se re-verifica y se devuelve `PERMISSION_REQUIRED` + `clearsPendingConsent=true`. No se lee.

---

## Qué pasa si el usuario cancela

- `process("cancelar", pendingConsent=pending)` →
  - `consentManager.cancel(pending)` (no-op interno hoy, hook futuro).
  - Outcome: `state=IDLE, clearsPendingConsent=true, spokenText="Acción cancelada."`
- Si el usuario dice "cancelar" cuando no hay nada pendiente, el flujo va al CommandRouter que devuelve "No hay ninguna acción pendiente."

---

## Qué pasa si el usuario confirma sin pendiente

- Si `pendingConsent == null` y `pendingConfirmation == null`, "confirmar" cae en `CommandRouter.route()` que devuelve un `Failed` con  
  *"No hay ninguna acción pendiente para confirmar."*
- Estado: `ERROR`, recoverable. Nunca silencio.

---

## Qué pasa si el consent venció

- TTL: 2 minutos (`ConsentManager.DEFAULT_TTL_MILLIS`).
- `confirmSimple(pending, now > pending.expiresAtMillis)` →
  - Outcome: `isError=true, clearsPendingConsent=true, state=ERROR, spokenText="La acción pendiente venció. Volvé a pedirla."`

---

## Cambios concretos en el código

| Archivo | Cambio |
|---------|--------|
| `domain/OrchestratorResult.kt` | Agregados `newPendingConsent: PendingSensitiveAction?` y `clearsPendingConsent: Boolean`. |
| `domain/AssistantOrchestrator.kt` | `process()` recibe `pendingConsent`. Nuevos handlers `handleConsentConfirm`, `handleConsentCancel`, `executeConsentAction`. `READ_VISIBLE_SCREEN` ahora pide consent en vez de ejecutar directo. ConsentManager inyectado. |
| `ui/home/HomeViewModel.kt` | Nuevo `pendingConsentAction: PendingSensitiveAction?`. Pasa al orchestrator. Aplica `newPendingConsent` y `clearsPendingConsent` desde el outcome. `onStopSpeechRequested` limpia el consent pending. |
| `ui/home/HomeViewModel.kt` (handleExternalCommandIfNeeded) | Si hay consent pending, fuerza el comando a pasar por orchestrator (no cae al backend por error). |

---

## Tests

`AssistantOrchestratorTest` — 17 tests (5 nuevos):

- `readVisibleScreenAsksForConsentBeforeReading` — "qué dice la pantalla" → WAITING_CONFIRMATION + newPendingConsent.
- `leemeEsteMensajeAlsoAsksForConsent` — frases alternativas también piden consent.
- `confirmWithConsentPendingExecutesReadVisibleScreen` — "confirmar" con pending válido → ejecuta + clearsPendingConsent.
- `cancelWithConsentPendingClearsIt` — "cancelar" → IDLE + clearsPendingConsent + frase de cancelación.
- `confirmWithExpiredConsentReturnsClearMessage` — TTL vencido → mensaje claro, no ejecuta.
- `confirmWithConsentPendingButAccessibilityLostAsksForPermission` — re-verifica accesibilidad al confirmar.

Tests existentes intactos:
- `confirmWithoutPendingReturnsFailedMessage` (sigue válido: cuando no hay ningún pending, el flujo va al CommandRouter).
- `cancelWithoutPendingReturnsNoPendingMessage`.
- `readVisibleScreenAccessibilityMissingReturnsPermissionRequired` (sigue válido: la verificación temprana es antes del consent).
- WhatsApp y compose mensajes intactos.

`ConsentManagerTest` — 13 tests (preexistentes, todos verdes).

---

## Comandos ejecutados

```powershell
.\gradlew.bat :androidApp:compileDebugKotlin    → BUILD SUCCESSFUL
.\gradlew.bat :androidApp:assembleDebug         → BUILD SUCCESSFUL
.\gradlew.bat :androidApp:testDebugUnitTest --rerun-tasks
                                                → BUILD SUCCESSFUL (80 tests, 0 fallidos)
.\gradlew.bat :shared:allTests                  → BUILD SUCCESSFUL

adb -s emulator-5554 install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb -s emulator-5554 shell am start -n com.ojoclaro.android/.MainActivity
adb -s emulator-5554 logcat -d | grep -c "FATAL EXCEPTION"
                                                → 0
```

---

## Resultado de tests por suite

| Suite | Tests | Failures |
|-------|-------|----------|
| AssistantOrchestratorTest | 17 | 0 |
| ConsentManagerTest | 13 | 0 |
| LocalRuleBasedAiProviderTest | 11 | 0 |
| PrivacyGuardTest | 16 | 0 |
| CommandRouterTest | 12 | 0 |
| CapabilityRegistryTest | 7 | 0 |
| StableTextDetectorTest | 4 | 0 |
| **Total Android** | **80** | **0** |
| Shared (allTests) | NO-SOURCE | — |

---

## Riesgos pendientes

1. **El `OjoClaroAccessibilityService` lee toda la ventana visible.** En el futuro, conviene segmentar por zona (ej. solo la región tocada, o solo la conversación foco) para reducir lectura de elementos cosméticos. Hoy se mitiga con `MAX_TEXT_ITEMS=24` y `MAX_SINGLE_TEXT_LENGTH=280`.
2. **Detección de pantalla bancaria.** `SensitiveActionType.READ_BANKING_SCREEN` está modelado y rechaza explícitamente, pero no hay clasificador automático todavía. Hoy depende de que el usuario no pida leer pantallas de banco — riesgo bajo en el MVP.
3. **TTL fijo de 2 minutos.** Suficiente para uso normal, pero si el usuario tarda más (ej. está pensando), el consent vence. Mensaje es claro ("Volvé a pedirla.") pero la fricción existe.
4. **Validación con persona ciega real** sigue siendo el bloqueante para producto. Build/tests verdes ≠ feature funciona para la usuaria.
5. **Smoke test E2E del flujo completo de consent en hardware** requiere un input de voz/texto en HomeScreen para disparar "qué dice la pantalla" — hoy la UI tiene botones, no texto libre. La validación full pasará a hacerse en dispositivo físico cuando el usuario pueda hablar.

---

## Próximo paso recomendado

Probar en Samsung Galaxy S23+ con TalkBack activo:

1. Activar `OjoClaroAccessibilityService` en Ajustes.
2. Abrir un chat de WhatsApp con un mensaje visible.
3. Disparar "qué dice la pantalla" (vía un futuro input de voz, o vía adb shell input text).
4. Verificar que el TTS habla la frase de consentimiento.
5. Decir "confirmar" → la app lee el texto visible.
6. Repetir con "cancelar" para verificar que vuelve a Listo sin leer nada.
7. Repetir tocando Callar mientras se habla la frase de consentimiento → debe limpiar pending y volver a IDLE.
