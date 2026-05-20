# Agent Task Planner

Paquete 6A agrega una capa pura de planificacion de tareas con tickets
operativos. No ejecuta acciones reales, no abre apps y no usa APIs de
accesibilidad para actuar sobre la pantalla.

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

## Integracion actual

`HomeViewModel` intercepta comandos de tarea antes del flujo legacy solo para:

- crear plan de viaje
- responder `que estas haciendo`
- responder `en que paso estas`
- responder `que falta`
- cancelar con `cancelar tarea`, `cancela eso` u `olvidalo`
- consultar apps de transporte instaladas
- abrir una app segura de transporte como handoff externo

Si hay una confirmacion pendiente del bridge o del flujo legacy, no inicia una
tarea nueva. Responde que primero hay que confirmar o cancelar la accion
pendiente.

El estado minimo visible se expone en `HomeUiState`:

- `activeTaskTitle`
- `activeTaskStep`
- `activeTaskSummary`

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

## Futuro, Paquete 6B

Paquete 6B ya agrego registry de apps y apertura segura.

## Futuro, Paquete 6C

El siguiente paquete puede observar pantallas de Uber, Cabify, WhatsApp y
Ajustes con `StructuredScreenSnapshot`, actualizar tickets segun pantalla real
y guiar paso a paso. Todavia no debe agregar clicks automaticos ni
confirmaciones sensibles automaticas.
