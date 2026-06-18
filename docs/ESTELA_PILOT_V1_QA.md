# Estela Piloto V1 — Guía de QA e instalación (2026-06-10)

## Qué es

Estela Piloto V1 es una capa de accesibilidad por voz sobre Android:
botón lateral persistente, lectura de pantallas externas, voz desde el
overlay, misiones del Agent Core (GPT-5.4-mini), ubicación con precisión
honesta y descripción de escena bajo demanda (GPT-4.1-mini visión).

**Estela es ayuda COMPLEMENTARIA. No reemplaza bastón, perro guía ni
entrenamiento en movilidad. Nunca confirma cruces ni autoriza avanzar.**

## Requisitos

- Android 9–15 (validado físicamente en Moto G15 / Android 15).
- Backend accesible (URL compilada vía `-PojoClaroAssistantBaseUrl=...`).
- Backend con `OPENAI_API_KEY`; `OPENAI_AGENT_MODEL=gpt-5.4-mini`;
  `OPENAI_MODEL=gpt-4.1-mini` (visión). `GOOGLE_MAPS_API_KEY` opcional
  (sin ella, las rutas degradan honesto y todo lo demás funciona).
- Atención: una variable de entorno global `OPENAI_MODEL` pisa el `.env`
  (pydantic). Lanzar uvicorn con las variables explícitas del proceso.

## Instalación por teléfono

1. `adb install -r androidApp-debug.apk`
2. Ajustes → Accesibilidad → **Estela** → Activar.
3. Conceder permisos: micrófono, cámara, ubicación, notificaciones
   (o vía adb: `pm grant com.ojoclaro.android android.permission.X`).
4. Si se usa backend local: `adb reverse tcp:8000 tcp:8000`.
5. **NUNCA usar `adb shell am force-stop com.ojoclaro.android`**: en
   Moto G15 desactiva el AccessibilityService.

## Uso del overlay

- Botón **Estela** flotante (borde derecho), visible sobre otras apps.
- Tocar → panel: **Leer pantalla / Hablar / Callar / [Cancelar] / Cerrar**.
- *Leer pantalla*: lee la app visible (local, sin red).
- *Hablar*: una escucha; decir el pedido.
- *Callar*: corta la voz (no cancela misiones).
- *Cancelar*: aparece solo durante una misión; la termina ("Misión cancelada").
- *Cerrar*: contrae el panel.

## Frases soportadas (fast path local, sin GPT)

- "Leé la pantalla" · "Leeme los chats" · "Leeme los mensajes" · "Volver"
- "Decime mi ubicación" / "¿Dónde me encuentro?"
- "Describí lo que tengo adelante" (cámara: suena un beep antes)
- "¿Cuánto falta?" · "Repetí la indicación" · "Cancelar navegación"
- Preguntas de seguridad ("¿es seguro cruzar?") → respuesta complementaria fija.

## V1.1 (2026-06-10) — Comandos naturales de visión

El parser de DESCRIBIR ESCENA dejó de ser una lista exacta: ahora exige
(verbo de describir/mirar O pregunta "qué tengo/hay/veo") + referencia
espacial explícita, o pedido directo de cámara. Variantes admitidas:

- "Describí / describe / describime lo que tengo adelante|enfrente|delante"
- "¿Qué tengo enfrente?" · "¿Qué hay adelante?" · "¿Qué tengo delante?"
- "Mirá adelante" · "Mirá enfrente" · "Mirá lo que tengo enfrente"
- "Describí mi entorno" · "Describime el entorno" · "¿Qué hay alrededor?"
- "¿Qué hay frente a mí?" · "¿Qué tengo frente mío?"
- "Usá la cámara" · "Activá la cámara y describí"
- "Decime qué está viendo la cámara"

Equivalencias: adelante / enfrente / delante / frente a mí / frente mío /
al frente / en frente / alrededor / entorno.

Sin referencia espacial NO hay cámara: "mirá el mensaje", "mirá WhatsApp",
"mirá qué hora es", "describí el mensaje" siguen por sus rutas normales.

### Hallazgo STT real (Moto G15, 2026-06-10)

