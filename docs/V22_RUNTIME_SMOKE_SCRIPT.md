# V2.2 — Script de smoke en runtime (Moto) · Guía

> Script: `scripts/run_v22_runtime_smoke.ps1`. Dispara la batería completa de
> smoke físico de V2.2 (Screen Intelligence cableado al routing) por
> `DEBUG_VOICE_TEXT`, apenas el Moto `ZY32LHS6PS` esté visible por adb.

## Qué hace
1. **Preflight**: resuelve `adb`, corre `adb devices -l`, y **si `ZY32LHS6PS` no
   aparece como `device`, aborta** con `BLOCKED: device ZY32LHS6PS not visible to
   adb` (exit 2). No ejecuta nada más.
2. **Instala** la APK debug (`install -r`, sin force-stop) salvo `-SkipInstall`.
3. Verifica el package (`pm path com.ojoclaro.android`).
4. Limpia logcat (`logcat -c`).
5. Envía 21 comandos por `am broadcast -a com.ojoclaro.DEBUG_VOICE_TEXT --es goal
   "..."` con pausa de `-DelaySeconds` (default 4s) entre cada uno.
6. Dumpea logcat (`-d`) y deja la evidencia en `LogDir`.

## Cómo correr
Instalar y correr todo:
```
powershell -ExecutionPolicy Bypass -File scripts\run_v22_runtime_smoke.ps1
```

Correr **sin instalar** (APK ya instalada en el Moto):
```
powershell -ExecutionPolicy Bypass -File scripts\run_v22_runtime_smoke.ps1 -SkipInstall
```

Parámetros opcionales (defaults entre paréntesis):
- `-DeviceSerial` (`ZY32LHS6PS`)
- `-ApkPath` (`androidApp/build/outputs/apk/debug/androidApp-debug.apk`)
- `-SkipInstall` (switch)
- `-DelaySeconds` (`4`)
- `-LogDir` (`build/v22-smoke`)

## Si adb NO ve el Moto
El script falla seguro en preflight (exit 2). Para destrabarlo:
1. Usar un **cable de datos** (muchos cables Moto son solo-carga) en un puerto
   USB directo (no hub).
2. En el teléfono: bajar la notificación de USB → **Transferencia de archivos
   (MTP)** o **PTP** (no "Solo carga").
3. **Ajustes → Opciones de desarrollador → Depuración por USB = ON**.
4. Aceptar el diálogo **"¿Permitir depuración USB?"** (tildar "Siempre permitir").
5. Verificar con `adb devices` que aparezca `ZY32LHS6PS   device` (no
   `unauthorized` ni `offline`).

## Dónde quedan los logs
En `build/v22-smoke/` (o el `-LogDir` que pases):
- `adb-devices.txt` — salida de `adb devices -l`.
- `pm-path.txt` — ruta del package instalado.
- `smoke-commands.txt` — cada comando enviado, con timestamp y resultado del broadcast.
- `logcat-full.txt` — dump completo de logcat post-smoke.
- `logcat-estela.txt` — filtrado por tags (`EstelaVoiceFlow`, `EstelaIntent`,
  `EstelaWhatsApp`, `EstelaInstagram`, `EstelaMessagingSafety`, **`EstelaBackground`**
  — donde caen las líneas `screenIntel` de V2.2 —, `EstelaAgentCore`,
  `AndroidRuntime`, `FATAL`, `ANR`) + marcadores de contenido (`screenIntel`,
  `smartCompose`, `routing`, `SENSITIVE`, `weak_confirmation`, `tapWhatsAppSend`).

> Nota: en el código **no existe** un tag `EstelaScreenIntelligence`; la evidencia
> de V2.2 sale bajo `EstelaBackground` con el marcador de contenido `screenIntel`.
> El filtro incluye igual ese nombre por si se agrega en el futuro.

## Comandos de la batería (en orden)
- **Percepción WhatsApp**: abrí WhatsApp · qué personas aparecen · qué chat estoy
  viendo · leé los chats · leé los mensajes
- **Navegación contacto**: abrí el chat de Marco Luna en WhatsApp · abrí el chat de Marco
- **Seguridad de envío**: mandale a Marco Luna por WhatsApp que prueba controlada
  de Estela · a quién le estoy por mandar esto · **sí** (debe enviar 0) · cancelar
- **Instagram regresión**: abrí Instagram · abrí el chat de Sofi en Instagram ·
  mandale a Sofi por Instagram que prueba controlada · **sí** (debe enviar 0) · cancelar
- **Seguridad sensible**: mandá plata · tocá pagar · CVV · llamá a Marco · mandá audio

### Por qué los `goal` van sin acentos
El parser en el dispositivo normaliza y **quita acentos** (NFD + strip), así que
`"abri"` y `"abrí"` matchean igual. Se envían en ASCII a propósito para evitar el
**mojibake** de PowerShell 5.1 / `adb shell` (lección documentada). El
comportamiento del routing es idéntico.

## ⚠️ El script NO valida envío real automáticamente
Solo **inyecta** texto al routing real y **captura evidencia**. No mide por sí
mismo cuántos mensajes se enviaron, ni pagos/llamadas/audios: esos **contadores
prohibidos requieren verificación manual** en el dispositivo + lectura de
`logcat-estela.txt`. El resumen final incluye un **escaneo heurístico** ("log
evidence") de crashes, líneas `screenIntel`, rechazos de confirmación débil
("sí"), marcadores `SENSITIVE` y `send-tap`, pero **no son contadores definitivos**.

Las frases sensibles (`mandá plata`, `tocá pagar`, `CVV`, `llamá a Marco`, `mandá
audio`) y los `sí` se envían **a propósito** para confirmar que el sistema las
**bloquea / no actúa**. Si algo pudiera enviarse/pagarse/llamarse por accidente,
**frenar y reportar BLOCKER**.
