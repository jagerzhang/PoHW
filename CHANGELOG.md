# Changelog

> **版本口径**：对外发布 12 个正式 Release，按 `MAJOR.MINOR.PATCH` 递增：
> **v1.0.0**（versionCode 12）、**v1.0.1**（13–17）、**v1.0.2**（18–21）、
> **v1.2.0**（25–28）、**v1.2.1**（29–31）、**v1.2.2**（33）、**v1.2.3**（35）、
> **v1.2.4**（37）、**v1.2.5**（39）、**v1.2.6**（41）、**v1.2.7**（43）、
> **v1.3.0**（44）。
> `v1.1.0` 是未发布的开发版本线，成果由 v1.2.0 承接。
> `versionCode` 是单调技术计数，同一版本线内的内部构建不单独发 Release，
> 在本文件中以 `###` 子条目保留逐次改动。`v1`–`v6` 为开发期内部编号，
> 已合并归档为末尾的 `v0.9.0-re`，不对应任何可下载的 Release。
> Android 覆盖安装要求 `versionCode` 严格递增，因此它不随展示版本重置。
> **发布流程**：改动先进 feature 分支 → 开 Pull Request → GitHub Actions
> 自动审核通过 → 合入 main；tag 与 Release 由维护者另行指定后创建。

## v1.3.0 (versionCode 44) — LAN admin, group quiz, wake resilience, UI regression

Display version 1.2.7 -> **1.3.0**, `versionCode` 43 -> **44**, same release
keystore (SHA-256 `76eb6063b574...26172`). Twelfth published release.
This release collects four work packages that were merged into `main` as
unpublished test builds, plus the fixes found by the first full emulator UI
regression. The question bank stays at bank-v5 / 3989 questions.

- **手机管理后台回归修复**：局域网管理页 18 项自动化回归发现并修复
  ① `/api/config` 只有最后一个数字字段生效（`jsonInt` 会把下一个 key 的
  引号当值）；② 路径穿越请求返回 200 首页（现只放行白名单路径并拒绝 `..`
  与编码变体）；③ 状态接口版本硬编码 1.2.7（改为读取真实 versionName）；
  ④ 8099 被同设备其它应用占用时后台静默启动失败（现自动顺延到 8100–8108，
  二维码与实际端口一致）。另补上「家长开启后 APP 重启自动恢复后台」。
- **题库题干兜底**：`questionFitTv()` 在抽题层挡掉 >90 字题干、依赖课文原文
  与多子项编号的题（均为逐题复核已确证的结构问题），不按学科内容判超纲。
- **README 开源化**：首屏补协议/技术栈/快速导航/双后台说明，弱化版本台账。
- **UI 回归补修（模拟器实测）**：局域网模拟器调试通道抓到 4 个选项 54–71 字的
  极端题，18sp + 3 行上限仍把末行裁成半截。`QuizTextSizer.optionSp()` 增加
  14/16/19/22sp 分档（>48 字降到 14sp），选项放宽到 4 行；新增 O13–O15 JVM
  断言、契约断言与负向变异 CR。13–17 字常规长选项实测已完整显示。
- **抽题层过滤超长选项**：82 字选项题（且多为高中语文错标小学 3 年级）在
  14sp / 4 行下仍会被固定高度按钮裁掉末行。`QuizTextSizer.optionsFitTv()`
  设定 48 字上限，`QuizOverlayService.drawFresh()` 最多重抽 12 次跳过这类题；
  `showQuiz()` / `swapQuestion()` 已有判空兜底。新增 O16–O18 JVM 断言、契约
  断言与负向变异 CS。字号分档最终收敛为 24/20/16/14sp（19sp/22sp 档在
  35 字题上仍会顶出固定高度按钮），模拟器采样复验最长 44 字选项完整显示。
- **WP5 开机与常驻**：新增 `ReviveReceiver`（`USER_PRESENT` / `SCREEN_ON` /
  `MY_PACKAGE_REPLACED` / `CONNECTIVITY_CHANGE` / 自定义 `REVIVE`）、
  `GuardWatchdog`（`JobScheduler.setPersisted` + `AlarmManager` 双 15 分钟兜底）、
  `onTaskRemoved` 2 秒拉起、`stopWithTask=false`；`BootPolicy` 扩厂商开机广播白名单，
  新增 `shouldRevive()` 纯逻辑；`SettingsStore.lastStartVia()` 记录启动来源。
  单独 receiver 是为了绕开 `RECEIVE_BOOT_COMPLETED` permission 对非保护广播的过滤。
