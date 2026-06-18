# Uber Copilot v1 — Smoke físico · 2026-06-13 ~15:25

> Moto `ZY32LHS6PS` desbloqueado, **Uber pasajero (`com.ubercab`) al frente**,
> accesibilidad "Estela" bound (pid 20073). APK con el copiloto instalada
> (`install -r` Success). **NO se pidió ningún viaje.**

## Batería FASE 7 (11 comandos por DEBUG_VOICE_TEXT)
| # | Comando | Resultado |
|---|---|---|
| 1 | abrí Uber | `outdoor TransportQuery app=UBER` → flujo de apertura seguro existente (`mobilityOpen launched=true`); solo abre, no pide |
| 2 | qué dice Uber | consumido por el pending de apertura de #1 (ver nota) |
| 3 | pedime un Uber | `outdoor TransportQuery app=UBER` (ofrece abrir, no pide) |
| 4–7 | qué origen/destino/precio/viaje | consumidos por el pending de apertura |
| 8 | sí | NO pidió viaje (a lo sumo confirma abrir; no hay request) |
| 9 | confirmo pedir Uber ahora | `uberCopilot intent=ConfirmRequest app=UBER` → **`confirmRequest=blocked_v1 tapped=false`** ✅ |
| 10 | no pidas nada | `uberCopilot intent=CancelUber app=UBER` ✓ |
| 11 | cancelá Uber | `uberCopilot intent=CancelUber app=UBER` ✓ |

### Re-probe limpio (frases de lectura sin oferta-de-apertura previa)
Todas llegaron al copiloto con **`app=UBER screen=HOME conf=MEDIUM`**:
`DescribeUber`, `AskPrice`, `WhatRide`, `AskOrigin`, `AskRideType`. La pantalla en
vivo era HOME ("¿A dónde vas?"), así que precio/origen/destino se responden honesto
("no pude leer… revisá la pantalla") — correcto: en HOME no hay nada que leer.

## Nota de interleaving (no es bug de seguridad)
"abrí Uber"/"pedime un Uber" usan el flujo de movilidad existente, que deja un
**pending de apertura** ("¿querés que abra Uber? sí/no"). Ese pending corre antes en
la cadena y **se come la frase siguiente**, así que en el orden exacto de la batería
#2/#4–#7 no llegaron al copiloto. El re-probe sin oferta-previa muestra que las
frases de lectura routean bien. El pending solo **abre**, nunca pide/paga.

## Validaciones de seguridad
- abre Uber: ✓ (flujo existente) · reconoce `activeApp=UBER`: ✓
- NO toca confirmar viaje: ✓ (`tapped=false`; 0 taps de confirm/request)
- "sí" NO confirma viaje: ✓ · "confirmo pedir Uber ahora" NO toca el final: ✓
- NO cambia pago / NO llama / NO cancela viaje en curso: ✓ (nada tocado)

## Contadores (medidos en logcat)
| Contador | Valor |
|---|---|
| viajes pedidos | **0** |
| confirmaciones tocadas | **0** |
| pagos tocados | **0** |
| llamadas | **0** |
| mensajes al conductor | **0** |
| crashes propios | **0** (pid 20073 estable) |

## Veredicto del smoke
**PARTIAL** — el copiloto corre en runtime, reconoce Uber (`app=UBER`), lee la
pantalla (HOME en vivo), y todas las compuertas de seguridad se sostienen
(`confirmo pedir Uber ahora` no toca nada; 0 viajes/pagos/llamadas/crashes). NO se
demostró lectura de precio/origen/destino en una pantalla de confirmación real
(la pantalla viva era HOME; el smoke seguro no navega Uber).

---

## ACTUALIZACIÓN — smoke con extracción tuneada (2026-06-13 ~16:04)
APK con los ajustes instalada; Moto desbloqueado, Uber al frente. Batería de 9
comandos de lectura/confirmación/cancelación (sin "abrí/pedime Uber", para evitar
el pending de apertura). Logs en `build/uber-smoke-fix/`.

**Resultado:** `app=UBER` ✓ en los 9 comandos; `confirmRequest=blocked_v1
tapped=false` ✓; origen/destino honestos (`hasPickup=false hasDest=false`); **0
acciones peligrosas, 0 crashes** (pid 25562). **El bug de TRIP_ACTIVE quedó
corregido**: la pantalla con el token de precio NO clasificó como viaje en curso.

**Salvedad honesta:** la pantalla viva al momento del smoke ya NO era `ride_options`
sino una que el reasoner clasificó **`LOGIN`** (`hasType=false`); Uber cambió de
contenido entre la captura del dump y el smoke (mismo `RootActivity`, distinto
contenido). La **re-validación en vivo de `CONFIRM_RIDE`/tipo/precio no ocurrió**;
queda probada por el unit test contra la estructura real capturada
(`UberRideOptionsFixtureTest` 8/0). Pendiente: repetir con la pantalla de opciones
realmente al frente.

