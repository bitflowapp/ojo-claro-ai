# Plan de preparación para producto — Ojo Claro AI

Fecha: 2026-05-04

---

## 1. Qué falta para producto serio

| Área | Estado | Bloqueante para prod? |
|------|--------|-----------------------|
| Onboarding accesible | ✅ Implementado | No (mejora UX) |
| Centro de ayuda de voz | ✅ Implementado | No |
| Confirmaciones seguras | ✅ Implementado | Sí |
| CapabilityRegistry | ✅ Implementado | Sí (mensajes claros) |
| AssistantOrchestrator | ✅ Implementado | Sí (arquitectura) |
| PrivacyGuard | ✅ Implementado | Sí |
| FutureCloudAiProvider | ✅ Placeholder | No |
| IA cloud real (DESCRIBIR) | ❌ Pendiente | Para feature central |
| Login / cuentas | ❌ Excluido | No (MVP) |
| Política de privacidad publicada | ❌ Pendiente | Sí (Google Play) |
| Tests instrumentales de UI | ❌ Pendiente | No |
| Validación con usuarios ciegos reales | ❌ Pendiente | Sí |
| APK firmado para producción | ❌ Pendiente | Sí (distribución) |
| Eliminar `usesCleartextTraffic` | ❌ Pendiente | Sí (HTTPS en prod) |

---

## 2. Qué falta para publicar en Play Store

1. Cuenta de desarrollador Google Play activa.
2. Política de privacidad publicada en URL pública, aceptada por el usuario al onboarding.
3. APK firmado con keystore de producción (`release`).
4. `usesCleartextTraffic="false"` en producción (backend con HTTPS).
5. Declaración de uso de `AccessibilityService` con justificación para revisión de Google.
6. Iconos y assets de la app (launcher icon, feature graphic).
7. Descripción en Play Store en español.
8. Pruebas mínimas en alpha/closed testing antes de producción.
9. Remover `android:debuggable="true"` implícito del APK de release.

---

## 3. Checklist de accesibilidad

### Implementado y verificado

- [x] Onboarding por voz con TTS, pasos cortos ≤ 18 palabras
- [x] Cada botón tiene `contentDescription` semántica
- [x] Estado de la app anunciado con `contentDescription` ("Estado: Listo", "Estado: Esperando confirmación", etc.)
- [x] Indicador de carga con `contentDescription = "Procesando, esperá un momento."`
- [x] Botón Callar siempre visible en home y en escáner
- [x] Botones con altura mínima 72 dp (secondary) y 88 dp (primary)
- [x] Fondo negro con texto blanco — alto contraste
- [x] Fuente ≥ 22 sp en todos los textos visibles
- [x] TalkBack: orden de foco encabezado → estado → respuesta → botones
- [x] Pantalla bloqueada en retrato (`screenOrientation="portrait"`)
- [x] No se leen campos de contraseña (`node.isPassword`)

### Pendiente

- [ ] Validación real con 3+ usuarios ciegos (ver `docs/ACCESSIBILITY_CHECKLIST.md`)
- [ ] Prueba a velocidad 2x y 3x de TalkBack
- [ ] Prueba con diferentes tamaños de fuente del sistema
- [ ] Prueba con auriculares vs. altavoz
- [ ] Prueba en Android 8, 10, 12, 14 (diferentes versiones TalkBack)

---

## 4. Checklist de privacidad

- [x] No se guardan imágenes (`PrivacyGuard`, sin `WRITE_EXTERNAL_STORAGE`)
- [x] No se guardan chats ni texto visible (`PrivacyGuard.NO_STORE_GUARANTEE`)
- [x] No se leen contraseñas (`OjoClaroAccessibilityService.isPassword`)
- [x] No se envían mensajes automáticamente (`PrivacyGuard.NO_AUTO_SEND_GUARANTEE`)
- [x] `AccessibilityService` sin taps, gestos ni escritura (`onAccessibilityEvent` vacío)
- [x] `PrivacyGuard.canSendToCloud` bloquea envío sin consentimiento
- [x] `FutureCloudAiProvider` no llama APIs reales
- [ ] Política de privacidad publicada (pendiente)
- [ ] Consentimiento del usuario aceptado en arranque (pendiente — parte del onboarding v2)

