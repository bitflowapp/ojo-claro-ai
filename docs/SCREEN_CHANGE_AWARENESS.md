# Screen Change Awareness — Paquete 5E

Capa de awareness controlada que detecta transiciones relevantes de pantalla y
permite a Ojo Claro emitir anuncios cortos, seguros y deduplicados a una
persona ciega. **No actúa**: nunca toca botones, nunca gesticula, nunca lanza
Intents. Solo describe lo que cambió.

## Qué anuncia

| Evento | Importancia | Ejemplo de texto |
|---|---|---|
| `APP_CHANGED` | NORMAL | "Cambiaste a WhatsApp." / "Cambiaste a Ajustes." |
| `PASSWORD_SCREEN_ENTERED` | CRITICAL | "Hay un campo de contraseña. No voy a decir su contenido." |
| `PAYMENT_OR_BANKING_SCREEN_ENTERED` | CRITICAL | "Esta parece una pantalla bancaria o de pago. No voy a leer datos sensibles." |
| `SENSITIVE_SCREEN_ENTERED` (OTP) | CRITICAL | "Veo un código o verificación. No voy a leerlo en voz alta." |
| `CHAT_SCREEN_ENTERED` | NORMAL | "Parece una pantalla de mensajes." |
| `FORM_SCREEN_ENTERED` | NORMAL | "Apareció un formulario. Podés pedirme \"qué hago ahora\" para orientarte." |
| `DIALOG_OR_ALERT_APPEARED` | HIGH | "Apareció una opción de confirmación." |
| `IMPORTANT_BUTTONS_CHANGED` | LOW | "Cambiaron las opciones disponibles." |
| `SCREEN_BECAME_EMPTY` | LOW | "Perdí la lectura de la pantalla." |
| `SCREEN_RESTORED` | LOW | "Volví a tener lectura de la pantalla." |

## Qué NO anuncia

- Cambios mínimos de texto (un valor cambió pero el package, los signals y los
  botones se mantienen).
- Recolecciones repetidas por throttle del `ScreenContextCollector`.
- Snapshots idénticos.
- `APP_CHANGED` dos veces seguidas al mismo `packageName` (memoria por
  package).
- Anuncios dentro del cooldown por `semanticKey` (default 15s; LOW noise 30s).
- Cualquier evento si el snapshot actual es vacío (sin texto + sin botones +
  sin editables + sin packageName).
- Cualquier evento si el flag `screenChangeAwarenessEnabled` está OFF.
- Cualquier evento si el flag `accessibilityRuntimeContextEnabled` está OFF
  (no llegan snapshots al repository).

## Cómo evita molestar

1. **Cooldown por `semanticKey`**: los eventos repetidos quedan suprimidos
   durante 15-30s. Sin re-emisión hasta que el cooldown expira.
2. **Importancia escalonada**: solo HIGH/CRITICAL emiten con `force = true`
   en el `_speechEvents` (bypassa el dedup literal de `SpeechController`).
   LOW/NORMAL respetan dedup del bajo nivel.
3. **Pending awareness**: si hay un pending de confirmación legacy o del
   bridge tipado, el handler **no anuncia** salvo que la importancia sea
   CRITICAL (safety warning).
4. **Package memory**: el engine recuerda el último package anunciado y no
   re-anuncia `APP_CHANGED` al mismo package.
5. **CRITICAL puede romper cooldown** solo si el `reasonKey` cambió (ej. de
   `password` a `banking`).

## Cómo trata pantallas sensibles

- **Hot zones detectadas via `StructuredScreenSnapshot.signals`**:
  - `hasPasswordField` → password warning,
  - `isBankingApp` o `hasPaymentOrTransferSignals` → banking/payment warning,
  - `hasVerificationCode` → OTP warning.
- **NUNCA cita contenido**: el texto del anuncio menciona la categoría, no el
  contenido del snapshot. No lee `redactedTextLines`, ni `focusedLabel`, ni
  button labels en safety warnings.