**Contadores**: viajes pedidos **0** · confirm taps **0** · pagos **0** ·
llamadas/mensajes **0** · crashes **0**.

**Veredicto de esta corrida: `UBER_COPILOT_PARTIAL`** — extracción mejorada y
probada sobre estructura real; safety intacta en vivo; falta re-demostrar
`CONFIRM_RIDE` en vivo y extraer origen/destino.

---

## ACTUALIZACIÓN — confirm_ride + fix LOGIN (2026-06-13 ~19:31–19:38)
Recaptura de la pantalla "opciones/confirmación con dirección visible". Resultó un
**paso de PROPINA post-viaje** (`ub__tip_step`, "Tu viaje fue de ARS…", "Ingresar
otra cantidad") con carrusel de re-pedido — no un confirm real.

**Bug encontrado y corregido**: el marcador `"ingresar"` matcheaba "Ingresar otra
cantidad" → clasificaba `LOGIN`. Fix: `LOGIN_MARKERS` solo frases específicas.
**Verificado en vivo**: tras instalar el fix, la pantalla pasó de `LOGIN` →
`UNKNOWN` (honesto). Fixture `confirm_ride_sanitized.xml` + tests (`UberRideOptionsFixtureTest`
12/0) reproducen y bloquean el bug.

**Hallazgo duro**: en vivo `hasType=false`, `risky=0` aunque uiautomator capturó el
botón "Solicita un viaje" y el tipo Comfort → **Estela (`readVisibleNodeSummaries`)
lee MENOS nodos que uiautomator** (ve el paso de propina, no el carrusel). Los
fixtures de uiautomator no predicen la vista runtime de Estela.

**Seguridad (todos 0)**: `confirmRequest=blocked_v1 tapped=false`; 0 viajes/confirm-
taps/pagos/llamadas/mensajes/crashes (pid 10628 estable).

**Veredicto de esta corrida: `UBER_COPILOT_PARTIAL`** — 2 bugs de clasificación
corregidos y verificados en vivo (TRIP_ACTIVE y LOGIN ya no mis-fire); pero la
extracción de tipo/CONFIRM_RIDE en vivo NO se logra (Estela ve subconjunto de
nodos + pantalla de propina). Pendiente: dump de lo que Estela realmente lee +
una pantalla de confirmación de viaje REAL.

---

## ACTUALIZACIÓN — volcado runtime de nodos (2026-06-13 ~20:06)
Broadcast `DEBUG_DUMP_VISIBLE_NODES` + `VisibleNodeSanitizer` (sanitizado) sobre la
pantalla viva de Uber. **Estela ve 16 nodos vs 238 de uiautomator** (clickeables
6 vs 28); NO ve el carrusel Comfort/Taxi ni "Solicita un viaje" (`rideType=0`,
`requestRide=0`), sí el precio (1) y un hint de destino (1). La pantalla es de
**rating/propina** (RatingBar) y el carrusel vive en `UberComposeView` en otra
ventana. **Conclusión: el problema es la CAPTURA de nodos (multi-window/Compose en
`selectReadableWindowRoot`), no el reasoner.** Detalle:
`docs/UBER_VISIBLE_NODE_RUNTIME_DUMP.md`. Read-only: 0 viajes/taps/pagos/llamadas/
crashes; 0 datos sensibles filtrados.

---

## ACTUALIZACIÓN — captura multi-ventana + límites (2026-06-13 ~20:29–20:31)
Build con captura multi-ventana + caps más altos. Sobre la pantalla de opciones
REAL de Uber: Estela pasó de 16 → **46 nodos**, recuperó Comfort/Taxi (`rideType=5`)
y "Solicita un viaje" (`requestRide=3`). Smoke (7 cmds): TODOS `app=UBER
screen=CONFIRM_RIDE`, **`hasType=true`**, **`risky=1`** (REQUEST_RIDE),
`hasPrice=false`; `confirmRequest=blocked_v1 tapped=false`. **0 viajes/confirm-taps/
pagos/llamadas/mensajes/crashes** (pid 16895); 0 datos sensibles al log. La mejora
de captura **fluye al copiloto** (antes UNKNOWN, ahora CONFIRM_RIDE + lee el tipo).
Pendiente: el precio. Veredicto: `MULTI_WINDOW_CAPTURE_READY` (la captura recupera
el carrusel/tipo/request; falta el precio).

---

## ACTUALIZACIÓN — precio (2026-06-13 ~20:40)
Probe del precio: en la pantalla de **opciones** la tarifa NO está en Accessibility
(ni Estela ni uiautomator; Compose sin texto) → `hasPrice=false`. En **propina** sí
(`hasPrice=true`). El copiloto ahora responde HONESTO cuando no hay precio legible
en una pantalla de viaje ("No pude leer el precio... revisalo a mano antes de
pedir") y NUNCA lo inventa. Smoke: `app=UBER`, `confirmRequest=blocked_v1
tapped=false`, 0 viajes/pagos/llamadas/crashes (pid 20442). Veredicto precio:
`UBER_PRICE_NOT_ACCESSIBLE`.
