# Estela V1.10 Release Candidate

Fecha: 2026-06-11
Rama: `fix/estela-real-device-intelligence`

## Resumen ejecutivo

Estela V1.10 queda como release candidate orientada a activacion rapida y validacion fisica. No agrega nuevas capacidades sensibles. La base esperada combina:

- V1.8: WhatsApp real seguro con confirmacion fuerte.
- V1.9: GPS, conversacion backend/ngrok y memoria corta validadas.
- V1.10: activacion rapida por Quick Settings tile, notificacion y accesibilidad, sin hacks de power button ni captura de volumen.

Este documento separa alcance de producto, exclusiones de PR y checklist antes de merge. El worktree actual esta sucio y requiere seleccion manual de archivos antes de abrir PR.

## V1.8 incluido

- Flujo WhatsApp real con contacto confirmado antes de preparar envio.
- Diferencia entre confirmar contacto y confirmar envio.
- `si` no envia durante confirmacion de contacto.
- `si` final no envia.
- `envia` envia solo despues de verificacion explicita.
- Bloqueo de sensibles.
- Contratos de safe-send: un solo punto autorizado para tap de enviar.

## V1.9 incluido

- GPS mas util para `Donde estoy`.
- Ruta activa con `Cuanto falta`, `Repeti`, `Recalcula` y cancelacion.
- Aviso si no llegan updates de ubicacion.
- Conversacion backend local/ngrok con `reply` no vacio.
- Memoria corta conversacional con animo/eventos no sensibles.
- Prompt conversacional mas humano.
- Diseno cloud TTS documentado, no activado como feature productiva.

## V1.10 incluido

- Quick Settings tile `Estela`.
- Accion del tile: iniciar modo global de Estela y entrada de voz cuando microfono y accesibilidad estan listos.
- Fallback honesto si faltan permisos: `Necesito permiso de microfono y accesibilidad activa.`
- Notificacion foreground con acciones seguras: `Hablar`, `Callar`, `Cerrar`.
- Accessibility button/shortcut documentado.
- `flagRequestShortcutWarningDialogSpokenFeedback` agregado para mejorar feedback hablado del shortcut.
- No captura de power button.
- No captura de teclas de volumen.
- No envio WhatsApp desde tile ni notificacion.

## Como activar Estela ahora

1. Abrir la app normalmente.
2. Usar el tile `Estela` desde Ajustes rapidos.
3. Usar la notificacion foreground con `Hablar`, `Callar` o `Cerrar`.
4. Usar el boton flotante/overlay de accesibilidad de Estela.
5. Usar el accessibility button o shortcut del sistema si Android/Moto lo ofrece.

## Estado de tests

Estado verificado en esta preparacion de RC:

- Backend: `.\.venv\Scripts\python.exe -m pytest -q` desde `backend`: 56 passed, 1 warning de Starlette/TestClient.
- Android: `.\gradlew.bat :androidApp:testDebugUnitTest --console=plain` desde la raiz: BUILD SUCCESSFUL.
- Resultados XML Android: 2432 tests, 0 failures, 0 errors, 0 skipped.

Comandos de referencia antes de merge:

- Backend: `cd backend; .\.venv\Scripts\python.exe -m pytest -q`
- Android: `.\gradlew.bat :androidApp:testDebugUnitTest --console=plain`

## Estado de APK

- APK debug instalada previamente en Moto con base URL ngrok.
- No reinstalar por cambios solo de docs/scripts.
- Reinstalar solo si entra codigo Android productivo o manifest nuevo despues de este RC.

## Inventario del worktree

El worktree actual esta mezclado. No commitear en bloque. Usar seleccion explicita.

### Android producto modificado

