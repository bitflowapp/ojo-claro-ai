# Ojo Claro AI — Flujo seguro "abrir chat de WhatsApp"

Fecha: 2026-05-06
Estado: implementado, build verde, **439/439 tests verdes**, APK regenerada.
Autor: ingeniería.

---

## 1. Problema detectado en prueba física

Durante la QA física del Samsung S23+ (SM-S916B, Android 16) se observó este patrón:

1. El usuario dice **"abrí wp"**.
2. Ojo Claro abre WhatsApp correctamente vía `Intent.ACTION_VIEW` con `getLaunchIntentForPackage("com.whatsapp")`.
3. **WhatsApp pasa al frente y Ojo Claro queda en segundo plano.** Ahí termina su rol: por contrato, la escucha continua sólo vive mientras la app es visible.
4. Acto seguido, el usuario dice **"andá al chat de Marco Antonio"**, pero Ojo Claro ya no escucha — está oculta.

El bug no es funcional: el sistema se comporta como está diseñado. El bug es **expectativa**: el usuario cree que puede pedir "andá al chat" después de "abrí WhatsApp" como si fuera un mismo flujo. WhatsApp no expone APIs públicas para que un asistente externo navegue dentro de su UI sin recurrir a navegación por `AccessibilityService` (taps automáticos sobre elementos privados de otra app).

## 2. Por qué NO escuchamos dentro de WhatsApp todavía

- **No abrir el mic en background** es una garantía explícita del producto (`docs/ACCESSIBILITY_QUICK_ACTIVATION.md` §6: "el voice loop NO arranca desde el tile ni desde el AccessibilityService — sólo desde la UI cuando es visible").
- **No automatizar taps en otra app** vía `AccessibilityService` con `performAction(GLOBAL_ACTION_…)`. Eso convertiría a Ojo Claro en una herramienta de control remoto sobre WhatsApp — terreno minado para privacidad y políticas de Play Store.
- **No leer sin pedir** dentro de WhatsApp. La lectura de pantalla actual se hace bajo `READ_VISIBLE_MESSAGE` consent estricto — agregar navegación silenciosa rompe ese contrato.
- **No interceptar gestos del usuario.** El SO ya tiene TalkBack para esa función.

Por todo esto, la solución correcta NO es "que Ojo Claro siga vivo dentro de WhatsApp", es **resolverlo antes de salir de Ojo Claro** — preparar el chat directo como un solo intent y delegar el resto al usuario.

## 3. Solución con intent seguro

### Comando único, antes de salir

Cuando el usuario dice "abrí el chat de Marco Antonio", Ojo Claro:

1. Detecta el intent **OPEN_WHATSAPP_CHAT** localmente (sin red, sin IA cloud).
2. Resuelve el contacto contra `MemoryContactResolver` — sólo memoria local aprobada (`TRUSTED_CONTACT` / `EMERGENCY_CONTACT`).
3. Si falta contacto en el comando, pregunta **"¿Qué chat querés abrir?"**.
4. Si el contacto no tiene número guardado, responde **"No tengo un número guardado para Marco Antonio. Podés decir: el número de Marco Antonio es..."**.
5. Si el número existe, pide confirmación estricta: **"Voy a abrir el chat de WhatsApp con Marco Antonio. No voy a enviar ningún mensaje. Confirmá para continuar."**.
6. Sólo con `confirmar` / `confirmo` / `aceptar` se ejecuta. `sí`, `si`, `dale` siguen sin confirmar acciones sensibles.
7. Al confirmar, dispara `Intent.ACTION_VIEW` con URI `https://wa.me/<dígitos sin "+">` y `setPackage("com.whatsapp")` cuando está instalado. **Sin texto, sin envío automático, sin chooser web**.

### Intent técnico

```kotlin
WhatsAppChatIntentSpec(
    action = Intent.ACTION_VIEW,
    dataUri = "https://wa.me/5491123456789",   // sólo dígitos, sin "+"
    packageName = "com.whatsapp"               // null si no está instalado
)
```

