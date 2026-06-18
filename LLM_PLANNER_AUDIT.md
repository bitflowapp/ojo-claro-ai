# LLM Planner Audit — Estela / Ojo Claro (2026-06-18)

Rama `chore/unattended-hardening-sprint` @ `b220ac9`. **Solo auditoría** — sin cambios
de código, sin commit/push, sin FASE 5.

## 1. Resumen ejecutivo
Estela tiene **dos integraciones LLM** y **un proveedor local de reglas**, no una sola:

- **`/intent` (planner ESTRUCTURADO)** — `OpenAiProxyAgentInterpreter`→`EstelaIntentEngine`→`LlmAgentResponse` (intent + confidence + params + missingSlots + userFacingQuestion + requiresConfirmation + shouldExecuteImmediately + safetyNotes), con un **gate de seguridad** (`LlmSafetyPolicy`). Lo usan **`EstelaAgentRuntime` (Agent Core)** y **`HomeViewModel` (foreground)**.
- **`/conversation` (texto LIBRE)** — `EstelaConversationClient.converse` → string. Lo usa **`GlobalAssistantService` (voz background = la UX real del no vidente)**, gateado por `SafeLlmFallbackPolicy`.
- **`LocalRuleBasedAiProvider`** — reglas on-device (no LLM) para lectura/escena; da guía hardcodeada.

**Hallazgo central:** el **planner estructurado existe y es seguro**, pero en la **ruta de voz background** sólo se alcanza para **objetivos compuestos** (`AgentMissionPhrases.isMissionGoal`: triggers explícitos, o verbo-de-chequeo + sustantivo-de-listeza, o frases encadenadas "… y después …"). Una **intención natural simple** ("quiero hablar con Marco") que los parsers locales rígidos no atrapan **no** recibe clasificación de intención estructurada: cae a **conversación de texto libre** (`/conversation`) o a `no_local_match`. ⇒ El LLM hoy es un **planner PARCIAL**: estructurado y seguro en foreground/misiones, pero **conversacional-solo** para el grueso de intenciones naturales sueltas en background.

**Veredicto corto:** no es chatbot decorativo ni ejecutor peligroso (la seguridad es sólida); es un **planner parcial** subutilizado en background. Falta ~**40%** para "planner seguro" pleno (clasificación estructurada de intención natural en la ruta de voz, con mapeo a acción local validada).

## 2. Diagrama textual del flujo actual (voz background, `handleRecognizedText`)
```
STT → WakeWordStripper → EstelaColloquialNormalizer (coloquial→canónico)
  └─ pendings (confirmación/envío/visible-chat)            [LOCAL, primero]
  └─ forbidden / relationship / instagram / media-refusal  [LOCAL refusals]
  └─ taskAssist / camera / blindFirst / context / dangerous [LOCAL]
  └─ ScreenIntelligence OpenChat (abrí/buscá/encontrá X)    [LOCAL safe chat-open]
  └─ AgentMission.isMissionGoal? → Agent Core ─────────────► /intent (ESTRUCTURADO + LlmSafetyPolicy)
  └─ contextualWhatsApp / foregroundRead / globalScreenQuery/ scroll / back  [LOCAL]
  └─ basicCommand (ayuda/repetir/cancelar)                  [LOCAL]
  └─ EstelaCompanionPhrases.respond (charla exacta)         [LOCAL hardcoded]
  └─ handleWhatsAppCriticalGuardBeforeLlm                   [LOCAL refusal, ANTES del LLM]
  └─ handleSafeLlmFallback → SafeLlmFallbackPolicy.decide:
        ALLOW_CONVERSATION ─► handleFreeConversation ──────► /conversation (TEXTO LIBRE, input sanitizado)
        SUGGEST_REPLY_ONLY / ASK_CLARIFY / BLOCK_DANGEROUS / BLOCK_PRIVATE_CONTEXT  [LOCAL]
        NO_MATCH_SAFE_HELP ─► (return false)
  └─ orchestrator.process (no_local_match) → contact-memory/phone/maps + LocalRuleBasedAiProvider [LOCAL]
```

## 3. Dónde se usa el LLM hoy
| Vía | Cliente | Forma | Caller | Cuándo |
|---|---|---|---|---|
| `/intent` | `OpenAiProxyAgentInterpreter`/`EstelaIntentEngine` | **Estructurada** (`LlmAgentResponse`) | `EstelaAgentRuntime`, `HomeViewModel` | misiones Agent Core + foreground |
| `/conversation` | `EstelaConversationClient` | **Texto libre** | `GlobalAssistantService` | `SafeLlmFallbackPolicy.ALLOW_CONVERSATION` (último recurso seguro) |
| `/api/vision` | `OutdoorScene` | escena (visión) | DescribeAhead/Outdoor | "qué ves/mirá" |
- Gate de seguridad estructurado: `LlmSafetyPolicy.coerce` fuerza `shouldExecuteImmediately=false` para OPEN_WHATSAPP/OPEN_WHATSAPP_CHAT/COMPOSE/CALL/OPEN_PHONE/OPEN_MAPS/NAVIGATE + `forbiddenActionTokens` (call_phone/read_contacts/banco/tarjeta/clave/token/…). `requiresManualReview` si confidence<0.75.
- Gate de conversación: `SafeLlmFallbackPolicy` + `LlmInputSanitizer` (redacta teléfono/email/token antes de salir).

