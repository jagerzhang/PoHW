#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PoHW UI / 发布契约：源码级静态断言，零 Android 依赖，可由构建门禁直接执行。

首页 3 列 × 2 排、家长设置单列卡片流与发布证书连续性是 TV 遥控器可用性
与覆盖安装的硬约束；纯 JVM 单测无法实例化 android.view.*，因此用源码符号
契约守住结构，再配合 build.sh 的 javac/apksigner 保证符号真的被编译进 dex。

注意：调用次数类断言必须基于「去注释代码行」（含 /* */ 块注释状态机），
否则把调用注释掉也会被计数放行；三栏残留这类负向断言仍基于原始文件。
"""
import io
import json
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, "app/src/com/jager/tvquiz/MainActivity.java")
SET = os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsActivity.java")
BUILD = os.path.join(ROOT, "app/build.sh")

TOTAL = 0


def read(path):
    with io.open(path, encoding="utf-8") as fh:
        return fh.read()


def strip_comments(text):
    """丢弃 // 整行注释与 /* ... */ 块注释（含跨行块），保留行内代码。"""
    out = []
    in_block = False
    for line in text.splitlines():
        cur = line
        if in_block:
            end = cur.find("*/")
            if end < 0:
                continue
            cur = cur[end + 2:]
            in_block = False
        while True:
            start = cur.find("/*")
            if start < 0:
                break
            end = cur.find("*/", start + 2)
            if end < 0:
                cur = cur[:start]
                in_block = True
                break
            cur = cur[:start] + cur[end + 2:]
        if cur.lstrip().startswith("//"):
            continue
        out.append(cur)
    return "\n".join(out)


def check(ok, msg):
    global TOTAL
    if not ok:
        print("LAYOUT_CONTRACT_FAIL: " + msg)
        sys.exit(1)
    TOTAL += 1
    print("[PASS] " + msg)


raw_m = read(MAIN)
raw_s = read(SET)
b = read(BUILD)
m = strip_comments(raw_m)
s = strip_comments(raw_s)

# ---- 首页：3 列 × 2 排 ----
check("private static final int GRID_COLS = 3;" in m, "home declares 3 columns")
check(m.count("new Button[GRID_COLS]") == 2, "home has exactly 2 grid rows")
check(len(re.findall(r"actionButton\(row[12],", m)) == 6, "home has exactly 6 action buttons")
check("row3" not in m, "legacy third button row removed")
check(m.count("l.addView(row") == 2, "exactly two grid rows added to the page")
check("new LinearLayout.LayoutParams(0, Ui.dp(this, 54), 1f)" in m,
      "home buttons are equal-width instead of hardcoded 190dp")
check(m.count("wireGrid()") == 2, "home declares and calls the grid focus chain")
for direction in ("setNextFocusLeftId", "setNextFocusRightId",
                  "setNextFocusUpId", "setNextFocusDownId"):
    check(direction in m, "home focus chain includes " + direction)
check("Ui.fixed(this, 46)" in m, "home title has enough height to avoid clipping")
check("getPackageInfo" in m, "home version still read from PackageManager")

# ---- 家长设置：单列卡片流 ----
check(s.count("new ScrollView(this)") == 1, "settings has exactly one scroll container")
check(s.count("addCard(") == 6, "settings declares 5 grouped cards + helper")
check(len(re.findall(r'addCard\(col, "0\d', s)) == 5, "settings has 5 numbered cards")
check("addColumn" not in raw_s and "wireColumns" not in raw_s,
      "legacy three-column helpers are gone")
check(s.count("wireVerticalFocus()") == 2, "settings declares and calls the vertical focus chain")
check(s.count("wireSubjectRows()") == 2, "subject rows keep their left/right chain")
check("setNextFocusUpId" in s and "setNextFocusDownId" in s,
      "settings vertical chain is explicit across nested cards")
check("root.addView(footer, Ui.fixed(this, 54))" in s,
      "save / change-pin footer stays fixed while the form scrolls")
check(s.count('"保存并返回"') == 1, "exactly one save action")
check(s.count('"检查题库更新"') == 1, "bank update entry lives in settings only")
check("track(urlEdit)" in s, "URL field participates in the focus chain")
check("setMinHeight(Ui.dp(this, 72))" in s,
      "bank status uses min-height instead of a clipping fixed height")
check("Ui.dp(this, GUTTER_DP)" in s, "cards and footer share one centred gutter")
check("ViewGroup.LayoutParams.WRAP_CONTENT, 1f" in s,
      "row values fill the narrow column by weight and wrap by content")

