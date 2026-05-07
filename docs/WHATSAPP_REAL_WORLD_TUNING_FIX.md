# WhatsApp Real-World Tuning Fix

Fecha: 2026-05-06
Estado: implementado, build verde, **481 / 481 tests verdes**, APK regenerada.
Ámbito: tuning del modo guiado de WhatsApp para QA física real. No se agregaron features.

---

## 1. Resumen ejecutivo

En la prueba física real con voz humana, la versión anterior del WhatsApp Guided Mode todavía se sentía torpe. El usuario podía decir "abrí wp", pero al continuar con frases naturales como "Marco Antonio", "el de Marco", "buscá el chat de Marco" o "mensaje para Marco", la app respondía con un fallback largo "No entendí qué hacer en WhatsApp..." y mostraba "Esperando confirmación" en pantalla, cuando el sub-estado real era "esperando acción de WhatsApp". Además, decir "sí" sin pending dejaba el mensaje viejo "No pude conectar. Entendí: sí sí" en pantalla.

Esta ronda corrige todo eso sin agregar features nuevas, sin background listening, sin taps automáticos en WhatsApp y sin bajar seguridad. La APK quedó lista en disco para QA física apenas el Samsung vuelva a conectarse.

---

## 2. Problema físico observado

| Síntoma | Causa raíz | Severidad |
|---------|-----------|-----------|
| UI mostraba "Esperando confirmación" tras "abrí wp" | `AgentState.WAITING_WHATSAPP_ACTION.toAppState()` mapeaba a `AppState.WAITING_CONFIRMATION` y `statusText` no diferenciaba | Alta |
| "Marco Antonio", "con Marco", "el de Marco" caían en `WHATSAPP_GUIDED_RETRY` largo | `parseWhatsAppGuidedAction` solo detectaba "chat de X", "del chat de X", "con X" — el resto caía como UNKNOWN | Alta |
| "buscá el chat de Marco" desde el modo guiado caía en retry | El parser delegaba a `LocalIntentParser`, que sí lo entendía, pero el wrapping era frágil | Media |
| "mensaje para Marco" no abría el flujo de mensaje | Sin regex específico | Alta |
| "chat" alone no era confirmable después de elegir contacto | No existía estado de desambiguación chat/mensaje | Alta |
| "No entendí qué hacer en WhatsApp..." era largo (~25 palabras) | `WHATSAPP_GUIDED_RETRY` y `WHATSAPP_GUIDED_QUESTION` eran multi-frase | Alta |
| "No pude conectar. Entendí: sí sí" cuando el usuario decía "sí" sin pending | `localFallback` exponía detalle interno + nada cortocircuitaba ruidos antes del backend | Media |
| Sin debug visible para QA | No había panel de información para entender qué reconoció la app | Media |

---

## 3. Causa raíz

Tres causas estructurales:

1. **Mapeo de estados perdía granularidad**. Múltiples sub-estados conversacionales del agente colapsaban en `AppState.WAITING_CONFIRMATION` para que la UI los pinte como "esperando", pero `statusText` no veía el sub-estado original.
2. **Parser guiado no era contextual**. El re-parser interno usaba el mismo `LocalIntentParser` general. No había regex específicos para frases parciales ("contacto solo", "el de X", "mensaje para X") ni un estado de desambiguación chat/mensaje.
3. **Fallback de backend filtraba detalle interno**. `localFallback` decía "No pude conectar. Entendí: $text" y eso quedaba pegado en pantalla aunque el problema real fuera "el usuario dijo 'sí' sin pending".

---

## 4. Qué se cambió

### 4.1 Estado nuevo: `WAITING_WHATSAPP_CHAT_OR_MESSAGE`

Agregado a `AgentState`. Mapea a `AppState.WAITING_CONFIRMATION` (igual que los otros sub-estados conversacionales). La UI ahora distingue ambos vía un nuevo campo `agentState: AgentState?` en `HomeUiState`.

### 4.2 UI distingue sub-estados WhatsApp

