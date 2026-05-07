# Auditoría blind-first — Ojo Claro AI

Fecha: 2026-05-04
Revisión: paso de MVP a base de producto serio
Foco: experiencia para una persona que NO VE.

Este documento responde, con evidencia del código actual, las preguntas obligatorias de la auditoría blind-first, lista los problemas reales encontrados, las mejoras aplicadas en esta misma iteración, las que se postergaron deliberadamente y el criterio de "listo para probar con usuario real".

---

## 1. Preguntas obligatorias

### ¿La app habla cuando debe?

**Mayormente sí, con un agujero importante.**

- Sí habla cuando: hay respuesta de backend (`HomeViewModel.publishAssistantResponse`), hay error de cámara/permiso (`onCameraPermissionDenied`, `onCameraError`), entra al modo cámara (`startScanning` con `force = true`), reconoce texto (`onTextScanResult`), no encuentra texto (`onTextScanNoTextFound`), abre WhatsApp o necesita confirmación (`publishLocalMessage` desde `handleExternalCommandIfNeeded`).
- **Agujero**: la app NO habla al abrirse por primera vez. El estado inicial es `HomeUiState(spokenText = "Listo. Toca DESCRIBIR, Leer texto o Pedir ayuda.")` pero ese texto es solo visual. `LaunchedEffect(viewModel) { viewModel.speechEvents.collect(...) }` recolecta eventos pero el ViewModel no emite el saludo inicial. Una persona ciega abre la app y escucha silencio.
- **Mejora aplicada**: el orquestador (FASE 6) emite un `SpeechEvent` de bienvenida + invitación a comandos al primer ciclo de vida. El onboarding (FASE 2) además habla en cada paso.

### ¿La app avisa cuando entra al modo cámara?

**Sí.** `HomeViewModel.startScanning()` emite `SpeechEvent("Buscando texto con la cámara. Apuntá al texto.", force = true)`. El estado pasa a `SCANNING` con `statusText = "Leyendo texto"`.

### ¿La app explica permisos?

**Parcialmente.**

- Cámara denegada: dice "Necesito permiso de cámara para leer texto." (`onCameraPermissionDenied`).
- Accesibilidad inactiva: dice "Necesito activar el permiso de accesibilidad para leer la pantalla." (`AccessibilityScreenReader.readVisibleScreen`).
- **Falta**: no se explica *por qué* se piden los permisos antes de pedirlos, ni qué hace cada uno. El onboarding nuevo (FASE 2) cubre esto y la `Capability.userMessageWhenMissing` (FASE 5) deja un único punto de mensaje cuando una capacidad falta.

### ¿La app avisa si no puede hacer algo?

**Sí, en general.**

- Sin WhatsApp: "No encontré WhatsApp instalado." (`WhatsAppIntentHelper`).
- Sin internet: fallback local explicando "No pude conectar con el asistente. Entendí: $text. Probá de nuevo o revisá internet." (`HomeViewModel.localFallback`).
- **Pendiente**: el botón DESCRIBIR todavía depende del backend (mock). Si el backend está caído, la persona escucha el fallback de internet pero no entiende que la feature en sí no está completa. Documentado como riesgo.

### ¿El botón Callar está siempre disponible?

**Sí.** Está fijo en `HomeScreen` (es el último secundario) y también en `TextScanScreen` como "Callar y volver". Llama a `speechController.stop()` y `viewModel.onStopSpeechRequested()`, que cancela voz, limpia confirmación pendiente y vuelve a IDLE.

### ¿Se puede volver atrás?

**Sí en escáner, no claro en home.**

- En `TextScanScreen`, "Callar y volver" cierra y vuelve a home.
- En home no hay "atrás" porque es la pantalla raíz. Pero si se está en un estado erróneo (`AppState.ERROR`), tocar Callar limpia y vuelve a IDLE — eso cumple el principio "siempre hay salida".

### ¿TalkBack lee botones y estados de forma clara?

**Sí, con un detalle.** Cada botón tiene `contentDescription` corto y propio: "Describir lo que tengo enfrente.", "Leer texto con la camara.", "Pedir ayuda.", "Callar la voz.", "Callar la voz y volver a la pantalla principal.", "Volver a la pantalla principal." (verificado en `HomeScreen` y `TextScanScreen`). Estado anunciado como "Estado: Listo / Escuchando / Leyendo texto / Procesando / Hablando / Aviso".

