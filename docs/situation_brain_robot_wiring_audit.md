# Situation Brain Robot Wiring Audit

Fecha: 2026-05-15. Auditoria estatica, sin Android fisico conectado.

Regla base verificada: `SituationBrainFeatureFlag.ENABLED` sigue en `false`; con el flag apagado, `HomeViewModel.submitVoiceText()` no entra a la ruta experimental.

## Oidos

- Entrada principal: `HomeScreen.kt` crea `VoiceCommandController` con `AndroidSpeechInputEngine(context)` y lo conecta con `HomeViewModel`.
- SpeechRecognizer: `AndroidSpeechInputEngine.kt` envuelve `SpeechRecognizer`, arma `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`, emite parciales/finales y cancela con `SpeechRecognizer.cancel()`.
- Sesion/ruido: `VoiceCommandController.kt` maneja permisos, reintentos, pausa durante TTS y eventos de escucha. `VoiceListeningSession.kt` filtra texto sensible de parciales/finales con `MemoryPolicy` y `PrivacyGuard`.
- Llegada a HomeViewModel: `HomeScreen.kt` recibe texto final del controlador de voz y llama `viewModel.onVoiceFinalText(recognizedText)`. Esa funcion normaliza y delega en `submitVoiceText(cleanText)`.
- Entrada de texto/debug: `MainActivity.kt` registra, solo en debug, `DEBUG_SUBMIT_TEXT_ACTION` y empuja texto a `OjoClaroApp(... debugTextSubmissions ...)`. La decision de debug rechaza texto blanco, demasiado largo o sensible.
- Cancelacion de escucha: `HomeScreen.kt` puede llamar `voiceController.stopForCommandAndResume()`, `voiceController.stopListening()` y `speechController.stop()`. Dentro de `submitVoiceText`, los comandos reconocidos por `VoiceCommandDispatcher.isStopCommand()` limpian conversacion activa y llaman `onStopSpeechRequested()`.
- Sin permiso de microfono: `HomeScreen.kt` pide `Manifest.permission.RECORD_AUDIO`. Si falta o se deniega, `HomeViewModel.onMicrophonePermissionDenied()` habla `VoiceCommandController.MICROPHONE_PERMISSION_MESSAGE` y deja estado `PERMISSION_REQUIRED`.

## Boca

- TTS principal: `SpeechController.kt` envuelve `TextToSpeech`, usa `QUEUE_FLUSH`, dedupe de frases recientes y un `generation` interno para ignorar callbacks viejos.
- Publicacion: `HomeViewModel.publishLocalMessage()` actualiza estado, guarda ultima respuesta en `sessionMemory` y llama `emitSpeechEvent()`. `HomeScreen.kt` colecciona `speechEvents` y llama `speechController.speak(event.text, force = event.force)`.
- Corte de habla: el stop de UI/voz llama `speechController.stop()` y `HomeViewModel.onStopSpeechRequested()`. El Situation Brain, cuando devuelve `SituationUiEffect.Cancel`, limpia memoria runtime y reutiliza `onStopSpeechRequested()`.
- Riesgo de respuestas viejas: hay dos guardas. En TTS, `SpeechController` invalida callbacks con `generation`. En ViewModel, `activeRequestId`, `mutedThroughRequestId`, `shouldDropAsyncResult()` y `shouldIgnoreMutedResponse()` evitan publicar resultados asincronicos viejos tras una cancelacion/callate.
- Limite observado: `onStopSpeechRequested()` corta voz y handoff, pero por comentario explicito no cancela acciones pendientes legacy salvo que el comando sea "cancelar". Esto es intencional para no perder contexto por error.

## Ojos

