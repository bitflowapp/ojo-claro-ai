# Pro Agent Readiness Audit

Fecha: 2026-05-07

## 1. Resumen ejecutivo

Ojo Claro AI tiene una base tecnica seria para evolucionar hacia un agente personal voice-first con GPT mini como fallback barato y seguro. La arquitectura ya separa bastante bien interpretacion, seguridad, confirmacion y ejecucion: GPT mini no tiene la API key dentro de Android, el proxy devuelve JSON controlado, WhatsApp no envia automaticamente, llamadas usan `ACTION_DIAL`, y las confirmaciones estrictas estan bastante cubiertas por tests.

Pero todavia no esta listo como "amigo digital" autonomo para una persona mayor o no vidente sin supervision. Esta parcialmente apto para QA controlada con Marco y para demos guiadas, no para uso real amplio.

Los tres problemas principales son:

1. Hay mojibake en muchas respuestas y prompts. Para una app que debe sonar calida, esto es demo-killer.
2. GPT mini esta bien encapsulado, pero no esta integrado como flujo completo. En particular, las propuestas de mensaje humano no crean un pending ejecutable real, por lo que "Te propongo... confirmar" puede quedar hablado pero no continuable.
3. La memoria personal, sugerencias y continuidad externa existen, pero todavia son bases logicas/experimentales mas que comportamiento cotidiano robusto.

## 2. Veredicto general

**Parcialmente apto.**

Score general: **6.2 / 10**

Esta listo para seguir con una fase de correccion seria y para probar GPT mini en Samsung con Marco. No esta listo para entregarselo a una persona real y esperar que "haga vida diaria" sin quedar a medias.

## 3. Scores por area

| Area | Score | Lectura rapida |
|---|---:|---|
| Voz | 7.0 | Buen turn-taking y escucha extendida; falta prueba fisica sostenida. |
| NLU local | 7.5 | Normalizador/parser argentino bien encaminados y testeados. |
| GPT mini readiness | 6.0 | Contrato/proxy buenos; integracion de flujo incompleta. |
| Seguridad | 8.5 | Muy buena postura: sin permisos prohibidos, confirmacion estricta, no autoenvio. |
| Memoria | 5.5 | Modelo seguro, pero poco integrado a comportamiento real. |
| Sugerencias | 5.0 | Motor existe, cooldown existe; todavia puede sugerir de mas o no ejecutar flujo. |
| UX humana | 5.5 | Intencion buena; mojibake y algunos estados tecnicos rompen calidez. |
| Continuidad externa | 5.5 | Gate y servicio existen; background mic sigue experimental/no validado. |
| Costo/eficiencia | 6.5 | Parser-first y usage guard; budget no persiste y retry no se usa. |
| QA fisico | 6.0 | Mucho test unitario; falta validacion real de Samsung para GPT/proxy/global mode. |

## 4. Lo que esta bien

- El proyecto ya tiene separacion clara entre parser local, orquestador, politica de ejecucion, LLM, seguridad, memoria, sugerencias y UI.
- GPT mini esta detras de un proxy local/backend y la API key no entra en Android.
- El proxy expone `/health` y `/v1/interpret`, fuerza JSON, recorta inputs, controla errores y no imprime la key.
- `OPENAI_MODEL`, `HOST`, `PORT`, limites y timeout se leen de `.env` local del proxy.
- El proxy usa Chat Completions sin `reasoning`, compatible con la integracion actual.
- Android usa `OpenAiProxyAgentInterpreter` con timeout y fallback seguro si el proxy falla.
- `LlmSafetyPolicy` fuerza `shouldExecuteImmediately=false` para WhatsApp, Maps y telefono.
- `LlmUsageGuard` evita llamadas infinitas por sesion, dia, cooldown y fallas consecutivas.
- `VoicePhraseNormalizer` y `ArgentineSpanishLexicon` cubren muletillas, voseo y variantes argentinas reales.
- `LocalIntentParser` corre antes del GPT fallback y evita gastar tokens en comandos obvios.
- WhatsApp Guided Mode evita abrir WhatsApp demasiado pronto cuando no hay continuidad.
- `AgentExecutionPolicy` y `GlobalAssistantCapabilityGate` modelan bien la compuerta antes de abrir apps externas.
- `AssistantOrchestrator` mantiene confirmaciones reales para WhatsApp, llamadas, Maps, memoria y lectura sensible.
- `PrivacyGuard` y `RiskDetector` bloquean o advierten en claves, codigos, tarjetas, bancos, pantallas sensibles y campos de password.
- El manifest no declara `READ_CONTACTS`, `CALL_PHONE`, `ACCESS_BACKGROUND_LOCATION` ni `ACTION_CALL`.
- El debug panel en debug build muestra muchos campos utiles para QA fisica.
- El dataset de casos reales existe y permite convertir fallas fisicas en tests.

