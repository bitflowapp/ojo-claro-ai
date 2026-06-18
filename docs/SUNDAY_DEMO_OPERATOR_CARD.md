# Estela — Tarjeta de Operador (Demo del Domingo)

> Una página para Marco. Leela antes de empezar. Sin improvisar.
> Dispositivo: Moto G15 (`ZY32LHS6PS`) · Package `com.ojoclaro.android`
> Detalle completo en `docs/SUNDAY_DEMO_CHECKLIST.md`.

---

## 1. Antes de salir (5 minutos)

- [ ] Moto **cargado**.
- [ ] Moto **desbloqueado** (la demo no funciona bien con la pantalla bloqueada).
- [ ] **Volumen alto** (Estela habla; es voz-primero).
- [ ] **Instagram logueado** en el Moto.
- [ ] Si vas a usar **escena**: backend + ngrok corriendo.
  - Terminal 1: `powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_backend.ps1`
  - Terminal 2: `powershell -ExecutionPolicy Bypass -File scripts\start_estela_demo_ngrok.ps1`
- [ ] Correr el verificador: `powershell -ExecutionPolicy Bypass -File scripts\check_estela_demo_infra.ps1` → tiene que decir **READY** o **PARTIAL** (PARTIAL por disco está OK).
- [ ] **Hoja con texto grande** (para el OCR / "leé el texto").
- [ ] **Contacto Sofi avisado** si vas a probar Instagram.

## 2. Apertura honesta (decílo vos)

> "Esto es una beta funcional. No quiero venderte humo; quiero ver si te ayuda de verdad, dónde falla y qué sería prioritario para vos."

## 3. Flujo recomendado (en orden, pausado)

Que la persona diga, natural:

1. **"leé la pantalla"**
2. **"qué puedo tocar"**
3. **"leé el texto"** (apuntando a la hoja)
4. **"describime qué estoy apuntando"** (escena — solo si `/health` dio 200)
5. **"abrí Instagram"**
6. **"abrí el chat de Sofi en Instagram"**
7. **"mandá plata"** / **"tocá pagar"** → mostrar que **se niega** (seguridad)

> Si preparás un mensaje en Instagram: un **"sí"** NO envía (es a propósito). Para frenar: **"cancelar"**.

## 4. Qué NO hacer

- ❌ **WhatsApp** (sigue bloqueado por sesión).
- ❌ **Pagos reales** / tocar botones de pago.
- ❌ **Tarjetas**.
- ❌ **Llamadas reales**.
- ❌ Prometer **autonomía total** / que "hace todo solo".
- ❌ Probar **escena si `/health` no da 200**.

## 5. Si algo falla (plan de rescate)

1. Decir **"cancelar"**.
2. Decir **"cerrar cámara"**.
3. Decir **"volver"**.
4. Revisar **volumen**.
5. Revisar **backend/ngrok** (`check_estela_demo_infra.ps1`).
6. **Reiniciar Estela** si hace falta (apagar/encender Accesibilidad — sin `force-stop`).
7. **Anotar el fallo y seguir.** No insistir 20 veces con lo mismo.

## 6. Preguntas de feedback (al final)

- ¿Qué fue lo **más útil**?
- ¿Qué te dio **desconfianza**?
- ¿Qué comando **dirías vos** naturalmente?
- ¿En qué **situación real** la usarías?
- ¿Qué tendría que **mejorar** para que la uses todos los días?
- ¿**Pagarías** por algo así o la **recomendarías**?

## 7. Clasificación rápida del feedback

- **BLOCKER**: peligro, crash, cámara pegada, acción sin permiso.
- **HIGH**: algo clave no funciona.
- **MEDIUM**: incomodidad o frase no entendida.
- **LOW**: detalle menor.

---

## Si la escena no funciona (anti-pánico)

Si "describime qué estoy apuntando" falla (ngrok/backend caídos), **NO te trabes**: casi todo es local y sigue andando.

- ✅ **OCR / "leé el texto"** sigue funcionando — **es local**, no depende de internet.
- ✅ **Lectura de pantalla** ("leé la pantalla", "qué puedo tocar") sigue funcionando.
- ✅ **Instagram** (abrir, abrir chat) sigue funcionando.
- ✅ **Seguridad** (bloqueo de pagos/tarjetas, "sí" no envía) sigue funcionando.
- ❌ **No prometas** descripción del entorno si ngrok/backend están caídos.

Frase para salir bien parado:

> "La descripción de escena depende de internet y de un servidor; hoy puede fallar. Pero la **lectura de texto con la cámara es local** y funciona igual."

> Para reactivar la escena: levantá backend + ngrok (sección 1) y verificá `/health` = 200.
