# QA Final en Emulador — Ojo Claro AI

Fecha: 2026-05-04 (MVP)
Actualización: 2026-05-04 (post refactor AI-Ready)  
Emulador: `Medium_Phone_API_36.0` — Android 16 (API 36), Google Play Store, x86_64  
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`  
Logcat: `logs/emulator_final_qa_logcat.txt` (267 KB)

---

## Comandos ejecutados

```powershell
adb devices -l
adb -s emulator-5554 install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n com.ojoclaro.android/.MainActivity
# Smoke test via uiautomator dump + input tap
adb -s emulator-5554 shell settings put secure enabled_accessibility_services "...talkback..."
adb -s emulator-5554 logcat -d > logs\emulator_final_qa_logcat.txt
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :shared:allTests
```

---

## Resultado de instalación

| Paso | Resultado |
|------|-----------|
| `adb install -r` | `Success` |
| APK instala limpio | ✅ |

---

## Resultado de smoke test

### Sin TalkBack

| Prueba | Resultado | Detalle |
|--------|-----------|---------|
| Arranque sin crash | ✅ | `Displayed .MainActivity` — sin FATAL EXCEPTION |
| TTS conecta | ✅ | `Sucessfully bound to com.google.android.tts` |
| DESCRIBIR → fallback | ✅ | "No pude conectar con el asistente. Entendí: describir que tengo enfrente. Probá de nuevo o revisá internet." |
| Acentos correctos (UTF-8) | ✅ | Verificados leyendo XML directo del emulador |
| Leer texto → TextScanScreen | ✅ | CameraX abre, CaptureSession configurada |
| Cámara activa | ✅ | 12 entradas de CameraX en logcat |
| Callar y volver → home | ✅ | Estado "Listo", textos correctos |
| Pedir ayuda → fallback | ✅ | "Si estás en peligro, llamá a tu contacto de emergencia..." |
| Callar → IDLE | ✅ | Sin ProgressBar, sin crash |
| FATAL EXCEPTION | ✅ Ninguno | 0 ocurrencias en 267 KB de logcat |

### Con TalkBack activo (`touchExplorationEnabled=true`)

| Check | Resultado |
|-------|-----------|
| App abre sin crash | ✅ |
| DESCRIBIR — contentDescription | ✅ "Describir lo que tengo enfrente." en (540,834) |
| Leer texto — contentDescription | ✅ "Leer texto con la camara." en (540,1092) |
| Pedir ayuda — contentDescription | ✅ "Pedir ayuda." en (540,1328) |
| Callar — contentDescription | ✅ "Callar la voz." en (540,1564) |
| Estado "Listo" | ✅ `content-desc="Estado: Listo"` |
| ProgressBar en procesamiento | ✅ `content-desc="Procesando, esperá un momento."` (verificado en sesión anterior) |
| TalkBack errors conocidos | ⚠️ `Failed to query component interface` y `attributionTag` — bug conocido API 36, no bloquean |

---

## Resultado de builds y tests

```
.\gradlew.bat :androidApp:assembleDebug   → BUILD SUCCESSFUL (82 tareas, UP-TO-DATE)
.\gradlew.bat :androidApp:testDebugUnitTest → BUILD SUCCESSFUL
.\gradlew.bat :shared:allTests             → BUILD SUCCESSFUL
```

---

## Ruta del APK

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

---

## Notas de comportamiento observado

**Desplazamiento de botones con texto largo:**  
Cuando la respuesta ocupa más de 1 línea (ej: fallback de backend), los botones se desplazan hacia abajo. Para smoke tests por coordenadas, conviene tomar un dump fresco antes de cada tap con `uiautomator dump` y parsear las coordenadas. Para TalkBack o dispositivo real esto no es un problema.

**UIAutomator y Compose sobre CameraX:**  
En `TextScanScreen`, UIAutomator reporta 0 botones clickables porque la `TextureView` de CameraX cubre el área. El botón "Callar y volver" sí funciona (confirmado en 2 sesiones en y≈2252). Este es un límite conocido de UIAutomator con vistas AndroidView de Compose, no un bug de la app.

---

## Resultado post refactor AI-Ready (2026-05-04)

```
.\gradlew.bat :androidApp:assembleDebug        → BUILD SUCCESSFUL
.\gradlew.bat :androidApp:testDebugUnitTest    → BUILD SUCCESSFUL (62 tests, 0 failed)
.\gradlew.bat :shared:allTests                 → BUILD SUCCESSFUL
```

Emulador no disponible en esta sesión. Smoke test en dispositivo físico pendiente.

Cambios que requieren re-validación en emulador:
- Flujo de onboarding (primera apertura → voz habla los pasos).
- Botón "¿Qué puedo decir?" (antes "Pedir ayuda") → respuesta local con comandos.
- Estado "Esperando confirmación" en statusText cuando se pide componer mensaje.
- Saludo inicial en HomeScreen (requiere TTS audible).

---

## Qué queda pendiente para dispositivo físico

| Ítem | Por qué no se puede probar en emulador |
|------|----------------------------------------|
| OCR con texto impreso real | La cámara emulada no tiene escena con texto |
| TTS en español escuchado | El emulador no reproduce audio audible |
| TalkBack con gestos reales | No se puede simular deslizamiento táctil fiablemente con ADB |
| WhatsApp (apertura + composición) | No está instalado en el emulador |
| AccessibilityService leyendo WhatsApp | Requiere WhatsApp instalado y una conversación abierta |
| Pronunciación de acentos (cámara, Probá, Entendí) | Requiere audio real |
| Flujo de activación del servicio en Ajustes | Requiere interacción manual en Settings |
