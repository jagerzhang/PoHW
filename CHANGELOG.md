# Changelog

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
