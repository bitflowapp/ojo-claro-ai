# QA Manual — Ojo Claro AI en dispositivo Android real

Revisión: 2026-05-04  
Revisado por: QA senior Android / accesibilidad  
APK: `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

---

## 0. Prerrequisitos

- Dispositivo Android físico con Android 8.0 (API 26) o superior.
- WhatsApp instalado y con al menos un chat existente.
- Depuración USB habilitada o instalación por APK directo.
- Batería ≥ 50 %.
- Auriculares o volumen de dispositivo audible.

---

## 1. Instalar el APK

### Opción A — ADB por cable

```bash
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### Opción B — Archivo directo en el dispositivo

1. Copiá el APK al dispositivo (USB, Drive, etc.).
2. En el dispositivo: Ajustes > Seguridad > Instalar apps de fuentes desconocidas (habilitá solo para el gestor de archivos que vayas a usar).
3. Abrí el gestor de archivos, tocá el APK y confirmá la instalación.
4. Deshabilitá "Fuentes desconocidas" después de instalar.

---

## 2. Activar TalkBack

1. Ajustes > Accesibilidad > TalkBack.
2. Activá el interruptor. Confirmá el aviso del sistema.
3. Verificá que el dispositivo empiece a leer en voz alta los elementos de pantalla.
4. Navegación básica con TalkBack activo:
   - Deslizá derecha/izquierda para moverse entre elementos.
   - Doble toque para activar el elemento enfocado.
   - Deslizá con dos dedos para hacer scroll.

---

## 3. Activar el servicio de accesibilidad Ojo Claro

> Hacé esto con TalkBack ya activo para probar la experiencia real de un usuario ciego.

1. Abrí la app Ojo Claro AI desde el lanzador.
2. Navegá hasta el botón **"Pedir ayuda"** — verifica que TalkBack diga "Pedir ayuda."
3. Antes de activar el servicio, entrá a:  
   Ajustes > Accesibilidad > Aplicaciones instaladas (o "Servicios descargados") > Ojo Claro AI.
4. Leé en voz alta la descripción que aparece:  
   *"Este permiso permite que Ojo Claro lea texto visible en pantalla para ayudarte. No guarda mensajes ni contraseñas."*  
   Verificá que coincida con esa frase exacta.
5. Activá el servicio y confirmá el aviso del sistema.
6. Volvé a la app. Tocá el botón de "Pedir ayuda" — la respuesta no requiere accesibilidad, pero permite verificar que la app esté activa.

---

## 4. Casos de prueba

### 4.1 — Arranque de la app

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Abrí la app | Pantalla negra, sin lectura automática al arrancar |
| 2 | Con TalkBack: deslizá derecha | El foco pasa por: "Ojo Claro AI" (encabezado) → Estado: Listo → Respuesta → DESCRIBIR → Leer texto → Pedir ayuda → Callar |
| 3 | Escuchá cada elemento | Cada botón se anuncia por su contentDescription, no por texto visual |

**Foco esperado de TalkBack (orden):**
1. "Ojo Claro AI" — encabezado
2. "Estado: Listo"
3. "Respuesta: Listo. Toca DESCRIBIR, Leer texto o Pedir ayuda."
4. "Describir lo que tengo enfrente." — botón principal
5. "Leer texto con la camara." — botón secundario
6. "Pedir ayuda." — botón secundario
7. "Callar la voz." — botón secundario

---

### 4.2 — Lectura OCR (botón "Leer texto")

#### 4.2.1 — Primera vez (sin permiso de cámara)

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Tocá "Leer texto" (doble toque con TalkBack) | El sistema pide permiso de cámara |
| 2 | Negá el permiso | La app dice en voz alta: "Necesito permiso de cámara para leer texto." |
| 3 | No aparece pantalla de cámara | Correcto — nunca se navega a escáner sin permiso |

