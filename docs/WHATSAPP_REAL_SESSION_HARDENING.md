# WhatsApp — Real-Session Hardening (resultados, sanitizado)

**Fecha:** 2026-06-16 · **Rama:** `chore/unattended-hardening-sprint`
**Alcance:** registro versionado (solo flags/longitudes) de la sesión de hardening de WhatsApp validada en vivo sobre el chat propio, en modo dry-run. **Sin envío real, sendTap 0 en todo el flujo.**

> Nota de privacidad: este documento contiene **únicamente** marcadores de log, banderas booleanas y longitudes. **No** incluye número de teléfono, contenido de chats, tokens, ni logs crudos. El reporte operativo completo y las notas de sesión viven fuera del repo.

---

## Commits relacionados (todos LOCALES, sin push)

| Commit | Tipo | Contenido |
|---|---|---|
| `89f9d06` | fix | Microfix WA-5: cancelación prioritaria sobre el STOP global |
| `8557d4d` | feat | Accessibility onboarding (guiar recuperación si el servicio está apagado) |
| `afbdfea` | test | Fixtures de fiabilidad + matriz de capacidades + fix de `inChat` |
| `dee46aa` | feat | Fluidez de voz y robustez |

---

## 1. Microfix WA-5 — cancelación prioritaria sobre el STOP global ✅ VALIDADO EN VIVO

**Problema:** con un envío **pendiente WA-5**, frases de cancelación que también son STOP global ("pará no mandes", "no mandes", "pará", "basta", "me equivoqué") matcheaban el **STOP global ANTES** del cancel WA-5 → cancelaban y silenciaban, pero **no** reproducían el copy "Cancelado. No envié nada." ni **limpiaban el borrador** del campo.

**Fix (`89f9d06`):**
- `GlobalAssistantService.handleRecognizedText`: **guard de cancelación prioritaria** insertado ANTES del bloque `when { isStopModeCommand(text) -> … }`. Si hay `pendingWhatsAppReply` (o `pendingWhatsAppSendDraft`) y la frase es cancelación (`WhatsAppReplyPhrases.isCancel` / `isCancelSend`) → rutea a la ruta segura WA-5 (`handlePendingWhatsAppReplyConfirmation`), que limpia el borrador propio (`setWhatsAppDraft("")`) y dice "Cancelado. No envié nada.".
- **Sin pending —o ante un barge-in puro ("callate"/"silencio")— el STOP global queda intacto.**
- Tests: `WhatsAppReplyConfirmationResolverTest.stopLikeCancelPhrasesCancelAtStep2` + `WhatsAppAnxietyHardeningContractTest.whatsAppCancelHasPriorityOverGlobalStop`; ajuste de 2 contract tests a `lastIndexOf` (el guard agregó una llamada previa a `handlePendingWhatsAppReplyConfirmation`).
- **`testDebugUnitTest`: 2780 / 0** · **`assembleDebug`: PASS**.

### Smoke en vivo (chat propio, dry-run)

Gate read-only previo:

| Check | Valor |
|---|---|
| foreground | `com.whatsapp/com.whatsapp.Conversation` |
| entryField | 1 |
| callButtons | 0 (self-chat) |
| detector | `touchProbe inWhatsApp=true inChat=true` |
| sendTap inicial | 0 |

Traza ordenada (logcat, sanitizada):

```
STEP 1  (len=27)
  ROUTING_AUDIT handler=whatsapp_reply_dry_run
  WHATSAPP_REPLY_TEXT_EXTRACTED len=14      (muletilla stripeada)
  whatsappDraftSet ok=true draftLen=14
  WHATSAPP_REPLY_DRAFTED len=14
  WHATSAPP_SEND_CONFIRMATION_REQUIRED step=1
STEP 2  (len=2)
  WHATSAPP_SEND_CONFIRMATION_1_OK
  WHATSAPP_SEND_CONFIRMATION_2_REQUIRED step=2
STEP 3  (len=14, frase de cancelación stop-like)
  whatsappDraftSet ok=true draftLen=0                      <- BORRADOR LIMPIADO
  WHATSAPP_EMERGENCY_CANCEL source=pending_reply step=2    <- RUTA WA-5 (no STOP global)
  WHATSAPP_SEND_CANCELLED
  WHATSAPP_NOT_SENT_REASSURANCE
  TTS: "Cancelado. No envié nada."
```

