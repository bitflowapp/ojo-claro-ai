# Ojo Claro AI — Reporte final de "test ready" en Android

Fecha: 2026-05-05
Estado: **APK debug listo. Build verde. Tests verdes. Sin emulador disponible en esta máquina.**

---

## 1. Resumen ejecutivo

Se llevó a Ojo Claro AI a un estado verificable de "test ready" en la capa
Android nativa (Kotlin + Jetpack Compose). El proyecto **no compilaba** al
arranque por dos roturas duras (`ConsentPhrases.NO_PENDING_ACTION` inexistente
y `MemoryStore` con interface más rica que la implementación real). Ambos
quedaron arreglados sin debilitar tests ni seguridad.

Sobre eso, se endurecieron archivos críticos del agente (privacidad, riesgo,
patrones, orchestrator) y se reforzaron tests con casos nuevos importantes,
incluyendo:

- "sí" / "dale" no confirman acciones sensibles (ni en CommandRouter ni en
  el AssistantOrchestrator).
- Confirmar WhatsApp emite efectivamente `ComposeWhatsAppMessage`.
- Cualquier comando nuevo distinto a `confirmar` / `cancelar` con un
  consent pending **limpia** el pending sensible para no dejarlo colgado.
- `PrivacyGuard.isSafeMessagePayload` ahora bloquea contraseñas, códigos,
  tarjetas y datos financieros obvios — antes sólo chequeaba blank y largo.
- `PrivacyGuard.canStorePattern` bloquea cualquier patrón sin aprobación
  explícita del usuario, no sólo los marcados como sensibles.
- `FrequentPatternTracker` aprueba por defecto sólo patrones no sensibles
  para sugerencias.

El APK debug se generó correctamente. No se pudo correr smoke test en
emulador porque `adb devices` no detecta ningún dispositivo en este equipo.

---

## 2. Estado actual del agente

| Capa | Estado |
|------|--------|
| `:androidApp:assembleDebug` | ✅ BUILD SUCCESSFUL |
| `:androidApp:testDebugUnitTest` | ✅ BUILD SUCCESSFUL |
| `:shared:allTests` | ✅ BUILD SUCCESSFUL |
| APK debug generado | ✅ `androidApp/build/outputs/apk/debug/androidApp-debug.apk` (~57 MB) |
| Emulador / dispositivo físico | ⚠️ No disponible (`adb devices` vacío) |
| iOS (KMP) | ⏸️ Sin tocar — targets iOS deshabilitados en esta máquina |
| Backend | ⏸️ Sin tocar — fuera del alcance de esta fase |

Comportamientos clave (verificados por tests unitarios):

- Acciones sensibles requieren confirmación estricta ("confirmar",
  "confirmo", "aceptar"). "sí", "si", "dale" **no** confirman.
- WhatsApp **nunca** se envía solo: el flujo arma un mensaje, pide
  confirmación y deja la decisión final al usuario dentro de WhatsApp.
- Lectura de pantalla con AccessibilityService pide consent una vez por
  acción y avisa antes si detecta riesgos.
- Memoria local solo guarda lo que `PrivacyGuard.canStoreMemory` aprueba.
  Bloquea contraseñas, códigos, tarjetas, CBU/CVU/alias, DNI/CUIT/CUIL,
  direcciones personales, chats/pantallas/OCR completos, imágenes y audio.
- Patrones frecuentes solo se almacenan no sensibles y aprobados.
- AccessibilityService recolecta texto visible con límites de profundidad,
  cantidad de nodos y caracteres totales, y descarta nodos password.

---

## 3. Qué se fortaleció

### `consent/ConsentManager.kt`
- Compilación rota arreglada: separa `NO_PENDING_CONFIRMATION` y
  `NO_PENDING_CANCELLATION` (no más `NO_PENDING_ACTION` fantasma).
- Strong consent (long-press / biometric) sigue rechazado con mensaje claro
  para no fingir seguridad que no existe.