#### 4.2.2 — Con permiso concedido

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Otorgá permiso de cámara | La app dice: "Buscando texto con la cámara. Apuntá al texto." |
| 2 | Apuntá la cámara a un texto impreso | Luego de ~1 segundo estable: "El texto dice: [texto detectado]" |
| 3 | El texto aparece en pantalla y se habla | Solo se lee una vez por resultado único |
| 4 | Cubrí la cámara o alejala del texto | Si pasan varios segundos sin texto: "No encontré texto claro." (una sola vez) |
| 5 | Verifica que TalkBack anuncie "Camara activa para leer texto." en la vista de cámara | Solo para usuarios con TalkBack sin la lectura en voz de la app |

#### 4.2.3 — Botón "Callar y volver"

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Mientras escanea, tocá "Callar y volver" | TTS se detiene, vuelve a la pantalla principal |
| 2 | TalkBack anuncia el botón como | "Callar la voz y volver a la pantalla principal." |
| 3 | La app queda en estado IDLE | Sin hablar nada más |

---

### 4.3 — Lectura de pantalla visible (AccessibilityService)

#### 4.3.1 — Con servicio inactivo

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Desactivá el servicio Ojo Claro en Ajustes | — |
| 2 | Dictá o usá el comando de texto "que dice la pantalla" | La app dice: "Necesito activar el permiso de accesibilidad para leer la pantalla." |
| 3 | El mensaje es claro y recuperable | Sin crash |

#### 4.3.2 — Con servicio activo

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Activá el servicio y volvé a la app | — |
| 2 | Dictá "que dice la pantalla" (o usá botón DESCRIBIR como proxy de prueba) | La app lee el texto visible: "La pantalla dice: [texto de la pantalla]" |
| 3 | Abrí WhatsApp con una conversación abierta | — |
| 4 | Volvé a Ojo Claro y pedí "que dice la pantalla" | La app lee el último contenido visible de WhatsApp, NO envía nada |
| 5 | Abrí un campo de contraseña (cualquier app) | La app NO lee ese campo — verifica en los logs que `isPassword` lo filtra |

---

### 4.4 — Apertura de WhatsApp

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Tocá DESCRIBIR y dictá "abrir WhatsApp" | La app dice: "Abrí WhatsApp." — WhatsApp se abre |
| 2 | Con WhatsApp no instalado (simulado desinstalando) | La app dice: "No encontré WhatsApp instalado." |

---

### 4.5 — Preparación de mensaje en WhatsApp

> El mensaje NUNCA se envía automáticamente. Siempre requiere confirmación manual.

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Dictá: "mandale a María que ya llegué" | La app dice: "Voy a preparar un mensaje para María que dice: ya llegué. Confirmá antes de enviarlo." |
| 2 | Respondé "confirmar" | La app dice: "Confirmado. Voy a abrir WhatsApp con el mensaje preparado." — WhatsApp se abre con el texto prellenado |
| 3 | Verificá que el mensaje NO se envió solo | El usuario debe tocar Enviar manualmente en WhatsApp |
| 4 | Respondé "cancelar" en paso 1 | La app dice: "Acción cancelada." — WhatsApp no se abre |
| 5 | Esperá más de 2 minutos sin confirmar | La app dice: "La acción pendiente venció. Volvé a pedirla." |

---

### 4.6 — Botón Callar

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Activá una respuesta larga (DESCRIBIR) | La app empieza a hablar |
| 2 | Tocá "Callar" (doble toque con TalkBack) | TTS se detiene inmediatamente |
| 3 | La app vuelve a IDLE | Sin hablar nada más, ni respuestas anteriores encoladas |
| 4 | Con TalkBack: verifica que el botón siempre tenga foco | No debe quedar inaccesible en ningún estado |

---

### 4.7 — Procesando (indicador de carga)

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Tocá DESCRIBIR | Aparece un indicador circular mientras procesa |
| 2 | Con TalkBack: enfocá el indicador | TalkBack dice: "Procesando, esperá un momento." |

---

### 4.8 — Errores y recuperación

| Paso | Acción | Resultado esperado |
|------|--------|--------------------|
| 1 | Desactivá internet y tocá DESCRIBIR | La app dice el fallback: "No pude conectar con el asistente. Entendí: [texto]. Probá de nuevo o revisá internet." |
| 2 | Bloqueá la cámara (tape la lente físicamente al abrir escáner) | La app no crashea; eventualmente dice "No encontré texto claro." |
| 3 | Rotá el dispositivo | La app tiene `screenOrientation="portrait"`, no debe rotar |