# 复审加固：滚动容器参数与左右焦点边界
check("sv.setFillViewport(true)" in s, "scroll view uses fillViewport for short content")
check("root.addView(sv, Ui.fill(1))" in s, "scroll area takes remaining height by weight")
check("shell.addView(col" in s and "ViewGroup.LayoutParams.WRAP_CONTENT" in s,
      "scroll child is wrap_content so long forms can scroll")
check(s.count("Ui.fill(1)") == 1, "exactly one weighted scroll area, footer stays fixed")
check("root.addView(sv, Ui.fixed(" not in s and "setFillViewport(false)" not in s,
      "no fixed-height / non-fill scroll regression")
check(s.count("setMinHeight(Ui.dp(this, 44))") >= 4,
      "row buttons keep a 44dp minimum but grow with font scale")
check("getNextFocusLeftId() == View.NO_ID" in s
      and "getNextFocusRightId() == View.NO_ID" in s,
      "undefined left/right targets are pinned to self, not geometric search")
check("!(v instanceof EditText)" in s,
      "URL EditText keeps left/right keys for cursor movement")

# ---- 发布证书门禁：防止 dev 随机密钥产出可发布 APK ----
check("RELEASE_CERT_SHA256" in b and "ACTUAL_CERT_SHA256" in b,
      "build compares actual APK certificate to the release certificate")
check("apksigner verify --print-certs" in b,
      "certificate is read from the signed APK, not from the keystore alone")
check("76eb6063b574d6df1cd52ac490d82185793bf48abe0185687f4d977e49626172" in b,
      "pinned release certificate SHA-256 is present")
check("POHW_ALLOW_DEV_SIGN" in b,
      "dev-sign override is explicit and opt-in")
check("versionCode='$VERSION_CODE'" in b,
      "artifact check verifies versionCode as well as versionName")


# ---- 方案 B：四槽学科权重、双路径抽题与题目标签反推 ----
_w = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SubjectWeights.java")))
_rw_e = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizEngine.java"))
_e = strip_comments(_rw_e)
_rw_d = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizDb.java"))
_d = strip_comments(_rw_d)
_st = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsStore.java")))
_o = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizOverlayService.java")))
_mf = read(os.path.join(ROOT, "app/AndroidManifest.xml"))
_run = read(os.path.join(ROOT, "tests/run.sh"))

check("new int[Subjects.COUNT]" in _w, "weights declare four subject slots")
check("w[Subjects.OTHER] = clampWeight(other);" in _w, "other slot is wired to the setting")
check("!(allSubjects && otherAvailable)" in _w, "other slot is gated by the all-subjects switch")
check("setOtherAvailable" in _w, "other slot can be disabled when no question bank is ready")
check("public int pick(Random r)" in _w and "MAX_WEIGHT" in _w,
      "weighted roulette is pure Java and clamped")
check("private final SubjectWeights weights;" in _e, "engine delegates to SubjectWeights")
check("int[3]" not in _e, "legacy three-slot weight array is gone")
check("return weights.pick(r);" in _e, "engine draws from the four-slot roulette")
check("syncOtherAvailable()" in _e, "engine refreshes OTHER availability on db/bank changes")
check("r.nextInt(100) < 15" not in _e, "hardcoded 15 percent branch is gone")
check("s == Subjects.OTHER) s = MATH" not in _e,
      "OTHER is no longer downgraded to a math question")
check("gradeWide" not in _d, "grade-wide cross-subject SQL is gone")
check("NOT IN ('数学','语文','英语')" in _d, "other-subject pool uses its own SQL predicate")
check("WHERE grade<=? AND subject=?" in _d, "core subjects keep their own SQL predicate with a grade cap")
check("POOL_OTHER_ALL" in _d and "POOL_CORE_ALL" in _d,
      "pool-preserving fallback predicates are declared as constants")
check("pick(poolWhere + diffW, poolArgs)" in _d,
      "first fallback keeps subject, difficulty and the grade cap")
check("subjectFromBank(bankSubject)" in _d, "question label is derived from the real bank subject")
check("fromBank(subject," not in _d, "legacy label from the chosen pool is gone")
check("st.weight(Subjects.OTHER)" in _o, "service passes the fourth weight to the engine")
check("st.weight(3)" in m, "home status line shows the four-subject ratio")
check("wt.setEnabled(false)" not in raw_s, "other-subject weight button is enabled")
check("if (subj < 3)" not in raw_s and "if (s < 3)" not in raw_s,
      "no three-subject-only UI branches remain")
check("未启用" in raw_s, "disabled fourth slot is labelled in the UI")
check("SubjectWeights.MAX_WEIGHT + 1" in raw_s,
      "UI weight cycle is driven by the same max weight")