`statusText(appState, agentState)` ahora devuelve:
- `"Esperando acción de WhatsApp"` para `WAITING_WHATSAPP_ACTION` y `WAITING_WHATSAPP_CHAT_OR_MESSAGE`.
- `"Esperando contacto"` para `WAITING_CONTACT`.
- `"Esperando mensaje"` para `WAITING_MESSAGE`.
- `"Esperando número"` / `"Esperando destino"` / `"Esperando nombre de lugar"` / etc. para los demás sub-estados.
- `"Esperando confirmación"` solo cuando hay confirmación real pendiente y no hay sub-estado conversacional vivo.

### 4.3 Parser contextual del modo guiado

`parseWhatsAppGuidedAction` ahora reconoce, en este orden:

1. Comandos completos detectados por `LocalIntentParser` (cualquier WhatsApp-related).
2. **Patrones de chat directo** (`OPEN_WHATSAPP_CHAT`):
   - "chat Marco" / "chat de Marco" / "el chat de Marco" / "el chat con Marco"
   - "del chat de Marco" / "el del chat de Marco"
   - "abrilo con Marco" / "abrilo a Marco"
   - "abrí el de Marco" / "abre el de Marco"
   - "buscá Marco" / "busca Marco" / "buscar Marco" / "buscame Marco" / "buscame a Marco" / "encontrá Marco"
   - "quiero hablar con Marco" (sin "por WhatsApp" porque ya estamos en contexto WhatsApp)
3. **Patrones de mensaje** (`COMPOSE_WHATSAPP_MESSAGE` con `MESSAGE_TEXT` faltante):
   - "mensaje para Marco" / "un mensaje para Marco" / "el mensaje para Marco"
   - "mandar un mensaje a Marco" / "mandarle un mensaje a Marco"
4. **Contacto ambiguo** (transición a `WAITING_WHATSAPP_CHAT_OR_MESSAGE`):
   - "Marco Antonio" / "Marco" (frase pelada)
   - "con Marco" / "con Marco Antonio"
   - "el de Marco" / "la de Marco Antonio"

Filtros conservadores para no tomar interjecciones como nombres:
- Lista negra de tokens de relleno: `uh`, `eh`, `ah`, `oh`, `mm`, `ja`, `ay`, `uy`, `ey`, `em`.
- Lista negra de palabras-comando: `si`, `no`, `dale`, `confirmar`, `confirmo`, `aceptar`, `cancelar`, `cancela`, `anular`, `callar`, `callate`, `silencio`, `ayuda`, `chat`, `mensaje`, `principal`, `solamente`, `solo`.
- Mínimo una palabra de 3+ letras para considerarlo "name-like".
- Máximo 4 palabras totales.

### 4.4 Estado de desambiguación

Cuando el usuario dice un contacto solo en `WAITING_WHATSAPP_ACTION`, se transiciona a `WAITING_WHATSAPP_CHAT_OR_MESSAGE` con frase corta:

> "¿Abrir chat con Marco Antonio o mandarle un mensaje?"

Desde ese estado:
- "chat" / "el chat" / "abrir chat" / "abrir el chat" / "abrilo" → `OPEN_WHATSAPP_CHAT` con el contacto guardado.
- "mensaje" / "un mensaje" / "el mensaje" / "mandale" / "mandale un mensaje" / "escribirle" → transiciona a `WAITING_MESSAGE` y pregunta "¿Qué mensaje querés mandarle a Marco Antonio?"
- "cancelar" / "cancela" / "no" / "anular" → limpia todo y vuelve a IDLE.
- "sí" / "si" / "dale" → NO confirma (mantiene la regla de seguridad).
- "confirmar" → se descarta porque el pending no es confirmable todavía (no hay acción real preparada). Frase: "No hay ninguna acción pendiente para confirmar."
- Cualquier otra cosa → re-prompt corto: "Decime: chat o mensaje."

### 4.5 Respuestas más cortas