- **CRITICAL siempre**: las warnings de hot zone son CRITICAL y se hablan con
  `force = true`.
- **Independiente del riesgo de la pantalla previa**: una transición de
  pantalla no-hot a hot siempre dispara warning. La transición inversa NO
  dispara nada (el usuario salió de la zona caliente).

## Relación con TalkBack

Ojo Claro **no detecta TalkBack hoy**. La estrategia para evitar competir es:

- Anuncios LOW: una sola línea corta. Si TalkBack los lee primero, el dedup
  semántico evita repetición.
- Anuncios NORMAL: cortos, una línea, sin enumeración de elementos.
- Anuncios HIGH/CRITICAL: cortos pero priorizados. Pueden solapar TalkBack
  por diseño — el usuario necesita oír la advertencia de seguridad.

Pendiente para iteración futura: detectar `AccessibilityManager.isEnabled()` +
`isTouchExplorationEnabled` para bajar la verbosity cuando TalkBack está
activo. Ver `R4` del ranking 5E.

## Wiring runtime

```
OjoClaroRuntimeGraph
  └─ ScreenContextRepository
        ↓ (setOnPublishListener)
  └─ ScreenChangeAwarenessCoordinator
        ├─ ScreenChangeAwarenessEngine (puro)
        │     └─ ScreenChangeMemory (cooldown + último package)
        └─ MutableSharedFlow<ScreenChangeAnnouncement>
              ↓ (collect en viewModelScope)
        HomeViewModel.handleScreenChangeAnnouncement
              ├─ Verifica !hasLegacyPending || importance == CRITICAL
              └─ emitSpeechEvent(text, force = HIGH || CRITICAL)
```

- `install()` registra el listener en `screenRepository`.
- `tearDown()` desregistra + resetea memoria del coordinator.

## Feature flag

`AgentCoreFeatureFlags.screenChangeAwarenessEnabled`:
- Default OFF en producción.
- Encendido en `QA_PREVIEW`.
- Independiente de `accessibilityRuntimeContextEnabled`: ambos deben estar ON
  para que se emitan anuncios.

## Smoke test físico

Cosas a verificar en dispositivo real (paquete 6+):

1. **Cambio de app desde launcher → WhatsApp**: debe decir "Cambiaste a WhatsApp."
2. **Cambio a banca (Galicia / BBVA / Mercado Pago)**: debe decir
   "Esta parece una pantalla bancaria o de pago. No voy a leer datos sensibles."
   **No debe enumerar elementos de la pantalla.**
3. **Pantalla con campo de contraseña**: debe decir "Hay un campo de
   contraseña. No voy a decir su contenido."
4. **Pantalla de OTP / verificación**: debe decir el warning de código sin
   leer dígitos.
5. **Aparición de diálogo de permisos Android**: debe decir
   "Apareció una opción de confirmación."
6. **Volver de WhatsApp a Ajustes y volver a WhatsApp en < 15s**: la segunda
   vez NO debe re-anunciar WhatsApp (memoria por package).
7. **Mientras hay un pending de confirmación del bridge**: cambios LOW/NORMAL
   no deben interrumpir. CRITICAL sí.
8. **TalkBack ON + Ojo Claro ON**: medir si se solapan. Si LOW/NORMAL suenan
   redundantes, evaluar bajar verbosity en paquete futuro.

## Reglas duras cumplidas

- Sin Android APIs en engine + memory + announcement + event + importance.
- Sin `performClick`, `dispatchGesture`, `performGlobalAction`, `startActivity`.
- Sin mensajes enviados, pagos, transferencias, borrados, llamadas, compras.
- Sin lectura de datos sensibles en spokenText.
- Sin logging de textos de pantalla (la integración usa
  `_speechEvents` que NO incluye `redactedTextLines`).
- Sin claves hardcodeadas, sin APIs pagas.
- Legacy intacto si flag OFF.
- Degrada de forma segura si `AccessibilityService` está off (no llegan
  snapshots → no emisión).
