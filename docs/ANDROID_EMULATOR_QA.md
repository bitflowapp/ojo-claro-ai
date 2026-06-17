# QA en Emulador Android — Ojo Claro AI

Fecha: 2026-05-04  
QA: senior Android / accesibilidad  
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

---

## 1. Entorno

| Campo | Valor |
|-------|-------|
| ADB | 1.0.41 (Version 36.0.0-13206524) |
| AVD usado | `Medium_Phone_API_36.0` |
| API Android | 36 (Android 16) |
| Imágenes del sistema | `D:\Work\Android\Sdk\system-images\android-36\google_apis_playstore\x86_64\` |
| Google Play Store | Incluido (Google APIs + Play Store) |
| Cámara emulada | Sí (`-camera-back emulated`) |
| Resolución | 1080×2400 |

---

## 2. Comandos ejecutados

```powershell
# Inicio emulador
C:\Users\marco\AppData\Local\Android\Sdk\emulator\emulator.exe -avd Medium_Phone_API_36.0 -no-snapshot-load -camera-back emulated

# Boot check
adb wait-for-device
adb shell getprop sys.boot_completed  # → 1

# Build
.\gradlew.bat :androidApp:assembleDebug  # → BUILD SUCCESSFUL (UP-TO-DATE)

# Instalación
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk  # → Success

# Lanzar app
adb shell am start -n com.ojoclaro.android/.MainActivity

# Smoke test (UI dumps para extracción de coordenadas)
adb shell uiautomator dump /sdcard/ui_home.xml

# TalkBack
adb shell settings put secure enabled_accessibility_services \
  "com.google.android.marvin.talkback/com.google.android.marvin.talkback.TalkBackService"
adb shell settings put secure accessibility_enabled 1

# OjoClaroAccessibilityService
adb shell settings put secure enabled_accessibility_services \
  "[talkback]:com.ojoclaro.android/com.ojoclaro.android.accessibility.OjoClaroAccessibilityService"

# Logcat
adb logcat -d > logs\emulator_smoke_logcat.txt

