# Uber — Notas de extracción de pantalla · 2026-06-13

> Rama `mobility/uber-copilot-v1`. Tuning de `UberScreenReasoner` con un dump REAL
> de `com.ubercab` (pasajero) en el Moto. **Sin pedir viaje. Sin tocar nada.**

## Pantalla capturada
- **ride_options** (Moto `ZY32LHS6PS`, foco `com.ubercab/...RootActivity`). Dump
  crudo en `build/uber-captures/window-ride_options.xml` (**gitignored, NO commiteado**).
- Estructura real relevante (uiautomator, sanitizada):
  - Botón final: `class=android.widget.Button`, `content-desc="Llega a tu destino con Uber Comfort, Solicita un viaje"` y otro `text="Solicita un viaje"`.
  - Tipo: copy `text="Llega a tu destino con Uber Comfort"`, `text="Taxis a pedido"`.
  - Precio: `text="Tu viaje fue de ARS 2,443.00"` con `resource-id=...ub__tip_step_description` (frase del paso de propina; referencia a un viaje, no un precio aislado).
  - Origen/destino: direcciones en nodos **sin etiqueta** "Origen/Destino".

## Hallazgos y ajustes mínimos aplicados
1. **Bug de clasificación (TRIP_ACTIVE)**: el marcador laxo `"tu viaje"` hacía que
   `"Tu viaje fue de ARS 2.443"` clasificara como viaje en curso. **Fix**: se
   quitaron `"tu viaje"`/`"your trip"`; se dejaron solo marcadores fuertes
   (`tu conductor`, `conductor asignado`, `en camino`, `llegando`, `viaje en curso`,
   `on the way`, `arriving`).
2. **Botón final no detectado (gap de seguridad)**: el botón real dice
   **"Solicita un viaje"** (`solicita`, sin `r`); el matcher buscaba `"solicitar"`.
   **Fix**: se agregaron `"solicita"`/`"solicita un viaje"` (+ `confirm ride`,
   `confirm pickup`) → ahora marca `REQUEST_RIDE` y la pantalla pasa a `CONFIRM_RIDE`.
3. **Precio**: antes devolvía la frase entera. **Fix**: regex que extrae solo el
   monto (`ARS 2.443`, `ARS 2,443.00`, `$ 2.443`, `$2,443.00`, y rangos `$X - $Y`).
4. **Tipo**: antes devolvía el copy completo. **Fix**: mapa marcador→canónico que
   devuelve `Uber Comfort`/`Taxi`/`UberX`/`Uber Moto`/…; se prioriza el tipo del
   nodo clickeable (la opción/CTA elegida).
5. **Origen/destino**: **se mantienen `null`** — no hay etiqueta confiable en el
   árbol y NO se adivina por posición. Respuesta honesta: "No pude leer el
   origen/destino con seguridad."

## Qué se extrae hoy (sobre la estructura real, vía unit test)
| Dato | Estado |
|---|---|
| screenType | ✅ `CONFIRM_RIDE` (ya NO `TRIP_ACTIVE`) |
| tipo de viaje | ✅ `Uber Comfort` (canónico) |
| precio | ✅ monto aislado (`ARS 1,234.00` en fixture) |
| botón final | ✅ `REQUEST_RIDE` riesgoso |
| origen | ⚠️ `null` (sin etiqueta confiable) |
| destino | ⚠️ `null` (sin etiqueta confiable) |
| método de pago | ⚠️ solo si aparece (`Visa ••••`, "efectivo", etc.); no visible en este dump |

## Fixtures
- `androidApp/src/test/resources/uber/ride_options_sanitized.xml` — derivado del
  dump real, **sanitizado** (dirección → "Direccion de prueba 123", precio →
  `ARS 1,234.00`). El dump crudo NO se commitea.
- Test: `UberRideOptionsFixtureTest` (8 casos) parsea el fixture → `ReasonerNode`s
  → valida los 5 ajustes.

## Notas de privacidad / seguridad
- No se loguean direcciones/precios reales (el log de runtime usa solo flags
  booleanos `hasPrice/hasType/hasPickup/hasDest`, nunca el valor).
- `readyToRequest` sigue cerrada (no hay origen/destino) → ni con la frase fuerte
  se pide; `confirmo pedir Uber ahora` = `blocked_v1 tapped=false`.

## Segunda captura (confirm_ride) y 2 hallazgos duros (2026-06-13)
Se recapturó con la pantalla "de opciones/confirmación con dirección visible". El
dump resultó **estructuralmente idéntico** al de ride_options y, de hecho, es un
**paso de PROPINA post-viaje** (`resource-id=...ub__tip_step_description`,
`text="Tu viaje fue de ARS 2,443.00"`, `text="Ingresar otra cantidad"`) con un
carrusel de "volver a pedir" (Comfort/Taxi) abajo — **no** una confirmación de
viaje nueva.