| Antes | Después |
|------|---------|
| "¿Qué querés hacer en WhatsApp? Podés decir: abrir el chat de Sofi, o mandale a Sofi que estoy llegando." | "Decime: chat de Sofi, mensaje para Sofi, o cancelar." |
| "No entendí qué hacer en WhatsApp. Podés decir: abrir el chat de Sofi, mandale a Sofi que estoy llegando, o cancelar." | "No escuché bien. Probá de nuevo." |
| (sin estado) | "¿Abrir chat con Marco Antonio o mandarle un mensaje?" |
| (sin estado) | "Decime: chat o mensaje." |
| (sin estado) | "¿Qué mensaje querés mandarle a Marco Antonio?" |
| "No pude conectar. Entendí: sí sí. Revisá tu internet o probá en un momento." | "No entendí. Probá de nuevo." (y cortocircuito previo para "sí"/"dale"/"uh") |

### 4.6 Cortocircuito de ruidos

`HomeViewModel.submitVoiceText` ahora detecta `isAffirmativeNoise(text)` para "si", "sí", "si si", "sí sí", "dale", "dale dale", "ok", "okey", "okay", "uh", "eh", "mm", "mhm", "ajá", "aja". Si llega un ruido sin pending real, responde corto "No hay ninguna acción pendiente." y NO va al backend. Antes producía el "No pude conectar..." viejo.

### 4.7 Debug visible para QA

En builds debug (`BuildConfig.DEBUG`), aparece un panel chico al final del scroll:

```
Último: <texto reconocido>
Estado: <AgentState o AppState>
Intent: <AgentIntent>
```

Color gris claro, fontSize 12sp, contentDescription dedicado para TalkBack. No se promete como feature al usuario final — solo facilita la QA física.

### 4.8 UI más limpia

- `Callar` sigue fijo abajo (no se pisa con contenido).
- `state.spokenText` está dentro de un `verticalScroll` con bottom padding 136dp para que no quede tapado por `Callar`.
- El debug panel solo aparece en debug y no se superpone con el contenido principal.
- El botón DESCRIBIR sigue oculto (decisión previa).
- No se duplican acciones.

---

## 5. Cómo quedó WAITING_WHATSAPP_ACTION

Diagrama de transiciones nuevo:

```
[Usuario] "abrí wp" / "abrí WhatsApp" / "abrí wsp" / etc.
   |
   v
[Estado] WAITING_WHATSAPP_ACTION
   "Decime: chat de Sofi, mensaje para Sofi, o cancelar."
   |
   |---- "abrí WhatsApp principal" / "solo abrí WhatsApp" ----> handoff (abre WhatsApp)
   |---- "buscá el chat de Marco" / "abrí el chat de Marco" --> OPEN_WHATSAPP_CHAT(Marco)
   |---- "chat Marco" / "el chat de Marco" -------------------> OPEN_WHATSAPP_CHAT(Marco)
   |---- "mandale a Marco que..." ----------------------------> COMPOSE(Marco, msg)
   |---- "mensaje para Marco" --------------------------------> WAITING_MESSAGE(Marco)
   |---- "Marco Antonio" / "con Marco" / "el de Marco" -------> WAITING_WHATSAPP_CHAT_OR_MESSAGE(Marco)
   |                                                             |
   |                                                             |---- "chat" --> OPEN_WHATSAPP_CHAT(Marco)
   |                                                             |---- "mensaje" --> WAITING_MESSAGE(Marco)
   |                                                             |---- "buscá el chat de X" --> OPEN_WHATSAPP_CHAT(X)
   |                                                             |---- "cancelar" --> IDLE
   |                                                             |---- ruido --> "Decime: chat o mensaje."
   |---- "uh eh" / ruido --------------------------------------> "No escuché bien. Probá de nuevo."
   |---- "cancelar" -------------------------------------------> IDLE
```

---

## 6. Respuestas nuevas (catálogo)

| Caso | Frase |
|------|-------|
| Abrir wp guiado | "Decime: chat de Sofi, mensaje para Sofi, o cancelar." |
| Contacto ambiguo | "¿Abrir chat con $contact o mandarle un mensaje?" |
| Re-prompt en desambiguación | "Decime: chat o mensaje." |
| Re-prompt en modo guiado base | "No escuché bien. Probá de nuevo." |
| Mensaje pendiente sin texto | "¿Qué mensaje querés mandarle a $contact?" |
| Ruido sin pending | "No hay ninguna acción pendiente." |
| Backend caído / desconocido | "No entendí. Probá de nuevo." |

