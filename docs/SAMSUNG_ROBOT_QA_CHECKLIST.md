# Samsung Robot QA Checklist

Checklist reproducible para probar Ojo Claro en un Samsung real sin usar chats privados ni datos sensibles.

## 1. Levantar proxy GPT mini

En la PC:

```powershell
cd C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\tools\ojo_claro_ai_proxy
npm test
node server.mjs
```

Validar desde otra terminal:

```powershell
curl http://127.0.0.1:8787/health
curl http://127.0.0.1:8787/metrics
```

El JSON puede mostrar `hasApiKey=true|false`, pero nunca debe mostrar el valor de la key.

## 2. Compilar APK con URL LAN

Reemplazar `192.168.1.39` por la IP LAN de la PC:

```powershell
cd C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp
.\gradlew.bat assembleDebug -PojoClaroAssistantBaseUrl=http://192.168.1.39:8787 --console=plain
```

## 3. Instalar y abrir

```powershell
adb devices -l
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb shell monkey -p com.ojoclaro.android 1
```

## 4. Permisos en Samsung

- Activar Accesibilidad: Ajustes -> Accesibilidad -> Apps instaladas -> Ojo Claro -> Activado.
- Conceder micrófono cuando Android lo pida.
- No abrir chats privados reales para estas pruebas.

## 5. DEBUG_SUBMIT_TEXT

Disponible solo en debug y pasa por el pipeline real del robot.

```powershell
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "resetear"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "abrí WhatsApp"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "abrir ure Max"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "qué hay en pantalla"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "qué chats ves"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "explicame esta app"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "callate"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "repetí"
```

Casos negativos:

```powershell
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "canción de Marco Antonio"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "android"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "mandale mensaje a Marco"
adb shell am broadcast -a com.ojoclaro.DEBUG_SUBMIT_TEXT --es text "banco clave tarjeta cbu"
```

## 6. Validar métricas y logs

```powershell
curl http://127.0.0.1:8787/metrics
adb logcat -d | findstr /C:"handler=voice_correction" /C:"commandRedacted=true"
```

Esperado:

- `abrí WhatsApp`, `abrir wp`, `abrir guasap`, `abrir wasap` y `abrir ure Max` abren WhatsApp principal.
- No queda "Pendiente: acción de WhatsApp" después de abrir WhatsApp directo.
- `qué chats ves` usa Visible Chats, no resumen general.
- `qué hay en pantalla` usa Screen Understanding.
- `canción de Marco Antonio` y `android` no abren WhatsApp.
- `callate`, `repetí` y `resetear` funcionan aunque haya pending.
- `/metrics` no devuelve input, output, chats, OCR ni key.
- Log de corrección usa `handler=voice_correction`, `commandRedacted=true`, `targetIntent` y `confidence`, sin texto real.

## 7. PASS / PARTIAL / FAIL

PASS:
- No hay auto-click ni auto-send.
- WhatsApp solo se abre para comandos seguros.
- Fuzzy dudoso pide confirmación o cae a fallback.
- GPT mini no se llama en pantalla sensible.
- No aparece API key, OCR completo, nombres de chats ni mensajes en logs o métricas.

PARTIAL:
- El proxy no está disponible, pero el robot degrada con respuesta segura.
- Accesibilidad o micrófono faltan, y Ojo Claro lo explica sin loops.

FAIL:
- Se envía o toca algo automáticamente.
- Se lee un chat completo sin pedido explícito.
- Una frase sensible se auto-ejecuta por fuzzy o confirmación.
- Logs, UI o `/metrics` muestran texto real, chats, OCR completo o API key.