# Tests finales
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :shared:allTests
```

---

## 3. Resultado de instalación

| Paso | Resultado |
|------|-----------|
| `adb install` | `Success` |
| `assembleDebug` | `BUILD SUCCESSFUL` (82 tareas, UP-TO-DATE) |
| `testDebugUnitTest` | `BUILD SUCCESSFUL` |
| `shared:allTests` | `BUILD SUCCESSFUL` |

---

## 4. Smoke test — resultados por caso

### 4.1 — Apertura de la app

| Resultado | Detalle |
|-----------|---------|
| ✅ Sin crash | ActivityTaskManager: Displayed `.MainActivity`: +4s237ms |
| ✅ TTS conecta | `TextToSpeech: Sucessfully bound to com.google.android.tts` |
| ✅ TTS listo | `TextToSpeech: Connected to TTS engine` en ~2 s |
| ✅ Sin permisos al inicio | No dialog de permiso al abrir |
| ✅ UI correcta | Pantalla negra, botones visibles |

### 4.2 — Botón DESCRIBIR

| Resultado | Detalle |
|-----------|---------|
| ✅ Sin crash | No FATAL EXCEPTION |
| ✅ Fallback activo | App muestra: "No pude conectar con el asistente. Entendí: describir que tengo enfrente. Probá de nuevo o revisá internet." |
| ✅ Estado vuelve a IDLE | Estado muestra "Listo" tras error |
| ✅ Acentos correctos | Verificado via `adb shell cat` en UTF-8 |

### 4.3 — Botón Leer texto (cámara)

| Resultado | Detalle |
|-----------|---------|
| ✅ Diálogo de permiso | Apareció solo al tocar "Leer texto", no al iniciar la app |
| ✅ Texto correcto | "Allow Ojo Claro AI to take pictures and record video?" |
| ✅ TextScanScreen abre | CameraX se inicializa, CaptureSession abierta |
| ✅ ML Kit cargado | `libmlkit_google_ocr_pipeline.so` cargada correctamente |
| ✅ Sin texto → avisa | "No encontré texto claro." (emulador sin texto real) |
| ✅ Botón "Callar y volver" | Visible, contentDescription correcto |
| ⚠️ Cámara virtual sin texto | El emulador no muestra texto real; OCR no puede detectar nada — esperado |

### 4.4 — Botón Callar y volver (en TextScanScreen)

| Resultado | Detalle |
|-----------|---------|
| ✅ Vuelve a home | HomeScreen visible tras tap |
| ✅ Estado = Listo | Sin ProgressBar, sin spinner |
| ✅ Respuesta visible | "No encontré texto claro." con acento correcto |

### 4.5 — Botón Pedir ayuda

| Resultado | Detalle |
|-----------|---------|
| ✅ Sin crash | No FATAL EXCEPTION |
| ✅ Estado PROCESSING | Spinner + "Estado: Procesando" visibles |
| ✅ ProgressBar con desc | `content-desc="Procesando, esperá un momento."` ✓ |
| ✅ Fallback correcto | "Si estás en peligro, llamá a tu contacto de emergencia..." |
| ⚠️ Callar con texto largo | El botón Callar se desplaza hacia abajo cuando el texto de respuesta ocupa más de 2 líneas (ver nota abajo) |

### 4.6 — Botón Callar (en home)

| Resultado | Detalle |
|-----------|---------|
| ✅ Detiene TTS | Sin voz posterior |
| ✅ Vuelve a IDLE | "Estado: Listo", sin spinner |
| ⚠️ Coordenadas dinámicas | Ver nota en sección 7 |

### 4.7 — Permisos

| Permiso | Estado | Esperado |
|---------|--------|----------|
| `CAMERA` | granted=true (concedido en runtime al tocar Leer texto) | ✅ |
| `INTERNET` | granted=true | ✅ |
| `ACCESS_NETWORK_STATE` | granted=true (agregado automáticamente por librería de red) | ✅ esperado |
| `ACCESS_FINE_LOCATION` | NO declarado | ✅ |
| `RECORD_AUDIO` | NO declarado | ✅ |
| `READ_CONTACTS` | NO declarado | ✅ |

---

## 5. TalkBack en emulador

TalkBack se habilitó via ADB y se verificó funcionamiento básico.

| Check | Resultado |
|-------|-----------|
| TalkBack habilitado | ✅ `touchExplorationEnabled=true`, `Bound services: TalkBack` |
| App abre con TalkBack | ✅ Sin crash |
| `focusable="true"` en botones | ✅ DESCRIBIR, Leer texto, Pedir ayuda, Callar |
| contentDescription en nodo hijo | ✅ Todas las descripciones presentes en el árbol |
| ProgressBar contentDescription | ✅ "Procesando, esperá un momento." — verificado en vivo |
| TalkBack errors en logcat | ⚠️ `Failed to query component interface for required system resources: 6` y `attributionTag not declared` — son errores conocidos del emulador API 36, no bloquean |

**Nota sobre árbol semántico de botones (UIAutomator vs TalkBack):**

UIAutomator muestra los botones Compose con estructura de dos nodos:
```
<outer clickable=true content-desc="">
  <inner focusable=false content-desc="Describir lo que tengo enfrente.">
