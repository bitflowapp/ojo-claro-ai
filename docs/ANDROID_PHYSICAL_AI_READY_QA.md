# QA en Dispositivo Físico — Ojo Claro AI

Fecha: 2026-05-05  
Dispositivo: **Samsung Galaxy S23+** (SM-S916B)  
Android: **16** (API 36)  
Resolución: 1080×2340, nav gestures, insets: 74 px top, 144 px bottom  
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`  
Logcat: `logs/physical_ai_ready_qa_logcat.txt` (8601 líneas)

---

## Resultado de instalación

| Paso | Resultado |
|------|-----------|
| `adb install -r` | `Success` |
| APK instala limpio | ✅ |

---

## Resultado de builds y tests

```
.\gradlew.bat :androidApp:assembleDebug        → BUILD SUCCESSFUL
.\gradlew.bat :androidApp:testDebugUnitTest    → BUILD SUCCESSFUL (62 tests, 0 failed)
.\gradlew.bat :shared:allTests                 → BUILD SUCCESSFUL
```

---

## Tests automatizados vía ADB (sin PIN)

### Arranque

| Prueba | Resultado | Detalle |
|--------|-----------|---------|
| App arranca sin crash | ✅ | `Started com.ojoclaro.android` en logcat |
| TTS conecta | ✅ | `Sucessfully bound to com.google.android.tts` |
| TTS en español | ✅ | `com.google.android.tts` (Samsung TTS privado bloqueado, fallback a Google TTS) |
| FATAL EXCEPTION | ✅ Ninguno | 0 ocurrencias en 8601 líneas |

### Onboarding — primer arranque en hardware real

| Paso | Texto mostrado | contentDescription |
|------|---------------|-------------------|
| Paso 1 (INTRO) | "Soy Ojo Claro. Puedo ayudarte a leer texto, describir acciones básicas y preparar mensajes." | ✅ mismo texto |
| Paso 2 (NO_AUTO_SEND) | "No envío mensajes sin que los confirmes." | ✅ |
| Paso 3 (NO_STORAGE) | "No guardo tus chats ni tus imágenes." | ✅ |
| Paso 4 (CAMERA_PERMISSION) | "Para leer texto necesito permiso de cámara." | ✅ |
| Paso 5 (ACCESSIBILITY_PERMISSION) | "Para leer la pantalla necesito que actives accesibilidad." | ✅ |
| Paso 6 (STOP_ANYTIME) | "Podés tocar Callar en cualquier momento." | ✅ |
| Botón SIGUIENTE | `content-desc="Siguiente paso del tutorial."` | ✅ |
| Botón EMPEZAR (último paso) | `text="EMPEZAR"` | ✅ |
| Botón Repetir explicación | `content-desc="Repetir explicación."` | ✅ |
| Botón Saltar | `content-desc="Saltar tutorial."` | ✅ |
| Avance por tap | ✅ Funciona | SIGUIENTE → SIGUIENTE → ... → EMPEZAR → HomeScreen |
| Sin crash al completar | ✅ | Transición limpia |

### HomeScreen

| Elemento | contentDescription | Resultado |
|----------|-------------------|-----------|
| Estado inicial | `"Estado: Listo"` | ✅ |
| Saludo inicial | `"Respuesta: Listo. Tocá DESCRIBIR, Leer texto o Pedir ayuda."` | ✅ |
| Botón DESCRIBIR | `"Describir lo que tengo enfrente."` | ✅ |
| Botón Leer texto | `"Leer texto con la camara."` | ✅ |
| Botón ¿Qué puedo decir? | `"Escuchar ejemplos de comandos disponibles."` | ✅ |
| Botón Callar | `"Callar la voz."` | ✅ |

### Funcionalidad

| Prueba | Resultado | Detalle |
|--------|-----------|---------|
| DESCRIBIR → respuesta IA local | ✅ | "Para describir lo que ves, todavía necesito IA avanzada que no está activada." |
| Leer texto → TextScanScreen | ✅ | `text="Leyendo texto"`, botón "Callar y volver" activo |
| CameraX activo en hardware real | ✅ | `CameraX: QuirkSettings` en logcat |
| OCR activo en hardware real | ✅ | `OCR process succeeded via visionkit pipeline` (múltiples entradas continuas) |
| OCR resultado sin texto apuntado | ✅ | "No encontré texto claro." |
| Callar y volver → HomeScreen | ✅ | Estado: Listo |
| ¿Qué puedo decir? → VoiceHelpCenter | ✅ | Frases completas de comandos |
| Acentos UTF-8 | ✅ | "Podés", "todavía", "está", "encontré" correctos en contentDesc |

---

## Defecto de UX encontrado: Botón Callar fuera de pantalla con respuestas largas

**Severidad: Alta para usuarios ciegos**

En el Samsung Galaxy S23+ (1080×2340, nav bar gestures 144 px desde y=2196), cuando la respuesta es media o larga:

- ¿Qué puedo decir? (6 frases) → Callar cae completamente debajo del área de gestos (y>2196)
- Backend fallback largo → Callar cae detrás del área de gestos

**Ejemplo concreto:** con respuesta VoiceHelpCenter, el contenedor de Callar queda en `[72,2134][1008,2269]`. La navegación por gestos de Samsung bloquea taps a partir de y≈2196. El botón Callar tiene center y=2201 — inaccessible por toque.

**Impacto para usuario ciego:** Si el TTS está hablando una respuesta larga y el usuario quiere callar, no puede tocar el botón Callar. Con TalkBack activo (que usa navegación por deslizamiento), este problema puede no existir — TalkBack puede navegar a elementos fuera del área visible. **Requiere validación con TalkBack activo en hardware real.**

**Solución recomendada:** Limitar el texto de respuesta en pantalla (truncar a 3-4 líneas, con expansión) o hacer que el botón Callar sea sticky/fijo fuera del flujo de scroll.

---

## Tests pendientes — requieren interacción manual con PIN

Los siguientes tests no pudieron ejecutarse de forma remota porque el dispositivo tiene PIN de bloqueo de pantalla. Se requiere que el usuario realice estas pruebas en el dispositivo.

### TalkBack

**Pasos:**
1. Ajustes → Accesibilidad → TalkBack → Activar
2. Aceptar la advertencia
3. Abrir Ojo Claro AI
4. Verificar:
   - Orden de foco: título → estado → respuesta → botones (de arriba a abajo)
   - TTS anuncia contentDescription de cada botón al deslizar
   - DESCRIBIR: doble tap para activar (TalkBack)
   - Leer texto: doble tap → cámara → Callar y volver con doble tap
   - ¿Qué puedo decir?: doble tap → escuchar ejemplos
   - Callar: botón accesible con doble tap desde cualquier estado

**Criterio de éxito:** usuario ciego puede operar la app completamente sin ver la pantalla.

**Nota sobre el defecto de Callar:** Con TalkBack, el foco de accesibilidad puede llegar al botón Callar aunque esté "fuera de pantalla" visible, porque TalkBack navega por el árbol de accesibilidad, no por coordenadas. **Esto puede mitigar el defecto de UX encontrado.**

---

### AccessibilityService — Ojo Claro AI

**Pasos:**
1. Ajustes → Accesibilidad → Servicios instalados → Ojo Claro AI → Activar
2. Leer la advertencia de Android y aceptar
3. Volver a la app
4. Abrir WhatsApp (si está instalado)
5. Decir "qué dice la pantalla" o tocar el botón correspondiente
6. Verificar que se lee el texto visible sin guardar nada

**Criterio de éxito:**
- Nombre del servicio: "Ojo Claro AI" (legible, no técnico)
- Descripción: entendible por usuario ciego
- La app no crashea al activar el servicio
- La lectura de pantalla funciona

**Limitación conocida:** La lectura de pantalla solo funciona si el servicio está activo. No se puede activar programáticamente desde la app — el usuario lo activa manualmente.

---

### WhatsApp — sin envío automático

**Pasos:**
1. Con WhatsApp instalado, abrir Ojo Claro AI
2. Probar comando: "mandale a [contacto real]: [mensaje corto]"
   - O simular: tap en área de texto de voz → tipear el comando
3. Confirmar que la app:
   - Pide confirmación antes de abrir WhatsApp ("¿Querés enviarle X a Y?")
   - Solo abre WhatsApp con el mensaje prellenado al confirmar
   - No envía automáticamente
   - El usuario debe tocar Enviar en WhatsApp manualmente
4. Verificar que cancelar funciona limpiamente (no queda estado pendiente)
5. Probar que sin WhatsApp instalado el mensaje de error es claro

**Criterio de éxito:**
- Nunca se envía un mensaje sin que el usuario lo haga desde WhatsApp
- Cancelar limpia el estado correctamente

---

### OCR con texto impreso real

**Pasos:**
1. Abrir Ojo Claro AI → Leer texto
2. Apuntar la cámara a una hoja con texto impreso claro (tipografía ≥ 14pt, a ≥ 20 cm)
3. Esperar que el TTS hable el resultado
4. Verificar que el texto leído corresponde al texto real
5. Probar con: texto pequeño, texto en cursiva, texto sobre fondo complejo

**Criterio de éxito:** OCR lee texto impreso claro correctamente. Texto difícil dice "No encontré texto claro" en lugar de inventar.

---

### TTS audible

**Verificar manualmente:**
- Pronunciación de acentos: "Podés", "todavía", "está", "encontré", "cámara"
- Velocidad: ¿entendible para usuario sin experiencia con TTS?
- Volumen: ¿adecuado con y sin auriculares?
- Idioma: ¿español neutro / rioplatense?

---

## Resumen ejecutivo

| Área | Estado | Notas |
|------|--------|-------|
| Instalación APK | ✅ | Limpio, sin errores |
| Arranque sin crash | ✅ | 0 FATAL EXCEPTION |
| TTS Google TTS | ✅ | Samsung TTS privado → fallback a Google |
| Onboarding 6 pasos | ✅ | Todos verificados en hardware |
| HomeScreen contentDescriptions | ✅ | Todos correctos |
| DESCRIBIR → respuesta local | ✅ | IA avanzada no activada |
| OCR activo en hardware real | ✅ | visionkit pipeline en logcat |
| Callar y volver desde cámara | ✅ | Funciona en y≈2100 |
| ¿Qué puedo decir? | ✅ | VoiceHelpCenter completo |
| **Botón Callar con respuestas largas** | ⚠️ **Defecto** | Cae detrás del nav bar de Samsung; con TalkBack puede ser OK |
| TalkBack | ❌ Pendiente | Requiere PIN |
| AccessibilityService | ❌ Pendiente | Requiere PIN |
| WhatsApp sin envío | ❌ Pendiente | Requiere PIN |
| TTS audible | ❌ Pendiente | Requiere escucha directa |
| OCR con texto real | ❌ Pendiente | Requiere apuntar cámara manualmente |

---

## Riesgos pendientes antes de probar con usuario ciego

| Riesgo | Severidad | Acción requerida |
|--------|-----------|-----------------|
| Botón Callar inaccessible con respuestas largas | Alta | Verificar con TalkBack; si persiste, añadir scroll o Callar sticky |
| TTS pronuncia mal los acentos | Alta | Escuchar en hardware real con usuario piloto |
| TalkBack no anuncia el estado correctamente | Alta | Probar con TalkBack real, velocidad 2x |
| AccessibilityService nombre/descripción confusos | Media | Verificar en Ajustes → Accesibilidad |
| WhatsApp pide confirmación de forma no audible | Media | Probar flujo completo con TTS activo |
| OCR falla con texto pequeño o fondos complejos | Media | Probar con materiales reales |
| Onboarding muy largo para primera escucha | Media | Escuchar audio de 6 pasos, medir tiempo |

---

## Recomendaciones antes de probar con una persona no vidente

**Crítico (no negociable):**
1. Verificar que el botón Callar sea siempre accesible — con TalkBack Y sin TalkBack, con cualquier longitud de respuesta.
2. Escuchar TTS completo en hardware real: onboarding, respuestas, errores. Ajustar si hay pronunciaciones incorrectas.
3. Probar una sesión completa con TalkBack activo sin mirar la pantalla.

**Importante:**
4. Limitar largo de respuesta hablada — el VoiceHelpCenter tiene 6 frases largas; quizás hablarlas de a 2 es más usable.
5. Confirmar que el flujo de confirmación de WhatsApp es audible y claro ("¿Confirmar? Decí confirmar o cancelar").
6. Probar AccesibilityService: la advertencia de Android es larga y en inglés/español técnico; preparar al usuario para eso.

**Verificación post-corrección de Callar:**
7. Después de la corrección del Callar sticky, repetir las pruebas funcionales completas.

---

## APK

```
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```
