# Estela V1.9 GPS y Conversacion

Este documento resume el cierre QA de V1.9 para GPS, conversacion y voz. No agrega features; fija una guia de prueba y limites.

## Que mejoro en GPS

- `Donde estoy` tiene una respuesta mas util y hablable.
- `Estoy perdido` entra por el camino local de ubicacion, no por conversacion libre.
- `Cuanto falta` tiene manejo propio cuando hay ruta activa o degradacion honesta si no hay ruta.
- `Recalcula` refresca la ruta cuando corresponde.
- `Cancelar ruta` responde sin dejar silencio.
- Si no llegan updates de ubicacion, Estela debe avisar o degradar, no quedarse muda.

## Que mejoro en conversacion

- `/api/v1/conversation` devuelve `reply` no vacio local y por ngrok.
- El backend filtra safety vial antes del modelo.
- La memoria corta conserva contexto reciente sin guardar secretos.
- El prompt responde con tono breve, humano y argentino moderado.
- Android falla cerrado si el backend no devuelve reply util y habla un fallback honesto.

## Probar `Estoy nervioso` + `Que probamos`

Backend:

1. Ejecutar `scripts/qa/check-conversation.ps1`.
2. Confirmar `Estoy nervioso` con status 200 y `replyPresent=True`.
3. Confirmar `Hablame` con status 200 y `replyPresent=True`.

ADB o voz real:

1. Decir `Estoy nervioso`.
2. Despues decir `Que probamos`.
3. Esperado: la primera puede responder por compania local; la segunda puede ir a conversacion LLM y usar contexto reciente.
4. No debe haber silencio.

## Probar `Donde estoy`

1. Estar en exterior o cerca de ventana.
2. Decir `Donde estoy`.
3. Esperado: respuesta hablada con ubicacion aproximada o limitacion honesta.
4. Logs esperados en debug: fast path outdoor `WhereAmI`, fix valido si hay GPS, reverse label si backend/resolver responde.

## Probar ruta activa

1. Con acompanante, iniciar ruta peatonal simple.
2. Decir `Cuanto falta`.
3. Decir `Recalcula`.
4. Decir `Repeti`.
5. Decir `Cancelar ruta`.
6. Esperado: Estela informa estado o limitacion, no queda en silencio, y cancela de forma audible.

## Limitaciones de seguridad vial

Estela no es autoridad para cruzar calles ni avanzar fisicamente. Ante frases como `Es seguro cruzar`, la respuesta debe negar confirmacion de seguridad y recomendar baston, escucha del transito y ayuda humana.

No validar V1.9 en cruces reales sin acompanante. No presentar vision, GPS ni LLM como reemplazo de baston, perro guia, acompanante o criterio humano.

## Cloud TTS

Cloud TTS esta documentado como diseno, pero no activado como feature de producto en esta version. La voz Android puede sonar generica y depende del motor TTS instalado en el dispositivo.

## Smoke rapido

- Backend/ngrok: `scripts/qa/check-backend.ps1`.
- Conversacion: `scripts/qa/check-conversation.ps1`.
- ADB sin envio real: `scripts/qa/adb-smoke-estela.ps1`.
