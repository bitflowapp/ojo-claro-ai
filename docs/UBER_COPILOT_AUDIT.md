# Uber Copilot — Auditoría (Level 2, copiloto guiado) · 2026-06-13

> Rama: `mobility/uber-copilot-v1` (desde V2.2 `d3a9174`). Objetivo: que Estela
> asista a una persona no vidente a **preparar** un viaje de Uber de forma guiada
> y segura. **NO pedir/confirmar viaje en este sprint.**

## Paquetes detectados en el Moto (`ZY32LHS6PS`)
| Paquete | Qué es | Rol en este sprint |
|---|---|---|
| `com.ubercab` | **App de pasajero (RIDER)** — instalada (con splits GooglePaySdk/VoipTwilio/BarcodeScanner) | **OBJETIVO**: abrir/leer/preparar |
| `com.ubercab.driver` | **App de CONDUCTOR** — instalada | **EXCLUIDA**: jamás automatizar (ir en línea / aceptar viajes de pasajeros es más peligroso y no aplica a un pasajero ciego) |

> ⚠️ Riesgo de ambigüedad: ambas apps están instaladas. Estela debe abrir y
> reconocer **solo `com.ubercab`**. Si el foreground es `com.ubercab.driver`, el
> copiloto NO debe actuar (lo trata como app desconocida/otra).

## Estado de captura en vivo
**Bloqueada**: al intentar abrir Uber el Moto estaba **LOCKED** (keyguard con
credencial; `mCurrentFocus=NotificationShade`, ids `com.android.systemui:id/keyguard_*`).
No se pudo capit­urar la pantalla real del rider en esta pasada. Pendiente: una
sesión desbloqueada para validar marcadores y correr el smoke.

> No se registran direcciones/ubicaciones reales en este doc ni en logs (regla de
> privacidad). El dump local (`build/uber_dump.xml`) NO se commitea.

## Cómo abre apps Estela hoy (reutilizable, NO reinventar)
- `AppCapabilityRegistry` ya define `UBER_PACKAGE = "com.ubercab"` como
  `RIDE_HAILING`, `canOpenSafely=true`, prohibido: "No solicita viajes, no
  confirma precio y no toca pagos."
- `SafeAppLauncher.launch(capability)` abre por intent LAUNCHER y habla
  "Abrí Uber. Todavía no solicité ningún viaje." (`AndroidSafeAppStarter`).
- `GlobalAssistantService.handleTransportAssist()` (V1.10.2) ya ofrece **abrir**
  Uber/Cabify/DiDi con confirmación (`pendingMobilityOpen`), sin pedir/confirmar/
  pagar. Ruta: OutdoorPhrases (fast-path outdoor del routing).
- Snapshot de pantalla: `OjoClaroAccessibilityService.readActivePackageName()` +
  `readVisibleNodeSummaries()` (lo mismo que usa ScreenIntelligence V2.2).
- Inyección QA: broadcast `com.ojoclaro.DEBUG_VOICE_TEXT --es goal "<frase>"`.
- Bloqueos financieros existentes: `taskIntent=payment kind=SENSITIVE_BLOCK`,
  `PrivacyGuard`, contrato "sí/dale/ok" nunca confirma.

## Diseño elegido para `UberScreenReasoner`
**Marcadores de TEXTO tolerantes (ES + EN), NO resource-ids.** Uber ofusca y rota
sus ids (`com.ubercab` usa ids dinámicos), así que clasificar por texto visible/
contentDescription es más robusto y resiliente a updates. Clasifica:
`LOGIN / HOME / DESTINATION_SEARCH / ROUTE_REVIEW / RIDE_OPTIONS / CONFIRM_RIDE /
DRIVER_SEARCH / TRIP_ACTIVE / UNKNOWN` y extrae pickup/destino/precio/tipo/pago +
controles riesgosos. Los marcadores se afinan contra la captura real cuando el
device esté desbloqueado; por ahora se validan con pantallas sintéticas en tests.

## Qué se puede automatizar SEGURO hoy (Level 2)
- Abrir `com.ubercab` (reusando el flujo existente, con confirmación de apertura).
- **Leer** la pantalla y describir: tipo de pantalla, origen, destino, precio/rango,
  tipo de Uber, método de pago (si aparecen) — solo lectura.
- Guiar paso a paso por voz.
- Detenerse ANTES del botón final y pedir intervención humana.

## Qué NO se debe automatizar (bloqueado SIEMPRE)
- Tocar "Confirmar/Solicitar/Pedir viaje" (botón final).
- Agregar/cambiar método de pago; tocar cualquier control de pago.
- Cancelar un viaje activo sin confirmación fuerte exacta.
- Llamar/mensajear al conductor; compartir ubicación.
- Actuar si hay LOGIN, error, permiso faltante o pantalla desconocida.
- Actuar sobre `com.ubercab.driver`.
- Pedir viaje sin leer con confianza origen + destino + precio + tipo + pago.

## Nivel de autonomía
**Level 2 — Copiloto guiado.** La acción final (pedir viaje) queda **bloqueada**
en v1 aun con la frase fuerte `"confirmo pedir Uber ahora"`: Estela responde que
todavía no puede pedir el viaje y deja al usuario en la pantalla para que lo toque.
