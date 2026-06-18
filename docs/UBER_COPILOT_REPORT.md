# Uber Copilot v1 — Reporte de implementación · 2026-06-13

> Rama: `mobility/uber-copilot-v1` (desde V2.2 `d3a9174`). Nivel: **Level 2 —
> copiloto guiado**. NO pide/confirma viajes, NO toca pagos. SIN merge/push/PR.

## Qué se implementó
- **`UberScreenReasoner`** (puro, `agent/intelligence/`): `RawScreen` →
  `UberScreenModel` con `activeApp (UBER/OTHER)`, `screenType (LOGIN/HOME/
  DESTINATION_SEARCH/ROUTE_REVIEW/RIDE_OPTIONS/CONFIRM_RIDE/DRIVER_SEARCH/
  TRIP_ACTIVE/UNKNOWN)`, `pickup/destination/price/rideType/payment` (best-effort),
  `riskyControls` y `confidence`. Targetea **`com.ubercab` (pasajero)**; trata
  `com.ubercab.driver` y todo lo demás como `OTHER`. Marcadores de **texto ES/EN**
  (no resource-ids, que Uber ofusca/rota).
- **`UberCopilotPhrases` / `UberCopilotNarrator` / `UberIntent`** (puros): parser
  preciso + texto hablado. Reclama solo lectura/freno; **no** reclama "abrí Uber"/
  "pedime un Uber" (los maneja el flujo de apertura seguro existente).
- **`handleUberCopilotCommand`** en `GlobalAssistantService`, insertado **ANTES**
  del fast-path de Outdoor (que reclama cualquier "uber" para ofrecer abrir), para
  interceptar las frases de lectura/confirmación/cancelación. Loguea la decisión
  **sin datos sensibles** (solo intent/app/screen/confidence/#risky).
- **Compuerta `readyToRequest`** (pura): exige origen+destino+precio+tipo+pago +
  pantalla de confirmación + confianza alta. En v1 **el router NUNCA toca el botón
  final aunque sea true**. Y como el reasoner aún no extrae origen/destino (devuelve
  null), la compuerta queda **estructuralmente cerrada** (freno extra).

## Qué NO se implementó (a propósito)
- Tap del botón final de pedir/confirmar viaje. `"confirmo pedir Uber ahora"` en v1
  responde que no está habilitado y deja a la persona que toque.
- Extracción robusta de origen/destino (UI de Uber muy custom; pendiente de tunear
  con capturas reales).
- Tocar pagos / agregar/cambiar tarjeta / llamar/mensajear conductor / compartir
  ubicación / cancelar viaje en curso → **bloqueado siempre** (solo se reportan
  como `riskyControls`, nunca se tocan).
- Soporte de la app de **conductor** (`com.ubercab.driver`) → excluida.

## Paquetes detectados (Moto `ZY32LHS6PS`)
- `com.ubercab` — **app de pasajero (OBJETIVO)**, instalada y logueada.
- `com.ubercab.driver` — app de conductor, instalada → **excluida**.

## Pantallas reconocidas en runtime
- `HOME` reconocida en vivo (Uber abierto en "¿A dónde vas?"), `app=UBER conf=MEDIUM`.
- `LOGIN/RIDE_OPTIONS/CONFIRM_RIDE/TRIP_ACTIVE/UNKNOWN` cubiertas por unit tests con
  pantallas sintéticas (no demostradas en vivo: el smoke seguro no navega Uber).

## Riesgos
- Marcadores de texto pueden requerir tuning contra la UI real (capturas via
  uiautomator salen pobres porque Uber usa vistas custom; Estela lee por
  accesibilidad, que ve más).
- Interleaving: "abrí Uber"/"pedime un Uber" (flujo existente) dejan un pending de
  apertura que se come la frase siguiente. Mitigación: las frases de lectura andan
  solas; documentado.

## Próximos pasos para habilitar pedido real controlado
1. Tunear extracción de origen/destino/precio con capturas reales de cada pantalla.
2. Validar `CONFIRM_RIDE` + precio en vivo (en una pantalla de confirmación real).
3. Definir el tap final detrás de `readyToRequest` + frase fuerte exacta + un
   segundo factor (repetir destino/precio en voz) — solo tras validación manual.
4. Resolver el interleaving con el pending de apertura de movilidad.

## Veredicto
**UBER_COPILOT_PARTIAL** — copiloto de lectura/guía cableado, seguro y testeado;
reconoce Uber en runtime (`app=UBER`); NUNCA pide/confirma/paga. Falta tunear
extracción de datos y validar pantallas de confirmación reales. Ver
`docs/UBER_COPILOT_SMOKE.md` y `docs/UBER_COPILOT_AUDIT.md`.

---

## ACTUALIZACIÓN — tuning de extracción con dump real (2026-06-13)
Con un dump REAL de la pantalla `ride_options` (ver `UBER_SCREEN_EXTRACTION_NOTES.md`)
se aplicaron ajustes mínimos a `UberScreenReasoner` y se validaron con un fixture
sanitizado (`ride_options_sanitized.xml`, test `UberRideOptionsFixtureTest` 8/0):
1. **TRIP_ACTIVE** endurecido: `"Tu viaje fue de ARS 2.443"` ya NO clasifica como
   viaje en curso (se quitó el marcador laxo `"tu viaje"`).
2. **Botón final**: detecta el real **"Solicita un viaje"** → `REQUEST_RIDE` riesgoso
   → pantalla `CONFIRM_RIDE`.
3. **Precio**: extrae el monto aislado (`ARS 1,234.00`), no la frase.
4. **Tipo**: canónico (`Uber Comfort`/`Taxi`), no el copy completo.
5. **Origen/destino**: quedan `null` (sin etiqueta confiable) → respuesta honesta.

Extracción sobre estructura real: **screenType ✅ · tipo ✅ · precio ✅ · botón
riesgoso ✅ · origen/destino ⚠️ null (a propósito) · pago ⚠️ solo si aparece.**
`readyToRequest` sigue cerrada; `confirmo pedir Uber ahora` = `blocked_v1 tapped=false`.
