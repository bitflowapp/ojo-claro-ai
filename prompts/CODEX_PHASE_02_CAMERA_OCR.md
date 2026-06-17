# Prompt para Codex - Fase 2

Objetivo:
Implementar flujo real de cámara + OCR local.

Tareas Android:
1. Crear pantalla/módulo CameraX.
2. Integrar `TextRecognitionAnalyzer`.
3. Capturar texto visible.
4. Enviar comando `READ_TEXT` al backend con texto detectado en `userMessage`.
5. Hacer que TalkBack lea estados importantes.

Tareas iOS:
1. Crear servicio de cámara con AVFoundation.
2. Capturar frame.
3. Usar Vision `VNRecognizeTextRequest`.
4. Enviar texto reconocido al backend.
5. Mantener VoiceOver-first.

Criterios:
- No se guarda imagen en disco.
- OCR funciona en Android.
- iOS queda listo para Xcode.
- Respuestas se reproducen por voz.