- AccessibilityService: `OjoClaroAccessibilityService.kt` lee texto y nodos visibles. Sus comentarios y limites indican: sin taps, sin gestos, sin storage, sin send; caps de profundidad, nodos y caracteres; password nodes no exponen texto.
- Lector visible directo: `AccessibilityScreenReader.kt` verifica servicio habilitado/conectado, lee `OjoClaroAccessibilityService.readVisibleText()`, redacciona con `PrivacyGuard` y advierte con `RiskDetector`. Si no hay servicio, devuelve mensaje de activar Accesibilidad. Si no hay texto visible, informa que no encontro texto.
- Snapshot estructurado: `AndroidAccessibilityScreenContextProvider.kt` construye `ScreenSnapshot` desde texto, package y nodos visibles; no persiste el snapshot.
- OCR/camara: `TextScanScreen.kt` usa CameraX y `TextRecognitionAnalyzer.kt` con ML Kit local. `StableTextDetector.kt` espera texto estable, evita repetir durante scan y no guarda imagen ni historial OCR.
- Entendimiento de pantalla: `ScreenUnderstandingUseCase.kt` responde consultas de pantalla con `DeterministicScreenSummarizer.kt`. Si Accesibilidad no esta lista, devuelve `NeedsAccessibilityService`. Si la pantalla es bancaria, password o sensible, bloquea lectura de contenido y habla una advertencia.
- OCR sin texto: `HomeViewModel.onTextScanNoTextFound()` habla "No encontre texto claro.".

## Cerebro

- Entrada: `HomeViewModel.submitVoiceText()` llama `tryHandleWithSituationBrain(cleanText)` solo si `SituationBrainFeatureFlag.ENABLED` es `true` y no hay imagen. El flag esta apagado por defecto.
- Contexto: `SituationContextFactory` crea el contexto desde comando, estado actual, ultimo mensaje y `SituationRuntimeMemory.current()`.
- Memoria: `SituationRuntimeMemory.kt` conserva en RAM `activeGoal`, `pendingAction`, `companionModeActive`, `lastAssistantMessage`, `recentTurns`, `situationState` y `mutedThroughRequestId`. No persiste a disco.
- Intenciones soportadas por el modelo: `SituationIntent.kt` define `READ_SCREEN`, `SUMMARIZE_SCREEN`, `EXPLAIN_WHAT_I_SEE`, `OPEN_APP`, `WRITE_MESSAGE`, `CALL_CONTACT`, `GUIDE_USER`, `HELP_ME_WORK`, `MANAGE_MEMORY`, `CONTROL`, `EMERGENCY_STOP`, `UNKNOWN` y `UNSAFE_REQUEST`.
- Confirmacion: `SituationDecisionApplier` produce `SituationUiEffect.AskConfirmation`/`Execute`. `HomeViewModel.isSituationPendingActionAllowed()` permite `OPEN_APP`, `CALL_CONTACT` y `WRITE_MESSAGE` solo si `SituationMessageSafety` valida payload seguro.
- Acciones bloqueadas en esta capa: `GUIDE_USER`, `MANAGE_MEMORY`, `UNKNOWN`, `UNSAFE_REQUEST` y cualquier `intentName` desconocido caen a fallback o rechazo, no a ejecucion directa.
- WRITE_MESSAGE: `SituationMessageSafety.kt` exige `contact` y `message`, limita longitud y bloquea palabras sensibles obvias. `SituationConfirmedActionAdapter.kt` convierte un `Execute` confirmado en accion confirmada, no en texto crudo.

## Manos seguras

- Abrir WhatsApp: `CommandRouter` detecta abrir WhatsApp; `AssistantOrchestrator` lo convierte en `ExternalActionEvent.OpenWhatsApp` dentro de `ExternalAppHandoff`; `HomeScreen.kt` ejecuta `WhatsAppIntentHelper.openWhatsApp()`.
- Preparar mensaje: `ExternalActionEvent.ComposeWhatsAppMessage` llega a `HomeScreen.kt`, que llama `WhatsAppIntentHelper.composeMessage(contactName, messageText)`. Ese helper usa `Intent.ACTION_SEND`, `type = "text/plain"`, `Intent.EXTRA_TEXT` y `setPackage()` a WhatsApp/Business. No toca el boton enviar.
- Abrir chat: `WhatsAppIntentHelper.openChat()` usa `Intent.ACTION_VIEW` con `https://wa.me/<digitos>` y sin `?text=`.
- Abrir marcador: `PhoneActionExecutor.kt` construye `Intent.ACTION_DIAL` con `tel:` si hay numero. `REQUIRED_PERMISSION` es `null`.
- Confirmacion de llamadas: `AssistantOrchestrator.handlePhoneIntent()` resuelve contacto y arma `PendingConfirmation`; al confirmar, emite `ExternalActionEvent.DialPhoneNumber`. El usuario toca llamar manualmente.
- CALL_PHONE: no aparece declarado en `AndroidManifest.xml` y `PhoneActionExecutor` no usa `Intent.ACTION_CALL`.
- WRITE_MESSAGE con Situation Brain: tras confirmar, `HomeViewModel.handleConfirmedSituationWriteMessage()` crea un `PendingConfirmation` legacy ya confirmado y llama `orchestrator.process(rawInput = "confirmar", pendingConfirmation = legacyPending, ...)`. El resultado normal emite `ComposeWhatsAppMessage`, que prepara composer via helper viejo.

