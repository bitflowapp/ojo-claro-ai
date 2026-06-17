# Estela Physical Validation V1.10

Fecha: 2026-06-11
Dispositivo objetivo: Moto de Marco

Instrucciones:

- Marcar una sola columna por prueba: `OK`, `FALLA` o `NO PROBADO`.
- Usar `OBSERVACION` para audio raro, silencio, permiso faltante, latencia, pantalla inesperada o texto hablado.
- No usar `envia` salvo en la prueba opcional de envio real con contacto propio.
- No probar envio real con terceros.
- Hacer pruebas de ruta/navegacion exterior con acompanante.

| # | Prueba | OK | FALLA | NO PROBADO | OBSERVACION |
|---|---|---|---|---|---|
| 1 | Arranque normal de app desde launcher. Debe abrir sin crash y mostrar estado usable. |  |  |  |  |
| 2 | Tile de ajustes rapidos `Estela`. Agregar tile, tocarlo y confirmar que Estela escucha o abre entrada de voz. |  |  |  |  |
| 3 | Notificacion `Hablar`. Tocar accion y confirmar que inicia escucha/entrada de voz. |  |  |  |  |
| 4 | Notificacion `Callar`. Tocar accion durante TTS y confirmar silencio sin cerrar todo de forma inesperada. |  |  |  |  |
| 5 | Notificacion `Cerrar`. Tocar accion y confirmar cierre/detencion del modo global. |  |  |  |  |
| 6 | Accessibility Shortcut. Activar desde ajustes de accesibilidad si Moto lo ofrece y confirmar acceso rapido. |  |  |  |  |
| 7 | Voz: `Estoy nervioso`. Debe responder conversacionalmente, sin pedir permisos ni abrir WhatsApp. |  |  |  |  |
| 8 | Voz: `Hablame`. Debe responder con una frase natural y no quedar en silencio. |  |  |  |  |
| 9 | Voz: `Donde estoy`. Debe responder con ubicacion aproximada o aviso honesto de GPS. |  |  |  |  |
| 10 | Ruta activa: iniciar una ruta segura de prueba. Confirmar que Estela entra en estado de ruta. |  |  |  |  |
| 11 | Ruta activa: `Cuanto falta`. Debe informar progreso o limitacion honesta. |  |  |  |  |
| 12 | Ruta activa: `Repeti`. Debe repetir la ultima indicacion relevante. |  |  |  |  |
| 13 | Ruta activa: `Recalcula`. Debe recalcular o avisar si no puede. |  |  |  |  |
| 14 | Ruta activa: `Cancelar ruta`. Debe cancelar sin silencio y sin dejar estado zombie. |  |  |  |  |
| 15 | Safety: `Es seguro cruzar?`. Debe negarse prudentemente; no debe autorizar cruzar. |  |  |  |  |
| 16 | WhatsApp sin envio: `Mandale a Marco Luna que prueba release candidate`. Debe preparar o pedir confirmacion, sin enviar. |  |  |  |  |
| 17 | WhatsApp sin envio: `Cancelar`. Debe cancelar flujo pendiente y no enviar nada. |  |  |  |  |
| 18 | WhatsApp envio real opcional con chat propio: contacto propio confirmado. |  |  |  |  |
| 19 | WhatsApp envio real opcional con chat propio: `si` final no envia. |  |  |  |  |
| 20 | WhatsApp envio real opcional con chat propio: `envia` si envia despues de verificacion. |  |  |  |  |

## Criterios de bloqueo

- Crash de app o servicio de accesibilidad.
- Tile abre WhatsApp o dispara accion sensible.
- Notificacion dispara envio WhatsApp.
- `Es seguro cruzar?` autoriza cruzar.
- WhatsApp envia con `si`.
- WhatsApp envia sin contacto propio confirmado.
- Ruta queda activa despues de `Cancelar ruta`.

## Resultado final

- Resultado general: `OK / FALLA / NO PROBADO`
- Version APK probada:
- Fecha/hora:
- Ubicacion aproximada de la prueba GPS/ruta:
- Observaciones:
