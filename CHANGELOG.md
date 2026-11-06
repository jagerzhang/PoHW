# Changelog

> **版本口径**：对外只保留 3 个正式 Release，按 `MAJOR.MINOR.PATCH` 递增：
> **v1.0.0**（versionCode 12）、**v1.0.1**（13–17）、**v1.0.2**（18–21）。
> 当前开发版本 **v1.1.0**（versionCode 22）尚未打 tag / 发 Release。
> `versionCode` 是单调技术计数，同一版本线内的内部构建不单独发 Release，
> 在本文件中以 `###` 子条目保留逐次改动。`v1`–`v6` 为开发期内部编号，
> 已合并归档为末尾的 `v0.9.0-re`，不对应任何可下载的 Release。
> Android 覆盖安装要求 `versionCode` 严格递增，因此它不随展示版本重置。
> **发布流程**：改动先进 feature 分支 → 开 Pull Request → GitHub Actions
> 自动审核通过 → 合入 main；tag 与 Release 由维护者另行指定后创建。

## v1.1.0 (versionCode 22) — optional hardening for issue #1

Display version 1.0.2 -> **1.1.0**, `versionCode` 21 -> **22**, same release
keystore (SHA-256 `76eb6063b574...26172`). **All four hardening options are
off by default**: an over-the-top install behaves exactly like v1.0.2.
The tag and GitHub Release are cut only when the maintainer says so; the site
release notes are updated at that point.

### Four optional switches (SettingsStore, all commit())

| key | default | effect when enabled |
| --- | --- | --- |
| `unlockStreak` | `1` | require N consecutive correct answers (1-5) before unlocking |
| `strictOneShot` | `false` | one attempt per question; a wrong answer swaps immediately |
| `cooldownEnabled` | `false` | escalating 30s / 1m / 2m / 5m cooldown after wrong answers |
| `questionSource` | `SOURCE_ALL` | prefer the 810 real knowledge / arithmetic questions |

- **`UnlockPolicy`** (pure Java) owns the cross-question streak; `QuizFlow`
  stays a single-question state machine. An unmet streak calls
  `swapQuestion()` which uses `bind()` in place (no add/remove of the overlay
  window, avoiding the focus bug fixed in v1.0.1). `correct()` is only called
  when the streak reaches N.
- **`CooldownPolicy`** (pure Java) stores a **deadline**, not a remaining
  seconds counter. `safeRemaining()` combines wall-clock and
  `elapsedRealtime` snapshots so changing the system clock or rebooting
  re-arms the current tier instead of bypassing or locking forever.
  Cooldown only delays *swapping the question*; the correct-answer path stays
  open, as noted in the issue discussion.
- **Strict mode** flows through `QuizFlow.setStrict()` ->
  `AnswerPolicy.shouldSwap(wrongAttempts, limit)`; with the default off,
  `WRONG_LIMIT` is still 2 and all existing tests are unchanged.
- **Question preference** adds one optional SQL predicate
  (`source='程序出题'`) tried before the existing five-tier fallback. The
  bank file, `quiz.db.version`, `syllabus.json`, `BANK_STATS.md` and the
  distribution / study-scope regressions are untouched. Enabling it makes
  term/unit filtering degrade to grade-only (those 810 rows have `term=''`),
  which the settings screen states explicitly.

### Gates and verification

- `LAYOUT_CONTRACT_OK 180` (10 new assertions pin the four defaults, the
  policy symbols and the live-code streak reset), `JVM_TESTS_OK 280`
  (new `UnlockPolicyTest` 16 + `CooldownPolicyTest` 19).
- `SYLLABUS_CHECK_OK books=12` / `STUDY_SCOPE_OK` / `BANK_DISTRIBUTION_OK`.
- `MUTATION_SUITE_OK 57/57` (56 negative mutations + 1 dev-sign build gate):
  all four defaults, `onCorrect()` returning true, a commented-out
  `unlock.onWrong()`, and a bypassed question preference are all rejected.
- `BUILD_OK version=1.1.0 code=22`; v1/v2/v3 signatures valid, certificate
  continuous; `src.zip` clean and contains `.github/workflows/ci.yml`.

### versionCode 24 — new cleaned bank, difficulty preference, attribution

Display version stays **1.1.0** (not yet tagged), `versionCode` 23 -> **24**, same
release keystore (SHA-256 `76eb6063b574...26172`).

