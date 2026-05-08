# Ojo Claro AI — Auditoría de producto y plan de demo

Fecha: 2026-05-06
Autor: Auditoría externa (rol: director de producto senior, UX conversacional, accesibilidad)
Alcance: producto, no código. No se modificó nada del repositorio.
Audiencia: fundador, equipo de demo, próximos contribuyentes Codex, posibles aliados (fundación, municipio, institución).

---

## 1. Resumen ejecutivo

Ojo Claro AI es, hoy, un asistente Android voice-first **honesto, local-first y razonablemente seguro**. La capa de voz está madura: escucha continua, TTS con dedup, "callar" como interrupción prioritaria, slot filling para WhatsApp/llamadas/mapas, confirmación estricta para acciones sensibles, memoria local con consentimiento, RiskDetector y PrivacyGuard ya en uso. La cobertura de tests (~408 unit tests verdes, build limpio, APK ~57 MB) es alta para un MVP en esta etapa.

**Lo bueno de verdad**:
- Se siente como un agente real en los flujos de WhatsApp, llamadas, mapas y memoria.
- El "no envío automático" no es marketing: está enforced en código (`ACTION_DIAL` en vez de `ACTION_CALL`, `ACTION_SEND` con `setPackage`, confirmación estricta sólo con "confirmar/confirmo/aceptar").
- La ergonomía no-visual está pensada: Quick Settings tile, botón flotante de Accesibilidad, atajo de volumen, intent público `ACTION_START_LISTENING`.
- Personalidad de voz coherente, frases cortas, sin disculpas en cadena.

**Lo que todavía no se siente como agente real**:
- No hay reconocimiento visual real (DESCRIBIR depende de un backend mock — el botón promete más de lo que entrega).
- El primer "callar" puede tener latencia perceptible según el TTS engine del teléfono. Nadie controla eso en demo.
- Sin device físico validado en este sprint: el emulador no tiene mic real, así que la cadena completa "voz humana → comando → confirmación → acción" no está cerrada con evidencia repetible.
- Hay agujeros de borde que hoy son tolerables pero pueden romper una demo: TalkBack solapado, paquetes `es-AR` ausentes, ambientes con ruido, frases ambiguas sin "que" ni ":".

**Veredicto**: la app **está lista para una demo controlada de 60 a 180 segundos**, con un teléfono específico, en un ambiente silencioso, sin tocar bancos / contraseñas / OCR de documentos privados. **No** está lista para distribución abierta ni para una prueba sin supervisar con persona ciega real. La prueba con persona ciega es el siguiente milestone, no el actual.

---

## 2. Estado actual real del producto

### 2.1 Capas implementadas (con evidencia)

| Capa | Estado | Evidencia |
|------|--------|-----------|
| Escucha continua + auto-relisten | ✅ funcional | `VoiceCommandController`, lifecycle ON_RESUME en `HomeScreen` |
| TTS con dedup 5 s | ✅ funcional | `SpeechController.DEDUP_WINDOW_MILLIS = 5_000L` |
| "Callar" prioritario | ✅ funcional | `VoiceCommandDispatcher.isStopCommand`, corte en partial text |
| WhatsApp seguro sin auto-envío | ✅ funcional | `ACTION_SEND` + `setPackage`, confirmación estricta |
| Llamadas seguras | ✅ funcional | `PhoneActionExecutor` con `ACTION_DIAL`; sin `CALL_PHONE` |
| Contactos seguros locales | ✅ funcional | `SafeContactMemory`, `MemoryType.EMERGENCY_CONTACT` |
| Mapas / ubicación | ✅ funcional | `MapsActionExecutor`, `LocationProvider` (sin background) |
| Lectura de pantalla por accesibilidad | ✅ funcional con consent | `OjoClaroAccessibilityService`, `AccessibilityScreenReader` |
| OCR local | ✅ funcional | `TextRecognitionAnalyzer`, `StableTextDetector` |
| Memoria segura local | ✅ funcional | `LocalMemoryStore`, `MemoryPolicy`, `UserMemory` |
| PrivacyGuard | ✅ funcional | redacción, bloqueo de mensajes sensibles |
| RiskDetector | ✅ funcional | dinero, banco, contraseñas, códigos, urgencia |
| Quick Settings tile | ✅ funcional | `OjoClaroQuickTileService` |
| Botón flotante de accesibilidad | ✅ funcional | `flagRequestAccessibilityButton` |
| `ACTION_START_LISTENING` | ✅ funcional | dump de package mostrado en QA emulador |
| Tests unit | ✅ ~408 verdes | reportes Phase 1/2A/2B/3 |
| APK demo | ✅ generado | `androidApp/build/outputs/apk/debug/androidApp-debug.apk` |

