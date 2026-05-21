# Safe Execution Gate (Paquete 6F)

Paquete 6F agrega una **puerta de ejecucion segura**: la capa que decide si
una propuesta de accion (paquete 6E) puede ejecutarse, si solo puede
prepararse, si esta bloqueada o si requiere confirmacion.

Estela distingue de forma explicita cinco cosas distintas:

1. **Proponer** una accion -- describir que se podria hacer (6E).
2. **Preparar** una accion -- dejar contenido listo en memoria propia.
3. **Ejecutar** una accion segura -- abrir una app soportada.
4. **Bloquear** una accion sensible -- frenarla, no ejecutarla.
5. **Confirmar** -- pedir confirmacion fuerte para acciones futuras.

## Que puede ejecutar 6F

Solo acciones limitadas, seguras y reversibles:

- **OPEN_APP** -- abrir una app ya soportada por `SafeAppLauncher`, con un
  intent de launcher (`ACTION_MAIN` + `CATEGORY_LAUNCHER`). Nunca toca botones
  internos de la app.
- **PREPARE_MESSAGE_TEXT** -- dejar el texto del mensaje preparado en memoria.
- **PREPARE_AUDIO_SCRIPT** -- dejar el guion del audio preparado en memoria.
- **PREPARE_SEARCH_QUERY** -- dejar la query de busqueda preparada en memoria.
- Consultar estado, cancelar propuesta, esperar al usuario.

Preparar contenido ES una ejecucion segura: solo escribe en memoria propia de
Estela, nunca en una app externa, y nunca envia nada.

## Que bloquea 6F

Nunca se ejecuta:

- enviar mensaje, enviar audio, grabar audio,
- pedir taxi, confirmar viaje,
- pagar, transferir, comprar, confirmar compra, tocar metodo de pago,
- borrar, llamar,
- tocar boton de confirmar dentro de una app,
- escribir texto automaticamente en apps externas,
- `performClick`, `dispatchGesture`, `performGlobalAction`.

Aunque el usuario diga "confirmo", una accion critica (`FINAL_CONFIRM_RIDE`,
`FINAL_CONFIRM_SEND_MESSAGE`, `FINAL_CONFIRM_SEND_AUDIO`) sigue bloqueada.

## Decisiones de la puerta

`AgentSafeExecutionGate` es PURO: decide y no tiene efectos.

| Tipo de accion | Decision |
| --- | --- |
| `OPEN_APP` (allowedToExecuteNow) | `ALLOW_SAFE_EXECUTION` |
| `PREPARE_MESSAGE_TEXT` / `PREPARE_AUDIO_SCRIPT` / `PREPARE_SEARCH_QUERY` | `ALLOW_SAFE_EXECUTION` |
| `FOCUS_SEARCH_FIELD` | `PREPARE_ONLY` (falta API segura de foco) |
| `REVIEW_PAYMENT_METHOD` / `REVIEW_RIDE_PRICE` | `PREPARE_ONLY` |
| `FINAL_CONFIRM_RIDE` / `FINAL_CONFIRM_SEND_MESSAGE` / `FINAL_CONFIRM_SEND_AUDIO` | `BLOCK_SENSITIVE_ACTION` |
| `BLOCKED_SENSITIVE_ACTION` | `BLOCK_SENSITIVE_ACTION` |
| `WAIT_FOR_USER_INPUT` | `WAITING_FOR_USER` |
| `UNKNOWN` | `FAILED_SAFE` |
| sin propuesta | `NO_ACTIVE_PROPOSAL` |
| confirmacion externa pendiente | `REQUIRE_CONFIRMATION` |

## Arquitectura

Capa en `com.ojoclaro.android.agent.task.execution`:

| Clase | Rol |
| --- | --- |
| `AgentSafeExecutionGate` | PURO: decide si se puede ejecutar |
| `AgentSafeExecutionRequest` | Entrada: plan + propuesta + comando + flags |
| `AgentSafeExecutionDecision` | Decision pura, sin efectos |
| `AgentSafeExecutionResult` | Resultado de pasar por la puerta |
| `AgentSafeExecutionStatus` | Los 7 estados de decision |
| `AgentExecutionAuditEntry` | Registro de auditoria, sin datos sensibles |

`AgentTaskOrchestrator` integra la puerta: detecta los comandos de ejecucion,
llama al gate, y SOLO si la decision es `ALLOW_SAFE_EXECUTION` ejecuta la
parte segura (abrir app o preparar contenido). Cada intento queda en una
auditoria en RAM (`recentExecutionAudits()`), sin texto sensible.

La apertura de app usa el `SafeAppLauncher` existente. Con `safeAppLauncher`
null (default de produccion hoy), `OPEN_APP` sigue el handoff externo legacy.
Con un launcher inyectado, se abre directo. Ningun camino toca botones
internos: solo un intent de launcher.

## Comandos de voz

| Comando | Efecto |
| --- | --- |
| "ejecuta la accion segura" / "hacelo" / "avanza" / "preparalo" / "dejalo listo" | ejecuta la parte segura de la propuesta actual |
| "confirmo" / "dale" | igual, pero se cede al bridge si hay confirmacion pendiente |

Si no hay propuesta: "No hay una accion preparada." Los comandos ambiguos
("confirmo", "dale") se ceden al flujo de confirmacion legacy/bridge cuando no
hay propuesta propia o hay una confirmacion externa pendiente, para no
secuestrar un "confirmo" que pertenece al bridge.

## Por que enviar, pagar y pedir taxi siguen bloqueados

Son acciones irreversibles o con costo real para una persona ciega. 6F todavia
no tiene una capa de ejecucion auditada para apps externas ni un canal de
confirmacion final robusto. Hasta que existan, estas acciones se proponen y se
preparan, pero NUNCA se ejecutan, aunque el usuario confirme.

## Diferencia propuesta / preparacion / ejecucion

- **Propuesta** (6E): Estela describe la proxima accion segura.
- **Preparacion** (6F): Estela deja el contenido listo en su propia memoria.
  No escribe en la app externa, no envia.
- **Ejecucion** (6F): Estela realiza una accion segura y permitida -- hoy,
  solo abrir una app soportada.

## Proximos pasos (Paquete 6G)

- Investigar acciones Android realmente seguras.
- Foco / busqueda dentro de la app si existe una API segura.
- Tests instrumentados.
- Nunca enviar, pagar ni pedir viaje sin confirmacion fuerte y una capa
  especifica para eso.
