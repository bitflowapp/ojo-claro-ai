# Smoke Test en Emulador — Refactor AI-Ready

Fecha: 2026-05-05  
Emulador: `Medium_Phone_API_36.0` — Android 16 (API 36), Google Play Store, x86_64  
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`  
Logcat: `logs/emulator_ai_ready_smoke_logcat.txt` (4439 líneas)

---

## Bug crítico encontrado y corregido durante este smoke test

**`HomeViewModel` no podía instanciarse con el factory por defecto de AndroidViewModel.**

Causa: El refactor AI-Ready cambió el constructor de `HomeViewModel` de `(Application)` a `(Application, CapabilityRegistry, AssistantOrchestrator, CommandParser, AssistantApi)` con defaults. El `AndroidViewModelFactory` de Lifecycle usa reflexión para buscar un constructor `(Application)` exacto — los parámetros adicionales (aunque tengan defaults) lo rompen.

Síntoma: crash inmediato al mostrar `HomeScreen`:
```
FATAL EXCEPTION: Cannot create an instance of class HomeViewModel
at ViewModelProvider$AndroidViewModelFactory.create
at HomeScreen.kt:280 (viewModel())
```

Fix aplicado en este smoke test:
- **`HomeScreen.kt`**: reemplazado `viewModel()` por `viewModel(factory = object : ViewModelProvider.Factory { ... })` con creación explícita `HomeViewModel(context.applicationContext as Application)`.
- **`HomeViewModel.kt`**: agregado `companion object { class Factory(...) }` (disponible para uso futuro desde OjoClaroApp).
- Resultado: 0 crashes en logcat post-fix, todos los tests siguen pasando.

---

## Comandos ejecutados

```powershell
# Emulador
emulator -avd Medium_Phone_API_36.0 -no-audio -no-snapshot-load &
adb devices -l

# Build e instalación
.\gradlew.bat :androidApp:assembleDebug
adb -s emulator-5554 install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk

# Lanzamiento
adb -s emulator-5554 logcat -c
adb -s emulator-5554 shell am start -n com.ojoclaro.android/.MainActivity

# Dump de accesibilidad
adb -s emulator-5554 shell "uiautomator dump /sdcard/ojo_ui.xml"
adb -s emulator-5554 pull //sdcard/ojo_ui.xml logs/ojo_ui.xml

# Logcat final
adb -s emulator-5554 shell "logcat -d" > logs\emulator_ai_ready_smoke_logcat.txt