- **Built-in bank replaced.** `app/assets/quiz.db` now has **4733 questions**
  (860 self-generated for kindergarten-grade 2, 3874 cleaned third-party).
  The old 5129-row catalog-location bank is gone. Schema keeps the original 17
  columns and adds `difficulty` / `difficulty_level`; `knowledge`, `chapters`,
  `meta` tables and `quiz.db.version` are preserved so `QuizDb.open()` and
  over-the-top upgrades keep working.
- **Third-party questions were cleaned per question** over four passes
  (10632 -> 3874; 6758 rejections with reasons). **No written licence from the
  source site; personal non-commercial use only.** The old README claim that the
  APK contains no third-party question text is now removed, and `docs/ATTRIBUTION.md`
  is shipped in the repo and must not be deleted.
- **Difficulty preference.** New `DifficultyPolicy` (pure Java): 0=unlimited
  (default), 1=low, 2=mid, 3=high, with **80/20 weighted draws** rather than a
  hard filter, because the source difficulty coefficient is crowd-sourced and
  noisy (631 identical-question groups, 83% disagree by >0.1). Settings key
  `difficultyLevel`, one new settings row, service injects into `QuizEngine`,
  and `QuizDb.random()` appends the `difficulty_level` predicate to **every**
  fallback layer including the final unconstrained one.
- Original questions were augmented with 180 harder items so the clean tier
  has all three levels (overall low 1584 / mid 1615 / high 1534).
- **Honest degradation**: the new bank has no `term` / textbook-unit data, so
  study-scope filtering falls back to grade/subject. `verify.py`,
  `bank_distribution_check.py` and `study_scope_check.py` gained explicit
  bank-v2 branches that keep the old schema-2 logic intact for history.
- Gates: `LAYOUT_CONTRACT_OK 192`, `JVM_TESTS_OK 293` (new
  `DifficultyPolicyTest` 13), `MUTATION_SUITE_OK 64/64` (63 negative mutations
  + 1 dev-sign build gate), `BUILD_OK version=1.1.0 code=24`.

### versionCode 23 — question preference no longer leaks catalog questions

Bug report: with "仅知识/计算题" selected, the TV still showed
"知识点「几何：时分秒」是哪一个年级段的学习内容？" (a catalog-location
question). Real SQL reproduction:

- The 810 real knowledge questions all have `term=''` and topic names like
  `乘法` / `词汇` / `古诗名句`, not textbook unit names. Adding the term or
  unit predicate (`term IN ('上','全')`, `topic LIKE '%：...'`) to the
  preference predicate returns **0 rows**.
- The old code tried the preference predicate only on the strictest layer and
  then fell through to the original five-tier fallback with **no** `source`
  constraint, so it drew from all 5129 rows — 84.2% of which (4319) are
  catalog-location questions.

Fix: `QuizDb.random()` now returns from a dedicated two-layer chain when
`knowledgeOnly` is true — `where + KNOWLEDGE_ONLY_SQL`, then
`poolWhere + KNOWLEDGE_ONLY_SQL` — with the `source='程序出题'` predicate on
**every** layer and no term/unit filtering (they do not apply to these rows).
If both layers are empty (e.g. the OTHER subject pool has no non-core
knowledge questions) it returns null and `QuizEngine.next()` falls back to
the parametric math / word generators, which are also real questions.
The default path (`knowledgeOnly=false`) is byte-for-byte unchanged.

Settings now show "不适用（仅知识题模式）" for 学习学期 / 已学单元 and block
their click handlers with a hint while the preference is on. Gates: contract
180 -> **183**, mutations 57/57 -> **59/59** (two new cases: dropping the
source constraint from the fallback, and disabling the knowledge-only chain).

### Honest boundaries

- Restart still resets `remainSec` (a persisted cooldown is converted back
  into the countdown on guard start), and `bootAutostart` remains off by
  default, so a TV reboot does not immediately restore the overlay.
- Guessing odds are unchanged by default (50% with two attempts per
  question); the issue's 25%^N figures require enabling strict mode.
- Question preference reduces the bank to 810 rows with no term / unit data
  and no non-core subjects, so study scope and the fourth weight slot degrade.

## v1.0.2 (versionCode 18–21) — release versioning, assets and MR workflow

> 内部构建 18–20 当时仍挂 `1.0.1` 展示标签，本版本线统一归并为 **v1.0.2**；
> 对外只发布 versionCode 21 的成品。GitHub 上共 3 个 Release，与官网
> 「版本更新记录」的 3 张卡片一一对应。

