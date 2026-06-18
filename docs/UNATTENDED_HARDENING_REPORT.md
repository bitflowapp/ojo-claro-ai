# Sprint de hardening desatendido · Estela / Ojo Claro

> Fecha: **2026-06-13**. Sesión sin Marco presente. **Cero acciones contra apps
> reales, cero ADB contra Uber/WhatsApp/Instagram, cero smoke físico, cero push/
> merge/PR/release, sin tocar `.idea`.** Solo: auditoría, docs, tests puros, build
> local, commits locales.

## Estado al iniciar (FASE 1 — congelado)
- **Rama de trabajo creada**: `chore/unattended-hardening-sprint` (desde
  `mobility/uber-copilot-v1` @ `d4f1383`), para NO mover el HEAD congelado de Uber.
- **Estable / demo**: `fix/estela-real-device-intelligence` @ `dfba5b2` — **INTACTA**.
- **V2.2 Screen Intelligence**: `intelligence/contact-resolver-screen-reasoner-v22`
  — Instagram runtime validado; WhatsApp pendiente (Moto deslogueado). No verificado
  en esta sesión (sin ADB).
- **Uber Copilot v1**: `mobility/uber-copilot-v1` @ `d4f1383` — CONGELADO,
  `UBER_COPILOT_PARTIAL` + `UBER_PRICE_NOT_ACCESSIBLE`. No tocado.
- **Disco C:**: ~14 GB libres.
- **Working tree**: limpio salvo `.idea/*` (pre-existente, NO mío) y
  `CHECKLIST_PRUEBA_FISICA_FASE2A.md` (untracked, NO mío).
- **Dumps crudos / screenshots tracked**: ninguno (`build/` gitignored).

## Bloqueado por humano (no se puede sin Marco)
- Validar WhatsApp logueado + contacto por nombre (necesita desbloquear Moto +
  loguear WhatsApp).
- Cualquier smoke físico / ADB contra apps reales.
- Pedir Uber, tocar botón final, enviar mensajes, pagos, llamadas, audios.
- OCR de tarifa de Uber (futuro, fuera de alcance).
- push / merge / PR / release.

## Trabajado en esta sesión (sin humano)
1. **Auditoría de seguridad de acciones** → `docs/ACTION_SAFETY_AUDIT.md`.
2. **Auditoría de privacidad de logs/dumps** → `docs/PRIVACY_LOGGING_AUDIT.md`.
3. **Tests de seguridad cruzados** → `SafetyRegressionTest` (11 tests nuevos).
4. **Guía de scripts de debug** → `docs/DEBUG_SCRIPTS_GUIDE.md`.
5. **Índice de estado del proyecto** → `docs/PROJECT_STATE_INDEX.md`.
6. **Privacidad**: número real en un test propio (`VisibleNodeSanitizerTest`)
   reemplazado por uno ficticio.
7. Build local de verificación.

## Resultados
- **Tests**: `testDebugUnitTest` → **BUILD SUCCESSFUL**. `SafetyRegressionTest`
  11/0; **0 fallas en 231 clases de test** (sin regresión).
- **Build**: `assembleDebug` → (ver sección "Build" al pie).
- **Riesgos detectados**: (a) receptores DEBUG exportados — acotados a builds DEBUG
  (no existen en release); (b) precio de Uber no accesible — mitigado con respuesta
  honesta; (c) teléfono real autorizado tracked — intencional/pre-existente, con
  recomendación de mover a config.
- **Riesgos corregidos**: número real → ficticio en `VisibleNodeSanitizerTest`;
  documentación de las invariantes de seguridad como tests de regresión.
- **Datos sensibles tracked**: solo el teléfono autorizado de Marco (intencional,
  documentado en `PRIVACY_LOGGING_AUDIT.md`). Sin claves/tokens/tarjetas/direcciones.
- **¿push/merge/PR/release?** **NO.**
- **¿ADB / apps reales?** **NO.**

## Próximos pasos (con Marco presente)
1. WhatsApp logueado → validar V2.2 contacto por nombre + smoke supervisado.
2. Confirmar que el build de producción NO incluye los receptores DEBUG.
3. Decidir si mover el teléfono autorizado a config no commiteada.
4. Mantener Uber en Level 2 (pedido manual); OCR de tarifa como futuro.

## Veredicto
**SAFE_PROGRESS** — avance real en seguridad, tests y documentación, sin ninguna
acción peligrosa ni contra apps reales. Estable `dfba5b2` y Uber `d4f1383` intactos.

## Build
- `assembleDebug -PojoClaroAssistantBaseUrl=<ngrok>` → **BUILD SUCCESSFUL**.
- APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (~57 MB).
- Warnings: 1 deprecación benigna (`LocalLifecycleOwner` movida a lifecycle-runtime-compose); sin errores.
- **NO instalada** en el Moto (regla: sin ADB/apps reales desatendido).
