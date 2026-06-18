# Auditoría de seguridad de acciones reales · Estela / Ojo Claro

> Sprint de hardening desatendido (2026-06-13). Rama `chore/unattended-hardening-sprint`
> (desde `mobility/uber-copilot-v1` @ d4f1383). **Solo lectura de código** — no se
> ejecutó nada contra apps reales. Lo no verificable se marca "no verificado".

## 1. Qué acciones PUEDEN tocar apps reales
| Acción | Dónde | Freno |
|---|---|---|
| Abrir app (Uber/WA/IG/Maps) | `SafeAppLauncher` / `WhatsAppIntentHelper.openWhatsApp` / `handleTransportAssist` | Apertura es segura (no pide/paga); ride dice "todavía no solicité ningún viaje" |
| Abrir chat WhatsApp por nombre (tap) | `openVisibleWhatsAppChatByName` | Solo navega; matcher re-verifica; nunca envía |
| Abrir chat WhatsApp por número (wa.me) | `WhatsAppIntentHelper.openChat` | Sin texto, sin envío |
| Tocar enviar WhatsApp | `tapWhatsAppSend(expectedMessage)` (single call-site) | Verifica que el campo == texto esperado; exige confirmación FUERTE |
| Instagram: set-draft + enviar | `handleInstagramTaskCommand` (V1.12) | Deja borrador (pending); "sí" → `weak_confirmation_rejected` |
| Videollamada / audio guiado | V1.11 task assist | Requiere confirmación; tap real históricamente bloqueado/guiado |

## 2. Qué acciones están BLOQUEADAS (por diseño)
- **Pedir/confirmar viaje de Uber**: el copiloto NUNCA toca el botón final; `confirmo
  pedir Uber ahora` = `blocked_v1 tapped=false`. Solo lee/guía.
- **Pagos/tarjetas**: `taskIntent=payment` → `SENSITIVE_BLOCK`; `MessagingTaskPlanner.canSend`
  devuelve false si hay `PAYMENT`/`CARD` a la vista; `WhatsAppVoiceSendPhrases.looksSensitive`
  bloquea credenciales/CBU/tarjeta (8+ dígitos) ANTES de preparar el envío.
- **Enviar al chat equivocado**: `canSend` exige que el título de la conversación
  coincida con el destino resuelto.
- **Enviar con confirmación débil**: "sí"/"dale"/"ok"/"okey"/… → `WEAK_REJECTED`;
  solo frases fuertes (`mandalo`/`confirmo enviar`/…) llegan a `STRONG_SEND`.
- **Llamadas/audios reales sin confirmación**: flujos guiados; no se disparan solos.

## 3. Frases DÉBILES que se rechazan (jamás confirman)
`sí`, `si`, `dale`, `ok`, `okey`, `oka`, `sip`, `obvio`, `claro` →
`MessagingTaskPlanner.WEAK_AFFIRM` / no están en `WhatsAppVoiceSendPhrases.CONFIRM_SEND`
/ no son `UberIntent`. Cubierto por `SafetyRegressionTest` + `MessagingTaskPlannerTest`.

## 4. Frases FUERTES que existen
- Envío WhatsApp/IG: `mandalo`, `envialo`, `enviar mensaje`, `confirmo enviar`,
  `confirmo`, `mandalo prueba autorizada` (igualdad EXACTA).
- Uber (v1): `confirmo pedir Uber ahora` — reconocida pero **bloqueada** (no toca).
- Cancelar (siempre gana): `no`, `cancelar`, `no mandes nada`, etc.

## 5. Rutas que NUNCA deben ejecutarse sin humano
- Tap del botón final de Uber (pedir/confirmar viaje). **No implementado**.
- Envío real de WhatsApp/IG con frase fuerte (existe el camino; requiere humano y
  contexto autorizado; el envío real nunca se validó automáticamente).
- Cualquier pago, alta de tarjeta, llamada o audio real.
- Smoke físico / ADB contra apps reales.

## 6. Riesgos actuales
- **Uber precio no accesible** (`UBER_PRICE_NOT_ACCESSIBLE`): si el copiloto guiara
  un pedido, el usuario podría no escuchar la tarifa. Mitigado: respuesta honesta
  "revisalo a mano antes de pedir" y el pedido es manual.
- **Receptores DEBUG exportados** (`DEBUG_VOICE_TEXT`, `DEBUG_DUMP_VISIBLE_NODES`,
  `DEBUG_AGENT_MISSION`, `DEBUG_IG_ENDCALL`): `RECEIVER_EXPORTED` pero **solo se
  registran en builds DEBUG** (`if (!BuildConfig.DEBUG) return`). En release NO
  existen. Riesgo acotado a builds de QA. Recomendación: confirmar que el release
  de Play Store es non-debug.
- **`canSend` no bloquea CALL/AUDIO visibles**: es CORRECTO — los chats de WA/IG
  siempre muestran botones de llamada/video; bloquear por eso rompería todo envío
  legítimo. El comentario de clase que menciona "audio/llamada a la vista" es
  aspiracional; la protección real y suficiente es PAYMENT/CARD + título de chat.

## 7. Recomendaciones
1. Mantener el pedido de Uber **manual** hasta una validación humana clara (OCR de
   tarifa pendiente).
2. Verificar que el build de producción **no** incluye los receptores DEBUG.
3. Conservar `canSend` como la única compuerta de envío (single source of truth) y
   no agregar caminos de envío que la salteen.
4. Si se habilita envío real, exigir además un **segundo factor hablado** (repetir
   destinatario + primeras palabras) antes del tap.

## Estado
Sin rutas inseguras evidentes nuevas. Las compuertas puras (`MessagingTaskPlanner`,
`WhatsAppVoiceSendPhrases`, `UberScreenReasoner`, `ActiveAppResolver`) están
testeadas. Nada se ejecutó contra apps reales en esta auditoría.
