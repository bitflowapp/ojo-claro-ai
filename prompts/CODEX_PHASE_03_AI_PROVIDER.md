# Prompt para Codex - Fase 3

Objetivo:
Conectar proveedor IA multimodal real en backend manteniendo mock seguro.

Tareas:
1. Implementar `AiProvider` abstracto.
2. Implementar `OpenAiVisionProvider`.
3. Mantener fallback mock si no hay `OPENAI_API_KEY`.
4. Agregar timeout, manejo de errores y respuesta segura.
5. Evitar guardar imágenes.
6. Agregar tests con proveedor fake.
7. Documentar variables `.env`.

Criterios:
- Sin claves hardcodeadas.
- Tests pasan.
- Backend sigue funcionando sin internet ni clave.
- Respuestas tienen `confidence`, `category` y `safetyNotice` cuando corresponda.