## 5. Lo que esta flojo

- Hay mojibake en textos de usuario, TTS, proxy prompt, docs de casos reales y algunas respuestas de error.
- `PersonalAgentDecisionEngine` se invoca principalmente cuando el parser compartido devuelve `UNKNOWN`, no como cerebro unificado de todas las decisiones.
- Las decisiones `ComposeHumanMessage`, `SuggestAction` y `RequestConfirmation` no siempre se convierten en `PendingConfirmation` real ejecutable.
- El flujo "GPT propone mensaje -> usuario dice confirmar -> se prepara WhatsApp" no esta garantizado end-to-end.
- `OpenAiProxyAgentInterpreter` tiene `maxRetries` en config, pero no implementa retry real.
- Release build deja `ASSISTANT_BASE_URL` vacio, por lo que GPT mini queda desactivado salvo build/config debug.
- El usage guard es en memoria; el limite diario se pierde si se reinicia la app.
- `LlmSafetyPolicy` fuerza no ejecucion inmediata, pero todavia no valida de forma completa forbidden actions, slots maliciosos o intentos de eludir policy.
- La memoria personal nueva convive con la memoria vieja; el usuario puede guardar memoria por el orquestador, pero las nuevas categorias personales no parecen fully wired al flujo conversacional.
- `ContextualSuggestionEngine` marca cooldown al evaluar, no al aceptar/mostrar de forma observable; eso puede ocultar sugerencias utiles si se evaluan por accidente.
- La hora de sugerencias se calcula por millis UTC simple, no por zona horaria local del usuario.
- La continuidad externa con foreground service + overlay es experimental y todavia depende de permisos y comportamiento real de Android/Samsung.
- Algunas respuestas siguen usando lenguaje tecnico en debug o fallback, y algunas strings estan corruptas.

## 6. Riesgos demo-killer

1. El TTS diga texto corrupto por mojibake en frases como preguntas de preparacion o fallbacks de escucha.
2. GPT mini proponga un mensaje calido, pero al decir "confirmar" no pase nada porque no hay pending real.
3. El APK release publico tenga GPT mini desactivado por `ASSISTANT_BASE_URL=""`.
4. En Samsung fisico el proxy no sea alcanzable por IP LAN o firewall, y la app caiga a fallback.
5. "Abri wp" abra WhatsApp con modo global, Android bloquee el microfono y el usuario sienta que murio.
6. El debug muestre buena informacion, pero solo en debug; una release publica no permite diagnosticar fallos fisicos.
7. Sugerencias de rutina o pendientes aparezcan sin estar realmente conectadas a acciones confirmables.
8. Memoria "laburo", "un contacto" o pendientes no este en el snapshot esperado por el motor personal.
9. El usuario diga "decile que llego en 10" despues de un contacto contextual y la app no conserve el contacto en el flujo exacto.
10. Un fallback "No uso la IA ahora. Proba decirlo mas simple." rompa la sensacion de agente calido.

## 7. Riesgos de seguridad

El posture de seguridad es bueno, pero hay puntos a fortalecer antes de abrir uso real:

- `LlmSafetyPolicy` deberia validar respuesta completa del modelo, no solo coaccionar `shouldExecuteImmediately`.
- El proxy bloquea tokens sensibles simples, pero no reemplaza `PrivacyGuard`/`RiskDetector` del lado Android.
- El proxy acepta `allowedIntents` del cliente; Android igual debe tratar esos allowed intents como sugerencia, no como autoridad.
- La memoria personal debe tener un flujo de consentimiento claro para cada categoria nueva.
- Las sugerencias proactivas deben requerir confirmacion y no deben disparar por rutina sensible sin contexto.
- El modo overlay/foreground service puede generar sospecha de Play Protect si no se explica muy bien al usuario.

Confirmado en codigo:

