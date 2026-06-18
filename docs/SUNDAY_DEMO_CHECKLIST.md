# Estela — Checklist de Demo del Domingo (V1.16 Demo Readiness)

> Objetivo: que una persona no vidente pruebe a Estela de forma **segura, clara y confiable**.
> No es un producto final. Es una prueba de campo honesta.
>
> Rama de referencia: `fix/estela-real-device-intelligence` · HEAD `fb0ae94` · Package `com.ojoclaro.android`
> Dispositivo de prueba: Moto G15 (`ZY32LHS6PS`)

---

## Estado (actualizado 2026-06-12 noche) — LEER ANTES

- ✅ **Backend de visión + ngrok: LEVANTADOS y verificados.** `/health` (local 8080 y público) = **HTTP 200**.
  Escena reprobada en el Moto: **2/2 requests reales con `visionHttpStatus=200`, `analysisCompleted=true`, `safetyPolicyApplied=true`**; la cámara abre y se cierra sola, buffer liberado, **0 imágenes persistidas**, 0 crashes. → **Escena READY.**
  > El backend + el túnel corren en una sesión de la PC. **Si se cierran, hay que volver a levantarlos antes de la demo** (ver "Cómo levantar backend + ngrok antes de la demo" abajo). El OCR de cámara (leer texto) NO depende del backend.
- ⚠️ **Disco C: crítico (~0.28 GB libres)**. No se puede compilar; se usa la **APK ya instalada** (que pasó todo el smoke + escena). Liberar espacio en C: antes de la demo por estabilidad.

### Cómo levantar backend + ngrok antes de la demo

Dos terminales que quedan ocupadas (no las cierres durante la demo) + una verificación:

1. **Terminal 1 — backend** (puerto 8080; el script ya fuerza el modelo de visión correcto):
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_backend.ps1
   ```
2. **Terminal 2 — ngrok** (túnel al dominio reservado que ya usa la APK instalada):
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_ngrok.ps1
   ```
3. **Terminal 3 — verificar** (tiene que imprimir `READY` o `PARTIAL`; `/health` local y público = 200):
   ```powershell
   powershell -ExecutionPolicy Bypass -File scripts\check_estela_demo_infra.ps1
   ```

> Notas: hay un `OPENAI_MODEL` global en la PC que pisa el `.env`; el script de backend lo corrige solo (`gpt-4.1-mini`). Con backend + ngrok arriba, "describime qué estoy apuntando" funciona sin tocar nada más. Un `PARTIAL` suele ser solo por el disco C: bajo — la demo corre igual.

---

## 1. Qué puede hacer Estela hoy

- [ ] **Leer la pantalla** (lo que hay en el celular en ese momento).
- [ ] **Decir qué se puede tocar** (botones / elementos accionables).
- [ ] **Abrir Instagram**.
- [ ] **Abrir el chat de Sofi / Sofia** en Instagram (por nombre visible).
- [ ] **Preparar un mensaje** con **confirmación fuerte** (no manda solo).
- [ ] **Rechazar "sí"** como confirmación débil (no envía con un simple "sí").
- [ ] **Guiar el audio** sin enviarlo automáticamente.
- [ ] **Leer texto con la cámara / OCR** (carteles, hojas).
- [ ] **Describir una escena** bajo demanda (requiere backend vivo).
- [ ] **Bloquear pagos / tarjetas / frases financieras** (no opera plata).
- [ ] **Mostrar presencia visual por estados** (hablar / pensar / escuchar / cámara / reposo).

## 2. Qué NO prometer

- [ ] No es un **producto final**.
- [ ] No tiene **autonomía completa**.
- [ ] No hace **pagos**.
- [ ] No maneja **tarjetas**.
- [ ] No usa **WhatsApp** si sigue bloqueado por sesión.
- [ ] No hace **llamadas reales** sin aviso.
- [ ] No funciona **perfecto en toda luz / ruido / app**.

## 3. Frases recomendadas (decirlas naturales)

| Para… | Frase |
|---|---|
| Leer la pantalla | "leé la pantalla" |
| Saber qué tocar | "qué puedo tocar" |
| Entender dónde está | "qué onda esto" |
| Leer texto con cámara | "leé el texto" |
| Leer un cartel | "qué dice este cartel" |
| Describir escena | "describime qué estoy apuntando" |
| Abrir Instagram | "abrí Instagram" |
| Abrir chat | "abrí el chat de Sofi en Instagram" |
| (Seguridad) plata | "mandá plata" → debe **negarse** |
| (Seguridad) pago | "tocá pagar" → debe **negarse** |
| Cerrar cámara | "cerrar cámara" |
| Frenar todo | "cancelar" |

