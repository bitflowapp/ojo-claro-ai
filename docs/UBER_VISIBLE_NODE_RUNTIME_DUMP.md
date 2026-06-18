# Uber — Volcado runtime de nodos que ve Estela vs uiautomator · 2026-06-13

> Herramienta: broadcast `com.ojoclaro.DEBUG_DUMP_VISIBLE_NODES` →
> `VisibleNodeSanitizer` → log `EstelaVisibleNodeDump` (sanitizado) →
> `scripts/dump_visible_nodes.ps1` → `build/uber-captures/` (gitignored).
> **Sin taps, sin pedir viaje. Sin datos sensibles en el log** (solo flags,
> longitudes, marcadores y tokens de categoría).

## Captura
- **Stage**: `uber_runtime_current`. **Foco**: `com.ubercab/...RootActivity`.
- Dump: ✅ PASS — `build/uber-captures/visible-nodes-uber_runtime_current.txt`
  (crudo NO commiteado).

## Comparación dura: Estela vs uiautomator (MISMA pantalla)
| | Estela (`readVisibleNodeSummaries`) | uiautomator dump |
|---|---|---|
| **nodos** | **16** | **238** |
| clickeables | 6 | 28 |
| clases | Button×6, View×5, TextView×4, **RatingBar×1** | View×117, TextView×30, FrameLayout×28, LinearLayout×23, Button×13, ViewGroup×11, **UberComposeView×6**, ScrollView×1 |
| UBER_PRICE | 1 (lo ve) | sí |
| UBER_RIDE_TYPE | **0 (NO lo ve)** | sí (Comfort/Taxi) |
| UBER_REQUEST_RIDE | **0 (NO lo ve)** | sí ("Solicita un viaje") |
| UBER_DESTINATION_HINT | 1 | sí |
| screenType del reasoner | `UNKNOWN` (honesto) | (sobre estructura uiautomator → `CONFIRM_RIDE`) |

## Qué ve Estela (16 nodos, sanitizado)
- 1× **RatingBar** (dLen=91) → es una pantalla de **CALIFICACIÓN/PROPINA** post-viaje.
- 5× **View** con content-desc (dLen 44–45) → estrellas / opciones de propina.
- 1× TextView **UBER_PRICE** (`ARS_AMOUNT_REDACTED`) → el monto del viaje (propina).
- 1× TextView **UBER_DESTINATION_HINT** (`DESTINATION_LABEL`).
- 6× Button (montos de propina / "Ingresar otra cantidad" / "Listo"; uno disabled).
- Headers TextView. **NINGÚN** nodo de tipo de viaje ni botón "Solicita un viaje".

## Diagnóstico
1. **La pantalla es de RATING/PROPINA** (RatingBar), no un confirm de viaje. El
   "carrusel Comfort/Taxi" que ve uiautomator es una sugerencia de re-pedido detrás.
2. **Estela captura un SUBCONJUNTO (16 de 238 nodos)**: lee la **ventana/overlay
   de calificación arriba**, pero NO el carrusel de opciones, que vive en
   **`UberComposeView` (Jetpack Compose)** dentro de otra ventana/scroll.
   `uiautomator` **fusiona todas las ventanas** (238 nodos) y por eso sí lo ve.

### ¿Reasoner o captura? → **CAPTURA DE NODOS**
El reasoner clasifica BIEN lo que recibe (16 nodos: precio + rating → `UNKNOWN`,
honesto; no inventa). El problema está en **`selectReadableWindowRoot` /
`readActiveWindowNodeSummaries`**: selecciona una sola ventana (el overlay de
rating) y/o no desciende al árbol de semántica de Compose, perdiendo el carrusel.

### Hipótesis (ordenadas por evidencia)
- ✅ **Multi-window / root incorrecta**: el overlay de rating es la ventana activa
  superior; el carrusel está en otra ventana/capa que `selectReadableWindowRoot`
  no elige. uiautomator merge-ea todas → 238 vs 16. **Principal.**
- ✅ **Compose no traversado**: el carrusel es `UberComposeView`; la semántica de
  Compose puede venir mergeada/virtual y el traversal de Estela no la captura.
- ⚠️ **visibleToUser / scroll**: parte del carrusel puede estar fuera de pantalla
  (scroll) y Estela filtra; uiautomator dumpea todo. (`AccessibilityNodeSummary`
  ni siquiera trae `visibleToUser`/`bounds`, así que Estela no puede razonar por
  visibilidad/posición.)
- ⚠️ **Pantalla equivocada**: además, es una pantalla de rating, no de opciones.

## Próximo fix recomendado (en orden)
1. **Captura multi-ventana**: que `readActiveWindowNodeSummaries` recorra TODAS las
   ventanas de aplicación (no solo `selectReadableWindowRoot`), o elija la ventana
   con más nodos de app (no el overlay), para alinear con uiautomator. Es el cambio
   de mayor impacto y donde está el bug real.
2. **Traversal de Compose**: validar que el recorrido desciende a la semántica de
   `UberComposeView` (nodos virtuales de Compose).
3. **Pantalla correcta**: capturar un confirm de viaje REAL (iniciar pedido nuevo:
   destino → opciones → confirmar), no una de rating/propina.
4. Recién con (1)+(3): tunear origen/destino y `CONFIRM_RIDE` en vivo de verdad.

## Contadores (read-only)
viajes pedidos **0** · confirm taps **0** · pagos **0** · llamadas/mensajes **0** ·
crashes **0** (pid 13185 estable). Datos sensibles filtrados al log: **0** (solo
flags/longitudes/marcadores/tokens).

