# WhatsApp Blind-First Automation

Estado: **implementado (local, sin push)** · Rama `chore/unattended-hardening-sprint`
Alcance del sprint: **navegación por voz, dry-run, cero envíos**. Documento sanitizado
(sin números completos, sin contenido de chats, sin logs crudos, sin secretos).

## 1. Problema

Durante el QA, el usuario entraba **manualmente** al chat correcto y recién ahí
probaba a Estela. Eso sirve para pruebas, pero **no es un producto** para personas
no videntes: una persona ciega no puede "tocar el chat correcto" en una lista.

Objetivo: que Estela lleve a la persona **al chat correcto por voz**, con rutas
confiables (contacto, deep link, notificación, chat ya abierto), y que **explique
por voz** cuando algo no se puede hacer de forma segura, ofreciendo una
alternativa. Nunca depender de un toque visual como flujo final.

## 2. Arquitectura implementada

Tres módulos **puros** (sin Android, sin estado, sin IO, unit-testeados) + un
cableado fino en `GlobalAssistantService` (GAS).

| Módulo | Rol |
|---|---|
| `agent/runtime/whatsapp/WhatsAppBlindRoute.kt` | Parser de intención: `OpenContactChat(query)` y `ReplyToLastNotification`. Solo clasifica; no abre ni resuelve. |
| `agent/runtime/whatsapp/WhatsAppDestination.kt` | Value object de destino: `source` (CURRENT_CHAT/CONTACT/NOTIFICATION/DEEP_LINK), `confidence`, `redactedLabel`, `phoneEnding` (últimos 4). `canPrepareDraft` solo con confianza ALTA. Nunca guarda el número completo. |
| `agent/runtime/whatsapp/WhatsAppBlindRouteNarrator.kt` | Respuestas de voz de recuperación: cortas, tranquilas, accionables, sin PII. |

Cableado en GAS (un solo punto de entrada en el dispatch):

```
… pendientes / stop / fluency / anxiety …
handleWhatsAppBlindFirstCommand(text)      ← NUEVO (antes de todo lo de abajo)
handleWhatsAppFirstControlCommand(text)    (abrir WhatsApp genérico / diagnóstico)
handleWhatsAppOpenChatOrdinalCommand(text) (ordinal — best-effort, ver §4)
handleWhatsAppNotificationQueryCommand(text) (lectura de notificaciones)
handleWhatsAppVoiceSendCommand / handleWhatsAppReplyCommand / handleSmartCompose
… orchestrator/LLM (fallback) …
```

La ruta ciega corre **antes** de abrir-genérico, ordinal, reply, compose y del
LLM: así "abrí WhatsApp con Juan" deja de abrir WhatsApp a secas, y "respondé el
último" deja de tipear "el último" como borrador en el chat abierto.

## 3. Rutas confiables (blind-first)

### a) Abrir chat por contacto (voz → memoria → deep link)
Frases: "abrí WhatsApp con Juan", "abrime WhatsApp para Juan", "escribile a Juan",
"mandale un WhatsApp a Juan", "respondé a Juan".

Flujo:
1. `WhatsAppBlindRoute` reconoce la intención (sin el sustantivo "chat" — ese caso
   lo cubre ScreenIntelligence — y sin mensaje "… que …" — ese caso es compose).
2. Se bloquea cualquier destinatario que mencione datos sensibles.
3. Se resuelve el contacto en **memoria local** (`MemoryContactResolver`:
   contactos de confianza + número dictado). Resultados:
   - **Resuelto** → se construye un `WhatsAppDestination(CONTACT, HIGH, …)` y se
     abre el chat por **deep link `wa.me/<dígitos>` SIN `?text=`** (no escribe).
   - **Varios** → se piden por nombre exacto; no se abre nada.
   - **No encontrado** → no se abre; se ofrece número/otro nombre/cancelar.
4. **Verificación diferida**: tras ~1,2 s se lee la pantalla (`WhatsAppScreenDetector`)
   y se confirma `inChat`. Si entró: "Abrí el chat de X. No escribí nada." Si no:
   "Abrí WhatsApp, pero no pude confirmar que entró al chat de X. No escribí nada."

### b) Responder la última notificación (WA-3 → contacto → deep link)
Frases: "respondé el último WhatsApp", "contestá el último mensaje", "respondé a
quien me escribió".

Flujo:
1. Se lee la última notificación de WhatsApp del `WhatsAppNotificationStore`
   (ring buffer en memoria del listener WA-3). **No red, no LLM.**
2. Store vacío → "No tengo mensajes nuevos de WhatsApp registrados."
3. Remitente oculto por Android → se dice y se ofrece abrir WhatsApp.
4. Si el remitente coincide con un **contacto de confianza** → se abre su chat por
   deep link (mismo camino que (a), con `source = NOTIFICATION`).
5. Si no es un contacto de confianza → **alternativa segura**: se nombra al
   remitente y se ofrece abrir WhatsApp para leer, o dar un contacto guardado.
   (No se dispara una respuesta a ciegas ni se fuerza un chat equivocado.)