check("Subjects.norm(subject)" in _st and '"w" + s' in _st,
      "settings store persists all four normalized slots")
check('android:versionCode="36"' in _mf, "manifest versionCode advances to 36")
check('VERSION_CODE="36"' in b, "build script versionCode advances to 36")
check("SubjectWeights.java" in _run and "SubjectWeightsTest" in _run,
      "new weights class and JVM test are wired into the test runner")
check("bank_distribution_check.py" in b,
      "runtime SQL distribution regression is wired into the build")
check("grep -qF 'new int[Subjects.COUNT]'" in b,
      "build gate matches the four-slot declaration literally, not as a regex")


# ---- 独立复审加固：路由 / 绑定 / 兜底 / 状态的源码级护栏 ----
_rw_w = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SubjectWeights.java"))
_chk = read(os.path.join(ROOT, "tests/bank_distribution_check.py"))
_mut = os.path.join(ROOT, "tests/mutation_suite.py")

check("private boolean otherAvailable = false;" in _rw_w,
      "OTHER defaults to unavailable without an explicit bank readiness signal")
check("boolean other = s == Subjects.OTHER && allSubjects;" in _d,
      "OTHER pool routing condition is pinned")
check("boolean other = s == Subjects.OTHER && allSubjects;" in _chk,
      "runtime distribution check also pins the routing condition")
check("new String[args.length * 2]" in _d and _d.count("System.arraycopy") >= 2,
      "WHERE params are bound twice for main query and COUNT subquery")
check("new String[args.length * 2]" in _chk,
      "runtime distribution check also pins the double binding")
check("POOL_OTHER_GRADE" in _d and "POOL_CORE_GRADE" in _d,
      "both main-query predicates are named constants")
check("db.hasOtherQuestions()" in _e,
      "engine disables OTHER when the bank has no non-core questions")
check("hasOtherQuestions()" in _d, "QuizDb exposes the non-core pool count check")
check("if (qs != null)" in _e, "setBank(null) still refreshes bank availability")
check("random(int grade, int subject)" not in _d,
      "deprecated two-arg random overload that silently drew math is gone")
check("Log.e(TAG, " in _d, "SQL failures are logged instead of silently swallowed")
check("strip_comments" in _chk and "FALLBACK_POOL_OK" in _chk,
      "distribution check strips comments and verifies pool-preserving fallbacks")
check("MAX(1, (SELECT COUNT(*) FROM questions " in _d
      and "MAX(1, (SELECT COUNT(*) FROM questions " in _chk,
      "empty-pool-safe OFFSET template is shared by Java and the runtime check")
check("POOL_OTHER_ALL" in _chk and "POOL_CORE_ALL" in _chk,
      "distribution check reads the same named SQL constants")
check(os.path.isfile(_mut), "mutation harness is committed and reproducible")
check("245 assertions" in b, "build log states the actual JVM assertion count")
check("显示「—」并禁用焦点" not in raw_s, "stale UI comment about the fourth row is gone")
check("题库不可用" in raw_s, "settings note explains the bank-unavailable case")



# ---- 设置页行间距 / 四个默认开关 / APP 启动即守护 ----
_donate = strip_comments(read(os.path.join(
    ROOT, "app/src/com/jager/tvquiz/DonateActivity.java")))
_launch = strip_comments(read(os.path.join(
    ROOT, "app/src/com/jager/tvquiz/LaunchPolicy.java")))
_res = os.path.join(ROOT, "app/res")

check("ROW_GAP_DP = 12" in s, "settings declares a shared 12dp row gap")
check(s.count("rowLp()") == 6, "row gap is applied to every row, button, note and paired bank row")
check("p.setMargins(0, half, 0, half)" in s, "row gap is symmetric top and bottom")
check('getBoolean("bootAutostart", false)' in _st,
      "boot autostart defaults off (parent opt-in)")
check('getBoolean("autoGuardOnBoot", true)' in _st,
      "launch-to-guard defaults on")
check('getBoolean("pauseOnQuiz", true)' in _st
      and 'getBoolean("resumePlayback", true)' in _st,
      "pause and resume both default on")
check("开启（启动即守护）" in raw_s and "开机即守护" not in raw_s,
      "auto-guard label says launch, not boot")
check("LaunchPolicy.shouldAutoGuard" in m,
      "home delegates launch auto-guard to a pure policy")
check("b == null" in m, "auto-guard fires only on a genuine cold start")
check("启动即守护" in m, "home status line uses the new wording")
check("public static boolean shouldAutoGuard" in _launch,
      "launch policy is pure Java and unit-testable")
