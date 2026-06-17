# Agent Phase 1 Intent/State Report

Fecha: 2026-05-06

## Resumen

Se implemento solo la Fase 1 del documento maestro: modelo base de intenciones,
slots, estados, eventos, outcomes, parser local y manager conversacional de alto
nivel. La ejecucion Android existente sigue pasando por `AssistantOrchestrator`,
`CommandRouter`, `ConsentManager`, `PrivacyGuard` y los helpers ya probados.

## Que se implemento

- Nuevo paquete `androidApp/src/main/java/com/ojoclaro/android/agent/`.
- `AgentIntent`: enum base con las intenciones actuales y futuras preparadas.
- `AgentSlot`: modelo simple `name/value/confidence/isSensitive` y nombres de
  slots conocidos.
- `AgentState`: estados conversacionales nuevos y adaptadores con `AppState`.
- `AgentEvent`: eventos de voz, permisos, confirmacion, cancelacion y errores.
- `AgentOutcome`: salida pura del agente, sin ejecutar Android directamente.
- `LocalIntentParser`: parser heuristico local que reutiliza `CommandRouter`
  para WhatsApp, pantalla y memoria, y agrega HELP, STOP_SPEAKING y READ_OCR_TEXT.
- `AgentConversationManager`: maneja estado de alto nivel para contacto/mensaje
  faltante, cancelacion, confirmacion sin pending, errores recuperables y callar.
- Integracion minima en `HomeViewModel`: cuando falta contacto o mensaje, el
  manager pregunta y conserva el contexto; al completarse el mensaje, reconstruye
  el comando y lo devuelve al `AssistantOrchestrator` actual para que genere la
  confirmacion estricta y el pending real.

## Que NO se implemento

- No se agrego IA cloud.
- No se agregaron recordatorios.
- No se agregaron Maps, ubicacion ni navegacion.
- No se agrego Spotify ni controles de musica nuevos.
- No se agregaron llamadas ni `ACTION_DIAL`.
- No se cambio `WhatsAppIntentHelper`.
- No se cambio la politica de confirmacion estricta.
- No se envio WhatsApp automaticamente.
- No se toco iOS.

## Convivencia con AssistantOrchestrator

`LocalIntentParser` ahora participa como capa de normalizacion antes de decidir
si un texto entra a los flujos actuales. Para ejecucion real, la app sigue usando
`AssistantOrchestrator`.

El nuevo `AgentConversationManager` no lanza intents ni persiste datos. Solo
resuelve slots faltantes. Cuando obtiene un `COMPOSE_WHATSAPP_MESSAGE` completo,
`HomeViewModel` arma un comando legacy equivalente (`mandale a contacto: mensaje`)
y lo procesa con el orquestador existente. Eso conserva:

- confirmacion obligatoria;
- `confirmar` / `confirmo` / `aceptar` como unicas confirmaciones;
- bloqueo de `si`, `sí` y `dale`;
- bloqueo de mensajes sensibles via `PrivacyGuard`;
- garantia de no auto-envio.

## Tests nuevos

- `LocalIntentParserTest`
  - HELP, STOP_SPEAKING, CONFIRM, CANCEL.
  - OPEN_WHATSAPP.
  - COMPOSE_WHATSAPP_MESSAGE completo.
  - Missing slot `contactName`.
  - Missing slot `messageText`.
  - `si`, `sí` y `dale` no confirman.
  - Mensajes con codigo se marcan sensibles.
  - Lectura OCR, lectura visible y memoria actual.

- `AgentConversationManagerTest`
  - Pregunta contacto faltante.
  - Pregunta mensaje faltante.
  - Cancelar limpia pending.
  - Confirmar sin pending responde claro.
  - Mensaje sensible no queda colgado y permite nuevo comando.
  - Errores recuperables reescuchan.
  - Callar entra en `STOPPED_BY_USER`.
  - Completar contacto + mensaje no ejecuta Android directamente.

## Resultados de validacion

- `.\gradlew.bat :androidApp:testDebugUnitTest`
  - 296 tests, 0 failures, 0 errors, 0 skipped.
- `.\gradlew.bat :androidApp:assembleDebug`
  - BUILD SUCCESSFUL.
- `.\gradlew.bat :shared:allTests`
  - 8 tests, 0 failures, 0 errors, 0 skipped.

APK generada:

- `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Tamano observado: 57,368,695 bytes.

Warnings observados:

- Kotlin Multiplatform informa que AGP 8.7.3 esta por encima del maximo probado
  por el plugin Kotlin.
- Targets iOS de Kotlin/Native aparecen deshabilitados en esta maquina.

Ambos son warnings existentes/de entorno; no bloquearon tests ni APK.

## Proximos pasos recomendados

1. Fase 2: conectar llamadas con `ACTION_DIAL`, confirmacion estricta y tests.
2. Agregar transiciones UI explicitas para `WAITING_CONTACT` y `WAITING_MESSAGE`
   cuando se quiera reemplazar el mapeo temporal a `AppState.ERROR`.
3. Unificar gradualmente `AgentOutcome` y `OrchestratorOutcome` con un adapter
   formal si el orquestador empieza a hablar directamente en terminos de agente.
4. Extender `LocalIntentParser` solo por fase: llamadas en Fase 2, Maps en Fase 3,
   musica en Fase 4, recordatorios en Fase 5.

## Riesgos

- `AgentConversationManager` ya puede modelar estados mas ricos que `AppState`,
  pero la UI todavia no tiene estados visuales dedicados para todos ellos.
- El slot filling de contacto/mensaje es intencionalmente simple; resolucion de
  alias por memoria queda para una fase posterior.
- La integracion mantiene el camino legacy para no romper seguridad, por lo que
  durante una fase conviven parser/manager nuevo y `CommandRouter`.
