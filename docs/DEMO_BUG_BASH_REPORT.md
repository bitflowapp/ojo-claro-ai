# Demo Bug Bash Report

## 1. Resumen ejecutivo

Se hizo una ronda de bug bash enfocada en fallos que podrian arruinar una demo real de Ojo Claro AI: escucha continua, TTS, Callar, auto-relisten, permisos, WhatsApp seguro, intents de activacion rapida, UI accesible y estabilidad de arranque.

Resultado: se encontraron y corrigieron bugs reales de estabilidad/fluidez. El APK compila, los tests pasan, abre en emulador, Home queda en estado `Escuchando` despues de conceder microfono y no se detecto `FATAL EXCEPTION`.

## 2. Si se toco codigo o no

Si se toco codigo, solo para corregir bugs reales:

- Evitar doble procesamiento de comandos no externos.
- Evitar que SpeechRecognizer se reactive mientras TTS esta hablando.
- Manejar resultados finales vacios de SpeechRecognizer como `ERROR_NO_MATCH`.
- Hacer `cancel()` idempotente en SpeechRecognizer para cortar sesiones tardias.
- Liberar el voice loop si TextToSpeech falla al inicializar.

No se agregaron features nuevas, cloud IA, BiometricPrompt, hotword en background, foreground service ni cambios iOS.

## 3. Bugs demo-killer buscados

- Crash al abrir la app.
- Escucha muerta despues de un comando.
- Saludo repetido en loop.
- Respuestas duplicadas.
- Agente escuchandose a si mismo durante TTS.
- Microfono trabado.
- Necesidad de tocar Escuchar despues de cada comando.
- Falta de respuesta ante comandos no entendidos.
- WhatsApp abierto cuando faltan contacto o mensaje.
- WhatsApp preparado sin confirmacion.
- Confirmacion accidental con `si`, `sí` o `dale`.
- Pending sensible colgado.
- Falla fea si falta microfono, camara, WhatsApp, TTS o accesibilidad.
- ContentDescription faltante en controles criticos.
- Spam grave en logcat.
- Callar lento o roto.

## 4. Bugs encontrados

### Bug 1: comandos no externos podian disparar doble flujo

`HomeViewModel` lanzaba el `AssistantOrchestrator` antes de saber si el texto era realmente un comando externo. Una frase no reconocida podia terminar con dos respuestas compitiendo: una del orquestador local y otra del fallback/backend.

Impacto demo: respuesta duplicada, estado visual raro o TTS pisandose.

### Bug 2: error tardio de SpeechRecognizer podia reactivar el microfono durante TTS

Al pausar la escucha para hablar, Android puede devolver un error recuperable por la cancelacion. El controlador podia tomar ese error como un timeout normal y programar retry, abriendo el mic mientras la app hablaba.

Impacto demo: el agente se escucha a si mismo o entra en loop.

### Bug 3: resultados finales vacios dejaban la escucha sin recuperacion clara

`AndroidSpeechInputEngine.onResults()` no notificaba nada si el bundle venia sin texto. El controlador podia quedar creyendo que seguia escuchando sin recibir final ni error.

Impacto demo: escucha muerta hasta tocar Escuchar.

### Bug 4: `stopListening()` podia no cancelar una sesion tardia

Despues de `onEndOfSpeech`, el flag interno podia estar en falso aunque SpeechRecognizer aun no hubiera entregado resultado final. Si justo ahi arrancaba TTS, `stopListening()` no llamaba `cancel()`.

Impacto demo: final tardio procesado durante TTS.

### Bug 5: si TTS falla al inicializar, el voice loop podia quedar esperando

Si TextToSpeech no inicializaba correctamente, no habia callback de fin de habla para reanudar escucha.

Impacto demo: app abierta pero sin volver a escuchar.

## 5. Bugs corregidos

- Se agrego una compuerta pura `shouldHandleExternalCommand()` para que solo comandos externos o consentimientos pendientes entren al orquestador externo.
- `VoiceCommandController` ahora marca estados de pausa antes de cancelar SpeechRecognizer y bloquea retries cuando el estado es `SPEAKING`, `PROCESSING`, `IDLE` o `STOPPED_BY_USER`.
- `AndroidSpeechInputEngine` ahora transforma finales vacios en `ERROR_NO_MATCH`.
- `AndroidSpeechInputEngine.stopListening()` cancela siempre de forma idempotente.
- `AndroidSpeechInputEngine.startListening()`, `cancel()` y `destroy()` quedan protegidos con `runCatching`.
- `SpeechController` llama `onSpeechFinished()` si TTS no inicializa, para no dejar la escucha congelada.

## 6. Archivos modificados

- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/VoiceCommandController.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/AndroidSpeechInputEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/speech/SpeechController.kt`
- `androidApp/src/test/java/com/ojoclaro/android/ui/home/HomeViewModelExternalRoutingTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/voice/VoiceCommandControllerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/voice/SpeechResultDispatchTest.kt`
- `docs/DEMO_BUG_BASH_REPORT.md`

Nota: `git status` y `git diff --stat` no pudieron ejecutarse porque esta carpeta no contiene metadata `.git`.

## 7. Tests ejecutados

Baseline y validacion final:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:allTests
```