- **WP5 HOME 抗性**：`ACTION_CLOSE_SYSTEM_DIALOGS` 检测后调用既有
  `rebuildOverlay()` 重抢窗口焦点；新增**默认关闭**的 `GuardAccessibilityService`
  作为系统常驻看门狗（`canRetrieveWindowContent=false`），家长可到系统
  「设置 → 无障碍 → 答题守护」手动开启。**HOME 键本身由系统保留，第三方无法拦截**；
  不开启无障碍时只能做到检测 + 重抢焦点，不能保证盖住桌面。
- **WP1 按组答题**：新增纯 Java `RoundPolicy`（默认一组 5 题、答对 60% 通过、
  `CONTINUE/PASS/FAIL` 三态、整数 `minCorrect()`）；`SettingsStore` 加
  `roundMode`（默认按组）/ `questionsPerRound` / `passPercent`；
  `QuizOverlayService.answer()` 改为组结算，策略对象放 Service 层（跨 `bind()` 保留），
  同组题目用 `usedInRound` 去重；`SettingsActivity` 新增「每组题量」「通过正确率」
  两行（沿用 `addRow` + `track`，不禁用任何按钮，焦点链不变）；`OverlayQuizView`
  的 `metaFor()` 显示第几题 / 需答对几题。答完未达标会重开一组，不会放行。
- **门禁**：JVM 单测 `RoundPolicyTest`（21 项，总计 358）；`layout_contract` 新增
  20 条断言（含「测试构建必须保持 1.2.7 / 43」）；`mutation_suite` 新增
  CG/CH/CI/CJ/CK 五条负向变异。
- **UI 修复（真机截图）**：答题选项长文本第二行被固定高度网格裁掉下半截。
  `OverlayQuizView` 选项网格 186dp → 200dp，按钮加 `setMaxLines(3)` /
  `setIncludeFontPadding(false)` / `setEllipsize(null)`；`QuizTextSizer` 新增
  `optionSp()` 按最长选项统一四按钮字号，并提供纯 Java 的 `optionFits()`
  高度估算；新增 `QuizTextSizerTest`（14 项）。
- **UI 测试方法**：新增 `tools/ui_check/`（`overlay_check.sh` 抓 uiautomator
  dump + screencap，`analyze_dump.py` 筛查长文本可疑节点，README 说明三层验证）。
  远程构建沙箱没有安卓调试桥工具与模拟器镜像，`mac-exec` 当前 SSH 认证失败，因此这一版
  仍需人在本机跑脚本；脚本产物可回传做多模态截图复核。
- **WP3 二维码 + 局域网双后台**：新增零依赖 `QrEncoder`（QR Model 2 /
  V1–V4 / EC-L / Byte / mask0，单块 RS；**Java 输出与标准库 29×29 参考矩阵
  逐位一致**）、`LanServer`（`ServerSocket:8099` + 极简 HTTP/1.1）、`AdminApi`
  （PIN→token、失败 5 次锁 60 秒、白名单 API）、`AdminAssets`（文件名白名单）、
  `PairActivity`（本地渲染二维码 + 大字地址）；`app/assets/admin/` 为 ES5 管理页，
  零外部 CDN。`lanAdminEnabled` **默认关闭**，家长在「06 版本更新 → 手机扫码管理」
  手动开启；**TV 端原有设置页全量保留备用**。
- **文案口径修正**：局域网 ≠ 互联网。官网与 README 从「完全离线、零联网 /
  默认一次网络请求都不发」改为「题库与守护离线可用，不访问互联网；在线题库源、
  局域网管理默认关闭；三项都关闭后不产生任何对外请求」。
- **门禁**：JVM 单测 `QrEncoderTest`（14 项，总计 386）、`QuizTextSizerTest`；
  `layout_contract` 新增选项裁切与 WP3 断言；`mutation_suite` 新增
  CL/CM/CN（选项）与 CO/CP/CQ（WP3）六条负向变异。