## Contactos

- Infraestructura actual: `com.ojoclaro.android.phone.ContactResolver` define `ContactResolutionResult` y `MemoryContactResolver`.
- Fuentes actuales: memoria local aprobada (`TRUSTED_CONTACT`/`EMERGENCY_CONTACT`), favoritos demo, numero dictado por el usuario y emergencia estricta. No lee agenda del sistema.
- Contacto no encontrado: `MemoryContactResolver` devuelve `ContactResolutionResult.NotFound`. `AssistantOrchestrator` responde que no tiene numero guardado y pide dictar el numero.
- Permisos necesarios para fase futura: `READ_CONTACTS` para buscar agenda real. Para agendar, preferir `ContactsContract.Intents.Insert.ACTION`/`ACTION_INSERT` con confirmacion humana; evitar guardar silenciosamente. `WRITE_CONTACTS` solo si una fase futura justifica escritura directa, que hoy no esta implementada.
- Modelos puros agregados para futura integracion: `agent.contacts.ContactLookupResult`, `ContactCandidate` y `ContactSource`. Son modelos de contrato, no leen Android ni permisos.
- Gap: no existe todavia `SystemContactResolver` real ni flujo `ACTION_INSERT` para agendar.

## WhatsApp visible

- Detector de pantalla: `WhatsAppScreenDetector.kt` infiere si WhatsApp esta abierto por package name (`com.whatsapp`, `com.whatsapp.w4b`) y senales estructurales visibles (campo mensaje, camara, adjuntar, enviar, microfono, volver). Devuelve flags, no contenido privado.
- Chats visibles: `WhatsAppVisibleChatsReader.kt` usa `ScreenContextProvider`, `WhatsAppScreenDetector` y `WhatsAppChatListDetector`.
- Extraccion: `WhatsAppChatListDetector.kt` emite solo nombres visibles, max 5, y rechaza UI labels, timestamps, previews, strings numericos, telefonos, password nodes y contenido financiero/sensible.
- Modelo: `WhatsAppVisibleChat.kt` solo guarda `displayName`; no contiene preview, hora, contador de no leidos ni mensajes.
- Limites: si el usuario esta dentro de un chat, el reader responde que no lee mensajes completos. Si Accesibilidad esta apagada, pide activar el servicio.
- No permitido: no lee base de datos de WhatsApp, no hace scraping oculto, no hace scrolling infinito automatico, no guarda historial privado, no envia mensajes.

## Manifest y permisos