- Campo tras cancelar: placeholder de campo vacío (`draftLen=0`).
- **`sendTapLogCount final = 0`** (ningún `whatsappSendTap`; botón enviar nunca tocado).
- Criterios PASS: prepara el borrador ✓ · pasa a confirmación 2 ✓ · cancela vía **WA-5 (no STOP genérico)** ✓ · limpia borrador ✓ · dice "Cancelado. No envié nada." ✓ · sendTap=0 ✓ · sin LLM/fallback ✓ · logs sanitizados ✓.

---

## 2. Fix de `WhatsAppScreenDetector.inChat` (`afbdfea`) ✅ VALIDADO EN VIVO

- **Causa raíz:** (a) con borrador escrito, el label del `EDIT_TEXT` era el texto tipeado → `isMessageField` (exigía label "mensaje"/vacío) daba false; (b) `ScreenSnapshot` no exponía el activity `.Conversation`.
- **Fix:** `ScreenSnapshot.activityClassName` ← provider ← `readActiveWindowClassName()`; el detector usa `.Conversation` como señal fuerte + amplía `isMessageField` (acepta borrador) + `signals`/`reason` content-free; activities de lista/login → no-chat.
- **En vivo:** caso negativo (lista de chats → `inChat=false`) y caso positivo (dentro del chat → `inChat=true`, probe directo y detector coinciden).

---

## 3. Invariantes de seguridad (toda la sesión)

Envío real: **NO** · botón enviar: **NO** (sendTap 0) · taps sobre botones: **NO** · chats de terceros: **NO** · llamadas/videollamadas/audios/archivos/fotos/stickers/pagos: **NO** · borrar/archivar/bloquear: **NO** · contenido privado en logs: **NO** (solo flags/longitudes) · contenido al backend/LLM: **NO** · push: **NO**.

El único call-site de envío real (`tapWhatsAppSend`) permanece detrás de la confirmación fuerte exacta y nunca se alcanzó en este flujo.

---

## 4. Gotchas del harness de QA (smoke por broadcast)

1. **Pacing:** `completeOverlayVoiceTurn("tts_completed")` limpia TODO pendiente sin confirmar al terminar el TTS (anti-zombie, por diseño). En el smoke hay que inyectar los follow-ups con el turno **aún activo** (~2 s entre comandos, una sola sesión de `sh` con `sleep` en el dispositivo). Si se espera a que termine el TTS previo, el pending ya murió y el segundo comando cae a `no_local_match`.
2. **Quoting:** `adb shell` une su argv con espacios y PowerShell 5.1 descarta las comillas embebidas → los goals multi-palabra llegan rotos a `am`. Escapar los espacios a nivel sh (`--es goal a\ b\ c`), **sin** comillas dobles.
3. **Borrador persistente:** WhatsApp guarda el borrador entre reinicios → un leftover no es un draft fresco; confiar en el log `whatsappDraftSet ok=true draftLen=N`, no solo en el campo (vacío muestra el placeholder).
4. **Re-vinculación:** tras `adb install -r` la Accesibilidad se desvincula y NO re-bindea por ADB (anti-malware) → reboot (re-bindea lo ya habilitado) + desbloqueo manual con PIN (BFU). Servicio estable en idle; el único disconnect/connect es el cierre del turno single-shot (esperado).

---

## 5. Estado

Código del microfix commiteado (`89f9d06`, local, sin push). Validación física PASS en el dispositivo de QA. El reporte operativo detallado y las notas de sesión se mantienen fuera del repo a propósito; este archivo es el registro versionado y sanitizado.
