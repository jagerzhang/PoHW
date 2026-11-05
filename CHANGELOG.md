# Changelog

## v6.0.1 (versionCode 11) — review-driven hardening

Two independent reviews of v6.0.0 (feature acceptance + Android/AOSP compliance)
both concluded "do not ship as 5/5 complete". This release fixes the P0/P1 items
and adds the missing behaviour-level tests.

- **P0: overlay window focus state machine.** The overlay now reports
  `onWindowFocusChanged(false)` to the service; the service re-applies
  `WindowManager.LayoutParams` up to 6 times and rebuilds the window once
  (`remove` + `add` of the same View, preserving `QuizFlow` state). Documented
  that clearing `FLAG_NOT_FOCUSABLE` does NOT guarantee the WMS focused window.
  On exhaustion the quiz stays visible (never auto-unlocks) and touch/mouse
  clicks still work.
- **P0: foreground-service 5s contract on every branch.** `startForeground()`
  moved to the top of `onStartCommand()`, covering STOP, rejected STOP, missing
  overlay permission and notification-build failure. `onDestroy()` explicitly
  calls `stopForeground(true)`.
- **Critical: the new `QuizFlow` was wired but never advanced.** `flow.submit()`
  was never called, so the "continue watching" button stayed unclickable and
  disabled options never stuck. Judging now runs through
  `QuizOverlayService.answer()` -> `flow.submit()`, and `bind()` calls `reset()`
  on every question swap. The service no longer keeps its own `wrongAttempts`.
- **Correct-answer confirmation timing.** `onContinue()` is posted via
  `root.post()` instead of removing the window synchronously inside the key /
  click dispatch stack.
- **P1: one-shot STOP token.** The client-side `EXTRA_STOP_VERIFIED` boolean is
  gone. After PIN verification the app issues a random 24-byte token stored with
  `commit()` (60s TTL, deleted on every consume attempt, compared with
  `MessageDigest.isEqual`); the service consumes it before `GuardPolicy`.
  `Build.VERSION.SDK_INT >= 26` uses `startForegroundService` for STOP.
- **P1: boot receivers split.** `BootReceiver` handles only protected
  `BOOT_COMPLETED`; a new `QuickBootReceiver` handles vendor quickboot actions
  without the `RECEIVE_BOOT_COMPLETED` sender permission. Android 12+ may refuse
  to start an FGS from quickboot (not in the exemption list) - failures are
  explicitly caught and logged; `BOOT_COMPLETED` remains the reliable path.
- **P1: `KeyRouter` (pure Java).** Volume DOWN/UP pass through symmetrically;
  long-press repeats are suppressed for confirm / number / DPAD keys; BACK and
  media keys are swallowed. 2x2 highlight adjacency is unit-tested.
- **P1: audio focus API.** `MediaPauseController` uses `AudioFocusRequest` +
  a non-null listener on API 26+ (deprecated 3-arg API only on 21-25). When a
  player was paused by focus loss, abandoning focus is enough - no extra
  `MEDIA_PLAY` that could wake an unrelated MediaSession or double-toggle.
  `resumePlayback=false` is documented as best-effort.
- **P1: online packs were dead code.** `QuizEngine.next()` now mixes built-in
  SQLite questions with edition-filtered online packs (`BANK_RATIO`), routes
  `Subjects.OTHER` packs, and `Question.key()` is length-prefixed and includes
  subject + answerIndex so different-answer questions do not merge in the
  wrong-book.
- **P1: network / settings.** Automatic redirects disabled, `Location` manually
  validated to the same host (max 3 hops), streams always closed; the settings
  URL field is no longer wiped by `refreshRows()`; cleartext preset changed to
  `127.0.0.1` (allowed by network security config); explicit DPAD focus links
  between the three columns.
- **P1/P2: wrong-book & console.** `record()` wraps read+write in a transaction
  and refreshes the summary only after success; `onUpgrade()` is non-destructive;
  first open moved off the main thread; the header always uses the true
  `COUNT(*)`, while the list is capped at `LIST_LIMIT=500` with an explicit
  truncation hint; clear confirmation auto-disarms after 5s. Main console caches
  bank stats / overlay permission instead of re-querying SQLite every second,
  compares strings before `setText`, and reuses field Runnables that are all
  removed in `onDestroy()`; the PIN overlay takes focus itself and blocks
  descendant focus on the underlying console buttons.
- **Tests & gates.** New `BehaviorV601Test` (51 assertions: key timing, quiz
  state machine, one-shot token TTL/replay/process-restart). `tests/run.sh`
  compiles `KeyRouter` + `QuizFlow` and runs 7 suites: **174/174 total**.
  Build static gates now assert behaviour symbols rather than exact line text.
- The final APK is rebuilt from the finished source (the old v6.0.0 dist APK
  predated these fixes); `classes.dex` contains `refreshAsync`, `wireColumns`,
  `setUrlText`, `consumeStopToken` and `rebuildOverlay`.