---

## 5. Checklist con usuario real

Antes de la primera prueba con persona ciega:

- [x] App abre sin crash
- [x] Onboarding se muestra la primera vez
- [x] Voz habla al abrir (saludo en HomeScreen)
- [x] "Leer texto" abre cámara y habla resultado OCR
- [x] "Callar" detiene la voz en cualquier estado
- [x] Composición de WhatsApp pide confirmación
- [x] "Confirmar" sin acción pendiente responde con mensaje claro
- [x] WhatsApp no instalado: mensaje claro, sin crash
- [x] Accesibilidad inactiva: mensaje claro, sin crash
- [ ] Prueba con TalkBack activo en dispositivo físico (pendiente)
- [ ] Prueba de audio (TTS español, pronunciación acentos)

---

## 6. Riesgos legales

| Riesgo | Severidad | Estado |
|--------|-----------|--------|
| Uso de `AccessibilityService` — Google puede pedir justificación | Alta | Texto de justificación preparado en `docs/WHATSAPP_ACCESSIBILITY_ASSISTANT.md` |
| Falta de política de privacidad para Play Store | Alta | Pendiente de redacción y publicación |
| DESCRIBIR promete IA que hoy no existe | Media | Botón activo pero responde con fallback claro |
| `usesCleartextTraffic` en build de release | Media | Solo afecta debug; debe eliminarse antes de release |
| Sin consentimiento explícito para OCR ni voz | Media | Onboarding lo explica pero no tiene flujo de aceptación formal |

---

## 7. Límites honestos actuales

- **DESCRIBIR** no describe visualmente: llama al backend mock. Si el backend está caído, el fallback dice "No pude conectar" — no que la feature es futura.
- **Lectura de pantalla** funciona solo si el usuario activó manualmente el servicio en Ajustes. No hay forma de activarlo programáticamente (restricción de Android).
- **WhatsApp**: solo abre con texto prellenado. El usuario **debe** elegir el chat manualmente dentro de WhatsApp y tocar Enviar.
- **TTS en emulador**: el emulador no reproduce audio audible. Las pruebas de voz requieren dispositivo físico.
- **OCR**: funciona bien con texto impreso claro a ≥ 20 cm. Texto manuscrito, en cursiva o en fondos complejos da resultados irregulares.

---

## 8. Roadmap 30 días

| Semana | Objetivo |
|--------|----------|
| 1 | Prueba supervisada con 1-2 personas ciegas. Registrar frases que confunden. |
| 2 | Corregir mensajes de voz según feedback. Ajustar flujo de onboarding si necesario. |
| 3 | Evaluar proveedor de IA cloud (OpenAI, Gemini, Claude). Preparar `CloudAiProvider` con consentimiento. |
| 4 | Política de privacidad, keystore de release, eliminación de cleartext traffic. Primera prueba alpha. |

---

## 9. Actualización 2026-05-05 - memoria segura y patrones

- [x] Memoria local segura con `SharedPreferences` para contactos de confianza, preferencias, reglas de advertencia y apps sensibles.
- [x] Guardar memoria siempre pide confirmación por `ConsentManager`.
- [x] Borrar toda la memoria siempre pide confirmación.
- [x] "Qué recordás de mí" lista solo resúmenes seguros.
- [x] `MemoryPolicy` y `PrivacyGuard.canStoreMemory` bloquean chats completos, OCR completo, códigos, contraseñas, tarjetas y datos privados.
- [x] `FrequentPatternTracker` guarda solo metadatos de comandos frecuentes, sin texto privado.
- [x] `RiskDetector` advierte por transferencia, banco, contraseña, código de verificación, datos personales y urgencia.
- [x] La memoria no se sube al backend y no habilita acciones automáticas.

Riesgo pendiente: la detección de riesgos es heurística local y debe validarse con casos reales antes de usarla como defensa principal contra fraude.