### 2.2 Capas declaradas pero todavía con riesgo

- **DESCRIBIR**: depende de backend mock. La promesa de "describir lo que tengo enfrente" no se cumple sin IA cloud. Riesgo de credibilidad.
- **Visual cloud (CLOUD_AI capability)**: declarada en arquitectura, no conectada. Bien que no esté — pero el UI no diferencia "no hay internet" de "esta feature no existe todavía".
- **BiometricPrompt**: documentado como pendiente. Acciones tipo banco bloqueadas explícitamente — eso es correcto, pero conviene decirlo en voz al usuario.
- **Recordatorios / alarmas**: en spec, no implementadas. No prometer en demo.
- **Música / Spotify**: en spec, no implementado todavía. No prometer en demo.
- **Hotword / wake word permanente**: explícitamente fuera por seguridad. Bien.
- **iOS**: fuera de alcance por ahora. Bien.

### 2.3 Validación física

Pendiente. El device `R5CW22SMWDM` no estuvo conectado en los últimos sprints. El emulador no tiene mic real — la cadena completa con voz humana real no se ejecutó con repetibilidad. Esto no impide la demo, pero **es el riesgo más grande**.

---

## 3. Qué se siente como agente real

Estas son las partes que un usuario describiría como "uy, en serio me entendió y se portó como un asistente":

1. **El "callar" inmediato**. La mayoría de los asistentes comerciales requieren tocar la pantalla. Que Ojo Claro corte la voz al primer parcial "callar" se siente premium.
2. **El slot filling conversacional**. "Mandale a un contacto" → "¿Qué mensaje querés mandarle?" sin volver al inicio. Eso es agente, no IVR.
3. **La confirmación honesta**. "Voy a preparar un mensaje para un contacto que dice: estoy llegando. No lo envío automáticamente. Confirmá para continuar." Ese tono es lo que lo separa de un asistente que se tira a la pileta.
4. **"Sí" / "dale" no confirma**. Esto es feature, no bug. Una persona que dice "dale, dale" mientras camina no quiere disparar nada — y la app respeta esa intuición.
5. **Memoria opt-in con resúmenes seguros**. "Qué recordás de mí" lista lo que hay sin exponer datos crudos. Se siente cuidado.
6. **RiskDetector advirtiendo dinero / códigos**. Un agente que dice "este texto puede ser sensible" antes de responder transmite criterio, no obediencia.
7. **Aviso responsable de Maps**. "No reemplazo a un bastón ni a una persona acompañante" — eso es producto adulto.

---

## 4. Qué todavía se siente torpe

1. **DESCRIBIR no describe**. Depende de mock. El botón está, el nombre promete, la entrega no llega. Esto es lo más demo-killer.
2. **Latencia del primer "callar"** en TTS lentos (Pixel/Samsung viejos). 200–400 ms de cola del engine. La app hace todo bien — el engine no.
3. **Frases ambiguas sin conector**. "mandale a un contacto estoy llegando" se interpreta como "falta mensaje". Hoy es un guardrail útil pero suena rígido.
4. **TalkBack + Ojo Claro hablando al mismo tiempo**. En estados nuevos (`appState`), TalkBack puede leer encima del TTS. No bloqueante, pero molesto.
5. **Cierre de pendings al matar la app**. `pendingConsent` vive en memoria del ViewModel. Si Android mata el proceso, el contexto se pierde. Hoy es aceptable; en producción, no.
6. **Estados visuales para `WAITING_*`**. La UI no diferencia "esperando contacto" vs "esperando confirmación" con texto/color claros. Para una persona vidente que mira por encima del hombro queda raro.
7. **No hay evidencia física repetible**. Sin video o demo grabada en device físico, todo el discurso descansa en logs de emulador.
8. **El nombre del botón "DESCRIBIR"** en home arruina la coherencia: la app es voice-first, el botón es visual y promete una capacidad que hoy no existe.