- Esquema `wa.me` es la URL pública oficial de WhatsApp.
- No incluye `?text=` ni `send`. La capa de "abrir chat" jamás escribe nada en el cuadro de texto.
- Si WhatsApp no está instalado, `openChat` retorna fallo claro y NO abre un chooser web — eso evita resolver a un navegador por accidente.

### Comando "abrí WhatsApp" mantiene su flujo + sugerencia útil

Para el comando genérico, no se rompe nada:

> "Abriendo WhatsApp. Para abrir un chat directo, decime: abrí el chat de un contacto."

Antes decía sólo "Abriendo WhatsApp."

## 4. Comandos soportados

Todos detectados localmente por `LocalIntentParser.parseOpenWhatsAppChat`. Si la frase contiene un verbo de mensajería (`mandale`, `decile`, `escribile`, `enviar`, `mandar mensaje`, etc.), el parser se hace a un lado y deja que `COMPOSE_WHATSAPP_MESSAGE` tome el control — así no rompemos el flujo de redactar mensajes.

| Frase | Intent | Slots requeridos |
|-------|--------|------------------|
| "abrí el chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abri el chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abre el chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí chat con Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "andá al chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "anda al chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "ir al chat de Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí WhatsApp con Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí wp con Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí wsp con Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí wpp con Marco Antonio" | OPEN_WHATSAPP_CHAT | contactName |
| "quiero hablar con Marco Antonio por WhatsApp" | OPEN_WHATSAPP_CHAT | contactName |
| "abrí chat" / "abrí un chat" / "andá al chat" | OPEN_WHATSAPP_CHAT | (pregunta contacto) |
| "abrí WhatsApp" | OPEN_WHATSAPP | — (se mantiene legacy) |
| "mandale a Marco Antonio que estoy llegando" | COMPOSE_WHATSAPP_MESSAGE | (sin cambio) |
| "escribile a Marco Antonio por WhatsApp que estoy llegando" | COMPOSE_WHATSAPP_MESSAGE | (sin cambio) |

## 5. Archivos tocados

Implementación:

- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentIntent.kt` — agregado `OPEN_WHATSAPP_CHAT`.
- `androidApp/src/main/java/com/ojoclaro/android/agent/LocalIntentParser.kt` — `parseOpenWhatsAppChat`, regex y phrases. Rama exhaustiva en el `when` interno para `OPEN_WHATSAPP_CHAT` (cae a UNKNOWN si llegara desde `commandRouter.parse`).
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentConversationManager.kt` — `handleOpenWhatsAppChat` con missing `CONTACT_NAME` ("¿Qué chat querés abrir?") y completamiento por `WAITING_CONTACT`.
- `androidApp/src/main/java/com/ojoclaro/android/external/ExternalCommand.kt` — agregado `ExternalCommandType.OPEN_WHATSAPP_CHAT`.
- `androidApp/src/main/java/com/ojoclaro/android/external/CommandRouter.kt` — rama exhaustiva en `route()` (NotSupported defensivo: `parse()` no emite este tipo) y mensaje hablado al confirmar el pending OPEN_WHATSAPP_CHAT.
- `androidApp/src/main/java/com/ojoclaro/android/external/ExternalActionEvent.kt` — agregado `OpenWhatsAppChat(confirmationId, contactName, phoneE164)`.
- `androidApp/src/main/java/com/ojoclaro/android/external/WhatsAppIntentHelper.kt` — `openChat(contactName, phoneE164)` + `companion.buildOpenChatIntentSpec` puro/testeable.
- `androidApp/src/main/java/com/ojoclaro/android/domain/AssistantOrchestrator.kt` — `handleOpenWhatsAppChatIntent`, `buildOpenChatConfirmation`, ruteo del agent flow para `OPEN_WHATSAPP_CHAT`, rama `OPEN_WHATSAPP_CHAT` en `handleExternalSuccess`, copy actualizado de `OPEN_WHATSAPP` ("Abriendo WhatsApp. Para abrir un chat directo, decime: abrí el chat de un contacto.").
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt` — `OPEN_WHATSAPP_CHAT` agregado a `knownCurrentFlow`, `shouldRouteSuggestedIntent`, condición de `shouldUseAgentConversation`, y `toLegacyOpenWhatsAppChatCommand`.
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt` — case `is ExternalActionEvent.OpenWhatsAppChat -> whatsAppIntentHelper.openChat(...)` en el colector de eventos externos.

