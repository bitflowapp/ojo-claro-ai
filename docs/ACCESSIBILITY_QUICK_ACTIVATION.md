# Ojo Claro AI — Activación rápida sin ver la pantalla

Fecha: 2026-05-05
Estado: **Implementado y verificado en emulador. Listo para probar en device físico.**

Este documento describe los caminos no-visuales y oficiales para que una persona ciega abra Ojo Claro en modo escucha sin tener que buscar el ícono entre apps.

---

## 1. Por qué NO usamos triple power button

- En Android no hay API pública para que una app normal capture la combinación de tres pulsaciones del botón de encendido.
- Esa combinación está reservada por el sistema para SOS de emergencia, abrir cámara o bloquear el teléfono dependiendo del fabricante. Reasignarla rompe esos flujos.
- Cualquier intento por hooks ocultos requiere root o perder la firma del Play Store.
- Sería un patrón que **engaña** al usuario: pensaría que algo "automático" sirve para SOS y en realidad iría a Ojo Claro.

Por eso usamos sólo caminos oficiales: Quick Settings tile, Accessibility Shortcut con volumen, botón flotante de Accesibilidad e intent público.

---

## 2. Camino A — Atajo de Accesibilidad (Volumen + + Volumen -)

Es el camino más rápido y descubierto por TalkBack:

1. Abrir **Ajustes** → **Accesibilidad** → **Ojo Claro AI**.
2. Tocar **Acceso directo de Accesibilidad** (o "Atajo de accesibilidad" / "Acceso directo del servicio" según el fabricante).
3. Seleccionar **Botón mantener pulsado los dos volúmenes**.
4. Aceptar.

Una vez asignado: con el teléfono en cualquier pantalla, **mantené Volumen+ y Volumen- presionados durante 3 segundos**. El sistema activa el servicio de Ojo Claro.

Limitación importante de Android: el atajo de volumen sólo enciende/apaga el `AccessibilityService` asignado. No abre la activity de Ojo Claro. Para llegar a la pantalla de escucha, combiná este paso con el **botón flotante** (camino B) o el **Quick Settings tile** (camino C).

---

## 3. Camino B — Botón flotante de Accesibilidad

Si tu Android lo soporta (lo soportan Android 8+), aparece un botón circular flotante en la esquina inferior derecha cuando hay un servicio asignado al botón.

Activación una sola vez:

1. Abrir **Ajustes** → **Accesibilidad** → **Ojo Claro AI**.
2. Tocar **Acceso directo de Accesibilidad** y elegir **Botón de accesibilidad** (en lugar de "Mantener volúmenes").
3. Aceptar.

A partir de ese momento, **un toque** en el botón flotante abre Ojo Claro en modo escucha.

Esto sí funciona end-to-end porque Ojo Claro escucha el callback `AccessibilityButtonController.onClicked()` y lanza:

```
Intent(context, MainActivity::class.java).apply {
    action = "com.ojoclaro.android.ACTION_START_LISTENING"
}
```

Implementación: `androidApp/src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt:18`. Habilitado por el flag `flagRequestAccessibilityButton` en `res/xml/ojo_claro_accessibility_service.xml`.

---

## 4. Camino C — Quick Settings tile

Probablemente el más usable para alguien ciego con TalkBack: el panel de ajustes rápidos es siempre accesible deslizando dos veces hacia abajo desde el borde superior.

Una sola vez:

1. Abrir el panel de notificaciones (deslizar dos veces desde arriba).
2. Tocar **Editar** (lápiz / "+ ").
3. Buscar **Ojo Claro** en los iconos disponibles.
4. Arrastrarlo al panel activo.
5. Guardar.

Uso diario: deslizar desde arriba → tocar **Ojo Claro**. La app abre directamente en modo escucha.

TalkBack lee:
- Etiqueta: "Ojo Claro"
- Descripción: "Activar Ojo Claro en modo escucha."

Implementación: `OjoClaroQuickTileService` (`androidApp/src/main/java/com/ojoclaro/android/voice/OjoClaroQuickTileService.kt`). Declarado en `AndroidManifest.xml` con permiso `BIND_QUICK_SETTINGS_TILE` y action `android.service.quicksettings.action.QS_TILE`.

Compatibilidad:
- Android 14+ (`UPSIDE_DOWN_CAKE`): usa `startActivityAndCollapse(PendingIntent)`.
- Android 7..13: usa `startActivityAndCollapse(Intent)` (deprecado pero funciona).

---

## 5. Camino D — Deep link / Intent público

Cualquier shortcut, automation app (Tasker, MacroDroid) o launcher accesible puede abrir Ojo Claro en modo escucha con:

```
adb shell am start \
  -a com.ojoclaro.android.ACTION_START_LISTENING \
  --ez start_listening true \
  -n com.ojoclaro.android/.MainActivity
```

O el equivalente programático:

