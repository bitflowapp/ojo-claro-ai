# Estela Activation Shortcuts

Objetivo: dejar caminos rapidos y seguros para activar Estela sin abrir manualmente la app desde el launcher.

## Caminos disponibles ahora

- Quick Settings tile: tile llamado `Estela` en el panel de ajustes rapidos.
- Boton/overlay de accesibilidad: boton flotante propio de Estela cuando el servicio de accesibilidad esta activo.
- Accessibility button del sistema: Estela solicita `flagRequestAccessibilityButton`; si el telefono lo ofrece, puede aparecer en la barra o menu de accesibilidad.
- Notificacion foreground: cuando el modo global esta activo, la notificacion ofrece `Hablar`, `Callar` y `Cerrar`.
- App visible: fallback seguro cuando faltan permisos o el sistema no deja iniciar el modo global desde el tile.

## Quick Settings tile

Como agregarlo:

1. Bajar el panel de ajustes rapidos.
2. Elegir editar tiles.
3. Buscar `Estela`.
4. Arrastrarlo a los tiles visibles.
5. Tocar `Estela`.

Comportamiento esperado:

- Si microfono y accesibilidad estan listos, Estela activa el modo global y empieza a escuchar.
- Si falta microfono o accesibilidad, Estela muestra el aviso: `Necesito permiso de microfono y accesibilidad activa.` y abre la app para completar permisos.
- El tile no envia WhatsApp, no abre WhatsApp y no depende del backend para arrancar.

## Boton y shortcut de accesibilidad

Como activarlo:

1. Abrir Ajustes de Android.
2. Entrar en Accesibilidad.
3. Buscar `Estela`.
4. Activar el servicio.
5. En la configuracion del telefono, habilitar acceso directo o boton de accesibilidad para Estela si Android/Moto lo ofrece.

Comportamiento esperado:

- El servicio muestra un boton/overlay propio de Estela.
- El panel expandido ofrece `Leer pantalla`, `Hablar`, `Callar` y `Cerrar`.
- `Hablar` activa el modo global desde el contexto actual.
- `Cerrar` solo contrae el panel; no detiene TTS ni cancela tareas.

## Notificacion

Cuando Estela esta activa en modo global, la notificacion tiene acciones seguras:

- `Hablar`: vuelve a escuchar.
- `Callar`: detiene habla/escucha actual sin enviar nada.
- `Cerrar`: cierra el modo global.

No hay acciones sensibles en la notificacion.

## Boton de encendido

No se implementa captura de doble toque del boton de encendido.

Motivo:

- En Android moderno, el boton de encendido es una tecla de sistema y los fabricantes lo reservan para pantalla, camara, wallet, emergencia o asistente del sistema.
- Una app normal no debe interceptarlo con hacks.
- Algunos Moto/Samsung permiten configurar doble toque o tecla lateral desde ajustes del sistema, pero esa asignacion depende del fabricante y no se garantiza para apps comunes.

Recomendacion:

- Usar Quick Settings tile, boton de accesibilidad y notificacion.
- Si Moto permite configurar gesto de boton lateral hacia una app/asistente, probarlo manualmente sin codigo especifico ni permisos peligrosos.

## Teclas de volumen

No se implementa shortcut por doble toque de volumen en esta version.

Motivo:

- Android permite que servicios de accesibilidad soliciten eventos de teclas con `flagRequestFilterKeyEvents`, pero eso implica observar teclas globales antes de que lleguen a otras apps.
- Aunque se devuelvan los eventos al sistema, puede sorprender al usuario, interferir con control de volumen o convivir mal con TalkBack/otros servicios.
- Para V1.10 se prioriza no romper volumen normal ni accesibilidad de terceros.

Decision actual:

- No se agrega `flagRequestFilterKeyEvents`.
- No se captura power button.
- No se captura volumen.

## Default assistant app

Estela todavia no se registra como asistente predeterminada del sistema.

Que faltaria:

- Implementar un `VoiceInteractionService` y una `VoiceInteractionSession`.
- Declarar metadata de assistant/voice interaction.
- Pasar el flujo de seleccion de app asistente predeterminada del sistema.
- Auditar privacidad, pantalla contextual, permisos y lifecycle.

Riesgos:

- Es una superficie grande, no trivial.
- Puede competir con Google Assistant/Gemini y configuraciones del fabricante.
- Puede cambiar como se activa por boton lateral/power segun Moto/Samsung.

Recomendacion:

- Dejar default assistant para una mision futura especifica.
- Primero cerrar experiencia con tile, accesibilidad y notificacion.

## Recomendaciones por marca

Moto:

- Agregar tile `Estela` a primera pagina de ajustes rapidos.
- Activar shortcut/boton de accesibilidad para Estela si aparece.
- Revisar gestos Moto solo desde ajustes oficiales; no depender de capturar power.

Samsung:

- Agregar tile `Estela`.
- Revisar `Accesibilidad > Ajustes avanzados > Boton de accesibilidad` o acceso directo equivalente.
- La tecla lateral suele estar controlada por ajustes Samsung; tratarla como integracion manual, no como contrato de app.

## Smoke rapido

1. Tocar tile `Estela`.
2. Confirmar que escucha o abre la app si faltan permisos.
3. Desde notificacion, probar `Hablar`, `Callar`, `Cerrar`.
4. Desde overlay de accesibilidad, probar `Hablar` y `Cerrar`.
5. Probar comandos no sensibles: `Estoy nervioso`, `Donde estoy`, `Es seguro cruzar`, `Mandale a Marco Luna que prueba estela activacion`, `Cancelar`.
6. No decir `envia` durante este smoke.