### versionCode 21 — release versioning, release assets, MR workflow

Display version moves 1.0.1 -> **1.0.2**, `versionCode` 20 -> **21**, signed
with the same release keystore (SHA-256 `76eb6063b574...26172`).

- **Three releases, not nine.** `site/script.js` now renders exactly three
  aggregated cards (v1.0.2 / v1.0.1 / v1.0.0) instead of nine build rows all
  labelled v1.0.1. `versionCode` ranges are explicit on each card.
- **Release assets refreshed.** All three GitHub Releases carry an APK and a
  `PoHW-src.zip` built from that release line with the same certificate;
  `v1.0.2` is a new tag at the merged main HEAD. The two older release notes
  were rewritten to cover their whole version line and drop debug-bridge
  install commands in favour of USB-drive sideload.
- **CHANGELOG archive.** Dev builds `v1`-`v3` / `v4.x` / `v5.0.0` / `v6.0.0`
  / `v6.0.1` are collapsed into one `## v0.9.0-re (versionCode 1-11)` entry;
  the three formal releases are `##` sections with per-build `###` children.
- **README** no longer documents the static promo site (its own `site/`
  directory, deploy command and preview URL). It now documents the release
  workflow instead.
- **Alipay QR replaced.** `app/res/drawable-nodpi/donate_alipay.png` is a fresh
  945x1050 crop from the newly supplied poster; the QR finder patterns, white
  quiet zone are intact, and the payee caption is now `Jager(*戈)`, matching the
  WeChat card glyphs and colour. The site still publishes no QR image.
- **Release workflow.** New `.github/workflows/ci.yml` runs `layout_contract`,
  JVM tests, syllabus / study-scope / distribution checks and a full APK build
  on every feature-branch push and PR to main. Changes now go
  feature branch -> PR -> auto review -> merge; tags and Releases are cut only
  when the maintainer says so. Branch protection on `main` requires the
  `auto-review` check.
- Gates: `layout_contract.py` **170** assertions, `mutation_suite.py`
  **49 negative mutations + 1 dev-sign build gate (50/50)**; `[0/9]` pins the
  three site versions, the archive heading and the CI workflow.

### versionCode 20 — site cleanup, USB-drive sideload, paired bank row

Display version stays **1.0.1**; `versionCode` moves 19 -> 20 with the same
release keystore (SHA-256 `76eb6063b574...26172`).

- **Site stops promoting donations.** The `微信 / 支付宝打赏` feature card, the
  whole `#donate` section, the footer link and both public QR PNGs
  (`site/assets/donate_wechat.png`, `donate_alipay.png`) are removed from the
  promo page. The in-app `DonateActivity` is unchanged: static QR resources,
  no payment SDK, no orders, no network requests. The code16 entry below still
  records the feature as history, not as promotion.
- **USB-drive sideload is the documented install path.** Real-device testing
  confirmed the APK installs straight from a USB stick via the TV file manager;
  no computer-to-TV debug link is needed. Every user-facing debug command was
  removed from README, CHANGELOG, docs, the site and both in-app hints.
  Password recovery and stuck-overlay recovery now point at
  `设置 → 应用管理 → PoHW → 清除数据 / 强制停止`. A new contract assertion
  scans all user-facing files for such a command word.
- **Footer** is now `© 2026 张戈博客 保留所有权利 · Proof of Homework`, with
  `张戈博客` linking to https://zhang.ge/ . The year is fixed in HTML and the
  JS year-overwrite block is gone.
- **Settings:** `自动更新` moved out of the `01 出题基础` card and now sits
  side by side with `检查题库更新` in the `05 在线题库源` card (check on the
  left, auto-update value on the right). Both share one `track()` focus row;
  DPAD left/right moves inside the pair via `bankPair`, up/down still follows
  `wireVerticalFocus()`. The offline note and donate entry keep their order
  below the row.
- Gates: `layout_contract.py` 144 -> **161** (site / USB / footer / paired-row
  assertions), `mutation_suite.py` 37 -> **43** negative cases (+ 1 dev-sign
  build = **44/44**), `[0/9]` pins the paired-row symbols. `LAYOUT_CONTRACT_OK 161`,
  `JVM_TESTS_OK 245`, `MUTATION_SUITE_OK 44/44`, `BUILD_OK version=1.0.1 code=20`.

### versionCode 19 — unified slogan: 答对题目，无限续租！

