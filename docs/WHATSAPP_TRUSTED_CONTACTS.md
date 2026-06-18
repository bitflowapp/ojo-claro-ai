# Trusted Contacts / Relationship Resolver (WhatsApp Blind-First)

## Problema de producto

`MemoryContactResolver` resuelve contactos de confianza guardados y números
dictados. Pero "mi novia" no es un contacto guardado: el resolver devuelve
`not_found`, así que Estela no tiene fuente para construir un deep link `wa.me`
ni para confirmar el destino. Pedirle a una persona **no vidente** que abra el
chat a mano no es una solución. Hacía falta resolver **relaciones** ("mi novia",
"mi pareja", "mi contacto de prueba") contra una fuente local y segura.

## Diseño

Tres piezas puras + cableado en el servicio, **todo local**, sin red, sin LLM,
sin envío/llamada/pago.

### 1. `WhatsAppRelationshipAlias` (puro)
Mapea frases de relación a una **clave canónica** estable: "mi novia", "novia",
"mi pareja", "mi mujer", "mi esposa"… → `pareja`. Vincular una vez alcanza para
todos los alias. Grupos: `pareja`, `qa`, `mama`, `papa`.
- `canonicalKey(phrase)` / `isRelationshipPhrase(phrase)`
- `spokenLabel(key)` → etiqueta hablable/redactada ("tu pareja")
- `parseLinkRequest(text)` → clave a vincular SÓLO con marca explícita
  ("este contacto es mi novia", "guardá a mi novia", "marcá … como mi pareja").
  No roba "abrí el chat de mi novia".
- `parseForgetRequest(text)` → clave a olvidar ("olvidá a mi novia",
  "desvinculá a mi pareja", "ya no es mi novia").
- Tolera preposición/artículo inicial ("a mi novia") y elisión del posesivo
  ("novia" → "mi novia").

### 2. `RelationshipContactStore` (memoria local privada)
`SharedPreferences` privadas del paquete (`ojo_claro_relationship_contacts`,
prefijo `rel.`). Guarda `label`, `phone`, `source` por clave.
- `link/resolve/isLinked/forget/clearAll`
- El número se valida con `SafeContactMemory.normalizePhoneNumber` (6–15 dígitos).
- `RelationshipContact.redactedForLog()` = `key=… labelLen=… phoneLen=… source=…`
  (**nunca** el número).

### 3. Lectura del número desde la pantalla (accesibilidad)
`OjoClaroAccessibilityService.readVisibleWhatsAppPhoneNumber()` lee **sólo** los
nodos de IDENTIDAD del chat/perfil de WhatsApp (`conversation_contact_name`,
`conversation_contact_status`, `contact_title`) — jamás el cuerpo del chat, para
no confundir un número citado en un mensaje con el del contacto. Devuelve el
número normalizado o `null`; el llamador lo redacta en todo log/voz.

### 4. Verificación FUERTE de destino (`WhatsAppDestinationVerifier`, puro)
Tras abrir por `wa.me`, **no alcanza con `inChat=true`**: el deep link puede
dejar a la persona en otro chat. El verificador contrasta el destino esperado
contra las señales disponibles y produce un resultado explícito:
`VERIFIED` · `UNVERIFIED_NO_SIGNALS` · `MISMATCH` · `NOT_IN_CHAT` ·
`NO_ENTRY_FIELD` · `TIMEOUT`. Señal fuerte = últimos 4 dígitos visibles en la
cabecera vs los esperados; secundaria = coincidencia de etiqueta. Si el
resultado **no** es `VERIFIED` → bloqueo `COULD_NOT_CONFIRM_DESTINATION`: no
draft, no tap, no envío, no llamada, no LLM.

### Cableado en `GlobalAssistantService` (antes del LLM)
- **Abrir** (`handleBlindOpenContactChat`): resuelve la relación **antes** de la
  resolución por nombre. Con vínculo → `WhatsAppDestination` + `openBlindContactChat`
  (deep link → `verifyOpenedDestination` FUERTE → sólo afirma si `VERIFIED`).
  **Sin vínculo** → respuesta local segura que enseña a vincular; **no** cae al
  LLM ni pide abrir a mano.
- **Escribir por relación** (`handleWhatsAppRelationshipComposeCommand`,
  `WhatsAppRelationshipComposeParser`): "mandale/decile/escribile/respondé a mi
  novia que ESTOY LLEGANDO". Corre **antes** del smart-compose genérico.
  Flujo: relación → resolver → abrir → **verificar fuerte** → **recién si
  `VERIFIED`** preparar borrador (`draftWhatsAppMessageAndConfirm`) → leer →
  doble confirmación WA-5. **Nunca envía por defecto**: "sí" avanza pero no
  envía; "mandalo" → `SEND_REAL_NOT_ENABLED` (flag apagado); "pará no mandes"
  cancela y limpia. Sin mensaje no reclama (lo toma el flujo de apertura).
- **Vincular/olvidar** (`handleWhatsAppRelationshipCommand`): "este contacto es mi
  novia" lee el número visible y **confirma por los últimos 4** antes de guardar
  (`handlePendingRelationshipLinkReply`); "olvidá a mi novia" desvincula. Sólo en
  contexto WhatsApp. Reversible: un "sí" alcanza, "cancelar" descarta.
- STOP global limpia el pendiente de vínculo.

### Canal QA debug-safe (sólo builds debug)
`DebugCommandActivity` acepta:
```
# desde el chat/perfil de la persona (número NO se tipea):
adb shell am start -n com.ojoclaro.android/.debug.DebugCommandActivity --es seed_relationship pareja
# QA con número SINTÉTICO explícito:
adb shell am start -n com.ojoclaro.android/.debug.DebugCommandActivity \
  --es seed_relationship qa --es seed_phone +54 9 11 5550-0000 --es seed_label "QA"
# olvidar:
adb shell am start -n com.ojoclaro.android/.debug.DebugCommandActivity --es forget_relationship pareja
```
Escribe sólo en las SharedPreferences privadas; loguea redactado (longitud +
últimos 4); nunca el número entero.

## Privacidad (reglas duras cumplidas)
- Ningún número hardcodeado; ninguno en repo/docs/logs/memoria.
- Logs sólo redactados (`labelLen`/`phoneLen`/`phoneEnding`/`source`/`hasEnding`).
- Nada de contactos al backend ni al LLM. Todo local.
- Tests usan **sólo** números sintéticos.

## Estado
- Tests: 2901 verdes (alias 7, store 8, verifier 10, compose parser 8, contrato
  de ruteo 14).
- `assembleDebug` PASS. Commit local: `2586d17 feat(whatsapp): add trusted relationship contacts with destination verification`. Sin push. Pendiente smoke físico supervisado.
- **PENDIENTE**: smoke físico con número visible / seed QA en el dispositivo.
- **Gap conocido (lado seguro)**: para un contacto guardado por NOMBRE (sin
  número en la cabecera) la verificación da `UNVERIFIED_NO_SIGNALS` y **bloquea**
  el borrador. Es intencional: mejor bloquear que escribirle a quien no es. El
  caso real (contacto no agendado → número visible) verifica por últimos 4.
