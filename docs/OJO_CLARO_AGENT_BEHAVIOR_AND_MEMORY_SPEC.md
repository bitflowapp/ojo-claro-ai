# Ojo Claro AI — Especificación de comportamiento y memoria

Fecha: 2026-05-06
Estado: spec implementable. Para uso de Codex en fases.
Audiencia: ingeniería Android/Kotlin + producto.

Este documento define **qué hace y cómo se comporta** Ojo Claro AI a nivel
de agente conversacional, memoria, intenciones, errores, contratos y plan
de implementación. No describe UI ni colores. Cada sección está pensada
para traducirse a código real sin inventar.

Las secciones referencian implementación existente cuando ya está bien:
no rehacer lo que está, sólo extenderlo.

---

## 1. Visión del agente

**Ojo Claro AI es un agente operativo de voz para Android que ayuda a una
persona no vidente a comunicarse, orientarse, escuchar, leer y recordar
tareas del día a día de forma segura y local.**

### Qué problema resuelve
Una persona ciega en Argentina hoy depende de un mosaico: TalkBack para
navegar, Google Maps con voz, WhatsApp con dictado, una alarma del
sistema, OCR de Lookout o Envision, una app aparte para describir
escenas, y memoria propia. Cada cambio de app fricciona.

Ojo Claro es **un punto de entrada de voz unificado** para los flujos
diarios: escribir un WhatsApp, llamar, llegar a un lugar, leer un papel,
leer la pantalla de una app, recordar una medicación. Sin abrir 6 apps.

### Qué lo diferencia de un lector OCR
Un lector OCR sólo lee texto físico. Ojo Claro lee **OCR + pantalla
visible + memoria + contexto**, y además **ejecuta acciones**: prepara
mensajes, abre llamadas, guarda recordatorios.

### Qué lo diferencia de un chatbot
Un chatbot conversa. Ojo Claro **opera el teléfono** dentro de límites
estrictos: nunca envía solo, nunca llama solo, nunca guarda datos
sensibles. La conversación es el medio, no el fin.

### Por qué local-first
- **Privacidad**: una persona no vidente no debería tener que confiar
  toda su comunicación a un proveedor cloud para que su teléfono le
  funcione.
- **Latencia**: callar tiene que cortar en menos de 1 segundo. Pedir un
  recordatorio no debe esperar a una API.
- **Costo**: el 80–90% de los comandos son patrones repetitivos
  ("mandale a un contacto…", "poneme alarma a las 8…"). Eso se programa.
- **Disponibilidad**: si se cae internet o se acaban los créditos de la
  IA, la app sigue siendo útil.

### Por qué la IA cloud es auxiliar
Sólo entra cuando el parser local declara baja confianza:
frases ambiguas, intención poco clara, resúmenes complejos,
interpretación flexible. Ver Sección 15.

---

## 2. Personalidad y tono

Ojo Claro habla **corto, claro, útil y orientado a acción**. No es
infantil, no es robótico, no se disculpa de más, no repite, no dramatiza.

### Reglas duras de tono

- **Largo ideal por respuesta**: 1 oración (~10–20 palabras). Máximo
  absoluto: 2 oraciones.
- **Frases largas** sólo cuando: ayuda inicial ("qué puedo decir"),
  lectura de pantalla, OCR de un texto largo. En cualquier otro caso,
  dividir.
- **Confirmaciones** son frases compuestas: una que describe la
  acción + una que invita a confirmar. Ejemplo: "Voy a preparar un
  mensaje para un contacto que dice: estoy llegando. No lo envío
  automáticamente. Confirmá para continuar."
- **Pedido de datos faltantes**: una pregunta directa, sin preámbulo.
  "¿A quién querés mandarle el mensaje?" — no "Disculpá, no entendí muy
  bien a quién…".
- **Disculpas**: nunca encadenar dos. "No entendí" alcanza. Nada de
  "perdón, perdón, no logré escuchar".
- **No repetición**: el `SpeechController` ya hace dedup en una ventana
  de 5 segundos (`DEDUP_WINDOW_MILLIS`). Mantener ese contrato. Toda
  frase nueva pasa por dedup salvo `force=true` para emergencias.
- **Recuperación**: si no entiende, ofrece **un** ejemplo concreto. No
  enumera la ayuda completa. "No entendí. Decime, por ejemplo, mandale
  a un contacto que estoy llegando."

### Frases canónicas (catálogo)

| Caso | Frase canónica |
|------|----------------|
| No entendí | "No entendí. Decime, por ejemplo, mandale a un contacto que estoy llegando." |
| No escuché | "No escuché un comando claro." |
| Falta contacto | "¿A quién querés mandarle el mensaje?" |
| Falta mensaje | "¿Qué mensaje querés mandarle?" |
| Falta destino | "¿A dónde querés ir?" |
| Falta hora | "¿A qué hora?" |
| Permiso faltante (mic) | "Para usar Ojo Claro por voz, activá el micrófono. No guardo audio." |
| Permiso faltante (cámara) | "Para leer texto, activá la cámara. Solo la uso cuando vos me lo pedís." |
| Permiso faltante (accesibilidad) | "Para leer esta pantalla, activá Ojo Claro en Accesibilidad. Solo leo texto visible cuando vos me lo pedís." |
| App no instalada (WhatsApp) | "No encontré WhatsApp instalado. Cuando lo tengas, te ayudo a preparar mensajes." |
| App no instalada (genérico) | "No encontré $app instalada en este teléfono." |
| Acción cancelada | "Acción cancelada." |
| Acción confirmada (genérica) | "Listo." |
| Acción confirmada (WhatsApp) | "Confirmado. Voy a abrir WhatsApp con el mensaje preparado." |
| Riesgo detectado | "Antes de responder, te aviso: este texto puede ser sensible." |
| Acción vencida | "La acción pendiente venció. Volvé a pedirla." |
| Modo seguro | "Esta pantalla puede tener datos privados. Por ahora, hacelo desde la app correspondiente." |

Estas frases ya viven en `ConsentPhrases.kt`, `CommandRouter.unsupportedText`,
`Capability.MSG_*`, `VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE`.
Cualquier frase nueva entra a un `object` de constantes — nunca string
literal en lógica.

---

## 3. Máquina de estados conversacional

El agente vive en **un estado a la vez**. Las transiciones son
deterministas. La UI sólo refleja el estado, no toma decisiones.

### Estados

| Estado | Significado |
|--------|-------------|
| `IDLE` | Esperando comando. Mic listo. |
| `LISTENING` | Mic activo, recibiendo audio del usuario. |
| `PROCESSING` | Comando final recibido, parseando o ejecutando. |
| `SPEAKING` | TTS hablando. Mic pausado. |
| `WAITING_CONFIRMATION` | Hay pending — externo o sensible. |
| `WAITING_CONTACT` | Faltó contactName en COMPOSE_WHATSAPP_MESSAGE / CALL_CONTACT. |
| `WAITING_MESSAGE` | Faltó messageText. |
| `WAITING_DESTINATION` | Faltó destination en NAVIGATE_TO_DESTINATION. |
| `WAITING_TIME` | Faltó hora/fecha en CREATE_REMINDER / CREATE_ALARM. |
| `WAITING_FREQUENCY` | Faltó frecuencia (recurrence). |
| `WAITING_PERMISSION` | Esperando que Android conceda mic/cámara/accesibilidad/notificaciones/exact-alarm. |
| `SAFE_MODE` | Riesgo detectado y `canReadAloud == false` o `READ_BANKING_SCREEN`. La acción no se ejecuta. |
| `ERROR_RECOVERABLE` | Reconocimiento de voz falló, app no instalada, intent rechazado. Se puede reintentar. |
| `STOPPED_BY_USER` | El usuario dijo "callar" o tocó el botón Callar. |