1. **Bug LOGIN (loose marker)**: el marcador `"ingresar"` matcheaba
   **"Ingresar otra cantidad"** (botón de propina) → la pantalla clasificaba
   `LOGIN`. **Fix**: se quitó `"ingresar"`/`"verify"` de `LOGIN_MARKERS` (solo
   frases específicas: `iniciar sesion`, `ingresa tu numero/correo/contrasena`,
   `codigo de verificacion`, `log in`, etc.). Verificado en vivo: `LOGIN → UNKNOWN`.
   (Mismo patrón que `"tu viaje" → TRIP_ACTIVE`.)
2. **uiautomator ≠ lectura de accesibilidad de Estela**: en vivo, Estela
   (`readVisibleNodeSummaries`) ve el paso de propina (precio + "Ingresar otra
   cantidad") pero **NO** el carrusel de re-pedido (Comfort/Solicita/Taxi) que
   uiautomator sí captura → live `hasType=false`, `risky=0`. Es decir: los fixtures
   derivados de uiautomator **no predicen** lo que Estela lee en runtime.

### Implicancia
- Los unit tests validan la LÓGICA del reasoner contra la estructura de uiautomator
  (correcta), pero **no** contra lo que Estela realmente lee. La extracción en vivo
  de `CONFIRM_RIDE`/tipo/botón-final en esta pantalla NO se logra porque (a) Estela
  ve un subconjunto de nodos y (b) la pantalla es de propina, no un confirm real.
- Origen/destino siguen `null` (sin etiqueta; no se adivina por posición).

## Origen/destino: por qué siguen null
En el dump real **TODOS los nodos traen `resource-id` vacío** y las direcciones son
nodos de texto **sin** etiqueta "Origen/Destino" (solo posición/bounds). Regla:
no adivinar por posición → `null` honesto.

## RESUELTO (1): volcado runtime → el problema es la CAPTURA, no el reasoner
Se agregó el broadcast `DEBUG_DUMP_VISIBLE_NODES` + `VisibleNodeSanitizer` +
`scripts/dump_visible_nodes.ps1` (ver `UBER_VISIBLE_NODE_RUNTIME_DUMP.md`). En la
misma pantalla: **Estela ve 16 nodos; uiautomator 238**. Estela NO ve el carrusel
Comfort/Taxi ni "Solicita un viaje" (`rideType=0`, `requestRide=0`) — esos viven en
`UberComposeView` (Compose) en otra ventana/scroll; `selectReadableWindowRoot`
selecciona solo el overlay de rating. **El reasoner clasifica bien lo que recibe;
el bug está en `readActiveWindowNodeSummaries`/`selectReadableWindowRoot` (captura
de una sola ventana, no multi-window).**

## Pendiente (para habilitar extracción real en vivo)
1. **Captura multi-ventana** en `readActiveWindowNodeSummaries` (recorrer todas las
   ventanas de app / la más rica, no solo el overlay) + traversal de Compose.
2. **Pantalla de confirmación de viaje REAL** (no rating/propina).
3. Recién con (1)+(2): tunear origen/destino y `CONFIRM_RIDE` en vivo.

## RESUELTO (2): captura multi-ventana + límites más altos
Build con `MULTI_WINDOW_ACCESSIBILITY_SNAPSHOT=true` (flag, default true) +
`ReadableWindowPlanner` (puro) + caps de traversal más altos para el snapshot
(nodos 32→80, visited 160→600, chars 2000→6000). En la pantalla de opciones REAL:
**Estela pasó de 16 a 46 nodos**, recuperó el carrusel (`rideType` 0→5), el botón
"Solicita un viaje" (`requestRide` 0→3) y clasifica **CONFIRM_RIDE** con
`hasRideType=true`/`hasReqRisky=true`. Afinación honesta: había 1 sola ventana
com.ubercab → la recuperación vino de los **límites más altos** (el carrusel estaba
truncado), no de leer otra ventana; la selección multi-ventana queda como mejora
complementaria (testeada). **Sigue faltando el PRECIO** (`price=0`): el nodo de
precio no está en el set capturado o no matchea el regex. Detalle:
`UBER_VISIBLE_NODE_RUNTIME_DUMP.md`.

## PRECIO: no accesible en opciones (Compose), sí en propina
Investigación (2026-06-13): el precio de cada opción de viaje **no está en el árbol
de Accessibility** (ni Estela ni uiautomator lo ven; Uber lo dibuja con Compose sin
texto). NO es problema de regex. En la pantalla de **propina** sí es texto y se lee.
Decisión: **no inventar**; respuesta honesta "No pude leer el precio: revisalo a
mano antes de pedir". `PRICE_REGEX` ya cubre formatos AR (validado) y rechaza
min/km/rating. Leer la tarifa de opciones requeriría **OCR regional** (futuro, fuera
de v1). Veredicto precio: `UBER_PRICE_NOT_ACCESSIBLE`.