check("LaunchPolicy.java" in _run, "launch policy is compiled into JVM tests")

# ---- 打赏入口：位置、页面、渠道切换、静态资源 ----
check('"打赏作者 · 请喝一杯咖啡"' in raw_s, "donate entry is in parent settings")
check("DonateActivity.class" in raw_s, "donate entry opens the donate page")
check(raw_s.find('"检查题库更新"') < raw_s.find('"打赏作者'),
      "donate entry follows the bank-update option in the online card")
check('android:name=".DonateActivity"' in _mf,
      "donate activity is declared in the manifest")
check("R.drawable.donate_wechat" in _donate
      and "R.drawable.donate_alipay" in _donate,
      "both QR resources are referenced by the donate page")
check("KEYCODE_DPAD_LEFT" in _donate and "KEYCODE_DPAD_RIGHT" in _donate,
      "DPAD left/right switch the donation channel")
check("微信支付" in _donate and "支付宝" in _donate,
      "both payment tabs are labelled")
check("ImageView.ScaleType.FIT_CENTER" in _donate,
      "QR keeps its aspect ratio so it stays scannable on TV")
check(os.path.isfile(os.path.join(_res, "drawable-nodpi/donate_wechat.png"))
      and os.path.isfile(os.path.join(_res, "drawable-nodpi/donate_alipay.png")),
      "both QR PNGs are packaged as APK resources")

# ---- 品牌资源：可复现生成器 + 多密度图标 + TV banner ----
check(os.path.isfile(os.path.join(ROOT, "tools/branding/make_icons.py")),
      "icon generator is committed and reproducible")
check(all(os.path.isfile(os.path.join(_res, d, "ic_launcher.png")) for d in
          ("drawable-mdpi", "drawable-hdpi", "drawable-xhdpi",
           "drawable-xxhdpi", "drawable-xxxhdpi")),
      "launcher icon ships at five densities")
check(os.path.isfile(os.path.join(_res, "drawable-xhdpi/tv_banner.png")),
      "TV banner is shipped")
check("make_icons.py" in b, "build gate verifies the branding generator")


# ---- 题库说明位置：离线说明必须紧跟「检查题库更新」按钮 ----
check(raw_s.find('"检查题库更新"') < raw_s.find('"默认完全离线：')
      < raw_s.find('"打赏作者 · 请喝一杯咖啡"')
      < raw_s.find('"如果觉得 PoHW 好用'),
      "offline note sits directly under the bank-update button, before the donate entry")

# ---- 方案 B（学习进度）：学期 + 单元过滤的源码、数据与门禁契约 ----
_syl = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/Syllabus.java")))
_json = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/Json.java"))
_assets = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SyllabusAssets.java"))
_syljson = json.load(io.open(os.path.join(ROOT, "app/assets/syllabus.json"), encoding="utf-8"))
_scope = read(os.path.join(ROOT, "tests/study_scope_check.py"))

books = [(g, t, subj) for g, terms in _syljson["grades"].items()
         for t, subjs in terms.items() for subj in subjs]
check(len(_syljson["grades"]) == 6 and len(books) == 12,
      "syllabus.json declares 12 books (grades 1-6 x upper/lower term)")
check(all(subj == "数学" for _, _, subj in books),
      "only math has ordered units; chinese/english degrade honestly")
check(all(t in ("1", "2") for _, t, _ in books),
      "term keys are the same indices used by SettingsStore")
check(os.path.isfile(os.path.join(ROOT, "tools/quizbank/build_syllabus.py")),
      "syllabus generator / quiz.db consistency validator is committed")
check(os.path.isfile(os.path.join(ROOT, "tests/study_scope_check.py")),
      "runtime study-scope SQL regression is committed")
check("term IN ('上','全')" in _syl and "term IN ('下','全')" in _syl,
      "upper/lower term include 全一册 and exclude empty-term practice")
check("term == TERM_FIRST" in _syl and "TERM_ALL = 0" in _syl,
      "full-year default adds no term predicate (upgrade-safe)")
check('sb.append("(topic LIKE ? OR topic = ?)")' in _syl,
      "unit predicate matches both prefixed and bare topic names")
check('a[i * 2] = "%：" + names[i];' in _syl
      and 'a[i * 2 + 1] = names[i];' in _syl,
      "unit args keep the exact placeholder order, Chinese colon included")
check(all(k in _syl for k in ("matchNames", "maxUnits", "unitLabel", "bookCount")),
      "syllabus exposes ordered unit lookup for UI and SQL")
