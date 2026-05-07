# External App Handoff Mode

Fecha: 2026-05-06

## Problema físico detectado

En QA física se observó que, al decir "abrí wp", Ojo Claro abre WhatsApp correctamente pero queda en segundo plano. Desde ese momento el loop de voz deja de ser confiable: si el usuario dice "buscá el chat de Marco Antonio" dentro de WhatsApp, Ojo Claro no responde.

Esto no era un bug simple del parser. Es una limitación honesta del diseño actual: Ojo Claro escucha de forma confiable cuando está visible.

## Por qué no escuchar escondido todavía

No se implementó escucha oculta en background porque sería riesgoso para privacidad, batería, confianza del usuario y revisión de plataforma. También podría confundirse con un hotword permanente o grabación pasiva.

La fase actual mantiene estas reglas:

- No usar `SpeechRecognizer` en background.
- No grabar audio en background.
- No implementar hotword permanente.
- No pedir `READ_CONTACTS`.
- No navegar WhatsApp con AccessibilityService.
- No hacer taps automáticos.
- No enviar mensajes automáticamente.

## Solución implementada

Se agregó un modo explícito `EXTERNAL_APP_HANDOFF`.

Cuando Ojo Claro va a abrir WhatsApp, Maps o Teléfono:

- Pausa el loop de voz antes de salir.
- Emite un evento `ExternalAppHandoff` con `externalAppName`, `reason`, `returnHint` y la acción delegada.
- Dice una frase corta antes de abrir la app externa.
- Intenta mostrar una notificación persistente "Ojo Claro" con acciones "Escuchar" y "Callar".
- Si Android no permite notificaciones, degrada sin crash y el usuario puede volver con tile, botón de accesibilidad o launcher.

## Frases de handoff

WhatsApp general:

> Abrí WhatsApp. Mientras estés ahí no te escucho. Volvé con el botón Ojo Claro. Para chat directo, decime antes: abrí el chat de Sofi.

WhatsApp chat directo:

> Voy a abrir el chat de WhatsApp. No envío nada. Para seguir, volvé con el botón Ojo Claro.

WhatsApp compose:

> Voy a abrir WhatsApp con el mensaje preparado. No lo envío. Para seguir, volvé con el botón Ojo Claro.

Maps:

> Voy a abrir mapas. Te puedo orientar, pero no detecto peligros de la calle.

Teléfono:

> Voy a abrir el marcador. No llamo automáticamente.

## Cómo volver a Ojo Claro

Opciones actuales:

- Tocar la acción "Escuchar" en la notificación, si el permiso de notificaciones está disponible.
- Usar el tile de Quick Settings de Ojo Claro.
- Usar el botón de accesibilidad de Ojo Claro.
- Volver manualmente a la app y tocar "Escuchar".

La acción "Escuchar" abre `MainActivity` con `ACTION_START_LISTENING`. La UI vuelve visible y recién ahí reanuda el loop de voz.

La acción "Callar" abre `MainActivity` con `ACTION_STOP_SPEAKING`, detiene TTS si está activo y no ejecuta acciones sensibles.

## Límites actuales

- Ojo Claro no escucha dentro de WhatsApp si la app quedó en segundo plano.
- La notificación depende de `POST_NOTIFICATIONS` en Android 13+. Si el permiso no está concedido, no se muestra y no crashea.
- El retorno por notificación no es un foreground service ni un asistente global.
- El usuario debe decir la intención completa antes del handoff, por ejemplo: "buscá el chat de Marco Antonio".

## Futuro modo asistente global

Un modo global real debería evaluarse como una fase separada:

- Foreground service visible y justificado.
- Notificación permanente obligatoria.
- Controles claros de pausa, callar y privacidad.
- Auditoría de Play Protect y políticas de permisos.
- Explicación explícita al usuario antes de activar escucha fuera de la app.
- Tests físicos de lifecycle, batería, llamadas, WhatsApp, Maps y bloqueo de pantalla.

## Riesgos Play Protect

Los riesgos principales de un modo global futuro serían:

- Apariencia de grabación oculta.
- Uso excesivo de micrófono.
- Combinación riesgosa de AccessibilityService y automatización.
- Permisos no justificados para contactos, notificaciones o foreground service.
- Confusión del usuario sobre cuándo Ojo Claro está escuchando.

La implementación actual evita esos riesgos al no escuchar ni grabar en background.

## Tests cubiertos

- `AssistantOrchestratorTest`: WhatsApp, Maps y Teléfono producen handoff; chat directo mantiene no auto-envío; compose mantiene confirmación.
- `HomeViewModelExternalRoutingTest`: handoff pausa el loop de voz y no reinicia después de TTS/resume.
- `OjoClaroIntentsTest`: `ACTION_START_LISTENING` y `ACTION_STOP_SPEAKING` quedan diferenciados.

## QA físico recomendado

1. Instalar APK debug nueva.
2. Abrir Ojo Claro.
3. Decir "abrí WhatsApp".
4. Verificar que avisa que ahí no escucha y abre WhatsApp.
5. Volver con tile, botón de accesibilidad, notificación o launcher.
6. Decir "buscá el chat de Marco Antonio" desde Ojo Claro.
7. Verificar que pide confirmación.
8. Decir "sí" y verificar que no confirma.
9. Decir "confirmar".
10. Verificar que abre WhatsApp directo al chat, sin texto precargado y sin enviar nada.
11. Revisar logcat sin `FATAL EXCEPTION`.