```

Esto es una limitación del dump de UIAutomator con Compose: no aplica el `mergeDescendants=true` de Compose al mostrar el árbol raw. TalkBack usa `AccessibilityNodeInfo` con semántica mergeada — el nodo resultante combina `clickable=true` + `contentDescription="Describir lo que tengo enfrente."`. 

**Verificación pendiente en dispositivo real:** confirmar que TalkBack anuncia el nombre correcto de cada botón al enfocarlos con deslizamiento.

---

## 6. AccessibilityService

| Check | Resultado |
|-------|-----------|
| Servicio listado | ✅ `com.ojoclaro.android` en paquetes accesibles del sistema |
| Servicio activo | ✅ `Bound services: Service[label=Ojo Claro AI, ...]` |
| Label correcto | ✅ "Ojo Claro AI" |
| feedbackType | ✅ `FEEDBACK_SPOKEN` |
| eventTypes | ✅ `TYPE_VIEW_TEXT_CHANGED, TYPE_WINDOW_STATE_CHANGED, TYPE_WINDOW_CONTENT_CHANGED` |
| capabilities | ✅ `capabilities=1` (solo `CAN_RETRIEVE_WINDOW_CONTENT`, sin gestures) |
| No taps automáticos | ✅ `onAccessibilityEvent` vacío verificado en código |
| Descripción en Ajustes | ✅ "Este permiso permite que Ojo Claro lea texto visible en pantalla para ayudarte. No guarda mensajes ni contraseñas." |
| Resumen en Ajustes | ✅ "Lee texto visible en pantalla cuando lo pedís." |

---

## 7. Errores encontrados y estado

### Errores corregidos en sesión anterior (QA 2026-05-04)

| Error | Archivo | Estado |
|-------|---------|--------|
| 8 strings sin acentos (`camara`, `encontre`, `Proba`, etc.) | `HomeViewModel.kt` | ✅ Corregido y verificado en vivo |
| `startScanning()` no emitía SpeechEvent | `HomeViewModel.kt` | ✅ Corregido |
| `CircularProgressIndicator` sin contentDescription | `HomeScreen.kt` | ✅ Corregido y verificado en vivo |
| Botón "Volver" sin contentDescription | `TextScanScreen.kt` | ✅ Corregido |

### Comportamiento observado — no es bug, sí requiere documentar

**Desplazamiento del botón Callar con texto largo:**  
Cuando la respuesta del asistente ocupa más de 2 líneas, el layout se expande y el botón Callar se mueve hacia abajo. En emulador, con coordenadas fijas, esto requiere recalcular el tap. Para usuarios de TalkBack no es un problema (navegan por foco, no por coordenadas). Para testing automatizado, se recomienda encontrar el botón por contentDescription, no por coordenadas.

### Advertencias no bloqueantes en logcat

| Warning | Origen | Impacto |
|---------|--------|---------|
| `Failed to query component interface for required system resources: 6` | TalkBack en API 36 emulador | Ninguno — bug conocido de emulador |
| `attributionTag not declared in manifest of com.google.android.marvin.talkback` | TalkBack | Ninguno — bug de TalkBack en API 36 |
| `LocalLifecycleOwner: ProvidableCompositionLocal is deprecated` | `TextScanScreen.kt` | Warning de compilación; migrar a `lifecycle-runtime-compose` en próxima iteración |
| `EmojiCompatManager: EmojiCompat is not initialized` | Google IME, no de nuestra app | Ninguno |
| `FeatureFlagsImplExport: android.xr cannot be found` | Android sistema | Ninguno — XR APIs no disponibles en emulador estándar |
| `usesCleartextTraffic="true"` | Manifest | Solo en debug; eliminar antes de producción |

---

## 8. Qué no se pudo probar en emulador

| Ítem | Por qué | Cómo verificar |
|------|---------|----------------|
| OCR con texto real | La cámara emulada no muestra texto impreso real | Dispositivo físico con papel impreso |
| TTS en español con voz real | El emulador no reproduce audio audible fácilmente | Dispositivo físico con volumen |
| TalkBack navegando por foco con deslizado real | No se puede simular confiablemente con ADB | Dispositivo físico con TalkBack |
| Apertura de WhatsApp | WhatsApp no está instalado en el emulador | Dispositivo físico con WhatsApp |
| Servicio de accesibilidad leyendo pantalla real | El emulador no tiene apps de terceros con texto interesante | Dispositivo físico |
| Tiempos de voz (latencia TTS) | Audio no reproducible en emulador headless | Dispositivo físico con auriculares |
| TalkBack a velocidad 2x/3x | No aplicable sin audio | Dispositivo físico |

---

## 9. Ruta del APK

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Fecha del APK: 2026-05-04 02:12:57  
Incluye todas las correcciones críticas de QA.

---

## 10. Riesgos pendientes tras QA en emulador

| Riesgo | Severidad | Próximo paso |
|--------|-----------|--------------|
| Foco TalkBack en botones Compose (merge semántico) | Media | Verificar en dispositivo real que cada botón anuncia su contentDescription al navegar con deslizamiento |
| OCR no testeado con texto real | Alta | Probar en dispositivo físico con texto impreso a distintas distancias |
| Callar con texto largo cambia coordenadas del botón | Baja (solo afecta automatización) | Para tests instrumentales: buscar por contentDescription, no por coordenadas |
| TTS no testeado con voz real | Alta | Probar en dispositivo físico con auriculares |
| `LocalLifecycleOwner` deprecado en TextScanScreen | Baja | Migrar a `lifecycle-runtime-compose` en próxima iteración |
| `usesCleartextTraffic="true"` en Manifest | Media (solo producción) | Eliminar o restringir con `networkSecurityConfig` antes de release |

---

## 11. Próximo paso recomendado

Instalar el APK en un dispositivo Android físico (Pixel o equivalente, API ≥ 28, WhatsApp instalado) y ejecutar el checklist completo de `docs/ANDROID_REAL_DEVICE_QA.md`.

Prioridad de verificación en dispositivo real:
1. TalkBack: que cada botón anuncie correctamente su contentDescription al navegar con deslizamiento.
2. OCR: que el texto impreso se detecte y se lea en voz alta en ≤ 2 segundos.
3. TTS en español: que los acentos se pronuncien correctamente (especialmente "cámara", "Probá", "Entendí").
4. Flujo WhatsApp completo: apertura, composición con confirmación manual.

Para activar TalkBack en dispositivo real: Ajustes > Accesibilidad > TalkBack > Activar.