- **未验证**：小米电视关机→开机、待机→唤醒、运行中按 Home、切 App、强杀进程、
  拔 WAN 六项真机场景；无障碍服务在厂商省电策略下的存活率；二维码与局域网
  双后台（WP3）、文档重构（WP4）尚未落地。

## v1.2.7 (versionCode 43) — full per-question bank review, bank-v5, eleventh release

Display version 1.2.6 -> **1.2.7**, `versionCode` 41 -> **43** (42 reserved
for the Android 4.0 legacy branch), same release keystore
(SHA-256 `76eb6063b574...26172`). Eleventh published release.

**Scope**: question bank only. No app logic, guard, settings or update flow
changed. Bank `pohw-2026.11.07-bank-v4` -> **`pohw-2026.11.09-bank-v5`**,
3964 -> **3989** questions.

**Method**: all 3964 rows were flagged individually, 169 suspicious items were
read one by one (stem / four options / answer / explanation), 46 drop
candidates were re-read, over-broad rules were tightened, and 14 false-positive
drops were restored by hand. The signed per-question ledger, the flag dump and
the 60 original multiple-choice supplement are committed under
`tools/quizbank/review/`. `tools/quizbank/apply_review.py` reproduces bank-v5
from bank-v4 plus that ledger (idempotent).

**Applied in this release**:

- Drop 34 rows: 32 confirmed-broken items (senior-high Chinese appreciation
  questions that depend on unseen source texts, options spliced with later
  sub-items or a fifth option, multi-part letter questions, one wrong answer),
  one item with an ambiguous answer, and one duplicate transcription.
- Relocate 12 first-stage pinyin / alphabetical-order items from grades 4-5
  down to grade 2, with their difficulty labels re-set.
- Repair glued English tokens in 4 questions that the wordlist rules could not catch
  (`Look!LiMing`, `onhisT-shirt`, `At4:00`, `Lucyhas`, `Jackhas`), 2 typos,
  17 `[]` placeholders, 775 whitespace / underscore stem noise, and 270
  `topic` entries whose volume label contradicted the grade label.
- Insert 59 original four-option questions (one more was merged as an upgraded
  three-step explanation because the same stem already existed). Every new
  item carries a content domain, ability dimension, a curriculum-stage
  difficulty label, a neutral `通用(课标)` edition with empty term/unit, and a
  `判据 → 步骤 → 迁移` explanation. Thin cells: grade 6 Chinese 5 -> 17,
  grade 2 English 14 -> 26, grade 3 Chinese 67 -> 75.
- Recompute `qhash` / `qtype` / `meta` / `knowledge` / `chapters`.
- Two same-stem-different-options pairs keep their original bracket marker so
  that normalising it would not collapse distinct questions into one.

**Deliberately not done in this release** (no rule-based mass rewriting):
65 items whose correct option is much longer than the distractors still need
hand-authored distractors; ~3.5k one-line explanations still need upgrading;
57 possible out-of-stage items (mainly clock-angle questions) are kept and
marked `待核` pending a curriculum check; trailing `( )` blanks are kept
because removing them without semantic rewriting produces half sentences;
textbook edition / unit alignment still needs the student's actual textbook
table of contents.

- Gates: `BANK_QUALITY_OK questions=3989 qtype=3975/14 practice_combos=144`,
  `BANK_DISTRIBUTION_OK`, `STUDY_SCOPE_OK`, `LAYOUT_CONTRACT_OK 285`,
  `JVM_TESTS_OK 337`, `MUTATION_SUITE_OK 84/84`, `BUILD_OK version=1.2.7 code=43`.

## v1.2.6 (versionCode 41) — update card focus chain and feedback fix

Display version 1.2.5 -> **1.2.6**, `versionCode` 39 -> **41** (40 reserved
for the Android 4.0 legacy branch), same release keystore
(SHA-256 `76eb6063b574...26172`). Tenth published release.

**User report (real TV)**: in parent settings, "立即检查版本" and
"下载并安装新版本" appeared dead, and the DPAD could not reach the fixed
footer buttons "修改家长密码" / "保存并返回".

