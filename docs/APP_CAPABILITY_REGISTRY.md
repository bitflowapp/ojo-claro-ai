# App Capability Registry

Paquete 6B agrega un registry local de apps conocidas y una apertura segura de
apps por Intent. La capa es separada del planner para que pueda testearse sin
Android real.

## Apps detectadas

Tipos iniciales:

- `RIDE_HAILING`: Uber, Cabify, DiDi.
- `MESSAGING`: WhatsApp, WhatsApp Business, Telegram, Messages.
- `MAPS`: Google Maps.
- `SETTINGS`: Android Settings.
- `BROWSER`: Chrome.
- `PHONE`: Phone, Android Dialer.
- `PAYMENTS`: Mercado Pago y bancos conocidos.

Cada `AppCapability` define:

- nombre publico de la app
- package name
- tipo de capacidad
- nivel de riesgo
- si se puede abrir de forma segura
- si requiere confirmacion para abrir
- acciones prohibidas dentro de la app

## Resolver de instalacion

`InstalledAppResolver` es una interfaz testeable. En produccion,
`AndroidInstalledAppResolver` consulta `PackageManager` y devuelve `false` si el
sistema falla o si la app no esta visible/instalada.

Los tests usan un fake puro y no dependen de apps instaladas en la maquina.

## Apertura segura

`SafeAppLauncher` abre una app solo si:

- el paquete esta instalado,
- la capability permite apertura segura, o el usuario confirmo abrir una app
  que requiere confirmacion,
- el starter puede lanzar un Intent `ACTION_MAIN` con `CATEGORY_LAUNCHER`.

Resultado estructurado:

- `Launched`
- `NotInstalled`
- `RequiresConfirmation`
- `BlockedSensitiveApp`
- `Failed`

Abrir una app no ejecuta la tarea. Para viajes, abrir Uber o Cabify solo deja al
usuario en la app. Estela no solicita el viaje, no confirma precio, no toca
conductor y no revisa pagos automaticamente.

## Apps sensibles

Mercado Pago y bancos quedan como `PAYMENTS` con riesgo `HIGH`. Sin
confirmacion, el launcher devuelve `BlockedSensitiveApp`. Aunque se agregue
confirmacion para abrir en el futuro, siguen prohibidas estas acciones:

- pagar
- transferir
- comprar
- confirmar operaciones
- leer datos financieros completos
- guardar datos sensibles

Telefono y marcador tambien requieren confirmacion para abrir. Abrir telefono
no inicia llamadas.

## Integracion con Task Tickets

Para `REQUEST_RIDE`, el orquestador puede:

- consultar apps de transporte instaladas,
- completar el ticket `Buscar app de transporte` cuando encuentra una app,
- agregar o actualizar `Abrir Uber`, `Abrir Cabify` o equivalente,
- emitir un handoff externo seguro para abrir la app.

Si no hay apps de transporte:

`No encontre una app de transporte instalada. Podes instalar Uber o Cabify, o abrir una opcion manual.`

Si abre Uber:

`Abri Uber. Ahora puedo orientarte con la pantalla, pero no voy a solicitar el viaje sin confirmacion final.`

## Limites actuales

Esta capa no usa Accessibility para actuar. No invoca clicks, gestos ni acciones
globales. Tampoco observa la pantalla dentro de Uber, Cabify, WhatsApp o
Ajustes. Eso queda para Paquete 6C con `StructuredScreenSnapshot`, todavia sin
clicks automaticos.