## 4. Dónde NO se usa pero DEBERÍA ayudar (gaps)
Intenciones naturales **simples** que hoy NO reciben clasificación estructurada en background y caen a texto libre / `no_match`:
- **"quiero hablar con Marco"** → sin parser local → `ConversationGate`=conversacional → `/conversation` texto libre. Debería → estructurado `OPEN_WHATSAPP_CHAT{contact:"Marco"}` → flujo local seguro.
- **"leé esto"** → no está en `ScreenQueryPhrases` exacto → posible `no_local_match`. Debería → lectura de pantalla local.
- **"ayudame con WhatsApp"** → no matchea `WhatsAppAnxietyPhrases` ("ayudame con" ≠ "ayuda con") → conversación. Debería → capacidades/guía WhatsApp local.
- **"quiero mandar un mensaje"** (sin destinatario) → debería → clarificación estructurada ("¿a quién?") en vez de depender del parser de compose.
- En general: **el LLM no clasifica intención natural→ruta local en background**; sólo conversa o (para compuestos) planifica.

## 5. Dónde NO debe usarse NUNCA (y hoy se respeta)
- Tocar **enviar / llamar / videollamada / audio / borrar / pagar / ubicación / config sensible** → bloqueado por `WhatsAppCriticalGuard` (antes del LLM) + `SafeLlmFallbackPolicy.BLOCK_DANGEROUS` (refusal explícito, fix `b220ac9`) + `LlmSafetyPolicy.forcedFalseIntents`/`forbiddenActionTokens` + `LlmProposedActionPolicy` (fail-closed) + `requiresConfirmation` en `AgentConversationManager`.
- **Ejecución directa del LLM:** imposible — `/conversation` sólo habla; el estructurado tiene `shouldExecuteImmediately` forzado a false para todo lo riesgoso → pasa por flujo local + confirmación. ✓
- **Contenido privado al LLM:** no — `LlmInputSanitizer` + nunca se manda texto de chat (sólo la frase del usuario; `BLOCK_PRIVATE_CONTEXT`/`SUGGEST_REPLY_ONLY` exigen permiso).

## 6. Riesgos actuales
1. **Subutilización (no de seguridad):** intención natural simple no clasificada → UX pobre ("quiero hablar con Marco" no abre el chat). Riesgo de producto, no de seguridad.
2. **Doble cerebro divergente:** `/intent` (foreground/misión) y `/conversation` (background) no comparten clasificador → comportamiento distinto según ruta.
3. **Dependencia de parsers rígidos:** cada frase nueva exige tocar un parser; el LLM no cubre el hueco con estructura.
4. **`shouldExecuteimmediately` para intents NO forzados-false:** intents fuera de `forcedFalseIntents` podrían ejecutarse inmediato si el adapter lo respeta — revisar que TODO intent ejecutable pase por confirmación local.

## 7. Bugs / rutas pobres detectadas
- "quiero hablar con Marco" / "ayudame con WhatsApp" / "leé esto" → ruta pobre (texto libre o no_match en vez de intención local). **No es bug de seguridad**, es cobertura.
- `EstelaConversationClient.converse` con backend caído → `replyPresent=false` → copy de fallback (útil). OK, pero es texto, no re-intento estructurado.
- (Ya corregidos en la saga: "qué dice ahí", "buscá el chat de X", "WhatsApp de X", refusal explícito de peligrosos.)

## 8. Qué YA está bien
- **Seguridad del LLM: sólida.** No puede enviar/llamar/video/audio/borrar; no ejecuta directo; confirmaciones intactas; tokens prohibidos; input sanitizado; refusals explícitos; counters 0.
- **Forma estructurada ya existe** (`LlmAgentResponse` ≈ el ideal: intent/confidence/params/missingSlots/userFacingQuestion/requiresConfirmation/safetyNotes) + `LlmSafetyPolicy`.
- **Orden correcto:** lectura/WhatsApp/describir/cancelar/repetir/ayuda/refusals corren **antes** de conversación libre (verificado en el dispatch; `criticalGuard` justo antes del LLM).
- **Fallback útil** (no "no entiendo" seco) + clarificación/sugerencia/bloqueo privado en el router.

