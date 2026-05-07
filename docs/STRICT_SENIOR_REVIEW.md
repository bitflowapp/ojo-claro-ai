# Strict Senior Review - Ojo Claro AI

## Correcciones aplicadas

1. Android local HTTP
   - Agregado `android:networkSecurityConfig`.
   - Agregado `res/xml/network_security_config.xml` con `base-config cleartextTrafficPermitted=true` para desarrollo.
   - Agregado `android:usesCleartextTraffic=true`.
   - Motivo: Android 9+ bloquea HTTP claro por defecto cuando el target SDK es moderno; las IP literales como `10.0.2.2` pueden no quedar cubiertas por domain-config en todas las configuraciones. Producción debe usar HTTPS y remover esto.

2. iOS local HTTP
   - Agregado `NSAllowsLocalNetworking` en `Info.plist`.
   - Motivo: App Transport Security puede bloquear tráfico local no HTTPS.

3. JVM target
   - Agregado Java 17 en `androidApp` y `shared`.
   - Agregado `compilerOptions.jvmTarget = JVM_17` para Kotlin moderno.
   - Motivo: evitar inconsistencias con AGP/Kotlin/Compose modernos.

4. API client compartido
   - Normalización de `baseUrl` con `trimEnd('/')`.
   - Timeouts explícitos en Ktor.
   - Motivo: evitar URLs dobles y requests colgados.

5. iOS API client
   - URL relativa segura.
   - Timeout explícito.
   - Error específico para URL inválida.

6. Backend models
   - `suggestedActions` ahora usa `Field(default_factory=list)`.
   - Motivo: evitar defaults mutables y mejorar estilo Pydantic profesional.

## Validación real ejecutada

- Backend: `python -m pytest -q` pasa con 2 tests.
- Android/KMP: auditado estáticamente; el entorno actual no tiene Gradle instalado.

## Pendientes reales

1. Falta Gradle Wrapper.
   - No se debe inventar `gradle-wrapper.jar` a mano.
   - Generarlo en máquina local con Gradle instalado: `gradle wrapper --gradle-version 8.11.1`.

2. Android no fue compilado en este entorno porque no hay Gradle instalado.
   - La configuración fue auditada estáticamente.

3. iOS no fue compilado en este entorno porque no hay Xcode.
   - La estructura fue auditada estáticamente.

4. El flujo de cámara real todavía no está conectado a Home.
   - Existe analyzer OCR Android y servicio Vision iOS, pero falta pantalla de cámara completa.

5. El reconocimiento de voz real todavía no está implementado.
   - Actualmente los botones simulan comandos.

## Regla comercial

No vender ni presentar como producto final hasta validar con usuarios reales ciegos o con baja visión.