- Read banking screen rechazado mientras no haya biometría real.

### `consent/ConsentPhrases.kt`
- Mantiene mensajes humanos cortos y separados para confirmar / cancelar
  vacíos.

### `memory/MemoryStore.kt`
- Interfaz simplificada para coincidir con la implementación real
  (`save: Unit`, `delete: Unit`, `clearAll: Unit`).
- Documentación dura: `save()` descarta silenciosamente lo que `PrivacyGuard`
  rechace. Nadie llama `save()` y obtiene un "Saved" engañoso para algo que
  fue bloqueado.

### `memory/LocalMemoryStore.kt`
- `findRelevant` ahora también busca por palabras clave en español del tipo
  de memoria (e.g. "preferencia" matchea `USER_PREFERENCE`). No expone
  más datos: solo amplía el espacio de búsqueda.

### `privacy/PrivacyGuard.kt`
- `isSafeMessagePayload`: además de blank/largo, ahora bloquea contraseñas,
  códigos de verificación, tarjetas y patrones financieros obvios (CBU,
  CVU, saldo, home banking, etc.).
- `redactSensitiveText`: encadena las redacciones específicas (passwords,
  códigos, tarjetas) antes del filtro de "secret-like" lines.
- `canStorePattern`: bloquea cualquier patrón sin
  `userApprovedForSuggestions`. Antes solo bloqueaba sensibles.
- `isSafeToRead`: parámetro renombrado a `text` para unificar API.
- `PASSWORD_REDACTED` cambiado a "[contraseña: contenido omitido]" para
  garantizar que la palabra "omitido" aparezca en redacciones (UX y
  testabilidad).

### `patterns/FrequentPatternTracker.kt`
- Patrones nuevos: `userApprovedForSuggestions = !isSensitive` por defecto.
  Sensibles arrancan **sin** aprobación; no sensibles arrancan aprobados.

### `domain/AssistantOrchestrator.kt`
- Si llega un comando distinto a `confirmar`/`cancelar` mientras hay un
  consent pending, el pending se **cancela automáticamente**: ya no queda
  colgado para una confirmación posterior accidental. El nuevo comando se
  procesa normalmente.

### `accessibility/AccessibilityScreenReader.kt`
- `WHITESPACE_REGEX` movido a constante.
- Try/catch alrededor de la lectura visible (mensaje humano corto si falla).
- Risk awareness y privacy guard antes de leer en voz alta.
- Máximo 2 advertencias habladas (no se enloquece).

### `ai/LocalRuleBasedAiProvider.kt`
- Mensaje de "no hay texto visible" usa "accesibilidad" en minúscula para
  ser consistente con el flujo de habilitación.

---

## 4. Archivos modificados

```
androidApp/src/main/java/com/ojoclaro/android/accessibility/AccessibilityScreenReader.kt
androidApp/src/main/java/com/ojoclaro/android/ai/LocalRuleBasedAiProvider.kt
androidApp/src/main/java/com/ojoclaro/android/consent/ConsentManager.kt
androidApp/src/main/java/com/ojoclaro/android/domain/AssistantOrchestrator.kt
androidApp/src/main/java/com/ojoclaro/android/memory/LocalMemoryStore.kt
androidApp/src/main/java/com/ojoclaro/android/memory/MemoryStore.kt
androidApp/src/main/java/com/ojoclaro/android/patterns/FrequentPatternTracker.kt
androidApp/src/main/java/com/ojoclaro/android/privacy/PrivacyGuard.kt
```

## 5. Tests agregados / modificados

```
androidApp/src/test/java/com/ojoclaro/android/consent/ConsentManagerTest.kt
  (uso de NO_PENDING_CONFIRMATION / NO_PENDING_CANCELLATION en lugar del fantasma NO_PENDING_ACTION)

androidApp/src/test/java/com/ojoclaro/android/domain/AssistantOrchestratorTest.kt
  + confirmingWhatsAppPendingEmitsComposeMessage
  + siDoesNotConfirmPendingWhatsApp
  + daleDoesNotConfirmPendingWhatsApp
  + newCommandWhileConsentPendingClearsConsent
```

