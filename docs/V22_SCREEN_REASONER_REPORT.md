# V2.2 — Screen Reasoner · Reporte

## Qué se implementó (código real, SIN COMPILAR)
- **`ScreenReasoner`** (`agent/intelligence/ScreenReasoner.kt`) — PURO. Convierte un snapshot (`RawScreen{packageName, nodes: List<ReasonerNode>}`) en un `ScreenModel`:
  - `activeApp` (WhatsApp / WhatsApp Business / Instagram / otra)
  - `screenType` (CHAT_LIST / CONVERSATION / SEARCH / COMPOSER / UNKNOWN)
  - `currentChatTitle` (best-effort por encabezado)
  - `visibleContacts` (filas clickeables con nombre → `ContactCandidate`, nunca botones de acción)
  - `visibleMessagesCount`, `hasMessageInput`, `hasSendButton`
  - `riskyControls` (CALL / VIDEO_CALL / AUDIO / PAYMENT / CARD / ATTACH)
  - `screenLocked`, `confidence` (HIGH/MEDIUM/LOW/UNKNOWN), `explanation` (frase hablable)
- `ReasonerNode` es **isomorfo** a `AccessibilityNodeSummary` (text, contentDescription, hint, className, isClickable, isEditable, isPassword, isHeading) → el adapter mapea 1:1.
- Filosofía: **mirar y EXPLICAR con confianza**. Si no reconoce la pantalla, lo dice (confidence LOW). Nunca decide acciones (solo describe).

## Tests (escritos, NO corridos)
- `ScreenReasonerTest` (7 casos): WhatsApp chat-list, WhatsApp conversation (+título +input +send), Instagram inbox, Instagram conversation, pantalla desconocida (LOW), controles riesgosos (videollamada/pago detectados), pantalla bloqueada (vacía + reportada).

## Limitaciones honestas
- `currentChatTitle` es heurístico (primer encabezado / primer texto prominente); puede fallar si la app no marca el título como heading. Por eso el envío exige verificación + `canSend` frena ante duda.
- Detección por `className`/labels: frágil a cambios de UI (igual que el resto del stack de accesibilidad).

## Tests corridos
- **Ninguno** (C: ~0.35 GB). Escritos para correr con disco.

## Veredicto
**`V22_EXPERIMENTAL`** — razonador construido y testeado (tests escritos), sin compilar/instalar.
