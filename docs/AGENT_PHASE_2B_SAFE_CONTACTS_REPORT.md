# Ojo Claro AI - Fase 2B-A Contactos Seguros

## 1. Resumen ejecutivo

Se implementó Fase 2B-A para que Ojo Claro recuerde contactos seguros en memoria local, sin pedir `READ_CONTACTS` y sin tocar la libreta del sistema. La app ahora puede guardar contactos de confianza, contactos de emergencia, números aprobados por el usuario, listar contactos de confianza sin exponer números y olvidar contactos con confirmación.

La resolución de llamadas usa únicamente memoria local aprobada y mantiene el flujo seguro de Fase 2A: `ACTION_DIAL`, nunca `ACTION_CALL`, sin permiso `CALL_PHONE` y sin llamadas automáticas.

## 2. Qué se implementó

- Nuevo tipo `MemoryType.EMERGENCY_CONTACT`.
- Nuevo helper local `SafeContactMemory` para serializar contactos como:
  - `contact:<nombre>` cuando no hay número;
  - `phone:<numero>` cuando el usuario aprobó guardar un número válido.
- Parser local para:
  - `recordá que un contacto es contacto de confianza`;
  - `mamá es contacto de emergencia`;
  - `guardá el número de un contacto`;
  - `el número de un contacto es 2991234567`;
  - `quiénes son mis contactos de confianza`;
  - `olvidá el contacto un contacto`.
- Nuevas intenciones:
  - `SAVE_CONTACT`;
  - `SAVE_CONTACT_PHONE`;
  - `LIST_CONTACTS`;
  - `DELETE_CONTACT`.
- Nuevos slots:
  - `phoneNumber`;
  - `contactType`.
- Nuevo estado conversacional:
  - `WAITING_PHONE_NUMBER`.
- `AgentConversationManager` ahora puede pedir nombre/número faltante y preparar confirmación conversacional.
- `AssistantOrchestrator` guarda, lista y borra contactos mediante `ConsentManager`.
- `MemoryPolicy` y `PrivacyGuard` validan contactos antes de persistir.
- `ContactResolver` resuelve números desde memoria local aprobada, incluyendo `EMERGENCY_CONTACT`.
- Si falta número para llamada, responde:
  - `No tengo un número guardado para un contacto. Podés decir: el número de un contacto es...`

## 3. Qué NO se implementó

- No se pidió `READ_CONTACTS`.
- No se leyó la libreta del teléfono.
- No se agregó permiso `CALL_PHONE`.
- No se usó `ACTION_CALL`.
- No se hicieron llamadas automáticas.
- No se envían WhatsApp automáticamente.
- No se implementaron Maps, Spotify, recordatorios, IA cloud, BiometricPrompt ni iOS.

## 4. Por qué todavía no usamos READ_CONTACTS

Esta fase busca una base segura y auditable de contactos útiles aprobados explícitamente por el usuario. Pedir `READ_CONTACTS` ampliaría mucho la superficie de privacidad: accedería a toda la libreta, requeriría nuevos permisos peligrosos y agregaría decisiones de desambiguación más complejas.

Por eso Fase 2B-A se limita a memoria local segura: solo se guarda lo que el usuario dicta y confirma.

## 5. Comandos soportados

- `recordá que un contacto es contacto de confianza`
- `recordá que mamá es contacto de emergencia`
- `mamá es contacto de emergencia`
- `guardá el número de un contacto`
- `el número de un contacto es 2991234567`
- `llamá a un contacto`
- `mandale a un contacto que estoy llegando`
- `quiénes son mis contactos de confianza`
- `olvidá el contacto un contacto`

## 6. Archivos tocados

- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentIntent.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentSlot.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentState.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/LocalIntentParser.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentConversationManager.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/SafetyPolicy.kt`
- `androidApp/src/main/java/com/ojoclaro/android/domain/AssistantOrchestrator.kt`
- `androidApp/src/main/java/com/ojoclaro/android/memory/MemoryType.kt`
- `androidApp/src/main/java/com/ojoclaro/android/memory/MemoryPolicy.kt`
- `androidApp/src/main/java/com/ojoclaro/android/memory/LocalMemoryStore.kt`
- `androidApp/src/main/java/com/ojoclaro/android/memory/SafeContactMemory.kt`
- `androidApp/src/main/java/com/ojoclaro/android/phone/ContactResolver.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/LocalIntentParserTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/AgentConversationManagerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/memory/MemoryPolicyTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/privacy/PrivacyGuardTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/domain/AssistantOrchestratorTest.kt`

## 7. Tests nuevos/modificados

- Parser:
  - contacto de confianza;
  - contacto de emergencia;
  - guardar número completo;
  - guardar número con `phoneNumber` faltante;
  - olvidar contacto;
  - listar contactos de confianza.
- Manager conversacional:
  - pregunta número faltante;
  - pregunta nombre faltante;
  - pide confirmación antes de guardar;
  - cancelar limpia pending;
  - confirmar devuelve intención sugerida;
  - borrar contacto pide confirmación.
- Política y privacidad:
  - permite contacto seguro aprobado;
  - permite contacto de emergencia;
  - bloquea número inválido;
  - bloquea número tipo tarjeta;
  - bloquea DNI/CBU/CVU;
  - bloquea contacto sin aprobación.
- Orquestador:
  - guardar número requiere confirmación y luego guarda;
  - llamada usa número guardado con `ACTION_DIAL` vía evento seguro;
  - listar contactos no expone número;
  - borrar contacto requiere confirmación y luego borra.

## 8. Resultados de build/tests

- `.\gradlew.bat :androidApp:testDebugUnitTest`
  - Resultado: OK
  - Tests: 373
  - Fallas: 0
  - Skipped: 0
- `.\gradlew.bat :androidApp:assembleDebug`
  - Resultado: OK
- `.\gradlew.bat :shared:allTests`
  - Resultado: OK
  - Tests shared reportados: 8
  - Fallas: 0

## 9. Resultado emulador/logcat

Se ejecutó `adb devices` y no había dispositivos conectados:

```text
List of devices attached
```

No se instaló en emulador ni físico en esta ronda porque no había dispositivo ADB disponible.

## 10. APK

APK debug generada:

```text
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Tamaño observado:

```text
57,368,695 bytes
```

## 11. Riesgos pendientes

- La app todavía no desambigua múltiples contactos con el mismo nombre de forma conversacional rica.
- Los contactos sin número sirven para listar y para WhatsApp textual, pero no para llamadas.
- `READ_CONTACTS` sigue fuera de alcance; cuando se implemente, debe ir detrás de permisos explícitos y una capa separada de resolución.
- La validación de teléfonos es deliberadamente conservadora y puede rechazar formatos raros aunque sean reales.

## 12. Próximo paso recomendado

Fase 2B-B debería enfocarse en mejorar el flujo de contacto sin permiso:

- permitir actualizar número de un contacto ya guardado;
- listar también contactos de emergencia con frase separada;
- resolver alias simples como `mamá`, `mami`, `un contacto`;
- manejar múltiples coincidencias por nombre;
- preparar diseño futuro de `READ_CONTACTS` sin activarlo todavía.