**Detalle**: el `Text` interno de cada botón ("DESCRIBIR", "Leer texto", etc.) y el `contentDescription` del Button son distintos. Compose lee `contentDescription` cuando está seteado, lo cual es lo correcto. Verificado en QA del emulador (`docs/ANDROID_EMULATOR_FINAL_QA.md`).

### ¿Hay textos demasiado largos?

**No de manera sistemática.** Mensajes promedio entre 5 y 18 palabras. Lo más largo: "Abrí WhatsApp con el mensaje preparado. Elegí el chat de $contactName y confirmá manualmente antes de enviarlo." — son 18 palabras pero es información necesaria de seguridad. `PrivacyGuard.sanitizeForSpeech` ya recorta a 1.200 caracteres y `AccessibilityScreenReader` a 1.200. Los mensajes del orquestador (FASE 6) y del centro de ayuda (FASE 3) priorizan frases ≤ 14 palabras.

### ¿Hay estados silenciosos?

**Sí, dos.**

- **Arranque**: descripto arriba. Mejora aplicada.
- **Estado WAITING_CONFIRMATION**: cuando el usuario pide componer mensaje, la app dice "Voy a preparar un mensaje para X que dice: Y. Confirmá antes de enviarlo." y queda esperando. Hasta esta iteración el `AppState` reportaba `SPEAKING` y luego `IDLE`, sin marcar visualmente ni anunciar que está esperando confirmación. Mejora aplicada en FASE 9: nuevo estado `WAITING_CONFIRMATION`. La voz ya repetía la pregunta antes; ahora el statusText también lo refleja.

### ¿Hay posibilidad de loop de voz?

**Mitigada en varios frentes.**

- `SpeechController` deduplica por `lastSpokenKey + DEDUP_WINDOW_MILLIS=5000`. Llamadas iguales en menos de 5 s se silencian, salvo `force=true`.
- `StableTextDetector.emittedKeys` evita repetir el mismo OCR dentro del mismo escaneo.
- `StableTextDetector.noTextAnnounced` deja la frase "No encontré texto claro" en una sola vez por sesión.
- `TextRecognitionAnalyzer.minCallbackIntervalMillis=700` y `processing` `AtomicBoolean` impiden cascadas.
- `SpeechEvent` es un `MutableSharedFlow` con `BufferOverflow.DROP_OLDEST` y `extraBufferCapacity = 4`, evitando acumulación de frases viejas.
- El bucle de "no text" (`LaunchedEffect(Unit) { while (isActive) { delay(1_000L); detector.onNoText(...) } }`) es seguro porque depende del estado interno del detector.

Nuevo riesgo evaluado: si el flujo onboarding/ayuda dispara muchos `SpeechEvent` seguidos, el dedup de 5 s bloquearía el segundo aunque sea otra frase. Solución: cuando una frase es nueva, el dedup la deja pasar (compara por `lastSpokenKey`, no por timestamp absoluto). OK.

### ¿Hay acciones sensibles sin confirmación?

**No, todas requieren confirmación humana.**

- `COMPOSE_WHATSAPP_MESSAGE` siempre crea un `PendingConfirmation` y exige "confirmar" o "cancelar".
- `OPEN_WHATSAPP` y `READ_VISIBLE_SCREEN` son lecturas/aperturas neutras: no pueden enviar nada.
- Aunque el usuario diga "confirmar" sin nada pendiente, la respuesta es: "No hay ninguna acción pendiente para confirmar." (`CommandRouter.route` línea 70).
- El TTL de la confirmación (2 minutos) es saludable y se anuncia: "La acción pendiente venció. Volvé a pedirla."
- WhatsApp se abre con `ACTION_SEND` *prelllenado*, nunca se envía solo.

### ¿Hay permisos no usados?

**No.** Solo `CAMERA` (OCR) e `INTERNET` (backend). El servicio de accesibilidad usa `BIND_ACCESSIBILITY_SERVICE` que es una protección de Android, no un permiso de runtime. No hay `ACCESS_FINE_LOCATION`, `RECORD_AUDIO`, `READ_CONTACTS` ni `READ_EXTERNAL_STORAGE`. Verificado en `androidApp/src/main/AndroidManifest.xml` y en `docs/ANDROID_REAL_DEVICE_QA.md`.

---

## 2. Problemas encontrados

