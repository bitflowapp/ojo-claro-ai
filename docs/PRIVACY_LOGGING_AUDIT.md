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

## ✅ Número de teléfono real — REMOVIDO del árbol (saneado)

**Estado actual (árbol de este PR):** el número real del operador y todas sus
variantes (nacional, internacional, con separadores) = **0 coincidencias** en el
contenido versionado. Verificado por escaneo count-only sobre el árbol auditado.

- `ScreenIntelligence.kt` — el `phoneFallback` del alias autorizado es **`null`**
  (placeholder sintético). La feature (abrir el chat de Marco por wa.me cuando no
  está visible; nunca para enviar) resuelve el número en runtime desde
  almacenamiento seguro, no desde el código versionado.
- Los tests que ejercitan esa feature (`ContactResolverTest`, `ScreenIntelligenceTest`,
  `ConversationV17Test`, `WhatsAppSmartComposeParserTest`, `EstelaInstagramDirectV112Test`,
  `VisibleNodeSanitizerTest`) usan **números ficticios** (p. ej. `+54 9 11 5550-0000`,
  `5491123400009`, `+54 9 351 7654321`) — nunca el real.

**Contexto histórico (ya remediado):** en trabajos previos existió un número real
tracked como fallback del alias autorizado. Esa historia fue **saneada** (rama
re-rooteada a un árbol limpio) y el árbol actual de este PR **no lo contiene**.
Cualquier mención previa a "número real tracked" corresponde a ese hallazgo
histórico ya remediado, **no** al estado presente. El número real no se incluye en
este documento.

## Logs de runtime (sanitización)
- `EstelaVisibleNodeDump` (`VisibleNodeSanitizer`): por nodo solo flags/longitudes/
  marcadores/tokens de categoría; montos → `ARS_AMOUNT_REDACTED`, pagos →
  `PAYMENT_LABEL`, direcciones → `UNKNOWN_TEXT`. **Nunca texto crudo** (cubierto por
  `VisibleNodeSanitizerTest`).
- `uberCopilot` / `screenIntel`: solo intent/app/screen/confidence/#risky y flags
  booleanos `hasPrice/hasType/hasPickup/hasDest`. **Nunca** precios/direcciones.
- `multiWindowSnapshot`: solo conteos/paquetes.

## Veredicto
**Sin datos sensibles tracked en el árbol actual.** El número real fue removido
(saneado): el árbol de este PR no lo contiene (exact + variantes = 0) y los tests
usan datos ficticios. `build/` gitignored; dumps crudos no commiteados. Logs
sanitizados y testeados.
