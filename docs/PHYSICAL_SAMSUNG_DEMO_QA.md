# QA físico Samsung — Demo de Ojo Claro AI

Fecha de inicio: 2026-05-06
Estado: **bloqueado en Paso 1 — device físico no conectado a la PC.**
Autor: QA senior Android (sesión asistida por Codex/Claude).
Alcance: prueba de demo real en device Samsung. No se modifica código salvo crash real.

---

## 1. Resumen ejecutivo (provisional)

La QA física **no se pudo ejecutar en esta sesión**. `adb devices` no lista ningún dispositivo. Ni el Samsung `R5CW22SMWDM` esperado ni un emulador están presentes. Por política de QA, no se simulan resultados de comandos por voz cuando no hay hardware real escuchando — los reportes anteriores (`docs/AGENT_PHASE_3_EMULATOR_REALISTIC_QA.md`, `docs/VOICE_FIRST_DEMO_READINESS.md`) ya reflejan validación de emulador. El paso pendiente sigue siendo el dispositivo físico.

Cuando el device se conecte, este documento se completa con los resultados reales (ver §10 — checklist listo para retomar).

---

## 2. Dispositivo detectado

```
> adb devices
List of devices attached

(vacío)
```

**No hay dispositivo físico ni emulador conectado.**

El daemon de ADB arrancó correctamente (`daemon started successfully`). El problema no es de toolchain — es de cable / autorización USB / depuración.

---

## 3. Versión Android / API

Pendiente. Se confirmará con `adb -s <id> shell getprop ro.build.version.release` y `ro.build.version.sdk` apenas el device esté conectado.

---

## 4. Resultado instalación

Pendiente. Comando previsto:

```
adb -s R5CW22SMWDM install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Plan B si falla por firma:

```
adb -s R5CW22SMWDM uninstall com.ojoclaro.android
adb -s R5CW22SMWDM install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

APK objetivo:

```
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Tamaño esperado: 57.368.695 bytes. Generada después del fix del botón DESCRIBIR (`docs/DESCRIBIR_BUTTON_DEMO_FIX.md`).

---

## 5. Permisos concedidos

Pendiente. Se va a verificar in situ:

- `RECORD_AUDIO` (al primer arranque, prompt del sistema).
- `ACCESS_FINE_LOCATION` (al ejecutar "dónde estoy").
- `CAMERA` (sólo si se prueba OCR — no es obligatorio en este guion).

Permisos NO solicitados a propósito (verificación negativa también):

- `CALL_PHONE` — no debe aparecer.
- `READ_CONTACTS` — no debe aparecer.
- `ACCESS_BACKGROUND_LOCATION` — no debe aparecer.

---

## 6. Comandos probados

Pendiente. Plan completo en §10.

---

## 7. Qué funcionó / qué falló

Pendiente. Se completará con observación real, no con inferencia.

---

## 8. Latencia percibida

Pendiente. Se medirá por percepción del operador en device físico:

- Latencia del primer "callar" (objetivo: < 1 s).
- Latencia del saludo desde cold start (objetivo: ≤ 2 s a la espera del TTS).
- Latencia entre comando final y respuesta hablada.

---

## 9. Logcat final

Pendiente. Comando previsto:

```
adb -s R5CW22SMWDM logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro SecurityException maps location SpeechRecognizer RecognitionService TextToSpeech Binder Exception"
```

Criterios de aceptación:

- 0 `FATAL EXCEPTION` desde `com.ojoclaro.android`.
- 0 `AndroidRuntime` fatal de la app.
- 0 `SecurityException` originada por la app.
- `RecognitionServiceImpl` con `locale: es-AR`.
- Sin `W Binder` desde `com.ojoclaro.android.voice` o `com.ojoclaro.android.speech`.

---

## 10. Checklist listo para retomar

Cuando el Samsung esté conectado y `adb devices` lo muestre, ejecutar en orden:

### A. Pre-flight

- [ ] `adb devices` lista el device.
- [ ] `adb -s <id> shell getprop ro.build.version.release` registra versión Android.
- [ ] `adb -s <id> shell getprop ro.build.version.sdk` registra API.
- [ ] Pre-condición humana: paquete de voz `es-AR` instalado en el teléfono (`Ajustes → Sistema → Idiomas → Voz`).
- [ ] Pre-condición humana: WhatsApp instalado y logueado.
- [ ] Pre-condición humana: Google Maps instalado.
- [ ] Pre-condición humana: volumen de medios y voz al menos al 60%.

### B. Instalación

- [ ] `adb -s <id> install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- [ ] Si falla por firma: `uninstall com.ojoclaro.android` + `install -r ...`

