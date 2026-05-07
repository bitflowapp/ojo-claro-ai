# Fix de demo: botón "DESCRIBIR" oculto en Home

Fecha: 2026-05-06
Estado: aplicado, build verde, 408/408 tests verdes, APK regenerado.

---

## 1. Qué decía antes

En `androidApp/src/main/java/com/ojoclaro/android/ui/home/HomeScreen.kt` la Home tenía un botón primario blanco que decía literalmente:

- texto visible: **`DESCRIBIR`** (28 sp, FontWeight.Black)
- `contentDescription`: **"Describir lo que tengo enfrente."**
- `onClick`: `viewModel.submitVoiceText("describir que tengo enfrente")`

Ese comando llegaba al `AssistantOrchestrator`, se mapeaba a `AiTask.DESCRIBE_SCENE`
(`AssistantOrchestrator.kt:1414`) y la respuesta venía de
`LocalRuleBasedAiProvider.describeSceneFallback()`:

> "Para describir lo que tenés enfrente, todavía necesito IA avanzada. Por ahora puedo ayudarte con lectura local y acciones básicas."

El problema de demo: el **botón** prometía descripción visual con su nombre y su `contentDescription`, pero la capacidad real no existe (no hay IA cloud conectada). El usuario, vidente o no vidente, lee/escucha "DESCRIBIR" / "Describir lo que tengo enfrente" y espera una capacidad que la app no entrega. La respuesta hablada es honesta, pero llega después de que la promesa visual ya se hizo.

---

## 2. Qué cambié

Una sola edición en `HomeScreen.kt`. Eliminé el bloque del `Button` "DESCRIBIR" y lo reemplacé por un comentario que documenta la decisión y referencia este informe. El bloque quedó así:

```kotlin
// DESCRIBIR oculto a propósito hasta que exista descripción visual real con IA avanzada.
// El comando por voz "describir que tengo enfrente" sigue funcionando y devuelve la
// respuesta honesta de LocalRuleBasedAiProvider.describeSceneFallback().
// Ver docs/DESCRIBIR_BUTTON_DEMO_FIX.md.
```

El resto del archivo no se tocó:

- `SecondaryActionButton("Leer texto", ...)` sigue intacto.
- `SecondaryActionButton("¿Qué puedo decir?", ...)` sigue intacto.
- `SecondaryActionButton("Callar", ...)` sigue intacto.
- `SecondaryActionButton("Escuchar", ...)` sigue intacto cuando aplica.
- Imports: ninguno quedó huérfano (`Button`, `ButtonDefaults`, `RoundedCornerShape`, `Color.White/Black`, `heightIn`, `semantics{}`, `FontWeight.Black` se siguen usando en otros bloques de la pantalla).

### Lo que NO cambié (a propósito)

- **No** toqué `LocalRuleBasedAiProvider.describeSceneFallback()`. La respuesta honesta hablada queda intacta.
- **No** toqué `AssistantOrchestrator` ni el mapeo `describir → AiTask.DESCRIBE_SCENE`. Si el usuario dice "describir" por voz, sigue obteniendo la respuesta de "todavía necesito IA avanzada".
- **No** toqué OCR (`TextScanScreen`, `TextRecognitionAnalyzer`, `StableTextDetector`).
- **No** toqué WhatsApp, llamadas, mapas, memoria, RiskDetector, PrivacyGuard, ConsentManager.
- **No** toqué tests existentes. `LocalRuleBasedAiProviderTest.describeSceneFallbackMentionsAi` sigue pasando.
- **No** conecté IA cloud.
- **No** implementé descripción visual.
- **No** modifiqué seguridad ni reglas de confirmación estricta.
- **No** cambié `OnboardingState` ("describir acciones básicas" ya es una frase honesta — no promete visión).

---

## 3. Por qué (Opción A en vez de Opción B)

El brief permitía dos opciones:

- **Opción A**: ocultar el botón.
- **Opción B**: renombrarlo a "Pedir ayuda" o "Leer texto" si el flujo coincide.

Elegí **Opción A** porque las dos variantes de Opción B duplicaban botones existentes:

- **"Leer texto"** ya existe como `SecondaryActionButton` y abre el OCR de cámara real. Renombrar el botón visual blanco a "Leer texto" creaba dos botones con el mismo label apuntando a flujos distintos — peor UX que ocultarlo.
- **"Pedir ayuda"** es esencialmente lo que hace el botón **"¿Qué puedo decir?"** (llama a `viewModel.requestHelp()`). Renombrar a "Pedir ayuda" tampoco aporta nada nuevo y confunde la jerarquía.

Además, ocultar es la opción **menos riesgosa** para una demo: una capacidad que no existe deja de prometerse en pantalla y deja de ser tocable por accidente. El comando por voz "describir que tengo enfrente" sigue siendo manejable: si alguien lo dice, la respuesta hablada es honesta ("todavía necesito IA avanzada"), no promete falsamente y no rompe nada.

Cuando exista IA visual real con consentimiento explícito y costos resueltos, se vuelve a agregar el botón con el nombre y contentDescription que correspondan.

---

## 4. Tests

### `:androidApp:testDebugUnitTest`

```
BUILD SUCCESSFUL in 1m 15s
```

Conteo agregado de los XML en `androidApp/build/test-results/testDebugUnitTest/`:

```
TOTAL tests: 408   failures: 0   errors: 0
```

Tests relevantes que siguen pasando sin cambios:

- `LocalRuleBasedAiProviderTest.describeSceneFallbackMentionsAi` — confirma que la respuesta hablada para `AiTask.DESCRIBE_SCENE` sigue conteniendo "IA avanzada".
- `LocalRuleBasedAiProviderTest.helpResponseListsCommands` — el centro de ayuda sigue listando comandos.
- `AssistantOrchestratorTest` (toda la suite) — orquestación intacta.
- `CommandRouterTest`, `PrivacyGuardTest`, `RiskDetectorTest`, `MemoryPolicyTest`, `ConsentManagerTest`, `VoiceCommandControllerTest`, etc. — sin tocar.

### `:androidApp:assembleDebug`

```
BUILD SUCCESSFUL in 13s
```

Una sola advertencia preexistente, no introducida por este cambio:

```
w: ...HomeScreen.kt:81:26 'val LocalLifecycleOwner: ProvidableCompositionLocal<LifecycleOwner>' is deprecated.
   Moved to lifecycle-runtime-compose library in androidx.lifecycle.compose package.
```

### `:shared:allTests`

```
BUILD SUCCESSFUL in 2s
```

Sin cambios en el módulo `shared`.

---

## 5. APK final

Ruta:

```
C:\Users\marco\Desktop\ojo_claro_ai_accessibility_mvp\androidApp\build\outputs\apk\debug\androidApp-debug.apk
```

Tamaño observado: **57.368.695 bytes (≈57 MB)**.

---

## 6. Verificación visual recomendada

Antes de la próxima demo, en el device físico:

1. Instalar la APK regenerada.
2. Abrir Home y verificar que **no aparece** el botón blanco grande "DESCRIBIR".
3. Verificar que **sí siguen apareciendo**: "Escuchar" (cuando corresponde), "Leer texto", "¿Qué puedo decir?" y "Callar" fijo abajo.
4. Decir por voz "describir que tengo enfrente" y confirmar que la respuesta hablada incluye "todavía necesito IA avanzada" (comportamiento intacto).
5. Confirmar con TalkBack activo que ya no se anuncia "Describir lo que tengo enfrente." al recorrer la Home.

Si todo lo anterior pasa, la Home ya no promete una capacidad que la app no tiene.

---

## 7. Próximo paso

Cuando se decida activar IA visual real:

- Definir proveedor cloud, política de costos y consent hablado.
- Implementar el flujo end-to-end (`AiTask.DESCRIBE_SCENE` con un provider real, no `LocalRuleBasedAiProvider`).
- Recién ahí re-incorporar el botón en Home, con el contentDescription que corresponda.
- Antes de exponerlo, sumar tests que cubran el camino con consent activo y rechazo del usuario.