---

## 5. Riesgos de demo

| Riesgo | Probabilidad | Impacto | Mitigación |
|--------|--------------|---------|------------|
| Apretar "Describir" en vivo | Media | Alta — rompe la narrativa | No mostrar el botón. Tapar con cinta o esconder en demo. |
| Ruido ambiente arruina reconocimiento | Alta en lugares públicos | Alta | Demo en sala silenciosa o con micrófono cercano |
| Paquete `es-AR` ausente en device | Media | Media | Pre-flight: `Settings → Sistema → Idiomas → Voz → instalar es-AR` |
| TTS lento corta tarde | Media | Media | Probar el guion 3 veces en el device exacto antes |
| WhatsApp no instalado | Baja en device de demo | Alta si pasa | Verificar antes; si falla, frase preparada existe |
| TalkBack solapado | Media | Baja a media | Decidir antes: con TalkBack o sin TalkBack |
| Persona del público pregunta "¿escucha siempre?" | Alta | Media | Tener respuesta clara: "sólo cuando la app está abierta y visible. No hay hotword en background." |
| Persona pregunta "¿sube audio a la nube?" | Alta | Media | "No. El reconocimiento usa el motor de Android. Nada se sube." |
| Pedir lectura de pantalla en una app sensible | Media si improvisa | Alta | Demo lectura sólo en una app neutral (notas, navegador con texto público) |
| Demo larga (>5 min) saca el tema "DESCRIBIR" | Alta | Alta | Acotar a 3 min. No prometer visión de objetos. |

**Demo-killer #1**: improvisar fuera del guion. La app es robusta para los flujos diseñados; cualquier cosa que se salga del catálogo de intenciones cae en "no entendí" y rompe el ritmo.

---

## 6. Flujos recomendados para mostrar

Orden estricto, de menor a mayor riesgo:

1. **Saludo + ayuda**. Abrir la app, esperar saludo, decir "qué puedo decir". Demuestra: voice-first sin tocar nada.
2. **Callar prioritario**. Mientras lee la ayuda, decir "callar". Demuestra: control humano siempre disponible.
3. **WhatsApp seguro con slot filling**. "Mandale a un contacto" → la app pregunta el mensaje → "estoy llegando" → confirmación → "cancelar". Demuestra: agente conversacional + no envío automático.
4. **Confirmación estricta vs "sí"**. Generar un pending y decir "sí". Mostrar que no confirma. Decir "confirmar". Mostrar que sí. Demuestra: criterio de seguridad no negociable.
5. **Memoria con consent**. "Recordá que prefiero respuestas cortas" → consent → "confirmar" → "qué recordás de mí". Demuestra: memoria local opt-in.
6. **Borrar memoria**. "Borrá tu memoria" → consent → "confirmar". Demuestra: reversibilidad.
7. **(Opcional, si el ambiente es bueno)** Mapas: "abrí mapas". Demuestra integración con apps reales.
8. **(Opcional)** Llamada segura: "llamá a un contacto" (con contacto guardado) → confirmar → mostrar que se abre el dialer pero no llama.

Tiempo total recomendado: 2 a 3 minutos. Más que eso aumenta la chance de un fallo.

---

## 7. Flujos que NO conviene mostrar todavía

