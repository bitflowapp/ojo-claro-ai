# Voice-First Agent Plan

## Objetivo
Ojo Claro AI pasa a ser voice-first: la interacción principal es hablarle a la app. Los botones siguen existiendo como respaldo accesible con TalkBack.

## Cómo funciona el control por voz
- `HomeScreen` crea un `VoiceCommandController`.
- `VoiceCommandController` usa un `SpeechInputEngine`.
- En Android real, `AndroidSpeechInputEngine` usa `SpeechRecognizer` del sistema.
- En tests, `FakeSpeechInputEngine` permite validar el flujo sin grabar audio ni depender del framework.
- El texto final reconocido entra por `HomeViewModel.onVoiceFinalText`.
- `HomeViewModel` manda el comando al flujo existente:
  `AssistantOrchestrator.process(rawInput = recognizedText, ...)`.
- No se creó un cerebro paralelo para voz.

## Comandos soportados
- `qué puedo decir`
- `callar`
- `cancelar`
- `confirmar`
- `leer texto`
- `qué dice la pantalla`
- `leeme este mensaje`
- `abrí WhatsApp`
- `mandale a Sofi: estoy llegando`
- `recordá que prefiero respuestas cortas`
- `qué recordás de mí`
- `borrá tu memoria`

## Prioridad de Callar
- `callar` se detecta en texto parcial y final.
- Corta TTS inmediatamente.
- Cancela la escucha actual.
- Limpia acciones pendientes desde `HomeViewModel.onStopSpeechRequested`.
- No confirma acciones sensibles.

## Confirmación por voz
- Solo `confirmar`, `confirmo` y `aceptar` confirman.
- `sí`, `si` y `dale` no confirman acciones sensibles.
- `cancelar` cancela acciones pendientes.
- Las acciones sensibles siguen pasando por `ConsentManager`.

## Botones como fallback
- Los botones existentes siguen disponibles:
  - `DESCRIBIR`
  - `Leer texto`
  - `¿Qué puedo decir?`
  - `Callar`
- Se agrega un texto accesible en Home:
  `Ojo Claro funciona principalmente por voz. También podés usar estos botones con TalkBack.`
- Si falta permiso de micrófono, se ofrece `Activar voz`.
- Si el reconocimiento se corta por error o timeout, queda disponible `Escuchar`.

## Privacidad del audio
- No se guarda audio.
- No se sube audio.
- No hay hotword permanente.
- No se escucha en background.
- El reconocimiento usa el servicio estándar del sistema Android.
- La app pide `RECORD_AUDIO` solo para usar control por voz.
- Si falta permiso, responde:
  `Para usar Ojo Claro por voz, activá el micrófono. No guardo audio.`

## Límites de Android SpeechRecognizer
- Depende del motor de reconocimiento instalado en el teléfono.
- Puede requerir servicios del sistema o conectividad según la configuración del dispositivo.
- Puede devolver errores como timeout, no-match o recognizer busy.
- No garantiza escucha continua indefinida.
- En esta primera capa, no hay wake word ni escucha fuera de la app.

## Futuro hotword
Para un hotword seguro falta:
- definir palabra de activación;
- evaluar consumo de batería;
- revisar permisos y política de background;
- diseñar indicador claro de escucha;
- confirmar consentimiento explícito del usuario;
- validar privacidad con usuarios reales.

## Checklist de QA físico
- Instalar APK actual.
- Abrir app.
- Aceptar micrófono.
- Escuchar saludo: `Ojo Claro listo. Decime qué necesitás.`
- Decir `qué puedo decir`.
- Decir `callar` durante una respuesta larga.
- Decir `leer texto` y probar OCR con papel real.
- Decir `qué dice la pantalla` y verificar consentimiento.
- Decir `confirmar` y `cancelar`.
- Decir `recordá que prefiero respuestas cortas`.
- Decir `confirmar`.
- Decir `qué recordás de mí`.
- Decir `borrá tu memoria`.
- Decir `confirmar`.
- Decir `mandale a Sofi: estoy llegando`.
- Verificar que WhatsApp no envía automáticamente.
- Capturar logcat y verificar 0 `FATAL EXCEPTION`.

## Próximos pasos recomendados
- QA real con TalkBack y TTS audible en el dispositivo físico.
- Ajustar frases si SpeechRecognizer confunde comandos frecuentes.
- Medir latencia de `callar`.
- Evaluar si conviene un botón físico o gesto accesible para activar escucha en una fase futura.
