# GPT Mini Personal Agent Contract

## Goal
Use a future GPT mini model only for interpretation and drafting. It must never execute actions by itself.

## Contract

### Request

```kotlin
data class LlmAgentRequest(
    val originalText: String,
    val normalizedText: String,
    val locale: String,
    val agentState: AgentState,
    val externalApp: String?,
    val memorySummary: String,
    val knownSafeContacts: List<String>,
    val knownPlaces: List<String>,
    val activePendingTasks: List<String>,
    val allowedIntents: List<AgentIntent>,
    val forbiddenActions: List<String>
)
```

### Response

```kotlin
data class LlmAgentResponse(
    val intent: AgentIntent?,
    val confidence: Float,
    val contactName: String?,
    val messageText: String?,
    val proposedMessage: String?,
    val destination: String?,
    val locationAlias: String?,
    val routineName: String?,
    val pendingTask: String?,
    val missingSlots: List<String>,
    val userFacingQuestion: String?,
    val suggestionText: String?,
    val requiresConfirmation: Boolean,
    val shouldExecuteImmediately: Boolean,
    val safetyNotes: String?
)
```

## Rules

- GPT mini interprets and drafts only.
- Ojo Claro validates, confirms, and executes.
- `shouldExecuteImmediately` must be forced to `false` for:
  - WhatsApp
  - calls
  - Maps
- If `confidence < 0.75`, ask a question.
- If the model suggests anything forbidden, ignore it.
- If the response touches bank data, codes, cards, passwords, or documents, pass it through `PrivacyGuard` and `RiskDetector`.

## Allowed intents

- `COMPOSE_WHATSAPP_MESSAGE`
- `OPEN_WHATSAPP_CHAT`
- `OPEN_WHATSAPP`
- `OPEN_MAPS`
- `NAVIGATE_TO_DESTINATION`
- `OPEN_PHONE`
- `CALL_CONTACT`
- `GET_CURRENT_LOCATION`
- `REMEMBER_MEMORY`
- `LIST_MEMORY`
- `CLEAR_MEMORY`
- `SAVE_LOCATION_ALIAS`
- `LIST_LOCATION_ALIASES`
- `DELETE_LOCATION_ALIAS`
- `LIST_CONTACTS`

## Forbidden actions

- `read_contacts`
- `call_phone`
- `action_call`
- `background_location`
- `send_message_without_confirmation`
- `open_banking_apps`
- `share_codes`
- `share_passwords`
- `share_cards`
- `share_documents`

## JSON examples

### Human message proposal

Input:

```json
{
  "originalText": "decile a Sofi que llego tarde pero decilo bien",
  "normalizedText": "decir a Sofi que llego tarde pero decirlo bien",
  "locale": "es-AR",
  "agentState": "WAITING_MESSAGE",
  "externalApp": "WhatsApp",
  "memorySummary": "Contacto Sofi. Preferencia: respuestas cortas.",
  "knownSafeContacts": ["Sofi"],
  "knownPlaces": ["casa", "laburo"],
  "activePendingTasks": ["Responderle a Marco"],
  "allowedIntents": ["COMPOSE_WHATSAPP_MESSAGE", "OPEN_WHATSAPP_CHAT"],
  "forbiddenActions": ["read_contacts", "call_phone", "action_call"]
}
```

Output:

```json
{
  "intent": "COMPOSE_WHATSAPP_MESSAGE",
  "confidence": 0.88,
  "contactName": "Sofi",
  "messageText": "llego tarde",
  "proposedMessage": "Amor, voy un poco demorado. Llego en unos minutos.",
  "missingSlots": [],
  "userFacingQuestion": "Te propongo: Amor, voy un poco demorado. Llego en unos minutos. ¿Lo preparo?",
  "suggestionText": null,
  "requiresConfirmation": true,
  "shouldExecuteImmediately": false,
  "safetyNotes": "Local draft only."
}
```

### Route suggestion

Input:

```json
{
  "originalText": "me voy al laburo",
  "normalizedText": "me voy al laburo",
  "locale": "es-AR",
  "agentState": "IDLE",
  "externalApp": null,
  "memorySummary": "Lugar laburo. Preferencia: respuestas cortas.",
  "knownSafeContacts": ["Sofi"],
  "knownPlaces": ["laburo", "casa"],
  "activePendingTasks": ["Responderle a Marco"],
  "allowedIntents": ["NAVIGATE_TO_DESTINATION", "OPEN_MAPS"],
  "forbiddenActions": ["read_contacts", "call_phone", "action_call"]
}
```

Output:

```json
{
  "intent": "NAVIGATE_TO_DESTINATION",
  "confidence": 0.82,
  "destination": "laburo",
  "suggestionText": "Querés que abra la ruta al trabajo?",
  "requiresConfirmation": true,
  "shouldExecuteImmediately": false,
  "safetyNotes": "Needs local confirmation."
}
```

### Rare phrase

Input:

```json
{
  "originalText": "abrir algo raro que no entiendo",
  "normalizedText": "abrir algo raro que no entiendo",
  "locale": "es-AR",
  "agentState": "IDLE",
  "externalApp": null,
  "memorySummary": "",
  "knownSafeContacts": [],
  "knownPlaces": [],
  "activePendingTasks": [],
  "allowedIntents": ["OPEN_WHATSAPP", "OPEN_MAPS", "OPEN_PHONE", "COMPOSE_WHATSAPP_MESSAGE"],
  "forbiddenActions": ["read_contacts", "call_phone", "action_call"]
}
```

Output:

```json
{
  "intent": null,
  "confidence": 0.42,
  "missingSlots": [],
  "userFacingQuestion": "No te entendí del todo. Decime chat, mensaje, mapa o telefono.",
  "suggestionText": null,
  "requiresConfirmation": false,
  "shouldExecuteImmediately": false,
  "safetyNotes": "Low confidence. Ask the user."
}
```

## Validation

- If the response says execute immediately for WhatsApp, calls, or Maps, Ojo Claro must override it to `false`.
- If confidence is low, the app should ask a short question.
- If the response is risky, discard it and fall back to local rules.
- If the model is disabled, the app must keep working with local logic.

