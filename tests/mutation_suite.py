#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW 负向变异回归（可从当前 HEAD 复核）。

每组故意回退一处实现，确认布局/发布契约、运行时题库分布校验或构建门禁会拦下它；
最后校验源码哈希完全恢复，并用发布密钥重建 dist。
手动运行：python3 tests/mutation_suite.py
不接入 build.sh：它会跑多次完整构建，只用于发布前复核。
"""
import hashlib
import io
import os
import shutil
import subprocess
import sys
import tempfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app/src/com/jager/tvquiz")
M = os.path.join(SRC, "MainActivity.java")
S = os.path.join(SRC, "SettingsActivity.java")
E = os.path.join(SRC, "QuizEngine.java")
D = os.path.join(SRC, "QuizDb.java")
O = os.path.join(SRC, "QuizOverlayService.java")
ST = os.path.join(SRC, "SettingsStore.java")
W = os.path.join(SRC, "SubjectWeights.java")
B = os.path.join(ROOT, "app/build.sh")
MF = os.path.join(ROOT, "app/AndroidManifest.xml")
C = os.path.join(ROOT, "tests/layout_contract.py")
DO = os.path.join(SRC, "DonateActivity.java")
LP = os.path.join(SRC, "LaunchPolicy.java")
CHK = os.path.join(ROOT, "tests/bank_distribution_check.py")
SYL = os.path.join(SRC, "Syllabus.java")
SYLJ = os.path.join(ROOT, "app/assets/syllabus.json")
SITE = os.path.join(ROOT, "site/index.html")
UP = os.path.join(SRC, "UnlockPolicy.java")
CD = os.path.join(SRC, "CooldownPolicy.java")
RM = os.path.join(ROOT, "README.md")
CH = os.path.join(ROOT, "CHANGELOG.md")
SJS = os.path.join(ROOT, "site/script.js")
CI = os.path.join(ROOT, ".github/workflows/ci.yml")
APNG = os.path.join(ROOT, "app/res/drawable-nodpi/donate_alipay.png")
DP = os.path.join(SRC, "DifficultyPolicy.java")
MP = os.path.join(SRC, "MediaPauseController.java")
PA = os.path.join(SRC, "PracticeActivity.java")
PC = os.path.join(SRC, "PracticeConfig.java")
UI = os.path.join(SRC, "UpdateInstaller.java")
UCFG = os.path.join(SRC, "UpdateConfig.java")
RP = os.path.join(SRC, "RoundPolicy.java")
GW = os.path.join(SRC, "GuardWatchdog.java")
OVL = os.path.join(SRC, "QuizOverlayService.java")
AXML = os.path.join(ROOT, "app/res/xml/guard_accessibility.xml")
OVLQ = os.path.join(SRC, "OverlayQuizView.java")
ADMAS = os.path.join(SRC, "AdminAssets.java")
API = os.path.join(SRC, "AdminApi.java")
AIDX = os.path.join(ROOT, "app/assets/admin/index.html")
AJS = os.path.join(ROOT, "app/assets/admin/app.js")
ACSS = os.path.join(ROOT, "app/assets/admin/style.css")
QRJ = os.path.join(SRC, "QrEncoder.java")
QTS = os.path.join(SRC, "QuizTextSizer.java")
GP = os.path.join(SRC, "GuardPolicy.java")
BR = os.path.join(SRC, "BootReceiver.java")
PV = os.path.join(SRC, "PinVerifyView.java")
PAIR = os.path.join(SRC, "PairActivity.java")
LAN = os.path.join(SRC, "LanServer.java")
SGT = os.path.join(ROOT, "tests/src/StartGuardTest.java")
HC = os.path.join(SRC, "HolidayCalendar.java")
SP = os.path.join(SRC, "SchedulePolicy.java")
HA = os.path.join(SRC, "HolidayAssets.java")
HOLJ = os.path.join(ROOT, "app/assets/holidays.json")
HOLV = os.path.join(ROOT, "app/assets/holidays.json.version")
HCHK = os.path.join(ROOT, "tests/holiday_check.py")
HFETCH = os.path.join(ROOT, "tools/holidays/fetch_holidays.py")
SPT = os.path.join(ROOT, "tests/src/SchedulePolicyTest.java")
BOOTSET = os.path.join(SRC, "BootSettings.java")
RUNSH = os.path.join(ROOT, "tests/run.sh")
BK_DIR = tempfile.mkdtemp(prefix="pohw_mut_bk_")
BK = {}
for _i, p in enumerate((M, S, E, D, O, ST, W, DO, LP, B, MF, C, CHK, SYL, SYLJ, SITE,
           RM, CH, SJS, CI, APNG, UP, CD, DP, MP, PA, PC, UI, UCFG,
           RP, GW, OVL, AXML, OVLQ, ADMAS, QRJ, QTS,
           GP, BR, PV, PAIR, LAN, SGT, API, AIDX, AJS, ACSS,
           HC, SP, HA, HOLJ, HOLV, HCHK, HFETCH, SPT, RUNSH, BOOTSET)):
    # 不同目录下的同名文件（site/index.html 与 admin/index.html）必须用不同备份名，
    # 否则 restore() 会把手机管理页写回官网首页。
    BK[p] = os.path.join(BK_DIR, "%03d_%s.bk" % (_i, os.path.basename(p)))
    shutil.copyfile(p, BK[p])
ORIG = {p: hashlib.sha256(open(p, "rb").read()).hexdigest() for p in BK}
FAIL = []

OLD_OTHER_GRADE = """    private static final String POOL_OTHER_GRADE =
            "WHERE grade<=? AND subject NOT IN ('数学','语文','英语')";"""
NEW_OTHER_GRADE = """    private static final String POOL_OTHER_GRADE =
            "WHERE grade<=?";"""
DECOY = """
// POOL_OTHER_GRADE = "WHERE grade<=? AND subject NOT IN ('数学','语文','英语')";
"""


def restore():
    for p, b in BK.items():
        shutil.copyfile(b, p)


def read(p):
    return io.open(p, encoding="utf-8").read()


def write(p, t):
    io.open(p, "w", encoding="utf-8").write(t)


def replace(p, old, new):
    s = read(p)
    if s.count(old) != 1:
        raise RuntimeError("anchor mismatch %s count=%d for %r" % (
            os.path.basename(p), s.count(old), old[:70]))
    write(p, s.replace(old, new, 1))

def env_for(with_key):
    env = os.environ.copy()
    env["JAVA_HOME"] = "/opt/jdk17"
    env["ANDROID_HOME"] = os.environ.get("ANDROID_HOME",
                                        "/data/workspace/apkbuild/sdk")
    for k in ("POHW_KEYSTORE", "POHW_KEYSTORE_PASS", "POHW_ALLOW_DEV_SIGN"):
        env.pop(k, None)
    if with_key:
        env["POHW_KEYSTORE"] = os.environ.get("POHW_KEYSTORE",
                                          "/data/workspace/PoHW-release-signing-key.keystore")
        env["POHW_KEYSTORE_PASS"] = "android"
    return env


def contract():
    return subprocess.run(["python3", "tests/layout_contract.py"], cwd=ROOT,
                          capture_output=True, text=True)


def distro():
    return subprocess.run(["python3", "tests/bank_distribution_check.py"], cwd=ROOT,
                          capture_output=True, text=True)


def build(with_key):
    return subprocess.run(["bash", "app/build.sh"], cwd=ROOT, env=env_for(with_key),
                          capture_output=True, text=True)


def first_reason(out, keys):
    lines = out.splitlines()
    for line in lines:
        if any(k in line for k in keys):
            return line
    return lines[-1] if lines else "?"


def break_other_grade():
    replace(D, OLD_OTHER_GRADE, NEW_OTHER_GRADE)


def break_other_grade_with_decoy():
    break_other_grade()
    write(D, read(D) + DECOY)


# (名称, 说明, 变异函数, 是否验证构建门禁, 是否必须由契约拦下, 是否必须由运行时分布校验拦下)
MUTATIONS = [
    ("A", "home back to 2 columns", lambda: replace(M, "GRID_COLS = 3", "GRID_COLS = 2"), False, True, False),
    ("B", "legacy column helper", lambda: write(S, read(S) + "\n// wireColumns\n"), False, True, False),
    ("C", "line-commented vertical focus", lambda: replace(S, "        wireVerticalFocus();\n        wireSubjectRows();",
                     "        // wireVerticalFocus();\n        wireSubjectRows();"), False, True, False),
    ("E", "block-commented subject focus", lambda: replace(S, "        wireSubjectRows();", "        /*\n        wireSubjectRows();\n        */"), False, True, False),
    ("F", "fillViewport removed", lambda: replace(S, "        sv.setFillViewport(true);", "        // sv.setFillViewport(true);"), False, True, False),
    ("G", "fixed-height scroll area", lambda: replace(S, "        root.addView(sv, Ui.fill(1));", "        root.addView(sv, Ui.fixed(this, 300));"), False, True, False),
    ("H", "cert fingerprint changed", lambda: replace(B, "76eb6063b574d6df1cd52ac490d82185793bf48abe0185687f4d977e49626172", "deadbeef"), False, True, False),
    ("I", "back to three weight slots", lambda: replace(W, "new int[Subjects.COUNT]", "new int[3]"), True, True, False),
    ("J", "hardcoded 15 percent restored",
     lambda: replace(E, "        return weights.pick(r);",
                     "        if (allSubjects && !bankOther.isEmpty() && r.nextInt(100) < 15) return Subjects.OTHER;\n        return weights.pick(r);"), True, True, False),
    ("K", "other-pool SQL constant broken", break_other_grade, True, False, True),
    ("L", "label derived from chosen pool again",
     lambda: replace(D, "subjectFromBank(bankSubject)", "subjectFromBank(null)"), False, True, False),
    ("M", "fourth slot pinned to zero",
     lambda: replace(W, "w[Subjects.OTHER] = clampWeight(other);", "w[Subjects.OTHER] = 0;"), False, True, False),
    ("N", "fourth weight row disabled again",
     lambda: replace(S, "        track(wt);", "        track(wt);\n        wt.setEnabled(false);"), False, True, False),
    ("O", "service stops passing the fourth weight",
     lambda: replace(O, "st.weight(Subjects.OTHER)", "0"), False, True, False),
    ("P", "versionCode rolled back",
     lambda: replace(B, 'VERSION_CODE="52"', 'VERSION_CODE="43"'), False, True, False),
    ("Q", "OTHER routing disabled",
     lambda: replace(D, "boolean other = s == Subjects.OTHER && allSubjects;", "boolean other = false;"), True, True, False),
    ("R", "single WHERE binding",
     lambda: replace(D, "new String[args.length * 2]", "new String[args.length]"), True, True, False),
    ("S", "broken SQL plus comment decoy", break_other_grade_with_decoy, True, False, True),
    ("T", "pool-preserving fallback removed",
     lambda: replace(D, "        if (q == null) q = pick(poolWhere + diffW, poolArgs);", "        if (q == null) q = pick(\"\", null);"), True, True, False),
    ("U", "empty non-core pool ignored",
     lambda: replace(E, "db.hasOtherQuestions()", "true"), False, True, False),
    ("V", "row gap removed",
     lambda: replace(S, "ROW_GAP_DP = 12", "ROW_GAP_DP = 0"), False, True, False),
    ("W", "boot autostart default flipped back on",
     lambda: replace(ST, 'getBoolean("bootAutostart", false)',
                     'getBoolean("bootAutostart", true)'), False, True, False),
    ("X", "launch-to-guard default flipped back off",
     lambda: replace(ST, 'getBoolean("autoGuardOnBoot", true)',
                     'getBoolean("autoGuardOnBoot", false)'), False, True, False),
    ("Y", "stale boot-only label restored",
     lambda: replace(S, "开启（打开 APP 时自动守护）", "开启（开机即守护）"), False, True, False),
    ("Z", "home no longer delegates to LaunchPolicy",
     lambda: replace(M, "LaunchPolicy.shouldAutoGuard",
                     "LaunchPolicy.alwaysGuard"), True, True, False),
    ("AA", "donate activity removed from manifest",
     lambda: replace(MF, 'android:name=".DonateActivity"',
                     'android:name=".NoDonate"'), False, True, False),
    ("AB", "DPAD channel switching removed",
     lambda: replace(DO, "KEYCODE_DPAD_LEFT", "KEYCODE_DPAD_DOWN"), False, True, False),
    ("AC", "alipay QR resource dropped",
     lambda: replace(DO, "R.drawable.donate_alipay",
                     "R.drawable.donate_wechat"), True, True, False),
    ("AD", "upper-term predicate disabled",
     lambda: replace(SYL, "term == TERM_FIRST", "term == -1"), False, True, False),
    ("AE", "unit LIKE/equality predicate broken",
     lambda: replace(SYL, "topic LIKE ? OR topic = ?", "topic = ?"), False, True, False),
    ("AF", "unit LIKE pattern loses the chinese colon",
     lambda: replace(SYL, "%：", "%"), False, True, False),
    ("AG", "term default flipped to upper term",
     lambda: replace(ST, "getInt(\"termIdx\", Syllabus.TERM_ALL)",
                     "getInt(\"termIdx\", Syllabus.TERM_FIRST)"), False, True, False),
    ("AH", "strict scope layer dropped",
     lambda: replace(D, "String strict = where + termW + unitW + diffW;",
                     "String strict = where;"), False, True, False),
    ("AI", "engine stops passing study scope",
     lambda: replace(E, "syllabus, termIndex, unitLimit", "null, 0, 0"), False, True, False),
    ("AJ", "service drops study scope injection",
     lambda: replace(O, "engine.setStudyScope(SyllabusAssets.get(this), st.termIndex(), st.unitLimit());",
                     "// study scope dropped"), False, True, False),
    ("AK", "edition-system isolation removed from quiz pools",
     lambda: replace(D, 'if (fams.length > 0) {', 'if (false) {'), False, True, False),
    ("AL", "legacy 30-minute slogan restored",
     lambda: replace(SITE, '<p class="hero-tagline">答对题目，无限续租！</p>',
                     '<p class="hero-tagline">答对一题，续租 30 分钟。</p>'),
     True, True, False),
    ("AM", "paired bank row unregistered",
     lambda: replace(S, "        bankPair = new Button[]{btnCheck, rowSync};",
                     "        bankPair = null;"), False, True, False),
    ("AN", "bank pair horizontal focus chain removed",
     lambda: replace(S, "            bankPair[0].setNextFocusRightId(rightId);",
                     "            // bankPair[0].setNextFocusRightId(rightId);"), False, True, False),
    ("AO", "auto-update row back in quiz card",
     lambda: replace(S, '        note(quiz, "点击右侧取值即可循环切换，离开设置页时统一保存。");',
                     '        addRow(quiz, "自动更新", null);' + chr(10) + '        note(quiz, "点击右侧取值即可循环切换，离开设置页时统一保存。");'),
     False, True, False),
    ("AP", "donate section restored on site",
     lambda: replace(SITE, '<section id="faq" class="section">',
                     '<section id="donate" class="section"></section>' + chr(10) + '    <section id="faq" class="section">'),
     False, True, False),
    ("AQ", "computer-linked install command restored on site",
     lambda: replace(SITE, '<h3>用 U 盘把 APK 装到电视</h3>',
                     '<h3>用 U 盘把 APK 装到电视</h3>' + chr(10) + '            <code>adb install -r PoHW.apk</code>'),
     True, True, False),
    ("AR", "footer blog link removed",
     lambda: replace(SITE, 'href="https://zhang.ge/" target="_blank" rel="noopener">张戈博客</a> 保留所有权利',
                     'href="#">张戈博客</a> 保留所有权利'), False, True, False),
    ("AS", "duplicate release label restored on site",
     lambda: replace(SJS, 'version: "v1.0.2"', 'version: "v1.0.1"'), False, True, False),
    ("AT", "pre-1.0 dev section restored in changelog",
     lambda: write(CH, read(CH) + "\n## Dev build v6.0.0 (versionCode 10)\n"),
     False, True, False),
    ("AU", "promo-site section restored in README",
     lambda: write(RM, read(RM) + "\n## 官网与部署\nEdgeOne Makers\n"),
     False, True, False),
    ("AV", "auto-review workflow removed",
     lambda: os.remove(CI), False, True, False),
    ("AW", "display version rolled back to 1.0.1",
     lambda: replace(MF, 'android:versionName="1.3.5"', 'android:versionName="1.0.2"'),
     False, True, False),
    ("AX", "alipay QR crop corrupted",
     lambda: open(APNG, "wb").write(bytes([137, 80, 78, 71, 13, 10, 26, 10]) + bytes(16)),
     False, True, False),
    ("AY", "default streak raised to 2",
     lambda: replace(ST, 'getInt("unlockStreak", 1)', 'getInt("unlockStreak", 2)'), False, True, False),
    ("AZ", "cooldown default enabled",
     lambda: replace(ST, 'getBoolean("cooldownEnabled", false)', 'getBoolean("cooldownEnabled", true)'), False, True, False),
    ("BA", "strict mode default enabled",
     lambda: replace(ST, 'getBoolean("strictOneShot", false)', 'getBoolean("strictOneShot", true)'), False, True, False),
    ("BB", "knowledge-only default enabled",
     lambda: replace(ST, 'getInt("questionSource", SOURCE_ALL)', 'getInt("questionSource", SOURCE_KNOWLEDGE_ONLY)'), False, True, False),
    ("BC", "unlock always returns true",
     lambda: replace(UP, 'return streak >= required;', 'return true;'), False, True, False),
    ("BD", "wrong answer no longer resets streak",
     lambda: replace(O, 'if (roundMode == SettingsStore.ROUND_STREAK) unlock.onWrong();', ''),
     False, True, False),
    ("BE", "question preference bypassed",
     lambda: replace(O, 'engine.setKnowledgeOnly(knowledgeOnly);', 'engine.setKnowledgeOnly(false);'), False, True, False),
    ("BF", "knowledge-only fallback loses the source and term constraint",
     lambda: replace(D, 'pick(poolWhere + koW, poolArgs)', 'pick(poolWhere + diffW, poolArgs)'), False, True, False),
    ("CT", "knowledge preference loses the selected textbook term",
     lambda: replace(D, 'String koW = KNOWLEDGE_ONLY_SQL + termW + diffW;',
                     'String koW = KNOWLEDGE_ONLY_SQL + diffW;'), False, True, False),
    ("BG", "knowledge-only chain disabled",
     lambda: replace(D, 'if (knowledgeOnly) {', 'if (false) {'), False, True, False),
    ("BH", "difficulty default changed to low",
     lambda: replace(ST, 'getInt("difficultyLevel", 0)', 'getInt("difficultyLevel", 1)'), False, True, False),
    ("BI", "difficulty becomes a hard filter",
     lambda: replace(DP, 'MATCH_RATE = 80', 'MATCH_RATE = 100'), False, True, False),
    ("BJ", "fallback drops the difficulty predicate",
     lambda: replace(D, 'pick(poolWhere + diffW, poolArgs)', 'pick(poolWhere, poolArgs)'), False, True, False),
    ("BK", "service stops injecting difficulty",
     lambda: replace(O, 'engine.setDifficultyLevel(difficultyLevel);', ''), False, True, False),
    ("BL", "difficulty settings row removed",
     lambda: replace(S, 'rowDifficulty = addRow', '// rowDifficulty = addRow'), False, True, False),
    ("BM", "auto-continue default flipped off",
     lambda: replace(ST, 'getBoolean("autoContinueOnCorrect", true)',
                     'getBoolean("autoContinueOnCorrect", false)'), False, True, False),
    ("BN", "auto-continue settings row removed",
     lambda: replace(S, 'rowAutoContinue = addRow', '// rowAutoContinue = addRow'), False, True, False),
    ("BO", "auto-finish branch disabled",
     lambda: replace(O, 'if (autoContinue) {', 'if (false) {'), False, True, False),
    ("BP", "pause signals sent unconditionally again",
     lambda: replace(MP, 'if (!focusHeld) {', 'if (true) {'), False, True, False),
    ("BQ", "practice mode never suspends the guard",
     lambda: replace(PA, 'QuizOverlayService.setPracticeMode(true);', ''), False, True, False),
    ("BR", "guard ignores the practice flag",
     lambda: replace(O, 'if (practiceMode) { remainSec = interval; return; }', ''), False, True, False),
    ("BS", "practice button renamed back to immediate quiz",
     lambda: replace(M, 'actionButton(row1, "练习模式",', 'actionButton(row1, "立即出题",'), False, True, False),
    ("BV", "restored overlay authorization button removed",
     lambda: replace(M, 'actionButton(row2, "授权悬浮窗",', 'actionButton(row2, "已移除",'), False, True, False),
    ("BT", "practice entry removed from manifest",
     lambda: replace(MF, 'android:name=".PracticeActivity"', 'android:name=".NoPracticeActivity"'), False, True, False),
    ("BU", "practice always draws math only",
     lambda: replace(PC, 'return subject == MIXED ? (asked % 3) : subject;', 'return MATH;'), False, True, False),
    ("BY", "knowledge preference loses the qtype filter",
     lambda: replace(D, " AND qtype='knowledge'", ""), False, True, False),
    ("BW", "core fallback loses the grade cap",
     lambda: replace(D, 'private static final String POOL_CORE_ALL = "WHERE grade<=? AND subject=?";',
                     'private static final String POOL_CORE_ALL = "WHERE subject=?";'), False, True, False),
    ("BX", "final fallback loses the grade cap",
     lambda: replace(D, 'String rootW = diffRoot.isEmpty() ? "WHERE grade<=?" : diffRoot + " AND grade<=?";',
                     'String rootW = diffRoot;'), False, True, False),
    ("BZ", "practice question view collapses to zero height",
     lambda: replace(PA, "card.addView(text, Ui.fill(3));", "card.addView(text, Ui.fill(0));"), False, True, False),
    ("CA", "practice grade selector back to 9",
     lambda: replace(PC, "MAX_GRADE = 6", "MAX_GRADE = 9"), False, True, False),
    ("CB", "automatic version check defaults to off",
     lambda: replace(ST, 'getBoolean("autoCheckUpdate", true)',
                     'getBoolean("autoCheckUpdate", false)'), False, True, False),
    ("CC", "APK sha256 verification removed",
     lambda: replace(UI, 'info.sha256.equalsIgnoreCase(actual)',
                     'true'), False, True, False),
    ("CD", "download URL host check removed",
     lambda: replace(UCFG, 'u.startsWith("https://" + HOST + "/dl/")',
                     'true'), False, True, False),
    ("CE", "download buttons fall back to GitHub releases",
     lambda: write(SITE, read(SITE).replace(
         'href="https://pohw.zhangge.net/dl/latest-main.apk"',
         'href="https://github.com/jagerzhang/PoHW/releases/latest"', 1)),
     False, True, False),
    ("CF", "update button disabled again, breaking the DPAD chain",
     lambda: replace(S, 'btnUpdateInstall.setText(p == null',
                     'btnUpdateInstall.setEnabled(p != null);\n              btnUpdateInstall.setText(p == null'), False, True, False),
    ("CG", "group quiz falls back to a single question",
     lambda: replace(RP, "DEFAULT_TARGET = 5", "DEFAULT_TARGET = 1"), False, True, False),
    ("CH", "AlarmManager revive fallback removed",
     lambda: replace(OVL, "GuardWatchdog.scheduleAlarm(this);", ""), False, True, False),
    ("CI", "accessibility service starts reading window content",
     lambda: replace(AXML, 'canRetrieveWindowContent="false"',
                     'canRetrieveWindowContent="true"'), False, True, False),
    ("CJ", "HOME detection removed",
     lambda: replace(OVL, "Intent.ACTION_CLOSE_SYSTEM_DIALOGS",
                     "Intent.ACTION_BATTERY_CHANGED"), False, True, False),
    ("CL", "option text size pinned back to 24sp",
     lambda: replace(OVLQ, "QuizTextSizer.optionSp(q.options)", "24"), False, True, False),
    ("CM", "option grid shrunk back to 186dp",
     lambda: replace(OVLQ, "Ui.dp(c, 200)", "Ui.dp(c, 186)"), False, True, False),
    ("CN", "font padding restored, clipping long options",
     lambda: replace(OVLQ, "b.setIncludeFontPadding(false);", ""), False, True, False),
    ("CO", "LAN admin silently enabled by default",
     lambda: replace(ST, 'getBoolean("lanAdminEnabled", false)',
                     'getBoolean("lanAdminEnabled", true)'), False, True, False),
    ("CP", "admin asset whitelist bypassed",
     lambda: replace(ADMAS, "if (!ok) return null;", "if (false) return null;"),
     False, True, False),
    ("CQ", "QR encoder empty-input guard removed",
     lambda: replace(QRJ, "if (text == null || text.length() == 0) return -1;", ""),
     False, True, False),
    ("CK", "published versionCode rolled back",
     lambda: replace(MF, 'android:versionCode="52"', 'android:versionCode="43"'),
     False, True, False),
    ("CR", "extreme long option size back to 18sp",
     lambda: replace(os.path.join(SRC, "QuizTextSizer.java"),
                     "if (max > 48) return 14;", "if (max > 48) return 18;"),
     False, True, False),
    ("CS", "guard stops skipping over-long TV options",
     lambda: replace(OVL, "QuizTextSizer.optionsFitTv(q.options)", "true"),
     False, True, False),
    ("CU", "edition-system injection removed from guard service",
     lambda: replace(O, 'engine.setEditionMode(st.editionMode());',
                     '// edition mode dropped'), False, True, False),
    ("CV", "QR entry button loses its visible label",
     lambda: replace(S, 'rowQr.setText("点开扫码 ›");',
                     '// no label'), False, True, False),
    ("CW", "setting rows clipped back to two lines",
     lambda: replace(S, 'b.setMaxLines(4);', 'b.setMaxLines(2);'), False, True, False),
    ("CX", "LAN config drops numeric range validation",
     lambda: replace(os.path.join(SRC, "AdminApi.java"),
                     'st.questionsPerRound(clamp(v, 1, 10))',
                     'st.questionsPerRound(v)'), False, True, False),
    ("CY", "LAN admin exposes the online bank URL",
     lambda: replace(os.path.join(SRC, "AdminApi.java"),
                     '"w0", "w1", "w2", "w3"',
                     '"w0", "w1", "w2", "w3", "bankUrl"'), False, True, False),
    ("CZ", "showQuiz schedule gate removed",
     lambda: replace(O, 'if (!sd.allowed) {', 'if (false) {'), False, True, False),
    ("DA", "muted gate stops retrying every second",
     lambda: replace(O, 'remainSec = 1;', 'remainSec = interval;'), False, True, False),
    ("DB", "holiday calendar lookup dropped from the gate",
     lambda: replace(O, 'st.scheduleConfig(), HolidayAssets.get(this));',
                     'st.scheduleConfig(), HolidayCalendar.empty());'), False, True, False),
    ("DC", "a 2026 makeup workday flipped into a holiday",
     lambda: replace(HOLJ, '"d": "2026-01-04",\n   "off": 0',
                     '"d": "2026-01-04",\n   "off": 1'), False, True, False),
    ("DD", "LAN config drops window format validation",
     lambda: replace(API, 'if (!SchedulePolicy.validWindows(v)) continue;',
                     'if (false) continue;'), False, True, False),
    ("DE", "schedule switched on by default",
     lambda: replace(SP, 'c.enabled = false;', 'c.enabled = true;'), False, True, False),
    ("DF", "makeup workday treated as a home day in Java",
     lambda: replace(HC, 'if (e == 0) return DAY_SCHOOL;',
                     'if (e == 0) return DAY_HOME;'), False, True, False),
    ("DG", "long-stem font padding restored",
     lambda: replace(OVLQ, "text.setIncludeFontPadding(false);",
                     "text.setIncludeFontPadding(true);"), False, True, False),
    ("DH", "boot guide confirm button removed",
     lambda: replace(BOOTSET, '"去系统设置"', '"关闭"'),
     False, True, False),
]

print("--- baseline ---")
base = contract()
print(base.stdout.strip().splitlines()[-1])
if base.returncode != 0:
    FAIL.append("baseline contract")

for name, label, apply_mut, with_build, need_contract, need_distro in MUTATIONS:
    restore()
    try:
        apply_mut()
    except Exception as _e:
        # 锚点失配时也必须还原，绝不能把变异态留在工作区污染后续构建。
        restore()
        print("MUT_%s_ANCHOR_FAIL: %s" % (name, _e))
        FAIL.append(name + " anchor")
        break
    c = contract()
    if need_contract:
        if c.returncode == 0:
            print("MUT_%s_CONTRACT_NOT_CAUGHT (%s)" % (name, label))
            FAIL.append(name + " contract")
        else:
            print("MUT_%s_CONTRACT_REJECTED: %s" % (
                name, first_reason(c.stdout, ["LAYOUT_CONTRACT_FAIL"])))
    else:
        print("MUT_%s_CONTRACT_INFO: %s" % (
            name, "passed (runtime check must catch it)" if c.returncode == 0
            else "also rejected"))
    if need_distro:
        d = distro()
        if d.returncode == 0:
            print("MUT_%s_DISTRIBUTION_NOT_CAUGHT" % name)
            FAIL.append(name + " distribution")
        else:
            print("MUT_%s_DISTRIBUTION_REJECTED: %s" % (
                name, first_reason(d.stdout + d.stderr, ["BANK_DISTRIBUTION_FAIL"])))
    if with_build:
        b = build(False)
        if b.returncode == 0:
            print("MUT_%s_BUILD_NOT_CAUGHT" % name)
            FAIL.append(name + " build")
        else:
            print("MUT_%s_BUILD_REJECTED: %s" % (
                name, first_reason(b.stdout + b.stderr,
                                   ["LAYOUT_CONTRACT_FAIL", "FORBIDDEN",
                                    "BANK_DISTRIBUTION_FAIL"])))

restore()
print("--- D: dev-signed build must be refused ---")
d = build(False)
reason = [l for l in (d.stdout + d.stderr).splitlines() if "FORBIDDEN: APK certificate" in l]
if d.returncode == 0 or not reason:
    print("MUT_D_BUILD_NOT_CAUGHT")
    FAIL.append("D build")
    print("\n".join((d.stdout + d.stderr).splitlines()[-6:]))
else:
    print("MUT_D_BUILD_REJECTED: " + reason[0])

restore()
print("--- restore integrity ---")
for p, digest in ORIG.items():
    if hashlib.sha256(open(p, "rb").read()).hexdigest() != digest:
        print("RESTORE_BAD " + os.path.basename(p))
        FAIL.append("restore " + p)
print(contract().stdout.strip().splitlines()[-1])

print("--- restore release-signed dist ---")
f = build(True)
for line in f.stdout.splitlines():
    if any(k in line for k in ("LAYOUT_CONTRACT_OK", "BANK_DISTRIBUTION_OK",
                               "FALLBACK_POOL_OK", "JVM_TESTS_OK", "BUILD_OK")):
        print(line)
if f.returncode != 0:
    print("RESTORE_BUILD_FAILED")
    print("\n".join((f.stdout + f.stderr).splitlines()[-12:]))
    FAIL.append("restore build")

count = len(MUTATIONS) + 1
if FAIL:
    print("MUTATION_SUITE_FAIL " + ",".join(FAIL))
    sys.exit(1)
print("MUTATION_SUITE_OK %d/%d" % (count, count))
