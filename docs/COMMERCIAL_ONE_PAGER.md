# Estela — Asistente visual de voz (Ojo Claro AI)

> **Beta funcional.** Lo que sigue describe lo que Estela hace hoy, probado en dispositivo real. No es un producto final.

## Qué es
Estela es una **asistente de voz** para el teléfono Android que ayuda a personas ciegas o con baja visión a entender lo que tienen en la pantalla y en el mundo real. Vive en un botón flotante que está siempre disponible, aunque uses otra aplicación. Se opera hablando, en **español natural**.

## Para quién es
- Personas **ciegas** o con **baja visión**.
- **Familiares y cuidadores** que las acompañan.
- Instituciones: centros de rehabilitación visual, ONGs, universidades, obras sociales, programas de accesibilidad.

## Qué problema resuelve
Mucha información cotidiana es visual y queda fuera de alcance: lo que dice la pantalla del teléfono, un cartel, una etiqueta, una hoja, lo que hay enfrente. Estela **lee y describe esa información con la voz**, de forma simple, rápida y segura, sin depender de que haya alguien al lado.

## Qué puede hacer hoy (verificado en dispositivo)
- **Leer la pantalla**: dice qué hay en la pantalla y qué se puede tocar. (Se lee **dentro del teléfono**, no se sube a internet.)
- **Leer texto con la cámara (OCR)**: carteles, hojas, etiquetas. **Funciona sin internet.**
- **Describir el entorno** bajo demanda: saca **una** foto, la usa un instante para describir lo que tenés enfrente y **no la guarda**. (Requiere internet + servidor.)
- **Mensajería asistida segura (Instagram)**: abre la app, abre un chat por nombre, prepara el mensaje y lo lee antes de mandar. **Solo envía con confirmación fuerte** — un "sí" suelto **no** envía.
- **Seguridad financiera**: si le piden pagar, tocar "pagar" o ingresar una tarjeta, **se niega** (bloqueo local, por diseño).
- **Presencia visual por estados**: un orbe muestra si está escuchando, pensando, hablando o usando la cámara (útil para baja visión y acompañantes).

## Qué no hace todavía (honesto)
- No es autónoma: **no "hace todo solo"**.
- **No confirma ni ejecuta pagos, no ingresa tarjetas.**
- No manda mensajes con un "sí" simple; **no graba ni envía audios** automáticamente; no hace llamadas sin aviso.
- **WhatsApp asistido está en pausa** (depende de la sesión del teléfono).
- **No reemplaza** bastón, perro guía, acompañante ni servicios de emergencia, y **no dice si es seguro cruzar** (a propósito).
- La **descripción de escena depende de internet/servidor** y puede fallar; el OCR de texto es local y sigue andando.
- Solo **Android** por ahora (no iOS).
- La IA **puede equivocarse**: conviene verificar la información crítica.

## Por qué es segura
- Capa de **seguridad financiera local** que bloquea pagos/tarjetas antes de cualquier acción.
- **Confirmación fuerte** para enviar mensajes ("sí" no alcanza).
- **Mínima retención**: no guarda fotos, ni audio, ni ubicación, ni mensajes (según las pruebas actuales).
- Pensada para **sumar información, no para reemplazar** herramientas de movilidad.

## Estado actual
**Beta funcional / lista para piloto.** Probada de punta a punta en un Moto G15 real: lectura de pantalla, OCR, descripción de escena, Instagram seguro y bloqueo financiero, sin fallas críticas en el ensayo.

## Propuesta de valor (en una frase)
> **Estela le devuelve autonomía cotidiana a personas ciegas o con baja visión: lee la pantalla y los textos del mundo real con la voz, en español natural, de forma simple y segura.**

## Cómo sería un piloto
2 semanas, 3 a 5 personas, uso real acompañado, con un formulario de feedback y una reunión final. Marco da soporte directo. Sale un reporte con aprendizajes y decisión. (Detalle en `PILOT_OFFER.md`.)

## Próximos pasos
1. Demo + primer feedback con una persona no vidente.
2. Definir y arrancar el piloto de 2 semanas.
3. Priorizar mejoras según lo que digan los usuarios reales.

---
*Ojo Claro AI ofrece asistencia visual complementaria. Puede equivocarse. No reemplaza herramientas de movilidad, acompañamiento humano ni servicios de emergencia.*