- **DESCRIBIR**: el botón visual prometiendo descripción de objetos. Mientras no haya CLOUD_AI conectada con consent y costos resueltos, no abrirlo.
- **OCR sobre documento privado real**: mostrar un DNI, tarjeta, recibo de sueldo. Riesgo legal y de credibilidad.
- **Lectura de pantalla en apps sensibles**: home banking, mail, mensajes de terceros. Aunque PrivacyGuard bloquee, la sensación es invasiva.
- **Hotword / "Hey Ojo Claro"**: no existe y no se debe sugerir.
- **Emergencias en público**: simular SOS o llamada al 911 en público es de mal gusto y puede activar atención no deseada.
- **Recordatorios/alarmas**: en spec, no implementados. Si los pedís en demo, la app va a fallar.
- **Música / Spotify**: idem.
- **iOS**: idem.
- **Voz en ambiente con tráfico, café concurrido, micro ambient**: el reconocimiento va a fallar. No demostrar afuera.
- **Triple power button / SOS**: no es API pública. No prometer.

---

## 8. Script demo 60 segundos

Pensado para mostrar a un decisor con poco tiempo. Tono: directo, honesto.

> **(Operador)**: "Esta es Ojo Claro. Es un asistente de voz para personas no videntes. Funciona local. No hay hotword, no hay envío automático. Mirá."
>
> *(Abre la app. Espera el saludo: "Ojo Claro listo. Decime qué necesitás.")*
>
> **(Operador)**: "Qué puedo decir."
>
> *(App lista comandos)*
>
> **(Operador, mientras la app habla)**: "Callar."
>
> *(Corte inmediato)*
>
> **(Operador)**: "Mandale a un contacto."
>
> **(App)**: "¿Qué mensaje querés mandarle?"
>
> **(Operador)**: "Estoy llegando."
>
> **(App)**: "Voy a preparar un mensaje para un contacto que dice: estoy llegando. No lo envío automáticamente. Confirmá para continuar."
>
> **(Operador)**: "Sí."
>
> *(App no confirma — silencio o "no hay…")*
>
> **(Operador)**: "Confirmar."
>
> *(WhatsApp abre con el mensaje precargado, no enviado)*
>
> **(Operador, cerrando)**: "El usuario manda manualmente. Ojo Claro nunca envía solo. Eso es todo."

Lo que demuestra: voice-first real, "callar" honesto, slot filling, "sí" no confirma, sin auto-envío.

---

## 9. Script demo 3 minutos

Para fundación, municipio, posible inversor o tester técnico.

> **0:00 — Apertura**
>
> "Ojo Claro AI es un asistente de voz para personas no videntes. Es Android, en español rioplatense, local-first. La idea es que una persona ciega pueda escribir un WhatsApp, llamar, llegar a un lugar y leer un cartel sin tener que abrir seis apps."
>
> **0:20 — Activación sin ver**
>
> "No depende de un ícono en la pantalla. Hay tres formas de abrirla sin ver: el atajo de volumen, el botón flotante de accesibilidad, y el tile de ajustes rápidos. Las tres son APIs oficiales de Android."
>
> *(Si es posible: mostrar el tile de Quick Settings activando la app)*
>
> **0:40 — Ayuda y "callar"**
>
> *(Abre app, espera saludo)*
>
> "Qué puedo decir."
>
> *(app lee opciones)*
>
> "Callar."
>
> *(corte)*
>
> "El control humano nunca lo perdés. Eso fue al primer 'callar' parcial — ni siquiera esperó a que termine la palabra."
>
> **1:00 — WhatsApp con slot filling**
>
> "Mandale a un contacto."
>
> *(app: "¿Qué mensaje querés mandarle?")*
>
> "Estoy llegando."
>
> *(app prepara el mensaje y pide confirmar)*
>
> "Esto es lo más importante: nunca envía sola. Confirma con 'confirmar', no con 'sí'. Si decís 'sí' por costumbre, no pasa nada."
>
> "Sí."
>
> *(no confirma)*
>
> "Confirmar."
>
> *(abre WhatsApp con el mensaje precargado)*
>
> **1:40 — Memoria y privacidad**
>
> *(volver a la app)*
>
> "Recordá que prefiero respuestas cortas."
>
> *(consent — "confirmar")*
>
> "Qué recordás de mí."
>
> *(app responde con resumen)*
>
> "La memoria es local, opt-in y reversible. 'Borrá tu memoria' la limpia."
>
> **2:10 — Lo que NO hace**
>
> "Lo que la app no hace, y no va a hacer:
> — No tiene hotword en background. Sólo escucha cuando está visible.
> — No graba audio.
> — No sube nada al cloud salvo el comando que el usuario decidió mandar.
> — No lee contraseñas, códigos o tarjetas. Está bloqueado por código.
> — No reemplaza a un bastón ni a una persona acompañante."
>
> **2:40 — Cierre**
>
> "Hoy entiende WhatsApp, llamadas seguras, mapas, ubicación, lectura de pantalla con consentimiento, OCR local y memoria. Lo siguiente es probarlo con personas no videntes reales y sumar IA visual con consentimiento explícito. Y para eso buscamos aliados."