- Version 6.0.1 / versionCode 11. Release artifacts are signed with the same
  externally-supplied keystore as v4.x/v5/v6.0.0 so `adb install -r` works;
  the default (no `POHW_KEYSTORE`) still generates a random dev key and does
  NOT guarantee signature continuity.

## v6.0.0 (versionCode 10) — real-device feedback from a Xiaomi TV

- **Fix: overlay lost remote-control input on the second quiz.** The service now
  creates a fresh `OverlayQuizView` per quiz instead of re-adding the same View,
  explicitly clears `FLAG_NOT_FOCUSABLE`, and routes DPAD / ENTER / number keys
  inside the root view's `dispatchKeyEvent` with manual highlight painting. Focus
  no longer depends on the window's child-view focus state.
- **Pause background video while answering.** `MediaPauseController` requests
  transient audio focus and dispatches `KEYCODE_MEDIA_PAUSE` when the quiz shows,
  then abandons focus and optionally dispatches `KEYCODE_MEDIA_PLAY` on unlock.
  Media keys are swallowed by the overlay while the quiz is on screen. This is
  best-effort by design: Android has no public API to force-pause a third-party
  player, and no extra permission is requested.
- **Per-subject textbook editions.** Four independent `edition_s0..3` settings
  (math / Chinese / English / other), with the old single `edition` value used as
  the migration default. `BankRepository.load(grade, String[])` filters optional
  online packs by subject edition; quiz metadata and wrong-book entries carry the
  subject's edition. The built-in offline catalog has no edition column, so
  offline draws remain edition-agnostic (documented).
- **Boot autostart controls.** `bootAutostart` (default on, v5-compatible) and
  `autoGuardOnBoot` (default off) switches, a pure-Java `BootPolicy` action
  allow-list, and `BootSettings` opening OEM autostart screens (MIUI / ColorOS /
  EMUI / Funtouch) with a fallback to the app details page.
- **Wrong-answer swap and wrong-question book.** Two wrong attempts on the same
  question swap to a fresh question in place (no window re-attach) and record the
  original into a separate writable `pohw_wrong.db`, deduplicated by a SHA-256
  key of stem + options. `WrongBookActivity` gives a TV-friendly paged review
  with correct answer, last wrong pick, explanation, delete and two-step clear.
  The console shows the count and an entry button.
- Fresh question draws now avoid immediately repeating the previous question.
- Settings screen reorganized into three independently scrollable columns so all
  new rows stay reachable with a TV remote.
- Added `Hashes`, `Subjects`, `AnswerPolicy`, `BootPolicy`, pure-Java policy
  classes and 45 new JVM assertions (`FeatureV6Test`): **123/123 total**.
- Build static guards extended with v6 regression checks; version bumped to
  6.0.0 / versionCode 10. Signing certificate is unchanged from v4.x/v5 for
  in-place `adb install -r` upgrades.

## v5.0.0 (versionCode 9) — PoHW / full offline catalog

- Renamed the project to **PoHW (Proof of Homework)**; version label is now read
  from `PackageManager` at runtime instead of being hardcoded.
- **Full offline catalog**: all 4,319 unique knowledge points from
  `RedSamerai/learning-content` (19 subjects, 21 grade bands) are imported into
  `app/assets/quiz.db`, producing 5,129 locally verifiable four-choice questions.
  See `docs/BANK_STATS.md`.
- Added grade 7-9 support and an optional "all subjects" draw mode, so the full
  K12 catalog is actually reachable from the parent settings.
- **Removed all third-party / paid question packs** from the APK. Online bank
  sources are now strictly opt-in: the parent must enter a URL in settings,
  otherwise the app never touches the network.
- Hardening: `allowBackup=false`, cleartext HTTP disabled by network security
  config, boot receiver validates actions, foreground-service strings moved to
  resources, built-in DB copy is versioned + atomic + fsync'd.
- Build is now reproducible from the repository root (`app/build.sh` +
  `tests/run.sh`), with 78 JVM unit tests and an independent bank verifier.
- Security hardening after an independent Android review: PBKDF2 PIN derivation
  (legacy hash migration), mandatory SHA-256 on downloaded packs, cross-host URL
  rejection, foreground-service contract on rejected STOP, background DB open,
  dedicated notification icon, randomly generated local signing key, and removal
  of unused permissions.

## v4.4.0 (versionCode 8)
- In-place PIN verification overlay when stopping the guard; toggle buttons
  change color/label instead of leaving the screen; dynamic version display.
- Fixed the zombie overlay window on `addView` failure.

## v4.3.0 (versionCode 7)
- Stopping the guard requires the parent PIN, enforced a second time in the
  service (`GuardPolicy`).

## v4.2.0 (versionCode 6)
- Fixed clipped topic text on the quiz overlay; adaptive question font size.

## v4.1.0 (versionCode 5)
- Fixed PIN setup (synchronous commit, mode reset, hardware number keys).

## v4.0.0 (versionCode 4)
- Switched to a built-in SQLite question bank; random local draw, no API.

## v1 - v3
- Demo APK, cross-app overlay, online bank cache, parent password experiments.