**Root cause**: `wireVerticalFocus()` builds the single-column DPAD chain once
in `buildSettings()`; `firstUsable()` skips disabled views. But `refreshRows()`
called `btnUpdateInstall.setEnabled(p != null)`. With no newer version
available (installed 39 == manifest 39) that button became a **disabled node
inside the chain**, so focus got stuck before the footer. Both onClick paths
also lacked immediate visible feedback when there was nothing to do.

**Changes**:

- Update buttons are never disabled. State is expressed through button text
  (`下载并安装新版本（暂无更新）` / with version), status text and Toast.
- `updateChecking` / `updateDownloading` booleans guard re-entrant taps
  without touching `setEnabled`.
- `refreshRows()` calls `wireVerticalFocus()` after state changes so the chain
  is rebuilt against the current enabled state.
- Both onClick paths show an immediate Toast; check results (including
  failure and "already latest") show a Toast plus status text.
- Contract assertions forbid `btnUpdateInstall.setEnabled(` and pin the focus
  rebuild; negative mutation CF restores the old broken behaviour and is
  rejected. Also fixes `update_manifest.py` cleanup to match `PoHW-*.apk`.

No change to the question bank or the guard logic.

## v1.2.5 (versionCode 39) — optional online version update, ninth release

Display version 1.2.4 -> **1.2.5**, `versionCode` 37 -> **39** (38 belongs to
the Android 4.0 legacy branch), same release keystore
(SHA-256 `76eb6063b574...26172`). Ninth published release.

**Scope**: the network is used only for version updates. Question bank,
guard and answering stay fully offline; no device identifier is uploaded.

- **Site**: `site/update.json` fixed manifest, `site/dl/` holds only the
  latest `latest-main.apk` / `latest-legacy.apk` aliases plus versioned
  `PoHW-v1.2.5*.apk` files; `site/edgeone.json` sets `no-store` on the
  manifest and long caching on APKs. `tools/publish/update_manifest.py`
  generates and verifies the manifest (sha256 / size / channel / URLs).
- **App**: `UpdateConfig` (pure Java: channel, version compare, 24h throttle,
  URL allowlist, manifest parsing), `UpdateChecker` (HTTPS only, no cross-host
  redirects), `UpdateInstaller` (download on explicit parent action, stream
  sha256 + size check, then system installer), `UpdateProvider` (platform
  ContentProvider for a `content://` Uri), `UpdateState` (in-process result).
- **Interaction**: automatic check defaults to **on** and runs at most once
  per 24h on cold start; a new version only shows a hint. Download and install
  require the parent to press "下载并安装新版本" in the new `06 版本更新`
  settings card; the Android installer still needs manual confirmation.
- **Channels**: `main` (minSdk 21) and `legacy` (minSdk 14) are separated in
  the manifest. **The Android 4.0 legacy app does not implement update
  checking this round**; its package stays available for manual download.
- **Docs**: site copy changed from "zero network" to the factual
  "offline guard, optional version check"; new FAQ entry. Historical release
  cards are unchanged.
- Gates: `layout_contract.py` and `build.sh` assert the default-on switch,
  sha256 verification, URL allowlist, private provider, `no-store` rule and
  manifest consistency; three negative mutations (CB/CC/CD) pin them.

- Post-release site fix (no APK change, no new versionCode): all four
  download buttons now use the Makers-hosted
  `https://pohw.zhangge.net/dl/latest-main.apk` direct link instead of the
  GitHub latest-release page; `site/edgeone.json` revalidates `/dl/*` so the
  fixed `latest-*` aliases never serve a stale APK. Historical versions stay
  on GitHub Releases. Pinned by layout contract assertions and negative
  mutation CE.

## v1.2.4 (versionCode 37) — practice layout fix, question-type tags, eighth release

Display version 1.2.3 -> **1.2.4**, `versionCode` 35 -> **37** (36 belongs
to the Android 4.0 legacy branch), same release keystore
(SHA-256 `76eb6063b574...26172`). Eighth published release.

**User report: practice mode showed no question at all.** The practice page
opened, but the card only had a subject label; the stem and all four A/B/C/D
buttons were invisible.