Renombrar el actual `AppState` para alinear: hoy hay
`AppState.{IDLE, LISTENING, PROCESSING, SPEAKING, WAITING_CONFIRMATION,
PERMISSION_REQUIRED, SCANNING, ERROR}`. La spec **agrega**
`WAITING_CONTACT`, `WAITING_MESSAGE`, `WAITING_DESTINATION`,
`WAITING_TIME`, `WAITING_FREQUENCY`, `SAFE_MODE`, `ERROR_RECOVERABLE`,
`STOPPED_BY_USER`. `SCANNING` queda como sub-estado de `PROCESSING`.

### Contrato general por estado

| Estado | Habla | Escucha | Sale por |
|--------|-------|---------|----------|
| `IDLE` | nada | sí | comando del usuario |
| `LISTENING` | nada | sí | onFinalText / onError |
| `PROCESSING` | nada (o "Procesando" sólo si tarda >1.5s) | no | outcome resuelto |
| `SPEAKING` | sí | no | onSpeechFinished / "callar" |
| `WAITING_CONFIRMATION` | nada (después del prompt inicial) | sí | "confirmar"/"cancelar"/timeout |
| `WAITING_*` (contact/message/etc) | la pregunta una vez | sí | dato recibido / "cancelar"/timeout |
| `WAITING_PERMISSION` | mensaje de permiso | no | callback del sistema |
| `SAFE_MODE` | aviso responsable | no | usuario nuevo comando |
| `ERROR_RECOVERABLE` | mensaje corto | sí (tras "Sigo escuchando.") | comando del usuario |
| `STOPPED_BY_USER` | nada | no inicialmente | tap "Escuchar" / nuevo intent |

### Reglas universales

1. **`callar`**: corta TTS y limpia todo pending (externo + consent). El
   estado va a `STOPPED_BY_USER` por un instante y vuelve a `IDLE`
   cuando el usuario hable de nuevo. Implementado en `VoiceCommandDispatcher.isStopCommand` + `HomeViewModel.onStopSpeechRequested`.
2. **`cancelar`**: si hay pending, lo limpia y dice "Acción cancelada.";
   si no, dice "No hay ninguna acción pendiente.".
3. **`confirmar`** (estricto: `confirmar`, `confirmo`, `aceptar`): si hay
   pending vivo y no vencido, ejecuta. Si no, dice "No hay ninguna
   acción pendiente para confirmar.".
4. **Silencio**: en `LISTENING`, `VoiceCommandController` reintenta con
   backoff (400/800/1200/2000 ms). Después de 4 fallos, dice
   "Sigo escuchando." una sola vez.
5. **Falla del SpeechRecognizer**: errores recuperables
   (`ERROR_NO_MATCH`, `ERROR_SPEECH_TIMEOUT`, `ERROR_CLIENT`,
   `ERROR_RECOGNIZER_BUSY`) → backoff + retry. Errores graves
   (`ERROR_AUDIO`, `ERROR_INSUFFICIENT_PERMISSIONS`) → `ERROR_RECOVERABLE`
   con mensaje humano.
6. **TTS hablando**: el `VoiceCommandController.pauseForSpeech` cancela
   el `SpeechRecognizer` antes de hablar. Resume al
   `onSpeechFinished/onSpeechStopped`. Esto ya está corregido para
   ejecutarse en main thread vía `scope.launch` en `HomeScreen`.

### El agente NO queda muerto esperando un botón

`HomeScreen` re-arma escucha automáticamente:
- al `Lifecycle.Event.ON_RESUME` con permiso de mic;
- al `appState` volver a `IDLE` o `WAITING_CONFIRMATION`;
- después del `onSpeechFinished` del TTS;
- después de un comando ejecutado.

El botón "Escuchar" es **fallback** para usuarios que prefieren tocar.
No es el flujo principal.

---

## 4. Modelo de intenciones

### Tipos

```kotlin
enum class AgentIntent {
    HELP,
    STOP_SPEAKING,
    CANCEL,
    CONFIRM,
    OPEN_APP,
    OPEN_WHATSAPP,
    COMPOSE_WHATSAPP_MESSAGE,
    READ_VISIBLE_SCREEN,
    READ_OCR_TEXT,
    CALL_CONTACT,
    OPEN_PHONE,
    OPEN_MAPS,
    GET_CURRENT_LOCATION,
    NAVIGATE_TO_DESTINATION,
    OPEN_SPOTIFY,
    PLAY_MUSIC, PAUSE_MUSIC, NEXT_SONG,
    VOLUME_UP, VOLUME_DOWN,
    REMEMBER_MEMORY, LIST_MEMORY, CLEAR_MEMORY, FORGET_LAST_MEMORY,
    CREATE_REMINDER, LIST_REMINDERS, CANCEL_REMINDER, MARK_REMINDER_DONE, SNOOZE_REMINDER,
    CREATE_ALARM,
    UNKNOWN
}

data class AgentSlot(
    val contactName: String? = null,
    val messageText: String? = null,
    val appName: String? = null,
    val destination: String? = null,
    val locationAlias: String? = null,
    val reminderText: String? = null,
    val date: String? = null,             // ISO local: 2026-05-08
    val time: String? = null,             // ISO local: 21:00
    val recurrence: Recurrence? = null,
    val medicationName: String? = null,   // sensible — sólo nombre, nunca dosis
    val confidence: Confidence = Confidence.HIGH,
    val rawCommand: String = ""
)

enum class Recurrence { ONCE, DAILY, WEEKLY_MONDAY, WEEKLY_TUESDAY, ..., WEEKDAYS, WEEKENDS, CUSTOM }
enum class Confidence { HIGH, MEDIUM, LOW }
```

### Tabla de intenciones

| Intent | Frases reales | Slots | Confirmación | Riesgo | Fallback | Acción Android |
|--------|---------------|-------|--------------|--------|----------|----------------|
| HELP | "qué puedo decir", "ayuda" | — | no | — | listar comandos | TTS local |
| STOP_SPEAKING | "callar", "callate", "silencio", "para" | — | no | — | — | TTS.stop + cancel mic |
| CANCEL | "cancelar", "no", "anular" | — | no | — | — | clear pending |
| CONFIRM | "confirmar", "confirmo", "aceptar" | — | n/a | — | sin pending → "No hay…" | ejecutar pending |
| OPEN_WHATSAPP | "abrí WhatsApp" + aliases | — | no | bajo | si falta → mensaje claro | `getLaunchIntentForPackage` |
| COMPOSE_WHATSAPP_MESSAGE | "mandale a un contacto que…", "decile a mamá que…" | contact, message | sí | medio | falta contact/message → preguntar | `ACTION_SEND` con `setPackage` |
| READ_VISIBLE_SCREEN | "qué dice la pantalla", "leeme este mensaje" | — | sí | medio-alto | si no hay accesibilidad → guiar | `AccessibilityScreenReader` |
| READ_OCR_TEXT | "leer texto", "leeme este papel" | — | no | bajo | si no hay cámara → guiar | abrir `TextScanScreen` |
| CALL_CONTACT | "llamá a un contacto", "llamá a mamá" | contact | sí | alto | falta contact → preguntar | `ACTION_DIAL` (no CALL) |
| OPEN_PHONE | "abrí teléfono" | — | no | bajo | — | `Intent.ACTION_DIAL` con número vacío |
| OPEN_MAPS | "abrí mapas" | — | no | bajo | — | `geo:0,0` con `setPackage` Maps |
| GET_CURRENT_LOCATION | "dónde estoy" | — | no | medio | sin permiso → guiar | `FusedLocationProviderClient` |
| NAVIGATE_TO_DESTINATION | "llevame a casa", "cómo llego a la farmacia" | destination o locationAlias | sí (la primera vez por alias) | medio | falta destino → preguntar | `geo:0,0?q=$destination` |
| OPEN_SPOTIFY | "abrí Spotify" | — | no | bajo | si falta → mensaje claro | `getLaunchIntentForPackage` |
| PLAY_MUSIC / PAUSE_MUSIC / NEXT_SONG | "poné/pausá/siguiente" | — | no | bajo | si Spotify no está → caer a media controls genéricos | `MediaController` o `Spotify intent` |
| VOLUME_UP / VOLUME_DOWN | "subí/bajá volumen" | — | no | bajo | — | `AudioManager.adjustStreamVolume` con tope |
| REMEMBER_MEMORY | "recordá que…" + variantes | tipo + label/value (ver §10) | sí | bajo | contenido sensible → bloquear con frase | `LocalMemoryStore.save` |
| LIST_MEMORY | "qué recordás de mí" | — | no | bajo | vacío → frase clara | `listAllSafeSummaries` |
| CLEAR_MEMORY | "borrá tu memoria" | — | sí | medio | — | `clearAll` |
| FORGET_LAST_MEMORY | "olvidá eso" | — | sí | bajo | vacío → frase | `delete(lastId)` |
| CREATE_REMINDER | "recordame tomar la medicación a las 21" | reminderText, time, recurrence?, medicationName? | sí | medio (medicación) | falta hora → preguntar | `WorkManager` o `AlarmManager` (ver §11) |
| LIST_REMINDERS | "qué recordatorios tengo" | — | no | bajo | vacío → frase | `ReminderStore.list()` |
| CANCEL_REMINDER | "cancelá el recordatorio de la medicación" | reminderText | sí | bajo | match único → confirmar; ambiguo → preguntar | `ReminderStore.delete + ReminderScheduler.cancel` |
| MARK_REMINDER_DONE | "ya lo hice", "marcá como hecho" | (último activo) | no | bajo | sin activo → frase | `ReminderStore.markDone` |
| SNOOZE_REMINDER | "recordamelo más tarde" | minutes (default 15) | no | bajo | — | reschedule +15 min |
| CREATE_ALARM | "poneme alarma a las 8" | time, date?, recurrence? | sí | bajo | sin permiso EXACT_ALARM → guiar | `AlarmClock.ACTION_SET_ALARM` |
| UNKNOWN | — | — | n/a | — | mensaje canónico de §2 | mantener en IDLE |

