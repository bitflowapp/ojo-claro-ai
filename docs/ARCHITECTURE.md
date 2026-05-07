# Arquitectura técnica

## Objetivo

Crear una plataforma AI-native de asistencia visual accesible.

## Capas

### Android

- Kotlin
- Jetpack Compose
- CameraX
- ML Kit Text Recognition
- TalkBack-first UI
- TextToSpeech
- SpeechRecognizer

### iOS

- SwiftUI
- AVFoundation
- Vision
- Core ML
- VoiceOver-first UI
- AVSpeechSynthesizer
- Speech framework

### Shared

Kotlin Multiplatform contiene:

- modelos de contrato
- parser de comandos
- prompt builder
- cliente API
- lógica reutilizable

### Backend

FastAPI contiene:

- contratos públicos
- orquestación IA
- seguridad
- proveedor multimodal
- historial seguro
- planes comerciales

## Flujo principal

```txt
Usuario habla
  -> parser local detecta intención
  -> app decide si requiere cámara/ubicación
  -> captura imagen/texto local si corresponde
  -> backend procesa con IA
  -> respuesta corta, hablada y con nivel de confianza
  -> app reproduce con voz
```

## Reglas de seguridad

1. La app debe expresar incertidumbre.
2. La app no debe inventar objetos, textos, personas ni riesgos.
3. La app no debe guiar cruces de calle como si fuera navegación autónoma.
4. La app debe usar frases cortas en contextos de emergencia.
5. La app debe poder funcionar parcialmente sin internet.
