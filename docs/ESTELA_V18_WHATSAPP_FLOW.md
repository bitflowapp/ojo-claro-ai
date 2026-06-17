# Estela V1.8 WhatsApp Flow

Este documento resume el contrato QA del flujo WhatsApp seguro validado en V1.8. No describe una arquitectura nueva; documenta el comportamiento esperado para no romperlo en cambios futuros.

## Objetivo del flujo

Permitir que Estela prepare y envie mensajes de WhatsApp solo cuando el usuario haya pasado por verificaciones explicitas. La regla principal es que preparar, confirmar contacto y enviar son pasos distintos.

## Flujo de contacto

1. Usuario pide preparar un mensaje, por ejemplo `Mandale a Marco que llego tarde`.
2. Estela intenta resolver el contacto.
3. Si hay un unico contacto confiable, abre o prepara el chat y deja el mensaje en estado pendiente.
4. Si hay multiples opciones o baja confianza, Estela pide elegir o confirmar contacto.
5. Si el contacto no se resuelve, Estela debe degradar con una respuesta hablada y no enviar nada.

## Confirmar contacto vs confirmar envio

Confirmar contacto significa: "si, ese es el contacto correcto". No significa enviar.

Confirmar envio significa: el usuario ya vio/escucho el borrador correcto, el chat fue verificado, y ademas dice una frase explicita de envio.

Estos estados no deben mezclarse. Un bug en esta separacion puede producir envio accidental.

## Por que `si` no envia

`si` es una confirmacion debil y ambigua. Puede significar que el contacto es correcto, que el usuario entendio, que quiere continuar o que esta respondiendo otra cosa. Por seguridad, V1.8 mantiene esta regla:

- `si` puede confirmar contacto.
- `si` no envia el mensaje final.
- El envio requiere una frase explicita como `envia`.

## Frases soportadas

Preparacion:

- `Mandale a Marco que llego tarde`
- `Enviarle a Marco que ya sali`
- `Decile a Marco que estoy probando Estela`
- `Mandale mensaje a Marco`

Confirmacion o cancelacion:

- `si` para confirmar contacto cuando Estela esta esperando esa confirmacion.
- `cancelar` para abortar el flujo.
- `envia` para envio final solo cuando el borrador y el chat ya fueron verificados.

Bloqueos:

- Mensajes con claves, tokens, tarjetas, CBU/CVU, PIN o datos similares no deben enviarse.
- Contactos sensibles o ambiguos deben requerir confirmacion o degradar.

## Riesgos conocidos

- Contactos no resolubles en memoria interna: si el contacto no existe en memoria o no esta visible/confiable, el flujo puede no preparar el mensaje. Esto es seguro, pero afecta demo.
- Normalizacion Argentina pendiente: nombres, apodos y variantes locales pueden requerir mas normalizacion para mejorar resolucion.
- Verificar titulo del chat dentro del tap pendiente: antes de tocar enviar, conviene reforzar que el titulo del chat visible coincida con el contacto esperado.
- WhatsApp cambia UI con frecuencia: los selectores de accesibilidad pueden degradar.
- No usar `si` como sinonimo de envio final en ningun test ni demo.

## Checks de no regresion

- Contacto confirmado no envia.
- `si` final no envia.
- `envia` envia solo tras verificacion.
- Sensibles bloqueados.
- Cancelar limpia el pendiente.
