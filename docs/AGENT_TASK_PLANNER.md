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

Si hay una confirmacion pendiente del bridge o del flujo legacy, no inicia una
tarea nueva. Responde que primero hay que confirmar o cancelar la accion
pendiente.

El estado minimo visible se expone en `HomeUiState`:

- `activeTaskTitle`
- `activeTaskStep`
- `activeTaskSummary`

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

## Futuro, Paquete 6B

El siguiente paquete puede agregar:

- registro de capacidades de apps
- deteccion de apps instaladas: Uber, Cabify, WhatsApp, Ajustes
- apertura segura con Intent
- actualizacion de tickets segun pantalla real

Todavia no debe agregar clicks automaticos ni confirmaciones sensibles
automaticas.