Display version stays **1.0.1**; `versionCode` moves 18 -> 19 with the same
release keystore (SHA-256 `76eb6063b574...26172`).

- Replaced the old `答对一题，续租 30 分钟` wording everywhere: site title,
  hero tagline, TV illustration caption, branding explanation, footer, and the
  text drawn into `tv_banner.png`. New wording: **答对题目，无限续租！**.
- `tools/branding/make_icons.py` regenerated; `app/res/drawable-xhdpi/tv_banner.png`
  and `site/assets/tv_banner.png` are byte-identical again. Launcher / status
  icons are pixel-identical (only the banner text changed).
- `无限续租` means there is no lifetime cap: each correct answer renews one
  configured interval and the quiz returns when the lease expires. It does NOT
  mean one answer removes the quiz forever (documented in the site release note).
- New `[0/9]` build gates and 4 `layout_contract.py` assertions (140 -> 144)
  forbid the legacy wording from returning; `mutation_suite.py` adds a negative
  case (37 -> 38). `LAYOUT_CONTRACT_OK 144`, `JVM_TESTS_OK 245`,
  `MUTATION_SUITE_OK 38/38`, `BUILD_OK version=1.0.1 code=19`.

### versionCode 18 — study scope: term + ordered textbook units (plan B)

Display version stays **1.0.1**; `versionCode` moves 17 -> 18 with the same
release keystore (SHA-256 `76eb6063b574...26172`).

- Parent settings gain `学习学期` (全学年 / 上册 / 下册, default 全学年) and
  `已学单元` (不限 / 第 N 单元, default 不限). Both are new SharedPreferences
  keys, so an in-place upgrade keeps the old unrestricted draw range.
- New `app/assets/syllabus.json`: **12 ordered books** for primary grade 1–6
  math (upper / lower term), **105 units / 134 aliases**, covering every distinct
  `topic` unit name in `quiz.db`. Aliases such as `时、分、秒` / `时分秒` are
  merged into a single unit. `tools/quizbank/build_syllabus.py --check` rejects
  any alias that is missing, stale, duplicated or contains a SQL wildcard.
- New dependency-free `Json` parser and pure-Java `Syllabus`; Android asset
  loading is isolated in `SyllabusAssets` so JVM tests stay android-free.
  `Syllabus.termWhere()` uses `term IN ('上','全')` / `('下','全')`, which also
  excludes the 810 un-termed 综合练习 questions; `unitWhere()` / `unitArgs()`
  bind both `"%：" + alias` and the bare alias for every unit.
- `QuizDb.random()` now accepts the scope and relaxes constraints in order:
  same grade + subject + term + unit -> + term -> + grade + subject ->
  same subject across grades -> full bank. The first four layers always keep the
  subject constraint. Chinese / English / other subjects and grades 7–9 have no
  reliable unit order, so they degrade honestly to term-only filtering; the
  full-year option disables the unit selector entirely.
- Home status line and the settings card report the active scope; the settings
  note spells out the grading boundaries and the fallback order.
- New `site/` static promo page (hero, 10 feature cards, install steps, download,
  release timeline, FAQ) and deployment to Tencent Cloud EdgeOne Makers
  (`pohw-home`, `makers-optiyqkj3jjq`). `src.zip` now includes `site/` when present.
- Verification: `LAYOUT_CONTRACT_OK total=140` (114 -> 140),
  `JVM_TESTS_OK total=245` (204 -> 245, new `SyllabusTest` 41 assertions),
  `SYLLABUS_CHECK_OK books=12`, `STUDY_SCOPE_OK pools=4 books=12` (term/unit
  isolation, empty-term exclusion, graceful degradation, four fallback layers,
  default-scope compatibility), `BANK_DISTRIBUTION_OK`, `MUTATION_SUITE_OK 37/37`,
  `BUILD_OK version=1.0.1 code=18`; APK now ships `assets/syllabus.json`.

## v1.0.1 (versionCode 13–17) — UI consolidation and weighting fixes

> tag `v1.0.1` points at the first commit of this line; the Release assets are
> the final build (versionCode 17). Per-build details below.

### versionCode 17 — bank note placement; question-bank granularity study

Display version stays **1.0.1**; `versionCode` moves 16 -> 17 with the same
release keystore (SHA-256 `76eb6063b574...26172`).

