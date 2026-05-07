# WhatsApp Guided Mode Fix

Fecha: 2026-05-06

## Resumen ejecutivo

Se cambió el flujo de WhatsApp para que Ojo Claro no abra WhatsApp general cuando la intención está incompleta. Ahora, si el usuario dice "abrí WhatsApp", "abrí wp", "abrí wsp", "abrí wpp" o "abrí wasap", Ojo Claro permanece visible y pregunta qué acción quiere hacer dentro de WhatsApp.

Esto evita la falla física observada en demo: abrir WhatsApp demasiado pronto deja Ojo Claro en segundo plano y el loop de voz deja de ser confiable.

## Qué cambió

- `abrí wp` ya no dispara apertura inmediata de WhatsApp.
- Se agregó el estado conversacional `WAITING_WHATSAPP_ACTION`.
- La app pregunta: "¿Qué querés hacer en WhatsApp? Podés decir: abrir el chat de Sofi, o mandale a Sofi que estoy llegando."
- Desde ese estado, "chat de Marco", "el chat de Marco", "con Marco", "buscá el chat de Marco" y frases completas se resuelven como `OPEN_WHATSAPP_CHAT`.
- Desde ese estado, "mandale a Marco que estoy llegando" y "decile a Marco que estoy llegando" se resuelven como `COMPOSE_WHATSAPP_MESSAGE`.
- "cancelar" limpia el estado y vuelve a escuchar.
- "abrí WhatsApp principal", "abrí WhatsApp solamente" y "solo abrí WhatsApp" sí abren WhatsApp general con handoff.

## Parser reforzado

Estas frases ahora se parsean como `OPEN_WHATSAPP_CHAT` y no como apertura general:

- "abrí WhatsApp y el chat de Marco"
- "abrí WhatsApp y el del chat de Marco"
- "abrí wp y el chat de Marco Antonio"
- "abrí wp y anda al chat de Marco"
- "busca el chat de Marco Antonio"
- "buscar el chat de Marco Antonio"
- "buscame el chat de Marco Antonio"
- "andá al chat de Marco Antonio"
- "quiero hablar con Marco Antonio por WhatsApp"

`mandale a Marco Antonio que estoy llegando` sigue siendo `COMPOSE_WHATSAPP_MESSAGE`.

## SpeechRecognizer

Se fortaleció el manejo de errores de reconocimiento:

- Si llega `ERROR_NETWORK`, `ERROR_NETWORK_TIMEOUT`, `ERROR_NO_MATCH` o `ERROR_SPEECH_TIMEOUT` con texto parcial útil, Ojo Claro procesa ese texto como mejor intento.
- Si no hay texto parcial útil, mantiene el fallback hablado claro.
- El parcial útil se procesa una sola vez para evitar comandos duplicados.

## Seguridad

No se implementó:

- escucha en background;
- hotword permanente;
- taps automáticos;
- navegación de WhatsApp con AccessibilityService;
- `READ_CONTACTS`;
- envío automático de mensajes;
- reducción de confirmaciones sensibles.

## Tests

Cobertura agregada o actualizada:

- `LocalIntentParserTest`: modo guiado para `abrí wp`, apertura explícita principal, frases ruidosas de chat, compose intacto.
- `AgentConversationManagerTest`: `WAITING_WHATSAPP_ACTION`, chat desde estado guiado, compose desde estado guiado, cancelar y apertura principal.
- `AssistantOrchestratorTest`: `abrí wp` no emite `OpenWhatsApp`, `abrí WhatsApp principal` sí emite handoff, frase ruidosa crea pending de chat, confirmación estricta.
- `VoiceCommandControllerTest`: error de conexión con parcial útil procesa el texto; error sin parcial usa fallback; parcial útil no se duplica.

## APK

APK debug:

`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

## QA físico recomendado

1. Decir "abrí wp".
2. Esperado: no abre WhatsApp; pregunta qué querés hacer.
3. Decir "buscá el chat de Marco Antonio".
4. Esperado: pide confirmación o pide guardar número si no existe.
5. Decir "abrí WhatsApp principal".
6. Esperado: avisa handoff y abre WhatsApp general.
7. Decir "abrí WhatsApp y el del chat de Marco".
8. Esperado: no muestra "No pude conectar"; intenta resolver chat.
9. Revisar logcat sin `FATAL EXCEPTION`.

## Estado de validación

- `:androidApp:testDebugUnitTest`: OK.
- `:androidApp:assembleDebug`: OK.
- `:shared:allTests`: OK.
- Samsung `R5CW22SMWDM`: APK instalada con `adb install -r`, resultado `Success`.
- Apertura Samsung: `am start -W -n com.ojoclaro.android/.MainActivity`, resultado `Status: ok`, `WaitTime: 388`.

## Logcat final

Comando ejecutado:

`adb -s R5CW22SMWDM logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro SecurityException WhatsApp maps location SpeechRecognizer TextToSpeech Binder Exception"`

Resultado:

- No apareció `FATAL EXCEPTION` de Ojo Claro.
- No apareció crash `AndroidRuntime` de `com.ojoclaro.android`.
- Aparecieron logs normales de arranque de `com.ojoclaro.android`.
- Apareció ruido del sistema por el filtro amplio: `Binder`, `LocationManagerService`, `TextToSpeech`, `RecognitionService`, `NetworkSpeechRecognizer` y logs de WhatsApp/Samsung.
- El warning `NetworkSpeechRecognizer: Recognizer network error` ocurrió asociado a cancelación/reinicio del servicio de reconocimiento, no como crash de Ojo Claro.

## Pendiente

- Un modo asistente global real requerirá foreground service explícito, notificación persistente, revisión fuerte de privacidad y diseño compatible con Play Protect.
- Por ahora la solución correcta sigue siendo completar la intención antes de salir de Ojo Claro.
- QA física de voz pendiente con usuario: decir "abrí wp", confirmar que no abre WhatsApp y continuar con "buscá el chat de Marco Antonio".