**Root cause:** `PracticeActivity.buildView()` passed `Ui.fill(0)` to the
stem, both option rows and the explanation. `Ui.fill(w)` builds
`LinearLayout.LayoutParams(MATCH_PARENT, 0, w)`, so `w=0` gives the child an
EXACTLY-0 height in a vertical LinearLayout. `TextView.setMinHeight()` cannot
override an EXACTLY measure spec, so the stem collapsed too and the 56dp
buttons were clipped by their zero-height parent row. The guard overlay never
had this problem because `OverlayQuizView` uses `Ui.fill(1)` and fixed dp
heights. The bug dates back to the v1.2.1 practice page, and no contract
asserted the card geometry.

**Changes:**

- `PracticeActivity`: stem `Ui.fill(3)`, each option row `Ui.fixed(64dp)`
  (56dp button + 4dp margins), explanation `Ui.fill(1)` with a 30dp minimum.
- `PracticeConfig.MAX_GRADE` 9 -> 6: the shipped bank only covers grades 1-6,
  so grades 7-9 could only return primary-school questions under a misleading
  label. `PracticeConfigTest` asserts the loop stays within 1-6.
- `tests/layout_contract.py` forbids `Ui.fill(0)` in any app source and
  asserts a positive weight for the stem / explanation plus a real height for
  the option rows; `build.sh` carries the same negative gate on code lines.
- `tests/bank_quality_check.py` replays the real `QuizDb.random()` pool SQL
  for all 144 practice combinations (grade 1-6 x 3 subjects x 4 difficulty
  levels x weighted on/off) and asserts every drawn row has a non-empty stem,
  four distinct non-empty options and a valid answer index.
- Negative mutations BZ (stem back to zero height) and CA (grade cap back to
  9) pin both fixes.

**Also shipped (previously an unreleased code-37 build):**

- **The "仅知识/计算题" preference was a no-op.** Every row in the bank carried
  `source='程序出题'`, so `KNOWLEDGE_ONLY_SQL` filtered nothing. The bank now
  has an explicit `qtype` column: 3950 `knowledge` and 14 `recognition`
  templates, and the predicate is `qtype='knowledge'`. With the preference
  off the whole bank is still available. Negative mutation BY pins it.
- **Second quality pass.** A stratified audit found 52 mis-graded items in
  grades <=3 (direction/方位角, 最简真分数, 小数计数单位, 比例放大, 列成等式);
  curriculum rules move them to grades 4-6, and the scan now reports zero
  such features in the <=3 pool.
- **Short English gluing.** Splitter threshold 5 -> 3 letters, wordlist
  completed (`me`, `than`, `ago` protected), repairing 106 questions; zero
  splittable residues left.
- Bank: 3965 -> **3964** questions (one row removed because repairing its
  options made two answers identical). `quiz.db.version` is
  `pohw-2026.11.07-bank-v4`; grades 1-6 and difficulty levels 1/2/3 intact.
  `bank_quality_check.py` asserts the qtype column, both values and the meta
  counters.

- Gates: `LAYOUT_CONTRACT_OK 253`, `JVM_TESTS_OK 311`,
  `BANK_QUALITY_OK questions=3964 qtype=3950/14 practice_combos=144`,
  `MUTATION_SUITE_OK 79/79`, `BUILD_OK version=1.2.4 code=37`.

## v1.2.3 (versionCode 35) — bank quality rebuild, grade cap, seventh release

Display version 1.2.2 -> **1.2.3**, `versionCode` 33 -> **35** (34 belongs
to the Android 4.0 legacy branch), same release keystore
(SHA-256 `76eb6063b574...26172`). Seventh published release.

User report: a third grader was asked `a×5=a×10` (an algebraic equation),
and English questions had missing spaces between words (`Sheisagirl`,
`I'mgoingto`, `Junefirstis`).

**Root cause** (full read-only audit of all 4733 questions):

- 3490 questions (74%) were `grade=-1` / 通用 with no reliable grade label.
- There were **zero** grade-3+ Chinese/English questions while settings allow
  grades 1-9, so `QuizDb.random()` fell back to `WHERE subject=?` and released
  the entire ungraded pool, including middle-school geometry, percentages,
  olympiad items and glued English text. It was not an occasional bad item;
  the default pool itself was an all-grade mixture.