# Builds y tests finales
.\gradlew.bat :androidApp:assembleDebug :androidApp:testDebugUnitTest :shared:allTests
```

---

## Resultado de builds y tests

```
.\gradlew.bat :androidApp:assembleDebug        → BUILD SUCCESSFUL
.\gradlew.bat :androidApp:testDebugUnitTest    → BUILD SUCCESSFUL (62 tests, 0 failed)
.\gradlew.bat :shared:allTests                 → BUILD SUCCESSFUL
```

---

## Smoke test sin TalkBack

| Prueba | Resultado | Detalle |
|--------|-----------|---------|
| Arranque sin crash | ✅ | `Displayed .MainActivity` en logcat, sin FATAL EXCEPTION |
| TTS conecta | ✅ | `Sucessfully bound to com.google.android.tts` |
| TTS en español | ✅ | `currentLocale = es-US`, `spa-USA` |
| Onboarding — pantalla visible | ✅ | `text="Bienvenida a Ojo Claro"`, pasos correctos |
| Onboarding — SIGUIENTE | ✅ | `content-desc="Siguiente paso del tutorial."` |
| Onboarding — Repetir explicación | ✅ | `content-desc="Repetir explicación."` |
| Onboarding — Saltar | ✅ | `content-desc="Saltar tutorial."` |
| HomeScreen post-onboarding | ✅ | Transición sin crash tras tap en Saltar |
| Estado "Listo" | ✅ | `content-desc="Estado: Listo"` visible |
| DESCRIBIR visible | ✅ | `content-desc="Describir lo que tengo enfrente."` |
| Leer texto visible | ✅ | `content-desc="Leer texto con la camara."` |
| ¿Qué puedo decir? visible | ✅ | `content-desc="Escuchar ejemplos de comandos disponibles."` |
| Callar visible | ✅ | `content-desc="Callar la voz."` |
| Respuesta inicial | ✅ | `"Listo. Tocá DESCRIBIR, Leer texto o Pedir ayuda."` |
| DESCRIBIR → IA local | ✅ | `"Para describir lo que ves, todavía necesito IA avanzada que no está activada."` |
| ProgressBar en procesamiento | ✅ | `content-desc="Procesando, esperá un momento."` visible durante DESCRIBIR |
| Leer texto → TextScanScreen | ✅ | `text="Leyendo texto"`, CameraX activa |
| TextScanScreen — Callar y volver | ✅ | `content-desc="Callar la voz y volver a la pantalla principal."` en y≈2252 |
| OCR → vuelve con resultado | ✅ | `"No encontré texto claro."` (correcto: emulador sin escena con texto) |
| ¿Qué puedo decir? → ayuda | ✅ | Respuesta con frases de VoiceHelpCenter completa |
| Callar → IDLE | ✅ | `content-desc="Estado: Listo"` tras tap Callar |
| Acentos UTF-8 | ✅ | `"Podés"`, `"todavía"`, `"está"`, `"encontré"` todos correctos en contentDesc |
| FATAL EXCEPTION post-fix | ✅ Ninguno | 0 ocurrencias en 4439 líneas de logcat |

---

## Checks de accesibilidad (contentDescriptions verificados)

| Elemento | contentDescription |
|----------|-------------------|
| Estado de la app | `"Estado: Listo"` |
| ProgressBar | `"Procesando, esperá un momento."` |
| Respuesta | `"Respuesta: <texto>"` |
| Botón DESCRIBIR | `"Describir lo que tengo enfrente."` |
| Botón Leer texto | `"Leer texto con la camara."` |
| Botón ¿Qué puedo decir? | `"Escuchar ejemplos de comandos disponibles."` |
| Botón Callar (home) | `"Callar la voz."` |
| Botón Callar y volver (cámara) | `"Callar la voz y volver a la pantalla principal."` |
| Onboarding SIGUIENTE | `"Siguiente paso del tutorial."` |
| Onboarding Repetir | `"Repetir explicación."` |
| Onboarding Saltar | `"Saltar tutorial."` |

---

## Observaciones de comportamiento

**Desplazamiento de botones con texto largo:**
Confirmado nuevamente. Cuando la respuesta ocupa múltiples líneas (ej.: lista de comandos de VoiceHelpCenter), los botones se desplazan hacia abajo. Para tests por coordenadas ADB, tomar dump fresco antes de cada tap. Para usuarios reales con TalkBack o dedo, esto no es problema.

**UIAutomator y CameraX:**
En `TextScanScreen`, la `TextureView` de CameraX cubre el área y UIAutomator reporta 0 botones clickables. El botón "Callar y volver" sí funciona (confirmado, y≈2252). Mismo comportamiento que en QA anterior — no es un bug.

**TalkBack activo al inicio de sesión:**
TalkBack quedó habilitado de una sesión anterior. Se deshabilitó vía `adb shell settings put secure enabled_accessibility_services ''` para poder interactuar con coordenadas normales. El diálogo de notificaciones de Android Accessibility Suite que aparecía en foreground es un diálogo del sistema (`com.android.permissioncontroller`), no de la app.

**HomeViewModel Factory:**
Ver sección "Bug crítico encontrado y corregido". Fix documentado en `HomeScreen.kt` y `HomeViewModel.kt`.

---

## Resultado de builds post-fix

```
.\gradlew.bat :androidApp:assembleDebug        → BUILD SUCCESSFUL (56 tareas)
.\gradlew.bat :androidApp:testDebugUnitTest    → BUILD SUCCESSFUL (62 tests, 0 failed)
.\gradlew.bat :shared:allTests                 → BUILD SUCCESSFUL
```

---

## Qué queda pendiente para dispositivo físico

| Ítem | Por qué no se puede probar en emulador |
|------|----------------------------------------|
| OCR con texto impreso real | La cámara emulada no tiene escena con texto |
| TTS en español escuchado | El emulador no reproduce audio audible |
| TalkBack con gestos reales | No se puede simular deslizamiento táctil con ADB |
| WhatsApp (apertura + composición) | No está instalado en el emulador |
| AccessibilityService leyendo WhatsApp | Requiere WhatsApp instalado |
| Pronunciación de acentos | Requiere audio real |
| Flujo de activación del servicio | Requiere interacción manual en Ajustes |
| Onboarding con voz audible | TTS no suena en emulador |

---

## APK

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```
