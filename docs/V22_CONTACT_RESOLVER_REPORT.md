# V2.2 — Contact Resolver + Safe Planner · Reporte

## Qué se implementó (código real, SIN COMPILAR)
- **`ContactResolver`** (`agent/intelligence/ContactResolver.kt`) — PURO. Resuelve una referencia humana contra candidatos visibles + alias.
  - Salidas explícitas: `Resolved / Ambiguous / NotFound / Unsafe`.
  - Reglas: exact gana; normaliza mayúsculas/acentos; "Marco"→"Marco Luna" (prefijo de token); "Sofi"→"Sofía"; **múltiples Marcos → Ambiguous** (nunca auto-elige); número `0000005678` SOLO como fallback de alias autorizado (Marco Luna); jamás resuelve a un botón de acción (enviar/llamar/pagar); query con pinta de credencial → Unsafe.
- **`MessagingTaskPlanner`** (`agent/intelligence/MessagingTaskPlanner.kt`) — PURO. `plan(goal, screenModel, resolution)` → `Steps / Ask / Refuse`. Pasos seguros: abrir app → resolver → abrir chat → **VERIFICAR que el chat es el destino** → preparar texto → pedir confirmación FUERTE.
  - `classifyConfirmation`: "sí"/"dale"/"ok" → WEAK_REJECTED; solo "mandalo"/"enviar mensaje"/"confirmo enviar"/"mandalo prueba autorizada" → STRONG_SEND; "cancelar"/"no mandes nada"/... → CANCEL.
  - Compuerta `canSend(...)`: **true SOLO si** confirmación fuerte + chat verificado + texto coincide con el campo + estamos en una conversación cuyo título coincide con el destino + no hay pago/tarjeta a la vista. Esto encarna "nunca enviar al equivocado".

## Qué quedó documentado (no codeado)
- Integración a GAS (adapter + handlers + fallback) → diseño en `V22_SCREEN_REASONER_AUDIT.md`. No se editó el GAS (riesgo + build bloqueado).

## Tests (escritos, NO corridos)
- `ContactResolverTest` (10 casos): exacto, parcial "Marco", "sofi"→"Sofía", múltiples Marcos→Ambiguous, nombre completo elige bien, no encontrado, fallback número autorizado, query sensible→Unsafe, WhatsApp/Instagram separados, nunca resuelve a botón.
- `MessagingTaskPlannerTest` (12 casos): plan WhatsApp/Instagram, ambiguous/notfound→Ask, mensaje sensible→Refuse, clasificación de confirmación, `canSend` solo con fuerte+chat verificado, "sí" no envía, **no envía al chat equivocado**, no envía fuera de conversación, no envía con pago a la vista, no envía si el texto no coincide.

## Tests corridos
- **Ninguno** (C: ~0.35 GB; Gradle arriesga ENOSPC). Escritos para correr en una máquina con disco.

## Contadores prohibidos (físico, sobre APK estable)
- "sí" envió **0** · sin confirmación fuerte **0** · duplicados **0** · contacto equivocado **0** · pagos/tarjetas **0** · llamadas **0** · audios **0** · pendings vivos **0** · crashes **0**.

## Mensaje real enviado
**NO** (el código V2.2 no está instalado; ver `V22_WHATSAPP_INSTAGRAM_REAL_SMOKE.md`).

## Veredicto
**`V22_EXPERIMENTAL`** — la lógica de resolución segura está construida y testeada (tests escritos), pero **sin compilar/instalar**; no validada en dispositivo. No mergear sin build + tests + smoke.