- Even graded labels were unreliable: `TK00258` arrived tagged as a
  third-grade textbook question but is an algebraic equation.

**Changes**:

- `tools/quizbank/rebuild_bank.py` (new, reproducible) re-grades every
  question from a primary-school curriculum whitelist instead of trusting
  upstream labels: only items that clearly and *solely* match grade 1-3
  keywords may enter the low-grade pool; everything else is graded 4+.
  It repairs glued English words with a wordlist splitter (heuristics avoid
  mis-splitting proper nouns such as `Hebei`), and removes out-of-scope,
  truncated, duplicate and unrepairable questions. **4733 -> 3965**
  questions, all with an explicit grade 1-6; `quiz.db.version` is now
  `pohw-2026.11.07-bank-v3`.
- `QuizDb`: all four pool predicates and the final fallback now carry
  `grade<=?`. No layer can widen past the user's grade, including the
  last-resort query. `countFor` / `countForGrade` use the same cap so the UI
  count matches the real pool. `study_scope_check.py` and
  `bank_distribution_check.py` replicate and assert these caps.
- `tests/bank_quality_check.py` (new CI gate) checks grade labels, distinct
  options, English spacing, duplicate stems, middle-school keywords, algebra
  in grades <=3, and a minimum cumulative pool per grade and subject. Wired
  into `build.sh` and the auto-review workflow.
- Two negative mutations (BW/BX) pin the grade caps; SQL-constant mutation
  anchors retargeted to the new predicates.
- Docs refreshed: `QUESTION_BANK.md` / `BANK_STATS.md` now describe the
  shipped bank instead of the retired 5129-row catalog bank; `DATA_SOURCES.md`
  and README carry the new counts.
- Post-release docs sync: `README.md` and the static site no longer describe
  the retired 5129-question catalog bank. Both now carry the shipped 3965
  questions with grade 1-6 labels, the grade cap on every draw layer, the
  practice mode, the default-on auto-continue setting and a corrected FAQ
  (the bank does have difficulty levels; it has no term/unit data). No APK
  change and no new versionCode.
- Gates: `LAYOUT_CONTRACT_OK 211`, `JVM_TESTS_OK 308`,
  `BANK_QUALITY_OK questions=3965`, `VERIFY = PASS (bank-v2)`,
  `MUTATION_SUITE_OK 76/76`, `BUILD_OK version=1.2.3 code=35`.

## v1.2.2 (versionCode 33) — console layout finalised, sixth release

Display version 1.2.1 -> **1.2.2**, `versionCode` 31 -> **33** (32 belongs
to the standalone Android 4.0 legacy branch), same release keystore
(SHA-256 `76eb6063b574...26172`). Sixth published release; `v1.2.1` and
its uploaded assets stay untouched.

- Console grid finalised after the maintainer's follow-up: row1[1]
  "立即出题" -> "练习模式" (opens `PracticeActivity`); row2[0]
  "练习模式" -> restored "授权悬浮窗" opening the system permission page.
  The parent settings card 03 keeps its own authorization entry as well.
- Carries forward everything from v1.2.1: the free-practice page (subject /
  grade / difficulty, auto-advance on correct answers, running stats),
  guard suspension while practising (the service is never stopped, so
  practice cannot become a bypass), mutually exclusive pause/resume
  signals, and the default-on "答对自动继续" toggle.
- Site gains a sixth release card; the CI metadata assertion, source
  contract and mutation anchors now expect six versions. A matching
  Android 4.0 build is published as `v1.2.2-legacy` (code 34).
- Gates: `LAYOUT_CONTRACT_OK 209`, `JVM_TESTS_OK 308`,
  `MUTATION_SUITE_OK 74/74`, `BUILD_OK version=1.2.2 code=33`; badging
  `versionCode='33' versionName='1.2.2'`, release certificate continuous.

## v1.2.1 (versionCode 29) — practice mode, console reshuffle and release