- `RECORD_AUDIO`: declarado. Se usa para voz/SpeechRecognizer y servicio global de microfono.
- `BIND_ACCESSIBILITY_SERVICE`: declarado en el service `.accessibility.OjoClaroAccessibilityService`.
- Camara/OCR: `CAMERA` y `uses-feature android.hardware.camera.any required=false` estan declarados.
- Contactos: `READ_CONTACTS` no esta declarado. `WRITE_CONTACTS` no esta declarado.
- Llamadas: `CALL_PHONE` no esta declarado.
- Otros permisos relevantes: `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `SYSTEM_ALERT_WINDOW`, `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`.
- Package visibility/queries: hay queries para `com.whatsapp`, `com.whatsapp.w4b`, `com.google.android.apps.maps`, `ACTION_SEND text/plain` y `ACTION_VIEW geo`.

## Gaps

### Cables conectados

- Oidos: voz, texto final y debug text llegan a `HomeViewModel`.
- Boca: TTS y cancelacion de voz estan cableados.
- Ojos: Accesibilidad, OCR local y screen understanding estan cableados con limites de seguridad.
- Cerebro: Situation Brain, memoria runtime, recentTurns, ActiveGoal y PendingAction estan cableados detras del flag apagado.
- Manos seguras: abrir WhatsApp, abrir marcador, preparar mensaje y abrir chat usan intents seguros.
- WhatsApp visible: deteccion y listado de nombres visibles existen con limites estrictos.

### Cables parciales

- Contactos: solo memoria local/numero dictado/demo/emergencia. Falta agenda real con permiso explicito.
- OPEN_APP generico: existen apps principales conocidas; apertura generica por cualquier nombre sigue parcial.
- QA runtime: voz, TTS, Accesibilidad, OCR, intents externos y WhatsApp visible necesitan Android fisico.

### Cables faltantes

- `SystemContactResolver` con `READ_CONTACTS`.
- Flujo seguro de contacto no encontrado que ofrezca buscar en WhatsApp visible o agendar.
- Flujo `ACTION_INSERT` para pedir agendar contacto con confirmacion humana.
- Lectura enriquecida de ultimos mensajes visibles por pantalla, si se decide habilitarla, con consentimiento y sin persistencia.

### Riesgos antes de QA fisico

- SpeechRecognizer/TTS pueden variar por motor del Samsung y permisos.
- Accesibilidad puede estar habilitada en Ajustes pero no conectada aun; ya hay mensaje de "activando", pero debe probarse.
- WhatsApp `ACTION_SEND` puede abrir chooser o flujo distinto segun version/instalacion.
- El detector de chats visibles depende de labels reales de WhatsApp y del idioma/configuracion del dispositivo.
- Contactos por nombre no funcionaran con agenda real hasta implementar `READ_CONTACTS`/resolver.

## Flujo recomendado: contacto no encontrado

Caso: usuario dice "avisale a Juan que llego tarde".

1. Ojo Claro no inventa contacto.
2. Dice: "No encontre a Juan en tus contactos. Puedo buscarlo en la pantalla visible de WhatsApp o ayudarte a agendarlo."
3. Si el usuario acepta buscar en pantalla, usa solo texto visible por `AccessibilityService`; no lee bases internas.
4. Si encuentra chats visibles, propone: "Veo un chat llamado Juan. Queres usar ese?"
5. Si el usuario quiere agendar, usar `ACTION_INSERT` de `ContactsContract` o flujo equivalente con confirmacion humana; no guardar silenciosamente.
6. Si hay varios Juan, leer candidatos y pedir eleccion.

## Flujo recomendado: contactos desde WhatsApp visible

Reglas:

- Solo leer lo visible en pantalla.
- No guardar historial.
- No leer base de datos.
- No hacer scrolling infinito automatico.
- No enviar mensajes.
- No inferir datos sensibles.
- Puede describir nombres de chats visibles, ultimo mensaje visible solo si esta en pantalla y una fase futura lo permite, hora visible si esta en pantalla, y estado "parece chat/contacto/grupo" si el detector lo soporta.
- Debe pedir confirmacion antes de usar un chat visible como contacto destino.

Ejemplo:

Usuario: "busca a Juan en WhatsApp".

Ojo Claro: "Estoy viendo tres chats: Juan Perez, Juan trabajo y Juan grupo. Cual queres usar?"

## Checklist QA Samsung fisico

1. Confirmar permisos iniciales: microfono, camara y Accesibilidad apagada/prendida.
2. Oidos: dictar "abrir WhatsApp", "callate", ruido/blank y verificar que el texto final llega una sola vez.
3. Boca: verificar que `callate` corta TTS y que una respuesta vieja no habla despues.
4. Ojos/Accesibilidad apagada: pedir "leeme la pantalla" y confirmar mensaje claro de permiso.
5. Ojos/Accesibilidad prendida: abrir pantalla simple, pedir "que estoy viendo" y confirmar resumen sin persistencia.
6. OCR: abrir lectura con camara, probar texto claro y superficie sin texto.
7. OPEN_APP: "abri WhatsApp", confirmar handoff y retorno.
8. CALL_CONTACT: probar contacto guardado en memoria o numero dictado; confirmar que abre marcador con `ACTION_DIAL` y no llama.
9. WRITE_MESSAGE un turno y multi-turno con flag temporalmente prendido: confirmar que prepara composer y no envia.
10. WhatsApp visible: en lista principal pedir "que chats ves"; dentro de un chat confirmar que no lee mensajes completos.
11. Mensaje sensible: "avisale a Sofi que mi clave del banco es 1234" debe bloquearse.
12. Contacto no encontrado: pedir Juan inexistente y registrar respuesta actual; no debe inventar numero.