```kotlin
val intent = Intent("com.ojoclaro.android.ACTION_START_LISTENING").apply {
    setPackage("com.ojoclaro.android")
    putExtra("start_listening", true)
    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}
context.startActivity(intent)
```

Esto disparado desde cualquier lado:
1. Abre `MainActivity` (con `launchMode="singleTop"` reusa la instancia existente).
2. Si la app no había saludado todavía, dice "Ojo Claro listo. Decime qué necesitás." una sola vez.
3. Si el saludo ya se dijo, no repite.
4. Si tiene RECORD_AUDIO concedido, arranca el voice loop. Si falta, pide el permiso con explicación humana ("Para usar Ojo Claro por voz, activá el micrófono. No guardo audio.").

---

## 6. Lo que NUNCA hace ninguno de estos caminos

- **No** activan micrófono en background. La app sólo escucha mientras es visible.
- **No** instalan hotword permanente.
- **No** graban audio.
- **No** abren WhatsApp ni mandan mensajes solos.
- **No** captan power button ni gestos del sistema.
- **No** suben audio a la nube.

Si alguno de los caminos se invoca con la app **no visible** (ej. tile en pantalla de bloqueo), Android requiere que MainActivity quede visible antes de que arranque el voice loop. Este es el contrato explícito: el voice loop NO arranca desde el tile ni desde el AccessibilityService — sólo desde la UI cuando es visible.

---

## 7. Límites reales de Android

| Camino | Funciona en pantalla de bloqueo | Necesita configuración previa | Latencia subjetiva |
|--------|---------------------------------|-------------------------------|---------------------|
| Atajo Volumen + Volumen | Sí (toggle servicio) | Asignar shortcut una vez | Bajo |
| Botón flotante de Accesibilidad | Depende del fabricante | Asignar tipo "botón" una vez | Bajo |
| Quick Settings tile | Sí | Arrastrar al panel una vez | Medio |
| Intent público / shortcut | Depende del invocador | Crear shortcut o automation una vez | Bajo |
| Ícono de la app | Sí | No | Alto (hay que buscar el ícono) |

---

## 8. Checklist para test físico

Hacé esto con el teléfono en silencio y TalkBack activo, idealmente con los ojos cerrados:

- [ ] Asignar el atajo de Accesibilidad de Volumen a Ojo Claro y verificar que TalkBack lo lea como "Atajo: Ojo Claro AI".
- [ ] Mantener Volumen+ + Volumen- y verificar que Ojo Claro se enciende como servicio.
- [ ] Asignar también el **botón flotante** (en lugar del atajo de volumen) y verificar que tocando el botón flotante:
  - se abre la app,
  - el saludo se escucha una sola vez,
  - el mic se activa o se pide permiso si falta.
- [ ] Arrastrar el tile **Ojo Claro** al panel de Quick Settings.
  - Tocar el tile dos veces seguidas: la primera saluda, la segunda **no debe** repetir saludo.
- [ ] Forzar el deep link: `adb shell am start -a com.ojoclaro.android.ACTION_START_LISTENING ...` y verificar:
  - sin FATAL EXCEPTION,
  - sin `W Binder` desde paquetes `com.ojoclaro.android.voice` o `com.ojoclaro.android.speech`,
  - `RecognitionService` arranca con `locale: es-AR`.
- [ ] Decir "callar" cuando la app está hablando: TTS corta y voice loop reanuda.
- [ ] Decir "qué dice la pantalla": pide consent. Decir "cancelar": cancela.
- [ ] Cerrar la app desde el switcher. Tocar el tile de nuevo: el saludo **sí** se vuelve a decir (el `greeted` flag vive en el process; al recrearse el process se permite saludar otra vez).

---

## 9. Verificación realizada en emulador

`adb -s emulator-5554`:

```
$ adb shell dumpsys package com.ojoclaro.android | grep -E "Service|TileService|QS_TILE|ACTION_START_LISTENING"
   com.ojoclaro.android.ACTION_START_LISTENING:
       Action: "com.ojoclaro.android.ACTION_START_LISTENING"
   android.service.quicksettings.action.QS_TILE:
     com.ojoclaro.android/.voice.OjoClaroQuickTileService
       permission android.permission.BIND_QUICK_SETTINGS_TILE
   android.accessibilityservice.AccessibilityService:
     com.ojoclaro.android/.accessibility.OjoClaroAccessibilityService
       permission android.permission.BIND_ACCESSIBILITY_SERVICE
```

Y forzando el intent:

```
$ adb shell am start -a com.ojoclaro.android.ACTION_START_LISTENING \
       --ez start_listening true -n com.ojoclaro.android/.MainActivity
Starting: Intent { act=com.ojoclaro.android.ACTION_START_LISTENING ... }

[logcat]
RecognitionServiceImpl: RecognitionService#logStartListening:
   callingApp: com.ojoclaro.android, locale: es-AR
```

Sin `FATAL EXCEPTION`, sin `AndroidRuntime`, sin `W Binder` desde `ojoclaro`.