Display version 1.2.0 -> **1.2.1**, `versionCode` 28 -> **29**, same release
keystore (SHA-256 `76eb6063b574...26172`). Fifth published release.

- **Practice mode** (user request): the console's "授权悬浮窗" grid button is
  replaced by "练习模式", opening a new full-screen `PracticeActivity`. Children
  pick subject (math / Chinese / English / mixed), grade (1-9) and difficulty,
  answer four-choice questions with the remote or number keys 1-4, and see a
  running summary (practiced / correct / streak). Correct answers auto-advance;
  two wrong attempts swap the question. Parameters live in pure-Java
  `PracticeConfig` and never touch the parent's guard settings.
- **Guard suspension, not guard stop**: entering practice calls
  `QuizOverlayService.setPracticeMode(true)` so `showQuiz()` does not fire.
  The service keeps running and the static flag disappears on exit or when
  the process is gone, so practice cannot become a zero-cost bypass of the
  guard. Exiting resumes the normal countdown automatically.
- **Overlay authorization moved** into settings card "03 开机与播放", beside
  boot autostart and the vendor autostart shortcut. When the permission is
  missing the console now shows a hint pointing there instead of jumping to
  the system settings page itself.
- Also included from `versionCode 28`: the mutually-exclusive pause/resume
  signal fix and the default-on "答对自动继续" setting.
- `QuizEngine.nextFrom(int)` exposes per-subject draws without changing the
  guard's weighted roulette (`next()` still delegates to it).
- A matching Android 4.0 legacy build is published as `v1.2.1-legacy`.
- Gates: `LAYOUT_CONTRACT_OK 208`, `JVM_TESTS_OK 308` (new
  `PracticeConfigTest` 15), `MUTATION_SUITE_OK 73/73`,
  `BUILD_OK version=1.2.1 code=29`; site release notes now list five versions.

### versionCode 31 — console button reshuffle (not yet released)

User follow-up: practice mode should take the old "立即出题" slot instead of
replacing the overlay authorization button.

- `MainActivity` row1[1]: "立即出题" -> "练习模式", opening `PracticeActivity`.
  The old `ACTION_QUIZ_NOW` console entry is gone (service handling kept).
- row2[0]: "练习模式" -> **"授权悬浮窗" restored**, with `openOverlaySettings()`
  back in `MainActivity`; the parent-settings card 03 keeps its own entry too.
  The status line again points at the console button.
- Contracts now pin both slots, the absence of "立即出题", and the restored
  system-settings intent; two mutations cover both buttons.
- `versionCode` 29 -> **31** (30 belongs to the v1.2.1-legacy branch); display
  version stays **1.2.1**. No tag / Release yet — awaiting maintainer.
  Site release notes still describe the released code-29 build.
- Gates: `LAYOUT_CONTRACT_OK 209`, `JVM_TESTS_OK 308`,
  `MUTATION_SUITE_OK 74/74`, `BUILD_OK version=1.2.1 code=31`.

## v1.2.0 (versionCode 25–28) — new bank, difficulty preference and release

Display version 1.1.0 -> **1.2.0**, `versionCode` 24 -> **25–26**, same release
keystore (SHA-256 `76eb6063b574...26172`). `1.1.0` was never tagged or released.
The built-in bank replacement (4733 cleaned questions), difficulty preference
and weighted draws are new capabilities, so the unreleased development line is
promoted to a minor version rather than shipping another build under a label
that has no Release. `versionCode` keeps increasing so over-the-top installs
from every earlier build still work.

- Synced the stale `versionCode 22` references in `README.md` and at the top of
  this file with the real build (25); `README.md` had contradicted its own
  version-policy paragraph.
- `app/build.sh`, `AndroidManifest.xml`, `docs/BUILD.md`, the source contract
  (`tests/layout_contract.py`) and the mutation anchors
  (`tests/mutation_suite.py`) now pin `1.2.0` / `25`, including the hardcoded
  static guard and the display-version rollback mutation.
- **Deliberately unchanged**: `site/script.js` still lists exactly three
  releases (v1.0.2 / v1.0.1 / v1.0.0), `site/index.html` still reports the
  last published build 21, and no tag or GitHub Release is cut. Per the release
  rule these move together only when the maintainer explicitly names a tag.