check("public static Object parse(String text)" in _json
      and "unknown escape" in _json,
      "dependency-free JSON parser is strict, not a silent fallback")
check("Syllabus.fromJson" in _assets and "Syllabus.empty()" in _assets,
      "asset loader degrades to term-only filtering when the table is broken")
check("random(int grade, int subject, boolean allSubjects," in _d,
      "QuizDb exposes the study-scope aware random() overload")
check("String strict = where + termW + unitW" in _d
      and "String termOnly = where + termW" in _d,
      "QuizDb relaxes unit before term before grade")
check("concat(args, unitA)" in _d and "!other" in _d,
      "unit args are bound and skipped for the OTHER pool")
check("setStudyScope" in _e and "syllabus, termIndex, unitLimit" in _e,
      "engine forwards study scope to the SQL layer")
check("SyllabusAssets.get(this)" in _o and "st.termIndex()" in _o,
      "service injects the real table and both settings")
check('getInt("termIdx", Syllabus.TERM_ALL)' in _st
      and 'getInt("unitLimit", 0)' in _st,
      "both study-scope settings default to the old unrestricted behaviour")
check("rowTerm" in raw_s and "rowUnit" in raw_s and "学习进度" in raw_s,
      "settings exposes term / unit rows with an honest scope note")
check("SyllabusAssets.get(this)" in raw_s
      and "maxUnits" in raw_s and "unitLabel" in raw_s,
      "settings clamps the unit index to the real book size")
check("Syllabus.TERM_LABELS" in m and "学习进度：" in raw_m,
      "home status line reports the active study scope")
check(all(k in _run for k in ("Json.java", "Syllabus.java", "SyllabusTest")),
      "JSON parser, syllabus and its JVM test are compiled and executed")
check("build_syllabus.py --check" in b and "study_scope_check.py" in b,
      "build runs both syllabus consistency and runtime scope regressions")
check("STUDY_SCOPE_OK" in _scope and "FALLBACK_UNIT_TO_TERM_OK" in _scope,
      "runtime regression asserts the fallback order, not just final answers")
check('android:versionCode="36"' in _mf, "manifest versionCode advances to 36")
check('VERSION_CODE="36"' in b, "build script versionCode advances to 36")


# ---- code19：品牌口号统一（官网 + TV 横幅生成脚本）----
_site = read(os.path.join(ROOT, "site/index.html"))
_gen = read(os.path.join(ROOT, "tools/branding/make_icons.py"))
_legacy = "答对一题，续租 30 分钟"
_legacy_caption = "答对一题 · 续租 30 分钟"
_slogan = "答对题目，无限续租！"
check(_site.count(_slogan) >= 5,
      "site carries the unified slogan in title/hero/caption/branding/footer")
check(_slogan in _gen, "TV banner generator draws the unified slogan")
check(_legacy not in _site and _legacy not in _gen,
      "legacy 30-minute slogan is gone from site and banner generator")
check(_legacy_caption not in _site, "legacy TV caption wording is gone")


# ---- code20：官网收敛、U 盘侧载、页脚版权、设置页并排行 ----
def adb_word(text):
    """只认独立单词，避免 preloadBank / deadbeef 这类巧合子串误报。"""
    return re.search(r"\badb\b", text, re.I) is not None

_user_files = [
    "README.md", "CHANGELOG.md",
    "docs/ARCHITECTURE.md", "docs/BUILD.md", "docs/QUESTION_BANK.md", "docs/BANK_STATS.md",
    "site/index.html", "site/script.js", "site/style.css",
    "app/src/com/jager/tvquiz/MainActivity.java",
    "app/src/com/jager/tvquiz/SettingsActivity.java",
]
check(not any(adb_word(read(os.path.join(ROOT, f))) for f in _user_files),
      "no debug-bridge command remains in user-facing docs, site or in-app hints")
_site_js = read(os.path.join(ROOT, "site/script.js"))
check("U 盘" in _site and "文件管理器" in _site,
      "site documents USB-drive sideload through the TV file manager")
check('id="donate"' not in _site and 'href="#donate"' not in _site,
      "donate section and its nav link are gone from the site")
check("打赏" not in _site and "收款码" not in _site,
      "site no longer promotes donations or QR codes")
check("assets/donate_wechat.png" not in _site and "assets/donate_alipay.png" not in _site,
      "site references no donation QR image")
check(not os.path.isfile(os.path.join(ROOT, "site/assets/donate_wechat.png"))
      and not os.path.isfile(os.path.join(ROOT, "site/assets/donate_alipay.png")),
      "donation QR PNGs are removed from the public site")
