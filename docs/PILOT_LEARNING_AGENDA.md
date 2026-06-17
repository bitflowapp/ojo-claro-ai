# Estela — Agenda de Aprendizaje del Piloto

> Para qué sirve el piloto: **aprender**, no demostrar. Si todo "sale perfecto", probablemente no aprendimos nada.
> Usar junto a `USER_FEEDBACK_FORM.md` y `POST_DEMO_REPORT_TEMPLATE.md`.

## Hipótesis a validar (con cómo medirlas)

- **H1 — La lectura de pantalla aporta valor inmediato.**
  Medir: ¿el usuario dice "esto me sirve" al leer la pantalla? ¿lo pediría de nuevo? Es la función más madura (local, confiable).
- **H2 — El OCR local es más confiable que la escena para la demo.**
  Medir: tasa de éxito OCR vs escena; tiempos; cuántas veces la escena falla por backend/internet. Hipótesis: OCR gana en confiabilidad.
- **H3 — Los usuarios quieren comandos naturales, no exactos.**
  Medir: capturar las **frases textuales** que dicen; cuántas no entiende Estela; qué formas naturales faltan.
- **H4 — La seguridad importa tanto como la capacidad.**
  Medir: ¿el bloqueo de pagos y el "sí no envía" generan **confianza** o se sienten una traba? Preguntar explícitamente.
- **H5 — Familiares/instituciones pueden ser mejores compradores que los usuarios individuales.**
  Medir: ¿quién muestra disposición a pagar? ¿el usuario, un familiar, o una institución detrás?

## Qué comandos reales capturar
- **Textual**, tal cual los dijo (no parafrasear). Sirven para enseñarle formas nuevas sin tocar el core.
- En qué **app/pantalla** estaba al decirlos.
- Cuáles **funcionaron** y cuáles cayeron en "no entendí".

## Qué miedos medir
- Miedo a que **mande algo sin querer** (mensajes, plata).
- Miedo a que **escuche/grabe** todo el tiempo.
- Miedo a **depender** de algo que puede fallar en la calle.
- Miedo a **quedar expuesto** (que alguien vea sus datos).
- Desconfianza general hacia una **herramienta nueva** que maneja el teléfono.

## Qué situaciones reales observar
- ¿Dónde la usaría de verdad? (casa, trabajo, trámites, compras, redes).
- ¿Qué tarea cotidiana le cuesta hoy que Estela podría aliviar?
- ¿Usa lector de pantalla? ¿Estela compite o complementa?
- ¿Hay alguien que normalmente lo ayuda con esto? (pista de comprador B2C familiar).

## Errores TOLERABLES (no asustarse)
- Una frase mal entendida → reintentar.
- Escena lenta o caída → fallback honesto (OCR sigue).
- TTS robótico / voz es-US.
- Que pida confirmación "de más" en algo sensible (mejor pasarse de seguro).

## Errores BLOCKER (frenar y anotar como crítico)
- **Mandar un mensaje sin confirmación fuerte.**
- **Tocar/iniciar un pago o pedir datos de tarjeta.**
- **Cámara que queda pegada/encendida.**
- **Crash** o que Estela quede muda/trabada sin recuperarse.
- Cualquier acción **irreversible** disparada por un "sí" suelto.

## Preguntas para hacer (abiertas, sin inducir)
- "¿Qué fue lo más útil?" / "¿Qué no te sirvió?"
- "¿Hubo algún momento en que desconfiaste?"
- "¿Cómo le pedirías esto vos, con tus palabras?"
- "¿En qué momento real de tu día la usarías?"
- "¿Qué tendría que pasar para que la uses todos los días?"
- "¿Esto lo pagarías vos, o lo pagaría tu familia/una institución?"

## Cómo convertir feedback en backlog
1. **Clasificar** cada hallazgo: BLOCKER / HIGH / MEDIUM / LOW.
2. **Etiquetar riesgo de romper app**: LOW (frases, infra, docs) vs HIGH (routing, seguridad, navegación).
3. **Priorizar**: primero BLOCKERs de bajo riesgo de romper; los de alto riesgo, con red y tests.
4. **Convertir frases reales** en ampliaciones de listas de frases (aditivo, seguro).
5. **Mapear** cada pedido al `ESTELA_PRODUCT_MATURITY_ROADMAP.md` (¿Etapa 1, 2, 3 o 4?).
6. **No** meter features nuevas en el core antes de cerrar el piloto.

## Señal de éxito del piloto
No es "0 errores". Es: **entendimos a quién le sirve, para qué, qué le da miedo, cómo habla, y quién pagaría** — con una lista priorizada y honesta de qué tocar después.
