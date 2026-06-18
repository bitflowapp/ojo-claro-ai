# Estela — Frontier Assistant Gap Analysis

> Documento interno y **duro**. Sin maquillaje. Solo capacidades **verificadas** de Estela.
> Estado de Estela: **beta funcional** (probada por inyección + ensayo; aún sin usuarios ciegos reales).

## Las 3 etiquetas (no confundirlas)
- **Beta funcional (hoy):** anda de punta a punta en 1 dispositivo, sin store, backend de desarrollo (ngrok), 0 usuarios reales.
- **Producto piloto:** onboarding accesible, multi-dispositivo, backend estable, probado por 3–5 personas reales.
- **Producto final:** en Play Store, política de privacidad, backend producción, fallback humano, navegación segura, memoria, soporte.

## Tesis estratégica (la verdad incómoda)
Estela **no le gana a ChatGPT/Gemini en voz natural ni a Seeing AI/Be My AI en visión**. No tiene sentido competir de frente ahí: esos son cloud, generales y enormes. El **único terreno defendible** de Estela es:

> **Asistente que OPERA tu teléfono (lee la pantalla de TUS apps y ejecuta acciones acotadas) de forma local, privada y con seguridad dura, en español — distribuida vía instituciones.**

Los frontera **no manejan tu teléfono** (ChatGPT/Gemini ven la cámara pero no abren tu Instagram ni leen tu app del banco). Los de accesibilidad (Seeing AI/Be My Eyes) **describen, no actúan**. Ahí está la cuña. Todo el roadmap debe **proteger esa cuña** y **subcontratar** voz/visión de frontera (vía API) en vez de pelearlas.

## Matriz resumen

| Eje | Estela hoy | Prioridad | Riesgo romper app |
|---|---|---|---|
| Voz natural | 🔴 TTS utilitario + STT que garbla AR | HIGH | **HIGH** |
| Visión/cámara | 🟡 escena 1-shot vía backend dev; OCR local OK | HIGH (infra) | MEDIUM |
| Lectura de pantalla | 🟢 fuerte, local, on-device | LOW (proteger) | **HIGH** |
| Navegación de apps | 🟡 maneja IG real con guardas; angosto/frágil | MEDIUM | **HIGH** |
| Seguridad | 🟢 bloqueo financiero + confirmación fuerte | LOW (proteger) | **HIGH** |
| Fallback humano | 🔴 inexistente | HIGH | LOW |
| Onboarding | 🔴 manual, no accesible solo | HIGH | LOW-MED |
| Distribución | 🔴 APK sideload, 1 device | BLOCKER (producto) | LOW |
| Backend | 🔴 uvicorn+ngrok dev, single point of failure | HIGH (BLOCKER producto) | LOW |
| Memoria/contexto | 🔴 turnos single-shot, sin personalización | MEDIUM | MEDIUM |
| Pruebas reales | 🔴 0 usuarios ciegos reales (el domingo es el 1º) | HIGH | LOW |
| Confianza/comercial | 🔴 sin marca/track record | HIGH | LOW |

> Lectura clave: lo **verde** (lectura de pantalla, seguridad) y la navegación tienen **HIGH riesgo de romper** → NO tocar antes/durante el piloto. Lo **rojo** que más importa (backend, onboarding, fallback humano, distribución, pruebas) es casi todo **LOW riesgo de romper** porque es aditivo/infra, no toca el core.

---

## Detalle por eje

### 1. Voz natural
- **Estela hoy:** TTS de sistema (es-US, no hay es-AR en el equipo) + STT on-device con parser por tokens tolerante a garble (porque el STT confunde palabras en AR). Funciona, pero es robótico y por turnos, no conversacional.
- **Líderes:** GPT-4o Advanced Voice y Gemini Live = voz en tiempo real, interrumpible, prosodia natural, baja latencia.
- **Riesgo si no mejora:** se siente "viejo"/torpe; el usuario siente que "no lo entiende" y abandona.
- **Mejora sugerida:** opción de STT en la nube o pack es-AR; TTS más natural (ya hay `ESTELA_CLOUD_TTS_DESIGN`); barge-in. Subcontratar a frontera vía API, no reinventar.
- **Prioridad:** HIGH. **Romper app:** **HIGH** (es el pipeline sobre el que corre toda la demo — tocar solo post-piloto, con red).