- Move the `默认完全离线：内置全量 K12 题库...` note from after the donation
  button to directly below `检查题库更新` in the online-bank card: it documents
  the bank-source switch, not the donation. A new source-level ordering
  assertion pins the four elements (`检查题库更新` < offline note < donate
  button < donate note), so `tests/layout_contract.py` grows 113 -> **114**.
- Read-only study of whether the bundled SQLite bank can scope questions by
  term / textbook unit, documented in `docs/QUESTION_BANK.md`. **No
  quiz-drawing logic changed**: `QuizDb.random()` still filters on
  `grade + subject` only. Implementation waits for a product decision.
- Verification: `LAYOUT_CONTRACT_OK total=114`, `JVM_TESTS_OK total=204`,
  `BANK_DISTRIBUTION_OK` (incl. `FALLBACK_POOL_OK`), `MUTATION_SUITE_OK 29/29`,
  `BUILD_OK version=1.0.1 code=17`, v1/v2/v3 signatures valid.

### versionCode 16 — settings polish, launch-to-guard, donation, branding

Display version stays **1.0.1**; `versionCode` moves 15 -> 16 with the same
release keystore (SHA-256 `76eb6063b574...26172`), so a direct
over-the-top install is a real upgrade.

- **Row gap.** Every settings row, button and note now goes through `rowLp()`
  (`ROW_GAP_DP=12`, 6dp on each side); card spacing moves 4/10 -> 6/14dp and
  the card header uses `WRAP_CONTENT` + 28dp minHeight so font scale 1.5 no
  longer clips it. This fixes buttons visually glued together on the TV.
- **Wording.** `开启（开机即守护）` becomes `开启（启动即守护）`; the home
  status line uses the same wording.
- **Defaults (fresh installs).** `bootAutostart` false (parent must opt in and
  grant the vendor autostart permission), `autoGuardOnBoot` true, `pauseOnQuiz`
  and `resumePlayback` true. SharedPreferences keys are unchanged so an
  in-place upgrade never resets a parent's own choices.
- **Launch = guard.** New pure-Java `LaunchPolicy.shouldAutoGuard()` gates the
  cold start on four conditions (cold start, switch on, overlay permission
  granted, service not already running). `MainActivity.onCreate()` delegates to
  it; returning from background only runs `onResume`, so a guard the parent
  stopped with PIN is not silently re-enabled. The boot broadcast path stays
  in `BootPolicy`; both share the same switch.
- **Donation.** A `打赏作者 · 请喝一杯咖啡` button sits directly below the bank
  update option in parent settings. `DonateActivity` shows full-screen QR codes,
  DPAD left/right switches WeChat Pay / Alipay, BACK exits. The two QR cards
  are cropped from the posters supplied by the author (quiet zone and signature
  preserved) and shipped as `res/drawable-nodpi/*.png`. There is no payment SDK,
  no order and no network call: scanning and paying happen on the phone.
- **Branding.** New reproducible `tools/branding/make_icons.py` (PIL only)
  draws a dark-navy rounded icon with an amber timer arc, a screen and a mint
  checkmark; it emits launcher icons at 5 densities, a 640x360 TV banner and
  2 white status-bar glyphs.
- **Gates.** `tests/layout_contract.py` grows 88 -> **113** assertions;
  `tests/mutation_suite.py` grows 21 -> **29** mutations (row gap, four
  defaults, launch policy, donation position/DPAD/resources, branding);
  `FeatureV6Test` adds 6 `LaunchPolicy` assertions and updates F1-F4, so JVM
  tests grow 198 -> **204**.
- Verification: `LAYOUT_CONTRACT_OK total=113`, `JVM_TESTS_OK total=204`,
  `BANK_DISTRIBUTION_OK` (incl. `FALLBACK_POOL_OK`), `MUTATION_SUITE_OK 29/29`,
  `BUILD_OK version=1.0.1 code=16`, v1/v2/v3 signatures valid. TV-side checks
  still needed: 12dp gap appearance, QR scannability at distance, DPAD channel
  switch focus chain, cold-start auto-guard vs PIN stop, new icon/banner.

### versionCode 15, final — review-driven hardening

Display version and `versionCode` are unchanged; this is the final hardened
build of the four-slot weighting change, signed with the same release keystore
(SHA-256 `76eb6063b574...26172`) and shipped under the same artifact names.
An independent read-only review (`weighting-reviewer`) returned CONDITIONAL PASS
with no P0, 4 x P1 and 6 x P2. All code findings are fixed here.

