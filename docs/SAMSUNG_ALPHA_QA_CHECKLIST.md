# Samsung Alpha QA Checklist

Objetivo: validar Ojo Claro AI v0.1.0-alpha en Samsung fisico con y sin proxy GPT mini, sin exponer secretos y sin ejecutar acciones peligrosas.

Dispositivo objetivo: `R5CW22SMWDM`

## 1. Preparacion

- Verificar que la PC y el Samsung esten en la misma Wi-Fi.
- Levantar el proxy solo si se va a probar GPT mini.
- No abrir ni mostrar `tools/ojo_claro_ai_proxy/.env`.
- Confirmar que Android tiene microfono concedido.
- Confirmar que la APK instalada corresponde al build que se quiere probar.

Comandos utiles:

```powershell
adb devices
adb -s R5CW22SMWDM logcat -c
adb -s R5CW22SMWDM shell am start -W -n com.ojoclaro.android/.MainActivity
```

## 2. Modo sin proxy

1. Abrir Ojo Claro.
2. Decir: "decile a Sofi que llego tarde pero decilo bien".
3. Esperado: si no hay proxy configurado, degrada a reglas locales o dice claramente que la IA flexible esta apagada.
4. Verificar que no crashea.
5. Verificar que no intenta enviar WhatsApp automaticamente.

## 3. Modo con proxy LAN

1. En PC, levantar proxy:

```powershell
cd tools\ojo_claro_ai_proxy
node server.mjs
```

2. En otra PowerShell, verificar:

```powershell
Invoke-RestMethod http://127.0.0.1:8787/health
```

3. Para Samsung fisico usar URL LAN:

```text
http://IP_DE_LA_PC:8787
```

4. Build debug configurable:

```powershell
.\gradlew.bat :androidApp:assembleDebug -PojoClaroAssistantBaseUrl=http://IP_DE_LA_PC:8787 --console=plain
```

Tambien se puede usar variable de entorno local:

```powershell
$env:OJO_CLARO_ASSISTANT_BASE_URL="http://IP_DE_LA_PC:8787"
.\gradlew.bat :androidApp:assembleDebug --console=plain
```

## 4. Voz base

1. Abrir app limpia.
2. Subir volumen.
3. Conceder microfono si aparece.
4. Escuchar saludo.
5. Decir: "que puedo decir".
6. Decir: "callar".
7. Esperado: corta TTS y no crashea.

## 5. WhatsApp seguro con GPT mini

1. Decir: "decile a Sofi que llego tarde pero decilo bien".
2. Esperado: propone mensaje calido y corto.
3. Verificar frase tipo: "Puedo preparar este mensaje para Sofi: ... Queres confirmarlo?"
4. Decir: "si".
5. Esperado: NO confirma.
6. Decir: "dale".
7. Esperado: NO confirma.
8. Decir: "confirmar".
9. Esperado: abre/prepara WhatsApp con texto, sin tocar enviar.
10. Confirmar manualmente que el boton enviar no fue presionado por Ojo Claro.

## 6. WhatsApp Guided Mode

1. Decir: "abri wp".
2. Si no hay continuidad externa real, esperado: no abre WhatsApp y pregunta accion.
3. Decir: "chat Marco".
4. Esperado: pide confirmacion o pide numero si no lo tiene.
5. Decir: "confirmar" solo si hay pending real.

## 7. OCR / camara

1. Tocar o decir flujo de lectura de texto.
2. Conceder camara si aparece.
3. Probar con texto impreso simple.
4. Esperado: lee texto sin guardar imagen.
5. Negar camara y repetir.
6. Esperado: explica permiso sin crash.

## 8. Permisos negados

- Negar microfono: debe pedir permiso claro y no crashear.
- Negar notificaciones: modo global debe degradar sin crash.
- Negar overlay: no debe prometer continuidad encima de WhatsApp.
- Negar ubicacion: "donde estoy" debe pedir permiso o explicar limite.

## 9. Cerrar y reabrir

1. Cerrar app desde recientes.
2. Reabrir.
3. Verificar que no queda pending viejo peligroso.
4. Decir "confirmar" sin pending.
5. Esperado: no ejecuta nada.

## 10. TalkBack basico

1. Activar TalkBack si el tester lo usa.
2. Recorrer pantalla principal.
3. Verificar botones con labels claros.
4. Verificar que debug no tape controles en build debug.

## 11. Logs si falla

Capturar:

```powershell
adb -s R5CW22SMWDM logcat -d | findstr /i "FATAL EXCEPTION AndroidRuntime ojoclaro SecurityException SpeechRecognizer TextToSpeech WhatsApp OpenAI proxy microphone overlay"
```

Anotar:

- frase hablada;
- texto reconocido;
- texto normalizado;
- estado;
- intent local;
- intent LLM;
- decision;
- pending;
- si habia proxy;
- URL base configurada;
- si WhatsApp abrio;
- si se envio algo automaticamente.

## 12. Criterio alpha demo-controlada

Demo-controlada seria si:

- No hay crash.
- No hay mojibake visible/TTS.
- "si" y "dale" no confirman.
- "confirmar" confirma solo pending real.
- WhatsApp prepara, no envia.
- Sin proxy hay fallback claro.
- Con proxy hay propuesta calida.
- El panel debug permite diagnosticar fallos.
