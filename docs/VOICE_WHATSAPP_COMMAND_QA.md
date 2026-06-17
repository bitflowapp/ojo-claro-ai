# Voice WhatsApp Command QA

## Resumen
Se mejoró el parser local de comandos de WhatsApp para frases naturales dictadas por voz. El flujo sigue siendo seguro: no envía mensajes automáticamente, pide confirmación estricta y bloquea mensajes con datos sensibles antes de preparar WhatsApp.

## Frases cubiertas por tests
- `mandale a un contacto: estoy llegando`
- `mandale a un contacto que estoy llegando`
- `mandale un mensaje a un contacto que estoy llegando`
- `mandale un WhatsApp a un contacto que estoy llegando`
- `escribile a un contacto por WhatsApp que estoy llegando`
- `decile a un contacto que estoy llegando`
- `en WhatsApp mandale a un contacto que estoy llegando`
- `abrí WhatsApp y mandale a un contacto que estoy llegando`
- `mandale a mi novia que estoy llegando`
- `mandale mensaje a mamá diciendo que estoy bien`
- `abrí whats app`
- `abrí wp`
- `abrí wsp`
- `abrí wpp`
- `abrí wasap`
- `abrí guasap`
- `abrí watsap`
- `abrí whasap`

## Cuáles entendió
Todas las frases anteriores quedan parseadas en tests unitarios. Las frases con contacto y mensaje devuelven `COMPOSE_WHATSAPP_MESSAGE`, con `contactName` y `messageText` separados.

## Aclaraciones cuando falta información
- `mandale un mensaje` no abre WhatsApp y responde: `¿A quién querés mandarle el mensaje?`
- `mandale a un contacto` no abre WhatsApp y responde: `¿Qué mensaje querés mandarle?`

## Seguridad
- `abrí WhatsApp` sigue devolviendo `OPEN_WHATSAPP`.
- Una frase de mensaje nunca cae por error en `OPEN_WHATSAPP`.
- `sí`, `si` y `dale` siguen sin confirmar acciones sensibles.
- `confirmar`, `confirmo` y `aceptar` siguen siendo las confirmaciones estrictas.
- Si el mensaje contiene contraseña, código, tarjeta o dato sensible detectado por `PrivacyGuard.isSafeMessagePayload`, no se crea pending de WhatsApp.
- La confirmación mantiene la frase: `No lo envío automáticamente. Confirmá para continuar.`

## Bugs corregidos
- El parser confundía frases naturales como `mandale un mensaje a un contacto en WhatsApp que estoy llegando`.
- El parser dependía demasiado de la forma exacta `mandale a un contacto: texto`.
- Variantes de voz como `wp`, `wsp`, `guasap` o `watsap` no eran tratadas como WhatsApp.
- Algunas frases con intención de mensaje podían terminar como `OPEN_WHATSAPP`.

## Tests ejecutados
- `.\gradlew.bat :androidApp:testDebugUnitTest` OK
- `.\gradlew.bat :androidApp:assembleDebug` OK
- `.\gradlew.bat :shared:allTests` OK

## Resultado APK / físico
- APK generado: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- `adb devices` no mostró `R5CW22SMWDM` durante esta pasada final.
- Solo apareció `emulator-5554`, por lo que no se instaló en físico ni se capturó logcat físico final.

## Próximos ajustes
- Probar en teléfono real con voz:
  - `mandale un mensaje a un contacto que estoy llegando`
  - `mandale un WhatsApp a un contacto que estoy llegando`
  - `escribile a un contacto por WhatsApp que estoy llegando`
  - `decile a un contacto que estoy llegando`
  - `mandale a mi novia que estoy llegando`
  - `abrí WhatsApp`
  - `confirmar`
  - `cancelar`
- Medir si SpeechRecognizer transcribe `un contacto`, `WhatsApp`, `guasap` y nombres familiares con suficiente estabilidad.
- Si el reconocedor confunde contactos frecuentes, agregar una capa local de alias aprobados por el usuario.
