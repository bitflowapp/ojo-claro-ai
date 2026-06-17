# Plan de Prueba en Dispositivo Físico — Ojo Claro AI

Fecha de preparación: 2026-05-04  
Para ejecutar cuando conectes un Android real por USB.

---

## Qué necesitás

- Teléfono Android (versión 8.0 o superior).
- Cable USB.
- WhatsApp instalado y con al menos un chat existente.
- Un papel con texto impreso (cualquier hoja con letras).
- Auriculares o volumen del teléfono activo.
- Este documento abierto en la PC.

---

## Paso 1 — Activar opciones de desarrollador

1. En el teléfono: **Ajustes → Acerca del teléfono**.
2. Buscá **"Número de compilación"** (puede estar en "Información del software").
3. Tocá ese renglón **7 veces seguidas**.
4. Aparece: "¡Ya eres desarrollador!".
5. Volvé a **Ajustes → Opciones de desarrollador**.
6. Activá el interruptor principal si está apagado.

---

## Paso 2 — Activar depuración USB

1. Dentro de **Opciones de desarrollador**: buscá **"Depuración USB"**.
2. Activala.
3. Si pregunta con un diálogo, confirmá.

---

## Paso 3 — Conectar el cable y autorizar

1. Conectá el cable USB entre el teléfono y la PC.
2. En el teléfono aparecerá un diálogo: **"¿Permitir depuración USB?"**
3. Tocá **Permitir** (opcionalmente activá "Siempre permitir desde esta computadora").
4. En la PC, verificá que el dispositivo aparece:

```powershell
! adb devices
```

Debe mostrar algo como:
```
List of devices attached
R5CX12345678    device
```

Si dice `unauthorized`: desbloqueá el teléfono y aceptá el diálogo que aparece en pantalla.

---

## Paso 4 — Instalar el APK

```powershell
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Resultado esperado: `Success`.

Si falla por versión anterior instalada:
```powershell
adb uninstall com.ojoclaro.android
adb install androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

---

## Paso 5 — Abrir la app y verificar arranque

```powershell
adb logcat -c
adb shell am start -n com.ojoclaro.android/.MainActivity
```

En el teléfono debés ver:
- Pantalla negra con botones blancos.
- Texto "Ojo Claro AI" arriba.
- Estado "Listo".
- Botones: DESCRIBIR, Leer texto, Pedir ayuda, Callar.

Verificar que no crasheó:
```powershell
adb logcat -d | Select-String "FATAL"
```
Resultado esperado: ninguna línea.

---

## Paso 6 — Probar flujo básico sin TalkBack

Hacé estas pruebas tocando los botones directamente en el teléfono:

| Prueba | Qué tocar | Qué debe pasar |
|--------|-----------|----------------|
| DESCRIBIR | Botón DESCRIBIR | La app dice algo (fallback si no hay internet) |
| Leer texto — primera vez | Botón Leer texto | Aparece diálogo de permiso de cámara |
| Aceptar cámara | "Mientras uso la app" | Se abre la cámara |
| OCR real | Apuntá a texto impreso | La app lee el texto en voz alta en ~1-2 segundos |
| Sin texto visible | Tapá la cámara con la mano | Dice "No encontré texto claro." (una sola vez) |
| Callar y volver | Botón Callar y volver | Vuelve a la pantalla principal |
| Pedir ayuda | Botón Pedir ayuda | La app dice el fallback de emergencia |
| Callar | Botón Callar | La voz se detiene inmediatamente |

**Verificar pronunciación TTS:**  
Escuchá que la app diga:
- "cámara" (con acento, no "camara")
- "Entendí" (no "entendi")
- "Probá" (no "proba")
- "No encontré texto claro" (no "encontre")

---

## Paso 7 — Activar TalkBack

1. **Ajustes → Accesibilidad → TalkBack**.
2. Activá el interruptor.
3. Confirmá el aviso del sistema.
4. El teléfono empieza a leer en voz alta los elementos.

**Navegación básica con TalkBack:**
- Deslizá con un dedo a la derecha/izquierda para moverse entre elementos.
- Toque doble para activar el elemento enfocado.
- Toque doble con dos dedos para ir Atrás.

**Qué verificar:**

| Elemento | Qué debe anunciar TalkBack |
|----------|---------------------------|
| Botón DESCRIBIR | "Describir lo que tengo enfrente. Botón" |
| Botón Leer texto | "Leer texto con la camara. Botón" |
| Botón Pedir ayuda | "Pedir ayuda. Botón" |
| Botón Callar | "Callar la voz. Botón" |
| Estado | "Estado: Listo" |
| Encabezado | "Ojo Claro AI. Encabezado" |
| Indicador de carga | "Procesando, esperá un momento." |

