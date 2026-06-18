# Privacy Logging Audit — FASE 0 (2026-06-18)

Hardening de logs de diagnóstico para que Estela/Ojo Claro no filtre datos
privados (STT, pantalla, OCR, WhatsApp, contactos, teléfonos, params de intent).
Rama `chore/unattended-hardening-sprint`. Sin commit.

> Nota: ya existía `docs/PRIVACY_LOGGING_AUDIT.md` (sprint anterior, foco distinto).
> Este archivo es la auditoría específica de **logs de diagnóstico** y vive en raíz.

## 1. Resumen ejecutivo
La disciplina de logging del proyecto ya era **mayormente segura**: las rutas de
voz/WhatsApp/pantalla loguean **metadatos** (`len=`, `count=`, `Present`,
categorías, flags) y existen sanitizadores dedicados (`VisibleNodeSanitizer`,
`SafeAiFallbackLogger`, `estelaTraceSpeechLabel`, `RobotLoopInstrumentation.commandRedacted`).
La auditoría encontró **un (1) leak real**: el motor STT, en builds **debug**,
logueaba el **texto reconocido crudo** (candidatos + seleccionado, truncado a 80
chars) a través de un helper mal llamado "safe". Se corrigió, se centralizó un
logger seguro (`SafeLog`) y se agregó una **barrera anti-regresión** (test + script).

## 2. Riesgo detectado
- **HIGH (corregido):** `AndroidSpeechInputEngine.safeSpeechCandidateForLog` /
  `safeSpeechCandidatesForLog` emitían el texto STT crudo en `Log.i(FLOW_TAG, "stt_final …")`
  bajo `if (BuildConfig.DEBUG)`. Ejemplos prohibidos que se filtraban en debug:
  `candidates=["leé mensajes de banco", …]`, `selected="mandale a mi novia que ya voy"`.
  En **release** ya era seguro (solo `sttResultPresent` + `sttLength`).
- **LOW (aceptado):** `HomeViewModel` loguea `error.message` de excepciones de red
  (`Log.e("Backend", …)`). Es el mensaje técnico de la excepción, no contenido del
  usuario; se deja, pero queda anotado.

## 3. Archivos revisados
- `androidApp/src/main/.../voice/AndroidSpeechInputEngine.kt` (STT) ← leak.
- `androidApp/src/main/.../global/GlobalAssistantService.kt` (routing/WhatsApp/LLM).
- `androidApp/src/main/.../accessibility/OjoClaroAccessibilityService.kt` (pantalla/nodos).
- `androidApp/src/main/.../agent/intelligence/VisibleNodeSanitizer.kt` (dump de nodos) — ya seguro.
- `androidApp/src/main/.../ui/home/HomeViewModel.kt` (foreground, `logEstelaTrace`) — ya seguro (clasifica, no vuelca).
- `androidApp/src/main/.../llm/SafeAiFallbackLogger.kt` + `performance/RobotLoopInstrumentation.kt` — ya seguros (redact / commandRedacted / metadatos).
- `tools/ojo_claro_ai_proxy`, `scripts/*.ps1` — sin logging de contenido de usuario en runtime (los scripts solo traen frases de QA hardcodeadas).

## 4. Logs peligrosos encontrados
| Severidad | Sitio | Qué filtraba | Estado |
|---|---|---|---|
| HIGH | `AndroidSpeechInputEngine` `stt_final` (debug) | texto STT crudo (candidatos + seleccionado) | **CORREGIDO** |
| LOW | `HomeViewModel` `Log.e("Backend", error.message)` | mensaje de excepción de red | aceptado / anotado |

No se encontraron logs crudos de: `userText`, `normalizedText`, `rawText`,
`recognizedText`, `visibleText`, `ocrText`, `messageText`, `contactName`,
`chatName`, `phoneNumber`, ni params de intent con valores.

## 5. Cambios aplicados
1. **STT (fix del leak):** `safeSpeechCandidatesForLog`/`safeSpeechCandidateForLog`
   ahora emiten **solo metadatos** (`candidatesCount`, `topCandidateLen`, `selectedLen`),
   nunca el texto, ni en debug. Línea `stt_final` actualizada en consecuencia.
2. **Logger seguro centralizado** `com.ojoclaro.android.logging.SafeLog`:
   - Canales: `voice / intent / screen / whatsapp / security / error`.
   - Helpers puros: `textLen`, `safeCount`, `shortHash` (token de igualdad NO
     reversible, 4096 buckets), `redact`, `safeIntentParams` (claves + longitudes,
     sin valores), `safePackageName` (categoría de app; terceros → `other_app`).
   - `error()` nunca pasa el `throwable` a `Log` (evita volcar el message).