| # | Problema | Severidad | Evidencia |
|---|----------|-----------|-----------|
| 1 | App no habla al abrir | Alta | `HomeUiState` inicial sin `SpeechEvent` |
| 2 | No hay onboarding | Alta | Sin `OnboardingState` ni pantalla |
| 3 | "Pedir ayuda" cae a fallback de emergencia, no explica comandos | Media | `localFallback` para `EMERGENCY_HELP` |
| 4 | DESCRIBIR depende de backend mock | Media | No hay capacidad CLOUD_AI declarada |
| 5 | Falta estado `WAITING_CONFIRMATION` visible | Media | `AppState` no lo modela |
| 6 | `HomeViewModel` mezcla parser + router + IA + UI state | Media | 404 líneas, varios responsibilities |
| 7 | No hay `AssistantOrchestrator` central; las decisiones se reparten | Media | Lógica en VM y en Screen |
| 8 | `CapabilityRegistry` no existe; los chequeos están dispersos | Media | `enum Capability` aislado |
| 9 | `PrivacyGuard` sin tests | Baja | `androidApp/src/test/` no lo cubre |
| 10 | `LocalRuleBasedAiProvider` no se usa todavía | Baja | Sin consumidor |
| 11 | `usesCleartextTraffic="true"` queda activo en producción | Baja | Manifest |
| 12 | DESCRIBIR no captura imagen | Baja | Documentado como pendiente |

---

## 3. Riesgos reales para usuario no vidente