---

## 10. Frases comerciales honestas

Catálogo para web, redes, pitch — todas pasaron por el filtro "esto se cumple en código".

- "Un asistente de voz para personas no videntes. Local. Honesto. Sin hotword."
- "Te ayuda a mandar un WhatsApp, llamar y llegar a un lugar — sin abrir seis apps."
- "Nunca envía mensajes solo. Nunca llama solo. Confirmás vos, siempre."
- "No graba audio. No sube imágenes. No lee contraseñas."
- "Funciona aunque se caiga internet."
- "Tu memoria es local. La borrás cuando querés."
- "No reemplaza a un bastón ni a una persona acompañante. Te asiste."

Frases prohibidas (no decirlas todavía):
- "Describe lo que tenés enfrente." — la feature no existe sin CLOUD_AI.
- "Te guía por la calle." — promesa de seguridad que no se puede cumplir.
- "Reconoce billetes / objetos / personas." — no implementado.
- "Funciona hands-free siempre." — falso: necesita la app abierta.
- "Es seguro al 100%." — nadie lo es.

---

## 11. Copy para landing web

### Hero

**H1**: Ojo Claro AI

**H2**: Un asistente de voz para personas no videntes. En tu Android. En español. Local.

**Subheadline**: Mandá un WhatsApp, llamá, llegá a un lugar y escuchá lo que dice una pantalla — sin abrir seis apps y sin perder el control.

**CTA primario**: Probá el APK demo
**CTA secundario**: Soy fundación / municipio / institución

### Sección "Cómo funciona"

1. **Hablás.** Decís lo que necesitás en español rioplatense. La app escucha sólo cuando está abierta. No hay hotword.
2. **Confirma con voz.** Antes de mandar un mensaje o llamar, te lee lo que va a hacer y espera tu "confirmar".
3. **Vos cerrás la acción.** Ojo Claro prepara el mensaje, no lo envía. Abre el dialer, no llama. Vos decidís el último paso.

### Sección "Lo que no hace"

- No graba audio.
- No sube imágenes a la nube.
- No lee contraseñas, códigos ni tarjetas.
- No tiene hotword en background.
- No reemplaza a un bastón ni a una persona acompañante.

### Sección "Para quién"

- **Personas con baja visión o ciegas** que quieren un punto de entrada de voz único.
- **Familiares y cuidadores** que quieren saber que la app no manda nada sin confirmación.
- **Fundaciones, municipios, centros de rehabilitación visual y obras sociales** que quieren ofrecer una herramienta auditable y local.

### Sección "Estado actual"

> Ojo Claro está en MVP funcional para Android. Hoy entiende WhatsApp, llamadas seguras, mapas, ubicación, lectura de pantalla con consentimiento, OCR local de texto físico, memoria personal local y advertencias de riesgo (dinero, banco, contraseñas, códigos).
>
> Lo que viene: pruebas con personas no videntes reales, IA visual con consentimiento explícito y soporte multi-dispositivo.

### Sección "Privacidad"

