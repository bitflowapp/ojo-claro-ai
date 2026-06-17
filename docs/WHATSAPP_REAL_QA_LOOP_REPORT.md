# WhatsApp Real QA Loop — Report

**Date:** 2026-06-17
**Scope:** WhatsApp only (no Uber / Instagram / camera / new features). Controlled QA on
the Moto (`ZY32LHS6PS`), real device, real WhatsApp install.
**Mode:** Ultra Code loop — understand → test on device → detect failure → fix → test → build →
reinstall → re-test.

> Privacy: this report never prints a full phone number. The QA target is referred to only as
> **self-chat ending=redacted**. No chat content, screenshots, audios, or private logs were saved.
> Device captures were filtered to Estela's own log tags (`EstelaBackground`, `EstelaDebugCmd`,
> `EstelaScreenDiagnostic`) and any digit run was redacted at capture time.

---

## 1. Branch / HEAD

| Item | Value |
|---|---|
| Branch | `chore/unattended-hardening-sprint` |
| HEAD | `f71bc08` |
| `git rev-list --count HEAD` | **1** (clean orphan root) |
| Working tree | uncommitted blind-safety layer + this session's fix (see §9) |

## 2. PII scan — working tree & reachable history (FASE 0 gate)

Method: counts/file-lists only (`git grep -l`, `rg --no-ignore -l`, `git grep … $(git rev-list HEAD)`),
never content mode, so the value itself is never echoed.

| Form | Working tree | Reachable history (`f71bc08`) | incl. gitignored/untracked |
|---|---|---|---|
| Unique suffix `…redacted` | **0** | **0** | **0** |
| National (10-digit) | **0** | **0** | **0** |
| Intl (13-digit `+549…`) | **0** | **0** | **0** |

All remaining numbers in the tree are **synthetic fixtures** (area codes 11 / 299, fake suffixes;
**none ends in redacted**), e.g. `0000005678`, `+5491150000000`, `+5492991234567`.
**Verdict: PII = 0. Gate cleared → physical testing authorized.**
(This supersedes a stale note that warned the national form was still present; it has since been
scrubbed and committed into `f71bc08`.)

## 3. Tests / build

| Step | Result |
|---|---|
| `:androidApp:testDebugUnitTest` (fresh `--rerun-tasks`) | **BUILD SUCCESSFUL** (exit 0) |
| `:androidApp:assembleDebug` | **BUILD SUCCESSFUL** — `androidApp-debug.apk` (~58 MB) |
| Rebuild after the §9 fix (tests + assemble) | **BUILD SUCCESSFUL** (exit 0) — all tests green incl. new ones |

`ASSISTANT_BASE_URL` baked into the APK = `""` (empty) → no backend/LLM egress is even possible in
this build, independent of routing.

## 4–6. Device readiness

| Check | Result |
|---|---|
| ADB device | `ZY32LHS6PS device` ✓ |
| APK installed | **YES** (reinstalled after fix; install did **not** unbind accessibility this time) |
| Accessibility (Estela) active | **YES** — `Bound services:{Service[label=Estela …]}`, master switch = 1 |
| WhatsApp installed & logged in | **YES** — `com.whatsapp` foreground on `HomeActivity`/`Conversation` (not login) |
| TTS | **YES** — `TTS_STARTED`/`TTS_COMPLETED` on every routed command |
| Screen node reading | **YES** — accessibility bound; state/scroll handlers read nodes |
| self-chat ending=redacted verifiable | **NOT exercised live** — see §7 note and §16/§17 |

> ⚠️ **Personal-device reality:** the Moto is Marco's personal phone with real third-party chats.
> At session start WhatsApp had a **third-party** chat open (not the self-chat). Per safety rules
> #1/#13/#14 I navigated to the chat list, recorded **no** content, and ran **no** write-path test
> against any third-party chat.

## 7. Result per command (real device)

Driver: `DebugCommandActivity` → `ACTION_DEBUG_VOICE_TEXT` → the **real** voice pipeline.

