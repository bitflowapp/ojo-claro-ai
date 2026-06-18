# Estela — Diseño de Cloud TTS opcional (V1.9, NO activado)

Estado: **diseño aprobado para implementación futura. Hoy no hay ninguna llamada
cloud de TTS, ningún costo nuevo y ningún cambio de comportamiento.**

## Por qué

El TTS de Android en el Moto G15 ya usa la mejor voz española offline
disponible (`SpeechController.selectPremiumVoice`: es-AR > es-419/es-US > es-ES,
solo voces offline, calidad máxima del engine; rate 0.97 / pitch 1.0). Suena
correcto pero genérico. Una voz neural cloud daría identidad, a cambio de
latencia, costo y un riesgo de privacidad que hay que acotar por diseño.

## Arquitectura propuesta

```
GlobalAssistantService / OutdoorForegroundService
        └── SpeechController (API pública SIN CAMBIOS: speak/stop/isSpeaking/callbacks)
              └── EstelaTtsEngine (interfaz nueva)
                    ├── AndroidTtsEngine   ← default SIEMPRE; es el código actual
                    └── CloudTtsEngine     ← opcional, detrás de config
```

- `SpeechController` conserva su contrato exacto (dedup, utteranceId,
  onSpeechStarted/Finished/Stopped): **el ciclo de vida del turno single-shot de
  V1.7/V1.8 depende de esos callbacks y no se toca.**
- `CloudTtsEngine` sintetiza a archivo/stream y reproduce con `MediaPlayer`/
  `AudioTrack`, emitiendo los mismos callbacks. Si la red falla o tarda más de
  ~1,2 s en empezar, **fallback automático a AndroidTtsEngine en la misma
  utterance** (la persona nunca se queda sin voz).
- Config: `estela_tts_engine = "android" | "cloud"` con default `"android"`,
  vía BuildConfig/ajuste local. Sin clave configurada ⇒ android, sin error.

## Reglas de privacidad (no negociables)

1. **Nunca** enviar a cloud TTS: borradores/contenidos de WhatsApp, nombres de
   contactos, etiquetas de calle de "dónde estoy", ni nada que pase por
   `looksSensitive`. Regla práctica: solo textos de SISTEMA (frases fijas de
   Estela) son elegibles; todo texto con interpolación de datos del usuario va
   al engine local.
2. Cache solo de frases comunes NO sensibles, pregeneradas en build o primera
   vez: "Dame un momento.", "Te escucho.", "No pude hacerlo.",
   "Para enviarlo decí: enviá. Para cancelar decí: cancelar.", earcons hablados.
   Cache por hash del texto, en almacenamiento interno de la app.
3. No loguear texto completo: solo longitudes y engine usado.
4. Si el dispositivo está offline ⇒ android directo (la calle no puede depender
   de señal).

## Recomendación de proveedor

| Proveedor | Pros | Contras | Veredicto |
|---|---|---|---|
| **Google Cloud TTS (Neural2/WaveNet es-US-Neural2)** | Voces es-US/es-419 neurales muy buenas, SDK estable, latencia baja, SSML completo (pausas), precio bajo por carácter | Sin es-AR específico | **Recomendado para V2**: mejor relación calidad/costo/integración Android |
| ElevenLabs | Calidad top, voces clonables con acento rioplatense | Costo por carácter alto, latencia mayor, API menos pensada para móvil | Para demos/branding puntual, no para guía en calle |
| OpenAI TTS | Buena calidad, simple | Sin control fino de prosodia/SSML, es neutro | Aceptable plan B |

Riesgos clave: latencia en calle (mitigado por fallback + cache), costo
descontrolado (mitigado por allowlist de frases de sistema + cache), privacidad
(mitigado por la regla 1), y divergencia de callbacks que rompa el ciclo de
turnos (mitigado: `SpeechController` es el único dueño de callbacks).

## Qué NO hacer

- No reemplazar el TTS local para navegación outdoor: la guía debe funcionar
  sin datos.
- No sintetizar en cloud nada que el usuario dictó.
- No activar por defecto ni sin presupuesto explícito de Marco.
