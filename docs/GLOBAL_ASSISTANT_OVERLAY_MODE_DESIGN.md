# Global Assistant Overlay Mode - Design

## 1. Que permite Accessibility overlay

Un `AccessibilityService` puede mostrar una superficie visible asociada a accesibilidad usando ventanas especiales del sistema. En Ojo Claro hoy el servicio de accesibilidad se mantiene conservador: lee texto visible solo cuando el usuario lo pide y no ejecuta taps ni gestos.

Para este MVP usamos un overlay propio de aplicacion cuando el permiso de "mostrar sobre otras apps" esta disponible. El overlay muestra controles de Ojo Claro y no navega WhatsApp, Maps ni Telefono.

## 2. Que permite foreground service

Un foreground service permite que Ojo Claro mantenga una tarea visible mientras el usuario esta en otra app. En este modo la app muestra una notificacion persistente, declara el tipo de servicio de microfono y expone acciones claras: Escuchar, Callar y Detener.

El servicio no graba audio. Solo intenta usar `SpeechRecognizer` para una ventana corta de continuacion y mantiene todo visible para el usuario.

## 3. Que bloquea Android con microfono en background

Android puede bloquear o volver inestable el uso del microfono cuando la app no esta visible, incluso con foreground service, especialmente por reglas de permisos "while-in-use", politicas del fabricante o restricciones de bateria. Tambien puede devolver errores como `SecurityException`, `ERROR_AUDIO`, `ERROR_CLIENT`, `ERROR_RECOGNIZER_BUSY` o fallas del servicio de voz.

El MVP debe degradar sin crash: si el microfono no arranca, se mantiene overlay/notificacion y se guia al usuario a tocar Escuchar o volver a Ojo Claro.

## 4. Permisos necesarios

Permisos usados:

- `RECORD_AUDIO`: ya existia para comandos de voz.
- `POST_NOTIFICATIONS`: ya existia para notificacion visible en Android 13+.
- `FOREGROUND_SERVICE`: necesario para ejecutar el servicio visible.
- `FOREGROUND_SERVICE_MICROPHONE`: necesario en Android moderno para declarar servicio de microfono.
- `SYSTEM_ALERT_WINDOW`: solo para el overlay normal sobre otras apps; si no esta concedido, se degrada a notificacion sin crash.

Permisos no usados:

- `READ_CONTACTS`
- `CALL_PHONE`
- `ACCESS_BACKGROUND_LOCATION`

## 5. Que se puede probar en Samsung

En el Samsung `R5CW22SMWDM` se puede probar:

- Abrir WhatsApp desde Ojo Claro y ver notificacion persistente.
- Ver overlay "Ojo Claro activo" si el permiso de overlay esta concedido.
- Decir un contacto durante los 60 segundos de continuacion.
- Confirmar solo con "confirmar", "confirmo" o "aceptar".
- Verificar que "si", "dale", "ok" y "bueno" no confirman.
- Detener el modo y validar que overlay/notificacion desaparecen.

## 6. Que queda para VoiceInteractionService futuro

Un modo asistente global mas nativo podria usar `VoiceInteractionService` o integraciones de asistente del sistema. Eso requiere otro contrato de producto, permisos/configuracion especifica y evaluacion de Play Protect. Este MVP no intenta reemplazar al asistente del sistema ni escuchar con hotword permanente.

## 7. Riesgos Play Protect

Riesgos principales:

- Overlay sobre apps sensibles puede generar sospecha si no es claramente visible.
- Foreground service de microfono debe tener finalidad accesible y visible.
- El permiso de overlay debe pedirse con explicacion humana, no como requisito oculto.
- Cualquier automatizacion de WhatsApp por taps seria riesgosa; queda fuera de alcance.

## 8. Riesgos de bateria

El microfono y `SpeechRecognizer` consumen bateria. Por eso el modo de continuacion dura 60 segundos, se corta con Detener, y Callar pausa TTS/microfono sin borrar memoria.

## 9. Fallback si Android bloquea el microfono

Si el microfono no funciona en background:

- No se crashea.
- Se mantiene notificacion visible.
- Si hay permiso de overlay, queda el control flotante.
- El usuario puede tocar Escuchar para volver a Ojo Claro con `ACTION_START_LISTENING`.
- Ojo Claro dice: "Para seguir, toca Escuchar o volve a Ojo Claro."
