# Argentine Spanish Voice Tuning Report

## 1. Resumen ejecutivo

Se implemento una primera version real del motor de normalizacion de lenguaje argentino/rioplatense para Ojo Claro AI. El cambio corre antes del parser local y convierte frases con muletillas, voseo y aliases frecuentes en una forma mas estable, sin agregar features nuevas ni relajar confirmaciones sensibles.

El objetivo practico es que frases reales como "che abri wp", "abrime el WhatsApp", "dale buscame el chat de Marco", "decile a mama que estoy bien", "llamame a mama", "llevame al laburo" y "donde ando" entren al flujo correcto.

## 2. Problema fisico que se busca resolver

En QA fisica la app entendia bien comandos limpios, pero fallaba con habla humana real: muletillas, voseo, aliases de WhatsApp, pausas, frases parcialmente redundantes y formas argentinas. Eso hacia que comandos reconocidos por SpeechRecognizer no llegaran al intent correcto o terminaran en fallback generico.

## 3. Archivos creados/modificados

Archivos principales:

- `androidApp/src/main/java/com/ojoclaro/android/voice/ArgentineSpanishLexicon.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/VoicePhraseNormalizer.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/LocalIntentParser.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/CommandRouter.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentConversationManager.kt`
- `androidApp/src/main/java/com/ojoclaro/android/domain/AssistantOrchestrator.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/VoiceCommandDispatcher.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt`

Tests:

- `androidApp/src/test/java/com/ojoclaro/android/voice/ArgentineSpanishLexiconTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/voice/VoicePhraseNormalizerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/LocalIntentParserTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/AgentConversationManagerTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/domain/AssistantOrchestratorTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/external/CommandRouterTest.kt`

## 4. Muletillas soportadas

Se centralizaron y filtran, si hay intencion util restante:

- che
- eh
- eeh
- ehh
- mmm
- mmmm
- bueno
- a ver
- tipo
- viste
- o sea
- porfa
- por favor
- dale

Regla importante: si la frase completa queda vacia al sacar muletillas, se conserva el texto original. Por eso "dale" o "dale dale" no se transforman en confirmacion ni en comando vacio.

## 5. Variantes argentinas soportadas

WhatsApp:

- wp, wsp, wpp, wasap, guasap, watsap, whasap, whats app -> whatsapp
- "che abri wp" -> WhatsApp Guided Mode
- "abrime el wp" -> abrir whatsapp
- "buscame el chat de Marco" -> OPEN_WHATSAPP_CHAT
- "dale busca el chat de Marco" -> OPEN_WHATSAPP_CHAT, sin confirmar

Voseo / formas naturales:

- abrime -> abrir
- buscame -> buscar
- mandale -> mandar
- decile -> decir
- escribile -> escribir
- llamame -> llamar
- llevame -> llevar
- poneme -> poner
- avisame -> avisar
- recordame -> recordar
- fijate -> revisar
- ubicame -> donde estoy

Maps:

- "donde ando" -> GET_CURRENT_LOCATION
- "ubicame" -> GET_CURRENT_LOCATION
- "abrime mapas" -> OPEN_MAPS
- "llevame al laburo" -> NAVIGATE_TO_DESTINATION con alias `laburo`

Llamadas:

- "llamame a mama" -> CALL_CONTACT
- "llama a mi viejo" -> CALL_CONTACT
- "llama a mi vieja" -> CALL_CONTACT
- "llama a mi novia" -> CALL_CONTACT

## 6. Como se mantiene seguridad

- No se agrego background listening.
- No se agrego hotword.
- No se agrego IA cloud.
- No se agrego Spotify ni recordatorios.
- No se agregaron taps automaticos.
- No se usa READ_CONTACTS.
- No se usa CALL_PHONE ni ACTION_CALL.
- WhatsApp no envia mensajes automaticamente.
- Las confirmaciones sensibles siguen siendo estrictas: `confirmar`, `confirmo`, `aceptar`.
- `si`, `sí`, `dale`, `ok`, `bueno`, `aja`, `de una` siguen sin confirmar.
- El normalizador conserva separadores de compose como `:` para no romper "un contacto: estoy llegando".
- El colapso de repeticiones se limita a ruido frecuente como "si si", "eh eh", "dale dale", "este este"; no comprime mensajes reales repetidos.

## 7. Tests agregados

Se agregaron tests para:

- Lexicon de aliases, confirmaciones estrictas y voseo.
- Normalizacion de "che abri wp", "abrime el wp", "dale buscame el chat de Marco", "eh mandale a un contacto que estoy llegando", "donde ando", "ubicame".
- Parser para WhatsApp Guided Mode, OPEN_WHATSAPP_CHAT, COMPOSE_WHATSAPP_MESSAGE, CALL_CONTACT, GET_CURRENT_LOCATION, OPEN_MAPS y NAVIGATE_TO_DESTINATION.
- Conversacion en WAITING_WHATSAPP_ACTION con contacto solo, chat, mensaje, "dale busca..." y confirmaciones no validas.
- Orquestador para guided mode sin abrir WhatsApp, compose con "decile", llamada con "llamame", ubicacion argentina, alias `laburo` y chat directo con "abrime el chat".
- CommandRouter para verbos canonicos `decir` y `escribir`.

## 8. Resultados build/tests

Comandos ejecutados:

- `.\gradlew.bat :androidApp:testDebugUnitTest`
- `.\gradlew.bat :androidApp:assembleDebug --console=plain`
- `.\gradlew.bat :shared:allTests --console=plain`

Resultado:

- Android unit tests: OK, 504 tests.
- APK debug: OK.
- Shared allTests: OK.

Notas:

- Gradle mantiene warnings existentes de compatibilidad Kotlin/AGP y targets iOS deshabilitados en esta maquina. No bloquearon build ni tests.

## 9. APK generada

APK generada:

- `androidApp/build/outputs/apk/debug/androidApp-debug.apk`

Tamano observado:

- 57,368,695 bytes.

## 10. Samsung instalado si/no

No instalado.

Comando ejecutado:

- `adb devices`

Resultado:

- No aparecio ningun device conectado. No se ejecuto install ni logcat porque el Samsung `R5CW22SMWDM` no estaba disponible.

## 11. QA fisica si se pudo

No se pudo ejecutar QA fisica ni instalar en Samsung desde esta sesion porque `adb devices` no mostro dispositivos conectados.

QA fisica recomendada cuando el Samsung este conectado:

1. Instalar `androidApp/build/outputs/apk/debug/androidApp-debug.apk`.
2. Abrir Ojo Claro.
3. Decir "che abri wp"; esperado: no abre WhatsApp, entra a modo guiado.
4. Decir "dale buscame el chat de Marco"; esperado: interpreta chat, no confirma por "dale".
5. Decir "decile a mama que estoy bien"; esperado: compose con confirmacion, sin enviar.
6. Decir "llamame a mama"; esperado: prepara llamada con confirmacion, sin llamar.
7. Decir "donde ando" y "ubicame"; esperado: ubicacion o permiso, sin crash.
8. Decir "llevame al laburo"; esperado: usar alias guardado si existe, pedir confirmacion.
9. Revisar logcat sin `FATAL EXCEPTION`.

## 12. Proximos pasos

- Conectar el Samsung `R5CW22SMWDM`, instalar APK y repetir QA fisica guiada.
- Capturar ejemplos reales nuevos del SpeechRecognizer y agregarlos como tests antes de tocar UX.
- Ajustar el debug panel si en demo resulta demasiado visible; hoy muestra original, normalizado, estado e intent solo en build debug.
- Mantener confirmacion estricta mientras se amplian frases naturales.
