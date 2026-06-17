# V2.2 — Smoke físico real WhatsApp/Instagram · Reporte

## Datos
- **Dispositivo:** Moto G15 (`ZY32LHS6PS`). **Fecha:** 2026-06-13. **Rama:** `intelligence/contact-resolver-screen-reasoner-v22` (desde estable `dfba5b2`).
- **WhatsApp:** logueado (`com.whatsapp/.home.ui.HomeActivity` / `.Conversation`). **Backend:** levantado (`/health` 200).
- **Build/install de APK V2.2:** ❌ **NO** (C: ~0.35 GB → Gradle arriesga ENOSPC). El smoke corrió sobre el **APK estable instalado** (no el código V2.2).

## Qué se probó (físico, APK estable)
- **Frases de percepción NUEVAS** ("qué personas aparecen", "qué chat estoy viendo", "a quién le estoy por mandar esto") → **`no_local_match`** incluso dentro de un WhatsApp Conversation → **no existen en el APK instalado** (baseline: es lo que V2.2 agrega). PID 4925 estable, sin FATAL.
- **Comportamiento WhatsApp** (mismo APK que V2.1, re-confirmado este sprint): abrir WA, leer pantalla/chats/mensajes, abrir chat por **número** (wa.me) → **OK**; abrir/compose por **nombre** "Marco Luna" → `smartCompose resolved=not_found` (falla, como en V2.1).
- **Seguridad:** "sí" no envía; financiero "mandá plata"/"tocá pagar"/"CVV" → `SENSITIVE_BLOCK`. (Validado en V2.1, mismo APK.)

## Qué NO se pudo probar
- El código V2.2 (ContactResolver/ScreenReasoner/MessagingTaskPlanner) en vivo → requiere build+install.
- Que "abrí el chat de Marco Luna" funcione con la nueva resolución → es justo lo que V2.2 arregla, pero **no instalado**.

## Mensaje real enviado
**NO.** Igual que V2.1: la frase autorizada exacta "mandalo prueba autorizada" no está en el APK instalado (V2.1/V2.2 la agregan, sin compilar). Cantidad: **0**. Texto autorizado (no enviado): "Prueba controlada de Estela/Ojo Claro. Ignorar."

## Contadores prohibidos (todos 0)
"sí" envió **0** · sin confirmación fuerte **0** · duplicados **0** · contacto equivocado **0** · pagos/tarjetas **0** · llamadas **0** · audios **0** · pendings vivos **0** · crashes **0**.

## Riesgos restantes
- La inteligencia nueva está **sin verificar** (no compiló). El `currentChatTitle` heurístico debe probarse en pantallas reales antes de confiar el envío a la verificación de título.

## Qué NO mergear
- **Nada** de esta rama hasta: liberar disco → tests verdes → `assembleDebug` + install → smoke físico real de percepción/navegación/planner → 1 envío real controlado verificando que el chat es Marco Luna.

## Veredicto
**`V22_EXPERIMENTAL`.**

---

## ACTUALIZACIÓN — Smoke con el BUILD ROUTED instalado (2026-06-13 ~13:46)
Ya con la integración cableada (commits `c1c72dc`/`fe2a3f3`/`351c7f6`/`5b884be`) y
la APK debug V2.2 **instalada** (`install -r` Success en `ZY32LHS6PS`, accesibilidad
"Estela" rebindeó sola, pid 19978 estable). Batería disparada por
`scripts/run_v22_runtime_smoke.ps1 -SkipInstall` (21/21 comandos, `result=0`).

**V2.2 AHORA ACTIVO** (ya no `no_local_match`): `screenIntel intent=who_is_visible`,
`intent=which_chat app=WHATSAPP`, `openChat defer=existing_whatsapp_path` ×2.

**Cambio de entorno respecto al smoke anterior:** WhatsApp pasó a **DESLOGUEADO**
(`com.whatsapp/.loginfailure.LogoutMessageActivity`). Por eso percepción dio
`conf=LOW`/0 contactos: no hay lista de chats que leer → narrador honesto. La
apertura por nombre **defirió** al flujo existente (no se ejercitó la resolución
nueva ni el fallback por número de Marco Luna).

**Seguridad (medida en logcat, todos 0):** "sí" → IG `weak_confirmation_rejected`
(0 envíos); financiero plata/pagar/CVV → `SENSITIVE_BLOCK` ×3; llamá/audio →
`no_local_match` sin acción real; 0 cámara; pendings limpios (WA
`cancelled_awaiting_recipient` + IG `cancelled_by_user`); 0 crashes (pid estable).

**Veredicto de esta corrida: `V22_ROUTED_PARTIAL`** (activo + seguro, pero
percepción/navegación sin demostrar por WhatsApp deslogueado). Detalle completo en
`docs/V22_RUNTIME_ROUTING_REPORT.md`. PENDIENTE: re-loguear WhatsApp y repetir
percepción/navegación.