---

## FIX multi-ventana + límites más altos — resultado (2026-06-13 ~20:29)
Build con `MULTI_WINDOW_ACCESSIBILITY_SNAPSHOT=true` + caps más altos
(nodos 32→80, visited 160→600, chars 2000→6000). Dump sobre la pantalla de
opciones REAL de Uber (`com.ubercab`, CONFIRM_RIDE).

| | Antes (rating, build viejo) | Después (opciones, build nuevo) |
|---|---|---|
| nodos que ve Estela | 16 | **46** |
| clickeables | 6 | **18** |
| `UBER_RIDE_TYPE` | 0 | **5** (Comfort + Taxi) |
| `UBER_REQUEST_RIDE` | 0 | **3** ("Solicita un viaje") |
| `UBER_DESTINATION_HINT` | 1 | 4 |
| `UBER_PRICE` | 1 | **0** (no captado) |
| screenType del reasoner | UNKNOWN | **CONFIRM_RIDE** |
| `hasRideType` / `hasReqRisky` | false / false | **true / true** |

**Diagnóstico afinado (honesto):** `multiWindowSnapshot windows=5 appWindows=1
chosenRoots=1 perRoot=47`. O sea, había **UNA sola ventana com.ubercab** y se
leyeron 47 nodos de ella. **El carrusel estaba en la MISMA ventana todo el tiempo;
lo truncaban los LÍMITES viejos** (visited=160 / chars=2000) antes de llegar a él.
La recuperación vino de los **límites más altos**, no de leer otra ventana. La
selección multi-ventana es una mejora COMPLEMENTARIA (maneja el caso multi-ventana
real y excluye SystemUI; cubierta por `ReadableWindowPlannerTest`), pero en ESTA
pantalla no fue el factor decisivo. (Corrige mi hipótesis previa "carrusel en otra
ventana": era el presupuesto de traversal.)

**Recuperado**: Comfort/Taxi (`tok=Uber Comfort`/`Taxi`), botón "Solicita un viaje"
(`tok=REQUEST_RIDE_LABEL`, clickeable), CONFIRM_RIDE, hints de destino.
**Sigue faltando**: el **PRECIO** (`price=0`, `hasPrice=false`) — el nodo de precio
no aparece en el set capturado o no matchea el regex (posible texto dibujado por
Compose / formato sin símbolo). Próximo: investigar el nodo de precio en esta
pantalla (regex más amplio o por qué Accessibility no lo expone).

## Smoke del copiloto sobre la pantalla recuperada (~20:31)
7 comandos: TODOS `app=UBER screen=CONFIRM_RIDE`, **`hasType=true`**, **`risky=1`**
(REQUEST_RIDE), `hasPrice=false`; `confirmRequest=blocked_v1 tapped=false`.
**0 viajes/confirm-taps/pagos/llamadas/mensajes/crashes** (pid 16895). 0 datos
sensibles al log. La mejora de captura **fluye end-to-end al copiloto**: ahora
clasifica CONFIRM_RIDE y lee el tipo de viaje en vivo (antes UNKNOWN).

---

## PRECIO en opciones/confirmación — investigación (2026-06-13 ~20:36–20:40)
Probe focalizado del precio (`dump_visible_nodes.ps1 -Stage uber_price_probe` +
`capture_uber_screen.ps1`). Resultado DURO:

- **El precio NO está en la accesibilidad de la pantalla de opciones** — ni en
  Estela (`price=0`) **ni en uiautomator**: el dump crudo de esa pantalla NO tiene
  ningún token con `$`/`ARS`; el único número es "Salta `<NUM>`" (no es precio). Las
  content-desc de las opciones ("Llega a tu destino con Uber Comfort, Solicita un
  viaje", "Envía un artículo") **no traen precio**.
- **Conclusión**: Uber dibuja la tarifa de cada opción con Compose **sin nodo de
  texto accesible**. **No es un problema de regex** (no hay texto que matchear).
- **Contraste**: en la pantalla de **propina** el precio SÍ es texto accesible
  ("Tu viaje fue de ARS X") → Estela lo lee (`hasPrice=true` en el smoke ~20:40).
  Y el `PRICE_REGEX` ya soporta los formatos AR (`ARS 2.443,00`, `$2,443.00`,
  rangos), validado por unit tests.

### Decisión (no vender humo)
- **No inventar** el precio. El reasoner/regex se mantiene (extrae cuando Uber lo
  expone como texto; rechaza min/km/rating).
- **Respuesta honesta** cuando es pantalla de viaje pero no hay precio legible:
  "No pude leer el precio: Uber no lo muestra de forma accesible en esta pantalla.
  Revisalo a mano antes de pedir." (+ aviso en "qué viaje estoy por pedir").
- **Futuro**: para leer la tarifa de opciones haría falta **OCR local de esa
  región de pantalla**, no Accessibility. Queda fuera de alcance de v1.

### Smoke (~20:40, pantalla de propina, precio accesible)
7 cmds: `app=UBER`, `hasPrice=true`, `confirmRequest=blocked_v1 tapped=false`;
**0 viajes/confirm-taps/pagos/llamadas/crashes** (pid 20442); 0 datos sensibles.

**Veredicto del precio: `UBER_PRICE_NOT_ACCESSIBLE`** en la pantalla de opciones
(Compose sin texto); extraíble cuando Uber lo da como texto (propina). Copiloto
responde honesto y nunca lo inventa.
