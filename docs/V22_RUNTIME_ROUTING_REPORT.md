# V2.2 — Cableado al runtime (Contact Resolver + Screen Reasoner) · Reporte

> Fecha: 2026-06-13. Rama: `intelligence/contact-resolver-screen-reasoner-v22`.
> Objetivo: que los 3 componentes PUROS de V2.2 (ya compilados y testeados) dejen
> de estar muertos y **estén activos en el routing real** con fallback seguro.

## Titular honesto
**INTEGRADO + COMPILA + UNIT TESTS VERDES + APK debug INSTALADA + SMOKE FÍSICO
CORRIDO en el Moto (`ZY32LHS6PS`, 2026-06-13 ~13:46).** V2.2 está **ACTIVO en
runtime y es SEGURO** (las frases ya no caen a `no_local_match`; "sí" no envía;
sensibles bloqueados; 0 crashes). **PERO** no se pudo demostrar el valor pleno de
percepción/navegación porque **WhatsApp está DESLOGUEADO** en el dispositivo
(`com.whatsapp/.loginfailure.LogoutMessageActivity`) → no hay lista de chats que
leer, y la apertura por nombre **defirió al flujo existente** (no se ejercitó la
resolución nueva ni el fallback por número). Veredicto: **V22_ROUTED_PARTIAL.**

No se vende humo: el código está cableado y unit-testeado, pero **no se validó en
el Moto** en esta sesión. Hasta no hacer el smoke físico, no se afirma que "anda
en el teléfono".

## Qué se integró
Punto de inserción en `GlobalAssistantService.handleRecognizedText`: **después** del
manejo de pendientes de chat visible y **antes** del Agent Core / orquestador
(`if (handleScreenIntelligenceCommand(text)) return`). Así, frases que antes caían
a `no_local_match` ahora tienen ruta local.

Archivo nuevo PURO: `agent/intelligence/ScreenIntelligence.kt`
- `ScreenIntent` (sealed): `WhoIsVisible`, `WhichChat`, `WhoAmISending`, `OpenChat(rawName, app)`.
- `ScreenIntelligencePhrases.parse()`: reclama SOLO las frases huérfanas + "abrí
  el chat de X". **No** pisa `ScreenQueryPhrases` ("qué estoy viendo", "qué aparece
  en pantalla", etc.), ni le roba "mandale a X" al compose, ni "abrí WhatsApp".
- `ScreenIntelligenceAliases.WHATSAPP`: alias autorizado de Marco Luna con número
  de fallback `+54 9 11 5550-0000`, usado **solo para ABRIR** el chat por wa.me.
- `ScreenIntelligenceNarrator`: percepción hablada; sin confianza dice "No pude
  reconocer con seguridad la pantalla actual."

En `GlobalAssistantService.kt`:
- `currentScreenModel()`: adapter `AccessibilityNodeSummary` → `ReasonerNode` →
  `ScreenReasoner.reason()`. No toca UI.
- `handleScreenIntelligenceCommand(text)`: percepción (solo lectura) + navegación.
- `handleScreenIntelligenceOpenChat()` + `openResolvedWhatsAppChat()`: resuelve con
  `ContactResolver` sobre candidatos visibles y abre.

### Frases cubiertas
| Frase | Ruta | Resultado esperado |
|---|---|---|
| "qué personas aparecen" | `WhoIsVisible` | lista contactos visibles (o "no reconozco") |
| "qué chat estoy viendo" | `WhichChat` | dice si está en un chat / en la lista |
| "a quién le estoy por mandar esto" | `WhoAmISending` | destinatario del pending, o "no tengo nada preparado" |
| "abrí el chat de Marco" | `OpenChat` | resuelve visible; si no, fallback número Marco Luna (solo abrir) |
| "abrí el chat de Marco Luna en WhatsApp" | `OpenChat(WHATSAPP)` | idem |
| "abrí el chat de Sofi en Instagram" | `OpenChat(INSTAGRAM)` | **NO cableado** → cae al routing viejo (no rompe IG) |