### 2. Visión / cámara
- **Estela hoy:** OCR **local** (ML Kit, ~1s, offline) = confiable. Escena = **una** foto → backend gpt-4.1-mini (~5–6s) → descripción; depende de internet + ngrok.
- **Líderes:** Be My AI (descripción rica GPT-4 + repreguntas), Seeing AI (multi-canal, mucho offline, instantáneo), Project Astra (visión continua + memoria).
- **Riesgo si no mejora:** escena lenta/caída deja mal en demo; calidad inferior a Seeing AI.
- **Mejora sugerida:** backend producción + reintentos/timeouts; repreguntas sobre la misma escena; mantener OCR local como el camino confiable. No prometer visión continua.
- **Prioridad:** HIGH (infra) / MEDIUM (calidad). **Romper app:** MEDIUM (casi todo es backend; el path de la app es estable).

### 3. Lectura de pantalla
- **Estela hoy:** 🟢 lee la pantalla de **cualquier app**, local, on-device; enumera qué se puede tocar; limita banca/contraseñas a propósito.
- **Líderes:** acá Estela **gana**: ChatGPT/Gemini NO leen la pantalla de tus otras apps; eso es territorio de TalkBack/VoiceOver. Estela suma una capa de comprensión por voz.
- **Riesgo si no mejora:** bajo — es una fortaleza.
- **Mejora sugerida:** resúmenes más ricos por app, con cuidado (es sensible y load-bearing).
- **Prioridad:** LOW (proteger, no romper). **Romper app:** **HIGH** (código central + sensible a seguridad).

### 4. Navegación de apps
- **Estela hoy:** 🟡 maneja **Instagram real** vía AccessibilityService (abrir, abrir chat por nombre, preparar/enviar con confirmación fuerte). WhatsApp en pausa. Es agentic mobile real, pero **angosto y frágil** (si IG cambia la UI, se rompe).
- **Líderes:** agentes Operator / computer-use = acción general (click/escribir/navegar), pero en browser/desktop, riesgosos y tempranos. Los asistentes de consumo **no** manejan tus apps.
- **Riesgo si no mejora:** se rompe con cada update de IG; cobertura mínima; no escala a más flujos sin mucho trabajo.
- **Mejora sugerida:** robustecer selectores; elegir 2–3 flujos de alto valor; aceptar que cada uno es frágil y cuesta.
- **Prioridad:** MEDIUM. **Romper app:** **HIGH** (es el código más frágil y más difícil de recuperar).

### 5. Seguridad
- **Estela hoy:** 🟢 bloqueo financiero local (pagos/tarjetas → SENSITIVE_BLOCK), confirmación fuerte para enviar ("sí" no envía), no graba/manda audios, mínima retención.
- **Líderes:** los agentes agentic son **menos** seguros (Operator se puede engañar); los de accesibilidad no toman acciones riesgosas. Estela es **más conservadora** = diferencial real.
- **Riesgo si no mejora:** bajo — es fortaleza y argumento de venta.
- **Mejora sugerida:** formalizar niveles de autonomía (ver `SAFE_AUTONOMY_LEVELS.md`) + auditoría; jamás relajar.
- **Prioridad:** LOW (proteger). **Romper app:** **HIGH** (la seguridad es sagrada; no refactorizar a la ligera).

### 6. Fallback humano
- **Estela hoy:** 🔴 **inexistente**. Si la IA falla, el usuario queda solo.
- **Líderes:** Be My Eyes (voluntarios humanos en vivo) = el ancla de confianza de la comunidad ciega.
- **Riesgo si no mejora:** cuando Estela se equivoca, no hay red; mata la confianza.
- **Mejora sugerida:** fallback a un humano (familiar/voluntario): "¿querés que llame a alguien que te ayude?". Aditivo, separado del core.
- **Prioridad:** HIGH (confianza/mundo real). **Romper app:** LOW (aditivo).

### 7. Onboarding
- **Estela hoy:** 🔴 guía en doc + activación manual de Accesibilidad; una persona ciega **no la configura sola**.
- **Líderes:** Seeing AI / Be My Eyes tienen onboarding accesible y pulido.
- **Riesgo si no mejora:** no se puede adoptar sin un vidente al lado → bloquea uso real.
- **Mejora sugerida:** primer arranque guiado por voz, walkthrough de permisos accesible.
- **Prioridad:** HIGH. **Romper app:** LOW-MEDIUM (UI aditiva; post-piloto).

