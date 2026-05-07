# Pro Agent Next Fix Prompt

Usar GPT-5.4-Mini con reasoning medium. No usar modelos mas caros salvo bloqueo real.

Trabajar sobre:

`C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp`

Actuar como arquitecto senior Android/Kotlin especializado en agentes personales voice-first, accesibilidad para personas mayores/no videntes, GPT mini fallback, seguridad, confirmaciones estrictas, UX conversacional calida y QA fisico.

## Objetivo

Cerrar la Fase 1 de estabilidad critica detectada en `docs/PRO_AGENT_READINESS_AUDIT.md`.

No agregar features nuevas. No conectar APIs nuevas. No tocar la API key. No leer ni imprimir `tools/ojo_claro_ai_proxy\.env`. No meter secretos en Android. No automatizar taps en WhatsApp. No enviar WhatsApp automaticamente. No llamar automaticamente. No agregar `READ_CONTACTS`, `CALL_PHONE`, `ACTION_CALL` ni `ACCESS_BACKGROUND_LOCATION`.

Regla central:

GPT mini interpreta y redacta. Ojo Claro decide, valida, confirma y ejecuta.

## Tarea 1: corregir mojibake user-facing

Auditar y corregir strings corruptas en:

- `androidApp/src/main/java/com/ojoclaro/android/domain/PersonalAgentDecisionEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/message/LocalMessageTemplateComposer.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/VoiceCommandController.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/WhatsAppIntentHelper.kt`
- `androidApp/src/main/java/com/ojoclaro/android/phone/PhoneActionExecutor.kt`
- `androidApp/src/main/java/com/ojoclaro/android/maps/MapsActionExecutor.kt`
- `androidApp/src/main/java/com/ojoclaro/android/privacy/PrivacyGuard.kt`
- `androidApp/src/main/java/com/ojoclaro/android/risk/RiskDetector.kt`
- `androidApp/src/main/java/com/ojoclaro/android/qa/VoiceTrainingDataset.kt`
- `tools/ojo_claro_ai_proxy/server.mjs`

Corregir secuencias mojibake conocidas para que los textos queden en UTF-8 real con signos, acentos, eñes y guiones correctos.

Mantener respuestas cortas y humanas.

Agregar test o verificacion por `rg` para detectar restos obvios:

`rg -n "[patrones mojibake]" androidApp/src/main/java tools/ojo_claro_ai_proxy/server.mjs`

## Tarea 2: hacer ejecutable el flujo LLM/Human Message Composer

Problema:

`PersonalAgentDecision.ComposeHumanMessage` hoy publica "Te propongo..." y deja `AppState.WAITING_CONFIRMATION`, pero no crea un `PendingConfirmation` real. Entonces el usuario puede decir "confirmar" y no hay accion garantizada.

Objetivo:

Cuando GPT mini o el compositor local propongan mensaje, debe quedar un pending confirmable con:

- contacto;
- mensaje propuesto;
- tipo `COMPOSE_WHATSAPP_MESSAGE`;
- confirmacion estricta;
- ejecucion final por la misma ruta segura existente que no envia automaticamente.

Opciones aceptables:

1. Extender `PersonalAgentDecision.ComposeHumanMessage` para incluir `contactName` y crear `PendingConfirmation` en `HomeViewModel`.
2. O delegar el texto final al `AssistantOrchestrator` para que cree el pending real.

No duplicar logica insegura. Reusar `PendingConfirmation`, `ExternalCommandType.COMPOSE_WHATSAPP_MESSAGE`, `WhatsAppIntentHelper.composeMessage` y `PrivacyGuard`.

## Tarea 3: conectar RequestConfirmation/SuggestAction a acciones reales o no prometer ejecucion

Si `PersonalAgentDecision.RequestConfirmation` no tiene pending real, no debe sonar como que se puede confirmar.

Regla:

- Si hay accion ejecutable, crear pending.
- Si solo es sugerencia, preguntar de forma no confirmatoria: "¿Querés que lo prepare?" y luego construir el intent/pending cuando el usuario responda con frase completa.
- "si", "dale", "ok", "bueno" nunca confirman.
- Solo "confirmar", "confirmo", "aceptar" confirman pending real.

## Tarea 4: tests end-to-end

Agregar/actualizar tests:

- `PersonalAgentDecisionEngineTest`
  - "decile a Sofi que llego tarde pero decilo bien" produce compose con contacto y mensaje propuesto.
  - Respuesta LLM de compose nunca marca auto-send.

- `HomeViewModelTest` o equivalente existente
  - frase humana con GPT/local composer -> propuesta -> estado waiting confirmation -> pending real existe.
  - "si" no confirma.
  - "dale" no confirma.
  - "confirmar" ejecuta `ComposeWhatsAppMessage` sin enviar automaticamente.

- `LlmSafetyPolicyTest`
  - response con action prohibida se rechaza o se degrada.
  - response con `shouldExecuteImmediately=true` en WhatsApp/Maps/Phone queda false.

- Regression:
  - WhatsApp Guided Mode sigue sin abrir WhatsApp con "abri wp" si no hay continuidad.
  - llamadas siguen `ACTION_DIAL`.
  - manifest sigue sin permisos prohibidos.

## Tarea 5: proxy prompt limpio

Corregir encoding del `SYSTEM_PROMPT` en `tools/ojo_claro_ai_proxy/server.mjs`.

Mantener:

- no imprimir key;
- no `reasoning`;
- JSON estricto;
- `shouldExecuteImmediately=false` para WhatsApp/Maps/Telefono;
- recorte de input/memory;
- fallback seguro.

Ejecutar:

`cd tools\ojo_claro_ai_proxy`

`node --test`

## Tarea 6: validacion final

Ejecutar desde raiz:

`.\gradlew.bat :androidApp:testDebugUnitTest --console=plain`

`.\gradlew.bat :androidApp:assembleDebug --console=plain`

`.\gradlew.bat :shared:allTests --console=plain`

Verificar seguridad:

`rg -n "OPENAI_API_KEY|sk-" androidApp/src/main androidApp/src/test androidApp/build.gradle.kts`

`rg -n "android.permission.READ_CONTACTS|android.permission.CALL_PHONE|android.permission.ACCESS_BACKGROUND_LOCATION|android.intent.action.CALL" androidApp/src/main/AndroidManifest.xml`

Esperado:

- sin API key en Android;
- sin permisos prohibidos;
- sin ACTION_CALL;
- tests verdes;
- APK debug generada.

## Entrega final

Responder con:

- resumen ejecutivo;
- archivos modificados;
- bugs criticos corregidos;
- tests ejecutados;
- APK generada;
- seguridad verificada;
- si el flujo "decile a Sofi que llego tarde pero decilo bien" ya queda confirmable end-to-end;
- proximos 3 pasos para QA fisica en Samsung.
