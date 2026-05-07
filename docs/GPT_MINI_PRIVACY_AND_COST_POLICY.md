# GPT Mini Privacy and Cost Policy

## What can go to GPT mini

- A short user phrase.
- The normalized text.
- The current agent state.
- A minimal memory summary.
- Safe contact names that the user already approved.
- Safe place aliases.
- Small pending task labels.
- Allowed intents.
- Forbidden actions.

## What must never go to GPT mini

- Audio.
- Full screen images.
- Full screenshots.
- Passwords.
- Verification codes.
- Cards.
- Bank data.
- Documents.
- Exact location without consent.
- Long chat history.
- Full agenda.
- Raw OCR dumps.
- Hidden permissions state.

## Cost strategy

1. Parse locally first.
2. Use GPT mini only when local rules do not understand the phrase.
3. Use GPT mini only when the text is useful and the risk is low.
4. Keep the response short and JSON-only.
5. Keep the prompt compact.
6. Avoid long histories and giant catalogs.
7. Prefer fewer than 500 to 900 tokens per interpretation.

## Safety strategy

- GPT mini never executes anything.
- Ojo Claro always validates the response.
- Sensitive topics are blocked locally before calling the model.
- If the model asks for forbidden data, ignore it.
- If the model is down, disabled, or low confidence, continue with local logic.

## Preferred prompt shape

- One user phrase.
- One normalized phrase.
- One state label.
- One short memory summary.
- One short list of known safe contacts.
- One short list of known places.
- One short list of pending tasks.
- One list of allowed intents.
- One list of forbidden actions.

## Expected behavior

- If the phrase is clear, the model returns a small JSON object.
- If the phrase is unclear, the model asks a short question.
- If the phrase is risky, the model should not try to be clever.
- If the user wants WhatsApp, Maps, or calling, the app still requires local confirmation and policy checks.