### 8. Distribución
- **Estela hoy:** 🔴 APK sideload, 1 Moto, sin store.
- **Líderes:** en App Store/Play, millones de instalaciones.
- **Riesgo si no mejora:** no llega a usuarios; no escala.
- **Mejora sugerida:** Play Store (ver `PLAY_STORE_PREP.md`); gateado por revisión de Accesibilidad + política de privacidad + build firmado.
- **Prioridad:** BLOCKER (producto) / innecesario (piloto). **Romper app:** LOW (es release/store, no lógica — aunque el build hoy está trabado por disco).

### 9. Backend
- **Estela hoy:** 🔴 uvicorn local + túnel ngrok de dev = **single point of failure**, sin auth/escala/monitoreo.
- **Líderes:** infra cloud robusta.
- **Riesgo si no mejora:** la escena se cae en cualquier momento; no es shippeable.
- **Mejora sugerida:** backend producción (host gestionado, dominio estable, auth, rate-limit, monitoreo).
- **Prioridad:** HIGH (BLOCKER para producto). **Romper app:** LOW (la app solo necesita la URL; no cambia código).

### 10. Memoria / contexto
- **Estela hoy:** 🔴 turnos single-shot; memoria corta de 5 turnos solo en el path conversacional; **sin personalización persistente**.
- **Líderes:** memoria de ChatGPT, memoria continua de Astra.
- **Riesgo si no mejora:** se siente impersonal, repite, no aprende tus contactos/preferencias.
- **Mejora sugerida:** perfil de usuario **persistente, privado, on-device** (contactos, preferencias, tareas frecuentes), con cuidado de privacidad.
- **Prioridad:** MEDIUM. **Romper app:** MEDIUM (toca manejo de estado).

### 11. Pruebas reales
- **Estela hoy:** 🔴 mucho smoke por inyección ADB + algunas validaciones físicas del **desarrollador**, pero **0 usuarios ciegos reales**. Riesgo de sobreajuste a la voz/uso del dev.
- **Líderes:** millones de usuarios reales, años de datos de campo.
- **Riesgo si no mejora:** fallas reales desconocidas; falsa sensación de "listo".
- **Mejora sugerida:** el **piloto** (3–5 usuarios, 2 semanas) — ya planificado.
- **Prioridad:** HIGH. **Romper app:** LOW (es testing).

### 12. Confianza / comercialización
- **Estela hoy:** 🔴 sin marca ni track record; un ciego (con razón) desconfía de una herramienta nueva que maneja su teléfono.
- **Líderes:** Be My Eyes/Seeing AI = gratis, confiables, respaldados por orgs grandes.
- **Riesgo si no mejora:** dificilísimo ganar confianza desde cero.
- **Mejora sugerida:** apoyarse en el **relato de seguridad** + fallback humano + **alianzas institucionales** (B2B de `COMMERCIAL_STRATEGY`); transparencia total.
- **Prioridad:** HIGH. **Romper app:** LOW.

---

## Snapshot de competidores (corte ene-2026)

| Asistente | Fuerte en | No hace (vs Estela) |
|---|---|---|
| ChatGPT (GPT-4o+) voz/visión | voz real-time, visión, memoria, distribución | no lee/maneja tus apps; no garantiza seguridad de acción |
| Gemini Live / Astra | multimodal continuo, baja latencia, memoria, Android | igual: no opera tus apps con guardas |
| Be My AI | descripción de imagen rica + repreguntas | no lee pantalla, no actúa en apps |
| Be My Eyes | **voluntarios humanos** (fallback de oro) | no es IA-on-device, no actúa |
| Seeing AI | OCR/escena multi-canal, offline, onboarding | no actúa en apps |
| Operator / computer-use | acción agentic general | desktop/browser, menos seguro, no foco accesibilidad móvil |

## Conclusión dura
Estela es una **beta funcional con una cuña real pero angosta**. Sus fortalezas (lectura de pantalla local, seguridad, operar el teléfono) son **justo lo que los frontera no hacen**, pero son frágiles y de alto riesgo de romper. Sus debilidades (voz, visión, backend, onboarding, fallback humano, distribución, confianza) son grandes pero la mayoría se arreglan **sin tocar el core**. El plan correcto: **congelar y proteger la cuña para el piloto**, arreglar infra/onboarding/fallback (bajo riesgo) después, y **no pelear voz/visión de frente** — subcontratarlas.
