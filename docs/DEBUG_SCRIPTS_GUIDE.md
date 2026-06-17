# Guía de scripts de debug/QA · Estela / Ojo Claro

> Qué hace cada script, cuándo usarlo, qué NO hace y sus riesgos. **Ningún script
> debe correrse desatendido contra apps reales.** Todos requieren el Moto presente
> y, los de smoke, supervisión humana.

## Read-only (seguros para inspección)
### `scripts/dump_visible_nodes.ps1`
- **Qué hace**: dispara el broadcast `com.ojoclaro.DEBUG_DUMP_VISIBLE_NODES`; el
  servicio loguea (tag `EstelaVisibleNodeDump`) un resumen SANITIZADO de
  `readVisibleNodeSummaries()`. Guarda en `build/uber-captures/` (gitignored).
- **Preflight**: verifica `adb` + device visible → si no, `BLOCKED` (exit 2).
- **NO hace**: taps, pedir viaje, enviar, instalar, loguear texto sensible.
- **Uso**: `powershell -ExecutionPolicy Bypass -File scripts\dump_visible_nodes.ps1 -Stage uber_runtime_current`
- **Riesgo**: muy bajo (solo lectura). Requiere build DEBUG (el receptor solo
  existe en DEBUG).

### `scripts/capture_uber_screen.ps1`
- **Qué hace**: captura foco + `uiautomator dump` (XML crudo) + logcat filtrado a
  `build/uber-captures/`. Imprime un resumen estructural sanitizado.
- **Preflight**: sale si el device está bloqueado o no es Uber pasajero.
- **NO hace**: taps, envíos, pedidos. **El XML crudo puede tener direcciones reales
  → NO commitear** (está en `build/`, gitignored).
- **Uso**: `... capture_uber_screen.ps1 -Stage ride_options`

## Smoke (INTERACTIVOS — NO desatendidos)
### `scripts/run_v22_runtime_smoke.ps1`
- **Qué hace**: instala la APK debug y **inyecta** una batería de frases por
  `DEBUG_VOICE_TEXT` (incluye "sí", financieras) para verificar que el sistema las
  **bloquea/no actúa**. Captura logcat a `build/v22-smoke/`.
- **Preflight**: `BLOCKED` si el device no aparece (exit 2).
- **NO hace por sí mismo**: no toca botones, no envía, no paga — solo inyecta texto;
  el bloqueo lo hace la app. Aun así **toca el device** (install + broadcasts).
- **⚠️ NO correr desatendido.** Requiere humano para confirmar contadores (0 envíos,
  0 pagos, etc.) y un device desbloqueado.
- **Uso (supervisado)**: `... run_v22_runtime_smoke.ps1 -SkipInstall`

## Infra de demo / backend (no tocan apps reales)
- `start_estela_demo_backend.ps1` (uvicorn 8080, fija `OPENAI_MODEL=gpt-4.1-mini`),
  `start_estela_demo_ngrok.ps1`, `check_estela_demo_infra.ps1` (READY/PARTIAL/FAIL),
  `estela_demo_preflight.ps1`, `run_backend.ps1`. Operan sobre el backend local, no
  sobre el teléfono.

## Release (NO correr sin autorización explícita)
- `build_signed_apk.ps1`, `create_release_keystore.ps1`, `create_github_release.ps1`,
  `alpha_release_gate.ps1`. **No** ejecutar en sprints desatendidos (la regla es no
  release/push).

## Reglas transversales
- Todos los scripts ADB exigen el Moto presente; salen `BLOCKED` si no está.
- Los dumps crudos y screenshots van a `build/` (gitignored) y **no se commitean**.
- Inyección de voz solo en builds **DEBUG** (los receptores no existen en release).
- `MSYS_NO_PATHCONV=1` si se invoca adb desde Git Bash con paths.
- ASCII-only en los `.ps1` (PowerShell 5.1 mojibakea acentos).
