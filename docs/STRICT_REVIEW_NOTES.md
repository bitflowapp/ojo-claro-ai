# Revisión estricta inicial

Correcciones aplicadas después del primer empaquetado:

1. Backend: se agregó `backend/pytest.ini` para que `pytest` encuentre el paquete `app` sin depender de variables manuales como `PYTHONPATH`.
2. Android: se agregó el plugin `org.jetbrains.kotlin.plugin.compose` requerido por Kotlin 2.x para Compose Compiler.
3. Android: se eliminó `kotlinCompilerExtensionVersion = "1.5.15"`, porque con Kotlin 2.x se debe usar el plugin de Compose Compiler.
4. Android: se agregó `gradle.properties` con AndroidX y configuración JVM base.
5. iOS: se corrigió el endpoint del cliente HTTP para usar `api/v1/assist` sin slash inicial.
6. iOS: se quitó la referencia a `AppIcon` inexistente en `project.yml`, para no romper la generación inicial con XcodeGen.
7. CI: el backend ahora corre tests con `python -m pytest` y el job Gradle instala Gradle 8.11.1 con `gradle/actions/setup-gradle@v4`.
8. Shared/KMP: se corrigió `iosMain` para usar `by getting` y la jerarquía moderna de Kotlin Multiplatform, evitando conflictos con source sets autogenerados.
9. Shared/KMP: se configuró la generación de framework iOS estático `Shared` para que el módulo común pueda integrarse con Xcode.

Limitación conocida:

- El repositorio todavía no incluye Gradle Wrapper (`gradlew` + `gradle-wrapper.jar`). Para un producto serio, Codex debe agregarlo desde una instalación local segura con `gradle wrapper --gradle-version <versión estable>` y commitearlo.
