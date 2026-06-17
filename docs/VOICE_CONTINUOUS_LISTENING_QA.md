# Voice Continuous Listening QA

## Resumen ejecutivo

Se implemento una primera capa de escucha continua voice-first mientras Home esta abierta. La app ya no depende del boton Escuchar para rearmar el reconocimiento: pausa la escucha mientras habla por TTS, vuelve a escuchar al terminar, reintenta ante errores recuperables de SpeechRecognizer y mantiene confirmacion por voz para acciones pendientes.

No se agrego IA cloud, hotword en background, foreground service, envio automatico de WhatsApp ni cambios en iOS.

## Como funciona el auto-relisten

- Al entrar a Home con permiso de microfono concedido, Ojo Claro inicia escucha automaticamente.
- Cuando SpeechRecognizer entrega texto final, la escucha pasa a procesamiento y el texto entra al flujo existente del agente.
- Si el agente habla una respuesta, la escucha se pausa para no capturar la propia voz del TTS.
- Cuando TTS termina o se detiene, la escucha se rearma automaticamente si Home sigue activa.
- Si SpeechRecognizer termina por silencio, timeout, no match, busy o error cliente recuperable, se programa un reintento con backoff.
- El boton Escuchar queda como respaldo accesible, no como flujo principal.

## Estados de escucha

Estados incorporados:

- `IDLE`
- `LISTENING`
- `PROCESSING`
- `SPEAKING`
- `WAITING_RETRY`
- `STOPPED_BY_USER`
- `ERROR`

El controlador evita doble `startListening()` simultaneo y diferencia una pausa interna recuperable de una detencion explicita del usuario.

## Errores recuperables

SpeechRecognizer reintenta automaticamente ante:

- `ERROR_NO_MATCH`
- `ERROR_SPEECH_TIMEOUT`
- `ERROR_CLIENT`
- `ERROR_RECOGNIZER_BUSY`

Backoff aplicado:

- primer retry: 400 ms
- segundo retry: 800 ms
- tercero: 1200 ms
- maximo: 2000 ms

No se habla un mensaje de error en cada timeout. Despues de varios fallos consecutivos, puede emitir una frase corta: "Sigo escuchando.", evitando repetirla en loop.

## Como evita escucharse a si misma

- `SpeechController` expone callbacks de inicio, fin y detencion de habla.
- Al iniciar TTS, `VoiceCommandController` pausa SpeechRecognizer.
- Al finalizar TTS, Home reanuda la escucha si el permiso de microfono esta concedido y la pantalla sigue en estado compatible.
- Si la app entra en estados de escaneo, procesamiento o permisos, la escucha se pausa.
- Al pausar Home, la escucha se detiene.
- Al volver a Home, se reanuda si corresponde.

## Como funciona Callar

- Si SpeechRecognizer detecta "callar" en parcial o final, el dispatcher dispara corte inmediato.
- Se detiene TTS, se cancela la escucha actual y se programa una reanudacion corta.
- El boton Callar sigue disponible como respaldo accesible.
- No se procesa el resto de la frase cuando la intencion es callar.

## Confirmacion pendiente

Con acciones sensibles pendientes:

- Confirmar: `confirmar`, `confirmo`, `aceptar`.
- Cancelar: `cancelar`, `cancela`, `no`, `anular`.
- `si`, `sí` y `dale` no confirman acciones sensibles.

La seguridad existente se mantiene: WhatsApp no se envia automaticamente y las acciones sensibles pasan por confirmacion.

## Limitaciones de SpeechRecognizer

- No hay hotword permanente.
- No escucha en background.
- No hay foreground service.
- La disponibilidad y calidad de reconocimiento dependen del servicio del sistema Android.
- Durante TTS, la escucha se pausa para evitar auto-captura; por eso la interrupcion de voz depende de que "callar" sea reconocido cuando el recognizer esta activo. El boton Callar sigue como respaldo inmediato.

## Resultado de tests

Comando ejecutado:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest :androidApp:assembleDebug :shared:allTests
```

Resultado:

- `:androidApp:testDebugUnitTest` OK.
- `:androidApp:assembleDebug` OK.
- `:shared:allTests` OK.
- `BUILD SUCCESSFUL`.

Advertencias no bloqueantes:

- Kotlin Multiplatform informa compatibilidad no testeada con Android Gradle Plugin 8.7.3.
- Targets iOS se omiten en esta maquina, sin tocar iOS.

## Resultado emulador

Dispositivo disponible por ADB:

```text
emulator-5554    device
```

Instalacion:

```powershell
adb -s emulator-5554 install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Resultado:

- Instalacion OK.
- Arranque limpio con `am start -W` OK.
- Home se mostro en emulador.
- Aparecio flujo de permiso de microfono, esperado para el modo voice-first.
- Busqueda estricta de `FATAL EXCEPTION`: sin resultados.

Nota: una primera corrida justo despues de instalar dejo ruido de timeout/arranque en logcat del emulador. Se repitio con `force-stop`, logcat limpio y `am start -W`; la app arranco correctamente sin `FATAL EXCEPTION`.

## Resultado fisico

Comando ejecutado:

```powershell
adb devices
```

Resultado:

```text
List of devices attached
emulator-5554    device
```

El Samsung `R5CW22SMWDM` no estuvo conectado en esta corrida, por lo que no se pudo instalar ni validar voz real, TTS audible, TalkBack, camara u OCR fisico con esta build.

## Bugs encontrados

- No se encontraron fallos de compilacion ni tests.
- No se encontro `FATAL EXCEPTION` en el arranque limpio de emulador.
- QA fisico queda pendiente por falta de dispositivo ADB.

## Proximos pasos

1. Conectar `R5CW22SMWDM` con depuracion USB habilitada.
2. Instalar el APK actual.
3. Probar sin tocar Escuchar:
   - "que puedo decir"
   - "callar"
   - "que dice la pantalla"
   - "cancelar"
   - "mandale un mensaje a un contacto que estoy llegando"
   - "cancelar"
   - esperar 10 segundos en silencio
   - "que puedo decir"
4. Verificar que la app vuelve a escuchar sola, no repite saludo en loop, no dice "no escuche" en cada timeout y no crashea.
