# Personal Agent Engine Foundation Report

## 1. Executive summary

Ojo Claro now has the base of a local-first personal agent engine prepared for a future GPT mini fallback. The system can interpret, draft, suggest, and ask questions without depending on a cloud model today.

## 2. Architecture

Implemented or reinforced:

- Local language normalization for Argentine Spanish.
- Local intent parsing and decision policy.
- Personal memory model and local store.
- Contextual suggestion engine with cooldown.
- Human message composer.
- LLM contract layer, disabled by default.
- Debug state fields for QA visibility.
- Dataset for real-world voice cases.

## 3. GPT mini readiness

Created the contract layer so a future `OpenAiMiniAgentInterpreter` can be plugged in without redesigning the app.

## 4. Cost control

- Local parser first.
- GPT mini only as fallback.
- Short JSON responses.
- Small memory summary.
- No long history prompts.

## 5. Safety

- No `READ_CONTACTS`.
- No `CALL_PHONE`.
- No `ACCESS_BACKGROUND_LOCATION`.
- No `ACTION_CALL`.
- GPT mini never executes directly.
- WhatsApp, calls, and Maps stay under local policy and confirmation.

## 6. Personal memory

Memory types prepared:

- CONTACT
- SAFE_PHONE
- PLACE
- ROUTINE
- PREFERENCE
- PENDING_TASK
- MESSAGE_STYLE

All memory remains local and requires user approval.

## 7. Suggestions

The suggestion engine can propose:

- Route to work.
- Pending task follow-up.
- Medication reminders.

It uses cooldown so it does not repeat in loops.

## 8. Human message composer

The local composer can draft short, warm, formal, calm, or professional messages without sending anything automatically.

## 9. Real world dataset

Voice training cases were added for:

- WhatsApp guided flow.
- Message drafting.
- Contextual contact reuse.
- Human phrasing with Argentine speech.

## 10. Debug panel

The debug UI now has fields for:

- original text
- normalized text
- intent
- confidence
- decision
- state
- memory used
- suggestion
- global continuation flags
- listening and speaking state
- LLM fallback status

## 11. Tests

Added coverage for:

- LLM contract and safety coercion.
- Human message composition.
- Personal memory persistence and blocking.
- Contextual suggestions and cooldown.
- Real-world voice dataset formatting.
- Manifest safety.
- Personal agent decision engine.

## 12. APK

Debug APK output:

`androidApp/build/outputs/apk/debug/androidApp-debug.apk`

## 13. Ready for GPT mini tomorrow

Yes. The app now has the contract, policy, and local fallbacks needed to wire GPT mini later without changing the public architecture.

## 14. Next steps

1. Add a real GPT mini interpreter behind the existing interface.
2. Feed the debug panel with live decision snapshots.
3. Extend the training dataset from real QA failures.

## Validation

Executed successfully:

- `.\gradlew.bat :androidApp:testDebugUnitTest --console=plain`
- `.\gradlew.bat :androidApp:assembleDebug --console=plain`
- `.\gradlew.bat :shared:allTests --console=plain`

Security checks remain in place and the manifest stays free of the forbidden permissions.