- `androidApp/src/main/AndroidManifest.xml`
- `androidApp/src/main/java/com/ojoclaro/android/MainActivity.kt`
- `androidApp/src/main/java/com/ojoclaro/android/accessibility/OjoClaroAccessibilityService.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/AgentConversationManager.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/LocalIntentParser.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/apps/SafeAppLauncher.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/core/AgentActionEvaluator.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/core/planner/AgentPlanner.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/core/screen/ScreenQueryPhrases.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/conversation/ConversationalRepair.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppChatListPhrases.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/situation/SituationBrain.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/task/AgentTaskOrchestrator.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/task/AgentTaskPlanner.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ai/LocalRuleBasedAiProvider.kt`
- `androidApp/src/main/java/com/ojoclaro/android/capabilities/Capability.kt`
- `androidApp/src/main/java/com/ojoclaro/android/domain/AgentExecutionPolicy.kt`
- `androidApp/src/main/java/com/ojoclaro/android/domain/PersonalAgentDecisionEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/CommandRouter.kt`
- `androidApp/src/main/java/com/ojoclaro/android/external/WhatsAppIntentHelper.kt`
- `androidApp/src/main/java/com/ojoclaro/android/global/GlobalAssistantMode.kt`
- `androidApp/src/main/java/com/ojoclaro/android/global/GlobalAssistantNotifier.kt`
- `androidApp/src/main/java/com/ojoclaro/android/global/GlobalAssistantService.kt`
- `androidApp/src/main/java/com/ojoclaro/android/help/VoiceHelpCenter.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/EstelaIntentClient.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/EstelaIntentEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/LlmAgentClientConfig.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/LlmAgentNetworkClient.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/ProxyHealthProbe.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/SafeAiFallbackCopy.kt`
- `androidApp/src/main/java/com/ojoclaro/android/speech/SpeechController.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/OjoClaroApp.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/components/ListenIndicator.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/components/ResponseCard.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/CriticalLocalWhatsAppRouter.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt`
- `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeViewModel.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/AndroidSpeechInputEngine.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/EstelaVoiceProfile.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/OjoClaroQuickTileService.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/VoiceCommandDispatcher.kt`
- `androidApp/src/main/res/xml/ojo_claro_accessibility_service.xml`

### Android producto nuevo

