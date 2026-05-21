# Agent Task Planner

Paquete 6A agrego una capa pura de planificacion de tareas con tickets
operativos. No ejecuta acciones reales, no abre apps y no usa APIs de
accesibilidad para actuar sobre la pantalla.

Paquete 6C extiende esa base para tareas guiadas de WhatsApp y para estado
operativo visible actualizado por pantalla observada.

## Que son los Task Tickets

Un `AgentTaskPlan` representa una tarea activa del usuario, por ejemplo
`Pedir viaje`. El plan contiene `AgentTaskTicket`: pasos internos visibles,
consultables y auditables.

Cada ticket guarda solo estado operativo:

- que paso se esta preparando
- que dato falta
- si requiere confirmacion
- nivel de riesgo
- si el paso seria seguro para automatizacion futura
- que se completo o cancelo

No guarda razonamiento privado ni trazas internas de decision.

## Que no guardan

La memoria de tarea es solo en RAM. No persiste en disco.

Los tickets no deben guardar:

- tarjetas completas
- contrasenas, claves o PIN
- OTP o codigos de verificacion
- chats completos
- OCR completo
- capturas de pantalla
- datos de pago completos

El planner redacta o descarta datos sensibles obvios antes de ponerlos en
`userGoal` o `resolvedData`.

## Flujo pedir taxi

Cuando el usuario dice algo como `pedime un taxi`, `pedi un Uber` o
`buscame un Cabify`, el planner crea un `AgentTaskPlan` de tipo
`REQUEST_RIDE`.

Tickets minimos:

1. Buscar app de transporte.
2. Confirmar destino.
3. Revisar ubicacion actual.
4. Revisar metodo de pago.
5. Revisar precio y conductor.
6. Confirmacion final para solicitar viaje.

Si detecta `Uber` o `Cabify`, lo deja como hint operativo en el primer ticket.
Si detecta destino, lo guarda como `resolvedData.destination` siempre que no
parezca sensible. Si falta destino, el ticket queda `WAITING_FOR_USER` y la voz
pregunta: `A donde queres ir?`

## Confirmaciones requeridas

El plan de viaje siempre tiene `requiresFinalConfirmation = true`.

Los pasos de pago, precio/conductor y confirmacion final requieren
confirmacion. La confirmacion final tiene riesgo `CRITICAL` y nunca queda
marcada como segura para automatizacion.

La frase de creacion del plan es deliberadamente conservadora:

`Voy a ayudarte a pedir un viaje. Primero necesito confirmar el destino y luego revisar precio y forma de pago. No voy a solicitar el viaje sin tu confirmacion final.`

## Flujo WhatsApp mensaje/audio

Cuando el usuario dice algo como:

- `anda a WhatsApp`
- `busca el chat de Sofi`
- `mandale un mensaje a Sofi diciendo hola`
- `mandale un audio a Sofi diciendo llego en 10`
- `quiero mandarle un audio a mi novia`
- `escribile a Juan que ya sali`
- `prepara un mensaje para papa`

el planner crea un plan `SEND_WHATSAPP_MESSAGE` o `SEND_WHATSAPP_AUDIO`.

Datos que intenta extraer:

- `contactName`
- `messageText`
- `wantsAudio`
- `targetApp = WhatsApp`

Tickets minimos:

1. Abrir WhatsApp.
2. Buscar contacto o chat.
3. Confirmar chat correcto.
4. Preparar contenido del mensaje o audio.
5. Confirmacion final antes de enviar.
6. Envio bloqueado hasta paquete futuro.

Si falta contacto, el ticket queda `WAITING_FOR_USER` y el estado operativo
explica: `Falta saber a que contacto queres escribir.`

Si falta contenido, el ticket queda `WAITING_FOR_USER` y explica:
`Falta saber que queres decir.`

Preparar contenido no equivale a enviar. Estela no debe afirmar que un mensaje
o audio ya salio; solo puede decir que queda preparado o pendiente de
confirmacion.

## Integracion actual

`HomeViewModel` intercepta comandos de tarea antes del flujo legacy solo para:

- crear plan de viaje
- crear plan de WhatsApp mensaje/audio
- responder `que estas haciendo`
- responder `en que paso estas`
- responder `en que paso estamos`
- responder `que falta`
- cancelar con `cancelar tarea`, `cancela eso` u `olvidalo`
- revisar pantalla para la tarea con `revisa la tarea`, `actualiza la tarea`,
  `segui con la tarea` o `revisa la pantalla para la tarea`
- consultar apps de transporte instaladas
- abrir una app segura de transporte como handoff externo

Si hay una confirmacion pendiente del bridge o del flujo legacy, no inicia una
tarea nueva. Responde que primero hay que confirmar o cancelar la accion
pendiente.

El estado minimo visible se expone en `HomeUiState`:

- `activeTaskTitle`
- `activeTaskStep`
- `activeTaskSummary`

Si ya hay una tarea activa, una tarea nueva no la reemplaza automaticamente.
Estela responde que el usuario puede cancelarla o pedir reemplazo explicito.

## Paquete 6B: app capability registry

El plan `REQUEST_RIDE` ahora puede consultar un registry local de apps
conocidas. Si detecta Uber, Cabify o DiDi instaladas, completa el ticket
`Buscar app de transporte` y agrega/actualiza un ticket operativo `Abrir <app>`.

