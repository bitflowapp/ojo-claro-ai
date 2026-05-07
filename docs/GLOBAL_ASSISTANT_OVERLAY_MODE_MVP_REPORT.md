# Global Assistant Overlay Mode MVP Report

## 1. Resumen ejecutivo

Se implemento un MVP experimental de `External App Continuation Mode` para Ojo Claro: cuando la app hace handoff a WhatsApp, Maps o Telefono, arranca un foreground service visible, muestra notificacion persistente y, si el permiso de overlay esta concedido, muestra un overlay pequeno con controles.

La meta no es esconder escucha ni automatizar WhatsApp. La meta es dar una ventana corta, visible y honesta de continuidad, con degradacion segura si Android bloquea el microfono en background.

## 2. Que modo se implemento

Modo implementado:

- `GlobalAssistantService`: foreground service visible con tipo `microphone`.
- `GlobalAssistantOverlayController`: overlay propio con botones Escuchar, Callar y Detener.
- `GlobalAssistantNotifier`: notificacion persistente "Ojo Claro activo".
- `ExternalConversationContext`: contexto de app externa con TTL de 60 segundos.
- Integracion desde handoff externo en `HomeScreen`.

Flujo WhatsApp principal actualizado:

- "abri wp" ahora abre WhatsApp y arranca continuacion global.
- Ojo Claro dice: "Abro WhatsApp. Puedo seguir por unos segundos. Decime el chat o el mensaje."
- En WhatsApp, intenta seguir escuchando durante la ventana visible.
- Si escucha "Marco Antonio", pregunta si abrir chat o mandar mensaje.
- Si luego escucha "decile que llego en 10", usa el contacto contextual y prepara mensaje con confirmacion.
- "si", "dale", "ok" y "bueno" no confirman.
- Solo "confirmar", "confirmo" o "aceptar" confirman segun la politica existente.

## 3. Permisos agregados

Se agregaron:

- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.SYSTEM_ALERT_WINDOW`

`POST_NOTIFICATIONS` y `RECORD_AUDIO` ya existian.

## 4. Permisos NO agregados

No se agregaron:

- `READ_CONTACTS`
- `CALL_PHONE`
- `ACCESS_BACKGROUND_LOCATION`

Tampoco se agrego `ACTION_CALL`. Las llamadas siguen usando marcador seguro y WhatsApp no envia mensajes automaticamente.

## 5. Background mic en Samsung

No probado en Samsung en esta corrida.

Resultado ADB:

```text
List of devices attached
```

No aparecio `R5CW22SMWDM`, por lo que no se instalo la APK ni se valido si el microfono funciona dentro de WhatsApp/Maps en el dispositivo real.

## 6. Overlay

Implementado con `TYPE_APPLICATION_OVERLAY`.

Si `SYSTEM_ALERT_WINDOW` no esta concedido:

- No crashea.
- No muestra overlay.
- Mantiene foreground service/notificacion.
- El fallback recomendado es tocar Escuchar o volver a Ojo Claro.

## 7. Notificacion

Implementada como notificacion persistente:

- Titulo: "Ojo Claro activo".
- Acciones: Escuchar, Callar, Detener.
- El tap principal vuelve a `MainActivity` con `ACTION_START_LISTENING`.
- Escuchar intenta rearmar escucha desde el foreground service.
- Callar corta TTS y pausa microfono sin borrar contexto.
- Detener corta TTS, detiene microfono, limpia pending externo y apaga overlay/notificacion.

## 8. Tests

Se agregaron tests para:

- inicio/expiracion TTL del contexto externo;
- Callar sin limpiar contexto;
- Detener limpiando contacto, mensaje y pending;
- deteccion de handoff WhatsApp con continuacion;
- confirmacion estricta;
- extraccion contextual de contacto y mensaje;
- "si"/"dale" como ruido no confirmante;
- seguridad de manifest sin permisos prohibidos.

Validacion ejecutada:

```text
.\gradlew.bat :androidApp:testDebugUnitTest --console=plain
BUILD SUCCESSFUL

.\gradlew.bat :androidApp:assembleDebug --console=plain
BUILD SUCCESSFUL

.\gradlew.bat :shared:allTests --console=plain
BUILD SUCCESSFUL
```

Advertencias no bloqueantes:

- compatibilidad Kotlin Multiplatform / Android Gradle Plugin;
- targets iOS deshabilitados en Windows;
- `LocalLifecycleOwner` deprecado.

## 9. APK

APK generada:

```text
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Tamano observado:

```text
57368754 bytes
```

## 10. Logcat

No hubo logcat final de Samsung porque no habia dispositivo conectado por ADB.

## 11. Limitaciones

- Android puede bloquear `SpeechRecognizer` en background aunque el foreground service este activo.
- El overlay requiere permiso de "mostrar sobre otras apps".
- No hay hotword permanente.
- No hay escucha oculta.
- No hay taps ni navegacion automatica dentro de WhatsApp.
- No se promete seguridad peatonal en Maps.
- El modo expira tras 60 segundos.

## 12. Riesgos Play Protect

Riesgos mitigados:

- foreground service visible;
- notificacion persistente;
- overlay visible;
- sin grabacion;
- sin READ_CONTACTS;
- sin CALL_PHONE;
- sin ACCESS_BACKGROUND_LOCATION;
- sin envio automatico de WhatsApp.

Riesgos restantes:

- overlay sobre apps externas puede requerir explicacion clara de accesibilidad;
- foreground service de microfono debe seguir siendo opt-in, visible y de corta duracion;
- si se quisiera un modo asistente global real, conviene evaluar `VoiceInteractionService`.

## 13. Proximo paso

Proximos pasos recomendados:

1. Instalar en Samsung y probar si el microfono funciona con WhatsApp abierto.
2. Si el microfono no funciona, validar que la notificacion/overlay permiten volver a Ojo Claro sin crash.
3. Evaluar `VoiceInteractionService` o una UX de retorno mas fuerte antes de intentar automatizacion avanzada.