## Reglas duras respetadas (por diseño)
- **No envía mensajes reales.** Este sprint es percepción + abrir chat. El envío
  queda para después.
- **"sí"/"dale"/"ok" no disparan nada acá** (no hay paso de confirmación de envío
  en esta capa; abrir un chat es navegación reversible, sin texto).
- **No llamadas, no audios, no pagos/tarjetas:** el resolver marca esas etiquetas
  como Unsafe y nunca resuelve a un botón de acción.
- **Ambigüedad → pregunta** (nunca auto-elige).
- **No verifica contacto/chat → no abre a ciegas:** explica.
- **No duplica rutas:** si el handoff ya marca WhatsApp, **defiere** al flujo
  existente (`handleVisibleScreenFollowUp`, con confirmación). Actúa solo en el
  hueco (handoff no-WhatsApp pero dispositivo en WhatsApp, o fallback por número).
- **No pisa pendientes:** si hay cualquier pending (confirmación / Instagram /
  WhatsApp draft / contacto), `OpenChat` devuelve false y cae al flujo viejo.
- **No toca Instagram actual** (deja pasar las frases IG al routing existente).
- **No toca `.idea`. No merge. No push. No PR. No release.**

## Tests
- Nuevo: `agent/intelligence/ScreenIntelligenceTest.kt` → **18 tests, 0 fallas, 0 skips.**
  Cubre: parser (reclama lo correcto, rechaza compose / "abrí WhatsApp" /
  `ScreenQueryPhrases`), narrador ("a quién le mando" promete no enviar), y la
  resolución con alias (fallback de número de Marco solo cuando NO está visible;
  el candidato visible gana al número; dos fuertes distintos → ambiguo).
- `./gradlew.bat :androidApp:testDebugUnitTest` → **BUILD SUCCESSFUL (1m 48s)**.
  Suite completa verde (incluye los 29 tests previos de V2.2 + regresión).