> Local-first por diseño. El reconocimiento de voz usa el motor del propio Android. Las acciones sensibles requieren confirmación verbal explícita ("confirmar", no "sí"). La memoria personal vive en tu teléfono y la borrás cuando quieras. No usamos `ACTION_CALL`, no pedimos `READ_CONTACTS`, no usamos `ACCESS_BACKGROUND_LOCATION`.

### Sección "Contacto"

Formulario corto: nombre, organización (opcional), rol, mensaje. CTA: "Quiero probar / colaborar / sumar a mi institución".

---

## 12. Pitch para institución / fundación / municipio

### 12.1 Versión corta (60 s)

> "Ojo Claro AI es un asistente de voz Android para personas no videntes. Funciona en español rioplatense, es local, y nunca envía mensajes ni hace llamadas sin confirmación verbal explícita.
>
> Hoy es un MVP funcional. Lo que necesitamos de ustedes son tres cosas:
> 1. acceso a 3 a 5 personas no videntes que quieran probarlo en sus teléfonos durante una semana,
> 2. una sala silenciosa para una sesión guiada de feedback,
> 3. una conversación honesta sobre qué falta para que les sirva en el día a día.
>
> No estamos vendiendo nada todavía. Estamos validando."

### 12.2 Versión expandida

**Problema**

> Una persona ciega en Argentina hoy depende de un mosaico: TalkBack, Maps, WhatsApp dictado, alarmas, OCR aparte, app de descripción aparte, y memoria propia. Cada cambio de app es fricción. Y la mayoría de los asistentes globales (Alexa, Siri, Google) no están afinados al español rioplatense ni a contactos / lugares locales.

**Qué es Ojo Claro**

> Un punto de entrada de voz unificado para los flujos diarios: escribir WhatsApp, llamar, llegar a un lugar, leer un papel, leer una pantalla, recordar tareas. Sin abrir seis apps.

**Qué lo diferencia**

> 1. **Local-first**: el reconocimiento de voz usa el motor de Android. La memoria vive en el teléfono. No subimos audio.
> 2. **Diseño "no envía sola"**: WhatsApp con `ACTION_SEND`, llamadas con `ACTION_DIAL` (no `ACTION_CALL`). El último paso siempre lo da el usuario.
> 3. **Tono adulto**: no promete reemplazar al bastón. No promete seguridad en calle. No promete describir lo que no puede describir.
> 4. **Auditable**: todas las reglas de privacidad y riesgo son código testeado. Cualquier auditor las puede leer.

**Qué pedimos**

> 1. **3 a 5 testers no videntes** durante 5 a 7 días, con un Android con TalkBack ya configurado.
> 2. **Una sesión presencial** de 60 a 90 minutos para observar el primer uso y recoger feedback.
> 3. **Un canal de comunicación** (mail, WhatsApp del programa, lo que sea) para reportar fallos.

**Qué les damos**

> 1. APK demo firmado para distribuir interno.
> 2. Documento de privacidad y permisos.
> 3. Reporte de hallazgos al final del piloto.
> 4. Posibilidad de un piloto institucional con branding y soporte si los hallazgos lo justifican.

**Qué no pedimos hoy**

> Plata. Datos personales. Compromisos comerciales. Esto es validación.

---

## 13. Checklist para test con usuario no vidente

Pre-condiciones (antes de la sesión):

- [ ] Device físico Android con TalkBack ya configurado por el usuario.
- [ ] APK instalada y permisos pre-concedidos: `RECORD_AUDIO`, `ACCESS_FINE_LOCATION`, `CAMERA` (sólo si se va a probar OCR).
- [ ] Paquete `es-AR` de voz instalado (`Ajustes → Sistema → Idiomas → Voz → es-AR`).
- [ ] WhatsApp instalado y logueado (si se prueba ese flujo).
- [ ] Tile de Quick Settings configurado.
- [ ] Atajo de Accesibilidad de volumen asignado (opcional).
- [ ] Botón flotante de Accesibilidad asignado (opcional, según preferencia del usuario).
- [ ] Sala razonablemente silenciosa.
- [ ] Cuidador o asistente presente, sentado al costado, no interviniendo salvo emergencia.
- [ ] Plan de contingencia: si la app falla, cómo desinstalar / recuperar el teléfono.
- [ ] Consent firmado / hablado para la sesión (que el usuario sepa que se observa, qué se anota, qué pasa con esa info).

