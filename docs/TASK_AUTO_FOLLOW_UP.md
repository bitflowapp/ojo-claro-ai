# Task Auto Follow-up

Paquete 6D agrega follow-up automatico de tareas activas cuando cambia la
pantalla. El objetivo es que Estela actualice tickets sin que el usuario tenga
que decir siempre `revisa la tarea`, pero sin hablar como loro y sin ejecutar
acciones.

## Componentes

- `AgentTaskFollowUpCoordinator`
- `AgentTaskFollowUpPolicy`
- `AgentTaskFollowUpCooldown`
- `AgentTaskFollowUpEvent`
- `AgentTaskFollowUpDecision`

Ruta:

- `androidApp/src/main/java/com/ojoclaro/android/agent/task/followup`

El graph crea un coordinator process-scope. `HomeViewModel` conserva la memoria
de tareas en `AgentTaskOrchestrator` y le pasa al coordinator el plan activo y
el callback `observeScreenForCurrentTask(snapshot)`.

## Flag

El hook real esta detras de:

- `AgentCoreFeatureFlags.taskAutoFollowUpEnabled`

Default de produccion: OFF.

Debug/smoke QA: ON junto con `accessibilityRuntimeContextEnabled` y
`screenChangeAwarenessEnabled`.

Con el flag OFF, el comportamiento legacy se conserva: el observer de tareas
solo corre por comando manual.

## Cuando habla automatico

Puede hablar solo si hay tarea activa y se detecta algo relevante:

- cambio a una app vinculada a la tarea, por ejemplo Uber o WhatsApp
- aparece un campo de destino
- aparece campo de mensaje
- aparece boton de solicitar viaje
- aparece boton enviar o microfono
- aparece pantalla sensible

Ejemplos:

- `Ya estamos en Uber.`
- `Veo un campo para destino. Decime a donde queres ir si todavia no lo confirmaste.`
- `Veo una opcion para solicitar el viaje. No voy a pedirlo sin tu confirmacion final.`
- `Ya estamos en WhatsApp.`
- `Veo el campo para escribir. Puedo ayudarte a preparar el mensaje, pero no voy a enviarlo sin confirmacion.`

## Cuando se calla

No habla si:

- no hay tarea activa
- no hay snapshot util
- el cambio es minimo y no cambia cues de la tarea
- el mismo `semanticKey` esta dentro del cooldown
- hay confirmacion pendiente y el aviso es LOW/NORMAL
- TalkBack esta activo y el aviso es LOW/NORMAL
- el texto candidato no es seguro para voz

Aunque se calle, si el observer detecta un update de ticket, la memoria de
tarea y el estado UI se actualizan.

## Cooldown

El cooldown se aplica por `semanticKey`, con ventana default de 15 segundos.

Efectos:

- no repite `Ya estamos en Uber` cuando ya fue dicho o el ticket ya quedo
  completado
- no repite la misma advertencia sensible dentro de la ventana
- permite un aviso CRITICAL si cambia el tipo de riesgo, por ejemplo de
  password a pantalla bancaria

## Confirmaciones pendientes

Si hay pending confirmation del bridge o del flujo legacy:

- LOW/NORMAL se suprimen
- HIGH/CRITICAL de seguridad se permiten

Esto evita que Estela hable encima de una pregunta de confirmacion activa.

## Pantallas sensibles

Pantallas bancarias, password, OTP o pago/transferencia se tratan como
CRITICAL. Estela puede avisar que la pantalla contiene datos sensibles, pero
no enumera contenido visible.

No se guardan tarjetas completas, contrasenas, OTP, claves ni texto sensible
completo. El coordinator no loguea texto del snapshot.

## TalkBack

El coordinator tiene hook `isTalkBackActive`. Si se pasa `true`, suprime
LOW/NORMAL para no competir con TalkBack. En la integracion actual no hay un
detector de TalkBack confiable cableado; el hook queda preparado para Paquete
6E o una fase de accesibilidad posterior.

## Limites actuales

Paquete 6D no ejecuta acciones. No hace:

- clicks
- gestos
- acciones globales
- escritura automatica
- pedido real de taxi
- envio de mensajes
- grabacion o envio de audios
- pagos
- transferencias
- compras
- borrados
- llamadas
- confirmacion de viajes o compras
- cambios de metodo de pago

## Para Paquete 6E

Queda pendiente agregar acciones controladas limitadas:

- abrir app
- enfocar busqueda si existe API segura
- preparar texto sin enviar
- pedir confirmacion fuerte

El envio, pago, pedido de viaje y confirmaciones sensibles siguen bloqueados.

## Paquete 6E: estado

Paquete 6E ya esta implementado como una capa separada de **propuestas de
accion controladas** (`CONTROLLED_ACTION_PROPOSALS.md`). El follow-up
automatico no cambia: sigue solo observando la pantalla y hablando frases
cortas con cooldown.

La capa 6E describe la proxima accion segura, clasifica su riesgo y la deja
preparada en memoria, pero todavia no la ejecuta. El envio, el pago, el pedido
de viaje y las confirmaciones sensibles siguen bloqueados.

## Para Paquete 6F

- ejecucion limitada de acciones seguras
- abrir app: ya permitido
- investigar API segura para foco / busqueda
- preparar texto con confirmacion

El envio, pago, pedido de viaje y confirmaciones sensibles siguen bloqueados.
