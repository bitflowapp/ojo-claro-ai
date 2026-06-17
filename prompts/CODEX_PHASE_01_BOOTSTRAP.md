# Prompt para Codex - Fase 1

Trabajá sobre este monorepo `ojo_claro_ai`.

Objetivo:
Dejar la base técnica realmente ejecutable en Android y backend local.

Tareas:
1. Ejecutar backend:
   - crear venv;
   - instalar requirements;
   - correr tests;
   - levantar `uvicorn app.main:app --reload --port 8080`.
2. Abrir el proyecto Android Gradle.
3. Corregir cualquier incompatibilidad de versiones Gradle/Kotlin/Compose.
4. Lograr que `androidApp` compile.
5. Probar que la app llame a `http://10.0.2.2:8080/api/v1/assist`.
6. No agregar claves privadas.
7. No romper la arquitectura KMP.
8. Si cambiás versiones, explicá por qué.

Criterios de éxito:
- Backend responde `/health`.
- `pytest` pasa.
- Android compila.
- HomeScreen habla con TTS y muestra respuesta del backend.
- Commit limpio con resumen técnico.
