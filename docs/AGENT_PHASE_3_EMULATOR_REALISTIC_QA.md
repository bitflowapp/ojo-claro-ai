# AGENT PHASE 3 EMULATOR REALISTIC QA

## 1. Resumen ejecutivo
Se pudo validar Ojo Claro en emulador como un Android real en casi toda la superficie posible sin tocar código de producto. La app instaló, abrió, respondió al arranque normal, soportó `ACTION_START_LISTENING`, mantuvo estabilidad por 60 segundos y no mostró `FATAL EXCEPTION` ni `SecurityException` de app. La limitación más fuerte del emulador fue la voz real y la automatización de `uiautomator` en reintentos, no la app.

## 2. Emulador usado
- AVD: `Medium_Phone_API_36.0`
- Dispositivo visible: `emulator-5554`

## 3. Android/API
- API objetivo del AVD: 36
- Ejecución observada sobre emulador Android 16/preview de API 36

## 4. Resultados de builds
- `:androidApp:testDebugUnitTest` verde
- `:androidApp:assembleDebug` verde
- `:shared:allTests` verde

## 5. Permisos verificados
- Presente: `android.permission.ACCESS_FINE_LOCATION`
- Presente: `android.permission.ACCESS_COARSE_LOCATION`
- Presente: `android.permission.RECORD_AUDIO`
- No se agregó `android.permission.ACCESS_BACKGROUND_LOCATION`
- No se agregó `android.permission.CALL_PHONE`
- No se agregó `android.permission.READ_CONTACTS`

## 6. Confirmación de NO ACCESS_BACKGROUND_LOCATION
Confirmado en `AndroidManifest.xml` y en la validación por `Select-String`: no aparece `ACCESS_BACKGROUND_LOCATION`.

## 7. Instalación APK
- APK instalada con éxito en `emulator-5554`
- Ruta: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`

## 8. Arranque normal
- `adb shell am start -W -n com.ojoclaro.android/.MainActivity`
- Resultado: la app abrió sin crash
- TTS se enlazó correctamente
- No apareció `FATAL EXCEPTION` de la app en ese arranque

## 9. Arranque `ACTION_START_LISTENING`
- `adb shell am start -W -a com.ojoclaro.android.ACTION_START_LISTENING --ez start_listening true -n com.ojoclaro.android/.MainActivity`
- Resultado: la app siguió estable
- Logcat mostró `RecognitionService` y `locale: es-AR`
- Hubo errores esperables del emulador por falta de voz real / pack offline, pero no crash de app

## 10. UI dump / accesibilidad
- El dump inicial expuso:
  - `Ojo Claro AI`
  - `Escuchando`
  - estado visible de respuesta
  - `DESCRIBIR`
  - `Leer texto`
  - `¿Qué puedo decir?`
  - `Callar`
- También apareció un tutorial accesible al tocar ayuda:
  - `Bienvenida a Ojo Claro`
  - `SIGUIENTE`
  - `Repetir explicación`
  - `Saltar`
- Conclusión: la Home no está muerta y los controles principales son accesibles por texto/descripcion.

## 11. Resultado logcat
- No se observó `FATAL EXCEPTION` de la app.
- No se observó `AndroidRuntime` fatal de la app.
- No se observó `SecurityException` de ubicación/maps propia de la app.
- Sí aparecieron mensajes del sistema/emulador:
  - `RecognitionService` con `es-AR`
  - `NO_SPEECH_DETECTED` por falta de entrada real de micrófono
  - errores de `uiautomator` al reintentar dumps, propios del servicio de automatización
- Esos mensajes no indicaron crash de `com.ojoclaro.android`.

## 12. Resultado Maps / ubicación
- `com.google.android.apps.maps` está instalado en el emulador.
- `adb emu geo fix -68.0591 -38.9516` respondió `OK`.
- `dumpsys location` mostró el proveedor GPS con última ubicación y actividad de providers.
- No se pudo validar navegación por voz real en emulador, pero la ruta de ubicación quedó simulada.

## 13. Resultado permisos
- Se concedieron:
  - `RECORD_AUDIO`
  - `ACCESS_FINE_LOCATION`
  - `ACCESS_COARSE_LOCATION`
- No fue necesario conceder permisos peligrosos adicionales.

## 14. Resultado voice loop en emulador
- La sesión de `ACTION_START_LISTENING` se mantuvo 60 segundos.
- Logcat mostró `RecognitionService#onStartListening`, `locale: es-AR`, `onStartOfSpeech` y eventos de cancelación/no speech esperables del emulador.
- No hubo loop de crash ni spam fatal de la app.

## 15. Qué no se pudo probar por limitación del emulador
- Voz real confiable con interpretación de comandos completos.
- Confirmación por voz con interacción humana real.
- Navegación completa por Maps como usuario final.
- Validación práctica de WhatsApp y llamadas por habla en tiempo real.
- Dump UI repetido en paralelo sin que `uiautomator` choque con el servicio ya registrado.

## 16. Qué queda para Android físico mañana
- `dónde estoy`
- `abrí mapas`
- `llevame a casa`
- `cómo llego a la farmacia`
- `guardá esta ubicación como casa`
- `callar`
- `qué puedo decir`
- validar permisos y respuesta hablada con una persona real usando TalkBack

## 17. Veredicto
- Lista para prueba física: sí
- Lista para demo controlada: sí

## Archivos generados durante la QA
- `logs/window.xml`
- `logs/window_after_callar.xml`
- `logs/window_after_skip.xml`
- `logs/ojo_claro_ui.xml`
- `logs/ojo_claro_help_after_tap.xml`
- `logs/ojo_claro_home_after_skip.xml`

## APK
- `androidApp\build\outputs\apk\debug\androidApp-debug.apk`