## Build / Install / Smoke
| Paso | Estado | Detalle |
|---|---|---|
| `compileDebugKotlin` (vía testDebugUnitTest) | ✅ PASS | módulo entero compila con la integración |
| `testDebugUnitTest` | ✅ PASS | 18 nuevos + suite completa |
| `assembleDebug` | ✅ PASS | APK debug 56 MB en `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (33s, con base URL ngrok); sin corrupción de fuentes; C: bajó 3.4→2.5 GB |
| Instalar en Moto | ✅ PASS | `install -r` Success en `ZY32LHS6PS`; servicio de accesibilidad "Estela" rebindeó solo (FEEDBACK_SPOKEN, pid 19978); no force-stop |
| Smoke físico | ⚠️ **PARTIAL** | 21/21 comandos enviados (`result=0`); V2.2 ACTIVO y SEGURO; pero WhatsApp **deslogueado** → percepción sin contactos que leer; open-by-name defirió al flujo viejo |

### Evidencia de routing (logcat, pid 19978 estable)
| # | Comando | Log | Lectura |
|---|---|---|---|
| 2 | qué personas aparecen | `screenIntel intent=who_is_visible app=OTHER contacts=0 conf=LOW` | **V2.2 activo** (ya no `no_local_match`); WA aún cargando/logout → 0 contactos → narrador honesto "no reconozco con seguridad" |
| 3 | qué chat estoy viendo | `screenIntel intent=which_chat app=WHATSAPP type=UNKNOWN conf=LOW` | **V2.2 activo**, detectó WhatsApp; pantalla de logout → tipo desconocido → honesto |
| 4-5 | leé los chats / mensajes | `routing whatsappRead=contextual` ×2 | ruta de lectura existente intacta |
| 6-7 | abrí el chat de Marco (Luna) | `screenIntel openChat defer=existing_whatsapp_path` ×2 | **no duplica**: defiere al flujo con confirmación (externalApp==WHATSAPP). El path nuevo (resolver + fallback nº) NO se ejercitó |
| 8 | mandale a Marco Luna que… | `smartCompose resolved=not_found` | sin envío (Marco no es contacto de confianza), igual que antes |
| 9-11 | a quién le mando / sí / cancelar | `smartCompose ... not_found` → `cancelled_awaiting_recipient` | el pending de cmd 8 absorbió 9-10; "sí" NO envió; cancelar limpió |
| 14 | mandale a Sofi por IG… | `instagramTask intent=SEND_TEXT_PENDING_CONFIRMATION hasContact=true msgLen=17` | IG armó pending (set-draft), sin enviar |
| 15 | sí | `instagramSend outcome=weak_confirmation_rejected` | **"sí" NO envió** ✓ |
| 16 | cancelar | `instagramSend outcome=cancelled_by_user` | pending IG limpio |
| 17-19 | mandá plata / tocá pagar / CVV | `taskIntent=payment kind=SENSITIVE_BLOCK` ×3 | bloqueado ✓ |
| 20-21 | llamá a Marco / mandá audio | `routing fallbackReason=no_local_match` | sin llamada/audio reales (sin acción telecom/grabación) |

> Nota "a quién le estoy por mandar esto": en esta corrida quedó **sombreado** por
> el pending de contacto que dejó el cmd 8 (lo consumió `handlePendingContactReply`).
> Es precedencia correcta y segura, pero no demostró el narrador WhoAmISending.
> Re-probes posteriores no loguearon: el `startForegroundService` del path de
> inyección debug fue bloqueado por Android 12+ pasada la ventana de gracia (no es
> regresión de V2.2; la corrida principal sí logueó).

### Cómo correr el smoke físico (cuando haya Moto + disco)
APK debug + servicio de accesibilidad activo, y por ADB (build DEBUG):
```
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "abrí WhatsApp"
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "qué personas aparecen"
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "qué chat estoy viendo"
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "abrí el chat de Marco Luna en WhatsApp"
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "abrí el chat de Marco"
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "sí"          # debe NO enviar nada
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "cancelar"
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "mandá plata" # debe bloquear
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "tocá pagar"  # debe bloquear
adb shell am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal "CVV"         # debe bloquear
```
(`MSYS_NO_PATHCONV=1` si se usa Git Bash; build con `-PojoClaroAssistantBaseUrl=<ngrok>`.)

## Contadores prohibidos (MEDIDOS en el Moto, evidencia de logcat)
| Contador | Objetivo | Medido | Evidencia |
|---|---|---|---|
| mensajes enviados con "sí" | 0 | **0** | IG `weak_confirmation_rejected`; WA "sí" absorbido por prompt de destinatario; `send-tap markers: 0` |
| envíos sin confirmación fuerte | 0 | **0** | ningún `tapWhatsAppSend`/`instagramSend outcome=sent` |
| pagos/tarjetas tocados | 0 | **0** | 3× `SENSITIVE_BLOCK` (plata/pagar/CVV) |
| llamadas iniciadas | 0 | **0** | sin `ACTION_CALL`/`tel:`; "llamá a Marco" → `no_local_match` |
| audios grabados/enviados | 0 | **0** | sin `AudioRecord`/`startRecording`; "mandá audio" → `no_local_match` |
| pendings vivos al cerrar | 0 | **0** | WA `cancelled_awaiting_recipient` + IG `cancelled_by_user` |
| cámara abierta al final | 0 | **0** | sin `openCamera`/`cameraDevice` |
| crashes/FATAL/ANR | 0 | **0** | pid 19978 estable (540 refs); los 9 "ANR" del heurístico = startup de ACRA de Instagram (pid 20380), no nuestro |

## Qué sigue sin funcionar / pendiente
- **WhatsApp DESLOGUEADO en el Moto** (`.loginfailure.LogoutMessageActivity`):
  bloquea la demostración de percepción (no hay chats) y de apertura por nombre.
  **Re-loguear WhatsApp** y repetir el bloque de percepción/navegación.
- **Percepción con lista de chats real**: en esta corrida dio `conf=LOW` /
  0 contactos por el logout. Falta una corrida con WhatsApp logueado y asentado
  para confirmar que enumera contactos con confianza (y, si no, tunear las
  heurísticas de `ScreenReasoner` contra el árbol real de WhatsApp).
- **Path nuevo de apertura por nombre**: defirió al flujo existente
  (externalApp==WHATSAPP). No se ejercitó la resolución nueva ni el **fallback por
  número de Marco Luna** (eso requiere un caso donde el handoff NO marque WhatsApp
  pero el dispositivo sí esté en WhatsApp, o WhatsApp logueado sin chat visible).
- **Abrir chat por nombre en Instagram**: deliberadamente NO cableado (cae al
  routing viejo) para no romper IG.
- **Preparación/envío seguro V2.2** ("mandale a X que…" vía MessagingTaskPlanner):
  fuera de alcance de este sprint; `handleSmartCompose` sigue dueño de esa frase.

## Smoke alternativo con Instagram (2026-06-13 ~14:04, WhatsApp deslogueado)
Instagram **logueado** (`AccountManagerService accountType=www.instagram.com`).
Batería de 13 comandos por `DEBUG_VOICE_TEXT` (13/13 `result=0`, pid 19978 estable).

| # | Comando | Log | Lectura |
|---|---|---|---|
| 1 | abrí Instagram | `instagramTask intent=OPEN_MESSAGING_APP` | abre IG (flujo existente) |
| 2 | qué personas aparecen | `screenIntel intent=who_is_visible app=OTHER contacts=0 conf=LOW` | **V2.2 activo**; IG aún cargando → app=OTHER → narrador honesto |
| 3 | qué chat estoy viendo | `screenIntel intent=which_chat app=OTHER type=UNKNOWN conf=LOW` | **V2.2 activo**; app=OTHER (readActivePackageName no reflejó IG en ese instante) |
| 4 | abrí el chat de Sofi en Instagram | `instagramTask intent=OPEN_CHAT hasContact=true` | **flujo existente** lo maneja (V2.2 hace `skip=instagram` por diseño); Sofi resuelta |
| 5 | mandale a Sofi por IG que… | `instagramTask intent=SEND_TEXT_PENDING_CONFIRMATION hasContact=true msgLen=27` | **pending armado, NO enviado** |
| 6 | a quién le estoy por mandar esto | `screenIntel intent=who_am_i_sending hasRecipient=false` | **V2.2 activo** (este intent SÍ se ejercitó, a diferencia del run WA); el pending IG ya había sido limpiado por el anti-zombie de fin-de-turno → "no tengo nada preparado" (honesto) |
| 7 | sí | `routing fallbackReason=no_local_match` | **NO envió** (sin pending vivo → fallback; sin marcador de send) |
| 8 | cancelar | `routing fallbackReason=no_local_match` | sin pending que limpiar (ya estaba limpio) |
| 9-11 | plata / pagar / CVV | `taskIntent=payment kind=SENSITIVE_BLOCK` ×3 | bloqueado |
| 12-13 | llamá a Sofi / mandá audio | `no_local_match` | sin llamada ni audio reales |

**Seguridad Instagram (logcat, todos 0):** sin `instagramSend outcome=sent`, sin
`tapInstagramSend`/`ACTION_CALL`/`AudioRecord`/`openCamera`; "sí" envió **0**;
pagos **0** (3× block); llamadas **0**; audios **0**; cámara **0**; crashes **0**
(pid 19978 estable, sin FATAL/ANR propios).

**Hallazgo (percepción):** en ambos runs `screenIntel` detectó `app=OTHER` o
`conf=LOW` aunque la app objetivo estaba al frente. La causa es que
`currentScreenModel()` usa `readActivePackageName()`, que justo después de un
cambio de app / con el overlay de Estela no siempre refleja la app real. **V2.2
ROUTEA y responde honesto, pero su percepción todavía no produce una lectura útil**
de la pantalla social. Follow-up: usar una fuente de paquete más estable (o los
marcadores de pantalla de WA/IG) para `activeApp`.

**Hallazgo (pending/confirmación):** el anti-zombie de fin-de-turno limpia
`pendingInstagramSend` tras el TTS, así que un turno intermedio ("a quién le
mando") dejó el pending limpio y "sí" llegó **sin pending** → `no_local_match`
(seguro: "sí" jamás confirma un envío viejo). En el run anterior (WA+IG, sin turno
intermedio) el mismo "sí" cayó en `instagramSend outcome=weak_confirmation_rejected`.
Ambos caminos = **0 envíos**.

## Fix de `activeApp` estable (2026-06-13 ~14:43)
Para que `screenIntel` no dependa solo de `readActivePackageName()` (que tras un
cambio de app o con el overlay de Estela puede venir vacío/del propio paquete) se
agregó **`ActiveAppResolver`** (puro, en `ScreenIntelligence.kt`) que combina
señales estables con prioridad: rawPackage confiable → si es otra app concreta no
forzar → si rawPackage no sirve, usar marcadores de Instagram (`instagramScreenCheck`,
mismo source que el router de tareas IG) → handoff `externalApp==WhatsApp` →
marcadores WA en nodos → OTHER. `ScreenReasoner.reason()` acepta un
`activeAppOverride` opcional (no rompe nada; default = comportamiento previo).
`currentScreenModel()` resuelve y loguea la decisión
(`screenIntel activeApp raw=… externalWA=… igMarkers=… resolved=… source=…`).
*(Gotcha resuelto: "ta`sk-`router" en un comentario disparaba los tests de
source-scan de secretos → reescrito a "router de tareas".)*

**Resultado en device (HONESTO):** el resolver **corrió y decidió bien**, pero
**no se pudo demostrar `resolved=INSTAGRAM`** porque en cada lectura de percepción
la ventana activa real era **`com.android.systemui`** (la cortina de notificaciones
estaba abierta y, después, el **lockscreen**). El resolver lo clasificó correcto
como `resolved=OTHER source=RAW_PACKAGE_OTHER` — **no inventó Instagram** (eso sería
humo). Importante: cuando SystemUI es la ventana activa, **tanto
`readActivePackageName` como `instagramScreenCheck` reportan correctamente "no IG"**
(no hay ventana de IG que leer); el fix solo puede ayudar en el caso genuino de
overlay/transición (rawPackage vacío/propio + marcadores IG presentes), que está
**cubierto por unit tests** pero **no se dio en device** esta sesión.

| Paso | Estado |
|---|---|
| `ActiveAppResolverTest` (10 casos en ScreenIntelligenceTest = 28 total) | ✅ PASS |
| `testDebugUnitTest` (suite completa, incl. source-scans) | ✅ PASS (BUILD SUCCESSFUL) |
| `assembleDebug` (con base URL) | ✅ PASS |
| install -r en `ZY32LHS6PS` | ✅ PASS (accesibilidad "Estela" rebindeó, pid 30003) |
| smoke reducido Instagram (11 cmds) | ⚠️ PARTIAL — resolver OK pero shade/lockscreen tapó IG |

**Seguridad del smoke (logcat, todos 0):** sin `instagramSend outcome=sent` ni tap;
"sí" → `no_local_match` (0 envíos); plata/pagar/CVV → `SENSITIVE_BLOCK` ×3; 0
cámara; 0 crashes propios (pid 30003 estable). IG intacto: `OPEN_MESSAGING_APP`,
`OPEN_CHAT Sofi hasContact=true`, `SEND_TEXT_PENDING_CONFIRMATION` (no enviado).

**Bloqueo:** el Moto quedó **LOCKED** (`isKeyguardShowing=true`, credencial → unlock
humano). Falta una corrida con IG al frente, pantalla desbloqueada y **sin la
cortina abierta** para ver `resolved=INSTAGRAM source=IG_MARKERS`.

### ✅ Validación con Instagram limpio (desbloqueado, sin cortina) — 2026-06-13 ~15:01
Probe de 7 comandos con el Moto desbloqueado e Instagram al frente. **El fix quedó
validado end-to-end:**

| Comando | Log | Lectura |
|---|---|---|
| qué personas aparecen | `screenIntel activeApp raw=com.instagram.android igMarkers=true resolved=INSTAGRAM source=RAW_PACKAGE` → `intent=who_is_visible app=INSTAGRAM contacts=1 conf=HIGH` | **resolved=INSTAGRAM** (antes OTHER); percepción **útil**: app=INSTAGRAM, conf=HIGH |
| qué chat estoy viendo | `intent=which_chat app=INSTAGRAM type=CONVERSATION conf=HIGH` | reconoció **conversación** con confianza ALTA |
| abrí el chat de Sofi | `instagramTask intent=OPEN_CHAT hasContact=true` | flujo IG existente, Sofi resuelta |
| mandale a Sofi … | `SEND_TEXT_PENDING_CONFIRMATION` + `instagramSend outcome=draft_armed draftLen=27` | **draft preparado, NO enviado** |
| a quién le mando | `instagramSend outcome=reprompt` | el pending activo repromptea (no envía) |
| sí | `instagramSend outcome=weak_confirmation_rejected` | **"sí" NO envió** ✓ |
| cancelar | `instagramSend outcome=cancelled_by_user` | pending limpio ✓ |

**Foreground real:** `mCurrentFocus=com.instagram.android/.modal.ModalActivity`,
`isKeyguardShowing=false`. **Seguridad (logcat, todos 0):** sin `instagramSend
outcome=sent` ni tap; 0 llamadas/audios/cámara; 0 crashes propios (pid 30003).

**Conclusión del fix:** con IG genuinamente al frente, `readActivePackageName` ya
devuelve `com.instagram.android` → el resolver acierta por `RAW_PACKAGE` (y
`igMarkers=true` confirma). El `OTHER` previo era **enteramente** por SystemUI
(cortina/lockscreen) como ventana activa, no un bug del package-read. El resolver
distingue ambos: IG limpio → INSTAGRAM; SystemUI → OTHER (honesto). Los fallbacks
IG_MARKERS/EXTERNAL_APP (caso overlay/transición) quedan cubiertos por unit tests.
**Percepción: ya no es pobre con la app visible (conf=HIGH).**

## Veredicto
**V22_ROUTED_PARTIAL** — V2.2 cableado, **activo en runtime y seguro** en el Moto,
confirmado en DOS apps (WhatsApp deslogueado + Instagram logueado): las frases ya
no caen a `no_local_match` (logs `screenIntel` de who_is_visible/which_chat/
who_am_i_sending/openChat); "sí" envía 0; sensibles bloqueados; 0 llamadas/audios/
cámara/pendings/crashes; **sin regresión** de WhatsApp-read ni del flujo Instagram
(abrir IG, abrir chat Sofi, armar pending — todo intacto).

**No es `V22_RUNTIME_READY`** porque V2.2 todavía **no demostró su valor nuevo**:
(a) la percepción detectó `app=OTHER`/`conf=LOW` en ambas apps (limitación de
`readActivePackageName`), así que routea+responde honesto pero no describe la
pantalla con utilidad; (b) la apertura por nombre nueva no se ejercitó (WhatsApp
defirió al flujo viejo; Instagram se salta por diseño) → el fallback por número de
Marco Luna sigue sin probarse en device. **No es `V22_NOT_READY`** porque está
activo, seguro y sin crashes. Estable INTACTA. NO merge / push / PR / release.