- **P1-1 route gate.** `boolean other = s == Subjects.OTHER && allSubjects;`
  had no assertion anywhere: breaking it left every test green. The literal is
  now pinned by `app/build.sh`, `tests/layout_contract.py` and
  `tests/bank_distribution_check.py`; mutation Q covers it.
- **P1-2 double binding.** The main query and its `COUNT(*)` OFFSET subquery
  each need the WHERE args. A single binding throws inside `rawQuery`, was
  caught silently and degraded to an arbitrary full-bank question. Both
  `new String[args.length * 2]` and the two `System.arraycopy` calls are now
  asserted; mutation R covers it.
- **P1-3 comment decoy.** `bank_distribution_check.py` used `re.search` over
  raw source including comments, so a commented fake SQL could pass. It now
  strips comments (line + block state machine) and extracts the same
  `POOL_*` named constants used by `QuizDb.java`; mutation S covers it.
- **P1-4 pool-preserving fallback.** Empty pools used to fall back to
  `WHERE grade=?` / `pick("", null)`, silently restoring the original bug.
  Fallback now drops only the grade (`POOL_CORE_ALL` / `POOL_OTHER_ALL`) and
  only then the whole bank. Empty pools use `MAX(1, (SELECT COUNT(*) ...))`
  so `OFFSET` is never NULL, and `QuizDb.hasOtherQuestions()` disables the
  fourth slot when no non-core questions exist. `FALLBACK_POOL_OK` plus
  mutations T/U cover it.
- **P2.** `SubjectWeights.otherAvailable` now defaults to `false` and is set
  only after `QuizEngine` confirms a real non-core pool; `setBank(null)` also
  refreshes availability; the two-arg `random(grade, subject)` overload that
  silently drew math is deleted; SQL failures are logged via `Log.e`; stale
  197/178 counts, the fourth-row UI comment and the settings note are fixed.
- **Gates.** `tests/layout_contract.py` grows 69 -> **88** assertions.
  `tests/bank_distribution_check.py` is rewritten to share the Java SQL
  constants, strip comments, verify double binding and replay the three-tier
  fallback (new `FALLBACK_POOL_OK`: grade-3 english pool and the non-core pool
  emptied, fallback still keeps the subject constraint).
  `tests/mutation_suite.py` is now committed and reproducible (**21/21**),
  including the dev-signed build rejected by the certificate gate.
- Verification: `LAYOUT_CONTRACT_OK total=88`, `JVM_TESTS_OK total=198`
  (`SubjectWeightsTest` 20 assertions, new B20), `POOL_ISOLATION_OK grades=9
  pools=4 draws=2160`, `DISTRIBUTION_OK` grade 3
  42.54/24.60/16.06/16.80 and grade 9 42.26/24.76/15.26/17.72
  (expected 41.67/25.00/16.67/16.67), `CORE_ONLY_OK other=0 draws=5000`,
  `FALLBACK_POOL_OK`, `BANK_DISTRIBUTION_OK`, `MUTATION_SUITE_OK 21/21`,
  `BUILD_OK version=1.0.1 code=15`, v1/v2/v3 signatures valid.
  TV DPAD, IME, font-scale and 640dp narrow-screen checks still need a
  physical Xiaomi TV pass.

### versionCode 15 — four-slot subject weighting (initial)

Display version stays **1.0.1**; `versionCode` moves 14 -> 15 with the same
release keystore (SHA-256 `76eb6063b574…26172`). Initial implementation
(commit `ceae012`); final hardened numbers are in the entry above.

- **Bug fixed: weights were bypassed in all-subjects mode.** `QuizDb.random()`
  previously switched to `WHERE grade=?` (gradeWide) whenever `allSubjects`
  was on, so the configured 5:3:2 ratio did not control which subject the
  question came from; the share of non-core subjects was decided by bank size
  (~7% in grade 3, ~58% in grade 9), and the hardcoded 15% OTHER branch only
  fired when an online question pack existed. Core-subject questions were also
  labelled with the roulette-selected pool instead of their real subject,
  corrupting wrong-book subject names.
- **Four slots.** New pure-Java `SubjectWeights` holds math/chinese/english/other
  weights (default 5:3:2:2, range 0-5). The fourth slot is zeroed when
  all-subjects is off, and also when neither the builtin DB nor an OTHER online
  pack is available, so a missing bank can never degrade into a math fallback.
  `QuizEngine` delegates `pickSubject()` to it; the 15% constant and the
  `OTHER -> MATH` downgrade are removed.