- Gates: `LAYOUT_CONTRACT_OK 192`, `JVM_TESTS_OK 293`,
  `MUTATION_SUITE_OK 64/64`, `BUILD_OK version=1.2.0 code=26`.

### versionCode 28 — auto-continue on correct + pause/resume signal fix

`versionCode` 26 -> **28** (27 belongs to the standalone Android 4.0 legacy
branch); display version stays **1.2.0**, same release keystore
(SHA-256 `76eb6063b574...26172`). Not tagged / released yet.

- **Bug fix (user report)**: on real TVs the video kept playing while the quiz
  was shown but paused *after* tapping "继续观看". `MediaPauseController` used to
  send `MEDIA_PAUSE` **and** grab `AUDIOFOCUS_GAIN_TRANSIENT` at the same time,
  then released the focus *before* sending `MEDIA_PLAY`. TV players commonly
  treat media keys as play/pause toggles, so the two pause signals cancelled
  each other and the trailing PLAY toggled playback back off. The two paths are
  now mutually exclusive: audio focus alone when granted, media keys alone when
  the focus request fails, and the resume key is sent before focus is returned.
- **New setting** "答对自动继续" in quiz card 01, default **on**: once the unlock
  gate is met the quiz closes itself, no second remote press. Turning it off
  keeps the original "继续观看" confirmation. `OverlayQuizView.correctAuto()`
  hides the continue button; the dismissal is posted off the key-dispatch stack
  so `removeViewImmediate()` never detaches an active ViewRootImpl.
- Gates: `LAYOUT_CONTRACT_OK 199` (+7 assertions), `JVM_TESTS_OK 293`,
  `MUTATION_SUITE_OK 68/68` (+4 mutations), `BUILD_OK version=1.2.0 code=28`.

### versionCode 26 — neutral data-source notice and release build

`versionCode` 25 -> **26**; display version stays **1.2.0**. The three `meta`
rows inside `app/assets/quiz.db` were rewritten to a neutral notice, which
changes the APK payload, so the build number advances to keep over-the-top
installs from every earlier build valid.

- README, CHANGELOG history, `LICENSE`, `docs/*`, the site FAQ/footer, the
  bank-builder notes and app source comments now use one neutral wording:
  part of the bank is compiled from publicly available Internet material,
  with a GitHub-Issues takedown contact. No specific source is named.
- A neutral `docs/DATA_SOURCES.md` replaces the previous source-and-licence
  document; stale references to data the shipped bank no longer uses were
  removed.
- `v1.2.0` is tagged at this build. The site release notes gain a fourth card
  and the CI metadata assertion now expects four published versions.
- Gates: `LAYOUT_CONTRACT_OK 192`, `JVM_TESTS_OK 293`,
  `MUTATION_SUITE_OK 64/64`, `BUILD_OK version=1.2.0 code=26`; badging
  `versionCode='26' versionName='1.2.0'`, release certificate
  `76eb6063...6172` continuous.


## v1.1.0 (versionCode 22–24) — unreleased development line

> This line was never tagged or released; all of its work (code 22-24) is
> carried forward by `## v1.2.0 (versionCode 25)` above.

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

### versionCode 24 — new cleaned bank and difficulty preference

Display version stays **1.1.0** (not yet tagged), `versionCode` 23 -> **24**, same
release keystore (SHA-256 `76eb6063b574...26172`).

- **Built-in bank replaced.** `app/assets/quiz.db` now has **4733 questions**
  (860 self-generated for kindergarten-grade 2, 3873 compiled from public
  Internet sources).
  The old 5129-row catalog-location bank is gone. Schema keeps the original 17
  columns and adds `difficulty` / `difficulty_level`; `knowledge`, `chapters`,
  `meta` tables and `quiz.db.version` are preserved so `QuizDb.open()` and
  over-the-top upgrades keep working.
- **Questions collected from public Internet sources were validated and cleaned
  per question** over four passes (10632 -> 3873; 6758 rejections with reasons).
  A neutral data-source notice lives in `docs/DATA_SOURCES.md`: rights to the
  content remain with their respective owners, and a takedown contact via GitHub
  Issues is provided.
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