## 9. Qué falta para "planner seguro" pleno
| Componente | ¿Existe? | Falta |
|---|---|---|
| ConversationState | parcial (`ConversationShortMemory`, pendings) | estado de turno unificado |
| TurnClassifier | parcial (`ConversationGate`, `SafeLlmPhrases`) | clasificador de turno explícito (comando/charla/peligroso/ambiguo) |
| SemanticIntentResolver | **falta en background** | clasificar intención natural→intent local cuando los parsers fallan |
| LlmIntentPlanner | **existe** (`EstelaIntentEngine`/`LlmAgentResponse`) | **cablearlo al background** (no sólo misiones) |
| SafetyPolicy | **existe** (`LlmSafetyPolicy` + `SafeLlmFallbackPolicy` + `LlmProposedActionPolicy`) | unificar las 3 en una sola política |
| ClarificationPolicy | parcial (`ASK_CLARIFY`, `userFacingQuestion`, `missingSlots`) | política de aclaración consistente foreground/background |
| ActionMapper | parcial (`AgentConversationManager`, `ScreenIntelligenceOpenChat`) | mapeo intent→handler local único |
| LocalActionExecutor | **existe** (handlers locales + confirmación) | reusar desde el planner background |

## 10. Plan incremental por fases (sin demolición)
- **Fase A (cableado):** en background, cuando ningún parser local atrapa la frase y `SafeLlmFallbackPolicy` daría `ALLOW_CONVERSATION`, primero intentar el **`/intent` estructurado** (ya existe) → si devuelve intent local con confidence≥umbral → mapear a handler local con confirmación; si no, recién `/conversation`. **No** ejecuta directo (reusar `LlmSafetyPolicy` + confirmaciones).
- **Fase B (clarificación):** usar `missingSlots`/`userFacingQuestion` del estructurado para preguntar ("¿a quién le hablo?") en vez de caer a texto libre.
- **Fase C (unificación):** una sola `SafetyPolicy` + `TurnClassifier` compartidos por foreground y background.
- **Fase D (cobertura):** mapear las frases-gap (hablar con X / ayudame con WhatsApp / leé esto) a intents locales vía el planner, con tests.
- Cada fase: detrás de flag, default seguro, sin tocar refusals/confirmaciones, con la barrera `PrivacyLoggingGuardTest`.

## 11. Tests recomendados
- frase natural → intent estructurado: "abrime el WhatsApp de Marco"/"quiero hablar con Marco" → `OPEN_WHATSAPP_CHAT{Marco}` (no texto libre).
- intent LLM → acción local segura (con confirmación; nunca tap directo).
- peligroso → refusal explícito: "tocá enviar"/"llamá a este contacto"/"mandá un mensaje sin preguntarme" → `BLOCK_DANGEROUS`/critical-guard, counters 0.
- ambiguo → pregunta: "quiero mandar un mensaje" (sin destinatario) → clarificación.
- falta de contexto → guía: "leé mis chats" fuera de WhatsApp → guía local.
- LLM down → fallback útil (no silencio); JSON inválido → fail-closed (`parseReply`=null → copy); LLM propone acción peligrosa → `LlmProposedActionPolicy=BLOCK`.
- no logs sensibles (`PrivacyLoggingGuardTest`).
- **Frases obligatorias** (matriz): "abrime el WhatsApp de Marco", "quiero hablar con Marco", "qué ves", "qué dice ahí", "leé mis chats", "quiero responderle", "decile que ya voy", "mandá eso", "sí", "no", "cancelá", "tocá enviar", "llamá a este contacto", "qué puedo pedirte", "ayudame con WhatsApp" → cada una asserta {ruta, seguridad, no-tap}.

## 12. Veredicto
- **¿LLM actual suficiente?** No para "copiloto/planner" pleno; **sí** para seguridad.
- **¿Sólo fallback conversacional?** En **background**, casi sí (texto libre + router seguro); el estructurado sólo entra en misiones compuestas.
- **¿Planner parcial?** **Sí.** El planner estructurado existe, es seguro y se usa en foreground/misiones; falta cablearlo a la voz background para intención natural simple.
- **¿Qué % falta para planner seguro?** ~**40%**: la **infraestructura segura ya está (~60%)** (forma estructurada + 3 políticas de seguridad + confirmaciones + sanitización + orden correcto); falta **clasificación de intención natural→ruta local en background + clarificación estructurada + unificación de políticas** (Fases A–D), todo encima de lo existente, sin demolición.

### Clasificación de las frases de la Q2 (estado actual)
| Frase | Ruta hoy | Ideal |
|---|---|---|
| "abrime el WhatsApp de Marco" | LOCAL (normalizer→ScreenIntelligence OpenChat seguro) | ✓ (ok) |
| "quiero hablar con Marco" | LLM fallback (texto libre) | estructurado OPEN_WHATSAPP_CHAT |
| "mostrame mis chats" | LOCAL (chat-list) | ✓ |
| "qué aparece" | LOCAL (screen read) | ✓ |
| "ayudame con WhatsApp" | LLM/charla | help WhatsApp local |
| "no sé qué hacer" | LLM fallback | ✓ (charla guía) |
| "leé esto" | no_match probable | lectura local |
| "respondéle que después voy" | LOCAL compose (draft+confirm, no envío) | ✓ |
| "decile que ahora no puedo" | LOCAL compose (draft+confirm) | ✓ |
| "quiero mandar un mensaje" | LOCAL compose/clarifier | clarificación estructurada |
| "mandá un mensaje sin preguntarme" | REFUSAL (dangerous) | ✓ |
| "tocá enviar" | REFUSAL explícito (`BLOCK_DANGEROUS`) | ✓ |
