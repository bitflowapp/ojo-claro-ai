# Ojo Claro AI — Review Policy

This document is the canonical review checklist for changes to Ojo Claro AI. Every
reviewer must apply these rules. Every author must justify any deviation in the PR
description. Stricter local rules in subdirectories override this file; weaker
overrides are not accepted.

## 1. Estela safety contract

- Estela must never auto-send WhatsApp messages.
- Estela must never place calls automatically.
- Estela must never request rides automatically.
- Every sensitive action requires explicit, voiced confirmation before execution.
- The only words that confirm a sensitive action are: `confirmar`, `confirmo`,
  `aceptar`. No casing, accent, or punctuation variant outside that set counts.
- The words `dale`, `sí`, `si`, `ok`, `bueno`, `ajá`, and `claro` must never
  execute a pending sensitive action. They are non-confirmations and must be
  handled as such by every routing layer.

## 2. Intent routing review rules

- `/intent` must never drop user input. Every utterance reaches a router with
  a defined outcome.
- A routed action result must never be silently consumed. Each result either
  produces an effect, hands off to a downstream owner, or falls back with a
  voice acknowledgment.
- If `HomeViewModel` cannot safely hand off an action, it must fall back along
  a documented safe path; it must not execute a partial or guessed action.
- A null or cannot-handle runtime result must fall back to the legacy path.
- Stale completions (a response that arrives after the user moved on) must not
  update UI state or execute old commands.

## 3. WhatsApp rules

- Use `Intent.ACTION_VIEW` with a `wa.me` URI only.
- Never use `Intent.ACTION_SEND` for automatic send.
- Never use `AccessibilityService` clicks or gestures to press send.
- The user remains responsible for the final send action, every time.

## 4. Accessibility rules

- Prefer clear voice feedback over silent state changes. State changes the user
  cannot see must be voiced.
- After an invalid confirmation, the pending confirmation state must be
  preserved. It is not cleared, expired, or replaced by an error state.
- Do not expose full contact names, phone numbers, passwords, PINs, banking
  data, or the contents of private messages in `voice_response` unless the
  action strictly requires it. When in doubt, redact.
- Avoid ambiguous or "magic" behavior. Any UI or runtime change must have a
  matching voice acknowledgment.

## 5. Test expectations

Any new sensitive flow must include tests that prove:

- A non-explicit confirmation does not execute the pending action.
- An explicit confirmation proceeds and executes the pending action.
- The pending state is preserved after an invalid confirmation.
- No auto-send, no auto-call, and no destructive action runs without explicit
  confirmation.

These tests are required even when the new flow reuses existing infrastructure.

## 6. Review severity guidance

- Critical: user input can be dropped, or a sensitive action executes without
  explicit confirmation. Block merge.
- Major: a routed action is consumed without handoff or fallback. Block merge
  until handoff or fallback is added.
- Major: an invalid confirmation poisons or clears the pending state. Block
  merge until the pending state is preserved.
- Minor: test naming, comments, or documentation drift. Request changes; do
  not block merge.
