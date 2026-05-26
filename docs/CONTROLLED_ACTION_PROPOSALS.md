# Controlled Action Proposals (Paquete 6E)

Paquete 6E le da a Estela una capa de **propuestas de accion controladas**.
La capa es pura: no toca Android, no abre apps, no escribe, no envia, no toca
la pantalla. Solo **describe** cual seria la proxima accion segura, clasifica
su riesgo y dice si Estela puede prepararla, si requiere confirmacion o si
esta bloqueada.

Pasa de:

> "Veo el campo para escribir."

a:

> "La proxima accion segura es preparar el mensaje para Sofi. El contenido
> seria: 'llego en 10'. No voy a enviarlo sin confirmacion final."

Y de:

> "Veo un boton para solicitar viaje."

a:

> "Esa accion es critica. Puedo dejarla marcada como pendiente, pero no voy a
> pedir el viaje en esta version."

## Proponer / Preparar / Ejecutar

Tres conceptos distintos. 6E llega hasta el segundo.

- **Proponer**: describir que se podria hacer. Siempre permitido.
- **Preparar**: dejar contenido listo en memoria (texto de mensaje, guion de
  audio, texto de busqueda). Permitido, pero NO se escribe en la app externa.
- **Ejecutar**: realizar la accion en la app real. En 6E NO ocurre para
  ninguna accion sensible. Solo abrir una app (`OPEN_APP`) queda marcado como
  ejecutable, porque ya esta soportado de forma segura por `SafeAppLauncher`.

Una propuesta nunca afirma que una accion se ejecuto. `allowedToExecuteNow` es
`false` para cualquier accion sensible (regla central, verificada en tests).

## Arquitectura

Capa pura en `com.ojoclaro.android.agent.task.action`:

| Clase | Rol |
| --- | --- |
| `AgentControlledActionModels` | Enums + `AgentControlledActionProposal` + `AgentControlledActionResult` |
| `AgentControlledActionPolicy` | Fuente unica de riesgo, estado y ejecutabilidad |
| `AgentControlledActionPlanner` | Mira tarea + tickets + snapshot y arma la propuesta |
| `AgentControlledActionMemory` | Guarda la propuesta activa (RAM, no disco) |

`AgentTaskOrchestrator` integra la capa: detecta los comandos de propuesta,
invoca al planner, guarda la propuesta en `AgentControlledActionMemory` y
devuelve un `AgentTaskOrchestratorResult.Handled` con `actionProposal`.

`HomeViewModel` expone campos minimos en `HomeUiState`:
`pendingActionTitle`, `pendingActionRisk`, `pendingActionSummary`,
`pendingActionRequiresConfirmation`.

## Tipos de accion

| Tipo | Riesgo | Estado | Ejecutable |
| --- | --- | --- | --- |
| `OPEN_APP` | LOW | READY_BUT_NOT_EXECUTED | si (abrir app ya es seguro) |
| `FOCUS_SEARCH_FIELD` | MEDIUM | READY_BUT_NOT_EXECUTED | no |
| `PREPARE_SEARCH_QUERY` | MEDIUM | READY_BUT_NOT_EXECUTED | no |
| `PREPARE_MESSAGE_TEXT` | HIGH | READY_BUT_NOT_EXECUTED | no |
| `PREPARE_AUDIO_SCRIPT` | HIGH | READY_BUT_NOT_EXECUTED | no |
| `REVIEW_PAYMENT_METHOD` | HIGH | REQUIRES_CONFIRMATION | no |
| `REVIEW_RIDE_PRICE` | HIGH | REQUIRES_CONFIRMATION | no |
| `FINAL_CONFIRM_RIDE` | CRITICAL | BLOCKED | no |
| `FINAL_CONFIRM_SEND_MESSAGE` | CRITICAL | BLOCKED | no |
| `FINAL_CONFIRM_SEND_AUDIO` | CRITICAL | BLOCKED | no |
| `BLOCKED_SENSITIVE_ACTION` | CRITICAL | BLOCKED | no |
| `WAIT_FOR_USER_INPUT` | LOW | WAITING_FOR_USER | no |
| `UNKNOWN` | LOW | BLOCKED | no |