## 4. Flujo de demo de ~20 minutos

1. [ ] **Saludo y explicación honesta** (qué es, qué no es, que tomamos notas).
2. [ ] **Lectura de pantalla** — "leé la pantalla".
3. [ ] **Navegación básica** — "qué puedo tocar", "volver".
4. [ ] **Cámara / OCR** — apuntar a una hoja con texto grande, "leé el texto".
5. [ ] **Descripción de escena** — "describime qué estoy apuntando" (solo si backend OK; si no, mostrar el fallback honesto).
6. [ ] **Instagram segura** — "abrí Instagram", "abrí el chat de Sofi", preparar mensaje y **mostrar que "sí" no envía**, luego "cancelar".
7. [ ] **Seguridad financiera** — "mandá plata" / "tocá pagar" → confirmar que se niega.
8. [ ] **Feedback final** — preguntas abiertas (sección 6).

## 5. Qué observar durante la prueba

- [ ] ¿Estela **entiende su voz**?
- [ ] ¿Las respuestas son **claras**?
- [ ] ¿**Tarda** mucho?
- [ ] ¿La **cámara** ayuda de verdad?
- [ ] ¿Se siente **segura**?
- [ ] ¿Algo la **incomoda o confunde**?
- [ ] ¿Qué función le parece **más útil**?

## 6. Qué anotar

- [ ] **Frases exactas** que dijo (cómo lo dijo, no solo la intención).
- [ ] **Comandos que fallaron** y en qué pantalla.
- [ ] **Situaciones reales** donde la usaría.
- [ ] **Miedos / desconfianzas**.
- [ ] **Mejoras prioritarias** según ella.
- [ ] ¿**Pagaría** o **recomendaría**?

## 7. Riesgos conocidos

- [ ] **WhatsApp** bloqueado por sesión — no usarlo en la demo si sigue así.
- [ ] **Instagram**: el handle `so_roomero` **no es confiable**; usar siempre el **nombre visible Sofi / Sofia**.
- [ ] **Disco C: crítico** (ver "Estado al preparar"): puede afectar estabilidad de la PC/mirroring.
- [ ] **Cámara en lockscreen**: decisión de privacidad **pendiente**; evaluar si se permite con pantalla bloqueada.
- [ ] **Escena depende de backend / ngrok**: si el túnel está caído, no hay descripción de escena.
- [ ] **OCR depende de luz / distancia / enfoque**: hoja bien iluminada, texto grande, cámara estable.

## 8. Qué hacer si algo falla (plan de rescate)

- [ ] Decir **"cancelar"**.
- [ ] Decir **"cerrar cámara"**.
- [ ] Decir **"volver"**.
- [ ] **Reiniciar Estela** si hace falta (apagar/encender Accesibilidad o relanzar la app — sin `force-stop`).
- [ ] **No insistir** con pagos / WhatsApp / login.
- [ ] **Anotar el fallo** (qué frase, qué pantalla, qué pasó).

## 9. Checklist ANTES de salir

- [ ] Moto **cargado**.
- [ ] Estela **instalada** (`com.ojoclaro.android`).
- [ ] Permisos **CAMERA** y **micrófono** OK.
- [ ] **AccessibilityService activo** (Estela bound).
- [ ] **Backend / ngrok OK** si se va a usar escena (verificar `/health` → 200).
- [ ] **Instagram logueado**.
- [ ] **Volumen alto**.
- [ ] **Datos / internet** OK.
- [ ] **Hoja con texto grande** para OCR.
- [ ] **Contacto de prueba avisado** si se prueba Instagram.

> Atajo: correr `scripts/estela_demo_preflight.ps1` para verificar casi todo esto automáticamente.

## 10. Checklist DESPUÉS de la prueba

- [ ] **Anotar feedback** (mientras está fresco).
- [ ] **Revisar logs seguros** (sin datos privados).
- [ ] **Confirmar cámara cerrada**.
- [ ] **Confirmar que no se enviaron mensajes** por accidente.
- [ ] **Clasificar bugs**: `BLOCKER` / `HIGH` / `MEDIUM` / `LOW`.

---

> **Privacidad:** no anotar ni guardar datos privados, números reales ni contenido de chats reales.
> Las notas son sobre el comportamiento de Estela, no sobre las personas.