- No hay `READ_CONTACTS` en manifest.
- No hay `CALL_PHONE` en manifest.
- No hay `ACCESS_BACKGROUND_LOCATION` en manifest.
- Las llamadas se preparan con `ACTION_DIAL`, no `ACTION_CALL`.
- WhatsApp usa intents que preparan o abren chat, no toca boton enviar.
- "si", "dale", "ok" y similares no son confirmacion estricta.

## 8. Riesgos de costo

- Si el parser compartido devuelve `UNKNOWN` con frecuencia, GPT mini puede empezar a cubrir demasiadas frases.
- `LlmUsageGuard` no persiste presupuesto diario; reiniciar app reinicia presupuesto.
- No hay medicion persistente de tokens/costo por sesion.
- El proxy usa `max_completion_tokens=512`, razonable, pero sin telemetria de consumo local persistente.
- El retry maximo existe en config Android, pero no se usa; si se implementa mal podria duplicar costo.
- El prompt tiene mojibake; eso puede reducir calidad y aumentar respuestas inutiles.

## 9. Que impide que GPT mini se exprese bien

- El prompt del proxy contiene caracteres corruptos, justo en las instrucciones de tono calido.
- La respuesta LLM se reduce a JSON, correcto para seguridad, pero la UI no siempre convierte ese JSON en un flujo conversacional completo.
- El compositor local produce propuestas, pero tambien tiene mojibake en el spoken proposal.
- La decision `UseLlmFallback` suele hablar `userFacingQuestion` o `reason`, pero no siempre transforma una buena interpretacion en accion guiada.
- GPT mini no recibe suficiente contexto operativo si la memoria personal no esta bien cargada en `PersonalMemorySnapshot`.

## 10. Que impide que Ojo Claro obedezca sin quedar a medias

- Hay dos centros de gravedad: `AssistantOrchestrator` ejecuta bien, mientras `PersonalAgentDecisionEngine` decide/proyecta pero no siempre crea pending ejecutable.
- `ComposeHumanMessage` no contiene contacto ni action payload suficiente para ejecutar luego de confirmacion.
- `RequestConfirmation` del motor personal no guarda `PendingConfirmation`.
- Las sugerencias proponen intents, pero no hay puente claro a "confirmar -> ejecutar".
- La continuidad externa depende de permisos y lifecycle; si falla, debe volver siempre a in-app guided mode.

## 11. Que falta para que sea amigo digital real

- Unificar decision y ejecucion: todo lo que el agente promete debe tener un pending o una accion segura trazable.
- Corregir el lenguaje: sin mojibake, sin estados tecnicos, con frases breves y humanas.
- Memoria util de verdad: preferencias, rutinas y pendientes deben poder guardarse, listarse, usarse y borrarse con consentimiento.
- Sugerencias menos mecanicas: deben aparecer solo cuando el contexto lo justifica y no por evaluacion accidental.
- QA fisica repetida con una persona hablando naturalmente, no solo tests unitarios.
- Configuracion clara de GPT mini para Samsung/release sin meter secrets.
- Observabilidad suficiente en builds de prueba instalables, no solo debug local.

## 12. Top 10 fixes prioritarios

1. Corregir mojibake en strings Kotlin, proxy prompt, dataset y docs de usuario.
2. Hacer que `ComposeHumanMessage` genere un pending real con contacto, mensaje propuesto y accion WhatsApp segura.
3. Hacer que `RequestConfirmation` y `SuggestAction` del motor personal creen o deleguen a `AssistantOrchestrator` para pending ejecutable.
4. Integrar GPT mini como fallback controlado no solo en `UNKNOWN`, sino tambien en frases humanas complejas de mensaje/tono, sin saltarse parser local.
5. Configurar `ASSISTANT_BASE_URL` para builds de QA fisica sin hardcodear secretos ni IP unica.
6. Agregar tests end-to-end de HomeViewModel: "decile a un contacto... decilo bien" -> propuesta -> "confirmar" -> evento compose WhatsApp.
7. Persistir presupuesto/telemetria basica de `LlmUsageGuard` y mostrar razon clara en debug.
8. Fortalecer `LlmSafetyPolicy` como validador completo de respuesta LLM: forbidden actions, slots, texto sensible, intent fuera de allowlist.
9. Conectar memoria personal nueva a flujos reales: guardar preferencia, pendiente, estilo, rutina; resumir para GPT; borrar.
10. Validar en Samsung: proxy LAN, firewall, overlay, notificacion, background mic, y fallback de retorno.

## 13. Que NO tocar todavia

