# Android Emulator Agent QA - Ojo Claro AI

Fecha: 2026-05-05

## 1. Dispositivo / Emulador usado

- Emulador: `emulator-5554`
- AVD: `Medium_Phone_API_36.0`
- Android / API: Android 16 Preview, API 36
- Estado AVD: arrancó correctamente desde `C:\Users\marco\AppData\Local\Android\Sdk\emulator\emulator.exe`

## 2. APK instalado

- APK: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- Instalación: `adb -s emulator-5554 install -r ...`
- Resultado: `Success`

## 3. Resultado build / tests

- `.\gradlew.bat :androidApp:assembleDebug` -> OK
- `.\gradlew.bat :androidApp:testDebugUnitTest` -> OK
- `.\gradlew.bat :shared:allTests` -> OK
- Revalidación después del smoke -> OK

## 4. Resultado logcat

- Se limpió logcat con `adb -s emulator-5554 logcat -c`
- Se abrió `MainActivity` con `adb -s emulator-5554 shell am start -n com.ojoclaro.android/.MainActivity`
- Búsqueda de errores con `adb -s emulator-5554 logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro"`
- Resultado: no apareció `FATAL EXCEPTION` del paquete `com.ojoclaro.android`
- Se observaron logs normales del sistema y del arranque del emulador, pero nada que indicara crash de la app

## 5. Flujos probados

- Apertura limpia de la app tras `pm clear`
- Onboarding visible en primer arranque
- Salto del tutorial con `Saltar`
- Home visible después del tutorial
- Árbol accesible con botones principales visibles
- `¿Qué puedo decir?` expuesto con `contentDescription`
- `Callar` expuesto con `contentDescription`
- `Leer texto` expuesto con `contentDescription`
- `DESCRIBIR` expuesto con `contentDescription`
- Intento de habilitar el servicio de accesibilidad de Ojo Claro por `adb`

## 6. Resultado de comandos y smoke

- `adb devices` mostró `emulator-5554 device`
- `emulator -list-avds` no estaba disponible en PATH, pero el binario sí existía en el SDK local
- `Medium_Phone_API_36.0` arrancó bien
- `Qué puedo decir` no produjo crash ni rompió la UI
- `Callar` no produjo crash ni rompió la UI
- La app volvió a mostrar Home correctamente luego de cerrar el onboarding

## 7. Resultado accesibilidad / TalkBack

- Los botones principales tienen `contentDescription` claros:
  - `Describir lo que tengo enfrente.`
  - `Leer texto con la cámara.`
  - `Escuchar ejemplos de comandos disponibles.`
  - `Callar la voz.`
- El orden del árbol accesible es razonable: título, estado, respuesta, acciones, Callar
- El intento de activar accesibilidad por `adb` no quedó persistente; el estado final consultado volvió a `accessibility_enabled=0` y `enabled_accessibility_services=null`
- TalkBack del sistema no se validó como flujo completo en este emulador

## 8. Bugs encontrados

- No encontré crash real de la app en este QA
- No encontré bloqueo de apertura
- No encontré regressión visible en la UI principal

## 9. Bugs corregidos

- Ninguno en esta ronda de QA

## 10. Qué no se pudo probar en emulador

- Confirmación y cancelación por voz
- Memoria local por frase hablada:
  - `recordá que prefiero respuestas cortas`
  - `confirmar`
  - `qué recordás de mí`
  - `borrá tu memoria`
  - `confirmar`
- Lectura real de pantalla por comando de voz
- `WhatsApp` desde flujo de voz extremo a extremo
- Loop de voz real con TTS audible en emulador
- TalkBack completo de usuario final

## 11. Checklist para Android físico

- Validar TTS audible
- Validar `Callar` mientras la app está hablando
- Validar `Qué dice la pantalla` con consentimiento
- Validar `mandale a Sofi: estoy llegando` y su confirmación
- Validar memoria local con voz real
- Validar `qué recordás de mí`
- Validar `borrá tu memoria`
- Validar lectura de OCR local con riesgo de transferencia/código
- Validar TalkBack real del sistema

## 12. Riesgos pendientes

- La memoria y los patrones ya no rompen el arranque, pero falta validación con voz real
- El emulador no es buen entorno para medir TTS y TalkBack de uso real
- Falta prueba humana de los flujos de confirmación
- La detección de riesgos es heurística y puede necesitar ajuste con casos reales
- La activación de accesibilidad por `adb` no se mantuvo, así que el paso confiable sigue siendo hacerlo manualmente en Ajustes

## 13. Próximo paso recomendado

Hacer una pasada en dispositivo físico con TalkBack y TTS activos, enfocada en:

1. Apertura
2. `Callar`
3. `Qué puedo decir`
4. Consentimiento de lectura visible
5. Memoria local
6. Detección de riesgos
