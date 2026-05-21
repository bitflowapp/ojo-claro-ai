# Task Screen Observer

Paquete 6C agrega una capa pura para que Estela pueda observar la pantalla
actual y actualizar tickets operativos del plan activo sin ejecutar acciones.

Clase principal:

- `AgentTaskScreenObserver`

Ubicacion:

- `androidApp/src/main/java/com/ojoclaro/android/agent/task/screen`

## Entrada

El observer recibe:

- `currentPlan: AgentTaskPlan?`
- `snapshot: StructuredScreenSnapshot?`

`StructuredScreenSnapshot` ya llega redactado. El observer usa solo estado
operativo:

- package visible
- app label
- lineas redactadas
- botones visibles
- campos editables
- foco visible
- flags de `ScreenSignals`

No guarda OCR completo, chats completos, tarjetas, claves, OTP ni datos de
pago completos.

## Salida

Devuelve `AgentTaskScreenUpdateResult` con:

- tipo de observacion
- plan actualizado, si corresponde
- lista de updates de tickets
- mensaje seguro para voz
- estado de espera
- si requiere confirmacion
- bloqueo de pantalla sensible
- advertencia de riesgo

El orquestador aplica el resultado con
`observeScreenForCurrentTask(snapshot)` y actualiza `AgentTaskMemory`.

## Uber, Cabify y DiDi

Para `REQUEST_RIDE`, el observer reconoce paquetes conocidos de transporte:

- Uber: `com.ubercab`
- Cabify: `com.cabify.rider`
- DiDi: `com.didiglobal.passenger`

Reglas:

- Si la app de transporte esta abierta, completa el ticket de app encontrada o
  app abierta y puede decir: `Ya estamos en Uber.`
- Si ve un campo de destino, marca `Confirmar destino` como
  `WAITING_FOR_USER`.
- Si ve informacion de pago, marca `Revisar metodo de pago` como
  `REQUIRES_CONFIRMATION`.
- Si ve precio o conductor, marca `Revisar precio y conductor` como
  `REQUIRES_CONFIRMATION`.
- Si ve una opcion de solicitar, pedir, reservar o confirmar viaje, marca la
  confirmacion final como `REQUIRES_CONFIRMATION`.

Nunca solicita el viaje. Nunca confirma precio. Nunca toca metodo de pago.

## WhatsApp

Para `SEND_WHATSAPP_MESSAGE` y `SEND_WHATSAPP_AUDIO`, el observer reconoce:

- WhatsApp: `com.whatsapp`
- WhatsApp Business: `com.whatsapp.w4b`

Reglas:

- WhatsApp abierto completa `Abrir WhatsApp`.
- Busqueda visible activa `Buscar contacto o chat`.
- Contacto esperado visible marca `Confirmar chat correcto` como
  `REQUIRES_CONFIRMATION`.
- Campo de mensaje visible activa o deja esperando `Preparar contenido`.
- Boton enviar o microfono visible marca `Confirmacion final antes de enviar`
  como `REQUIRES_CONFIRMATION`.

Para audio, el resultado es deliberadamente conservador: el audio queda como
tarea pendiente. Estela no graba, no mantiene presionado el microfono y no
envia audio.

## Pantallas sensibles

El observer bloquea enumeracion si detecta:

- app bancaria o de pagos
- campo de password
- OTP o codigo de verificacion
- señales de pago/transferencia fuera de una app de transporte o WhatsApp

Mensaje seguro:

`Esta pantalla puede contener datos sensibles. No voy a leerlos.`

En ese caso no completa tickets segun contenido visible.

## Comandos soportados

El orquestador usa esta capa para:

- `revisa la tarea`
- `actualiza la tarea`
- `segui con la tarea`
- `revisa la pantalla`
- `revisa la pantalla para la tarea`
- `en que paso estamos`
- `que falta`
- `que estas haciendo`
- `cancelar tarea`

Si no hay snapshot, Estela responde:

`Todavia no tengo una lectura de pantalla disponible.`

Si no hay plan activo, responde:

`No hay una tarea activa.`

## Limites actuales

El observer no ejecuta acciones. No usa:

- `performClick`
- `dispatchGesture`
- `performGlobalAction`
- escritura automatica
- grabacion de audio
- envio de mensajes
- pedido de taxi
- pagos
- compras
- llamadas
- confirmaciones sensibles

## Paquete 6D: follow-up automatico controlado

Paquete 6D conecta el observer con snapshots publicados por
`ScreenContextRepository` mediante `AgentTaskFollowUpCoordinator`.

El observer sigue siendo la unica pieza que interpreta la pantalla para una
tarea. El coordinator decide cuando invocarlo automaticamente:

- tarea activa + cue nuevo de la tarea
- cambio de package con tarea activa
- pantalla sensible nueva

La voz automatica pasa por cooldown semantico. Si el resultado actualiza
memoria pero no conviene hablar, el plan igual se actualiza en
`AgentTaskMemory` y en `HomeUiState`.

Si hay confirmacion pendiente, se callan anuncios LOW/NORMAL. Se permiten
avisos HIGH/CRITICAL de seguridad. Pantallas bancarias, password u OTP no se
enumeran.

El follow-up automatico tampoco ejecuta acciones. No agrega clicks, gestos,
escritura automatica, envio de mensajes, grabacion de audios, pagos ni pedido
real de viajes.

## Paquete 6E: propuestas de accion controladas

El observer de pantalla sigue siendo la unica pieza que interpreta la pantalla
para una tarea. Paquete 6E agrega una capa que consume el mismo snapshot para
**proponer** la proxima accion segura, sin ejecutarla.

`AgentControlledActionPlanner` recibe el snapshot ya estructurado y lo usa solo
para clasificar la situacion:

- boton de solicitar viaje visible -> `FINAL_CONFIRM_RIDE` (CRITICAL, BLOCKED)
- precio o conductor visible -> `REVIEW_RIDE_PRICE` (HIGH)
- info de pago visible -> `REVIEW_PAYMENT_METHOD` (HIGH)
- boton enviar o microfono visible -> `FINAL_CONFIRM_SEND_*` (CRITICAL, BLOCKED)
- pantalla bancaria / password / OTP -> `BLOCKED_SENSITIVE_ACTION`

La propuesta describe el riesgo y dice que NO se va a hacer. No interpreta el
contenido sensible ni lo enumera. Ver `CONTROLLED_ACTION_PROPOSALS.md`.
