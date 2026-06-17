# Agent Phase 2A Phone Calls Report

Fecha: 2026-05-06

## Resumen

Se implementó Fase 2A: llamadas seguras usando `ACTION_DIAL`. La app entiende
comandos de teléfono, prepara el marcador del sistema y mantiene confirmación
estricta antes de abrir un número asociado a un contacto. No se agregó
`ACTION_CALL`, no se pidió `CALL_PHONE` y no se hacen llamadas automáticas.

## Qué se implementó

- Nuevo paquete `androidApp/src/main/java/com/ojoclaro/android/phone/`.
- `PhoneActionExecutor`, con:
  - `buildDialIntent(phoneNumber: String?)`;
  - `buildDialIntentSpec(phoneNumber: String?)` para tests unitarios puros;
  - `openDialer()`;
  - `prepareCall(contactName, phoneNumber)`;
  - sanitización y extracción local de números seguros.
- Nuevos eventos externos:
  - `ExternalActionEvent.OpenPhone`;
  - `ExternalActionEvent.DialPhoneNumber(contactName, phoneNumber?)`.
- Nuevos tipos internos en `ExternalCommandType`:
  - `OPEN_PHONE`;
  - `CALL_CONTACT`.
- `LocalIntentParser` ahora reconoce:
  - `llamá a un contacto`;
  - `llama a mamá`;
  - `llamar a mi contacto de emergencia`;
  - `llamar`;
  - `abrí teléfono`;
  - `abrir teléfono`.
- `AgentConversationManager` ahora pregunta contacto faltante para llamadas:
  - `¿A quién querés llamar?`
- `AssistantOrchestrator` ahora:
  - abre Teléfono sin número para `OPEN_PHONE`;
  - pide confirmación para `CALL_CONTACT` solo si hay número resuelto;
  - no inventa números;
  - usa memoria local `TRUSTED_CONTACT` aprobada si contiene un número seguro;
  - prepara `911` para `llamá a emergencias` con aviso responsable;
  - mantiene `sí`, `si` y `dale` como no confirmatorios.
- `HomeScreen` ejecuta los nuevos eventos mediante `PhoneActionExecutor`.

## Qué NO se implementó

- No se implementó `ACTION_CALL`.
- No se agregó permiso `CALL_PHONE`.
- No se accedió a contactos del sistema.
- No se agregó resolución por `ContactsContract`.
- No se implementaron Maps, Spotify, recordatorios ni IA cloud.
- No se cambió WhatsApp ni su confirmación estricta.
- No se tocó iOS.

## Por qué ACTION_DIAL y no ACTION_CALL

`ACTION_DIAL` abre el marcador con un número preparado, pero la llamada queda en
manos del usuario. Esto evita llamadas accidentales, no requiere `CALL_PHONE` y
mantiene el contrato asistivo: Ojo Claro puede preparar, nunca llamar solo.

`ACTION_CALL` iniciaría una llamada directamente y exigiría permiso sensible.
Queda explícitamente fuera de esta fase.

## Comandos soportados

- `abrí teléfono`
- `abrir teléfono`
- `llamá a un contacto`
- `llama a un contacto`
- `llamar a un contacto`
- `llamá a mamá`
- `llamar`
- `llamá a mi contacto de emergencia`
- `llamá a emergencias`

## Archivos tocados

- `androidApp/src/main/java/com/ojoclaro/android/phone/PhoneActionExecutor.kt`
- `androidApp/src/main/java/com/ojoclaro/android/phone/ContactResolver.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/LocalIntentParser.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentConversationManager.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/SafetyPolicy.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/ExternalActionEvent.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/ExternalCommand.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/CommandRouter.kt`
- `androidApp/src/main/java/com/ojoclaro/android/domain/AssistantOrchestrator.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt`
- `androidApp/src/test/java/com/ojoclaro/android/phone/PhoneActionExecutorTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/LocalIntentParserTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/LocalIntentParserVariantsTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/AgentConversationManagerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/SafetyPolicyTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/domain/AssistantOrchestratorTest.kt`
- `docs/AGENT_PHASE_2A_PHONE_CALLS_REPORT.md`

## Seguridad

- Si falta contacto: pregunta `¿A quién querés llamar?`.
- Si no hay número guardado: `No tengo un número guardado para un contacto. Abrí Teléfono para elegirlo.`
- Si hay número guardado seguro: pide confirmación antes de abrir el marcador.
- Confirmar solo con `confirmar`, `confirmo`, `aceptar`.
- `sí`, `si` y `dale` no confirman.
- Cancelar con `cancelar`, `cancela`, `no`, `anular`.
- Emergencias incluyen: `Si estás en peligro, intentá llamar a emergencias o pedir ayuda cercana.`

## Tests

Nuevos o extendidos:

- `LocalIntentParserTest`
  - llamadas con contacto;
  - mamá/contacto de emergencia;
  - abrir teléfono;
  - llamada sin contacto;
  - confirmación estricta.
- `AgentConversationManagerTest`
  - contacto faltante en llamada;
  - cancelar limpia `WAITING_CONTACT`;
  - nuevo comando abandona pending de llamada.
- `PhoneActionExecutorTest`
  - usa `ACTION_DIAL`;
  - no usa `ACTION_CALL`;
  - `tel:` URI correcta;
  - número nulo abre marcador sin número;
  - no requiere `CALL_PHONE`;
  - maneja `ActivityNotFoundException`;
  - maneja `SecurityException`.
- `AssistantOrchestratorTest`
  - abrir teléfono emite evento;
  - llamada sin número no inventa;
  - llamada con memoria pide confirmación;
  - confirmar emite `DialPhoneNumber`;
  - cancelar no abre nada;
  - `sí` no confirma;
  - emergencias usan `911` y aviso responsable.

## Resultado build

- `.\gradlew.bat :androidApp:testDebugUnitTest`
  - 349 tests, 0 failures, 0 errors, 0 skipped.
- `.\gradlew.bat :androidApp:assembleDebug`
  - BUILD SUCCESSFUL.
- `.\gradlew.bat :shared:allTests`
  - 8 tests, 0 failures, 0 errors, 0 skipped.

## Resultado emulador / dispositivo

- `adb devices`
  - Sin dispositivos listados.
- No se ejecutó instalación ni logcat en emulador o físico porque no había
  `emulator-5554` ni `R5CW22SMWDM` conectados.

APK generada:

- `androidApp/build/outputs/apk/debug/androidApp-debug.apk`
- Tamaño observado: 57,368,695 bytes.

Warnings observados:

- Kotlin Multiplatform informa que AGP 8.7.3 está por encima del máximo probado
  por el plugin Kotlin.
- Targets iOS de Kotlin/Native aparecen deshabilitados en esta máquina.

No bloquearon tests ni APK.

## Pendientes

- Resolver contactos reales vía `ContactsContract` con permiso explícito
  `READ_CONTACTS`, si una fase futura lo aprueba.
- Agregar un tipo de memoria dedicado a contacto telefónico/emergencia si se
  quiere persistir números con mejor semántica que `TRUSTED_CONTACT`.
- Hacer configurable el número de emergencia por locale.
- Fase 2B: resolución de contactos más rica, sin `ACTION_CALL`.
