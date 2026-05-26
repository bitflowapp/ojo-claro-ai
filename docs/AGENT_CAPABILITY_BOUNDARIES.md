# Agent Capability Boundaries (Paquete 6G)

Paquete 6G audita y fija, de forma seria, que acciones de Android puede
ejecutar Estela hoy de forma realmente segura para una persona ciega, y
cuales no.

El resultado es el **Safe Action Capability Registry**: una fuente unica de
verdad que clasifica cada accion. La puerta de ejecucion segura
(`AgentSafeExecutionGate`) la consulta antes de permitir cualquier ejecucion:
si una capacidad no es `SUPPORTED_SAFE`, la accion NO se ejecuta, sin importar
lo que diga la propuesta.

## Tabla de auditoria de capacidades

| Accion | Existe hoy | Se puede hacer seguro | Riesgo | Requiere confirmacion | Decision 6G |
| --- | --- | --- | --- | --- | --- |
| Abrir app | Si (`SafeAppLauncher`) | Si | LOW | No | `SUPPORTED_SAFE` |
| Abrir ajustes de accesibilidad | Si (intent de sistema) | Si | LOW | No | `SUPPORTED_SAFE` |
| Abrir ajustes de app | Si (intent de sistema) | Si | LOW | No | `SUPPORTED_SAFE` |
| Abrir WhatsApp | Si (`SafeAppLauncher`) | Si | LOW | No | `SUPPORTED_SAFE` (via `OPEN_APP`) |
| Abrir Uber / Cabify | Si (`SafeAppLauncher`) | Si | LOW | No | `SUPPORTED_SAFE` (via `OPEN_APP`) |
| Abrir busqueda dentro de app | No de forma directa | No todavia | MEDIUM | Si | `INSTRUMENTED_TEST_REQUIRED` (`FOCUS_FIELD`) |
| Preparar texto en memoria | Si | Si | LOW | No | `SUPPORTED_SAFE` |
| Preparar guion de audio en memoria | Si | Si | LOW | No | `SUPPORTED_SAFE` |
| Preparar query de busqueda en memoria | Si | Si | LOW | No | `SUPPORTED_SAFE` |
| Escribir texto en app externa | No | No todavia | HIGH | Si | `INSTRUMENTED_TEST_REQUIRED` |
| Enfocar campo | No | No todavia | MEDIUM | Si | `INSTRUMENTED_TEST_REQUIRED` |
| Hacer scroll | No | No todavia | MEDIUM | Si | `INSTRUMENTED_TEST_REQUIRED` |
| Volver atras | No de forma segura | No (necesita accion global) | MEDIUM | Si | `UNSUPPORTED_NEEDS_RESEARCH` |
| Tocar boton | No | No | HIGH | - | `BLOCKED_DANGEROUS` |
| Enviar mensaje | No | No | CRITICAL | - | `BLOCKED_SENSITIVE` |
| Grabar / enviar audio | No | No | CRITICAL | - | `BLOCKED_SENSITIVE` |
| Pedir viaje | No | No | CRITICAL | - | `BLOCKED_SENSITIVE` |
| Confirmar pago / compra / viaje | No | No | CRITICAL | - | `BLOCKED_DANGEROUS` |
| Llamar | Flujo legacy aparte | No para tareas | HIGH | - | `BLOCKED_DANGEROUS` |
| Leer pantalla sensible | No | No (se redacta) | HIGH | - | bloqueada por `AgentTaskScreenObserver` |
| Dictar estado de tarea | Si | Si | LOW | No | `SUPPORTED_SAFE` (`READ_TASK_STATE`) |

## Que puede hacer Estela hoy

- Abrir una app soportada (WhatsApp, Uber, Cabify, ajustes) con un intent de
  launcher. Nunca toca nada adentro.
- Preparar en su propia memoria: texto de mensaje, guion de audio, query de
  busqueda. Ya saneados, sin datos sensibles.
- Dictar el estado de la tarea y un resumen seguro y redactado de la pantalla.

## Que prepara (pero no ejecuta)

- El contenido de un mensaje o el guion de un audio: queda listo en memoria,
  no se escribe en la app externa, no se envia.
- La busqueda de un chat: queda lista la query, no se escribe en WhatsApp.

## Que bloquea

- Tocar botones, hacer click, gestos, acciones globales.
- Escribir texto en apps externas.
- Enviar mensajes, grabar o enviar audios.
- Pedir viajes, confirmar pagos, compras o viajes, tocar el metodo de pago.
- Llamar, borrar, transferir, comprar.
- Leer datos sensibles completos de la pantalla.

## Que requiere instrumented test antes de habilitarse

- `FOCUS_FIELD` -- enfocar un campo dentro de una app externa.
- `WRITE_TEXT_EXTERNAL_APP` -- escribir texto en una app externa.
- `SCROLL` -- hacer scroll dentro de una app externa.

Estas tres podrian habilitarse en un modo controlado en el futuro, pero solo
despues de instrumented tests en Android real / emulador que confirmen que se
pueden hacer sin disparar acciones peligrosas.

## Por que no puede enviar, pagar ni pedir viaje todavia

Son acciones irreversibles o con costo real para una persona ciega: un viaje
pedido por error, un audio enviado sin querer, un pago equivocado. No alcanza
con una confirmacion por voz: hace falta una **capa de seguridad dedicada**
(confirmacion fuerte + auditoria + verificacion de pantalla) que todavia no
existe. Hasta entonces, estas acciones se proponen y se preparan, pero NUNCA
se ejecutan, aunque el usuario confirme.

## Que hace Estela ante pedidos peligrosos

Responde de forma util, no inutil. Explica el limite y ofrece la alternativa
segura:

> Usuario: "Busca el chat de Sofi."
> Estela: "Puedo preparar la busqueda de Sofi y guiarte para abrirla, pero
> todavia no voy a escribir ni tocar WhatsApp automaticamente."

> Usuario: "Manda el audio."
> Estela: "No puedo enviar audios automaticamente en esta version. Puedo
> dejar el guion preparado y pedirte confirmacion antes de cualquier envio
> futuro."

> Usuario: "Pedi el Uber."
> Estela: "No puedo solicitar el viaje automaticamente en esta version. Puedo
> guiarte hasta la pantalla de confirmacion y ayudarte a revisar precio,
> destino y forma de pago."

## Arquitectura

Capa pura en `com.ojoclaro.android.agent.task.capability`:

| Clase | Rol |
| --- | --- |
| `AgentActionCapabilityType` | Las 18 acciones auditadas |
| `AgentActionCapabilityDecision` | 6 decisiones de auditoria |
| `AgentActionCapabilityRisk` | LOW / MEDIUM / HIGH / CRITICAL |
| `AgentActionCapabilityRequirement` | Que hace falta para habilitarla |
| `AgentActionCapability` | Capacidad auditada (tipo valor inmutable) |
| `AgentActionCapabilityRegistry` | Tabla auditada, fuente unica de verdad |

`AgentSafeExecutionGate` consulta el registry: mapea el tipo de propuesta a
una capacidad y, si la capacidad no es `SUPPORTED_SAFE`, baja la decision a
confirmar, preparar o bloquear.

## Proximos pasos (Paquete 6H)

- Instrumented tests en Android real / emulador.
- Smoke test de WhatsApp / Uber sin acciones peligrosas.
- Decidir si `FOCUS_FIELD` o `WRITE_TEXT_EXTERNAL_APP` pueden existir en un
  modo controlado, con instrumented test previo.
- Nunca enviar, pagar ni pedir viaje sin confirmacion fuerte y una capa de
  seguridad dedicada.