Durante la sesión:

- [ ] **No tocar la pantalla por el usuario.** Si se traba, observar y anotar qué pasó.
- [ ] **No corregir vocabulario.** Si dice "Wasap" en vez de "WhatsApp", anotar — eso es señal real.
- [ ] **Anotar latencias percibidas.** Especialmente del primer "callar" y del primer comando.
- [ ] **Anotar frases que la app no entendió.** Cada "no entendí" es oro para mejorar el parser.
- [ ] **Anotar dónde la persona pidió ayuda externa.** Eso marca el límite real del agente.
- [ ] **Probar el guion de 3 minutos** primero, sin abrir flujos no implementados.
- [ ] **Después** dejar 10 minutos de uso libre. Anotar todo.
- [ ] **Pregunta clave al final**: "¿Lo usarías mañana? ¿Para qué? ¿Qué cambiarías primero?"

Post-sesión:

- [ ] Borrar memoria de la app antes de devolver el teléfono.
- [ ] Desinstalar si el usuario quiere.
- [ ] Documentar hallazgos en `docs/USER_TEST_<fecha>_<inicial>.md` sin nombrar al usuario.
- [ ] Priorizar 3 fixes para la próxima iteración.

---

## 14. Roadmap comercial 30 días

Asume hoy = 2026-05-06. Hitos absolutos.

### Semana 1 (06–12 mayo): consolidar la demo

- **Día 1-2**: validar el guion de 60 s y 3 min en device físico real (R5CW22SMWDM o equivalente). Grabar video de respaldo.
- **Día 3**: pulir landing web con el copy de §11. Subir APK demo accesible.
- **Día 4-5**: preparar "demo kit" — un teléfono con todo configurado, batería cargada, paquete es-AR instalado, WhatsApp y Maps instalados.
- **Día 6-7**: identificar 3 organizaciones target (1 fundación de ciegos, 1 área de discapacidad de un municipio, 1 centro de rehabilitación visual).

### Semana 2 (13–19 mayo): primeros contactos

- Contactar las 3 organizaciones con el pitch corto de §12.1.
- Objetivo: agendar 1 reunión presencial.
- En paralelo: hacer 2 sesiones internas de "persona vendada" (con persona vidente con los ojos cerrados) para detectar fricciones obvias antes del test real.

### Semana 3 (20–26 mayo): primer piloto micro

- Ejecutar 1 sesión de test con 1 a 2 personas no videntes, idealmente vía la organización contactada.
- Documentar hallazgos.
- Ajustar la app sólo en lo crítico (no todo lo que aparezca).
- No vender. No firmar.

### Semana 4 (27 mayo – 02 jun): iteración + decisión

- Iterar la app con los 3 fixes prioritarios del piloto.
- Decidir el siguiente camino comercial:
  - **Opción A — B2C lento**: Play Store, foco comunidad, freemium futuro.
  - **Opción B — B2B institucional**: piloto pago con la organización que mejor calzó.
  - **Opción C — Híbrido**: gratis y abierto, con propuesta institucional para sumar features.
- Escribir un memo de 1 página con la decisión.

**Riesgo del roadmap**: 30 días son cortos. Si en la semana 1 el device físico falla, la semana 2 se cae. Tener Plan B (otro device) listo desde el día 1.

---

## 15. Próximas tareas recomendadas para Codex

Orden por valor / costo. Codex no debería tocar lo que ya está bien.

### 15.1 Cosas que sí debería hacer

