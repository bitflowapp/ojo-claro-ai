# Agent Execution Policy and Global Continuation Fix

## 1. Resumen ejecutivo

Se implemento una compuerta central para que Ojo Claro actue como agente y no como launcher ciego de apps externas.

El cambio clave: si el usuario dice "abri wp" y la app no puede garantizar continuidad visible con foreground service, notificacion, overlay y microfono, Ojo Claro no abre WhatsApp. Se queda dentro de Ojo Claro y pregunta:

> Decime: chat de un contacto, mensaje para un contacto, o WhatsApp principal.

Si la continuidad global esta disponible, Ojo Claro puede abrir WhatsApp con modo global visible. Si solo hay fallback de retorno, abre apps principales solo cuando el usuario lo pide explicitamente, por ejemplo "abrir WhatsApp principal".

## 2. Causa raiz

El problema fisico no era solo de parser. El error de diseno era ejecutar una app externa demasiado pronto. Al abrir WhatsApp, Maps o Telefono sin continuidad real, Android deja a Ojo Claro en segundo plano y el usuario queda sin respuesta.

La solucion fue agregar una politica previa a la ejecucion:

1. Entender intencion.
2. Completar slots.
3. Validar seguridad.
4. Confirmar si corresponde.
5. Ejecutar solo si la compuerta permite continuidad o si el usuario pidio app principal explicitamente.

## 3. Que cambio

- Se agrego `AgentExecutionPolicy` con decisiones estructuradas.
- Se agrego `GlobalAssistantCapabilityGate` para decidir si hay continuidad real fuera de la app.
- `abrir wp` ahora entra a modo guiado si no hay continuidad real.
- `abrir WhatsApp principal` puede abrir con advertencia y fallback visible.
- `abrir mapas` respeta la compuerta; si no hay continuidad, pide destino o "mapas principal".
- `abrir telefono` respeta la compuerta de app principal.
- Los estados de espera de WhatsApp ahora son estados de UI propios, no confirmaciones genericas.
- El debug panel muestra decision, pending, capacidades globales, overlay, notificacion, microfono, app externa y TTL.
- El error de voz o fallback tipo "No entendi" no abre apps externas.

## 4. Compuerta de continuidad

`GlobalAssistantCapabilityGate` calcula:

- `foregroundServiceReady`
- `notificationReady`
- `overlayReady`
- `microphoneContinuationReady`
- `fallbackReturnReady`
- `canSafelyContinueOutsideApp`
- `reason`

Reglas aplicadas:

- Si `canSafelyContinueOutsideApp == true`, se permite abrir app externa con modo global visible.
- Si `canSafelyContinueOutsideApp == false`, "abri wp" no abre WhatsApp y queda en modo guiado.
- Si `fallbackReturnReady == true` pero no hay microfono, solo se permite abrir una app principal pedida explicitamente.
- Sin fallback, no se abre app externa desde una intencion incompleta.

## 5. Politica de ejecucion

`AgentExecutionPolicy` devuelve decisiones tipo:

- `AskQuestion`
- `RequestConfirmation`
- `ExecuteExternalAction`
- `StayInApp`
- `RejectUnsafe`
- `RetryListening`
- `Cancel`

Contrato importante:

- `UNKNOWN` no ejecuta app externa.
- "No entendi", "No escuche bien", "Proba de nuevo" y "No pude conectar" no disparan apertura externa.
- "si", "dale", "ok" y variantes no confirman acciones sensibles.
- Solo confirmaciones estrictas siguen validas: "confirmar", "confirmo", "aceptar".

## 6. Sin microfono en background

Si Android no permite continuidad de microfono fuera de Ojo Claro:

- No se promete escucha encima de WhatsApp.
- No se intenta grabar escondido.
- No se usa hotword.
- No se usa AccessibilityService para navegar WhatsApp.
- Se mantiene el flujo guiado dentro de Ojo Claro hasta completar intencion.

Para app principal explicita, la respuesta orienta al retorno:

> Abro WhatsApp principal. Para seguir, toca Escuchar o volve a Ojo Claro.

## 7. Permisos agregados

No se agregaron permisos peligrosos nuevos en esta fase.

Se reutiliza la infraestructura visible ya existente de foreground service, overlay y notificacion para evaluar capacidades.

## 8. Permisos prohibidos ausentes

Verificado en `androidApp/src/main/AndroidManifest.xml`:

- No `READ_CONTACTS`.
- No `CALL_PHONE`.
- No `ACCESS_BACKGROUND_LOCATION`.
- No `ACTION_CALL`.

La app sigue usando preparacion segura y confirmacion, sin enviar mensajes ni llamar automaticamente.

## 9. Tests

Comandos ejecutados:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest --console=plain
.\gradlew.bat :androidApp:assembleDebug --console=plain
.\gradlew.bat :shared:allTests --console=plain
```

Resultados:

- `:androidApp:testDebugUnitTest`: BUILD SUCCESSFUL, 544 tests.
- `:androidApp:assembleDebug`: BUILD SUCCESSFUL.
- `:shared:allTests`: BUILD SUCCESSFUL.

Tests agregados/actualizados:

- `AgentExecutionPolicyTest`
- `GlobalAssistantCapabilityGateTest`
- `ExternalConversationContextTest`
- `AssistantOrchestratorTest`
- `AgentConversationManagerTest`
- `HomeViewModelExternalRoutingTest`

## 10. APK

APK generada:

```text
androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Tamanio verificado: `57,368,754` bytes.

## 11. Resultado Samsung

Se ejecuto:

```powershell
adb devices
```

Resultado:

```text
List of devices attached
```

El Samsung `R5CW22SMWDM` no aparecio conectado, por lo tanto no se instalo ni se hizo QA fisica en esta pasada.

## 12. Limitaciones

- La continuidad real de microfono encima de WhatsApp queda pendiente de validacion fisica en Samsung.
- Si overlay o notificacion no estan listos, Ojo Claro no promete continuidad.
- No se implemento hotword, escucha oculta, taps automaticos ni navegacion de WhatsApp por AccessibilityService.
- Maps y Telefono quedan protegidos por la misma compuerta de app externa.

## 13. Veredicto

Demo-ready para prueba fisica guiada de la compuerta:

- Sin continuidad real: "abri wp" no debe abrir WhatsApp.
- Con continuidad real: "abri wp" puede abrir WhatsApp con overlay/notificacion visible.
- App principal explicita: "abrir WhatsApp principal" abre con warning de retorno.
- Fallbacks o errores de reconocimiento no abren apps externas.

Pendiente: instalar en Samsung y validar si el microfono realmente continua encima de WhatsApp con overlay/foreground service.