Si TalkBack dice solo "Botón" sin nombre → reportar como bug.  
Si TalkBack dice texto que no tiene sentido → reportar como bug.

**Desactivar TalkBack cuando terminés:** Ajustes → Accesibilidad → TalkBack → desactivar.

---

## Paso 8 — Activar el servicio de accesibilidad Ojo Claro

1. **Ajustes → Accesibilidad → Aplicaciones instaladas** (o "Servicios descargados").
2. Buscá **"Ojo Claro AI"**.
3. Leé la descripción que aparece:  
   *"Este permiso permite que Ojo Claro lea texto visible en pantalla para ayudarte. No guarda mensajes ni contraseñas."*  
   Verificá que sea esa frase exacta.
4. Activá el servicio.
5. Android muestra una advertencia — es normal, es el sistema protegiendo al usuario.
6. Confirmá.

**Verificar que está activo:**
```powershell
adb shell settings get secure enabled_accessibility_services
```
Debe incluir `com.ojoclaro.android/com.ojoclaro.android.accessibility.OjoClaroAccessibilityService`.

---

## Paso 9 — Probar lectura de pantalla

Con el servicio de accesibilidad activo:

1. Abrí WhatsApp. Abrí una conversación con mensajes visibles.
2. Volvé a Ojo Claro AI.
3. Dictá o escribí: **"que dice la pantalla"** (o probá desde el botón DESCRIBIR si está implementado como comando).
4. La app debe leer el texto visible de WhatsApp.

**Verificar:**
- La app lee texto visible, NO contraseñas.
- La app NO envía nada.
- La app NO guarda el contenido leído.

---

## Paso 10 — Probar WhatsApp sin enviar

Con WhatsApp instalado:

| Prueba | Qué decir | Qué debe pasar |
|--------|-----------|----------------|
| Abrir WhatsApp | "abrir WhatsApp" | WhatsApp se abre |
| Sin WhatsApp | — | "No encontré WhatsApp instalado." |
| Componer mensaje | "mandale a [nombre] que [texto]" | La app pide confirmación: "Voy a preparar un mensaje para [nombre]..." |
| Confirmar | "confirmar" | WhatsApp se abre con el mensaje prellenado |
| **NO enviar** | — | El usuario DEBE tocar Enviar manualmente en WhatsApp |
| Cancelar | "cancelar" (antes de confirmar) | "Acción cancelada." — WhatsApp no se abre |

**Regla crítica:** Nunca toques Enviar en WhatsApp durante las pruebas.

---

## Paso 11 — Capturar logs post-prueba

```powershell
adb logcat -d > logs\physical_device_qa_logcat.txt
```

Verificar que no haya crashes:
```powershell
Get-Content logs\physical_device_qa_logcat.txt | Select-String "FATAL EXCEPTION"
```
Resultado esperado: 0 líneas.

---

## Paso 12 — Guardar resultado

Completar el checklist de `docs/ANDROID_REAL_DEVICE_QA.md` con los resultados observados.  
Anotar cualquier comportamiento inesperado, frase confusa o elemento que TalkBack no leyó correctamente.

---

## Señales de alerta a reportar

| Lo que observás | Qué significa |
|-----------------|---------------|
| TalkBack dice "Botón" sin nombre | contentDescription no llega a TalkBack |
| TTS no pronuncia bien los acentos | Motor TTS distinto al esperado |
| La app envía un mensaje sola | Bug crítico — reportar inmediatamente |
| Crash (pantalla negra o "Ojo Claro dejó de funcionar") | Capturar logcat y reportar |
| El servicio de accesibilidad lanza avisos raros | Normal — es el sistema, no un bug nuestro |
| OCR lee texto inventado | Revisar iluminación y enfoque |
| OCR no detecta nada con texto claro en pantalla | Revisar que la cámara tenga permiso |

---

## Comandos rápidos de referencia

```powershell
# Ver dispositivos
adb devices

# Instalar
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk

# Abrir app
adb shell am start -n com.ojoclaro.android/.MainActivity

# Ver crashes
adb logcat -d | Select-String "FATAL"

# Guardar logs
adb logcat -d > logs\physical_device_qa_logcat.txt

# Permisos actuales
adb shell dumpsys package com.ojoclaro.android | Select-String "permission"

# Desinstalar
adb uninstall com.ojoclaro.android
```