check('© 2026 <a href="https://zhang.ge/"' in _site
      and '>张戈博客</a> 保留所有权利 · Proof of Homework' in _site,
      "footer links 张戈博客 to https://zhang.ge/ and states the new copyright")
check("保留所有权利 · Proof of Homework" in _site and "jagerzhang · PoHW" not in _site,
      "footer copyright uses the new wording")
check('id="year"' not in _site and 'getElementById("year")' not in _site_js,
      "footer year is fixed in HTML, not overwritten by JS")
check("versionCode 35" in _site and "versionCode 21" in _site_js,
      "site reports the latest build 35 and keeps the v1.0.2 card note")
check("addView(bankRow, rowLp())" in s,
      "bank update and auto-update share one side-by-side row")
check("track(btnCheck, rowSync)" in s,
      "both bank buttons are registered in one vertical focus row")
check("bankPair = new Button[]{btnCheck, rowSync};" in s,
      "bank button pair is registered for the horizontal focus chain")
check("bankPair[0].setNextFocusRightId(rightId);" in s
      and "bankPair[1].setNextFocusLeftId(leftId);" in s,
      "DPAD left/right moves inside the paired bank row")
check('addRow(quiz, "自动更新"' not in s,
      "auto-update row no longer lives in the quiz card")
check(raw_s.find('"检查题库更新"') < raw_s.find('"自动更新"'),
      "auto-update sits to the right of the bank-update button")
check('"自动更新 · "' in s,
      "auto-update value button keeps its label in refreshRows")

# ---- code21：3 个发布版本 / CHANGELOG 归档 / README / CI / 收款码 ----
import hashlib
import struct

_mf21 = read(os.path.join(ROOT, "app/AndroidManifest.xml"))
_b21 = read(os.path.join(ROOT, "app/build.sh"))
_ch21 = read(os.path.join(ROOT, "CHANGELOG.md"))
_rm21 = read(os.path.join(ROOT, "README.md"))
_ci21 = os.path.join(ROOT, ".github/workflows/ci.yml")
_ci_text = read(_ci21) if os.path.isfile(_ci21) else ""
check('android:versionName="1.2.3"' in _mf21
      and 'VERSION_NAME="1.2.3"' in _b21,
      "display version is 1.2.3 in both manifest and build script")
check(_site_js.count('version: "v1.2.3"') == 1
      and _site_js.count('version: "v1.2.2"') == 1
      and _site_js.count('version: "v1.2.1"') == 1
      and _site_js.count('version: "v1.2.0"') == 1
      and _site_js.count('version: "v1.0.2"') == 1
      and _site_js.count('version: "v1.0.1"') == 1
      and _site_js.count('version: "v1.0.0"') == 1
      and _site_js.count('version: "') == 7,
      "site release notes expose exactly seven distinct release versions")
check(_ch21.count("\n## v1.2.3") == 1 and _ch21.count("\n## v1.2.2") == 1
      and _ch21.count("\n## v1.2.1") == 1
      and _ch21.count("\n## v1.2.0") == 1
      and _ch21.count("\n## v1.0.2") == 1 and _ch21.count("\n## v1.0.1") == 1
      and _ch21.count("\n## v1.0.0") == 1
      and _ch21.count("\n## v0.9.0-re") == 1,
      "changelog groups builds into seven releases plus one dev archive")
check("## Dev build v6.0.0" not in _ch21 and "## Dev build v5.0.0" not in _ch21
      and "## v4.1.0" not in _ch21 and "## v1 - v3" not in _ch21,
      "pre-1.0 dev-build sections are collapsed into the 0.9.0-re archive")
check("## 官网与部署" not in _rm21 and "EdgeOne" not in _rm21
      and "site/index.html" not in _rm21,
      "README no longer documents the static promo site")
check("## 发布流程" in _rm21 and "Pull Request" in _rm21 and "auto-review" in _rm21,
      "README documents feature branch / PR / auto review / maintainer tags")
check(os.path.isfile(_ci21) and "pull_request" in _ci_text
      and "tests/layout_contract.py" in _ci_text
      and "POHW_ALLOW_DEV_SIGN" in _ci_text,
      "GitHub Actions auto-review gates every PR into main")
_png21 = os.path.join(ROOT, "app/res/drawable-nodpi/donate_alipay.png")
with open(_png21, "rb") as _fh:
    _png_head = _fh.read(24)
_png_wh = struct.unpack(">II", _png_head[16:24]) if len(_png_head) >= 24 else (0, 0)
check(_png_head[:8] == bytes([137, 80, 78, 71, 13, 10, 26, 10])
      and _png_wh[0] >= 900 and _png_wh[1] >= 1000,
      "alipay QR crop keeps the full code and its white quiet zone")