Comandos soportados por la tarea:

- `que apps tengo para pedir taxi`
- `abri Uber`
- `abri Cabify`
- `usa Uber`
- `segui con Uber`
- `abri la app`

Abrir una app significa solamente lanzar su pantalla inicial con un handoff
seguro. No significa pedir viaje, confirmar precio, elegir conductor, tocar
metodo de pago ni presionar botones internos.

Despues de abrir Uber o Cabify, Estela puede decir:

`Abri Uber. Ahora puedo orientarte con la pantalla, pero no voy a solicitar el viaje sin confirmacion final.`

## Limites de seguridad

Paquete 6A solo planifica. No hace:

- abrir apps
- pedir viajes
- enviar mensajes
- pagar
- transferir
- comprar
- borrar
- llamar
- confirmar acciones sensibles automaticamente
- ejecutar clicks o gestos de accesibilidad

Paquete 6B mantiene esos limites. Solo agrega deteccion de apps instaladas y
apertura segura con Intent. La confirmacion final del viaje sigue siendo
obligatoria y ningun ticket de pago/precio/conductor se completa por abrir una
app.

## Paquete 6C: observer de pantalla

Paquete 6C agrega `AgentTaskScreenObserver`, una capa pura que recibe el plan
activo y un `StructuredScreenSnapshot`. Devuelve updates de tickets, estado
operativo y un mensaje seguro para voz.

Ejemplos:

- Uber/Cabify/DiDi abierto completa o actualiza el paso de app abierta.
- Campo de destino visible pone `Confirmar destino` en `WAITING_FOR_USER`.
- Pago visible pone `Revisar metodo de pago` en `REQUIRES_CONFIRMATION`.
- Precio/conductor visible pone `Revisar precio y conductor` en
  `REQUIRES_CONFIRMATION`.
- Boton de solicitar viaje visible pone la confirmacion final en
  `REQUIRES_CONFIRMATION`.
- WhatsApp abierto completa `Abrir WhatsApp`.
- Busqueda de WhatsApp activa `Buscar contacto o chat`.
- Contacto visible pide confirmar chat correcto.
- Campo de mensaje visible activa preparar contenido.
- Boton enviar o microfono visible pide confirmacion final, sin enviar ni
  grabar.

Pantallas bancarias, password u OTP bloquean enumeracion de contenido. El
observer no completa tickets basandose en contenido sensible.

## Paquete 6D: Automatic Task Follow-up v1

Paquete 6D agrega `AgentTaskFollowUpCoordinator`, una capa pura/casi pura que
recibe:

- plan activo
- snapshot anterior
- snapshot actual
- estado de app si existe
- estado de confirmacion pendiente
- hook futuro de TalkBack
- clock inyectable

El coordinator decide si debe invocar
`AgentTaskOrchestrator.observeScreenForCurrentTask(snapshot)`, si debe hablar
el resultado, o si debe callarse por cooldown, confirmacion pendiente,
TalkBack activo o falta de cambio relevante.

La integracion real queda detras de `taskAutoFollowUpEnabled`. Produccion
sigue OFF por default. En debug/smoke QA queda ON junto con snapshots y Screen
Change Awareness.

Reglas de voz:

- sin tarea activa, no hace nada
- si cambia a una app relacionada, observa y puede hablar una frase corta
- si aparece un campo o boton relevante, actualiza tickets y puede orientar
- si no cambia nada relevante, no habla
- si hay confirmacion pendiente, suprime LOW/NORMAL
- si la pantalla es bancaria/password/OTP, avisa sin enumerar datos
- no repite el mismo semanticKey dentro del cooldown

Este paquete no agrega acciones reales. Abrir app sigue siendo solo handoff
seguro ya existente; no escribe texto, no envia mensajes, no graba audios, no
pide viajes, no paga y no confirma operaciones.

Paquete 6E queda reservado para acciones controladas limitadas: abrir app,
enfocar busqueda si existe API segura, preparar texto sin enviar y pedir
confirmacion fuerte.

## Paquete 6E: propuestas de accion controladas

`AgentTaskOrchestrator` ahora tambien puede mirar la tarea activa, los tickets
y el ultimo snapshot y producir una `AgentControlledActionProposal`: la
proxima accion segura, con riesgo clasificado y estado (proponer, preparar,
requiere confirmacion o bloqueada).

La logica vive en la capa pura `com.ojoclaro.android.agent.task.action`
(`AgentControlledActionPlanner`, `AgentControlledActionPolicy`,
`AgentControlledActionMemory`). El planner de tareas no cambia: sigue armando
planes y tickets. La capa 6E se apoya en esos planes para describir el proximo
paso, nunca para ejecutarlo.

Comandos nuevos: "cual es el proximo paso", "que vas a hacer ahora",
"prepara el mensaje", "prepara el audio", "busca el chat", "revisa el precio",
"cancela la accion". Ver `CONTROLLED_ACTION_PROPOSALS.md`.

Sigue sin clicks, sin gestos, sin escritura automatica, sin envio de mensajes,
sin grabacion de audios, sin pedido de viajes y sin pagos.
