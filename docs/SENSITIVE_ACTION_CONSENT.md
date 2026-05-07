# Capa de consentimiento — Ojo Claro AI

Fecha: 2026-05-05  
Estado: integrado con AssistantOrchestrator para lectura visible, memoria local segura y borrado de memoria.

---

## Por qué existe esta capa

Una persona ciega no puede ver una pantalla de "¿estás seguro?" como visten otras apps. Necesita un asistente que:

- No haga acciones sensibles sin confirmar.
- No la asuste con confirmaciones constantes para todo.
- Le diga claramente qué está por hacer, en una sola frase.
- Le permita cancelar siempre.
- Nunca lea contraseñas. Nunca.
- Nunca prometa seguridad bancaria que no podemos garantizar.

`ConsentManager` clasifica acciones por sensibilidad y decide qué nivel de confirmación pedir. Es **stateless**: la UI/ViewModel guarda la acción pendiente.

---

## Tipos de acción (`SensitiveActionType`)

| Tipo | Cuándo aplica |
|------|---------------|
| `OPEN_EXTERNAL_APP` | Abrir WhatsApp u otra app externa sin componer mensaje |
| `COMPOSE_MESSAGE` | Preparar (no enviar) un mensaje de WhatsApp |
| `READ_VISIBLE_MESSAGE` | Leer la pantalla cuando puede contener mensajes privados |
| `SAVE_MEMORY` | Guardar una preferencia o regla local segura |
| `DELETE_MEMORY` | Olvidar un recuerdo local específico |
| `CLEAR_MEMORY` | Borrar toda la memoria local |
| `READ_BANKING_SCREEN` | Pantalla detectada como bancaria |
| `READ_PASSWORD_FIELD` | Cualquier intento de leer un nodo `isPassword` |
| `UNKNOWN_SENSITIVE` | Acción que pide IA futura no clasificada todavía |

---

## Niveles de consentimiento (`ConsentLevel`)

| Nivel | Implementado | Cuándo |
|-------|--------------|--------|
| `NONE` | ✅ | Acciones cotidianas (abrir app, OCR de papel) |
| `SIMPLE_CONFIRMATION` | ✅ | "Decí confirmar" o tap accesible |
| `LONG_PRESS_CONFIRMATION` | ❌ Preparado | Para futuras acciones sensibles que no requieran biometría |
| `BIOMETRIC_CONFIRMATION` | ❌ Preparado | Pantallas bancarias y similares |

**Decisión deliberada:** las dos confirmaciones fuertes están en el modelo pero `requestAction()` las **rechaza** explícitamente con un mensaje claro. Mejor decir "no puedo hacer eso de forma segura" que fingir seguridad biométrica que no implementamos.

---

## Tabla de clasificación

| Acción | Nivel | Comportamiento de `requestAction` |
|--------|-------|-----------------------------------|
| `OPEN_EXTERNAL_APP` | NONE | `AllowedImmediately` |
| `COMPOSE_MESSAGE` | SIMPLE | `NeedsConfirmation` con `pending` |
| `READ_VISIBLE_MESSAGE` | SIMPLE | `NeedsConfirmation` con `pending` |
| `SAVE_MEMORY` | SIMPLE | `NeedsConfirmation` con `pending` |
| `DELETE_MEMORY` | SIMPLE | `NeedsConfirmation` con `pending` |
| `CLEAR_MEMORY` | SIMPLE | `NeedsConfirmation` con `pending` |
| `READ_BANKING_SCREEN` | BIOMETRIC | `Rejected` con frase específica |
| `READ_PASSWORD_FIELD` | NONE (rechazo absoluto) | `Rejected` siempre |
| `UNKNOWN_SENSITIVE` | SIMPLE | `NeedsConfirmation` por defecto |

---

## Frases canónicas (`ConsentPhrases`)

Centralizadas para que el tono sea consistente:

- `READ_VISIBLE_MESSAGE`: *"Voy a leer texto visible de la pantalla. No lo guardo ni lo envío. Confirmá para continuar."*
- `composeMessage(contact, message)`: *"Voy a preparar un mensaje para Sofi que dice: estoy llegando. No lo envío automáticamente. Confirmá para continuar."*
- `READ_BANKING_SCREEN`: *"Esta pantalla puede tener datos privados. Para continuar, usá la seguridad del teléfono."*
- `READ_PASSWORD_FIELD_REJECTED`: *"No puedo leer campos de contraseña. Eso es por seguridad."*
- `EXPIRED_ACTION`: *"La acción pendiente venció. Volvé a pedirla."*
- `NO_PENDING_ACTION`: *"No hay ninguna acción pendiente para confirmar."*
- `ACTION_CANCELLED`: *"Acción cancelada."*
- `SAVE_MEMORY_GENERIC`: *"Voy a recordar esto. Confirmá para guardar."*
- `MEMORY_SAVED`: *"Listo. Lo voy a recordar."*
- `MEMORY_SAVE_CANCELLED`: *"Cancelado. No guardé nada."*
- `CLEAR_MEMORY_CONFIRM`: *"Voy a borrar mi memoria local. Confirmá para continuar."*
- `MEMORY_CLEARED`: *"Listo. Borré mi memoria local."*

---

## Flujo de uso futuro

```
Usuario:  "Mandale a Sofi: estoy llegando."
                  │
                  ▼
        AssistantOrchestrator
                  │
                  ▼
   ConsentManager.requestAction(
     COMPOSE_MESSAGE,
     ConsentPhrases.composeMessage("Sofi", "estoy llegando"),
     payload = mapOf("contact" to "Sofi", "message" to "estoy llegando"))
                  │
                  ▼
        NeedsConfirmation(pending=…)
                  │
                  ▼
   ViewModel guarda `pending`, habla `spokenText`,
   transiciona a WAITING_CONFIRMATION
                  │
                  ▼
Usuario:  "Confirmar."
                  │
                  ▼
   ConsentManager.confirmSimple(pending, now)
                  │
                  ▼
        Confirmed(pending) → ViewModel ejecuta la acción
        (en este caso: WhatsAppIntentHelper.composeMessage)
```

---

## Reglas que debe seguir el orquestador cuando se integre

1. **Nunca** leer una pantalla de WhatsApp sin pasar por `requestAction(READ_VISIBLE_MESSAGE)`.
2. **Nunca** componer un mensaje sin pasar por `requestAction(COMPOSE_MESSAGE)`.
3. **Siempre** rechazar lectura de password fields antes de tocarlos (`PrivacyGuard.isSafeToRead` en el AccessibilityService ya lo hace).
4. **Siempre** habilitar el botón Callar — si Callar se toca con un pending, llamar a `cancel()` y limpiar el pending.
5. **Nunca** ejecutar una acción `Confirmed` sin haber recibido un explanation hablado primero.

---

## Tests

`androidApp/src/test/java/com/ojoclaro/android/consent/ConsentManagerTest.kt` — 13 tests.

Cubre:
- Acciones que no requieren confirmación.
- Acciones que requieren confirmación simple.
- Rechazo absoluto de password fields.
- Rechazo de pantallas bancarias hasta que haya biometría.
- Confirmar sin pendiente.
- Confirmar pendiente vencido.
- Confirmar pendiente válido.
- Cancelar sin pendiente.
- Cancelar con pendiente.
- Expirar acciones viejas.
- `UNKNOWN_SENSITIVE` por defecto pide confirmación.
- Helper `composeMessage()` formatea bien.

---

## Lo que falta

- Implementar BiometricPrompt cuando se decida que vale la pena agregar la dependencia.
- Detectar pantallas bancarias por `targetAppPackage` (lista de bancos conocidos) — hoy es un placeholder.
- Reflejar el `pending` de consent en la UI con un afirmativo claro al volver de WAITING_CONFIRMATION.

---

## Actualización 2026-05-05

- `AssistantOrchestrator` ya usa `ConsentManager` para `READ_VISIBLE_MESSAGE`.
- Guardar memoria usa `SAVE_MEMORY` y solo persiste al confirmar.
- Borrar memoria usa `CLEAR_MEMORY` y solo borra al confirmar.
- `PrivacyGuard` bloquea memoria sensible y lectura fuerte de códigos, bancos y contraseñas.
- `RiskDetector` agrega avisos antes de texto sensible.
