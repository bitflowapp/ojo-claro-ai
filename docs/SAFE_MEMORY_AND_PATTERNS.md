# Memoria segura y patrones frecuentes - Ojo Claro AI

Fecha: 2026-05-05

## Qué recuerda

Ojo Claro puede recordar información corta, útil y aprobada por el usuario:

- Contactos de confianza, por ejemplo: "Sofi es contacto de confianza".
- Preferencias de voz o estilo, por ejemplo: "prefiero respuestas cortas".
- Reglas de advertencia creadas por el usuario, por ejemplo: "si aparece transferencia me avises".
- Apps marcadas como sensibles.
- Comandos frecuentes y patrones de uso no sensibles.

Toda memoria local se guarda solo después de que el usuario dice "confirmar".

## Qué no recuerda

No se guardan chats completos, pantallas completas, OCR completo, imágenes, audios, contraseñas, códigos de verificación, tokens, datos bancarios, números de tarjeta, documentos privados ni ubicaciones sensibles.

La memoria no se sube al backend y no se usa para ejecutar acciones sin confirmación.

## Cómo pide confirmación

Cuando el usuario pide guardar algo, la app crea una acción sensible pendiente:

Usuario: "Recordá que Sofi es contacto de confianza."

App: "Voy a recordar que Sofi es contacto de confianza. Confirmá para guardar."

Usuario: "confirmar."

App: "Listo. Lo voy a recordar."

Si el usuario dice "cancelar", responde: "Cancelado. No guardé nada." y no persiste nada.

## Cómo borrar memoria

"Borrá tu memoria" pide confirmación antes de borrar todo:

"Voy a borrar mi memoria local. Confirmá para continuar."

Al confirmar, borra `SharedPreferences` locales de memoria y responde:

"Listo. Borré mi memoria local."

"Olvidá eso" apunta al último recuerdo seguro y también pide confirmación.

## Cómo lista memoria segura

"Qué recordás de mí" lista solo resúmenes seguros, nunca valores sensibles:

"Esto es lo que recuerdo de forma segura. Recuerdo que preferís respuestas cortas. Recuerdo que Sofi es contacto de confianza."

Si no hay memoria:

"Todavía no guardé preferencias."

## Patrones frecuentes que guarda

`FrequentPatternTracker` guarda metadatos:

- Tipo de comando, por ejemplo `READ_TEXT`.
- Comando normalizado seguro, por ejemplo `read_text`.
- Cantidad de usos.
- Primera y última vez.
- Paquete de app si aplica.
- Si el patrón es sensible.
- Si el usuario aprobó sugerencias para ese patrón.

Ejemplos permitidos:

- El usuario usa seguido "Leer texto".
- El usuario usa seguido "Qué dice la pantalla".
- El usuario cancela seguido.
- El usuario intenta usar WhatsApp seguido, guardado como tipo de acción, sin mensaje.

## Patrones que no guarda

No guarda contenido leído, texto de chats, mensajes de WhatsApp, OCR, códigos, contraseñas, números de tarjeta ni datos privados. Para WhatsApp se guarda `compose_whatsapp_message`, no el destinatario ni el mensaje.

Los patrones sensibles no se sugieren como atajo salvo aprobación explícita futura.

## Detección de riesgos

`RiskDetector` analiza texto visible, OCR, comandos y nombres de paquete. Detecta:

- Dinero o transferencias.
- Pantallas bancarias.
- Campos o texto de contraseña.
- Códigos de verificación.
- Pedidos de datos personales.
- Mensajes urgentes que piden acción.

Frases implementadas:

- "Este texto habla de dinero o transferencia. Revisalo con cuidado antes de responder."
- "Esto parece un código de verificación. No lo voy a leer en voz alta sin confirmación."
- "Esta pantalla puede contener datos privados. Para continuar, usá la seguridad del teléfono."
- "No puedo leer campos de contraseña. Eso es por seguridad."
- "Este mensaje parece urgente y pide una acción. Confirmá antes de responder."

## Límites

La detección es heurística local. Puede no detectar todos los fraudes o marcar texto legítimo como sensible. No reemplaza la seguridad del teléfono, la app bancaria ni el criterio de una persona de confianza.

La memoria no es un perfil completo del usuario. Es una lista local y mínima de ayudas prácticas.

## Privacidad

La memoria se guarda en `SharedPreferences` locales. No hay sincronización de red. `PrivacyGuard` bloquea memoria sensible y patrones con contenido privado.

`Callar` limpia pendientes y corta voz. `Cancelar` limpia la acción pendiente sin ejecutar nada.

## Próximos pasos

- Agregar pantalla accesible para revisar y borrar recuerdos uno por uno.
- Agregar aprobación explícita para sugerencias de patrones sensibles.
- Agregar confirmación fuerte real para pantallas bancarias.
- Validar frases con usuarios ciegos reales.
