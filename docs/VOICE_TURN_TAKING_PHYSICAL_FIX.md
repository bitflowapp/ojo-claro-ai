# Voice Turn-Taking Physical Fix

## Resumen ejecutivo

Se corrigio el ciclo de voz posterior a preguntas del agente. El caso observado en video era: Ojo Claro entendia "abri wp", entraba correctamente en `WAITING_WHATSAPP_ACTION`, hablaba la guia, pero despues no capturaba de forma confiable el segundo comando. El ajuste se centro en turn-taking TTS/SpeechRecognizer y no en parser.

No se agregaron features nuevas. No se toco Maps, llamadas ni OCR. No se agrego background listening, hotword ni automatizacion por taps.

## Problema fisico

Despues de que TTS dice:

`Decime: chat de un contacto, mensaje para un contacto, o cancelar.`

el microfono podia quedar con una ventana demasiado corta, reiniciar con timeout, procesar ruido o terminar en fallback:

`No escuche bien. Proba de nuevo.`

Eso hacia que el usuario sintiera que la app "se quedaba sorda" justo cuando estaba esperando una respuesta.

## Que cambio

### Turn-taking TTS -> Mic

- Se mantiene pausa explicita de `SpeechRecognizer` cuando TTS empieza.
- Al terminar TTS se agrega una demora corta antes de rearmar el microfono, para reducir eco del propio TTS.
- `onDone` / `onStop` del TTS actualizan debug y reactivan escucha solo si el estado lo permite.
- `callar` sigue cortando TTS y rearmando el loop rapido.

### Escucha extendida cuando se espera respuesta

Se agrego `SpeechListeningMode`:

- `DEFAULT`
- `EXPECTING_RESPONSE`

Cuando la UI esta en `WAITING_CONFIRMATION` o el agente esta en:

- `WAITING_WHATSAPP_ACTION`
- `WAITING_WHATSAPP_CHAT_OR_MESSAGE`
- `WAITING_CONTACT`
- `WAITING_MESSAGE`
- `WAITING_CONFIRMATION`

el `AndroidSpeechInputEngine` usa una ventana mas tolerante:

- mayor duracion minima;
- silencio completo mas largo;
- silencio "posiblemente completo" mas largo.

Ademas, `VoiceCommandController` en modo `EXPECTING_RESPONSE` trata errores como `NO_SPEECH`, `NO_MATCH`, `NETWORK`, `NETWORK_TIMEOUT` y `SERVER` como recuperables. No muestra fallback audible al primer silencio y reintenta sin limpiar contexto.

### Fallback de WhatsApp Guided

En `WAITING_WHATSAPP_ACTION`, si no se entiende una frase util, ahora se dice una sola vez:

`No escuche bien. Decime: chat de Marco, mensaje para Marco, o cancelar.`

Si vuelve a entrar ruido similar en loop, no repite la frase cada pocos segundos: mantiene estado y solicita re-escucha.

## Debug QA fisico

El panel debug de build debug ahora muestra:

- Original reconocido.
- Texto normalizado.
- Estado actual.
- Intent detectado.
- Ultimo error de SpeechRecognizer.
- `Listening: true/false`.
- `Speaking: true/false`.
- Timestamp del ultimo comando.

Ejemplo esperado:

```text
Original: —
Normalizado: —
Estado: WAITING_WHATSAPP_ACTION
Intent: UNKNOWN
Speech error: NO_SPEECH
Listening: true
Speaking: false
Timestamp: 1710000000000
```

## Archivos tocados

- `androidApp/src/main/java/com/ojoclaro/android/voice/SpeechListeningMode.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/SpeechInputEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/AndroidSpeechInputEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/VoiceCommandController.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentConversationManager.kt`
- `androidApp/src/test/java/com/ojoclaro/android/voice/VoiceCommandControllerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/AgentConversationManagerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/ui/home/HomeViewModelExternalRoutingTest.kt`

## Seguridad mantenida

- No se escucha en background.
- No se implemento hotword.
- No se agregaron permisos nuevos.
- No se agrego `READ_CONTACTS`.
- No se agrego `CALL_PHONE` ni `ACTION_CALL`.
- No se agregaron taps automaticos.
- WhatsApp sigue sin enviar mensajes automaticamente.
- Confirmaciones sensibles siguen siendo estrictas.

## Tests agregados/actualizados

Cobertura principal:

- Despues de una pregunta, el controller puede rearmar escucha.
- `EXPECTING_RESPONSE` usa modo extendido.
- `NO_SPEECH` en modo extendido reintenta sin emitir error audible.
- `NETWORK` sin texto util en modo extendido no limpia contexto.
- Error con parcial util sigue procesando el parcial una sola vez.
- Fallback de `WAITING_WHATSAPP_ACTION` no se repite en loop.
- `WAITING_WHATSAPP_ACTION` mantiene estado tras ruido/no-match.
- TTS terminado reanuda escucha con demora corta.
- `callar` sigue cortando aunque haya escucha pendiente.

## Validacion

Comandos ejecutados:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest
.\gradlew.bat :androidApp:assembleDebug --console=plain
.\gradlew.bat :shared:allTests --console=plain
```

Resultados:

- `:androidApp:testDebugUnitTest`: OK.
- `:androidApp:assembleDebug`: OK.
- `:shared:allTests`: OK.

Notas:

- Siguen apareciendo warnings existentes de compatibilidad Kotlin/AGP y targets iOS deshabilitados en esta maquina. No bloquearon la validacion.

## APK

APK generada:

`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

Tamano observado:

`57,368,695 bytes`

## Samsung

No instalado en esta corrida.

`adb devices` no mostro ningun dispositivo conectado, por lo que no se ejecuto:

```powershell
adb -s R5CW22SMWDM install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

## QA fisica recomendada

1. Instalar APK en `R5CW22SMWDM`.
2. Abrir Ojo Claro.
3. Decir: `abri wp`.
4. Esperar TTS: `Decime: chat de un contacto, mensaje para un contacto, o cancelar.`
5. Sin tocar pantalla, decir: `Marco Antonio`.
6. Esperado: `Abrir chat con Marco Antonio o mandarle un mensaje?`
7. Repetir desde Ojo Claro con:
   - `chat Marco`
   - `dale buscame el chat de Marco`
   - `mensaje para Marco`
8. Revisar panel debug: `Speech error`, `Listening`, `Speaking` y estado.
9. Capturar logcat buscando `FATAL EXCEPTION`, `SpeechRecognizer`, `TextToSpeech`.

## Veredicto

La APK queda lista para instalar y probar fisicamente el flujo de WhatsApp Guided Mode con mejor turn-taking. La limitacion actual sigue siendo intencional: Ojo Claro escucha de forma confiable mientras esta visible, no dentro de apps externas.
