# WhatsApp Search Chat Phrases Fix

Fecha: 2026-05-06

## Resumen ejecutivo

Se fortaleció el flujo seguro `OPEN_WHATSAPP_CHAT` para que Ojo Claro entienda frases naturales de búsqueda de chat antes de salir de la app. El cambio evita el problema observado en QA física: si el usuario abre WhatsApp genérico con "abrí wp", Ojo Claro queda en segundo plano y ya no escucha dentro de WhatsApp.

La solución mantiene el diseño seguro:

- No escucha en background.
- No usa taps automáticos.
- No navega WhatsApp con AccessibilityService.
- No pide `READ_CONTACTS`.
- No envía mensajes automáticamente.
- No toca Maps ni llamadas.
- No rompe `COMPOSE_WHATSAPP_MESSAGE`.

## Frases nuevas soportadas

Todas estas frases se parsean como `OPEN_WHATSAPP_CHAT` y extraen `contactName = "Marco Antonio"`:

- "busca el chat de Marco Antonio"
- "buscá el chat de Marco Antonio"
- "buscame el chat de Marco Antonio"
- "buscar chat de Marco Antonio"
- "encontrá el chat de Marco Antonio"
- "abrí el chat de Marco Antonio"
- "andá al chat de Marco Antonio"
- "abrí WhatsApp con Marco Antonio"
- "abrí wp con Marco Antonio"
- "abrí wsp con Marco Antonio"

Se preservaron estos comportamientos:

- "abrí wp" sigue siendo `OPEN_WHATSAPP`.
- "mandale a Marco Antonio que estoy llegando" sigue siendo `COMPOSE_WHATSAPP_MESSAGE`.

## Resolución segura de contacto

La resolución por memoria local ahora exige alias exacto normalizado:

- Ignora tildes.
- Ignora mayúsculas/minúsculas.
- Permite match exacto por alias guardado.
- No inventa un contacto parcial: si está guardado "Marco" y el usuario dice "Marco Antonio", no abre el chat de "Marco".

Cuando falta número guardado, la respuesta guía queda clara:

> No tengo un número guardado para Marco Antonio. Podés decir: el número de Marco Antonio es...

## Guía al abrir WhatsApp general

Cuando el usuario dice "abrí wp" o "abrí WhatsApp" sin contacto, Ojo Claro sigue abriendo WhatsApp, pero ahora orienta el flujo correcto:

> Abrí WhatsApp. Para abrir un chat directo, decime antes: abrí el chat de un contacto.

## Tests actualizados

Se actualizaron pruebas en:

- `LocalIntentParserTest`: cubre las frases nuevas de búsqueda de chat, mantiene "abrí wp" como `OPEN_WHATSAPP` y mantiene "mandale..." como `COMPOSE_WHATSAPP_MESSAGE`.
- `AgentConversationManagerTest`: refuerza confirmación estricta; "sí" no confirma y "confirmar" confirma una acción pendiente.
- `AssistantOrchestratorTest`: cubre "busca el chat de Marco Antonio" con número guardado, creación de pending, confirmación que emite `OpenWhatsAppChat`, cancelación sin evento externo, normalización de tildes/mayúsculas y bloqueo de match parcial "Marco" vs "Marco Antonio".

## Validación ejecutada

Comandos ejecutados:

```powershell
.\gradlew.bat :androidApp:testDebugUnitTest
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:allTests
```

Resultado:

- `:androidApp:testDebugUnitTest`: `BUILD SUCCESSFUL`
- `:androidApp:assembleDebug`: `BUILD SUCCESSFUL`
- `:shared:allTests`: `BUILD SUCCESSFUL`

Notas:

- Gradle mostró el warning existente de compatibilidad Kotlin MPP / Android Gradle Plugin.
- Los targets Kotlin/Native de iOS quedaron salteados en esta máquina, como ya venía ocurriendo.

## APK

APK generada:

```text
androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

## Cómo probar en físico

1. Instalar la APK debug nueva en el Samsung.
2. Abrir Ojo Claro.
3. Guardar un número si hace falta: "el número de Marco Antonio es 1123456789".
4. Confirmar el guardado con "confirmar".
5. Probar: "busca el chat de Marco Antonio".
6. Esperar que Ojo Claro pida confirmación y diga que no enviará ningún mensaje.
7. Decir "confirmar".
8. Verificar que abre WhatsApp directo al chat usando `wa.me`, sin texto precargado y sin enviar nada.
9. Probar "abrí wp" y verificar que abre WhatsApp general con la guía hablada para decir antes "abrí el chat de un contacto".