check(hashlib.sha256(open(_png21, "rb").read()).hexdigest()
      == "992ed91fb5d92e1014673b65a369470421ee72d34b8269b607ff1bbe0646517b",
      "alipay QR asset is pinned to the freshly supplied poster crop")


# ---- v1.1.0：四项可选加固的默认值与行为符号 ----
_u = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/UnlockPolicy.java"))
_c = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/CooldownPolicy.java"))
_st = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsStore.java"))
_o_raw = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizOverlayService.java"))
_o_code = strip_comments(_o_raw)
_act = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsActivity.java"))
_qd = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizDb.java"))
_qf = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizFlow.java"))
check('DEFAULT_STREAK = 1' in _u and 'public boolean onCorrect()' in _u
      and 'public void onWrong()' in _u,
      "UnlockPolicy keeps the default one-question unlock")
check('safeRemaining(' in _c and 'STEPS_SEC = {30, 60, 120, 300}' in _c,
      "CooldownPolicy uses deadlines and detects clock rollback")
check('getInt("unlockStreak", 1)' in _st
      and 'getBoolean("strictOneShot", false)' in _st
      and 'getBoolean("cooldownEnabled", false)' in _st
      and 'getInt("questionSource", SOURCE_ALL)' in _st,
      "all four optional defaults preserve v1.0.2 behaviour")
check('unlock.onCorrect()' in _o_code and 'swapQuestion(unlock.progress())' in _o_code
      and 'unlock.onWrong()' in _o_code and 'quizView.correct(' in _o_code,
      "unmet streak swaps in place instead of unlocking")
check('applyCooldown()' in _o_code and 'quizView.cooldown(' in _o_code
      and 'restoreCooldown()' in _o_code and 'st.clearCooldown()' in _o_code,
      "cooldown only delays swapping and survives process restart")
check('KNOWLEDGE_ONLY_SQL' in _qd and "source='程序出题'" in _qd
      and 'engine.setKnowledgeOnly(knowledgeOnly)' in _o_code,
      "question preference filters the bank without deleting rows")
check('rowStreak = addRow' in _act and 'rowStrict = addRow' in _act
      and 'rowCooldown = addRow' in _act and 'rowSource = addRow' in _act,
      "settings expose all four optional hardening switches")
check('setStrict(strictOneShot)' in _o_code and 'wrongLimit()' in _qf
      and 'strict ? 1 : AnswerPolicy.WRONG_LIMIT' in _qf,
      "strict one-shot mode stays off by default")

check('return streak >= required;' in _u,
      "UnlockPolicy requires the configured streak before unlocking")
check('unlock.onWrong();' in _o_code,
      "wrong answer resets the streak in live code")


# ---- versionCode 23：出题偏好不得回退到含纲目题的全库 ----
_qd_code = strip_comments(_qd)
check('if (knowledgeOnly) {' in _qd_code and 'return kq;' in _qd_code
      and _qd_code.count('KNOWLEDGE_ONLY_SQL') >= 3,
      "knowledge-only chain keeps the source constraint on every fallback")
check(_qd_code.index('if (knowledgeOnly) {') < _qd_code.index('String termW ='),
      "knowledge-only chain runs before term/unit filtering")
check('不适用（仅知识题模式）' in _act
      and '仅知识/计算题模式不受学习进度限制' in _act,
      "settings mark study scope as not applicable under the preference")


# ---- code24：题目难度倾向加权 ----
_qd24 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizDb.java"))
_dp24 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/DifficultyPolicy.java"))
_st24 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsStore.java"))
_e24 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizEngine.java"))
_sv24 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizOverlayService.java"))
_act24 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsActivity.java"))
check("LEVEL_ALL = 0" in _dp24 and "MATCH_RATE = 80" in _dp24,
      "difficulty uses 80/20 weighted preference, never a hard filter")
check('getInt("difficultyLevel", 0)' in _st24,
      "difficulty defaults to unlimited so upgrade behaviour is unchanged")
check("DifficultyPolicy.useLevel(difficultyLevel, r.nextInt(100))" in _e24,
      "engine rolls the weighted decision before drawing")
check("setDifficultyLevel(difficultyLevel)" in _sv24,
      "service injects the difficulty setting into the engine")
_act24_code = strip_comments(_act24)
check("rowDifficulty = addRow" in _act24_code and "不限（默认）" in _act24,
      "settings expose the four difficulty choices with unlimited as default")
check("String diffW = matchLevel ? DifficultyPolicy.predicate(difficultyLevel)" in _qd24,
      "difficulty predicate is conditional on the weighted roll")
