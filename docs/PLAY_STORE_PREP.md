# Estela — Preparación para Google Play (futuro)

> **Borrador de preparación.** No es para subir ahora: es la lista de lo que hará falta.
> La app usa un **AccessibilityService**, lo que implica una revisión más estricta de Google (ver "Riesgos de revisión").

## Identidad de la app
- **Nombre app**: Ojo Claro AI — Estela (asistente visual)
  - (Alternativas a evaluar: "Estela — Asistente Visual", "Ojo Claro: Estela")
- **Package**: `com.ojoclaro.android`
- **Categoría sugerida**: Accesibilidad / Herramientas.
- **Idioma principal**: Español (Latinoamérica).

## Descripción corta (máx ~80 caracteres)
> Asistente de voz que lee tu pantalla y el texto del mundo para personas ciegas.

## Descripción larga (borrador)
> Estela es una asistente de voz para personas ciegas o con baja visión. Te lee
> en voz alta lo que hay en la pantalla del teléfono y el texto del mundo real
> (carteles, hojas, etiquetas) con la cámara. Bajo pedido puede describirte el
> entorno. Funciona en español natural, desde un botón siempre disponible.
>
> Pensada para ser simple y segura: no hace pagos, no ingresa tarjetas y pide
> una confirmación clara antes de enviar un mensaje. La lectura de pantalla se
> procesa dentro del teléfono. Las fotos se usan un instante y no se guardan.
>
> Estela es una herramienta de asistencia complementaria. Puede equivocarse y no
> reemplaza el bastón, el perro guía, el acompañamiento humano ni los servicios
> de emergencia.

## Público objetivo
- Personas ciegas o con baja visión; familiares; cuidadores; instituciones de accesibilidad.
- Clasificación de contenido: apta para todo público (completar el cuestionario de Play).

## Permisos y justificación (para la revisión)
- **AccessibilityService**: función central — leer el contenido de la pantalla y describir qué se puede tocar para usuarios ciegos/baja visión. Requiere completar la **declaración de uso de la API de Accesibilidad**.
- **CAMERA**: leer texto (OCR) y describir el entorno bajo pedido del usuario.
- **RECORD_AUDIO**: comandos de voz (la app es voz-primero).
- **Servicio en primer plano (cámara/micrófono)**: para escuchar y usar la cámara a pedido mientras hay otras apps abiertas.
- **INTERNET**: enviar una foto al servidor solo para la descripción de escena.
- **Revisar en el manifest** si hay permisos extra (p. ej. ubicación) y quitar/justificar los que no se usen en esta versión.

## Privacidad
- **Hace falta una Política de Privacidad pública (URL)** antes de publicar.
- Completar el formulario de **Data Safety** de Play declarando: la foto de escena se envía a un servidor para procesarla; no se persisten imágenes/audio/ubicación/mensajes (según el comportamiento actual).
- Texto base de privacidad: ver `docs/PRIVACY_AND_SAFETY.md` y `docs/SAFETY_AND_PRIVACY_BRIEF.md`.

## Capturas necesarias (assets de la ficha)
- Teléfono: mínimo 2–8 capturas (1080×1920 aprox).
  - Botón/orbe de Estela en pantalla.
  - Lectura de pantalla en acción.
  - OCR leyendo un cartel/hoja.
  - Mensaje de seguridad (se niega a pagar).
- **Icono** 512×512 y **gráfico destacado** 1024×500.
- Evitar mostrar datos privados o chats reales en las capturas.

## Video demo sugerido (opcional, recomendado)
- 20–40 s: persona dice "leé el texto" → Estela lee; "describime qué tengo enfrente" → describe; "mandá plata" → se niega.
- Subtítulos + locución clara. Sin prometer autonomía total.

## Riesgos de revisión (importante)
- **Uso de AccessibilityService**: Google revisa con lupa estas apps. Hay que dejar **clarísimo** que el uso es genuinamente de accesibilidad (lo es) y completar la declaración; si no, rechazo.
- **Permisos sensibles** (cámara/micrófono/foreground): justificación clara y prominent disclosure si hace falta.
- **Política de privacidad** ausente o incompleta = rechazo.
- Mensajería/automatización sobre otras apps: explicar que es asistencia, no automatización abusiva.

## Qué falta antes de subir a Play Store
- [ ] Build **release firmado** (hoy bloqueado por espacio en C: y por la regla de no compilar — resolver en otra máquina/cuando haya disco).
- [ ] **Política de privacidad** publicada (URL).
- [ ] Formulario **Data Safety** completo.
- [ ] **Declaración de Accesibilidad** completa.
- [ ] **Assets** (icono, capturas, gráfico destacado, video).
- [ ] Cuenta de **Google Play Console** + ficha creada.
- [ ] **Testing track** (interno/cerrado) antes de producción.
- [ ] Revisar y limpiar **permisos** del manifest.
- [ ] Verificar que la **escena** apunte a un backend estable (no a un ngrok temporal).

> Nada de esto se hace en este kit: es la hoja de ruta para cuando se decida publicar.