---

## 5. Revisión de permisos

### Permisos declarados en el Manifest

| Permiso | Justificación | ¿Se pide en runtime? |
|---------|---------------|----------------------|
| `CAMERA` | OCR local con CameraX + ML Kit | Sí, solo al tocar "Leer texto" |
| `INTERNET` | Llamadas al backend de asistente | No requiere runtime en Android |

**Verificar ausencia de:**
- `ACCESS_FINE_LOCATION` — NO debe estar declarado
- `RECORD_AUDIO` — NO debe estar declarado
- `READ_CONTACTS` — NO debe estar declarado
- `READ_EXTERNAL_STORAGE` — NO debe estar declarado

### Comportamiento esperado de permisos

- La app NO pide ningún permiso al iniciar.
- La cámara se pide solo cuando el usuario toca "Leer texto".
- El servicio de accesibilidad requiere que el usuario vaya manualmente a Ajustes (comportamiento estándar de Android, no se puede cambiar).
- Si se niega la cámara, la app avisa verbalmente y no crashea.

---

## 6. Revisión de textos hablados

Todos los mensajes deben ser cortos, en español rioplatense y sin tecnicismos.

| Situación | Texto esperado |
|-----------|----------------|
| Permiso de cámara denegado | "Necesito permiso de cámara para leer texto." |
| Error de cámara | "No pude abrir la cámara. Probá de nuevo." |
| Escáner activo | "Buscando texto con la cámara. Apuntá al texto." |
| Texto detectado | "El texto dice: [texto]" |
| Sin texto claro | "No encontré texto claro." |
| Accesibilidad inactiva | "Necesito activar el permiso de accesibilidad para leer la pantalla." |
| WhatsApp no instalado | "No encontré WhatsApp instalado." |
| Mensaje preparado | "Abrí WhatsApp con el mensaje preparado. Elegí el chat de [contacto] y confirmá manualmente antes de enviarlo." |
| Sin internet (fallback) | "No pude conectar con el asistente. Entendí: [comando]. Probá de nuevo o revisá internet." |
| Emergencia (fallback) | "Si estás en peligro, llamá a tu contacto de emergencia o pedí ayuda a una persona cercana." |
| Procesando | "Procesando, esperá un momento." (contentDescription del indicador) |

---

## 7. Revisión del AccessibilityService

**Qué hace:**
- Lee texto visible en la ventana activa cuando el usuario lo pide.
- Respeta `node.isPassword` — no lee contraseñas.
- Limita la captura a 24 elementos de máximo 280 caracteres cada uno.
- No guarda, no transmite, no ejecuta gestos.

**Qué NO hace (verificar con código):**
- `onAccessibilityEvent` está vacío — no reacciona a eventos automáticamente.
- No hay llamadas a `performAction`, `click`, ni gestures.
- No hay logs ni escritura a disco del contenido leído.
- No hay llamadas de red desde el servicio.

**Verificar en Ajustes:**
- El servicio aparece listado como "Ojo Claro AI" con descripción legible.
- La descripción del servicio dice exactamente: "Este permiso permite que Ojo Claro lea texto visible en pantalla para ayudarte. No guarda mensajes ni contraseñas."

---

## 8. Checklist final — firma de QA

Marcar con ✓ cada ítem antes de considerar el APK listo para prueba con usuario final.

### Instalación y arranque
- [ ] APK instala sin errores
- [ ] La app abre sin crash
- [ ] No se pide ningún permiso al arrancar
- [ ] La pantalla es negra con alto contraste

### TalkBack
- [ ] Orden de foco lógico (encabezado → estado → respuesta → botones)
- [ ] Cada botón tiene contentDescription claro y corto
- [ ] El indicador de carga anuncia "Procesando, esperá un momento."
- [ ] El botón Callar siempre es accesible
- [ ] No hay elementos decorativos que TalkBack lea como botones
- [ ] La pantalla de escáner tiene contentDescription "Camara activa para leer texto."
- [ ] El botón "Callar y volver" anuncia "Callar la voz y volver a la pantalla principal."
- [ ] El botón "Volver" en pantalla de permiso faltante anuncia "Volver a la pantalla principal."