---

## 7. Cambios de UI

- `HomeUiState` ahora tiene `agentState: AgentState?` y `lastAgentIntent: AgentIntent?`.
- `statusText(appState, agentState)` es `internal` (testeable sin Compose).
- Panel debug visible cuando `BuildConfig.DEBUG`.
- Botón DESCRIBIR sigue oculto.
- Callar fijo, scroll del contenido principal.

---

## 8. Tests agregados

### `AgentConversationManagerTest` — 11 tests nuevos

- `abrirWpRespondeFraseCortaConChatYMensajeYCancelar` — frase corta ≤80 caracteres.
- `desdeWaitingWhatsAppActionContactoSoloPreguntaChatOMensaje` — "Marco Antonio" → ambiguo con frase exacta.
- `desdeWaitingWhatsAppActionConMarcoLoTrataComoContactoAmbiguo` — "con Marco" → ambiguo.
- `desdeWaitingWhatsAppActionElDeMarcoLoTrataComoContactoAmbiguo` — "el de Marco" → ambiguo.
- `desdeWaitingWhatsAppActionChatMarcoSinDeResuelveOpenChat` — "chat Marco" sin "de" resuelve chat directo.
- `desdeWaitingWhatsAppActionBuscaElChatDeMarcoResuelveOpenChat` — "buscá el chat de Marco Antonio" → chat directo.
- `desdeWaitingWhatsAppActionMensajeParaMarcoPideMensaje` — "mensaje para Marco" → WAITING_MESSAGE.
- `desdeWaitingWhatsAppChatOrMessageDecirChatResuelveOpenChat` — flujo desambiguación → chat.
- `desdeWaitingWhatsAppChatOrMessageDecirMensajePreguntaTextoUsandoElContactoGuardado` — flujo desambiguación → mensaje, contacto preservado.
- `desdeWaitingWhatsAppChatOrMessageSiNoConfirma` — "sí" no confirma.
- `desdeWaitingWhatsAppChatOrMessageCancelarLimpiaTodo` — cancela limpia.
- `desdeWaitingWhatsAppActionFraseRuidoVuelveAlRetryCorto` — "uh eh" → fallback corto, sigue escuchando.

### `HomeStatusTextTest` — 7 tests (archivo nuevo)

Cubre que `statusText` distingue:
- `WAITING_WHATSAPP_ACTION` y `WAITING_WHATSAPP_CHAT_OR_MESSAGE` → "Esperando acción de WhatsApp".
- `WAITING_CONTACT`, `WAITING_MESSAGE`, etc. → labels específicos.
- Sin agentState → comportamiento legacy.

### `AssistantOrchestratorTest` — 1 test ajustado

- `openWhatsAppGuidedDoesNotEmitExternalEvent` — ahora valida que la frase corta contenga `chat`, `mensaje` y `cancelar` en lugar del literal viejo "Qué querés hacer en WhatsApp".

### Tests existentes que siguen verdes (no se rompió nada)

- `LocalIntentParserTest`: variantes naturales WhatsApp/Maps/llamadas.
- `CommandRouterTest`: confirmaciones estrictas, alias.
- `AssistantOrchestratorTest`: WhatsApp compose, llamadas, Maps, memoria, handoff.
- `WhatsAppIntentHelperTest`: intent de chat directo wa.me.
- `PrivacyGuardTest`, `RiskDetectorTest`, `MemoryPolicyTest`, `ConsentManagerTest`: sin tocar.
- `VoiceCommandControllerTest`: error handling de SpeechRecognizer.

---

## 9. Resultados build/tests

### Suite

| Comando | Resultado |
|---------|-----------|
| `:androidApp:testDebugUnitTest` | **BUILD SUCCESSFUL — 481 tests, 0 fallas, 0 errores** |
| `:androidApp:assembleDebug` | BUILD SUCCESSFUL |
| `:shared:allTests` | BUILD SUCCESSFUL |

### Conteo

