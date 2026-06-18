# Estela — Niveles de Autonomía Segura

> Modelo para decidir **cuánto hace Estela sola** y dónde frena. Refleja el comportamiento **verificado** hoy y guía lo que viene.
> Regla madre: **ante la duda, Estela baja al nivel más seguro y frena.**

## Los niveles

| Nivel | Qué hace | Quién actúa |
|---|---|---|
| **0 — Describir** | Lee/describe. No toca nada. | Nadie actúa (solo informa). |
| **1 — Explicar qué se puede tocar** | Enumera elementos accionables. | El usuario decide y toca. |
| **2 — Guiar paso a paso** | Dice cómo hacerlo (el gesto, el paso). | El usuario ejecuta. |
| **3 — Preparar una acción** | Compone/arma la acción y la **lee**. No la ejecuta. | Espera al usuario. |
| **4 — Ejecutar con confirmación fuerte** | Ejecuta **solo** tras confirmación explícita y fuerte. | Estela, con permiso claro. |
| **5 — Bloquear / pedir takeover** | No ejecuta. Frena o pide que tome una persona. | Nadie / humano. |

**Reglas duras (no negociables):**
- **Pagos y tarjetas: SIEMPRE Nivel 5 (bloqueado).** Ni con confirmación.
- **"sí" NUNCA confirma** una acción sensible (Nivel 4+). Hace falta una frase fuerte y explícita.
- **Acciones irreversibles** (enviar, llamar) → **Nivel 4** con confirmación fuerte.
- **Acciones reversibles** (abrir app, abrir chat, describir) → se ejecutan directo, **siempre cancelables**.
- **Si hay duda** (intención ambigua, pantalla sensible, baja confianza) → **frena** y baja de nivel.

## Mapa de acciones (estado actual verificado)

| Acción | Nivel | Regla |
|---|---|---|
| **Leer pantalla** | 0 | Siempre. Read-only, **local** (no sube a internet). |
| **Leer texto (OCR)** | 0 | Siempre. **Local**, offline. |
| **Describir escena** | 0 | Bajo demanda. Una foto, no se guarda. Requiere backend. |
| **Qué puedo tocar** | 1 | Enumera accionables. No toca. |
| **Guía de gesto de audio** | 2 | Explica cómo mandar el audio. **No graba ni envía** por vos. |
| **Guía de pago (explicar)** | 2 | Solo explica. **Nunca** ejecuta un pago. |
| **Abrir Instagram / abrir chat** | 3→ejecuta | Navegación **reversible**: se ejecuta directo, siempre cancelable. Sin confirmación fuerte. |
| **Preparar mensaje** | 3 | Compone y **lee el borrador**. **No envía.** |
| **Enviar mensaje** | 4 | **Solo con confirmación fuerte explícita.** "sí" no alcanza. Irreversible. |
| **Llamada** | 4 (+aviso) | Confirmación fuerte + aviso. (Reversible: se corta, pero sensible.) |
| **Audio (mandar)** | 2 / bloqueado | No se graba/envía automático. A lo sumo **guía el gesto** (Nivel 2). |
| **Ubicación (compartir)** | 4–5 | Sensible. Confirmación fuerte, o bloqueo según el destino. Nunca automático. |
| **Pagos** | **5** | **BLOQUEADO siempre.** Ni con confirmación. |
| **Tarjetas** | **5** | **BLOQUEADO siempre.** No ingresa datos de tarjeta. |

## Por qué este modelo
- Hace **predecible** qué va a hacer Estela: nada sensible pasa "de una".
- Convierte la **seguridad en un argumento de venta** (los agentes de frontera son menos conservadores).
- Da un lenguaje común para discutir features nuevas: *"¿esto es Nivel 3 o Nivel 4? ¿es reversible?"*

## Cómo se aplica a features futuras (checklist)
Antes de sumar cualquier acción nueva, responder:
1. ¿Es **reversible**? Si no → Nivel 4 con confirmación fuerte como mínimo.
2. ¿Toca **plata/tarjetas/datos sensibles**? Si sí → Nivel 5 (bloqueo).
3. ¿Qué pasa si Estela **se equivoca**? Si el daño es serio → subir el nivel o bloquear.
4. ¿Puede el usuario **cancelar** en cualquier momento? Debe poder.
5. ¿"sí" podría dispararla por accidente? **No debe.**

> Este documento describe el **diseño de seguridad**, no cambia código. La implementación actual ya cumple las reglas duras (pagos/tarjetas bloqueados, "sí" no envía, confirmación fuerte para enviar).