`OPEN_APP` es genérico: `"abrí <appName>"`. Slots = `appName`. Acción =
`getLaunchIntentForPackage` con resolución por nombre de display vía
`PackageManager.queryIntentActivities`. Si no se encuentra, frase de
"App no instalada".

---

## 5. WhatsApp

Ya implementado en `CommandRouter` + `WhatsAppIntentHelper` +
`AssistantOrchestrator.handleExternalSuccess(COMPOSE_WHATSAPP_MESSAGE)`.

### Reglas duras

1. **Nunca enviar automáticamente.** El intent es `ACTION_SEND` con
   `setPackage("com.whatsapp")` o `com.whatsapp.w4b`. WhatsApp abre el
   selector de chat — el usuario manda manualmente.
2. **Confirmación obligatoria** antes de abrir el intent.
3. **Confirmar sólo con** `confirmar` / `confirmo` / `aceptar`.
   `sí`/`si`/`dale` jamás confirman acciones sensibles. Test:
   `siAndDaleDoNotConfirmSensitiveActions`.
4. **Cancelar con** `cancelar` / `cancela` / `no` / `anular`.
5. **Falta contacto** → "¿A quién querés mandarle el mensaje?". Estado
   `WAITING_CONTACT`.
6. **Falta mensaje** → "¿Qué mensaje querés mandarle?". Estado
   `WAITING_MESSAGE`.
