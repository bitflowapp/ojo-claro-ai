# Índice de estado del proyecto · Estela / Ojo Claro · 2026-06-13 · act. 2026-06-16 (WhatsApp hardening)

> Índice corto para orientarse entre ramas. **Todo local, sin push.** La regla #1
> es no romper la demo estable.

## Ramas y para qué sirven
| Rama | Estado | Para qué | ¿Mergear? |
|---|---|---|---|
| `fix/estela-real-device-intelligence` @ `dfba5b2` | **ESTABLE / DEMO** | La demo que funciona en el Moto. Base de todo. | NO tocar; es la referencia. |
| `intelligence/contact-resolver-screen-reasoner-v22` | Experimental, validado parcial | Screen Intelligence (ContactResolver/ScreenReasoner/ActiveAppResolver); percepción + abrir chat. Instagram runtime OK. | NO mergear hasta WhatsApp logueado + smoke completo. |
| `mobility/uber-copilot-v1` @ `d4f1383` | **CONGELADO** — `UBER_COPILOT_PARTIAL` + `UBER_PRICE_NOT_ACCESSIBLE` | Copiloto Uber Level 2 (lee/guía, no pide). Incluye fix de captura multi-ventana. | NO mergear; pedido de viaje es MANUAL; precio no accesible. |
| `whatsapp/real-device-blind-user-sprint` | Experimental | Envío seguro WhatsApp (V2.1). | NO mergear sin envío real controlado. |
| `frontier/estela-v2-commercial-product-sprint` | Experimental | Sprint comercial V2.0 (diagnóstico, frases naturales). | NO mergear sin build+test+smoke. |
| `feature/estela-live-demo-ui`, `feature/gpt-mini-agent-api`, `feature/samsung-alpha-hardening` | Históricas | Features previas. | Revisar antes de mergear. |
| `chore/unattended-hardening-sprint` | **Actual** · HEAD `2586d17` | Este sprint: seguridad/tests/docs/auditoría + endurecimiento WhatsApp (ver sección abajo). Sin acciones reales. | Mergeable a la rama de trabajo cuando Marco revise. |

## Qué está listo vs parcial
- **Listo para demo**: la rama estable `dfba5b2` (cámara/escena/voz/lectura básica).
- **Parcial (seguro, no listo para autonomía)**: V2.2 Screen Intelligence (IG sí,
  WhatsApp pendiente por logout), Uber copiloto (lee/guía, no pide).
- **No listo**: pedido automático de Uber, envío real automático de WhatsApp/IG.

## Qué NO mergear (resumen)
- Nada experimental a `main` o a la estable sin: disco OK + tests verdes +
  `assembleDebug` + smoke físico supervisado.
- El copiloto Uber no habilita pedir viaje; queda manual.

## Próximos pasos por prioridad
1. **No romper la demo estable** (`dfba5b2`).
2. **WhatsApp logueado + V2.2 contacto por nombre**: validar percepción/navegación
   con WhatsApp realmente logueado (hoy el Moto lo tiene deslogueado).
3. **Uber copiloto guiado** (no pedido automático): mantener Level 2; el pedido real
   sigue manual.
4. **OCR regional de tarifa Uber**: solo futuro (Uber no expone el precio por
   Accessibility); no ahora.
5. **Producción**: Play Store / onboarding / backend de producción (confirmar que el
   release no incluye los receptores DEBUG).

## WhatsApp hardening · `chore/unattended-hardening-sprint` (local-only, sin push)

Cadena de commits de seguridad de WhatsApp, en orden (todos **locales, sin push**):
1. `c2dbac4 feat(whatsapp): add full-control safety foundation` — capability model
   + feature flags peligrosas en OFF por defecto + contadores auditables + contexto redactado.
2. `2c53a72 feat(whatsapp): route forbidden actions locally before LLM` — acciones
   prohibidas (borrar/archivar/bloquear/pagar/…) ruteadas localmente y rechazadas antes del LLM.
3. `2586d17 feat(whatsapp): add trusted relationship contacts with destination verification`
   — **commit actual** (ver detalle abajo).

### `2586d17` — Trusted Contacts / Destination Verifier
- **Contactos confiables por relación** ("mi novia"/"mi pareja"/"mi contacto de prueba")
  → clave canónica local; deja de pedir abrir el chat a mano (bloqueo de producto para
  personas no videntes).
- **Verificación de destino** tras abrir por deep link, **antes** de preparar cualquier
  mensaje (estados VERIFIED / MISMATCH / NOT_IN_CHAT / NO_ENTRY_FIELD / UNVERIFIED_NO_SIGNALS / TIMEOUT).
- **Parser de composición por relación/contacto** ("mandale a mi novia que…"):
  resolver → abrir → verificar → recién preparar borrador (doble confirmación; **nunca envía por defecto**).
- **Store local** de contactos por relación (SharedPreferences privadas; logs redactados, número nunca completo).
- Integrado en las rutas WhatsApp **blind-first**.
- Garantías: **no real send**, sin flags peligrosos activos, **sin smoke físico** todavía.
  Todo local; nada al backend/LLM.
- Estado: **local-only, sin push.** 2901 tests verdes, `assembleDebug` PASS. Pendiente: smoke físico supervisado.

Archivos principales: `DebugCommandActivity.kt`, `OjoClaroAccessibilityService.kt`,
`WhatsAppBlindRouteNarrator.kt`, `GlobalAssistantService.kt`, `WhatsAppDestinationVerifier.kt`,
`WhatsAppRelationshipAlias.kt`, `WhatsAppRelationshipComposeParser.kt`,
`RelationshipContactStore.kt`, tests relacionados, `docs/WHATSAPP_TRUSTED_CONTACTS.md`.

## Documentación de referencia
- Seguridad: `ACTION_SAFETY_AUDIT.md`, `PRIVACY_LOGGING_AUDIT.md`.
- WhatsApp: `WHATSAPP_TRUSTED_CONTACTS.md`, `WHATSAPP_FULL_CONTROL_SAFETY.md`.
- Uber: `UBER_COPILOT_REPORT.md`, `UBER_SCREEN_EXTRACTION_NOTES.md`,
  `UBER_VISIBLE_NODE_RUNTIME_DUMP.md`, `UBER_COPILOT_SMOKE.md`.
- Screen Intelligence: `V22_RUNTIME_ROUTING_REPORT.md`.
- Scripts: `DEBUG_SCRIPTS_GUIDE.md`. Este sprint: `UNATTENDED_HARDENING_REPORT.md`.