---

## ACTUALIZACIÓN — Smoke alternativo INSTAGRAM (2026-06-13 ~14:04)
WhatsApp seguía deslogueado → se validó runtime/regresión con **Instagram
logueado** (`AccountManagerService accountType=www.instagram.com`). Batería de 13
comandos por `DEBUG_VOICE_TEXT` (13/13 `result=0`, pid 19978 estable).

**Instagram intacto (sin regresión):** abrir IG (`OPEN_MESSAGING_APP`), abrir chat
de Sofi (`OPEN_CHAT hasContact=true`, flujo existente — V2.2 hace `skip=instagram`
por diseño), preparar mensaje (`SEND_TEXT_PENDING_CONFIRMATION msgLen=27`, **no
enviado**).

**V2.2 activo:** `screenIntel intent=who_is_visible`, `which_chat`, y esta vez
**también `who_am_i_sending`** (que en el run WA quedó sombreado). Percepción dio
`app=OTHER`/`conf=LOW` (limitación de `readActivePackageName` tras cambio de app) →
routea + responde honesto, sin describir aún la pantalla con utilidad.

**Seguridad (logcat, todos 0):** sin `instagramSend outcome=sent` ni tap de envío;
"sí" → `no_local_match` (sin pending vivo; el anti-zombie limpió el pending tras el
TTS) → **0 envíos**; pagos plata/pagar/CVV → `SENSITIVE_BLOCK` ×3; "llamá a Sofi" y
"mandá audio" → `no_local_match` (0 llamadas, 0 audios); 0 cámara; 0 crashes/FATAL
propios (pid 19978 estable).

**Veredicto de esta corrida: `V22_ROUTED_PARTIAL`** — la **regresión + runtime +
seguridad de Instagram = PASS** (IG intacto, V2.2 routea, 0 acciones inseguras, 0
crashes); pero la percepción no reconoció la pantalla de IG (`app=OTHER`) y el path
de apertura por nombre nuevo no se ejercitó (IG se salta por diseño). Logs en
`build/v22-smoke-ig/`.

---

## ACTUALIZACIÓN — Fix de `activeApp` + re-smoke Instagram (2026-06-13 ~14:43)
Se agregó `ActiveAppResolver` (fuente estable: rawPackage → marcadores IG
`instagramScreenCheck` → handoff WhatsApp → nodos) + override en `ScreenReasoner`
+ log de decisión. Tests (28) + `assembleDebug` + install PASS; accesibilidad
rebindeó (pid 30003).

**Resultado honesto:** el fix corre y decide bien, pero **NO se vio
`resolved=INSTAGRAM`** porque la ventana activa real en cada lectura fue
`com.android.systemui` (cortina de notificaciones abierta y luego **lockscreen**).
El resolver clasificó correcto `resolved=OTHER source=RAW_PACKAGE_OTHER` (no inventó
IG). Cuando SystemUI es la ventana activa, `instagramScreenCheck` también dice "no
IG" (no hay ventana de IG que leer): el fix solo ayuda en el caso overlay/transición
(cubierto por unit tests, no dado en device). **Seguridad 0** (sin envíos/tap; "sí"
→ `no_local_match`; plata/pagar/CVV → `SENSITIVE_BLOCK` ×3; 0 cámara; 0 crashes).
**Device quedó LOCKED** (keyguard con credencial → unlock humano). PENDIENTE: corrida
con IG al frente, desbloqueado y sin la cortina abierta. Logs en
`build/v22-smoke-ig-fix/`.

---

## ✅ VALIDACIÓN FINAL — Instagram desbloqueado (2026-06-13 ~15:01)
Moto desbloqueado, Instagram al frente, cortina cerrada. Probe de 7 comandos. Logs
en `build/v22-smoke-ig-probe/`.

**El fix de `activeApp` quedó VALIDADO end-to-end:**
- `screenIntel activeApp raw=com.instagram.android igMarkers=true resolved=INSTAGRAM source=RAW_PACKAGE`
- `who_is_visible app=INSTAGRAM contacts=1 conf=HIGH` y `which_chat app=INSTAGRAM type=CONVERSATION conf=HIGH` → **percepción útil** (antes OTHER/conf=LOW).
- IG intacto: `OPEN_CHAT Sofi hasContact=true`; `SEND_TEXT_PENDING_CONFIRMATION` + `draft_armed` (no enviado); "sí" → `weak_confirmation_rejected` (**0 envíos**); "cancelar" → `cancelled_by_user` (pending limpio).
- Seguridad: 0 envíos/llamadas/audios/cámara; 0 crashes (pid 30003 estable).

**Veredicto Instagram: `V22_INSTAGRAM_RUNTIME_READY`** — con la app visible, V2.2
reconoce Instagram con confianza ALTA, percepción útil, flujo IG intacto y "sí" no
envía. (WhatsApp logueado sigue pendiente: el Moto lo tiene deslogueado.)
