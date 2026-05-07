# WhatsApp y asistente de accesibilidad

Fecha: 2026-05-04

Este documento describe la primera capa segura de comandos externos de Ojo Claro AI para WhatsApp y lectura visible de pantalla. El alcance es deliberadamente limitado: ayudar a preparar acciones, no controlar aplicaciones ni enviar mensajes por cuenta propia.

## Que hace

- Detecta comandos locales como:
  - `abrí WhatsApp`
  - `mandale a Sofi: estoy llegando`
  - `escribile a mamá que estoy bien`
  - `leeme este mensaje`
  - `qué dice la pantalla`
  - `confirmar`
  - `cancelar`
- Abre WhatsApp si está instalado.
- Prepara un mensaje con `ACTION_SEND` para que el usuario elija el chat y confirme manualmente dentro de WhatsApp.
- Pide confirmación antes de abrir WhatsApp con un mensaje preparado.
- Lee texto visible en pantalla mediante `AccessibilityService` solo cuando el usuario pide leer pantalla.
- Responde con voz clara cuando no puede hacer algo.

## Que NO hace

- No envía mensajes automáticamente.
- No toca botones de WhatsApp.
- No busca chats por scraping.
- No lee chats completos.
- No guarda mensajes.
- No sube mensajes ni textos visibles al backend.
- No usa WhatsApp Business API.
- No usa APIs no oficiales de WhatsApp.
- No lee contraseñas: los nodos marcados como password se ignoran.
- No controla el teléfono completo.

## Permisos usados

- `android.permission.CAMERA`: OCR local del MVP Android.
- `android.permission.INTERNET`: backend/fallback existente.
- `android.permission.BIND_ACCESSIBILITY_SERVICE`: declarado solo en el servicio de accesibilidad, protegido por Android para que el sistema pueda vincular el servicio.

El servicio de accesibilidad se declara con:

- `android:canRetrieveWindowContent="true"` para poder leer texto visible.
- `android:isAccessibilityTool="true"` porque la función central es asistir a una persona con discapacidad visual.
- Eventos básicos de ventana/contenido/texto.

## Por que usa AccessibilityService

Android no permite que una app lea texto visible de otra app sin consentimiento explícito del usuario. Para una persona ciega o con baja visión, leer el texto visible de pantalla puede ser una función de accesibilidad real, especialmente si TalkBack o la app actual se vuelven confusos.

Texto recomendado para explicar el permiso:

```txt
Este permiso permite que Ojo Claro lea texto visible en pantalla para ayudarte. No guarda mensajes ni contraseñas.
```

## Flujo de escritura segura

Ejemplo:

```txt
Usuario: Mandale a Sofi: estoy llegando.
Ojo Claro: Voy a preparar un mensaje para Sofi que dice: estoy llegando. Confirmá antes de enviarlo.
Usuario: Confirmar.
Ojo Claro: Abre WhatsApp con el texto preparado. El usuario elige el chat y toca enviar manualmente dentro de WhatsApp.
```

Si WhatsApp no está instalado:

```txt
No encontré WhatsApp instalado.
```

Si el usuario cancela:

```txt
Acción cancelada.
```

## Flujo de lectura de pantalla

Si el usuario dice `leeme este mensaje` o `qué dice la pantalla`:

- Si el servicio está activo, Ojo Claro lee texto visible limitado.
- Si no está activo, responde:

```txt
Necesito activar el permiso de accesibilidad para leer la pantalla.
```

No se guarda el contenido leído y no se manda al backend.

## Riesgos

- Un servicio de accesibilidad es sensible: puede ver contenido de otras apps. Por eso esta implementación no almacena, no transmite y no automatiza taps.
- Algunos textos visibles podrían incluir datos personales. La lectura se limita al momento en que el usuario la pide.
- WhatsApp puede cambiar su comportamiento de intents. El fallback seguro es avisar que no se pudo abrir o pedir que el usuario elija manualmente.
- Esta capa no reemplaza a TalkBack ni promete operar WhatsApp sin intervención humana.

## Como probar

1. Instalar el APK debug.
2. Abrir Ojo Claro AI y validar que el MVP anterior sigue funcionando.
3. Activar el servicio en Ajustes de Android, Accesibilidad, Ojo Claro AI.
4. Enviar al ViewModel o futuro flujo de voz frases como:
   - `abrí WhatsApp`
   - `mandale a Sofi: estoy llegando`
   - `confirmar`
   - `cancelar`
   - `qué dice la pantalla`
5. Verificar que WhatsApp solo se abre o recibe texto preparado, pero nunca envía mensajes solo.
6. Verificar que no hay persistencia de mensajes ni llamadas al backend con texto visible.

## Explicacion para Google Play

Ojo Claro AI usa AccessibilityService como herramienta de accesibilidad para personas ciegas o con baja visión. La finalidad es leer texto visible en pantalla bajo pedido explícito del usuario y asistir con acciones seguras como preparar un mensaje para que el usuario lo confirme manualmente. La app no recopila, almacena ni transmite contenido de chats, contraseñas o textos visibles. La app no realiza clics automáticos ni envía mensajes automáticamente.

## Limites actuales

- No hay control completo de WhatsApp.
- No hay búsqueda automática de chats.
- No hay envío automático.
- No hay lectura profunda de conversaciones.
- La lectura de pantalla depende de que el usuario active manualmente el servicio de accesibilidad.
- Falta validación real en dispositivo con TalkBack y usuarios ciegos o con baja visión.