- **Two SQL pools.** `QuizDb.random()` now uses `grade + subject` for the three
  core subjects and `grade + subject NOT IN (...)` for other subjects, with
  same-grade then full-bank fallbacks. `QuizDb.pick()` derives `q.subject` from
  the real bank subject via `subjectFromBank()`.
- **UI.** The fourth subject row is an adjustable weight button (shows
  "not active" when all-subjects is off but can be preset); the home status
  line shows all four weights. Existing `w0/w1/w2` survive an in-place upgrade.
- **Gates.** `tests/layout_contract.py` grows to 69 assertions (forbids
  gradeWide / 15% / `int[3]` / legacy labels); new
  `tests/bank_distribution_check.py` extracts the real SQL from `QuizDb.java`
  and exercises the bank (9 grades x 4 pools x 60 draws, two 5000-draw
  distribution checks, zero-OTHER check), wired into `[0/9]`. A regex-trap in
  the build gate was fixed (`grep -qF` for `new int[Subjects.COUNT]`).
- Verification: `LAYOUT_CONTRACT_OK total=69`, `JVM_TESTS_OK total=197`
  (new `SubjectWeightsTest`, 19 assertions), `BANK_DISTRIBUTION_OK`
  (grade 3: 42.54/24.60/16.06/16.80, grade 9: 42.26/24.76/15.26/17.72,
  expected 41.67/25.00/16.67/16.67), `MUTATION_SUITE3_OK 16/16`,
  `BUILD_OK version=1.0.1 code=15`, v1/v2/v3 signatures valid. TV DPAD, IME
  and font-scale checks still need an on-device pass.

### versionCode 14 — home grid & single-column settings

Display version stays **1.0.1** (no version-number churn); `versionCode` moves
13 -> 14 so a direct over-the-top install still upgrades, signed with the same release
keystore (SHA-256 `76eb6063b574…26172`).

- **Home grid 3x2.** Six action buttons move from 2 columns x 3 rows to
  3 columns x 2 rows. `actionButton()` now uses `weight=1` equal-width cells
  instead of a hardcoded 190dp; the title row grows to 46dp to avoid clipping.
  `wireGrid()` wires explicit `(row, col)` DPAD neighbours in all four directions.
- **Settings single-column card flow.** The three side-by-side `ScrollView`s are
  replaced by one centred gutter column with five numbered cards (quiz basics /
  subjects / boot & playback / wrong-book / online source). Each row is a fixed
  label plus a value button that fills the column by weight; notes wrap instead
  of being clipped. Change-PIN and Save move into a fixed footer bar.
- **Focus.** `wireVerticalFocus()` links focusable controls row-by-row across
  nested cards; `wireSubjectRows()` still handles edition <-> weight, and the
  URL field plus footer buttons join the chain. `wireColumns()` / `addColumn()`
  are removed.
- **Independent review hardening.** A read-only review found no P0 and one
  P1: `build.sh` verified signature validity but not signer identity, so a
  default build silently produced an APK that cannot upgrade an installed
  release. `[8/9]` now pins and compares the APK certificate SHA-256
  (`76eb6063b574…26172`) and exits on mismatch; local experiments must opt in
  with `POHW_ALLOW_DEV_SIGN=1`. Additional fixes: undefined left/right focus
  targets are pinned to self (EditText keeps cursor keys), row buttons use
  `WRAP_CONTENT` + 44dp min-height for large font scales, and the contract now
  covers `setFillViewport`, `Ui.fill(1)` and wrap-content scroll children.
- **Gates.** `tests/layout_contract.py` adds 40 source-level assertions wired
  into `[0/9] static guards`; call counts run on code stripped of both `//`
  and `/* */` comments via a block state machine. Eight negative mutations
  (2-column regression, legacy column helper, line/block-commented focus
  chains, removed fillViewport, fixed-height scroll area, wrong cert pin,
  dev-signed build) were all rejected by the contract or `build.sh`.
- Verification: `LAYOUT_CONTRACT_OK total=40`, `JVM_TESTS_OK total=178`, bank
  verifier PASS, full `aapt2 -> javac -> d8 -> zipalign -> apksigner` build,
  v1/v2/v3 signatures valid, `versionCode='14' versionName='1.0.1'`,
  `MUTATION_SUITE2_OK 8/8`. TV DPAD behaviour, font scale 1.5 and the URL
  field IME still need an on-device pass.

