# WhatsApp Full Control — Safety Model

Estado: **fundación implementada (local, sin commit/push)**. Rama `chore/unattended-hardening-sprint`.
Principio: **todo lo peligroso arranca DESACTIVADO**. Nada real (enviar / llamar /
videollamar / audio) se ejecuta sin un cambio deliberado de feature flag + autorización.
Documento sanitizado (sin números reales, contenido de chats, tokens, logs privados ni APKs).

## 1. Matriz de capacidades

Catálogo declarativo: `agent/runtime/whatsapp/WhatsAppActionCatalog.kt`
(`actionType / riskLevel / requiredConfirmation / requiredConfidence / destinationRequired / logMarker`).

| Acción | Riesgo | Confirmación | Destino | Estado |
|---|---|---|---|---|
| Leer chats / mensajes, describir estado, repetir, ayuda, listar opciones | SAFE | — | no | **Habilitado** |
| Abrir chat por contacto/deep link | MEDIUM | — (navegación) | sí, confianza ALTA | **Habilitado** (blind-first) |
| Scroll | MEDIUM | — | no | Habilitado |
| Preparar / limpiar borrador | MEDIUM | simple | sí, ALTA | **Dry-run** (no envía) |
| Enviar mensaje real | HIGH | doble FUERTE | sí, ALTA | **Flag `realSendEnabled=false`** |
| Grabar audio / enviar audio | HIGH | doble FUERTE | sí, ALTA | **Flags `audioRecordEnabled` / `audioSendEnabled=false`** + sin gesto real |
| Llamada de voz | HIGH | doble FUERTE | sí, ALTA | **Flag `callEnabled=false`** (sin tap real cableado) |
| Videollamada | HIGH | doble FUERTE | sí, ALTA | **Flag `videoCallEnabled=false`** (tap gateado) |
| Borrar / archivar / silenciar / fijar chat, bloquear / reportar / pagos / stickers / archivos / fotos / reenviar / compartir ubicación / abrir enlaces sospechosos | FORBIDDEN | — | — | **Bloqueado siempre** (sin flag que lo habilite) |

## 2. Feature flags (todo en `false` por defecto)

`agent/runtime/whatsapp/WhatsAppFeatureFlags.kt` → `WhatsAppFeatureFlags.DISABLED`.
El servicio usa `whatsAppFlags = WhatsAppFeatureFlags.DISABLED` (runtime, no se persiste,
vuelve a DISABLED al arrancar; **no se cambia por voz**).

- `realSendEnabled` → tocar el botón ENVIAR de texto.
- `audioRecordEnabled` / `audioSendEnabled` → grabar / enviar audio.
- `callEnabled` → iniciar llamada de voz.
- `videoCallEnabled` → iniciar videollamada.

Mientras el flag esté en `false`, aunque el usuario confirme, **no se toca nada** y Estela
dice: *"El envío/las llamadas/los audios … están desactivados en esta versión de prueba."*

## 3. Confirmaciones requeridas

`WhatsAppStrongConfirmPhrases` define las frases FUERTES EXACTAS por acción
(siempre terminadas en "ahora"); un "sí"/"dale" a secas JAMÁS confirma:

- Enviar: `mandalo ahora` / `enviar ahora` / `envialo ahora`.
- Llamar: `llamar ahora`.
- Videollamar: `videollamar ahora`.
- Audio: `mandar audio ahora`; grabar: `empezar grabación` / `terminar grabación`.

Flujo real (cuando un flag se habilite, fuera de este sprint): destino confianza ALTA →
lectura de vuelta → confirmación 1 ("¿querés enviarlo?") → confirmación 2 con destino →
**sólo la frase fuerte exacta** ejecuta. Si el destino no está confirmado: no se escribe,
no se llama, no se graba (regla 16).

## 4. Contadores auditables

`agent/runtime/whatsapp/WhatsAppActionAudit.kt` — contadores thread-safe sin PII:
`sendTap`, `callTap`, `videoCallTap`, `audioSendTap`, `audioRecord`, `blocked`.
Se incrementan en los call-sites reales y en cada bloqueo; `redactedSummary()` se loguea.
En este sprint, por diseño, **todos los *Tap quedan en 0** (los flags están en false).

## 5. Memoria/contexto conversacional

`agent/runtime/whatsapp/WhatsAppConversationContext.kt` — memoria LOCAL en runtime,
REDACTADA: `chatLabelRedacted` (nunca un número), `phoneEnding` (últimos 4), `lastUserIntent`,
`pendingAction`, `lastDraftLen` (longitud, no texto), `lastMessageCount`, resumen sanitizado.
Defensa: cualquier corrida de 7+ dígitos se reemplaza por `[número]`. No persiste, no red, no LLM.
Comandos: "de qué estábamos hablando", "qué le iba a mandar", "olvidá el contexto",
"limpiá la memoria de WhatsApp".

## 6. Límites de WhatsApp (Android/accesibilidad)

- **Audio**: grabar exige un gesto continuo (mantener el micrófono); la automatización de
  gestos está prohibida por contrato → el envío de audio es **guiado**, no automatizado.
- **Llamada de voz**: no hay tap real cableado (sólo videollamada lo tiene); la acción se
  reconoce y se bloquea por flag, pero no existe ejecución.
- **wa.me a número propio** no abre el self-chat de forma confiable (limita el QA del open).
- Si WhatsApp no expone nodos scrollables, Estela lo dice por voz en vez de tocar a ciegas.

## 7. Fixtures de QA requeridos

- Contacto QA controlado por el usuario, **reachable por wa.me**, runtime/local, logs redactados
  (`phoneLen`/`ending`), nunca en repo/docs.
- Habilitar un flag para un smoke de envío real es **autorización explícita + modo de prueba**.

## 8. Privacidad / logging

- Nunca número completo ni contenido de chat en logs (sólo longitudes / marcadores / endings).
- El contenido al TTS sí (es la función para el usuario); a backend/LLM **no** sin diseño + consentimiento.
- "resumime esta conversación": por defecto NO manda contenido al LLM (queda como diseño
  con redacción/consentimiento explícito).

## 9. Comandos soportados (nuevos/endurecidos)

- HIGH-RISK por contacto, ruteados antes del LLM y **bloqueados por flag**:
  "llamá a X por WhatsApp", "mandale un audio a X".
- Contexto: recuerdo/olvido (sección 5).
- (Existentes endurecidos) envío de texto y videollamada: ahora **gateados por flag + contador**.

## 10. Próximos pasos

1. Cablear el flujo de confirmación FUERTE de dos pasos al envío real (cuando se habilite el flag).
2. Diseño completo de audio (grabar/escuchar/enviar) detrás de flags, sin gestos prohibidos.
3. `resumime` local extractivo (sin LLM) o con redacción + consentimiento.
4. Smoke físico con fixture QA (sin acciones reales; *Tap = 0).

No incluye: números reales, contenido de chats, tokens, logs privados, APKs ni rutas personales innecesarias.
