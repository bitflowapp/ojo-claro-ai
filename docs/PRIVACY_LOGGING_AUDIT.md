# Auditoría de privacidad — logs, dumps y datos sensibles · 2026-06-13

> Sprint de hardening desatendido. Rama `chore/unattended-hardening-sprint`. Solo
> lectura + un cambio de test (número ficticio). Nada se ejecutó contra apps reales.

## Qué se revisó
- 134 archivos tracked en `docs/`, `scripts/`, `androidApp/src/test/resources/`.
- `androidApp/src/main` y `src/test` por: teléfonos, tarjetas/CBU (corridas de
  dígitos), tokens/API-keys/passwords, direcciones reales.
- `build/` y dumps crudos de Uber.

## Hallazgos
| Ítem | Resultado |
|---|---|
| API-keys / tokens / passwords reales | **Ninguno**. Las coincidencias de "password" son el campo `isPassword` (código que maneja campos de contraseña, NO valores). Los tests de source-scan (`EstelaIntentSecurityTest`, `CriticalStringsQualityTest`) ya prohíben `sk-`/`OPENAI_API_KEY`. |
| Tarjetas / CBU (12+ dígitos) en docs/fixtures | Solo **timestamps** (`1710000000000`) y números de ejemplo **obviamente ficticios** (`5491123400009`, `+54 9 11 2345-6789`). No hay tarjetas reales. |
| Direcciones reales en fixtures | **Ninguna**. Los fixtures están sanitizados ("Direccion de prueba 123", "Direccion de origen/destino prueba"). |
| Dumps crudos de Uber / screenshots | **No tracked**. `build/uber-captures/` (XML/PNG crudos) está bajo `build/`, que **está gitignored**. |
| Mensajes privados / nombres de contactos | No hay contenido de mensajes ni listas de contactos reales tracked. "Marco Luna"/"Sofía" son etiquetas de prueba. |

## ⚠️ Número de teléfono real tracked (intencional/autorizado)
El número del operador autorizado fue **removido** del árbol; se resuelve en runtime desde almacenamiento seguro. (Ejemplo ficticio: `+54 9 11 5550-0000`.)
- `ScreenIntelligence.kt` — `phoneFallback` del **alias autorizado** (feature real:
  abrir el chat de Marco por wa.me cuando no está visible; nunca para enviar).
- Varios tests pre-existentes (`ContactResolverTest`, `ScreenIntelligenceTest`,
  `ConversationV17Test`, `WhatsAppSmartComposeParserTest`, `EstelaInstagramDirectV112Test`)
  que validan esa feature.
- `docs/V22_RUNTIME_ROUTING_REPORT.md` (referencia).

**Valoración**: es un número real pero **deliberado y autorizado** por el usuario,
**pre-existente** (commiteado en trabajos previos), y **funcional** (la feature de
fallback depende de él). NO se removió (rompería la feature y contradice trabajo
autorizado). **Cambio aplicado en esta sesión**: el test `VisibleNodeSanitizer`
(que yo había agregado) usaba el número real como input de prueba de redacción →
reemplazado por un número **ficticio** (`+54 9 351 7654321`); el test sigue
verificando que NO se filtra al log.

**Recomendación (decisión del usuario, no urgente)**: si se quiere quitar el número
real del repo, moverlo a un config local NO commiteado (`local.properties` /
`BuildConfig` desde gradle.properties gitignored) y dejar los tests con un número
ficticio. Hoy NO es un leak accidental: es un dato funcional autorizado.

## Logs de runtime (sanitización)
- `EstelaVisibleNodeDump` (`VisibleNodeSanitizer`): por nodo solo flags/longitudes/
  marcadores/tokens de categoría; montos → `ARS_AMOUNT_REDACTED`, pagos →
  `PAYMENT_LABEL`, direcciones → `UNKNOWN_TEXT`. **Nunca texto crudo** (cubierto por
  `VisibleNodeSanitizerTest`).
- `uberCopilot` / `screenIntel`: solo intent/app/screen/confidence/#risky y flags
  booleanos `hasPrice/hasType/hasPickup/hasDest`. **Nunca** precios/direcciones.
- `multiWindowSnapshot`: solo conteos/paquetes.

## Veredicto
**Sin datos sensibles accidentales tracked.** Único dato real es el teléfono
autorizado de Marco (intencional, documentado, con recomendación de mover a config).
`build/` gitignored; dumps crudos no commiteados. Logs sanitizados y testeados.
