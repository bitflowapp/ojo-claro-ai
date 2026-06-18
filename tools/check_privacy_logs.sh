#!/usr/bin/env bash
# Privacy Logging Guard (FASE 0) — barrera práctica anti-regresión.
# Falla si algún log de PRODUCCIÓN (androidApp/src/main) interpola texto crudo
# sensible (STT/pantalla/OCR/WhatsApp/contactos/teléfono) sin acotarlo a
# metadatos (.length/.size/len=/count/redact/shortHash/presencia).
#
# Uso:  bash tools/check_privacy_logs.sh
# Exit: 0 = limpio, 1 = encontró logs peligrosos.
set -u

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/androidApp/src/main"

TOKENS='userText|normalizedText|rawText|rawRecognizedText|recognizedText|transcript|ocrText|visibleText|screenText|messageText|messageBody|contactName|chatName|phoneNumber'
LOGCALL='Log\.[diwev]\(|println\(|logBackground\(|logChatNav\(|logAgentCore\(|Timber\.[diwev]\('
SAFE='\.length|\.size|len=|Len=|count|Count|redact|shortHash|Present|!= null|== null|isBlank|isEmpty'

hits="$(grep -rnaE "($LOGCALL)" "$SRC" --include=*.kt 2>/dev/null \
  | grep -E "\\\$\{?($TOKENS)" \
  | grep -ivE "$SAFE" || true)"

if [ -n "$hits" ]; then
  echo "PRIVACY LOG GUARD: FAIL — raw sensitive text in production logs:"
  echo "$hits"
  exit 1
fi

echo "PRIVACY LOG GUARD: PASS — no raw sensitive text in production logs."