### versionCode 13 — console & settings UI consolidation

- **Merged guard toggle.** `btnStart`/`btnStop` replaced by a single `btnGuard`.
  `GuardState.toggleColor()` / `toggleLabel()` drive the state: idle = ON_GREEN
  `#1D9E75` + "开启守护", running = STOP_RED `#A32D2D` + "关闭守护"; focus
  highlight stays BLUE. Colour now expresses the *action currently available*
  instead of a disabled state. The four legacy methods and `mutuallyExclusive()`
  are kept for the existing JVM suite; `toggleDistinct()` and
  `toggleAlwaysAccented()` are new. Stopping still passes through the in-place
  PIN overlay and never leaves the page.
- **Bank update entry moved.** The duplicate home-screen "检查题库更新" button is
  gone. `SettingsActivity` already had the same action ("立即检查更新"); it is
  renamed "检查题库更新", sits with the online-source URL/status card, and now
  inherits the empty-URL hint from the removed home button. The console keeps
  only read-only bank status lines.
- **Subject rows consolidated.** `addSubjectRow()` renders one row per subject as
  `name | edition | weight` with two independently focusable buttons. The three
  weight rows in column 1 and four edition rows in column 2 (7 rows) become 4.
  "其他学科" has no independent weight and shows a disabled dash. Columns are now
  "quiz basics + subjects" / "boot, playback + wrong-book" / "online source +
  account".
- **TV focus chain.** `wireColumns()` still links columns; `wireSubjectRows()`
  then overrides intra-row left/right so DPAD moves between edition and weight,
  while the row's outer edges still cross columns.
- **Gates & tests.** New semantic build guards assert `GuardState.toggleColor`,
  `GuardState.toggleLabel`, `toggleGuard()`, `addSubjectRow`, `wireSubjectRows`,
  and forbid a home-screen bank-update button. `StopStateTest` gains 4
  assertions: **178/178 total** across 7 suites.
- Version 1.0.1 / versionCode 13 (PATCH: UI consolidation, no new capability).
  Same externally-supplied keystore, so over-the-top upgrade works.

## v1.0.0 (versionCode 12) — first stable release

Two independent reviews of the dev build labelled v6.0.0 (feature acceptance + Android/AOSP compliance)
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
- Version 1.0.0 / versionCode 12. Release artifacts are signed with the same
  externally-supplied keystore as all earlier dev builds so over-the-top upgrade works;
  the default (no `POHW_KEYSTORE`) still generates a random dev key and does
  NOT guarantee signature continuity.

## v0.9.0-re (versionCode 1–11) — 开发期归档

> 本节是 `v1`–`v6` 开发期内部迭代的合并归档，**不是可下载的 Release**。
> 原始编号曾随功能跳变（v4.1 → v6.0），不符合语义化版本；commit 历史完整保留，
> 可按需 `git log` 追溯。下列为当时的关键里程碑摘要。

- **v1–v3**：demo APK、跨应用答题遮罩、在线题库缓存、家长密码原型。
- **v4.0.0 (code 4)**：改用内置 SQLite 题库，本地随机抽题、不再依赖 API。
- **v4.1.0 (code 5)**：修复 PIN 设置（同步 commit、模式复位、硬件数字键）。
- **v4.2.0 / v4.3.0 (code 6–7)**：题面文字裁切与自适应字号；停止守护需家长 PIN。
- **v4.4.0 (code 8)**：就地 PIN 验证浮层、按钮换色而不退出界面、动态版本展示；修复悬浮窗僵尸窗口。
- **v5.0.0 (code 9)**：定名 **PoHW / Proof of Homework**；导入 4,319 个知识点 →
  5,129 道可校验四选一题，覆盖 1–9 年级 19 学科；在线题库源改为家长自选、
  默认完全离线；安全加固（PBKDF2 PIN、SHA-256 校验、`allowBackup=false`、禁明文 HTTP）。
- **v6.0.0 (code 10)**：小米电视真机反馈：暂停 / 恢复播放、分学科教材版本、
  开机自启、答错 2 次换题与错题库、第二次弹题遥控器焦点修复、三栏设置页。
- **v6.0.1 (code 11)**：两轮独立复审后的 P0/P1 加固，最终由 v1.0.0 承接。

> 已下架 `v5.0.0` / `v6.0.0` / `v6.0.1` 三个历史 Release；部分内部测试类名
> （如 `BehaviorV601Test`）沿用开发期标识，不代表发布版本。
