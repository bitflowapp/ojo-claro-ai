# GPT Mini Test Commands

Use these phrases to verify the proxy-backed fallback:

1. `decile a Sofi que llego tarde pero decilo bien`
2. `avisale a Marco que voy en camino pero más amable`
3. `me voy al laburo`
4. `qué tengo pendiente`
5. `el coso de WhatsApp con mamá`
6. `callar`
7. `confirmar`
8. `sí`
9. `dale`

Expected behavior:

- human phrases should be interpreted or drafted
- `callar` should not consume GPT
- `confirmar` should only confirm real pending actions
- `sí` and `dale` should not confirm sensitive actions