Tests:

- `androidApp/src/test/java/com/ojoclaro/android/external/WhatsAppIntentHelperTest.kt` — **nuevo**, 9 tests sobre `buildOpenChatIntentSpec`.
- `androidApp/src/test/java/com/ojoclaro/android/agent/LocalIntentParserTest.kt` — 11 tests nuevos para OPEN_WHATSAPP_CHAT y guardrails.
- `androidApp/src/test/java/com/ojoclaro/android/agent/AgentConversationManagerTest.kt` — 4 tests nuevos.
- `androidApp/src/test/java/com/ojoclaro/android/domain/AssistantOrchestratorTest.kt` — 7 tests nuevos.

## 6. Tests

### Suite

```
:androidApp:testDebugUnitTest      BUILD SUCCESSFUL
:androidApp:assembleDebug          BUILD SUCCESSFUL
:shared:allTests                   BUILD SUCCESSFUL
```

### Conteo

- **Total: 439 tests, 0 fallas, 0 errores.**
- Antes del cambio: 408 tests.
- Suma: **+31 tests** sobre el flujo nuevo.

### Tests clave

**`WhatsAppIntentHelperTest`**
- `openChatSpecUsaActionView` — el spec usa `Intent.ACTION_VIEW`, no `ACTION_SEND`.
- `openChatSpecUsaWaMeSinSigno` — URI = `https://wa.me/5491123456789` (sin `+`).
- `openChatSpecAplicaPackageWhatsAppCuandoEstaInstalado` — `setPackage("com.whatsapp")`.
- `openChatSpecPackageNullSiNoEstaInstalado` — sin chooser web.
- `openChatSpecNoIncluyeTextNiSendNiMensaje` — el URI no contiene `?text=`, `text=`, `send`. **No envía**.
- `openChatSpecNoUsaActionSend` — separado del flujo COMPOSE.
- `openChatSpecRechazaNumeroInvalidoCorto` / `openChatSpecRechazaNumeroVacio` — null si el número no es plausible.
- `openChatSpecToleraEspaciosYGuiones` — normaliza `"+54 9 11 2345-6789"` a `5491123456789`.

**`LocalIntentParserTest`** (nuevos)
- `parseaAbrirChatDeContactoComoOpenWhatsAppChat` y variantes con "andá al chat", "abrí WhatsApp con", "abrí wp con", "abrí wsp con", "quiero hablar con … por WhatsApp".
- `abrirChatSinContactoTieneMissingContactName` — flujo de slot filling.
- `abrirWhatsAppSinContactoSigueSiendoOpenWhatsApp` — no rompe el flujo legacy.
- `mandaleAContactoSigueSiendoComposeNoChat` — el verbo de mensajería desactiva la detección de chat.

**`AgentConversationManagerTest`** (nuevos)
- `siFaltaContactoEnAbrirChatPreguntaQueChat` — pregunta "¿Qué chat querés abrir?".
- `completarContactoEnAbrirChatProponeIntentParaOrquestador` — el manager NO ejecuta Android, sólo entrega el intent listo.
- `abrirChatConContactoCompletoNoPideMasSlots` — passthrough cuando ya hay contacto.
- `cancelarLimpiaPendingDeAbrirChat` — Cancelar limpia el pending.

**`AssistantOrchestratorTest`** (nuevos)
- `openWhatsAppChatWithStoredNumberAsksForConfirmation` — pending creado con `ExternalCommandType.OPEN_WHATSAPP_CHAT`, `targetName = "Marco Antonio"`, `payloadText = "1123456789"`. Sin externalEvent.
- `confirmingOpenWhatsAppChatPendingEmitsOpenWhatsAppChatEvent` — al confirmar emite `ExternalActionEvent.OpenWhatsAppChat`.
- `cancellingOpenWhatsAppChatPendingDoesNotEmitEvent` — Cancelar no abre nada.
- `siNoConfirmaOpenWhatsAppChatPending` — `"sí"` no confirma.
- `openWhatsAppChatSinNumeroGuardadoRespondeFraseClara` — frase guía si no hay número.
- `openWhatsAppChatSinWhatsAppDevuelveError` — error claro si WhatsApp no está instalado.
- `openWhatsAppGenericoSugiereChatDirecto` — el copy de "abrí WhatsApp" ahora menciona el chat directo.