1. **QA físico repetible**. Script de `adb logcat` + comandos típicos, ejecutado en el R5CW22SMWDM con voz humana. Generar video corto. Esto desbloquea todo.
2. **Esconder o renombrar "DESCRIBIR"** mientras no haya CLOUD_AI conectada, o convertirlo en "Pedir ayuda" con frase honesta. Hoy promete y no entrega.
3. **Estados visuales claros para `WAITING_*`**: que la pantalla diga "Esperando contacto", "Esperando mensaje", "Esperando confirmación" — útil para vidente acompañante en demo.
4. **Persistir `pendingConsent`** en SharedPreferences con TTL. Hoy se pierde si Android mata el proceso.
5. **Pre-flight check de `es-AR` instalado**. Si falta el paquete, decirle al usuario en voz: "Falta el paquete de voz en español argentino. Lo instalás desde Ajustes."
6. **Test físico end-to-end** del flujo Quick Settings tile → saludo → comando → confirmación → acción. Logcat limpio y sin `W Binder` desde paquetes `ojoclaro`.
7. **Documentación pública** (README orientado a usuario, no a dev): qué es, qué no es, cómo se instala el APK, cómo se da feedback.
8. **Política de privacidad** en docs/ y en la app, leíble por TTS al primer arranque.

### 15.2 Cosas que NO debería hacer todavía

- **Conectar IA cloud real**. Sin política de costos, consent y proveedor firmado, abrir la cola es peor que dejarla cerrada.
- **Hotword en background**. Decisión de seguridad ya tomada.
- **`ACTION_CALL` o `READ_CONTACTS`**. Idem.
- **BiometricPrompt** sin un flujo concreto que lo necesite.
- **iOS**.
- **Recordatorios / alarmas / Spotify**: en spec, pero no son prioridad antes de validar con usuarios reales.
- **Bancos / pagos / SOS automatizado**: explícitamente fuera por ahora.

### 15.3 Patrón de trabajo sugerido

- Cada cambio en una rama, con un report markdown corto en `docs/` describiendo qué se tocó, qué tests se sumaron, qué quedó afuera.
- Nunca tocar `PrivacyGuard`, `RiskDetector`, `MemoryPolicy`, `CommandRouter` confirmaciones estrictas, `ConsentManager` sin sumar tests que cubran el cambio.
- Nunca sacar tests existentes para hacer pasar un cambio.

---

## 16. Veredicto: demo técnica presentable sí/no

**Sí, presentable. Bajo estas condiciones**:

1. ✅ Demo en device físico **probado dos veces antes** con el guion de 60 s o 3 min de §8/§9.
2. ✅ Sala silenciosa, sin público que improvise comandos.
3. ✅ WhatsApp y Maps instalados en el device, paquete `es-AR` instalado, permisos pre-concedidos.
4. ✅ El operador **no improvisa**. Sigue el script.
5. ✅ El botón "DESCRIBIR" no se toca. Si se toca por accidente, frase preparada: "Esa parte conecta IA visual y todavía no la liberamos."
6. ✅ Tiempo total ≤ 3 minutos en demo a un decisor; ≤ 10 minutos en demo a un técnico.
7. ✅ El operador puede responder con honestidad las 3 preguntas obligatorias del público:
   - "¿Escucha siempre?" → No. Sólo cuando la app está visible.
   - "¿Sube algo a la nube?" → No. El reconocimiento usa el motor de Android.
   - "¿Manda mensajes solo?" → No. Nunca.
8. ❌ **No** prometer descripción visual.
9. ❌ **No** prometer guía en calle.
10. ❌ **No** prometer fechas para features que no están implementadas (recordatorios, música, IA cloud).

**No presentable** si: no se probó en device físico antes, si el ambiente es ruidoso, si el paquete `es-AR` no está, si el operador planea improvisar, o si el público que mira espera ver descripción visual / hotword / SOS automático.

---

## Cierre

Ojo Claro tiene una base honesta y poco común en este tipo de productos: **la promesa coincide con el código**. Eso es escaso. Lo que falta para volverse producto no es más código — es validación con personas no videntes reales, foco brutal en los 3 a 5 flujos que ya funcionan, y resistir la tentación de prometer visión cloud antes de tiempo.

El próximo movimiento no es agregar features. Es probarla con una persona ciega, anotar qué se rompe, y arreglar eso.