7. **Alias de contacto** → si el usuario dice "mi mamá" / "mi novia",
   resolver vía `MemoryStore.findRelevant` con tipo `TRUSTED_CONTACT`.
   Si hay match único, usar nombre real **en confirmación** ("Voy a
   preparar un mensaje para un contacto…"). Si hay varios, preguntar:
   "¿Cuál de las dos? ¿un contacto de trabajo o un contacto de casa?".
8. **Mensaje sensible** → `PrivacyGuard.isSafeMessagePayload(message)` ya
   bloquea contraseñas, códigos, tarjetas, CBU/CVU/saldo/home banking.
   Si devuelve false: "No puedo preparar ese mensaje porque parece
   contener datos sensibles." Estado vuelve a `IDLE`.

### Frases exactas

| Caso | Frase |
|------|-------|
| Falta contacto | "¿A quién querés mandarle el mensaje?" |
| Falta mensaje | "¿Qué mensaje querés mandarle?" |
| Confirmación | "Voy a preparar un mensaje para $contact que dice: $message. No lo envío automáticamente. Confirmá para continuar." |
| Cancelación | "Acción cancelada." |
| WhatsApp no instalado | "No encontré WhatsApp instalado. Cuando lo tengas, te ayudo a preparar mensajes." |
| Mensaje bloqueado | "No puedo preparar ese mensaje porque parece contener datos sensibles." |
| Mensaje preparado | "Confirmado. Voy a abrir WhatsApp con el mensaje preparado." |

### Truncado

Confirmación tiene cap por TTS:
- `contactName` ≤ 80 chars + "…" si excede.
- `messageText` ≤ 220 chars + "…".

Implementado en `CommandRouter.buildComposeConfirmation`. No tocar.

---

## 6. Llamadas

**Nuevo módulo.** No hay implementación todavía. Diseñar
`PhoneActionExecutor`.

### Comandos y mapping

| Frase | Intent | Slots |
|-------|--------|-------|
| "llamá a un contacto" | CALL_CONTACT | contact = "un contacto" |
| "llamá a mamá" | CALL_CONTACT | alias = "mamá" |
| "llamá a mi contacto de emergencia" | CALL_CONTACT | usar `EMERGENCY_CONTACT` de memoria |
| "abrí teléfono" | OPEN_PHONE | — |
| "llamá a emergencias" | CALL_CONTACT | número = "911" pero **no autodial** |

### Reglas duras

1. **Primera etapa: ACTION_DIAL, no ACTION_CALL.** El sistema abre el
   marcador con el número precargado y el usuario pulsa "llamar". Esto
   evita exigir `CALL_PHONE` permission y elimina llamadas accidentales.
   ```kotlin
   Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber"))
   ```
2. **Confirmación obligatoria** antes de abrir el dialer.
3. **Resolver contacto desde memoria** primero (`TRUSTED_CONTACT`,
   `EMERGENCY_CONTACT`). Si no hay match, usar `ContactsContract` con
   permiso `READ_CONTACTS` (pedido **explícito** la primera vez con
   explicación de privacidad: "Para llamar por nombre, necesito acceso
   a tus contactos. Sólo los uso cuando vos me lo pedís.").
4. **Sin permiso de contactos** → "No tengo acceso a tus contactos.
   Decime el número o activá el permiso desde Ajustes."
5. **Emergencias** nunca se autodial. Frase:
   - "Si estás en peligro, llamá a emergencias o pedí ayuda cercana.
     Voy a abrir el marcador con 911 — vos tocás llamar."

### Frases exactas

| Caso | Frase |
|------|-------|
| Confirmación normal | "Voy a preparar una llamada a $contact. No voy a llamar automáticamente. Confirmá para continuar." |
| Confirmación emergencia | "Voy a abrir el marcador con 911. No llamo automáticamente. Confirmá para continuar." |
| Falta contacto | "¿A quién querés llamar?" |
| Contacto no encontrado | "No encontré $contact en tus contactos." |
| Múltiples coincidencias | "Tengo varios $alias. ¿Cuál? Decime el nombre completo." |

---

## 7. Mapas y ubicación

### Comandos

| Frase | Intent | Slots |
|-------|--------|-------|
| "dónde estoy" | GET_CURRENT_LOCATION | — |
| "abrí mapas" | OPEN_MAPS | — |
| "llevame a casa" | NAVIGATE_TO_DESTINATION | locationAlias = "casa" |
| "cómo llego a la farmacia" | NAVIGATE_TO_DESTINATION | destination = "farmacia" |
| "abrí Google Maps hacia calle San Martín" | NAVIGATE_TO_DESTINATION | destination = "calle San Martín" |
| "guardá esta ubicación como casa" | REMEMBER_MEMORY | tipo = `LOCATION_ALIAS`, label = "casa" |

### Reglas duras

1. **Nunca prometer seguridad en calle.** Frase requerida la primera vez
   por sesión: "Te ayudo a abrir Maps con la ruta. No reemplazo a un
   bastón ni a una persona acompañante."
2. **Permisos**: `ACCESS_FINE_LOCATION` para `GET_CURRENT_LOCATION`. Si
   falta, frase: "Para saber dónde estás, activá la ubicación. Sólo la
   uso cuando vos me lo pedís."
3. **GPS apagado**: `LocationManager.isProviderEnabled(GPS_PROVIDER)`
   false → "No tengo GPS disponible. Activalo desde Ajustes y volvé a
   pedirlo."
4. **`locationAlias` desde memoria**: "casa", "trabajo", "lo de mamá"
   se buscan en `MemoryStore` tipo `LOCATION_ALIAS`. Match único →
   confirmar. No match → "No tengo guardada $alias. ¿Querés guardar la
   ubicación actual como $alias?"
5. **Guardar ubicación sensible** requiere confirmación
   `SIMPLE_CONFIRMATION` (no biometric, pero sí explícita). El payload
   guardado incluye lat/lng redondeado a 4 decimales (~10m de
   precisión, no exacto). Ver §10.
6. **Intent**: `Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$encodedDestination"))`.
   `setPackage("com.google.android.apps.maps")` si está instalado;
   sino, dejarlo abierto a chooser de mapas.

### Frases exactas

| Caso | Frase |
|------|-------|
| Ubicación disponible | "Estás cerca de $address aproximadamente." |
| Ubicación no disponible | "No tengo tu ubicación todavía. Probá afuera o activá GPS." |
| Permiso faltante | "Para saber dónde estás, activá la ubicación. Sólo la uso cuando vos me lo pedís." |
| Destino faltante | "¿A dónde querés ir?" |
| Navegación preparada | "Voy a abrir Maps con la ruta a $destination. Confirmá para continuar." |
| Aviso responsable (1 vez por sesión) | "Te ayudo a abrir Maps con la ruta. No reemplazo a un bastón ni a una persona acompañante." |
| Alias no guardado | "No tengo guardada $alias. ¿Querés guardar la ubicación actual como $alias?" |

---

## 8. Música / Spotify

### Comandos

| Frase | Intent |
|-------|--------|
| "abrí Spotify" | OPEN_SPOTIFY |
| "poné música" / "poné mi playlist" | PLAY_MUSIC |
| "pausá música" | PAUSE_MUSIC |
| "siguiente canción" | NEXT_SONG |
| "subí volumen" / "bajá volumen" | VOLUME_UP / VOLUME_DOWN |

### Reglas duras

1. **Spotify no instalado**: frase clara, no fallar silencioso.
2. **Sin Spotify**: caer a `MediaController` genérico
   (`MediaSessionManager.getActiveSessions`) si está disponible. Si no,
   sólo controlar volumen.
3. **Volumen** con tope: nunca subir más allá de
   `AudioManager.STREAM_MUSIC` máximo del sistema (`getStreamMaxVolume`).
   Cada `VOLUME_UP/DOWN` mueve **1 paso** de `adjustStreamVolume`.
4. **No depender de IA cloud** para nada de música.

### Implementación recomendada

```kotlin
class MusicActionExecutor(
    private val audioManager: AudioManager,
    private val packageManager: PackageManager,
    private val mediaSessionManager: MediaSessionManager?
) {
    fun openSpotify(): ActionResult { ... }
    fun playMusic(): ActionResult { ... }
    fun pauseMusic(): ActionResult { ... }
    fun nextSong(): ActionResult { ... }
    fun volumeUp(): ActionResult { ... }
    fun volumeDown(): ActionResult { ... }
}
```

### Frases

| Caso | Frase |
|------|-------|
| Spotify abierto | "Abrí Spotify." |
| Spotify no instalado | "No encontré Spotify instalado. Cuando lo tengas, te ayudo." |
| Música no controlable | "No tengo control sobre la música ahora. Probá tocar play en la app." |
| Volumen subido/bajado | "Listo." (corto, no decir el nivel — TalkBack ya lo lee) |
| Volumen ya en máximo/mínimo | "Está en el máximo." / "Está en el mínimo." |

---

## 9. Lectura de pantalla y OCR

Ya implementado en `AccessibilityScreenReader` +
`OjoClaroAccessibilityService` + `TextScanScreen` + `StableTextDetector`
+ `TextRecognitionAnalyzer`.

### Reglas duras

1. **OCR local** para texto físico (cámara). Nunca subir imagen a cloud
   sin consent explícito.
2. **AccessibilityService** sólo lee texto visible bajo pedido del
   usuario. No lee pasivamente.
3. **Consent obligatorio** antes de leer pantalla — `ConsentManager`
   con `SensitiveActionType.READ_VISIBLE_MESSAGE` ya lo cubre.
4. **Nunca leer contraseñas**: nodos con `isPassword=true` se descartan
   en `OjoClaroAccessibilityService.isReadableNode`.
5. **Risk awareness**: antes de leer en voz alta, `RiskDetector` corre.
   Si detecta `BANKING_SCREEN`, `PASSWORD_FIELD`, `VERIFICATION_CODE` y
   `PrivacyGuard.canReadAloud == false` → no se lee, se entra a
   `SAFE_MODE` con frase responsable.
6. **No persistir** OCR ni texto de pantalla. Buffers se descartan tras
   leer.
7. **Texto largo**: `PrivacyGuard.sanitizeForSpeech` corta a 1200
   chars. Para textos muy largos, leer por partes con "Hay más texto.
   Decime seguir para que lea más." (futuro — fase 7).
8. **`callar` corta inmediatamente** y limpia el buffer. Implementado.

### Flujo de lectura

```
Comando "qué dice la pantalla"
  ↓
verificar accesibilidad → si falta, guiar
  ↓
ConsentManager.requestAction(READ_VISIBLE_MESSAGE)
  ↓
WAITING_CONFIRMATION
  ↓
"confirmar" → AccessibilityScreenReader.readVisibleScreen
  ↓
RiskDetector → si requiresStrongConsent, SAFE_MODE
  ↓
PrivacyGuard.sanitizeScreenText + redactPasswords/codes/cards
  ↓
TTS habla → IDLE
```

### Frases (catálogo)

| Caso | Frase |
|------|-------|
| Empezar OCR | "Buscando texto con la cámara. Apuntá al texto." |
| Texto encontrado | "El texto dice: $sanitizedText" |
| No texto encontrado | "No encontré texto claro." |
| Pantalla sin texto | "No encontré texto visible en esta pantalla. Probá abrir un chat o una pantalla con texto." |
| Accesibilidad desactivada | "Para leer esta pantalla, activá Ojo Claro en Accesibilidad. Solo leo texto visible cuando vos me lo pedís." |
| Riesgo detectado (lectura permitida) | "Antes de responder, te aviso: este texto puede ser sensible. La pantalla dice: $sanitized" |
| Riesgo detectado (lectura bloqueada) | "Esta pantalla puede tener datos privados. Por ahora, hacelo desde la app correspondiente." |
| Lectura cancelada | "Acción cancelada." |

---

## 10. Memoria segura

### Tipos

```kotlin
enum class MemoryType {
    USER_PREFERENCE,
    TRUSTED_CONTACT,
    EMERGENCY_CONTACT,
    FREQUENT_DESTINATION,
    LOCATION_ALIAS,
    PREFERRED_APP,
    ROUTINE_PATTERN,
    WARNING_KEYWORD,
    SAFETY_RULE,
    REMINDER,
    ALARM,
    MEDICATION_REMINDER
}
```

`USER_PREFERENCE`, `TRUSTED_CONTACT`, `WARNING_KEYWORD`, `SAFETY_RULE`,
`ROUTINE_PATTERN` ya existen. Agregar `EMERGENCY_CONTACT`,
`FREQUENT_DESTINATION`, `LOCATION_ALIAS`, `PREFERRED_APP`, `REMINDER`,
`ALARM`, `MEDICATION_REMINDER`. `FREQUENT_COMMAND` se renombra como
`ROUTINE_PATTERN` para alinear con la spec — o se mantienen ambos si
hay tests dependientes.

### Modelo extendido

```kotlin
data class UserMemory(
    val id: String,
    val type: MemoryType,
    val label: String,           // corto, identificable
    val value: String,           // breve, NO datos sensibles
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val expiresAtMillis: Long?,  // null = no expira
    val isSensitive: Boolean,
    val userApproved: Boolean,
    val canBeSpoken: Boolean = true,
    val canBeUsedForSuggestions: Boolean = true,
    val requiresConfirmationBeforeUse: Boolean = false,
    val source: MemorySource = USER_COMMAND,
    val confidence: MemoryConfidence = HIGH,
    // Slots opcionales tipados — no en clear text:
    val locationLatRounded: Double? = null,   // 4 decimales
    val locationLngRounded: Double? = null,
    val phoneE164: String? = null              // sólo para TRUSTED_CONTACT/EMERGENCY_CONTACT
)
```

### Qué SÍ recordar

- "prefiero respuestas cortas" → USER_PREFERENCE
- "un contacto es contacto de confianza" → TRUSTED_CONTACT, label="un contacto"
- "mi mamá es contacto de emergencia" → EMERGENCY_CONTACT, label="mamá"
- "casa es esta ubicación" → LOCATION_ALIAS, label="casa", lat/lng
  redondeado
- "uso Spotify para música" → PREFERRED_APP, label="música",
  value="com.spotify.music"
- "los lunes a las 8 quiero alarma" → ALARM (recurrence=WEEKLY_MONDAY)
- "recordame tomar medicación a las 21" → MEDICATION_REMINDER,
  medicationName guardado, **dosis nunca**
- "avisame si aparece transferencia" → WARNING_KEYWORD
- "preguntame antes de abrir mapas" → SAFETY_RULE

### Qué NO recordar (`PrivacyGuard.canStoreMemory == false`)

Ya bloqueado por `MemoryPolicy.containsProhibitedContent`. La spec
agrega:
- ubicación con precisión > 4 decimales (= mejor que ~10m);
- contraseñas, PIN, códigos OTP/2FA, tarjetas, CBU/CVU, alias bancario;
- DNI, CUIT, CUIL, dirección personal exacta;
- chats completos, OCR completo, pantallas completas;
- imagen base64, audio;
- dosis de medicación específica ("tomá 200mg de paracetamol" → guardar
  sólo "paracetamol" como `medicationName`, nunca la dosis).

### Búsqueda

`LocalMemoryStore.findRelevant(query)` ya soporta:
- match por nombre del tipo (`USER_PREFERENCE`),
- match por keyword español (`preferencia`, `contacto`, `alerta`,
  `regla seguridad`, `app sensible`, `rutina`, `comando frecuente`).

Extender para los nuevos tipos:
- `EMERGENCY_CONTACT` → "emergencia, contacto emergencia"
- `LOCATION_ALIAS` → "ubicacion, lugar"
- `PREFERRED_APP` → "app preferida, aplicacion"
- `MEDICATION_REMINDER` → "medicacion, remedio, pastilla"
- `REMINDER` → "recordatorio, recordame"
- `ALARM` → "alarma, despertador"

### Política de privacidad (textual)

"Ojo Claro guarda solo cosas cortas y útiles que vos pediste explícitamente.
Nunca contraseñas, ni códigos, ni tarjetas, ni datos bancarios. Podés
borrar todo en cualquier momento diciendo 'borrá tu memoria'."

### Tests obligatorios (extender existentes)

Agregar en `MemoryPolicyTest`:
- `blocksLocationLatLngWithMoreThanFourDecimals`
- `blocksMedicationDose`
- `allowsLocationAliasWithRoundedCoords`
- `allowsEmergencyContactWithE164Number`

---

## 11. Recordatorios, alarmas y memoria prospectiva

**Esta sección es la más nueva y la más importante para que Ojo Claro
"se sienta como un cerebro".**

### Diferencia entre alarma y recordatorio

| Tipo | Backend Android | Cuándo usarlo |
|------|------------------|----------------|
| **Alarma exacta** | `AlarmClock.ACTION_SET_ALARM` (delega al reloj del sistema) | "poneme alarma mañana a las 8" — el usuario quiere un despertador real. |
| **Recordatorio exacto** | `AlarmManager.setExactAndAllowWhileIdle` con permiso `SCHEDULE_EXACT_ALARM` (Android 12+) | "recordame tomar medicación a las 21" — minuto exacto importa. |
| **Recordatorio flexible** | `WorkManager` con constraints + `setInitialDelay` | "preguntame a la noche si tomé la medicación" — rango aceptable de ±30 min. |
| **Notificación inmediata** | `NotificationManager` con channel propio | "avisame en 10 minutos" — corto plazo. |

### Permisos

- `POST_NOTIFICATIONS` (Android 13+): pedir la primera vez que se va a
  crear un recordatorio. Frase: "Para avisarte de tus recordatorios,
  activá las notificaciones de Ojo Claro."
- `SCHEDULE_EXACT_ALARM` (Android 12+) — solo para recordatorios de
  medicación que requieren minuto exacto. Si el sistema lo deniega
  (`AlarmManager.canScheduleExactAlarms() == false`), caer a
  `setWindow` con tolerancia de ±5 min y avisar:
  "Voy a avisarte cerca de las 21, no en el minuto exacto. Si querés el
  minuto exacto, activá 'alarmas exactas' para Ojo Claro en Ajustes."
- `USE_EXACT_ALARM` no usar (es para apps de calendario/reloj).

### Modelo de datos

```kotlin
data class Reminder(
    val id: String,
    val text: String,                          // "tomar medicación"
    val whenMillis: Long,                      // próximo disparo
    val recurrence: Recurrence,
    val medicationName: String? = null,        // sólo nombre
    val createdAtMillis: Long,
    val createdByCommand: String,              // raw original (para auditoría)
    val isMedical: Boolean,
    val active: Boolean = true,
    val lastFiredAtMillis: Long? = null,
    val lastDoneAtMillis: Long? = null,
    val snoozedUntilMillis: Long? = null
)

interface ReminderStore {
    fun save(r: Reminder)
    fun list(activeOnly: Boolean = true): List<Reminder>
    fun get(id: String): Reminder?
    fun delete(id: String)
    fun markDone(id: String, nowMillis: Long)
    fun snooze(id: String, untilMillis: Long)
}

class ReminderScheduler(
    private val context: Context,
    private val alarmManager: AlarmManager,
    private val workManager: WorkManager
) {
    fun schedule(r: Reminder)
    fun cancel(id: String)
    fun rescheduleNext(r: Reminder)   // para recurrence
}

object ReminderPolicy {
    fun canStore(r: Reminder): Boolean
    fun isMedicalAndSafe(r: Reminder): Boolean   // medicationName != null && no dosis
    fun maxRemindersPerUser(): Int = 32           // tope contra spam
}
```

### Reglas de comportamiento

1. **Confirmar antes de guardar** todo recordatorio. Frase canónica:
   "Voy a recordarte tomar la medicación todos los días a las 21.
   Confirmá para guardar."
2. **Editar / borrar**: "cancelá el recordatorio de medicación" → si
   match único por keyword, confirmar y borrar; si múltiple, listar y
   preguntar.
3. **Listar**: "qué recordatorios tengo" → "Tenés 3: medicación a las
   21 todos los días, llamar a un contacto mañana a las 17, salir a las 18."
   Cap a 5 elementos hablados; si hay más, "y 4 más. Decime ver todos
   para listarlos."
4. **Marcar como hecho**: cuando dispara la notificación, el usuario
   puede decir "ya lo hice" → `markDone` y deja de molestar hoy. Para
   recurrentes, sigue activo para mañana.
5. **Posponer**: "recordamelo más tarde" → snooze 15 min por default.
   "recordamelo en una hora" → parser extrae duración.
6. **Evitar molestar**:
   - sólo notifica entre 7:00 y 22:30 salvo override explícito
     ("alarma a las 5" sí dispara temprano);
   - máximo 1 notificación por minuto del mismo recordatorio;
   - si el usuario dice "callar" mientras suena, snooze 10 min
     automático.
7. **Recordatorios médicos**:
   - guardar sólo `medicationName` ("paracetamol"). **Nunca dosis,
     nunca frecuencia médica**, sólo el horario que el usuario pidió.
   - frase de notificación: "¿Ya tomaste tu $medicationName?"
     (pregunta, no orden).
   - **Nunca** sugerir cambios. Si el usuario dice "¿está bien que tome
     200mg?" → "No te puedo aconsejar sobre dosis. Eso es con tu
     médico."

### Reglas médicas (duras)

| Lo que NUNCA hace Ojo Claro | Lo que SÍ hace |
|------------------------------|------------------|
| Diagnosticar | Recordar lo que el usuario pidió |
| Sugerir dosis | Repetir el nombre que el usuario dictó |
| Cambiar tratamiento | Permitir borrar/pausar el recordatorio |
| Recomendar medicamentos | Sugerir "hablalo con tu médico" |
| Guardar dosis específica | Guardar sólo el nombre |

### Frases canónicas

| Caso | Frase |
|------|-------|
| Confirmación de creación | "Voy a recordarte $text $when. Confirmá para guardar." |
| Guardado | "Listo, lo voy a recordar." |
| Disparo (no médico) | "Te recuerdo: $text." |
| Disparo (médico) | "¿Ya tomaste tu $medicationName?" |
| Marcado como hecho | "Listo, lo marco como hecho." |
| Posponer | "¿Querés que te lo recuerde más tarde?" / "Te aviso de nuevo en 15 minutos." |
| Sin recordatorios | "No tengo guardado ningún recordatorio." |
| Listar (≤5) | "Tenés $n recordatorios: $list." |
| Listar (>5) | "Tenés $n recordatorios. Te leo los próximos 5: $first5." |
| Borrar único | "Voy a borrar el recordatorio de $text. Confirmá." |
| Borrar ambiguo | "Tenés varios con $keyword. ¿Cuál? $list." |
| Permiso notif. denegado | "No puedo avisarte si no me dejás mandar notificaciones." |
| Permiso exact-alarm denegado | "Voy a avisarte cerca de la hora, no en el minuto exacto. Para el minuto exacto, activá 'alarmas exactas' en Ajustes." |

---

## 12. Detección de patrones y sugerencias

Base existente: `FrequentPatternTracker` con `count`, `firstSeen`,
`lastSeen`, `isSensitive`, `userApprovedForSuggestions`.

### Reglas

1. **No sugerir antes de N=4 ocurrencias** del mismo patrón
   (`SUGGESTION_THRESHOLD`).
2. **Cooldown**: una vez sugerida una cosa, no volver a sugerir lo
   mismo durante 7 días salvo que el usuario la confirme primero.
3. **No sugerir patrones sensibles**: WhatsApp compose, llamadas con
   contactos sensibles, lecturas de pantalla. `canStorePattern` ya
   bloquea.
4. **No sugerir en momentos sensibles**: no proponer cuando el
   `appState` es `WAITING_CONFIRMATION`, `SAFE_MODE`, `ERROR_RECOVERABLE`.
5. **Pedir confirmación**: la sugerencia siempre cierra con
   "¿Querés que…?" — la respuesta `confirmar` la convierte en memoria
   o en alarma; `cancelar` la apaga 30 días.
6. **Desactivable**: "no me sugieras más cosas" → setear flag global
   `userPreferenceNoSuggestions = true` en memoria. Reactivar con
   "volvé a sugerir cosas".

### Ejemplos

| Patrón observado | Sugerencia |
|-------------------|-------------|
| 4 lunes seguidos pidió alarma 8:00 | "Los lunes solés pedir alarma a las 8. ¿Querés que la deje fija todos los lunes?" |
| 5 noches seguidas abrió Spotify a las 22:00–23:00 | "Solés escuchar música a la noche. ¿Querés que recuerde Spotify como tu app de música?" |
| 4 veces mandó WhatsApp a "un contacto" sin tenerla guardada | "Solés mandarle a un contacto. ¿Querés que la recuerde como contacto frecuente?" |
| 4 veces preguntó "dónde estoy" al salir | "¿Querés que cuando salgas de casa te ayude a abrir Maps?" |

### Algoritmo

```
on intent ejecutado con éxito:
    pattern = FrequentPatternTracker.recordCommand(...)
    if pattern.count >= SUGGESTION_THRESHOLD
        and !userPreferenceNoSuggestions
        and PrivacyGuard.canStorePattern(pattern)
        and lastSuggestedAt(pattern.id) older than 7 days
        and currentAppState in [IDLE, SPEAKING]:
        emit suggestion (uno por sesión máximo)
```

### Tests

- `frequentPatternBelowThresholdDoesNotSuggest`
- `sensitivePatternNeverSuggests`
- `cooldownPreventsRepeatSuggestion`
- `userCanDisableSuggestionsGlobally`
- `cancelledSuggestionDoesNotRepeatFor30Days`

---

## 13. Manejo de errores

Cada error tiene 5 atributos: **frase corta**, **acción sugerida**,
**¿reescuchar?**, **¿pedir permiso?**, **¿limpiar pending?**.

| Error | Frase | Acción | Reescuchar | Pedir permiso | Limpiar pending |
|-------|-------|--------|------------|----------------|------------------|
| No entendí | "No entendí. Decime, por ejemplo, mandale a un contacto que estoy llegando." | reintentar | sí | no | no |
| No escuché | "No escuché un comando claro." | reintentar | sí | no | no |
| Mic denegado | `MICROPHONE_PERMISSION_MESSAGE` | abrir launcher de permiso | no | sí | no |
| TTS no listo | (silencio: el TTS arrancará y emitirá luego) | esperar `onInit` | sí | no | no |
| Cámara denegada | `MSG_CAMERA_MISSING` | abrir launcher de permiso | no | sí | no |
| Accesibilidad off | `MSG_ACCESSIBILITY_MISSING` | abrir Ajustes accesibilidad | sí | sí (manual) | sí |
| WhatsApp no instalado | `MSG_WHATSAPP_MISSING` | nada | sí | no | sí |
| Spotify no instalado | "No encontré Spotify instalado." | nada | sí | no | sí |
| Maps no instalado | "No encontré Google Maps instalado." | abrir chooser de mapas si hay | sí | no | sí |
| Ubicación apagada | "No tengo GPS disponible. Activalo desde Ajustes." | abrir Ajustes | sí | no | sí |
| Permiso notif. denegado | "Para avisarte, activá las notificaciones de Ojo Claro." | abrir Ajustes app | sí | sí | sí |
| Exact alarm denegado | "Voy a avisarte cerca de la hora, no exacto. Activá 'alarmas exactas' si querés precisión." | abrir Ajustes especiales | sí | sí | no |
| Sin internet | (sólo aplica a backend opcional, fallback local toma el control) | usar local | sí | no | no |
| IA cloud sin créditos | (interno: no hablar) | fallback local | sí | no | no |
| Acción vencida | "La acción pendiente venció. Volvé a pedirla." | nada | sí | no | sí |
| Confirmación inválida | "No hay ninguna acción pendiente para confirmar." | nada | sí | no | no |
| Usuario `callar` | (silencio) | TTS.stop, mic cancel | sí (siguiente comando) | no | sí |
| `SAFE_MODE` | "Esta pantalla puede tener datos privados. Por ahora, hacelo desde la app correspondiente." | nada | sí | no | sí |

---

## 14. Arquitectura técnica recomendada

```
ui/
  HomeScreen.kt                 — Compose, ya wired
  HomeViewModel.kt              — orquesta estado y eventos, ya wired

domain/
  AgentConversationManager.kt   — NUEVO: máquina de estados unificada
  AssistantOrchestrator.kt      — existente, evoluciona como façade
  OrchestratorOutcome.kt        — existente

agent/                          — paquete nuevo
  AgentIntent.kt                — enum
  AgentSlot.kt                  — data class
  AgentState.kt                 — enum (la §3)
  AgentEvent.kt                 — sealed class (input del manager)
  AgentOutcome.kt               — sealed class (output)
  LocalIntentParser.kt          — NUEVO: parser regex/heurístico
  AiIntentInterpreter.kt        — NUEVO (fase 8): cliente IA mini

external/
  CommandRouter.kt              — existente; pasa a ser un módulo de
                                  LocalIntentParser
  WhatsAppActionExecutor.kt     — renombrar de WhatsAppIntentHelper
  PhoneActionExecutor.kt        — NUEVO
  MapsActionExecutor.kt         — NUEVO
  MusicActionExecutor.kt        — NUEVO
  ScreenReaderActionExecutor.kt — NUEVO (façade sobre AccessibilityScreenReader)
  AppLauncherActionExecutor.kt  — NUEVO (OPEN_APP genérico)

memory/
  MemoryStore.kt                — existente
  LocalMemoryStore.kt           — existente, extender tipos
  MemoryPolicy.kt               — existente
  ReminderStore.kt              — NUEVO
  ReminderScheduler.kt          — NUEVO
  ReminderPolicy.kt             — NUEVO
  RoutinePatternTracker.kt      — renombrar FrequentPatternTracker

consent/
  ConsentManager.kt             — existente
  ConsentPhrases.kt             — existente, agregar nuevas frases

privacy/
  PrivacyGuard.kt               — existente

risk/
  RiskDetector.kt               — existente

speech/
  SpeechController.kt           — existente
  SpeechLoopController.kt       — NUEVO: wrapper sobre VoiceCommandController
                                  + SpeechController para coordinar todo

voice/
  VoiceCommandController.kt     — existente
  VoiceCommandDispatcher.kt     — existente
  OjoClaroIntents.kt            — existente
  OjoClaroQuickTileService.kt   — existente
```

### Responsabilidades por clase nueva

#### `AgentConversationManager`
- **Hace**: dado un `AgentEvent` (texto del usuario, callback de TTS,
  callback de timer), avanza la máquina de estados y emite un
  `AgentOutcome` (qué hablar, qué intent ejecutar, qué pending crear).
- **No hace**: hablar, ejecutar intents, persistir nada.
- **Inputs**: `AgentEvent`, `AgentState` actual, `MemoryStore`,
  `ReminderStore`, `ConsentManager`.
- **Outputs**: `AgentOutcome` (texto, nuevo estado, executor a llamar).
- **Tests**: `AgentConversationManagerTest` — un test por transición.

#### `LocalIntentParser`
- **Hace**: convierte texto a `(AgentIntent, AgentSlot, Confidence)`.
- **No hace**: ejecutar acciones, hablar, persistir.
- **Inputs**: `String`, `MemoryStore` (para alias).
- **Outputs**: `ParsedCommand`.
- **Tests**: extender `CommandRouterTest`.

#### `PhoneActionExecutor`
- **Hace**: prepara `Intent.ACTION_DIAL`. Resuelve número desde
  contactos o memoria.
- **No hace**: `ACTION_CALL`. Llamar automáticamente. Pedir
  `CALL_PHONE`.
- **Inputs**: contactName/alias, `Context`, `ContentResolver`.
- **Outputs**: `ActionResult.Success | Failed | NotInstalled`.
- **Tests**: `PhoneActionExecutorTest`.

#### `MapsActionExecutor`
- **Hace**: `geo:0,0?q=...` con `setPackage(maps)` opcional.
  Resuelve alias desde memoria.
- **No hace**: GPS continuo, ubicación en background, ruta interna.
- **Tests**: `MapsActionExecutorTest`.

#### `MusicActionExecutor`
- **Hace**: launch Spotify, controla volumen, comandos media genéricos.
- **No hace**: streaming propio, scrobbling, login a Spotify.
- **Tests**: `MusicActionExecutorTest`.

#### `ReminderScheduler`
- **Hace**: programa `WorkManager` o `AlarmManager`. Cancela.
  Re-programa recurrentes en cada disparo.
- **No hace**: persistir el reminder (eso es `ReminderStore`).
- **Tests**: `ReminderSchedulerTest` con `WorkManagerTestInitHelper`.

#### `SpeechLoopController`
- **Hace**: une `VoiceCommandController` + `SpeechController` con un
  contrato simple: `onUserText(text)`, `onSpeak(text)`, `onStop()`.
  Arregla el contrato de threading.
- **No hace**: parsear comandos.
- **Tests**: `SpeechLoopTest`.

---

## 15. Contrato para IA futura

### Flujo

```
texto del usuario
  ↓
LocalIntentParser.parse(text, memory) → ParsedCommand(confidence)
  ↓ confidence == HIGH
  ejecutar local
  ↓ confidence < HIGH
  PrivacyGuard.sanitizeForCloud(context) → ReducedContext
  ↓
  AiIntentInterpreter.interpret(text, ReducedContext)
  ↓
  IA devuelve JSON estructurado
  ↓
  AppValidator.validate(json) → ParsedCommand
  ↓
  ConsentManager.requestAction si requiere
  ↓
  Executor ejecuta
```

### Esquema JSON esperado

```json
{
  "intent": "COMPOSE_WHATSAPP_MESSAGE",
  "slots": {
    "contactName": "un contacto",
    "messageText": "estoy llegando"
  },
  "confidence": 0.84,
  "missingSlots": [],
  "riskFlags": [],
  "userFacingClarification": null,
  "shouldAskConfirmation": true
}
```

```json
{
  "intent": "CALL_CONTACT",
  "slots": { "contactName": "mi novia" },
  "confidence": 0.62,
  "missingSlots": [],
  "riskFlags": [],
  "userFacingClarification": "Tenés varias contactas con ese alias. ¿Cuál querés llamar?",
  "shouldAskConfirmation": true
}
```

### Reglas duras al llamar IA

1. **Nunca enviar pantalla completa** — el contexto se reduce a
   `lastUserText`, `currentAppState`, `recentIntents (últimos 3)`,
   `memoriasRelevantes (sólo summaries seguras)`.
2. **Nunca enviar contraseñas/códigos/tarjetas/CBU/CVU/saldos** —
   `PrivacyGuard.sanitizeForCloud` corre antes.
3. **Nunca enviar audio** — sólo texto reconocido.
4. **Nunca enviar imagen** salvo `consentGranted == true` y
   `allowCloud == true`.
5. **Sin créditos** o sin internet → fallback automático a
   `LocalIntentParser` con `Confidence.LOW` y respuesta canónica de
   "no entendí".
6. **Validación dura** del JSON: si la IA devuelve un `intent` que no
   existe o slots prohibidos (ej. `intent=PAY_BILL`), descartar y caer
   a `UNKNOWN`.
7. **Timeout** de 3 segundos. Si la IA tarda más, fallback local.
8. **Logging**: contar requests por día. Tope diario configurable.
   Cuando se llega: silenciosamente fallback local hasta el día
   siguiente.

### Tests

- `aiInterpreterFallsBackOnTimeout`
- `aiInterpreterRejectsUnknownIntent`
- `aiInterpreterStripsSensitiveDataFromContext`
- `aiInterpreterFailsClosedWhenNoInternet`

---

## 16. Tests obligatorios

### Categorías existentes a extender

- `CommandRouterTest` — agregar variantes de llamadas, mapas, música,
  recordatorios.
- `AssistantOrchestratorTest` — agregar flow de
  `WAITING_CONTACT/MESSAGE/DESTINATION/TIME`.
- `MemoryPolicyTest` — agregar bloqueos de coords > 4 decimales y
  dosis médicas.
- `PrivacyGuardTest` — agregar `sanitizeForCloud`.
- `RiskDetectorTest` — agregar reconocimiento de intentos médicos
  raros.
- `FrequentPatternTrackerTest` → renombrar a
  `RoutinePatternTrackerTest`. Agregar cooldown y desactivación
  global.
- `VoiceCommandControllerTest`, `VoiceCommandDispatcherTest` —
  mantener.

### Nuevas suites

- `AgentConversationManagerTest`
- `LocalIntentParserTest`
- `ReminderParserTest`
- `ReminderSchedulerTest`
- `WhatsAppActionExecutorTest`
- `PhoneActionExecutorTest`
- `MapsActionExecutorTest`
- `MusicActionExecutorTest`
- `ScreenReaderActionExecutorTest`
- `SpeechLoopTest`
- `AiIntentInterpreterTest` (con fakes — no llamadas reales).

### Casos críticos no negociables

| Test | Qué prueba |
|------|-------------|
| `siDoesNotConfirmSensitiveAction` | "sí" no confirma. |
| `confirmConfirmsAction` | "confirmar" sí confirma. |
| `callarStopsTtsImmediately` | "callar" corta TTS y limpia pendings. |
| `whatsAppNeverSendsAutomatically` | Ningún path emite `ACTION_SEND` sin paso por `ConsentManager`. |
| `callContactUsesActionDial` | Nunca `ACTION_CALL`, siempre `ACTION_DIAL`. |
| `mapsActionNeverPromisesSafety` | El texto generado nunca dice "te llevo seguro" / "estoy con vos". |
| `locationRequestAsksPermission` | Si falta `ACCESS_FINE_LOCATION`, frase y pendiente de permiso. |
| `aiInterpreterDoesNotReceiveSensitiveData` | El payload a cloud filtra todo lo sensible. |
| `medicationReminderDoesNotStoreDose` | Aunque el usuario dicte dosis, sólo se guarda el nombre. |
| `weeklyAlarmIsParsedAndScheduled` | "todos los lunes a las 8" → `Recurrence.WEEKLY_MONDAY` + alarma reprogramable. |
| `frequentPatternRespectsCooldown` | Una sugerencia rechazada no vuelve a aparecer en 30 días. |
| `incompleteCommandAsksMissingSlot` | "mandale a un contacto" → `WAITING_MESSAGE`. |
| `safeModeBlocksRiskyReadAloud` | Si `RiskDetector` marca `READ_BANKING_SCREEN`, lectura no se ejecuta. |

---

## 17. Plan de implementación por fases

### Fase 1 — Normalizar modelo de intenciones y estados
- **Archivos**: `agent/AgentIntent.kt`, `agent/AgentSlot.kt`,
  `agent/AgentState.kt`, `agent/AgentEvent.kt`, `agent/AgentOutcome.kt`,
  `agent/LocalIntentParser.kt`, `agent/AgentConversationManager.kt`.
- **Tests**: `AgentConversationManagerTest`, `LocalIntentParserTest`.
- **Éxito**: todos los tests existentes siguen verdes; manager
  resuelve los flujos actuales (WhatsApp, lectura, memoria) sin tocar
  ejecutores.
- **No tocar**: `WhatsAppIntentHelper`, `AccessibilityScreenReader`,
  `MemoryStore`, `ConsentManager`.

### Fase 2 — WhatsApp y llamadas
- **Archivos**: `external/WhatsAppActionExecutor.kt` (renombre desde
  `WhatsAppIntentHelper`), `external/PhoneActionExecutor.kt`.
- **Tests**: `PhoneActionExecutorTest`, extender `CommandRouterTest`.
- **Éxito**: "llamá a un contacto" abre `ACTION_DIAL` con número resuelto y
  pasa por confirm.
- **No tocar**: lectura de pantalla, OCR, memoria.

### Fase 3 — Mapas / ubicación
- **Archivos**: `external/MapsActionExecutor.kt` + permission flow en
  `HomeScreen`.
- **Tests**: `MapsActionExecutorTest`, `LocationPermissionFlowTest`.
- **Éxito**: "llevame a casa" abre Maps con ruta o pide permiso o
  pregunta destino.
- **No tocar**: WhatsApp, llamadas, alarmas.

### Fase 4 — Spotify / música
- **Archivos**: `external/MusicActionExecutor.kt`.
- **Tests**: `MusicActionExecutorTest`.
- **Éxito**: comandos básicos funcionan; falla con frase clara si
  Spotify no está.

### Fase 5 — Recordatorios y alarmas
- **Archivos**: `memory/Reminder.kt`, `memory/ReminderStore.kt`,
  `memory/ReminderScheduler.kt`, `memory/ReminderPolicy.kt`,
  `memory/ReminderParser.kt`, `agent/ReminderConversation.kt`.
- **Permisos**: agregar `POST_NOTIFICATIONS` (Android 13+),
  `RECEIVE_BOOT_COMPLETED` para reprogramar tras reinicio.
- **Tests**: `ReminderParserTest`, `ReminderSchedulerTest`,
  `ReminderPolicyTest`.
- **Éxito**: "recordame tomar la medicación a las 21 todos los días"
  crea reminder, dispara notificación a las 21:00 ±5min, "ya lo hice"
  marca done.
- **No tocar**: WhatsApp, mapas, música.

### Fase 6 — Rutinas y sugerencias
- **Archivos**: `memory/RoutinePatternTracker.kt`,
  `agent/SuggestionEngine.kt`.
- **Tests**: `RoutinePatternTrackerTest`, `SuggestionEngineTest`.
- **Éxito**: tras N=4 alarmas lunes 8:00 el agente sugiere fijarla;
  cooldown 30 días tras "no, gracias".

### Fase 7 — Lectura/OCR refinada
- **Archivos**: extender `AccessibilityScreenReader`,
  `TextRecognitionAnalyzer`.
- **Cambios**: lectura por partes ("decime seguir"), summary breve para
  pantallas largas, indicador hablado de longitud restante.
- **Tests**: extender `StableTextDetectorTest`.

### Fase 8 — IA mini como fallback
- **Archivos**: `agent/AiIntentInterpreter.kt`,
  `privacy/PrivacyGuard.sanitizeForCloud`.
- **Cambios**: feature flag, tope diario, timeout 3s.
- **Tests**: `AiIntentInterpreterTest` con fake.
- **Éxito**: comandos ambiguos pasan a IA mini, IA responde JSON
  válido, app valida y ejecuta — y todo el camino respeta privacy.

### Fase 9 — QA físico y demo
- Probar en `R5CW22SMWDM` o equivalente con TalkBack y voz real.
- Checklist de §12 del doc `VOICE_FIRST_DEMO_READINESS.md`.
- Revisar latencia real del `callar`, naturalidad del TTS argentino,
  comportamiento del Quick Tile y del botón flotante.

---

## 18. Qué NO hacer todavía

- **No** hotword permanente ("hey Ojo Claro" siempre activo).
- **No** captura del power button.
- **No** envío automático de WhatsApp.
- **No** llamadas con `ACTION_CALL` ni `CALL_PHONE` permission.
- **No** integración con bancos.
- **No** pagos (Mercado Pago, Modo, etc.).
- **No** biometría (`BiometricPrompt`) — sigue siendo fase 2 cuando
  haya `READ_BANKING_SCREEN` real.
- **No** guardar contraseñas / códigos / tarjetas / CBU / CVU / DNI.
- **No** llamar a IA cloud por default. Sólo bajo feature flag y
  explicit consent del usuario.
- **No** prometer seguridad total en calle.
- **No** automatizar gestos del sistema con `dispatchGesture`.
- **No** taps automáticos de UI.
- **No** romper el modelo local-first: todo flujo central debe
  funcionar sin internet.

---

## 19. Resultado final esperado

Ojo Claro debe sentirse como un agente real porque **recuerda, pregunta,
confirma, se calla, se recupera, sugiere y ejecuta acciones útiles** —
no porque mande todo a una IA cara.

Cuando un usuario ciego abra Ojo Claro, debe poder:
- decir "mandale a mi mamá que llego en 10" y confirmar una vez,
- decir "llamá a un contacto" y tener el dialer listo en menos de 2 segundos,
- decir "llevame a casa" y abrir Maps con la ruta sin contar su
  ubicación a un servidor,
- decir "recordame tomar la medicación a las 21" y recibir una pregunta
  cuidadosa esa noche sin que la app se invente dosis,
- decir "callar" y obtener silencio inmediato,
- saber que Ojo Claro **nunca** envió un WhatsApp solo, **nunca** llamó
  solo, **nunca** mandó su pantalla a un servidor.

Si esto pasa, el agente es real. Si esto no pasa, hay que volver a leer
este documento y arreglar la fase que falló.
