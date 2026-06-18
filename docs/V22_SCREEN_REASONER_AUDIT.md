# V2.2 — Screen Reasoner + Contact Resolver · Auditoría y Arquitectura

> Método: audit de lectura en paralelo del snapshot/matchers/detectores + lo aprendido en V2.1 (WhatsApp real). Honesto: el código V2.2 **no se pudo compilar/testear** (C: ~0.35 GB).

## Cómo Estela VE la pantalla hoy
- `OjoClaroAccessibilityService.readVisibleNodeSummaries()` → `List<AccessibilityNodeSummary>`:
  `text, contentDescription, hint, className, isClickable, isEditable, isCheckable, isChecked, isPassword, isHeading, isEnabled`. **Nunca expone el valor de un password.**
- Para el Agent Core hay capas sanitizadas: `ScreenSnapshot` (`packageName, text, elements: List<ScreenElement{label, role, isInteractive, isPassword}>`) y `StructuredScreenSnapshot` (botones, campos editables, `focusedLabel`, `signals` con `hasPaymentOrTransferSignals/isMessagingApp/...`, `warnings`, `isLimited`).
- Roles de elemento: `HEADING, BUTTON, TEXT, EDIT_TEXT, LINK, IMAGE, CHECKBOX, LIST_ITEM, UNKNOWN`.

## Cómo detecta la app activa
- Por **package name**. `ExternalAppName` enum = `WHATSAPP, MAPS, PHONE, UNKNOWN` (**Instagram NO está en el enum**: se detecta por `package == com.instagram.android` en código aparte). Gap: no hay un `ActiveApp` unificado que incluya Instagram y WhatsApp Business.

## Cómo detecta chats/contactos
- WhatsApp: `WhatsAppVisibleChatMatcher` (score exacto=100 / contains=94 / reverse-contains+len≥5=84 / token-initials=78 / token-overlap) + `WhatsAppScreenDetector` (chat-list vs conversation por campo de mensaje / botón enviar / etc.).
- Instagram: `InstagramNameMatcher` (matchea handles dictados, ignora @/espacios/guiones) + `instagramScreenCheck` (THREAD/INBOX/FEED por ids 433.x).
- Resolución por nombre para **compose**: `smartComposeResolver` usa `LocalMemoryStore` (contactos de confianza).

## Cómo abre WhatsApp / Instagram
- WhatsApp: `WhatsAppIntentHelper.openWhatsApp()` (launch intent) y `openChat(name, phoneE164)` (deep link `wa.me/<digits>`, **sin** `?text=`).
- Instagram: abre la app + navega el inbox por `InstagramNameMatcher`.

## Por qué Instagram funciona MEJOR que WhatsApp (por nombre)
- Instagram tiene **set-draft** (Estela escribe el mensaje en el campo) + matcher de inbox robusto (`InstagramNameMatcher`) + ids 433.x capturados. El flujo abrir→chat→texto→confirmación fuerte está cerrado (V1.12).
- WhatsApp **NO tiene set-draft** (el mensaje debe estar ya tipeado), y la resolución por nombre va por `smartComposeResolver`/contactos de confianza → **"Marco Luna" da `resolved=not_found`** (no está en memoria). Abrir-por-nombre routea al orquestador y termina en un follow-up, **no abre el chat** (verificado físico en V2.1).

## Por qué falla WhatsApp por nombre (raíz)
- La resolución NO usa la **lista de chats VISIBLE** (que sí se puede leer) para encontrar a "Marco Luna"; depende de contactos de confianza en memoria. → Falta una capa que resuelva contra lo **visible en pantalla**.

## Riesgos de mandar al contacto equivocado
- Si la resolución es difusa y **auto-elige**, se podría mensajear a la persona equivocada. Mitigaciones de V2.2: `Ambiguous → preguntar`; **verificar el título del chat** contra el destino antes de enviar; compuerta `canSend` que frena si el chat no coincide o hay controles de pago/tarjeta a la vista.

## Arquitectura propuesta (V2.2 — implementada como componentes PUROS)
Tres capas puras y testeables (en `agent/intelligence/`), + un adapter + integración mínima:

1. **`ScreenReasoner`** — snapshot → `ScreenModel` (`activeApp, screenType{CHAT_LIST/CONVERSATION/SEARCH/...}, currentChatTitle, visibleContacts, hasMessageInput, hasSendButton, riskyControls{CALL/VIDEO/AUDIO/PAYMENT/CARD/ATTACH}, confidence, explanation`). Mira y EXPLICA con nivel de confianza.
2. **`ContactResolver`** — `(query, app, candidates, aliases)` → `Resolved / Ambiguous / NotFound / Unsafe`. Usa lo **visible** (no solo memoria). Score que espeja `WhatsAppVisibleChatMatcher` + prefijos ("marco"→"Marco Luna", "sofi"→"Sofia"). Número fallback SOLO para alias autorizado (Marco Luna → 0000005678). Jamás resuelve a un botón de acción.
3. **`MessagingTaskPlanner`** — `(goal, screenModel, resolution)` → `Steps / Ask / Refuse` + `classifyConfirmation` + compuerta `canSend`. Frenos: "sí" no envía; verificar chat == destino; pagos/tarjetas/audio/llamada → no enviar; ante la duda → preguntar.

### Adapter (a implementar en la integración)
- `AccessibilityNodeSummary` → `ReasonerNode` (campos isomorfos).
- `ScreenModel.visibleContacts` ya son `ContactCandidate` (mismo tipo que consume `ContactResolver`).
- alias autorizados: `LocalMemoryStore` + un alias fijo `Marco Luna → 0000005678 (WhatsApp)`.

### Integración mínima propuesta (DISEÑO; no aplicada por build bloqueado)
- Nuevas frases → nuevos handlers locales en GAS, **antes** de los handlers de mensajería existentes, con **fallback** a las rutas viejas si la confianza es baja:
  - "qué personas aparecen" / "qué chats ves" → `ScreenReasoner.reason(...)` → leer `visibleContacts`.
  - "qué chat estoy viendo" → `ScreenModel.currentChatTitle` + `explanation`.
  - "a quién le estoy por mandar esto" → describir el destino del pending actual.
  - "abrí el chat de X" → `ContactResolver.resolve` sobre `visibleContacts`; si `Resolved` → abrir (tap fila o deep link); si `Ambiguous` → preguntar; si `NotFound` → explicar.
  - "mandale a X por WhatsApp que Y" → `MessagingTaskPlanner.plan(...)` → ejecutar pasos seguros; **enviar solo** si `canSend(...)`.
- **No romper** las rutas de Instagram/WhatsApp existentes: el resolver/reasoner es una **capa adicional**; si no tiene confianza, cae al fallback viejo.

## Estado
Componentes PUROS + tests **escritos** (no corridos, build bloqueado). Integración GAS = **diseño**. Ver `V22_CONTACT_RESOLVER_REPORT.md`, `V22_SCREEN_REASONER_REPORT.md`, `V22_WHATSAPP_INSTAGRAM_REAL_SMOKE.md`.