- `androidApp/src/main/java/com/ojoclaro/android/accessibility/WhatsAppDraftAccess.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/mission/`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/conversation/ConversationGate.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/conversation/ConversationShortMemory.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/conversation/EstelaCompanionPhrases.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/screen/AndroidScreenNavigationActions.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/screen/ScreenNavigationCommand.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/screen/ScreenNavigationUseCase.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppMessageReadPhrases.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppOrdinalChatOpenUseCase.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppOrdinalChatParser.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppPhraseNormalizer.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppReadAloudPhrases.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppSmartComposeParser.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppVisibleMessagesReader.kt`
- `androidApp/src/main/java/com/ojoclaro/android/agent/runtime/whatsapp/WhatsAppVoiceSendPhrases.kt`
- `androidApp/src/main/java/com/ojoclaro/android/llm/EstelaConversationClient.kt`
- `androidApp/src/main/java/com/ojoclaro/android/outdoor/`
- `androidApp/src/main/java/com/ojoclaro/android/speech/EstelaEarcons.kt`
- `androidApp/src/main/java/com/ojoclaro/android/voice/WakeWordStripper.kt`

### Backend modificado o nuevo

- `backend/.env.example`
- `backend/app/core/config.py`
- `backend/app/main.py`
- `backend/app/models/assist.py`
- `backend/app/routes/assist.py`
- `backend/app/services/assistant_service.py`
- `backend/tests/test_assist_api.py`
- `backend/app/models/agent.py`
- `backend/app/models/intent.py`
- `backend/app/routes/agent.py`
- `backend/app/routes/conversation.py`
- `backend/app/routes/intent.py`
- `backend/app/routes/outdoor.py`
- `backend/app/services/agent_prompt.py`
- `backend/app/services/agent_service.py`
- `backend/app/services/agent_tools.py`
- `backend/app/services/conversation_service.py`
- `backend/app/services/intent_prompt.py`
- `backend/app/services/intent_service.py`
- `backend/app/services/outdoor_service.py`
- `backend/tests/test_agent_api.py`
- `backend/tests/test_conversation_api.py`
- `backend/tests/test_intent_api.py`
- `backend/tests/test_outdoor_api.py`
- `backend/tests/test_outdoor_reverse_api.py`

### Tests Android modificados o nuevos

- `androidApp/src/test/java/ai/ojoclaro/adapter/LlmIntentAdapterTest.kt`
- `androidApp/src/test/java/ai/ojoclaro/integration/WhatsappIntentEndToEndTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/agent/`
- `androidApp/src/test/java/com/ojoclaro/android/agent/runtime/`
- `androidApp/src/test/java/com/ojoclaro/android/domain/`
- `androidApp/src/test/java/com/ojoclaro/android/global/AndroidManifestSafetyTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/global/GlobalAssistantModeTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/global/GlobalAssistantNotifierTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/global/WhatsAppSafeSendContractTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/global/ConversationContinuityContractTest.kt`
- `androidApp/src/test/java/com/ojoclaro/android/outdoor/`
- `androidApp/src/test/java/com/ojoclaro/android/ui/home/`
- `androidApp/src/test/java/com/ojoclaro/android/voice/`

### Docs y scripts modificados o nuevos

- `docs/ESTELA_ACTIVATION_SHORTCUTS.md`
- `docs/ESTELA_CLOUD_TTS_DESIGN.md`
- `docs/ESTELA_DEMO_CHECKLIST.md`
- `docs/ESTELA_V18_WHATSAPP_FLOW.md`
- `docs/ESTELA_V19_GPS_CONVERSATION.md`
- `docs/KNOWN_RISKS_AND_NEXT_STEPS.md`
- `docs/ESTELA_V110_RELEASE_CANDIDATE.md`
- `docs/ESTELA_PHYSICAL_VALIDATION_V110.md`
- `scripts/qa/check-backend.ps1`
- `scripts/qa/check-conversation.ps1`
- `scripts/qa/adb-smoke-estela.ps1`
- `scripts/qa/release-v110-smoke.ps1`

### No deben commitearse

- `.idea/.name`
- `.idea/caches/deviceStreaming.xml`
- `.idea/vcs.xml`
- `estela_diag_after_tap.png`
- `estela_diag_scroll1.png`
- `estela_diag_top.png`
- `estela_qa_01_devoptions.png`
- `estela_qa_02_button_fixed.png`
- `estela_qa_03_whatsapp.png`
- `estela_qa_04_expanded.png`
- `estela_qa_05_after_callar.png`
- Cualquier `.env` real o archivo con secretos.
- APKs, AABs y salidas de build generadas.

## Riesgos conocidos

- El backend depende de PC local y ngrok; si ngrok cae, la app instalada queda apuntando a una URL no disponible.
- GPS interior puede fluctuar; validar exterior con acompanante.
- No usar Estela como autoridad para cruzar calles.
- WhatsApp real solo debe probarse con contacto propio.
- El worktree mezcla cambios grandes: no hacer `git add .`.
- `.idea` ya aparece modificada/no trackeada; ignorarla no quita cambios trackeados existentes.

## Queda para V1.11

- Default assistant app / `VoiceInteractionService` como investigacion separada.
- Validacion OEM especifica para Moto/Samsung de gestures de sistema.
- Mejorar flujo de instalacion de tile/shortcut para usuario no tecnico.
- Normalizacion Argentina pendiente para contactos WhatsApp.
- Verificar titulo del chat dentro del tap de WhatsApp antes de envio real.
- Cloud TTS productivo si se decide activar.

## Checklist antes de merge

- [ ] Revisar `git status --short --branch` y seleccionar archivos a mano.
- [ ] Confirmar que `.idea/` no entra al PR.
- [ ] Confirmar que capturas `estela_*.png` no entran al PR.
- [ ] Confirmar que no hay `.env` ni secretos.
- [ ] Ejecutar backend tests.
- [ ] Ejecutar Android tests.
- [ ] Ejecutar `scripts/qa/release-v110-smoke.ps1` sin llamadas pagas por defecto.
- [ ] Ejecutar `docs/ESTELA_PHYSICAL_VALIDATION_V110.md` en Moto.
- [ ] Validar WhatsApp real solo con chat propio si Marco decide hacerlo.
- [ ] Verificar que no se agrego captura de power button ni volumen.
- [ ] Revisar PR con foco en regresiones de WhatsApp V1.8 y GPS/conversacion V1.9.
