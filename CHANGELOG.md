# Changelog

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