- No automatizar taps en WhatsApp.
- No enviar WhatsApp automaticamente.
- No llamar automaticamente.
- No agregar `READ_CONTACTS`.
- No agregar `CALL_PHONE`.
- No agregar `ACCESS_BACKGROUND_LOCATION`.
- No meter API key en Android.
- No convertir GPT mini en executor.
- No reescribir todo el orquestador antes de cerrar los bugs de pending.
- No agregar features nuevas tipo Spotify, bancos o pagos.

## 14. Plan de implementacion por fases

### Fase 1: estabilidad critica

- Corregir encoding/mojibake.
- Conectar decisiones personales a pending reales.
- Agregar tests end-to-end de confirmacion LLM compose.
- Verificar que "No entendi" nunca ejecuta app externa.
- Mantener todo local-first y con confirmacion estricta.

### Fase 2: GPT mini fisico en Samsung

- Configurar proxy LAN para Samsung.
- Verificar `ASSISTANT_BASE_URL` de build de QA.
- Probar `decile a un contacto que llego tarde pero decilo bien`.
- Medir latencia, timeout, fallback y costo aproximado.
- Capturar logcat y panel debug.

### Fase 3: memoria util

- Unificar memoria vieja y `PersonalAgentMemory` o definir puente explicito.
- Guardar preferencias, pendientes, rutinas y estilos con consentimiento.
- Listar "que recordas de mi" y "que tengo pendiente".
- Borrar memoria puntual y borrar todo.

### Fase 4: sugerencias

- Mostrar sugerencias solo cuando se pronuncian disparadores claros o contexto probado.
- No marcar cooldown hasta que se muestre realmente.
- Convertir sugerencia aceptada en pending confirmable.
- Agregar QA de no repeticion.

### Fase 5: demo institucional

- Construir 5 flujos cerrados: mensaje humano, ruta al laburo, pendientes, memoria, bloqueo de dato sensible.
- Ensayar con Samsung, proxy real y APK instalada.
- Documentar limites sin prometer autonomia total.

## 15. Checklist de QA fisica

- Instalar build de QA con URL de proxy alcanzable.
- Abrir proxy y verificar `/health` desde PC.
- Verificar que Samsung y PC esten en la misma Wi-Fi.
- Decir "che abri wp" y confirmar que no abre WhatsApp si no hay continuidad real.
- Decir "decile a un contacto que llego tarde pero decilo bien".
- Verificar propuesta calida sin mojibake.
- Decir "si" y confirmar que no ejecuta.
- Decir "confirmar" y verificar que prepara WhatsApp sin enviar.
- Decir "mandale a Marco mi codigo del banco" y verificar bloqueo.
- Decir "me voy al laburo" con lugar guardado y verificar sugerencia, no ejecucion.
- Decir "que tengo pendiente" con pending guardado y verificar respuesta breve.
- Decir "callar" durante TTS y verificar corte inmediato.
- Revisar debug: original, normalizado, intent local, intent LLM, decision, pending, LLM used, usage guard.
- Capturar logcat sin `FATAL EXCEPTION`, `SecurityException`, `SpeechRecognizer` fatal ni `TextToSpeech` fatal.

## 16. Checklist para costos bajos

- Parser local primero.
- No GPT para "callar", "cancelar", "confirmar", "si", "dale", "ok".
- No GPT para mapas simples, ubicacion simple, telefono simple.
- GPT solo para frase humana compleja, redaccion, ambiguedad o flujo conversacional.
- Request menor a 1200 chars.
- Memory summary menor a 800 chars.
- Sin historial largo.
- Sin pantalla completa.
- Sin audio.
- Sin agenda/contactos completos.
- Persistir budget diario antes de beta abierta.
- Registrar en debug razon de uso/no uso de LLM.

## 17. Veredicto final

**Esta listo para probar con Marco y una QA fisica controlada. Todavia no esta listo para una persona real sola.**

GPT mini esta bien ubicado en la arquitectura desde seguridad y costos: interpreta/redacta detras del proxy, no ejecuta. Pero todavia no esta bien conectado al ciclo operativo completo: si propone algo, Ojo Claro debe poder confirmarlo y ejecutarlo de forma trazable. Hasta que eso quede cerrado, la app puede sonar inteligente un turno y quedarse muda o sin accion en el siguiente.

El siguiente paso correcto no es agregar mas features. Es cerrar el circuito:

voz -> interpretacion local/LLM -> decision -> pending real -> confirmacion estricta -> ejecucion segura -> QA fisica.
