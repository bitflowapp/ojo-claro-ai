# Ojo Claro AI

Monorepo base para una plataforma de asistencia visual con IA para personas ciegas o con baja vision.

El objetivo del MVP Android es simple: compilar, ser estable, no repetir la voz, permitir callar de verdad y leer texto con camara mediante OCR local.

## Principios

1. Primero accesibilidad, despues estetica.
2. Voz como interfaz principal.
3. Camara como sensor, no como decoracion.
4. IA con niveles de confianza.
5. Nunca prometer navegacion autonoma segura en la calle.
6. Privacidad por defecto: imagenes temporales, minimo historial y consentimiento claro.

## Estructura

```txt
androidApp/          App Android nativa
ios/                 App iOS nativa
shared/              Kotlin Multiplatform
backend/             API con FastAPI
docs/                Arquitectura, privacidad, accesibilidad y roadmap
prompts/             Prompts de trabajo
scripts/             Scripts locales
```

## Backend local

```bash
cd backend
python -m venv .venv
source .venv/bin/activate   # Windows: .venv\Scripts\activate
pip install -r requirements.txt
cp .env.example .env
uvicorn app.main:app --reload --host 0.0.0.0 --port 8080
```

Endpoint de salud:

```bash
curl http://localhost:8080/health
```

## Android

El wrapper de Gradle esta incluido despues del pase MVP. En Windows:

```powershell
.\gradlew.bat :androidApp:assembleDebug
.\gradlew.bat :shared:allTests
.\gradlew.bat :androidApp:testDebugUnitTest
```

En macOS/Linux:

```bash
./gradlew :androidApp:assembleDebug
./gradlew :shared:allTests
./gradlew :androidApp:testDebugUnitTest
```

APK debug generado:

```txt
androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

Por defecto Android usa:

```txt
http://10.0.2.2:8080
```

para conectar con el backend local desde el emulador. El proyecto permite HTTP local de desarrollo con `network_security_config.xml`; produccion debe usar HTTPS.

## iOS

La carpeta `ios/` se conserva como base del monorepo. Este pase se limito a Android, `shared` y documentacion.

## Documentacion MVP Android

Ver:

```txt
docs/ANDROID_ACCESSIBILITY_MVP.md
docs/WHATSAPP_ACCESSIBILITY_ASSISTANT.md
```

Incluyen cambios realizados, comandos ejecutados, resultados reales, riesgos, proximos pasos y los limites de la capa segura de WhatsApp/accesibilidad.