### FASE 2 — smoke (no send)

| # | Command | Route | Result |
|---|---|---|---|
| 1 | "qué pasa en WhatsApp" | `whatsapp_anxiety_hardening` → `WHATSAPP_STATE_QUERY_RESULT open=false inChat=false pendingReply=false pendingDraft=false` | ✅ local, no LLM |
| 2 | "leeme los mensajes" | local read → TTS | ✅ local, no LLM |
| 3 | "ayuda" | `COMMAND_MATCHED basicCommand=HELP` | ✅ |
| 4 | "repetí" | `basicCommand=REPEAT available=true` | ✅ |
| 5 | "cancelar" | `basicCommand=CANCEL` | ✅ |
| 6 | "abrí WhatsApp con mi contacto de prueba" | `whatsapp_blind_open` (relation `qa` not seeded → graceful "not found") | ✅ no write |
| 7 | "abrí WhatsApp conmigo" | `WHATSAPP_OPEN_REQUESTED→OPENED` | ⚠️ opens the app, **not** the self-chat (minor UX gap) |
| 8–11 | compose "…que estoy llegando" → "sí" → "sí" → "pará no mandes" | **not exercisable via harness** | see note ↓ |

> **Why 8–11 weren't exercised live:** the debug harness Activity is always foreground, so the
> in-chat detector returns `inChat=false` and the draft path safely refuses (`WHATSAPP_REPLY_FAILED_NOT_IN_CHAT`)
> — confirmed with "mandale eso" (ambiguous): reply path → `inChat=false` → **no draft written**.
> Exercising the real draft→confirm→cancel flow requires opening/identifying the **self-chat**, which
> on this personal device means reading the chat list (third-party PII) or typing the real number
> (PII) — both violate the privacy rules. The flow's safety is instead covered by the unit suite
> (`WhatsAppReplyConfirmationResolver`, `WhatsAppSafeSendContractTest`, `WhatsAppActionAuditTest`) and
> by code structure (real send is gated off, see §12).

### FASE 3 — forbidden / dangerous

| Command | Layer that blocked | Result |
|---|---|---|
| "borrá este chat de WhatsApp" | app `whatsapp_forbidden_action` `WA_ACT_DELETE_CHAT blocked=true` | ✅ |
| "archivá este chat de WhatsApp" | `WA_ACT_ARCHIVE_CHAT blocked=true` | ✅ |
| "reenviá esto por WhatsApp" | `WA_ACT_FORWARD blocked=true` | ✅ |
| "compartí mi ubicación por WhatsApp" | `WA_ACT_SHARE_LOCATION blocked=true` | ✅ |
| "borrá este chat" (bare) | **was** falling to LLM → **FIXED** → `WA_ACT_DELETE_CHAT blocked=true objectAnchor=true` | ✅ (after §9) |
| "bloqueá este contacto" (bare) | `WA_ACT_BLOCK_CONTACT blocked=true objectAnchor=true` | ✅ (after §9) |
| "mandá una foto/archivo/sticker por WhatsApp" | harness denylist `blocked_unsafe_for_harness` (defense-in-depth) | ✅ |
| "pagale por WhatsApp" | harness denylist | ✅ |
| "llamá por WhatsApp" | app `whatsapp_critical_guard blocked_llm=true` | ✅ |
| "hacé videollamada" | harness denylist | ✅ |
| "bloqueá la pantalla" (negative test) | correctly **not** claimed (`objectAnchor=false`) → fell through | ✅ no over-claim |

App-level guard for the harness-pre-blocked categories (photo/file/sticker/pay/call/audio) is verified
by unit tests (`WhatsAppForbiddenCommandParser`, `WhatsAppCriticalGuard`, `WhatsAppDangerousCommandParser`).

### FASE 4 — stress / error states

