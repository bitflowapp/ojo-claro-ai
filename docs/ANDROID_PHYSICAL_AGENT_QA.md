# Android Physical Agent QA

## Dispositivo físico usado
- Modelo: `SM-S916B`
- ID adb: `R5CW22SMWDM`
- Android: `16`
- API: `36`

## APK instalado
- Ruta: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`
- Instalación: `Success`

## Resultado build/tests
- `.\gradlew.bat :androidApp:assembleDebug` OK
- `.\gradlew.bat :androidApp:testDebugUnitTest` OK
- `.\gradlew.bat :shared:allTests` OK

## Resultado logcat
- `adb -s R5CW22SMWDM logcat -c` ejecutado antes de abrir la app.
- `adb -s R5CW22SMWDM shell am start -n com.ojoclaro.android/.MainActivity` abrió la app en el teléfono.
- Búsqueda de `FATAL EXCEPTION`, `AndroidRuntime` y `com.ojoclaro` no mostró crash de la app en el arranque validado.
- No se observaron excepciones fatales atribuidas a `com.ojoclaro.android`.

## Flujos probados
- Apertura de app en dispositivo físico.
- Onboarding visible tras limpiar datos.
- Home visible luego de saltar onboarding.
- Botón `¿Qué puedo decir?`.
- Botón `Callar`.
- Verificación de controles accesibles en el árbol UI:
  - `Describir lo que tengo enfrente.`
  - `Leer texto con la cámara.`
  - `Escuchar ejemplos de comandos disponibles.`
  - `Callar la voz.`

## Latencia de Callar
- No se midió con cronometraje fino en esta pasada.
- Validación práctica: `Callar` no produjo crash, bloqueo visible ni loop de voz durante el smoke realizado.

## TalkBack: orden de foco
- No se completó una sesión final de TalkBack de usuario en este dispositivo.
- En el dump de accesibilidad, el orden expuesto es razonable:
  1. título/estado
  2. respuesta principal
  3. acciones principales
  4. `Callar`
- Queda pendiente validar el foco real con TalkBack activado manualmente desde Ajustes.

## TTS: pronunciación
- No se completó una prueba auditiva final de TTS en este dispositivo físico.
- Pendiente validar pronunciación real de frases como `¿Qué puedo decir?`, `Callar` y respuestas de memoria.

## OCR: resultado con texto real
- No se completó una pasada final de cámara/OCR con papel real en este dispositivo.
- Pendiente validar lectura de texto impreso y comportamiento del consentimiento antes de `Qué dice la pantalla`.

## WhatsApp: confirmó que no envía solo
- No se completó la prueba con WhatsApp real en este dispositivo físico.
- Pendiente validar que `mandale a Sofi: estoy llegando` solo prepare la acción y no envíe automáticamente.

## Bugs encontrados
- No se encontró crash real de la app en el dispositivo físico.
- No se encontró regresión visible en el arranque ni en los controles básicos.

## Bugs corregidos
- No fue necesario corregir código en esta ronda de QA física.

## Riesgos pendientes
- TalkBack y servicio de accesibilidad de Ojo Claro requieren activación manual confiable en Ajustes para la validación final.
- TTS, OCR y WhatsApp todavía necesitan prueba humana en el teléfono.
- `adb` no dejó persistente la activación de accesibilidad, así que la verificación final depende de setup manual.
- Aún falta medir latencia de `Callar` con más rigor durante una sesión hablada real.

## Checklist para Android físico
- [ ] Activar TalkBack manualmente
- [ ] Activar servicio de accesibilidad de Ojo Claro
- [ ] Probar `¿Qué puedo decir?`
- [ ] Probar `Callar`
- [ ] Probar `Leer texto` con papel real
- [ ] Probar `Qué dice la pantalla`
- [ ] Confirmar/cancelar acciones pendientes
- [ ] Probar memoria local con `recordá que prefiero respuestas cortas`
- [ ] Probar `qué recordás de mí`
- [ ] Probar `borrá tu memoria`
- [ ] Probar WhatsApp y confirmar que no envía automáticamente
- [ ] Medir latencia percibida de `Callar`

## Próximo paso recomendado
- Hacer una sesión física guiada con TalkBack, TTS y cámara activa, comenzando por `¿Qué puedo decir?`, `Callar`, lectura de texto impreso y el flujo de memoria local.