## Acciones permitidas en 6E

- Proponer la proxima accion segura.
- Preparar texto de busqueda, contenido de mensaje o guion de audio en
  memoria (sin escribirlo en la app externa).
- Orientar sobre precio de viaje o metodo de pago, sin tocarlos.
- Marcar una confirmacion final como pendiente.
- Abrir una app ya soportada por `SafeAppLauncher`.

## Acciones bloqueadas en 6E

- Pedir el viaje / confirmar viaje.
- Enviar mensajes.
- Grabar o enviar audios.
- Pagar, transferir, comprar.
- Tocar o cambiar el metodo de pago.
- Escribir texto automaticamente en apps externas.
- `performClick`, `dispatchGesture`, `performGlobalAction`.
- Leer datos sensibles completos de la pantalla.

## Confirmaciones fuertes

Para `FINAL_CONFIRM_RIDE`, `FINAL_CONFIRM_SEND_MESSAGE` y
`FINAL_CONFIRM_SEND_AUDIO` la propuesta puede pedir una confirmacion fuerte y
explicita. Pero **aunque el usuario confirme**, en 6E la accion NO se ejecuta:
el estado queda `BLOCKED` y `allowedToExecuteNow` es `false`. La confirmacion
solo deja la accion marcada como pendiente.

## Por que taxi, mensaje, audio y pago siguen bloqueados

Son acciones irreversibles o con costo real para una persona ciega: un viaje
pedido por error, un audio enviado sin querer, un pago equivocado. 6E todavia
no tiene una capa de ejecucion auditada ni un canal de confirmacion final
robusto. Hasta que exista, estas acciones se proponen y se preparan, pero no
se ejecutan.

## Comandos de voz

| Comando | Intencion |
| --- | --- |
| "cual es el proximo paso" / "que vas a hacer ahora" / "segui" | NEXT_STEP |
| "prepara el siguiente paso" / "hace lo siguiente" | NEXT_STEP |
| "busca el chat" | SEARCH_CHAT |
| "prepara el mensaje" | PREPARE_MESSAGE |
| "prepara el audio" | PREPARE_AUDIO |
| "prepara el taxi" | PREPARE_RIDE |
| "revisa el precio" | REVIEW_PRICE |
| "cancela la accion" / "cancela la propuesta" | cancelar propuesta |

Si no hay tarea activa, Estela responde "No hay una tarea activa." y no crea
propuesta. Si hay una confirmacion pendiente del bridge, no se crea una
propuesta critica nueva hasta resolver o cancelar.

## Proximos pasos (Paquete 6F)

- Ejecucion limitada de acciones seguras.
- Abrir app: ya permitido.
- Investigar si hay API segura para foco / busqueda.
- Preparar texto con confirmacion.
- Todavia sin enviar, sin pagar y sin pedir viaje.

## Paquete 6F: Safe Execution Gate

Paquete 6F agrega la **puerta de ejecucion segura** sobre estas propuestas.
Cada `AgentControlledActionProposal` ahora tambien lleva `preparedText`: el
contenido preparable (texto del mensaje, guion del audio, query de busqueda),
ya saneado y sin datos sensibles.

La puerta (`AgentSafeExecutionGate`) decide, a partir de la propuesta, si la
accion puede ejecutarse de forma segura, si solo puede prepararse, si esta
bloqueada o si requiere confirmacion. En 6F solo se ejecuta abrir una app
soportada o preparar contenido en memoria. Ver `SAFE_EXECUTION_GATE.md`.

Comandos nuevos de ejecucion: "ejecuta la accion segura", "hacelo", "avanza",
"preparalo", "dejalo listo", "confirmo", "dale". El comando "dejalo listo"
pasa de proponer (6E) a ejecutar la parte segura (6F).

Enviar mensajes y audios, pagar y pedir viajes siguen bloqueados, aunque el
usuario confirme.
