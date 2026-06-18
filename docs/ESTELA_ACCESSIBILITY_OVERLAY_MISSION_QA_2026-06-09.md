# Estela Accessibility Overlay Mission QA - 2026-06-09

## Local Evidence

- Unit tests: `.\gradlew.bat :androidApp:testDebugUnitTest` passed.
- Debug build: `.\gradlew.bat :androidApp:assembleDebug` passed.
- APK ready: `androidApp\build\outputs\apk\debug\androidApp-debug.apk`.
- ADB physical validation blocked: device `ZY32LHS6PS` is connected but `unauthorized`.

## Code Evidence To Validate On Device

- Accessibility service owns a persistent `TYPE_ACCESSIBILITY_OVERLAY` button.
- Accessibility XML requests `flagRetrieveInteractiveWindows`.
- `OjoClaroAccessibilityService` no longer opens `MainActivity` from the accessibility button path.
- "Leer pantalla" runs `ScreenUnderstandingUseCase` inside the accessibility service.
- "Hablar" starts `GlobalAssistantService.ACTION_OVERLAY_VOICE_ENTRYPOINT` without creating a second app overlay.
- Overlay voice is single-shot and logs `finalState=IDLE`.

## Required Manual Step

Accept the Android USB debugging authorization prompt on the Moto G15.

Do not run:

```powershell
adb shell am force-stop com.ojoclaro.android
```

## Resume Commands

```powershell
adb devices -l
adb install -r androidApp\build\outputs\apk\debug\androidApp-debug.apk
adb shell dumpsys accessibility > accessibility_overlay_after_install.txt
adb logcat -c
adb logcat -v time EstelaScreenDiagnostic:I EstelaAccessibility:I EstelaBackground:I EstelaWhatsAppNav:I *:S
```

## Physical Mission Checklist

- With WhatsApp foreground, Estela side button remains visible.
- Tapping the button does not move WhatsApp to background.
- "Leer pantalla" logs `route=SCREEN_UNDERSTANDING_ACCESSIBILITY_OVERLAY`.
- Reading evidence includes `source=REAL_EXTERNAL_ACCESSIBILITY_WINDOW`.
- Reading evidence includes `targetPackage=com.whatsapp`.
- Reading evidence includes `ownPackageSelected=false`.
- Reading evidence includes `overlayWindowSelected=false`.
- Reading evidence includes `fallbackUsed=false`.
- TTS starts and completes, then logs `finalState=IDLE`.
- "Hablar" logs `route=OVERLAY_VOICE_ENTRYPOINT activityOpened=false`.
- STT receives final text and logs `localBeforeRemoteEvaluated=true`.
- TTS answers without opening `MainActivity`.
- The cycle repeats five times for reading and five times for voice.
- Repeat with Settings or another accessible app.

## Current Status

`BLOQUEADO POR VALIDACION FISICA`: local tests and debug build are green, but installation and WhatsApp foreground QA require USB debugging authorization on the device.
