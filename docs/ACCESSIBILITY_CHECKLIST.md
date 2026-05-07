# Checklist de accesibilidad

## UX

- Botón principal enorme.
- Alto contraste real.
- Texto grande.
- Sin gestos complejos obligatorios.
- La app habla al abrirse.
- Las acciones críticas tienen confirmación por voz.
- El orden de foco es lógico.
- Todo botón tiene etiqueta semántica clara.

## Android

- Probar con TalkBack activado.
- Usar `Modifier.semantics`.
- Evitar botones con solo ícono sin descripción.
- Tamaños mínimos táctiles.
- No bloquear el flujo por animaciones.
- Mantener feedback háptico/sonoro.

## iOS

- Probar con VoiceOver activado.
- Usar `accessibilityLabel`.
- Usar `accessibilityHint` cuando corresponda.
- Evitar interfaces que dependan solo de color.
- No usar texto decorativo leído como contenido útil.
- Botones críticos con labels simples.

## Prueba humana obligatoria

Validar con al menos:

- 3 usuarios ciegos.
- 3 usuarios con baja visión.
- 1 familiar/cuidador.
- 1 institución potencial compradora.

La app puede parecer perfecta para alguien que ve y ser inútil para quien realmente la necesita.