check(_qd24.count("diffW") >= 6,
      "every fallback layer keeps the difficulty predicate when matching a level")
check('String rootW = diffRoot.isEmpty() ? "WHERE grade<=?" : diffRoot + " AND grade<=?"' in _qd24
      and 'pick(rootW, new String[]{g})' in _qd24,
      "final fallback keeps both the difficulty predicate and the grade cap")
check("KNOWLEDGE_ONLY_SQL + diffW" in _qd24,
      "knowledge-only chain also keeps the difficulty predicate")

# ---- versionCode 28：答对自动继续 + 暂停信号互斥 ----
_st28 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsStore.java")))
_sa28 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/SettingsActivity.java")))
_o28 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizOverlayService.java")))
_v28 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/OverlayQuizView.java")))
_mp28 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/MediaPauseController.java")))
check('getBoolean("autoContinueOnCorrect", true)' in _st28,
      "auto-continue defaults to enabled")
check('rowAutoContinue = addRow' in _sa28
      and 'autoContinueOnCorrect(!st.autoContinueOnCorrect())' in _sa28,
      "quiz settings card exposes the auto-continue toggle")
check('autoContinue = st.autoContinueOnCorrect();' in _o28
      and 'if (autoContinue) {' in _o28
      and 'quizView.correctAuto(' in _o28,
      "service reads the setting and auto-finishes a correct answer")
check('if (quizShowing) finishQuiz();' in _o28,
      "auto-finish guards against a second dismissal")
check('correctAuto(String msg, String exp)' in _v28
      and 'cont.setVisibility(View.INVISIBLE)' in _v28,
      "auto-finish hides the continue button")
check('requestAudioFocus(focusListener' in _mp28
      and 'am.abandonAudioFocus(focusListener)' in _mp28,
      "legacy build uses only the pre-API-19 audio focus calls")
check('dispatchMediaKeyEvent' not in _mp28
      and 'AudioFocusRequest' not in _mp28,
      "legacy dex carries no API 21+ media classes")

# ---- v1.2.1：练习模式、控制台改版、守护挂起 ----
_m121 = strip_comments(read(MAIN))
_s121 = strip_comments(read(SET))
_p121 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/PracticeActivity.java")))
_o121 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizOverlayService.java")))
_e121 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/QuizEngine.java")))
_c121 = strip_comments(read(os.path.join(ROOT, "app/src/com/jager/tvquiz/PracticeConfig.java")))
_mf121 = read(os.path.join(ROOT, "app/AndroidManifest.xml"))
check('actionButton(row1, "练习模式"' in _m121
      and 'PracticeActivity.class' in _m121
      and 'actionButton(row2, "授权悬浮窗"' in _m121,
      "practice mode takes the former quiz-now slot and overlay authorization is restored")
check('"立即出题"' not in _m121 and 'ACTION_QUIZ_NOW' not in _m121,
      "the old immediate-quiz button is gone from the console")
check('ACTION_MANAGE_OVERLAY_PERMISSION' in _m121,
      "restored console button opens the system overlay settings")
check('"授权悬浮窗"' in _s121 and 'ACTION_MANAGE_OVERLAY_PERMISSION' in _s121,
      "overlay authorization moved into the boot/playback settings card")
check('android:name=".PracticeActivity"' in _mf121,
      "practice activity is registered in the manifest")
check('QuizOverlayService.setPracticeMode(true)' in _p121
      and 'QuizOverlayService.setPracticeMode(false)' in _p121,
      "practice suspends the guard on enter and resumes it on exit")
check('if (practiceMode)' in _o121 and 'setPracticeMode(boolean v)' in _o121,
      "guard service honours the practice suspension flag")
check('nextFrom(int s)' in _e121 and 'nextFrom(cfg.subjectForDraw())' in _p121,
      "practice draws questions per selected subject")
check('private int subject = MIXED;' in _c121 and 'private int grade = 3;' in _c121,
      "practice defaults to mixed subjects and grade 3")
check('asked % 3' in _c121,
      "mixed practice rotates across the three subjects")

# ---- v1.2.3：所有抽题层（含最后一层保底）都必须带年级上限 ----
check('POOL_CORE_ALL = "WHERE grade<=? AND subject=?"' in _d
      and _d.count('grade<=?') >= 6,
      "all four pool predicates keep the grade cap")
check('String rootW = diffRoot.isEmpty() ? "WHERE grade<=?"' in _d,
      "the last-resort query also keeps the grade cap")

print("LAYOUT_CONTRACT_OK total=%d" % TOTAL)