| Case | Behavior | Result |
|---|---|---|
| Fillers "ehh mandale a mi contacto de prueba que ya voy" | parsed relationship `key=qa` → `not_configured` | ✅ no write |
| Ambiguous "mandale eso" | reply path → `inChat=false` → refused | ✅ no write |
| Scroll "bajá" / "seguí leyendo" | `whatsapp_scroll` → `no_target` (graceful) | ✅ |
| WhatsApp not foreground / no input field / no nodes | all fail **closed** (refuse + speak) | ✅ |
| Backend off | `ASSISTANT_BASE_URL=""` → no egress possible | ✅ |

### FASE 6 — audio (block only; NO real audio)

| Command | Result |
|---|---|
| "mandale un audio" / "grabá un audio por WhatsApp" / "mandale un audio diciendo prueba" | `blocked_unsafe_for_harness` (harness) + app guard unit-tested; mic not touched, nothing recorded/sent | ✅ |

### FASE 5 — single controlled real message

**NOT executed.** Real send is currently **impossible** without a deliberate flag flip (see §12), the
in-chat draft is not exercisable via the harness, and doing it for real requires the verified self-chat
on a personal device. Deferred pending separate authorization + an isolated phase (safety rule #12).

## 8. Bugs found

1. **(MEDIUM, fixed) Rule-#7 fall-through.** Bare destructive phrases that don't name WhatsApp
   (e.g. "borrá este chat", "bloqueá este contacto") fell through to `fallbackReason=no_local_match`
   → `handleFreeConversation` (LLM/backend) when WhatsApp wasn't the detected foreground app. Root
   cause: `handleWhatsAppForbiddenActionCommand` and the critical guard are gated on
   `namedWhatsApp || isWhatsAppActiveContext()`; via the harness the debug Activity is foreground so
   the context gate fails. (In this build the empty base URL meant no data actually left the device,
   but the routing decision still chose the LLM path.)
2. **(LOW) "abrí WhatsApp conmigo"** opens the app, not the self-chat specifically (no self-chat
   intent resolver). Not a safety issue.
3. **Write-path audit findings** — 16 confirmed (4 HIGH, 3 MEDIUM, 8 LOW, 1 INFO) on the in-chat path
   the harness can't reach. See **§18** for the full triage, the 5-question write-path verdict, and the
   HIGHs that should be fixed before commit. None of these were fixed yet (newly discovered).

## 9. Fix applied

Closes finding #1 with a **scoped** relaxation (not a blanket one):

- `WhatsAppForbiddenCommandParser.mentionsExplicitMessagingObject(text)` — new pure helper: true only
  for an **explicit** messaging object (`chat/contacto/mensaje/grupo/conversación/charla`), deliberately
  excluding the too-loose `este/esto`.
- `GlobalAssistantService.handleWhatsAppForbiddenActionCommand` gate is now
  `!namedWhatsApp && !isWhatsAppActiveContext() && !objectAnchor` → an **already-detected** forbidden
  action that names an explicit chat/contact is blocked locally even without active WhatsApp context.
  Log gains `objectAnchor=…` for auditability.

This only affects phrases that **already** parse as a forbidden action; it does not let new phrase
shapes through, and the negative test ("bloqueá la pantalla") confirms no over-claim.

## 10. Tests added

- `WhatsAppForbiddenCommandParserTest.explicitMessagingObjectAnchorsDestructiveIntent` — behavioral:
  asserts anchor for "borrá este chat"/"bloqueá este contacto"/… and **no** anchor for
  "archivá el documento"/"bloqueá la pantalla"/"reenviá esto"/"borrá esto"/"pagale".
- `WhatsAppForbiddenRoutingContractTest.destructiveActionWithChatObjectIsClaimedWithoutWhatsAppContext`
  — source-scan guard so the gate's rule-#7 hardening can't silently regress.

## 11. Re-test (after fix, reinstalled APK)

- "borrá este chat" → `WA_ACT_DELETE_CHAT blocked=true named=false objectAnchor=true` ✅
- "bloqueá este contacto" → `WA_ACT_BLOCK_CONTACT blocked=true objectAnchor=true` ✅
- "bloqueá la pantalla" → `fallbackReason=no_local_match` (correctly **not** claimed) ✅
- Full unit suite + assembleDebug green.

## 12. sendTapLogCount

**0.** Every WhatsApp audit line during the session showed `sendTap=0`. `tapWhatsAppSend` (single
call-site) was never reached: the WA-5 reply path's strongest outcome is `WHATSAPP_SEND_REAL_NOT_ENABLED`
(never taps) and the legacy path is gated by `whatsAppFlags.realSendEnabled` (default **false** →
`whatsappSend outcome=blocked_feature_disabled`).

## 13. Dangerous counters

`callTap=0 videoCallTap=0 audioSendTap=0 audioRecord=0` throughout. `blocked` incremented on each
forbidden/dangerous command (auditable, e.g. `blocked=1..4`).

## 14. LLM / backend used?

**No** for WhatsApp-critical routes. The one fall-through path (finding #1) is now blocked locally; in
any case `ASSISTANT_BASE_URL=""` makes egress impossible in this build. Read/basic commands stayed
local (no `fallbackReason=no_local_match`, no agent).

## 15. PII / private logs?

**No.** Captures restricted to Estela tags with digit-run redaction; no chat content or full numbers in
logs; the third-party chat that was open at start was navigated away from and its content was never saved.

## 16. Verdict (updated after write-path audit §18 + HIGH fixes §20 + #8 clarifier §21)

| Audience | Verdict | Rationale |
|---|---|---|
| **Marco QA (controlled)** | 🟢 **GREEN** | Read/basic/forbidden/audio verified live; sendTap=0; dangerous counters 0; FASE 3 bug + 3 write-path HIGHs + #8 content-routing **fixed + tested (build green)**; PII = 0. |
| **Real blind user** | 🟢 read/forbidden/audio · 🟡 **compose/write** | Wrong-chat HIGH (#1) fail-closed; draft-cleanup/TOCTOU HIGHs (#11/#13/#14) fixed; #8 content-egress now caught by a local clarifier (no message content to LLM). YELLOW remains only because the in-chat draft flow still needs a **real-voice** end-to-end smoke (the harness can't reach it). Send stays gated. |
| **Commercial demo** | 🟡 **YELLOW** | Read-only WhatsApp demoable now; compose→confirm materially safer post-fix; do one real-voice self-chat smoke before a live compose demo. FASE 5 not executed. |

## 17. Next blockers

1. **Live in-chat write-path smoke** with **real voice** (not the debug harness) on the **self-chat
   ending=redacted** — the harness cannot exercise the in-chat draft path (`inChat=false`).
2. **Self-chat open intent** ("abrí WhatsApp conmigo"/"abrime mi chat").
3. **FASE 5 real send**: requires enabling `whatsAppFlags.realSendEnabled` (normally-off flag) in an
   isolated phase — explicit separate authorization (rule #12). NOT executed.
4. *(optional, LOW)* the documented LOW/INFO items in §18.4 (e.g. #7 `recordSendTap` counts non-Sent).

## 18. Write-path adversarial audit (`whatsapp-writepath-audit`)

5 read-only lenses × find→adversarially-verify, 24 agents. **19 raw findings → 16 confirmed, 3 refuted.**
This covers the in-chat write-path the physical harness **cannot** reach.

### 18.1 Write-path verdict (your 5 questions)

| Question | Answer | Basis |
|---|---|---|
| **¿Escribir borrador en chat equivocado?** | **SÍ, condicional** (HIGH) | #1 smart-compose: bidirectional `WhatsAppLabelMatcher` VERIFIES a name-only header that's a strict token-subset of the target ("Ana" vs "Ana García" = distinct contact) → draft into wrong composer + false confirmation. Needs deep-link misroute **and** subset-name collision. #2 (MEDIUM) reply path skips the verifier (status string passes the blank-only guard). Relationship-compose path is **safe**. **Never sends** (gated). |
| **¿Enviar por "sí" ambiguo?** | **NO** | Weak affirmations never send (unit-tested `weakAffirmativesNeverConfirmSend`). #6 (LOW) notes the strong set also accepts 'acepto'/'confirmo' (explicit, not weak) and the legacy path is single-step; #5 (LOW) legacy taps without re-verify — **all behind `realSendEnabled=false`**, so no real send regardless. |
| **¿Pendiente vivo?** | **NO para auto-enviar; SÍ puede quedar un borrador tipeado** | `completeOverlayVoiceTurn` nulls the pending objects (a pending send cannot survive a turn and fire later). BUT #11/#13 (HIGH): the new compose paths physically type the draft and do **not** clear the composer on turn-end / STOP / legacy-cancel (only WA-5 reply-cancel clears it). #14 (MEDIUM): STOP during the 1.2 s open-verify window is overridden by a fire-and-forget coroutine that re-types + re-arms. |
| **¿Mandar a LLM/backend?** | **SÍ, routing-wise — pero es la ruta conversacional general, no una ruta WhatsApp crítica** | #8 (see 18.3). A pure declarative with no action-marker, dictated while WhatsApp is foreground, passes `ConversationGate` → `/conversation`. Gated by `ASSISTANT_BASE_URL=""` (no egress in this build). Recognized WhatsApp **action** routes (compose/reply/forbidden/dangerous) stay local. |
| **¿Quedar sin TTS?** | **Mayormente NO; gaps** | #10 (MEDIUM): deferred-compose leaves ~1.2 s silence and audibility depends on a coroutine. #11 (HIGH): turn teardown leaves a typed draft without an audible warning. #12 (LOW): cancellation handling speaks after cancel. `CoroutineExceptionHandler` (Fix F) prevents hard mutes. |

### 18.2 HIGH risks — **ALL 3 FIXED this session (working tree, no commit)**

| # | Lens | Title | File | Fix applied? |
|---|---|---|---|---|
| 1 | wrong-chat | Bidirectional label match VERIFIES a strict token-subset into a different contact | `WhatsAppLabelMatcher.kt` | ✅ **fail-closed: token-set equality only** |
| 11 | blind-feedback | Turn teardown nulls pending but leaves draft typed in composer | `GlobalAssistantService.kt` | ✅ **clears own draft on teardown** |
| 13 | completeness | V1.2 cancel / global STOP null the pending but don't clear the typed compose draft | `GlobalAssistantService.kt` | ✅ **clears on STOP + legacy-cancel (+TTS on cancel)** |
| 14 | completeness | TOCTOU: STOP during 1.2 s open-verify is overridden — coroutine re-types + re-arms | `GlobalAssistantService.kt` | ✅ **generation guard aborts the deferred write** |

See **§20** for the exact changes, tests, and re-verification.

### 18.3 The content-routing HIGH (#8) — your focused questions

- **Qué ruta:** the **general conversational fallback** — `handleRecognizedText` → (no local handler
  matched) → `handleWhatsAppCriticalGuardBeforeLlm` (only blocks **mutating-verb** phrases) → `ConversationGate.isConversational(text)` (true when text has **no** action-marker) → `handleFreeConversation` → `POST /conversation` with `user_text=text`. It is **not** one of the recognized WhatsApp action routes.
- **¿Puede mandar contenido a backend/LLM?** Routing-wise **yes** (the utterance text is the payload).
  In **this build, no actual egress**: `ASSISTANT_BASE_URL=""`. With a real URL it would POST the text.
- **¿Ocurre en ruta crítica WhatsApp?** **No.** Recognized WhatsApp commands/actions stay local; this is
  the catch-all assistant path that happens to fire while WhatsApp is the foreground app. A *dictated
  message body* without a "mandale a X que…" wrapper is indistinguishable from a general utterance.
- **¿Hay test que lo cubra?** **No** specific test for "declarative dictation while WhatsApp foreground
  stays local." `ConversationGate` has over-block tests but not this case.
- **¿Fix aplicado?** **SÍ — resuelto (§21)** como **local clarifier**: con WhatsApp al frente, una
  frase que parece contenido de mensaje/continuación ambigua se aclara local (no egresa al LLM),
  mientras el Q&A claro sigue funcionando. (Product decision taken 2026-06-17.)

### 18.4 MEDIUM / LOW (documented; not commit-blockers)

- **MEDIUM** #2 reply path skips the verifier (status node passes blank-only guard); #10 deferred-compose silence window.
- **LOW** #3 cancel clears the *current* foreground chat's composer (if user navigated away); #4 phone-ending reader scans the status node; #5 legacy send taps without re-verify (gated off); #6 confirm set broader than "mandalo ahora" (explicit confirmations, gated off); #9 `PendingRelationshipLink`/`PendingWhatsAppReply` default `toString()` carry full number/draft (latent log-leak; **no current call-site logs the object**); #12 coroutine catches `CancellationException` as failure; #15 my new `objectAnchor` over-claims non-WhatsApp messaging (Telegram/SMS) with a WhatsApp-worded refusal — **cosmetic** (safe outcome, wrong app name); #16 `describeOpenChatDestination` misclassifies names with ≥7 digits.
- **INFO** #7 `recordSendTap()` increments even when the tap returns not-Sent (weakens the `sendTap=0` oracle; gated off). Easy hardening: only count `result == Sent`.

### 18.5 Refuted (3)

- TOCTOU between `verifyOpenedDestination` and `setWhatsAppDraft` (draft writer re-check) — refuted (no exploitable window).
- "Reply-path destination-confirm unreachable from harness" — refuted as **test-coverage debt**, not a concrete bug.
- `voiceTurnExceptionHandler` double-speak / wrong-thread — refuted (handler attachment is correct).

### 18.6 Inconclusive / judgment-call

- #8 content-egress (above) — real routing, but it's the general assistant path with a UX tradeoff on the fix; **your call**.

## 19. Commits (NOT done — awaiting authorization)

No commit / push / remote / main touched. The §18.2 HIGHs are now fixed (§20). Suggested split when
authorized:
1. (n/a) privacy scrub / clean root — already clean on `f71bc08`.
2. **blind safety fixes** — pre-existing uncommitted blind-safety layer + FASE 3 fix
   (`mentionsExplicitMessagingObject` + gate) + the §20 write-path HIGH fixes + all new tests.
3. **physical WhatsApp QA report** — this file.

## 20. HIGH fixes applied this session (working tree, no commit)

All built (`compileDebugKotlin` + `testDebugUnitTest` + `assembleDebug` = **BUILD SUCCESSFUL**),
reinstalled on `ZY32LHS6PS`, regression re-verified on device (forbidden block intact, `sendTap=0`,
dangerous counters 0, no crash).

**#1 — wrong-chat label matcher → fail-closed** (`WhatsAppLabelMatcher.kt`)
- `matches()` now verifies **only** on token-set **equality** (`at == bt`); the old bidirectional
  subset (`aInB || bInA`) is removed. `"Ana García"` vs `"Ana"` (and the reverse) → **not VERIFIED**.
- Phone-ending (last-4) remains the dominant strong signal and is unchanged.
- Tests: `WhatsAppBlindSafetyPhrasesTest` updated (subset cases now `false`) +
  `verifierFailsClosedOnSubsetNameWithoutPhoneEnding` (end-to-end: subset name w/o last-4 → not verified;
  matching last-4 → verified; exact name → verified).

**#11/#13 — draft cleanup on teardown/STOP/legacy-cancel** (`GlobalAssistantService.kt`)
- New `clearOwnWhatsAppDraftIfPending()` (clears the composer **only** when a pending exists; never
  sends; logs `WHATSAPP_OWN_DRAFT_CLEARED`). Wired into `completeOverlayVoiceTurn` (turn teardown),
  both global STOP branches, and the legacy V1.2 cancel (which already speaks "Cancelado. No envié nada.").
- WA-5 reply-cancel already cleared + spoke (unchanged).
- *Note:* `"cancelar"/"pará no mandes"` clears **and speaks**; pure barge-in `"callate"`/timeout clears
  **silently** (respects the stop / the user was already re-prompted) — the draft is never left typed.

**#14 — TOCTOU generation guard** (`GlobalAssistantService.kt`)
- New `whatsAppComposeGeneration` (`AtomicInteger`) + `invalidateInFlightWhatsAppCompose()`.
- Both deferred compose paths (`openVerifyThenDraft`, `prepareDraftAndAskSend`) capture the generation
  before the async window and **re-check it before writing the draft / arming the pending**; any
  cancel/STOP during the 1.2 s window bumps the generation → the coroutine aborts (no draft, no re-arm).
- Tests: `WhatsAppBlindSafetyContractTest` — `deferredComposeAbortsOnCancelDuringWindow`,
  `teardownAndStopBranchesClearOwnDraft`, `legacyCancelClearsDraftAndSpeaks`,
  `clearOwnDraftHelperOnlyClearsWhenPendingAndNeverSends`, `weakYesStillNeverSends`.

**Deferred:** LOW/INFO items (§18.4). **#8 content-routing → now resolved in §21.**

## 21. #8 content-routing — resolved as a local clarifier (product decision)

**Decision:** when WhatsApp is the active context, an ambiguous **message-like** utterance is NOT sent
to `/conversation`/LLM; Estela asks a **local** clarification instead. Clear Q&A still goes to the
normal assistant flow, and explicit WhatsApp actions still take their safe/forbidden routes.

**Implementation (working tree, no commit):**
- New pure `WhatsAppMessageClarifierPhrases` — `looksLikeQuestion()` (Q&A: interrogatives + help
  markers) and `looksLikeAmbiguousMessageContent()` (vague continuations like "eso"/"lo anterior",
  status declaratives like "estoy llegando"/"ya voy", and tell/send verbs with vague content like
  "decile que sí"/"mandale eso"/"ponele ok"). Handles the voseo→infinitive rewrite the normalizer
  applies (`mandale→mandar`, `decile→decir`).
- New GAS handler `handleWhatsAppAmbiguousMessageClarifier()` — gated on `isWhatsAppActiveContext()`;
  lets explicit forbidden actions and clear Q&A pass; for ambiguous message content it speaks
  **"¿Querés que use eso como mensaje de WhatsApp? Decime a quién y qué querés mandar."** and returns.
  It does **not** write, draft, send, touch UI, or call the LLM/backend. Wired **after** the forbidden
  handler and **before** compose/reply and the conversation gate.

**Policy mapping:**
- **A** (ambiguous message content) → local clarifier, no backend, no draft, no UI. ✓
- **B** (clear Q&A) → normal assistant flow; never includes private WhatsApp content. ✓
- **C** (explicit critical/forbidden WhatsApp action) → stays local via forbidden/critical routes,
  never LLM. ✓ (clarifier defers to them)
- **No WhatsApp foreground** → clarifier is inert (`isWhatsAppActiveContext()` false); Q&A and other
  utterances behave exactly as before; no WhatsApp action is invented. ✓

**Tests added:** `WhatsAppMessageClarifierPhrasesTest` (D1–D7 classification: ambiguous flagged, Q&A
allowed, explicit/specific not flagged) + `WhatsAppBlindSafetyContractTest` (`ambiguousMessageClarifierIsLocalAndNonEgressing`,
`ambiguousMessageClarifierRunsBeforeComposeAndLlm`). One iteration was needed: the first run caught
`"decile que sí"` failing because of the voseo→infinitive rewrite; fixed by adding infinitive forms.

**Build:** `compileDebugKotlin` + `testDebugUnitTest` + `assembleDebug` = **BUILD SUCCESSFUL** (all
tests green). Not installed on device this round (per your rules); the clarifier isn't harness-reachable
anyway (`isWhatsAppActiveContext()` is false under the debug overlay), so it's covered by unit + contract
tests, to be confirmed in the future real-voice smoke.

**Does not break Q&A; no egress of possible message content while WhatsApp is active.**
