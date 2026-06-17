# Estela — Roadmap de Madurez de Producto

> Por etapas, de **beta funcional** a **producto final**. Cada etapa tiene una **regla de oro** para no romper lo que ya anda.
> Ver gaps en `FRONTIER_ASSISTANT_GAP_ANALYSIS.md` y seguridad en `SAFE_AUTONOMY_LEVELS.md`.

## Principio rector
Lo que está **verde y frágil** (lectura de pantalla, seguridad, navegación de apps) se **protege**, no se toca sin red. Lo **rojo** se arregla donde el **riesgo de romper la app es bajo** (backend, onboarding, fallback humano, distribución, pruebas). No pelear voz/visión de frente: subcontratarlas a frontera vía API.

---

## Etapa 0 — Demo congelada (AHORA)
**Estado:** beta funcional, READY_FOR_SUNDAY.
**Regla de oro:** **NO tocar la app.** Solo piloto y feedback.
- [ ] No cambiar Kotlin/backend/prompts/seguridad.
- [ ] Correr la demo con la operator card.
- [ ] Capturar feedback con el formulario y el post-demo report.
**Sale con:** primer feedback real + decisión.

## Etapa 1 — Después de 1 usuario real
**Foco:** que **una** persona ciega la pueda usar y entender.
- [ ] **Frases naturales:** sumar las formas reales que dijo el usuario (sin tocar el core de routing; ampliar listas de frases).
- [ ] **Rescates:** mejorar los "no entendí" y los caminos de cancelar/cerrar/volver.
- [ ] **Onboarding:** primer arranque guiado por voz (activar accesibilidad, permisos, volumen).
- [ ] **Diagnóstico:** "comprobá si estás lista" más claro y accionable.
**Riesgo romper app:** LOW-MEDIUM (cambios aditivos, con tests).
**Sale con:** Estela usable por 1 persona sin un vidente al lado.

## Etapa 2 — Piloto 3–5 usuarios
**Foco:** confiabilidad en varios teléfonos y manos.
- [ ] **Multi-dispositivo:** probar en 2–3 modelos Android distintos.
- [ ] **Reporte de bugs:** canal simple para que el usuario/acompañante reporte.
- [ ] **Modos claros:** que se entienda en qué modo está (escuchando/cámara/leyendo).
- [ ] **Backend estable:** salir del ngrok de dev → host con dominio fijo y monitoreo básico.
**Riesgo romper app:** LOW (infra) / MEDIUM (UI de modos).
**Sale con:** datos de 3–5 usuarios y backend que no se cae solo.

## Etapa 3 — Producto vendible temprano
**Foco:** poder cobrar un piloto sin vergüenza.
- [ ] **Play Store:** build firmado, ficha, testing track (ver `PLAY_STORE_PREP.md`).
- [ ] **Política de privacidad** pública + Data Safety + declaración de Accesibilidad.
- [ ] **Backend producción:** auth, rate-limit, escala, costos controlados.
- [ ] **Soporte:** un canal y un SLA mínimo.
- [ ] **Pricing piloto:** activar opción A/B/C de `PILOT_OFFER.md`.
**Riesgo romper app:** LOW (release/infra) — **pero** el build hoy está trabado por C: crítico: resolver disco/otra máquina.
**Sale con:** algo instalable, legal y cobrable.

## Etapa 4 — Producto fuerte
**Foco:** confianza profunda y autonomía segura.
- [ ] **Navegación segura:** caminata por GPS con todas las salvaguardas (nunca "cruzá"); en etapa muy temprana hoy.
- [ ] **WhatsApp/Instagram robusto:** selectores resistentes a updates, más flujos.
- [ ] **Fallback humano:** "que te ayude una persona" (familiar/voluntario).
- [ ] **Memoria personalizada:** perfil privado on-device (contactos, preferencias).
- [ ] **Niveles de autonomía:** implementar el modelo de `SAFE_AUTONOMY_LEVELS.md` de forma explícita.
**Riesgo romper app:** MEDIUM-HIGH (toca core y seguridad — con red, tests y revisión).
**Sale con:** un asistente en el que un ciego confía para tareas reales.

---

## Mapa rápido etiqueta → etapa
| Etiqueta | Dónde estamos |
|---|---|
| **Beta funcional** | Etapa 0 (hoy) |
| **Producto piloto** | Etapas 1–2 |
| **Producto vendible** | Etapa 3 |
| **Producto final** | Etapa 4 |

## Lo que NO se hace todavía (anti-scope-creep)
- No prometer autonomía total.
- No navegación como función principal hasta la Etapa 4 con salvaguardas.
- No relajar la seguridad financiera ni la confirmación fuerte en ninguna etapa.
- No competir en voz/visión de frente: usar APIs de frontera.