### Permisos
- [ ] Cámara solo se pide al tocar "Leer texto"
- [ ] No hay permiso de ubicación ni micrófono en el Manifest
- [ ] Denegar cámara produce mensaje de voz, no crash
- [ ] Servicio de accesibilidad tiene descripción clara en Ajustes

### OCR
- [ ] Al entrar al escáner la app dice "Buscando texto con la cámara. Apuntá al texto."
- [ ] El texto se estabiliza ~1 segundo antes de hablarse
- [ ] El mismo texto no se repite en el mismo escaneo
- [ ] "No encontré texto claro." se dice solo una vez
- [ ] "Callar y volver" detiene TTS y vuelve a home

### AccessibilityService
- [ ] No lee campos de contraseña
- [ ] No hace taps ni gestos automáticos
- [ ] Si el servicio está inactivo, la app avisa con mensaje claro
- [ ] Si la pantalla no tiene texto, la app avisa

### WhatsApp
- [ ] "Abrir WhatsApp" abre la app
- [ ] Sin WhatsApp instalado: mensaje claro, sin crash
- [ ] Composición de mensaje requiere confirmación verbal
- [ ] El mensaje se prelllena en WhatsApp pero NO se envía solo
- [ ] Cancelar la acción pendiente funciona
- [ ] La acción pendiente vence después de 2 minutos

### Callar
- [ ] Callar detiene TTS inmediatamente
- [ ] Callar cancela acciones pendientes
- [ ] Callar no crashea en ningún estado

---

## 9. Riesgos pendientes

| Riesgo | Impacto | Mitigación actual |
|--------|---------|-------------------|
| `SpeechController.isSpeaking` depende de callbacks del motor TTS | Puede no detectar habla en motores lentos | El `AtomicBoolean` es la fuente de verdad; el `tts.isSpeaking` es fallback |
| El APK usa `usesCleartextTraffic="true"` | Solo afecta conexiones HTTP al backend en debug | Justificado para MVP local; debe eliminarse en producción |
| `DESCRIBIR` no captura imagen todavía | El botón funciona pero manda texto solamente | Documentado como feature pendiente, no regresión |
| No hay tests instrumentales de UI | El flujo de botones no está cubierto automáticamente | Pendiente para siguiente iteración |
| LocalLifecycleOwner deprecado en TextScanScreen | Warning de compilación, no error | No bloquea MVP; migrar a `lifecycle-runtime-compose` en próxima iteración |

---

## 10. Recomendaciones para prueba con persona no vidente

1. **No explicar la UI de antemano.** Entregar el dispositivo con TalkBack activo y pedir a la persona que abra la app y trate de leer un texto impreso. Observar sin intervenir.

2. **Registrar las frases que confunden.** Si la persona pregunta "¿qué tengo que hacer ahora?", es una señal de que falta un anuncio de voz en ese estado.

3. **Probar con auriculares.** El volumen del TTS y el del TalkBack pueden mezclarse. Auriculares evitan confusión entre los dos.

4. **Probar a diferentes velocidades de TalkBack.** Algunos usuarios usan velocidad 2x o 3x. Los mensajes deben ser comprensibles a alta velocidad porque son cortos.

5. **No asumir que el usuario lee la pantalla.** Todo feedback útil debe llegar por audio. La pantalla es solo para quien ve.

6. **Tamaño de botón mínimo 72 dp.** Ya está implementado. Verificar con dedos de distintos tamaños que el área táctil sea cómoda.

7. **Verificar con dedo índice y con pulgar.** Usuarios con movilidad reducida pueden usar técnicas de toque diferentes.

8. **Temperatura del feedback.** La app debe confirmar cada acción con una frase corta. Si el usuario toca un botón y no pasa nada audible en 2-3 segundos, lo va a tocar de nuevo. Verificar que no haya silencios inexplicables.
