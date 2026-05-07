# APK Distribution Report

## Resumen ejecutivo

Se preparó una base simple para distribuir Ojo Claro AI con una landing web en `docs/`, scripts locales para firmar una APK release y un flujo manual para GitHub Releases.

## Cómo quedó la landing

- Página mobile-first, simple y clara.
- Incluye descarga de APK, instalación, permisos, privacidad y contacto.
- Usa texto apto para personas mayores y familias.
- El botón principal lleva a la página de GitHub Releases para no prometer una descarga inexistente.
- Si no hay remoto Git configurado, la landing cae por defecto en `https://github.com/marcoluna-nqn/ojo-claro-ai/releases/latest`.

## Cómo activar GitHub Pages

1. Subir la rama principal.
2. Ir a **Settings > Pages**.
3. Elegir `Deploy from a branch`.
4. Seleccionar `main` y `/docs`.
5. Guardar.

## Cómo crear keystore

1. Ejecutar `scripts/create_release_keystore.ps1`.
2. Elegir contraseñas locales.
3. Guardar el keystore en el proyecto, pero no commitearlo.
4. Copiar `keystore.properties.example` a `keystore.properties` y completar los datos.
5. `keystore.properties`, `*.keystore`, `*.jks`, `.env` y `dist/` quedan fuera de Git.

## Cómo buildar APK firmado

1. Asegurarse de tener `keystore.properties`.
2. Ejecutar `scripts/build_signed_apk.ps1`.
3. El APK sale en `dist/ojo-claro-ai-release.apk`.
4. El hash SHA256 queda en `dist/ojo-claro-ai-release.apk.sha256.txt`.
5. El script corre primero los tests debug mínimos antes de firmar.

## Dónde queda el APK

- `dist/ojo-claro-ai-release.apk`
- `dist/ojo-claro-ai-release.apk.sha256.txt`

## Cómo crear GitHub Release

1. Usar `scripts/create_github_release.ps1`.
2. Si `gh` no está instalado, seguir el flujo manual en GitHub.
3. Subir la APK y el SHA256.
4. Publicar solo cuando esté confirmado.

## Cómo instalar en Android

- Descargar la APK.
- Permitir apps desconocidas si Android lo pide.
- Instalar y abrir.
- Conceder permisos de cámara, micrófono, ubicación y notificaciones según el caso.

## Seguridad

- No se tocó la API key de OpenAI.
- No se agregó la key a Android.
- `.env`, keystore y `keystore.properties` quedan fuera de Git.

## Qué falta para publicar oficialmente

- Crear el keystore real y el `keystore.properties` local.
- Firmar la release.
- Subir el APK a GitHub Releases.
- Publicar GitHub Pages con el repo real.