### c) Chat ya abierto (current chat)
Si Estela ya verifica `inChat=true` (`.Conversation` + composer), la respuesta en
el chat abierto la maneja **WA-5** (`handleWhatsAppReplyCommand`), que escribe un
borrador y exige **doble confirmación en dry-run** (no envía).

### Confirmación de destino (seguridad)
`WhatsAppDestination` codifica el destino con la precisión justa:
`canPrepareDraft` solo es `true` con **confianza ALTA**. La confirmación hablada
incluye, como mucho, los **últimos 4 dígitos**; nunca el número completo.

## 4. Rutas NO confiables (documentadas)

- **Taps sintéticos por ordinal** ("abrí el primer chat"): WhatsApp bloquea el tap
  de accesibilidad sobre el row de la lista, así que esta ruta es **best-effort**
  y no se usa como ruta principal blind-first. La ruta principal es deep link por
  contacto/notificación.
- **Disparar el `PendingIntent` de la notificación** (abriría el chat exacto del
  remitente aunque no esté en contactos) sería la ruta más confiable para (b),
  pero hoy el modelo de notificación es **puro** (sin tipos de Android) a
  propósito. Queda como mejora futura (ver §7).

## 5. Seguridad

- **Cero envíos** en este sprint: las rutas ciegas **solo abren/verifican**. No
  preparan borrador por la vía de envío real, no tocan "enviar".
- `tapWhatsAppSend(...)` sigue teniendo **un único call-site** (contrato verificado
  por test); la ruta ciega no lo invoca y no arma `pendingWhatsAppSendDraft`.
- Apertura por `wa.me/<dígitos>` **sin `?text=`**: el chat abre con el campo vacío.
- **Privacidad**: el número completo nunca se loguea ni se habla (solo últimos 4 a
  pedido del flujo); los logs usan `redactedForLog()` / longitudes; el contenido
  de chats no se loguea ni se manda a backend/LLM.
- Contenido sensible (credenciales, etc.) bloqueado **antes** de resolver contacto.
- Si Android/WhatsApp bloquea una acción, Estela **lo explica por voz** y ofrece
  alternativa segura (regla de producto del sprint).

## 6. Pruebas

Unit + contract (todas verdes; `:androidApp:testDebugUnitTest` = 2811 tests, 0 fallas;
`:androidApp:assembleDebug` PASS):

- `WhatsAppBlindRouteTest` — reconoce abrir-por-contacto y responder-última;
  NO roba compose-con-mensaje, "abrí el chat de X", navegación ("mandame a casa"),
  ni lecturas de notificación.
- `WhatsAppDestinationTest` — solo confianza ALTA habilita borrador; `phoneEnding`
  son 4 dígitos; el número completo nunca se retiene ni se habla; log redactado.
- `WhatsAppBlindRouteNarratorTest` — respuestas cortas, con tranquilidad
  ("No escribí nada"), sin corridas de 4+ dígitos.
- `WhatsAppBlindFirstContractTest` — la ruta ciega corre antes de
  abrir-genérico/reply/compose/LLM; no toca enviar; abre sin `?text=`; lee el store
  WA-3; `tapWhatsAppSend` sigue con un solo call-site.

## 7. Limitaciones

- **Resolución de contactos** = solo memoria de confianza + número dictado
  (`MemoryContactResolver`). Un nombre no guardado da "no encontrado" (seguro) y
  pide número/otro nombre. No se lee la agenda del sistema (sin `READ_CONTACTS`).
- **Responder a una notificación** abre el chat solo si el remitente es un contacto
  de confianza; si no, ofrece alternativa (no abre a ciegas). Para abrir el chat
  exacto de cualquier remitente haría falta el `PendingIntent` (futuro).
- **Verificación de `inChat`** tras deep link es temporizada (~1,2 s); en equipos
  lentos puede reportar "no pude confirmar" aun habiendo entrado (mensaje honesto,
  sin falso positivo).
- **Envío real**: deshabilitado a propósito en este sprint (dry-run / cancel).

## 8. Próximos pasos

1. **Fixture de contacto de QA** (contacto de confianza con número, o número
   dictado) para smoke físico end-to-end sin tocar chats de terceros.
2. **`PendingIntent` de notificación** como ruta de apertura más confiable para
   responder-a-notificación (requiere extender el modelo del listener sin filtrar
   PII; abrir es navegación reversible, no envío).
3. **Borrador dry-run blind-first**: tras abrir por contacto y verificar `inChat`,
   permitir dictar el mensaje y prepararlo por la vía **WA-5 dry-run** (sin tocar
   enviar), con confirmación de destino por `WhatsAppDestination`.
4. **Envío real controlado** (fase futura, deliberada): destino + mensaje +
   confirmación fuerte, reutilizando el único call-site de envío.

---
No incluye: número completo, contenido de chats, logs crudos, datos privados,
tokens, `.env`, keystores ni APKs.