3. **Barrera anti-regresión:**
   - Test `PrivacyLoggingGuardTest` (escanea `androidApp/src/main`): falla si un log
     interpola una variable cruda sensible sin acotarla a metadatos; y verifica que
     el helper de STT no vuelva a truncar/loguear texto crudo.
   - Scripts `tools/check_privacy_logs.sh` y `tools/check_privacy_logs.ps1`.

No se tocó comportamiento funcional: WhatsApp, navegación por accesibilidad, taps
sintéticos, confirmaciones, envío ni borradores quedaron **intactos** (solo cambió
el **contenido textual** de líneas de log).

## 6. Qué queda PERMITIDO loguear
- `event=stt_received len=34`, `stt_final candidatesCount=3 topCandidateLen=20 selectedLen=29`.
- `event=intent_resolved intent=READ_SCREEN confidence=0.91`.
- `event=screen_snapshot activePackage=whatsapp visibleNodes=23`.
- `event=whatsapp_route route=OPEN_CHAT safety=read_only`.
- `event=pending_action type=OPEN_CHAT ttlMs=30000`, `event=confirmation_required action=SEND_DRAFT safety=draft`.
- `event=backend_intent status=success latencyMs=840`.
- Categorías, flags booleanos, conteos, longitudes, `shortHash` (igualdad), `safePackageName`.

## 7. Qué queda PROHIBIDO loguear (release Y debug)
- Texto crudo del usuario / `normalizedText` / candidatos STT completos.
- Texto visible de pantalla / OCR crudo / texto de nodos de accesibilidad.
- Mensajes de WhatsApp, nombres reales de contactos, nombres de chat, teléfonos.
- Params de intent con sus **valores**.
- Material bancario, códigos de verificación, claves, tokens, mensajes privados.

## 8. Cómo correr la verificación
```bash
# Linux/Git Bash
bash tools/check_privacy_logs.sh
# Windows PowerShell
powershell -File tools/check_privacy_logs.ps1
# Como test (corre en el build):
./gradlew.bat :androidApp:testDebugUnitTest --tests "*PrivacyLoggingGuardTest" --tests "*SafeLogTest"
```
Exit 0 / verde = sin logs peligrosos. Exit 1 / rojo = lista de archivos:línea.

## 9. Resultado de tests/build
- `./gradlew.bat :androidApp:testDebugUnitTest :androidApp:assembleDebug` → **BUILD SUCCESSFUL** (ver §reporte de sesión).
- `bash tools/check_privacy_logs.sh` → **PASS** (exit 0).
- `check_privacy_logs.ps1` → sintaxis validada (AST parser OK).
- `SafeLogTest` (helpers) + `PrivacyLoggingGuardTest` (barrera) verdes.

## 10. Riesgos pendientes
- La barrera es **token-based**: detecta variables crudas conocidas. Un helper
  *mal llamado "safe"* (como el bug de STT) NO se detecta genéricamente — por eso
  se agregó un check específico para STT. Al introducir nuevos canales, sumá un
  check puntual y usá `SafeLog`.
- `HomeViewModel` aún loguea `error.message` de red (LOW). Mover a `SafeLog.error`
  en una próxima pasada.
- `SafeLog` es un estándar disponible; las rutas existentes **no fueron migradas**
  en masa (ya eran seguras). Migración incremental recomendada, no urgente.
- `shortHash` agrupa por igualdad pero, por diseño, NO permite reconstruir el texto
  (colisiones intencionales). No usar para nada que requiera unicidad.

## 11. Recomendaciones para la próxima fase (ConversationOrchestrator)
- Todo el nuevo pipeline conversacional debe loguear **solo** vía `SafeLog`.
- Nunca enviar al LLM/backend ni loguear: texto visible de chat, OCR, nombres de
  contacto, mensajes. Para contexto de chat → exigir permiso explícito (ya hay
  `SUGGEST_REPLY_ONLY` / `BLOCK_PRIVATE_CONTEXT` en `SafeLlmFallbackPolicy`).
- Para diagnóstico profundo, dejar un flag explícito **apagado por defecto** y, aun
  así, **sin** texto crudo (longitudes/categorías/`shortHash`). Documentar el riesgo.
- Correr `tools/check_privacy_logs.*` (o el test) en CI antes de cada merge.