1. **Silencio al abrir** (problema #1) → la persona no sabe que la app se cargó. Toca al azar. Mejora aplicada.
2. **"Pedir ayuda" mal calibrado** (problema #3) → el usuario pidió guía de uso y le respondieron "llamá a tu contacto de emergencia". Confusión y posible alarma. Mejora aplicada vía centro de ayuda (FASE 3).
3. **DESCRIBIR engaña** (problema #4) → el botón se llama DESCRIBIR pero hoy depende del backend mock. Si la persona apoya el celular sobre algo, la app dice "no pude conectar" y la persona deduce que la app está rota, no que la feature no existe. Mitigación documentada; corrección plena queda para iteración con CLOUD_AI real.
4. **Confirmación silenciosa** (problema #5) → se pide confirmación pero el statusText no destaca el modo. Usuario distraído podría tocar otro botón y perder el contexto. Mejora aplicada (FASE 9).
5. **Acumulación de lógica en VM** (problema #6) → un cambio futuro mal hecho podría romper varios caminos. Mejora aplicada (FASE 6 + 8).

---

## 4. Mejoras aplicadas en esta iteración

- **Onboarding accesible** (FASE 2): pasos de bienvenida con voz + texto, repetible, salteable, persistido en `SharedPreferences`.
- **Centro de ayuda de voz** (FASE 3): botón "¿Qué puedo decir?" responde con ejemplos cortos.
- **Confirmaciones seguras** (FASE 4): mensajes claros sin acción pendiente, tests de confirmar/cancelar.
- **CapabilityRegistry serio** (FASE 5): expone CAMERA, ACCESSIBILITY_SERVICE, WHATSAPP, TTS, OCR_LOCAL, CLOUD_AI con `userMessageWhenMissing` único.
- **AssistantOrchestrator** (FASE 6): conecta CommandRouter, CapabilityRegistry, AiProvider, Accessibility, WhatsApp, SpeechEvent.
- **Capa IA futura** (FASE 7): `AiTask`, `AiContext`, `AiResult`, `AiProvider`, `LocalRuleBasedAiProvider`, `FutureCloudAiProvider` placeholder seguro.
- **HomeViewModel adelgazado** (FASE 8): delega al Orchestrator.
- **Estados nuevos** (FASE 9): `WAITING_CONFIRMATION`, `PERMISSION_REQUIRED` con mensajes y salidas claras.
- **OCR robusto** (FASE 10): revisión de cierres y `noTextAnnounced`.
- **PrivacyGuard reforzado** (FASE 11): nuevas reglas testeables.
- **Documentación de producto** (FASE 12): `PRODUCT_READINESS_PLAN.md` y `AI_READY_ARCHITECTURE.md`.
- **Tests** (FASE 13): cubre comandos, OCR, capabilities, IA local, privacidad, orquestador.

---

## 5. Mejoras pospuestas

| Mejora | Por qué se posterga | Cuándo se aborda |
|--------|---------------------|------------------|
| DESCRIBIR con visión real (CLOUD_AI) | Requiere claves API y política de costos; el prompt explícito dice "no conectar IA cloud real todavía" | Cuando se firme proveedor y se tenga consentimiento del usuario por mensaje hablado |
| Login y pagos | Excluido por prompt | Iteración comercial |
| Adaptación iOS | Excluido por prompt | Iteración multiplataforma |
| Eliminación de `usesCleartextTraffic` | Romper conexión con backend en `10.0.2.2` (emulador) sin TLS | Cuando se monte backend con HTTPS |
| Tests instrumentales de UI con Compose | Costo alto, baja relación señal/ruido en MVP | Cuando se estabilicen los flujos de IA cloud |
| Soporte gestos avanzados | Riesgo de confundir a usuarios de TalkBack | Después de validar con usuarios ciegos reales |
| Lectura más profunda de pantalla (multi-ventana) | `AccessibilityService` puede pasar a leer demasiado y romper privacidad | No mientras no exista una política clara |
| Reintento automático de IA | Aumenta superficie de error y silencios | Cuando haya provider cloud |

---

## 6. Criterio de "listo para probar con usuario real"

Una persona no vidente puede hacer la prueba cuando se cumple lo siguiente:

- [x] La app habla al abrir y guía el primer uso (onboarding por voz).
- [x] El botón Callar está disponible en home y en escáner; detiene TTS y limpia confirmación pendiente.
- [x] Cada estado tiene mensaje hablado y salida (volver/callar/confirmar/cancelar/abrir ajustes).
- [x] Ninguna acción sensible se ejecuta sin confirmación verbal.
- [x] Mensajes ≤ 18 palabras, en español rioplatense, sin tecnicismos.
- [x] La app explica los permisos *antes* de pedirlos (onboarding) y cuando faltan (CapabilityRegistry).
- [x] No se guardan OCR, capturas de pantalla ni mensajes (`PrivacyGuard`, manifest sin `WRITE_EXTERNAL_STORAGE`).
- [x] No se envían mensajes automáticamente. WhatsApp siempre prelllenado, nunca disparado.
- [x] No se sube contenido al backend salvo el comando del usuario (no se transmite imagen ni texto visible).
- [x] Tests unit verdes (`:androidApp:testDebugUnitTest`, `:shared:allTests`).
- [x] Build debug exitosa (`:androidApp:assembleDebug`).
- [ ] Validación con persona ciega real (3 usuarios + 1 cuidador, ver `docs/ACCESSIBILITY_CHECKLIST.md`). **Pendiente — requiere intervención humana fuera del alcance de esta iteración.**
- [ ] Política de privacidad publicada y aceptada en arranque por voz. **Pendiente — depende del producto y del proveedor de IA cloud.**
- [ ] Confirmación de TTS audible en dispositivo físico (no en emulador). **Pendiente — requiere QA en hardware.**

Si los tres ítems pendientes quedan abiertos, la app está lista para **prueba supervisada** con persona ciega y un asistente; **no** lista para distribución abierta.

---

## 7. Cómo se reaplica la auditoría

Releer este documento antes de mergear cualquier cambio que toque:

- `HomeViewModel`, `HomeScreen`, `TextScanScreen`, `OjoClaroAccessibilityService`, `WhatsAppIntentHelper`.
- `SpeechController` o cualquier emisor de `SpeechEvent`.
- `CapabilityRegistry`, `AssistantOrchestrator`, `LocalRuleBasedAiProvider`, `FutureCloudAiProvider`.
- `PrivacyGuard` o reglas de retención.
- Manifest, permisos, descripciones de servicio.

Si el cambio rompe alguno de los criterios de la sección 6, **debe quedar pendiente o documentarse explícitamente como riesgo** antes de avanzar.

---

## 8. Actualización 2026-05-05 - comportamiento de agente personal

La app ahora empieza a comportarse como agente operativo sin pasar a vigilancia:

- Recuerda preferencias útiles solo con confirmación hablada.
- Lista únicamente resúmenes seguros.
- Borra memoria local con confirmación.
- Detecta patrones frecuentes como metadatos, no como contenido privado.
- Advierte riesgos antes de leer o responder cuando aparece dinero, banco, contraseñas, códigos o urgencia.
- Mantiene Callar y Cancelar como salidas inmediatas.

Nuevo criterio blind-first: cualquier memoria o sugerencia futura debe poder explicarse en voz con una frase corta, reversible y sin revelar datos sensibles.