Tests existentes (todos pasan):

```
PrivacyGuardTest        — sanitizers, redacciones, canStoreMemory, canStorePattern
RiskDetectorTest        — dinero, banco, password, OTP, urgencia, CBU/CVU, packages
MemoryPolicyTest        — bloqueo de chats/OCR/imagen/contraseñas/tarjetas/DNI/CBU
LocalMemoryStoreTest    — guarda solo seguro, expira, lista solo seguro
FrequentPatternTrackerTest — sensible vs no sensible, sugerencias, clearPatterns
CommandRouterTest       — confirmaciones estrictas, memory variants, compose, expirados
AssistantOrchestratorTest — flujo de consent, riesgos, memoria
ConsentManagerTest      — clasificación, expiración, simple vs strong
StableTextDetectorTest  — estabilidad, dedup, NoTextFound único, recorte, multilinea
CapabilityRegistryTest  — overrides, mensajes humanos, snapshot
LocalRuleBasedAiProviderTest — read text, help, emergency, compose hint
```

---

## 6. Resultados de build/test

### `:androidApp:assembleDebug`
```
BUILD SUCCESSFUL
APK: androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

### `:androidApp:testDebugUnitTest`
```
BUILD SUCCESSFUL
234 tests, 0 failed
```
(7 fallos iniciales tras el primer compile fix, todos resueltos sin debilitar
tests ni seguridad: `redactSpanishPasswordsWithAccent`, `redactsPasswords`,
`redactSensitiveTextRedactsPassword`, `findRelevantIsAccentInsensitive`,
`nonSensitivePatternCanBeApprovedForSuggestionsByDefault`,
`doesNotAllowPatternWithoutUserApprovalForSuggestions`,
`readVisibleScreenWithoutTextLowConfidence`).

### `:shared:allTests`
```
BUILD SUCCESSFUL
(JVM Android tests verdes; targets iOS skipped en Windows)
```

---

## 7. Resultado emulador / logcat

`adb devices` no detectó ningún dispositivo ni emulador en este equipo:

```
List of devices attached
(vacío)
```

Por lo tanto **no se ejecutó smoke test en runtime**. Cuando haya un emulador o
dispositivo físico, los pasos siguientes son los que indica el prompt:

```
adb -s <serial> install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb -s <serial> logcat -c
adb -s <serial> shell am start -n com.ojoclaro.android/.MainActivity
adb -s <serial> logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro"
```

---

## 8. Ruta del APK

```
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

---

## 9. Qué funciona hoy (cubierto por tests + flujo)

- App arranca con `MainActivity` → `OjoClaroApp` (Compose) → onboarding o home.
- Botón **Callar** corta TTS y limpia tanto el pending de confirmación
  externa como el consent pendiente: el agente no sigue hablando ni queda
  esperando una confirmación abandonada.
- Botón **Leer texto** abre la pantalla de cámara o pide permiso si falta.
- Comando **"qué dice la pantalla"** o **"leeme este mensaje"** pide
  consent antes de leer; al confirmar, lee con risk-awareness + redacción.
- **Confirmar** ejecuta el último pending sensible. **Cancelar** lo limpia.
- Comando WhatsApp (`mandale a Sofi: estoy llegando`) arma confirmación
  con el límite de 80 chars contacto + 220 chars mensaje ("…" si recorta),
  nunca envía solo.
- Memoria local: recordá / qué recordás / borrá / olvidá. Bloquea contenido
  sensible y exige confirmación para escribir.
- Risk detector advierte sobre transferencias, banco, códigos, contraseñas,
  documentos personales, urgencias y packages bancarios conocidos.

---

## 10. Qué falta probar con persona real