Tambien se ejecuto la variante compacta final:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest :androidApp:assembleDebug :shared:allTests
```

Resultado final:

- `:androidApp:testDebugUnitTest` OK.
- `:androidApp:assembleDebug` OK.
- `:shared:allTests` OK.
- `BUILD SUCCESSFUL`.

Advertencias no bloqueantes:

- Kotlin Multiplatform informa que AGP 8.7.3 esta por encima del maximo testeado por el plugin Kotlin.
- Targets iOS se omiten en esta maquina. No se toco iOS.

## 8. Resultado emulador

Dispositivo ADB:

```text
emulator-5554    device
```

Instalacion:

```powershell
adb -s emulator-5554 install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Resultado: `Success`.

Cold start limpio:

```powershell
adb -s emulator-5554 shell pm clear com.ojoclaro.android
adb -s emulator-5554 shell am start -W -n com.ojoclaro.android/.MainActivity
```

Resultado:

- `Status: ok`
- `LaunchState: COLD`
- `TotalTime: 3011 ms`
- App abre sin crash.
- Onboarding aparece despues de `pm clear`, esperado.
- Tras saltar onboarding, aparece permiso de microfono.
- Tras conceder microfono, Home queda visible en estado `Escuchando`.

Intent de escucha:

```powershell
adb -s emulator-5554 shell am start -W -a com.ojoclaro.android.ACTION_START_LISTENING --ez start_listening true -n com.ojoclaro.android/.MainActivity
```

Resultado:

- Intent entregado a la instancia visible.
- Sin crash.
- No inicia microfono en background; opera con MainActivity visible.

UI dump:

- Se verifico Home con:
  - `Ojo Claro AI`
  - estado `Escuchando`
  - respuesta del agente
  - `DESCRIBIR`
  - `Leer texto`
  - `¿Qué puedo decir?`
  - `Callar`
- Los controles criticos tienen `contentDescription` accesible en el dump.

## 9. Resultado fisico

No se pudo ejecutar QA fisico en esta ronda.

`adb devices` mostro solamente:

```text
emulator-5554    device
```

El Samsung `R5CW22SMWDM` no estuvo conectado.

## 10. Resultado logcat

Busqueda estricta:

```powershell
adb -s emulator-5554 logcat -d | Select-String -Pattern 'FATAL EXCEPTION'
```

Resultado:

- Sin resultados.
- `0 FATAL EXCEPTION`.

Busqueda amplia solicitada:

```powershell
adb -s emulator-5554 logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro SpeechRecognizer RecognitionService TextToSpeech Binder Exception"
```

Observaciones:

- Aparecio ruido del sistema/emulador: `BluetoothPowerStatsCollector`, `GoogleInputMethodService`, `TextToSpeech`, `BpBinder`.
- No se observo crash de `com.ojoclaro.android`.
- No se observo `AndroidRuntime` fatal de la app.
- No se observo loop evidente de saludo en el dump de Home.

## 11. Ruta APK final

```text
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Tamaño observado:

```text
57368695 bytes
```

## 12. Veredicto

Lista para demo: si, con condiciones.

Condiciones:

- Hacer una prueba fisica hablada antes de mostrarsela a otra persona.
- Usar un telefono con TTS funcionando y SpeechRecognizer disponible.
- Conceder microfono antes de la demo.
- Tener WhatsApp instalado solo si se va a mostrar el flujo de WhatsApp.
- No demostrar OCR con texto critico o privado.
- No mostrar automatizaciones de accesibilidad que todavia no existen.

## 13. Checklist para mostrar la demo

1. Instalar el APK final.
2. Abrir Ojo Claro.
3. Saltar onboarding si ya no se quiere mostrar.
4. Conceder microfono.
5. Esperar el saludo.
6. Sin tocar Escuchar, decir: `qué puedo decir`.
7. Esperar respuesta.
8. Decir: `callar`.
9. Decir: `mandale un mensaje a Sofi que estoy llegando`.
10. Verificar que pide confirmacion y dice que no envia automaticamente.
11. Decir: `cancelar`.
12. Decir: `mandale a Sofi`.
13. Verificar que pregunta que mensaje queres mandarle.
14. Decir: `mandale un mensaje`.
15. Verificar que pregunta a quien.
16. Crear un pending y decir: `sí`.
17. Verificar que no confirma.
18. Decir: `confirmar` solo cuando se quiera abrir WhatsApp con mensaje preparado.
19. Verificar manualmente que WhatsApp no envia el mensaje solo.
20. Capturar logcat final y confirmar `0 FATAL EXCEPTION`.

## 14. Cosas que no conviene mostrar todavia

- Hotword en background: no existe por decision de seguridad.
- Envio automatico de WhatsApp: no existe y no debe prometerse.
- Biometria/BiometricPrompt: no implementado todavia.
- Control automatico de gestos de AccessibilityService: no implementado todavia.
- Lectura de contrasenas, codigos 2FA o tarjetas: esta bloqueada por seguridad.
- OCR sobre documentos privados reales en demo publica.
- Funcionamiento de voz en ambientes con mucho ruido sin prueba previa.
- QA fisico completo con TalkBack/OCR/camara/WhatsApp real: pendiente en esta ronda porque no estaba conectado el dispositivo fisico.