- Antes del cambio: 462 tests.
- Después: **481 tests**.
- Nuevos: **+19 tests**.
- Ningún test existente se rompió. Un único test viejo (`openWhatsAppGuidedDoesNotEmitExternalEvent`) se actualizó para usar la nueva frase corta — su contrato semántico se mantiene (sin externalEvent, sin newPending, frase guía).

---

## 10. Instalación Samsung

| Paso | Resultado |
|------|-----------|
| `adb devices` | `List of devices attached` (vacío) |
| Estado del Samsung `R5CW22SMWDM` | **No conectado en este momento** |
| APK regenerada | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (57 368 695 bytes) |
| Build | OK |

**No se pudo instalar en esta sesión.** El device se desconectó antes del paso de instalación. Por política de QA no simulo resultados de logcat ni guion físico sin device real.

Pasos para retomar (operador físico):

1. Reconectar el Samsung con cable de datos.
2. Aceptar el diálogo de "Permitir depuración USB" si aparece.
3. Ejecutar:
   ```
   adb devices
   adb -s R5CW22SMWDM install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
   adb -s R5CW22SMWDM logcat -c
   adb -s R5CW22SMWDM shell am start -W -n com.ojoclaro.android/.MainActivity
   ```
4. Seguir el guion de QA física de §11.
5. Recoger logcat:
   ```
   adb -s R5CW22SMWDM logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro WhatsApp SpeechRecognizer TextToSpeech SecurityException"
   ```

---

## 11. Resultado QA física

**No ejecutada en esta ronda** — device no conectado al cierre. Guion previsto para la próxima conexión:

1. Decir "abrí wp" → esperar **frase corta** "Decime: chat de Sofi, mensaje para Sofi, o cancelar." y estado visible **"Esperando acción de WhatsApp"** (no "Esperando confirmación").
2. Decir "Marco Antonio" → "¿Abrir chat con Marco Antonio o mandarle un mensaje?" y estado **"Esperando acción de WhatsApp"**.
3. Decir "chat" → si Marco tiene número guardado, pide confirmación de chat. Si no, frase guía de número.
4. Decir "sí" → NO confirma.
5. Decir "confirmar" → abre WhatsApp directo al chat (si número guardado).
6. Volver a Ojo Claro. Decir "abrí wp" + "buscá el chat de Marco Antonio" → pide confirmación directo.
7. Decir "abrí WhatsApp principal" → handoff abre WhatsApp general.
8. Decir "mandale a Marco Antonio que estoy llegando" → COMPOSE con confirmación.
9. Decir "sí" sin pending → debe responder "No hay ninguna acción pendiente." (no "No pude conectar. Entendí: sí sí").
10. En todos los casos verificar que el panel debug (gris al final) muestra `Último`, `Estado`, `Intent` consistentes.

---

## 12. Qué queda pendiente

- **QA física real** — no se pudo ejecutar en esta ronda; device físico desconectado al final de la sesión.
- **Resolución por `READ_CONTACTS`** — sigue fuera de alcance por decisión de privacidad.
- **Background listening / hotword** — explícitamente fuera por seguridad y políticas.
- **Espacio de búsqueda fuzzy de contactos** — si el usuario dice "Marco" y solo hay "Marco Antonio" guardado, el match exacto sigue siendo conservador. Mejorar fuzzy es una fase aparte.
- **Recordatorios, alarmas, Spotify, IA cloud, iOS** — todos fuera de alcance, sin cambios.

---

## 13. Veredicto demo-ready para WhatsApp

**Sí, demo-ready en seco** (build verde, 481 tests verdes, APK lista). **Veredicto físico pendiente** porque el Samsung se desconectó antes de cerrar el guion. La probabilidad de regresión sobre los flujos cubiertos por tests es baja — no se modificó WhatsApp compose, llamadas, Maps, OCR, PrivacyGuard, RiskDetector, ni reglas de confirmación estricta. El cambio aísla el modo guiado y la presentación del estado.

Riesgo restante: la lista de tokens "name-like noise" (`uh`, `eh`, etc.) puede no cubrir todos los falsos positivos del SpeechRecognizer en español rioplatense. Si en QA física aparece algo como "este este" mal interpretado como contacto, hay que sumarlo al filtro.