### Tests existentes que siguen verdes (no se rompió nada)

- `CommandRouterTest` — confirmación estricta, alias, falta de contacto/mensaje.
- `AssistantOrchestratorTest` — todo el flujo de COMPOSE, llamadas, mapas, memoria.
- `PrivacyGuardTest`, `RiskDetectorTest`, `MemoryPolicyTest`, `ConsentManagerTest` — sin tocar.
- `LocalIntentParserVariantsTest` — variantes naturales de COMPOSE.

## 7. APK

```
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Tamaño: **57.368.695 bytes (≈57 MB)**. Recompilada con el cambio.

## 8. Cómo probar en físico

Pre-condiciones:

1. APK instalada en el Samsung (`adb -s R5CW22SMWDM install -r ...`).
2. WhatsApp instalado y logueado.
3. Permiso de micrófono concedido.
4. Volumen 70–80%.

Guion:

1. Abrir Ojo Claro y esperar saludo.
2. Decir "**recordá que Marco es contacto de confianza**" → consent → "**confirmar**".
3. Decir "**el número de Marco es 1123456789**" (usar un número real propio para no llamar a desconocidos) → consent → "**confirmar**".
4. Decir "**abrí el chat de Marco**".
5. Verificar que la app dice algo como "Voy a abrir el chat de WhatsApp con Marco. No voy a enviar ningún mensaje. Confirmá para continuar.".
6. Decir "**sí**" — debe NO confirmar.
7. Decir "**confirmar**" — WhatsApp se abre directamente sobre el chat de Marco con el campo de texto vacío.
8. Verificar manualmente que ningún mensaje se envió.
9. Volver a Ojo Claro.
10. Decir "**andá al chat de Marco**" — debe pedir confirmación de nuevo (el pending anterior ya se consumió).
11. Decir "**cancelar**" — "Acción cancelada.".
12. Decir "**abrí el chat de Persona Inexistente**" — debe responder "No tengo un número guardado para Persona Inexistente. Podés decir: el número de Persona Inexistente es...".

Comportamiento esperado en todos los casos: ningún mensaje enviado automáticamente, ningún tap simulado dentro de WhatsApp, ninguna escucha en background.

## 9. Pendientes / no implementado a propósito

- **Escuchar dentro de WhatsApp**: explícitamente fuera de alcance. Cuando Ojo Claro pasa a segundo plano, el voice loop pausa. Si en el futuro se quisiera atender ese caso, requeriría un foreground service de mic con consentimiento explícito y reglas claras sobre apps "permitidas" — fase aparte.
- **Resolver alias múltiples**: si hay dos "Marco" guardados, hoy se responde con la frase genérica "Tengo varios contactos con ese nombre. Decime el nombre completo o el número.". Mejorar la desambiguación queda fuera de este cambio.
- **Leer último mensaje**: el usuario podría pedir "leeme el último mensaje de Marco". Eso depende de `READ_VISIBLE_MESSAGE` con consent (pantalla visible) o de una fase con notification listener — fuera de alcance.
- **`READ_CONTACTS`**: NO se pidió. La resolución sigue siendo 100% memoria local aprobada por el usuario.
- **Hotword en background**: NO se implementó.
- **iOS**: no tocado.
- **OCR / WhatsApp compose / llamadas / Maps**: sin cambios funcionales (sólo compatibilidad con el nuevo enum donde lo exigió Kotlin para mantener exhaustividad).

## 10. Veredicto

Demo-ready para mostrar el flujo "abrir chat de WhatsApp con un contacto guardado", siempre y cuando el contacto esté guardado en memoria local y WhatsApp esté instalado. El bug de expectativa que motivó el cambio queda cerrado: el usuario ahora puede pedir el chat directo en una sola frase, y Ojo Claro lo prepara, lo confirma y lo abre — sin enviar nada, sin tocar la libreta del sistema, sin escuchar oculto.