### C. Cold start con logcat limpio

- [ ] `adb -s <id> logcat -c`
- [ ] `adb -s <id> shell am start -W -n com.ojoclaro.android/.MainActivity`
- [ ] Aceptar permiso de micrófono cuando aparezca.

### D. Flujo 1 — voz base

- [ ] Esperar saludo "Ojo Claro listo. Decime qué necesitás."
- [ ] Decir "qué puedo decir" → app lista comandos.
- [ ] Decir "callar" mientras lee → corte percibido < 1 s.
- [ ] Después de unos segundos, validar que el voice loop reanudó solo (sin tocar nada).

### E. Flujo 2 — WhatsApp seguro

- [ ] Decir "mandale un mensaje a un contacto que estoy llegando".
- [ ] La app debe decir: "Voy a preparar un mensaje para un contacto que dice: estoy llegando. No lo envío automáticamente. Confirmá para continuar."
- [ ] Decir "sí" → la app NO debe confirmar.
- [ ] Decir "cancelar" → "Acción cancelada."

### F. Flujo 3 — llamadas

- [ ] Decir "abrí teléfono" → debe abrir marcador del sistema, sin llamar.
- [ ] Volver a Ojo Claro (back).
- [ ] Decir "llamar" → la app debe preguntar "¿A quién querés llamar?"
- [ ] Decir "cancelar" → "Acción cancelada."

### G. Flujo 4 — ubicación / Maps

- [ ] Decir "dónde estoy" → si pide permiso, aceptarlo.
- [ ] Verificar que la app describe la ubicación o avisa que no la tiene aún. Sin crash.
- [ ] Decir "guardá esta ubicación como casa" → consent.
- [ ] Decir "confirmar".
- [ ] Decir "llevame a casa" → confirmación o apertura de Maps con la ruta, según flujo esperado.
- [ ] Decir "cancelar" si quedó pending.

### H. Flujo 5 — estabilidad

- [ ] Dejar la app abierta y en silencio 30 segundos.
- [ ] Sin tocar nada, decir "qué puedo decir".
- [ ] Confirmar que respondió → el auto-relisten sigue vivo.
- [ ] Verificar que NO repitió el saludo de bienvenida.

### I. Logcat final

- [ ] `adb -s <id> logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro SecurityException maps location SpeechRecognizer RecognitionService TextToSpeech Binder Exception"`
- [ ] Pegar el resultado en §9 de este informe.

### J. Veredicto

- [ ] Demo-ready sí / no, con condiciones.

---

## 11. FATAL EXCEPTION

Pendiente. No medible sin device.

---

## 12. Veredicto

**Bloqueado por hardware no conectado.**

No se puede emitir veredicto demo-ready hasta que `adb devices` muestre el Samsung físico y se ejecute el guion de §10.

Riesgo de avanzar sin esto:

- Cualquier latencia real del TTS engine del Samsung no se valida.
- Cualquier comportamiento de SpeechRecognizer en hardware real (paquete `es-AR`, ruido ambiente, eco) no se valida.
- Cualquier interacción con WhatsApp y Maps reales del device no se valida.
- La demo a un decisor sin esta validación es irresponsable.

---

## 13. Acciones para destrabar

Operador físico debe:

1. Conectar el Samsung por USB **con cable de datos** (no de carga solamente).
2. Desbloquear el teléfono.
3. Aceptar el diálogo "Permitir depuración USB desde esta computadora".
4. Verificar en *Ajustes → Opciones de desarrollador*: `Depuración USB` activado.
5. Volver a correr `adb devices` y confirmar que aparece el ID.

Una vez listo, retomar desde §10 paso A.
