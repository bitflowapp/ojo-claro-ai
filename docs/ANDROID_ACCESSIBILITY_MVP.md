# Android Accessibility MVP

Fecha de revision: 2026-05-04

Este documento deja trazabilidad del pase Android/Kotlin sobre Ojo Claro AI. El objetivo fue priorizar compilacion real, voz estable, boton Callar funcional y lectura basica de texto con CameraX + ML Kit, sin agregar features fuera del MVP.

## Que cambie

- Genere Gradle Wrapper 8.11.1 en el repo.
- Corregi la configuracion KMP de `shared` para que Gradle pueda evaluar `iosMain` sin romper Android.
- Oculte `io.ktor.client.HttpClient` de la API publica de `AssistantApi`; Android no necesitaba compilar contra ese tipo.
- Refactorice `SpeechController` para inicializar TTS una sola vez, usar espanol Argentina con fallback razonable, hacer `stop()` antes de `speak()`, usar `QUEUE_FLUSH`, deduplicar por 5 segundos, limpiar frases pendientes al tocar Callar y liberar recursos.
- Separe estado visual de eventos de voz en `HomeViewModel` con `SpeechEvent`, evitando que `spokenText` dispare loops por recomposicion.
- Cambie `HomeScreen` a una interfaz negra, simple, de alto contraste, con botones grandes: `DESCRIBIR`, `Leer texto`, `Pedir ayuda`, `Callar`.
- Movi el pedido de permiso de camara al boton `Leer texto`. La app ya no pide microfono ni ubicacion al iniciar.
- Rehice `TextScanScreen` para usar CameraX Preview + ImageAnalysis con executor dedicado, ML Kit OCR local, texto estable por 1 segundo, pausa mientras TTS habla, salida limpia y boton `Callar y volver`.
- Agregue `StableTextDetector` para deduplicar OCR y cubrirlo con tests unitarios.
- Refactorice `TextRecognitionAnalyzer` para cerrar siempre `ImageProxy`, manejar errores de ML Kit, evitar callbacks vacios y reducir spam.
- Agregue tests de parser compartido y tests Android de estabilidad/deduplicacion de texto.

## Errores encontrados

- No habia Gradle Wrapper en el ZIP.
- `gradle wrapper --gradle-version 8.11.1` fallo porque `gradle` no estaba en PATH.
- Al usar un Gradle local en cache, la tarea wrapper fallo al evaluar `shared/build.gradle.kts`: `KotlinSourceSet with name 'iosMain' not found`.
- Primer `assembleDebug` con wrapper fallo en `HomeViewModel.kt`: `Cannot access class 'io.ktor.client.HttpClient'`.
- Tras el refactor de OCR hubo un error de constructor en `TextScanScreen.kt` porque el callback de `TextRecognitionAnalyzer` no era el ultimo parametro; se corrigio usando argumento nombrado.

## Comandos ejecutados

```powershell
gradle wrapper --gradle-version 8.11.1
```

Resultado: fallo. `gradle` no estaba instalado en PATH.

```powershell
& 'C:\Users\marco\gradle-cache\wrapper\dists\gradle-8.12-all\ejduaidbjup3bmmkhw3rie4zb\gradle-8.12\bin\gradle.bat' wrapper --gradle-version 8.11.1
```

Resultado inicial: fallo por `iosMain` inexistente. Despues de corregir `shared/build.gradle.kts`, resultado: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :androidApp:assembleDebug
```

Resultado inicial: fallo por `HttpClient` expuesto desde `AssistantApi`. Resultado intermedio: fallo por constructor de `TextRecognitionAnalyzer`. Resultado final: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :shared:allTests
```

Resultado inicial y final: `BUILD SUCCESSFUL`.

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest
```

Resultado: `BUILD SUCCESSFUL`.

## APK generado

APK debug:

```txt
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

## Resultado funcional esperado

- La pantalla principal no habla por si sola al recomponer.
- Una respuesta nueva del backend/fallback se emite como evento de voz una sola vez.
- `Callar` llama a `SpeechController.stop()`, invalida requests pendientes y evita que una respuesta vieja hable despues.
- `Leer texto` solo abre camara si existe permiso.
- OCR local no guarda imagenes ni audio.
- El texto debe verse estable al menos 1 segundo antes de hablarse.
- El mismo texto no se lee dos veces durante el mismo escaneo.
- Si no aparece texto claro tras unos segundos, se dice una sola vez: `No encontre texto claro.`
- `Callar y volver` detiene TTS, cierra la pantalla de camara y vuelve a `IDLE`.

## Riesgos tecnicos pendientes

- No se probo en dispositivo fisico con TalkBack; falta validacion real de foco, tiempos de voz y ergonomia.
- `SpeechController.isSpeaking` depende de callbacks del motor TTS del dispositivo; algunos motores pueden reportar estados con demora.
- `DESCRIBIR` sigue dependiendo del backend y no captura imagen todavia.
- Las advertencias KMP indican que los targets iOS estan deshabilitados en Windows y que AGP 8.7.3 esta por encima del maximo testeado por Kotlin 2.0.21. No bloquean Android ni shared tests.
- El backend es fallback/mock para este MVP; no se agrego IA cloud ni keys.

## Proximos pasos

1. Probar el APK con TalkBack en un Android real.
2. Ajustar frases y pausas con una persona ciega o con baja vision.
3. Agregar una prueba instrumental minima para abrir `HomeScreen` y verificar botones/content descriptions.
4. Implementar descripcion de escena con captura controlada de imagen solo cuando el usuario lo pida.
5. Revisar versiones Kotlin/AGP para bajar warnings de compatibilidad KMP.