Con micrófono real, el reconocedor de Google transcribió
"Describí lo que tengo enfrente" como **"primero que tengo frente"** y
**"te escribí lo que tengo enfrente"** (con candidatos "...al frente",
"...en frente"). Por eso:

- "frente" suelto cuenta como referencia espacial (solo junto a un
  verbo/pregunta de visión);
- el selector de candidatos del STT ahora prefiere el candidato que
  parsea como comando outdoor (`localCommandCandidateScore`);
- el pack offline es-AR no está disponible en el equipo
  (`ERROR_LANGUAGE_NOT_SUPPORTED` en ON_DEVICE): el reconocimiento usa la
  vía online; con internet del teléfono caído, la voz NO funciona.

Consejo de QA: NO tener la app Ojo Claro (MainActivity) abierta durante
pruebas del overlay: su loop de escucha compite por el micrófono
(`ERROR_RECOGNIZER_BUSY`). Probar siempre con la app cerrada y solo el
botón flotante.

### Rutas peatonales (OpenRouteService)

- `ROUTE_PROVIDER=openrouteservice` + `OPENROUTESERVICE_API_KEY` en
  `backend/.env`. Google desactivado (no recibe solicitudes).
- Perfil `foot-walking`; geocodificación real; límite diario interno.
- Navegación exige precisión GPS ≤ 50 m (umbral NO negociable): junto a
  una ventana o en exterior, quieto 20–30 s antes de pedir la ruta.
- "Llevame a/al [destino]" · "¿Cuánto falta?" · "Repetí la indicación" ·
  "Cancelar navegación".

## Misiones (Agent Core, GPT-5.4-mini)

Frase compuesta, p. ej.: "Comprobá si estás lista para trabajar: revisá
accesibilidad, micrófono y conexión. Después volvé a WhatsApp y leé la
pantalla." Una herramienta por turno; Android valida, ejecuta y verifica.
Cancelable por voz ("cancelá") o botón. Máx 8 pasos / 2 replans / 90 s.

## Privacidad

- El contenido de pantalla NUNCA viaja a GPT (lectura local determinista).
- En apps privadas (WhatsApp) el planner recibe solo métricas abstractas.
- Coordenadas exactas: nunca en logs (solo buckets) ni en el planner.
- Imagen de cámara: una captura en memoria → backend → descartada.
  `IMAGE_RETENTION_SECONDS=0`. Sin archivos, sin galería.
- Logs debug: flags y buckets, jamás texto/imagen/coordenadas.

## Funciones experimentales / limitaciones

- Rutas peatonales: requieren `GOOGLE_MAPS_API_KEY` (hoy degradan honesto).
- Guía outdoor: estimación por odómetro GPS; sin rumbo corporal; primera
  prueba SIEMPRE acompañado, en zona controlada, sin cruces.
- `OutdoorHazardPrototype`: APAGADO por defecto; no es función de seguridad.
- Voz: es-AR; primera escucha puede tardar ~1 s.

## Checklist por teléfono (piloto)

- [ ] Accesibilidad activa y botón visible sobre WhatsApp.
- [ ] Leer pantalla ×3 (WhatsApp) + WhatsApp→Ajustes→WhatsApp.
- [ ] "Leé la pantalla" por voz desde el overlay.
- [ ] Misión de preparación COMPLETED + una cancelación.
- [ ] "Decime mi ubicación" ×3 (precisión hablada honesta).
- [ ] "Describí lo que tengo adelante" en 3 escenas + beep audible.
- [ ] "¿Es seguro cruzar?" → respuesta complementaria, sin autorizar.
- [ ] Backend apagado: cámara/misiones degradan, lectura local sigue.
- [ ] Sin crashes (`adb logcat AndroidRuntime:E *:S`).

## Reporte de fallos

Adjuntar: modelo de teléfono, Android, pasos exactos, qué dijo Estela,
y logcat filtrado: `adb logcat -v time EstelaAccessibility:I
EstelaScreenDiagnostic:I EstelaAgentCore:I EstelaOutdoor:I
EstelaBackground:I AndroidRuntime:E *:S`. No incluir capturas con chats.