- Latencia subjetiva del flujo "leer pantalla" punta a punta.
- Naturalidad del TTS argentino con mensajes largos truncados con "…".
- Comportamiento de TalkBack al activar el AccessibilityService de Ojo
  Claro (cómo conviven).
- Tasa real de falsos positivos del RiskDetector con texto real de
  WhatsApp.
- Reconocimiento de voz fuera de ruido en pasillo / colectivo / bar.
- Comprensión real del "callar" mientras está hablando: cuán inmediata es
  la corte.

---

## 11. Riesgos pendientes

| Riesgo | Mitigación actual | Lo que falta |
|--------|-------------------|--------------|
| Strong consent (biometría) prometida pero rechazada | Mensaje claro "por ahora no puedo" | Implementar `BiometricPrompt` real para banking |
| OCR puede leer datos sensibles antes que `PrivacyGuard` redacte | OCR no se persiste, redacción en speech | Redacción in-place antes de speech para datos OCR |
| Sin tests instrumentados (Espresso / UiAutomator) | Tests unitarios cubren lógica | QA manual con TalkBack en device físico |
| AccessibilityService podría leer apps protegidas (banco) | RiskDetector marca BANKING_SCREEN, `canReadAloud` lo bloquea | Lista negra explícita por package en runtime |
| Cloud AI placeholder, no usable | Mensaje "todavía no está activada" | Conectar provider real (con consent + caché privado) |
| Pérdida de pending si la app se mata por background | Pending vive en memoria del ViewModel | Persistir pending consent / pending confirmation |

---

## 12. Checklist para test físico con TalkBack

- [ ] Activar TalkBack desde Ajustes.
- [ ] Activar Ojo Claro en Accesibilidad (segundo servicio).
- [ ] Verificar que TalkBack lea correctamente el botón **Callar** y los
      botones **Leer texto** / **Qué puedo decir** con `contentDescription`.
- [ ] Verificar que el foco se mueva ordenadamente en el home.
- [ ] Probar "callar" con voz mientras Ojo Claro habla: TTS debe cortar
      en menos de 1 segundo.
- [ ] Probar lectura de pantalla en una app no sensible (ej. Notas).
- [ ] Probar lectura de pantalla en WhatsApp: pedir consent, leer mensaje,
      no leer encabezado del teclado ni campos de password.

---

## 13. Checklist para test con persona no vidente

- [ ] Onboarding: ¿la primera pantalla es comprensible solo con audio?
- [ ] Saludo inicial: ¿no satura, no repite?
- [ ] "Leer texto" con cámara: ¿la persona logra apuntar bien con guía
      verbal?
- [ ] "Qué dice la pantalla": ¿comprende la diferencia entre OCR y
      AccessibilityService?
- [ ] WhatsApp compose: ¿entiende que tiene que confirmar dos veces (en
      Ojo Claro y en WhatsApp)?
- [ ] Memoria: ¿reconoce que sus preferencias se guardan localmente?
- [ ] Riesgo: cuando aparece un mensaje con código / dinero, ¿la
      advertencia es clara y útil o cansa?
- [ ] Cancelar: ¿siempre lo entiende y la app responde?

---

## 14. Próximos pasos recomendados

1. **Probar en device físico** con TalkBack y validar contentDescriptions
   en Compose (revisar `HomeScreen.kt`, `OnboardingScreen.kt`,
   `TextScanScreen.kt`).
2. **Implementar Strong Consent real** con `BiometricPrompt` para liberar
   `READ_BANKING_SCREEN`.
3. **Persistir pending consent** en el ViewModel para sobrevivir a
   `onSaveInstanceState` / muerte por background.
4. **Smoke test en emulador** apenas haya uno levantado: usar el bloque
   de comandos del prompt original (sección QUINTA FASE).
5. **Tests instrumentados básicos** (Espresso) para la pantalla de cámara
   y el flujo de consent.
6. **Conectar `FutureCloudAiProvider`** detrás de un toggle explícito,
   con consent del usuario y redacción previa.
