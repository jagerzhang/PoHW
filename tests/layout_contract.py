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
check(s.count("addCard(") == 7, "settings declares 6 grouped cards + helper")
check(len(re.findall(r'addCard\(col, "0\d', s)) == 6, "settings has 6 numbered cards")
check("addColumn" not in raw_s and "wireColumns" not in raw_s,
      "legacy three-column helpers are gone")
check(s.count("wireVerticalFocus()") == 3, "settings declares and calls the vertical focus chain")
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
check('android:versionCode="44"' in _mf, "manifest versionCode advances to 44")
check('VERSION_CODE="44"' in b, "build script versionCode advances to 44")
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
check(s.count("rowLp()") == 8, "row gap is applied to every row, button, note and paired bank row")
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
check('android:versionCode="44"' in _mf, "manifest versionCode advances to 44")
check('VERSION_CODE="44"' in b, "build script versionCode advances to 44")


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
check("versionCode 44" in _site and "versionCode 21" in _site_js,
      "site reports the latest build 44 and keeps the v1.0.2 card note")
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
check('android:versionName="1.3.0"' in _mf21
      and 'VERSION_NAME="1.3.0"' in _b21,
      "display version is 1.3.0 in both manifest and build script")
check(_site_js.count('version: "v1.3.0"') == 1
      and _site_js.count('version: "v1.2.7"') == 1
      and _site_js.count('version: "v1.2.6"') == 1
      and _site_js.count('version: "v1.2.5"') == 1
      and _site_js.count('version: "v1.2.4"') == 1
      and _site_js.count('version: "v1.2.3"') == 1
      and _site_js.count('version: "v1.2.2"') == 1
      and _site_js.count('version: "v1.2.1"') == 1
      and _site_js.count('version: "v1.2.0"') == 1
      and _site_js.count('version: "v1.0.2"') == 1
      and _site_js.count('version: "v1.0.1"') == 1
      and _site_js.count('version: "v1.0.0"') == 1
      and _site_js.count('version: "') == 12,
      "site release notes expose exactly twelve distinct release versions")
check(_ch21.count("\n## v1.3.0") == 1
      and _ch21.count("\n## v1.2.7") == 1
      and _ch21.count("\n## v1.2.6") == 1
      and _ch21.count("\n## v1.2.5") == 1
      and _ch21.count("\n## v1.2.4") == 1 and _ch21.count("\n## v1.2.3") == 1 and _ch21.count("\n## v1.2.2") == 1
      and _ch21.count("\n## v1.2.1") == 1
      and _ch21.count("\n## v1.2.0") == 1
      and _ch21.count("\n## v1.0.2") == 1 and _ch21.count("\n## v1.0.1") == 1
      and _ch21.count("\n## v1.0.0") == 1
      and _ch21.count("\n## v0.9.0-re") == 1,
      "changelog groups builds into twelve releases plus one dev archive")
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
check('KNOWLEDGE_ONLY_SQL' in _qd and "qtype='knowledge'" in _qd
      and 'engine.setKnowledgeOnly(knowledgeOnly)' in _o_code,
      "question preference filters recognition templates without deleting rows")
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
check('usedMediaKey' in _mp28 and 'if (!focusHeld) {' in _mp28
      and _mp28.count('dispatch(KeyEvent.KEYCODE_MEDIA_PAUSE)') == 1,
      "media pause is only a fallback when audio focus was denied")
check(_mp28.find('dispatch(KeyEvent.KEYCODE_MEDIA_PLAY)')
      < _mp28.find('abandonAudioFocus'),
      "resume key is sent before releasing audio focus")

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

# ---- v1.2.4：练习模式题目卡片必须真的可见 ----
# Ui.fill(w) 生成 LayoutParams(MATCH_PARENT, 0, w)：w=0 时子 View 在垂直
# LinearLayout 中收到 EXACTLY 0 高度，题干 / 选项 / 解析全部被压没，setMinHeight
# 也救不回来。用户反馈「自由练习题目没有内容」即由此而来。
_src124 = os.path.join(ROOT, "app/src/com/jager/tvquiz")
for _fn124 in sorted(os.listdir(_src124)):
    if _fn124.endswith(".java"):
        check("Ui.fill(0)" not in strip_comments(read(os.path.join(_src124, _fn124))),
              "no zero-height weighted view in " + _fn124)
_m124 = re.search(r"card\.addView\(text,\s*Ui\.fill\((\d+)\)\)", _p121)
check(_m124 is not None and int(_m124.group(1)) > 0,
      "practice question text has a positive height weight")
_rows124 = re.findall(r"card\.addView\(row,\s*Ui\.fixed\(this,\s*(\d+)\)\)", _p121)
check(len(_rows124) == 1 and int(_rows124[0]) >= 64,
      "practice option rows have a real height for the 56dp buttons")
_m124b = re.search(r"card\.addView\(explain,\s*Ui\.fill\((\d+)\)\)", _p121)
check(_m124b is not None and int(_m124b.group(1)) > 0,
      "practice explanation has a positive height weight")
check("MAX_GRADE = 6" in _c121 and "MAX_GRADE = 9" not in _c121,
      "practice grade selector matches the grade 1..6 built-in bank")


# ---- v1.2.5：在线版本更新契约 ----
_src125 = os.path.join(ROOT, "app/src/com/jager/tvquiz")
_cfg125 = strip_comments(read(os.path.join(_src125, "UpdateConfig.java")))
_chk125 = strip_comments(read(os.path.join(_src125, "UpdateChecker.java")))
_ins125 = strip_comments(read(os.path.join(_src125, "UpdateInstaller.java")))
_sto125 = strip_comments(read(os.path.join(_src125, "SettingsStore.java")))
_mf125 = read(os.path.join(ROOT, "app/AndroidManifest.xml"))
_edge125 = read(os.path.join(ROOT, "site/edgeone.json"))
_check_script = read(os.path.join(ROOT, "tools/publish/update_manifest.py"))
check('getBoolean("autoCheckUpdate", true)' in _sto125,
      "automatic version check defaults to enabled")
check('CHECK_INTERVAL_MS = 24L' in _cfg125,
      "version check is throttled to 24 hours")
check('safeUrl(' in _cfg125 and 'HOST + "/dl/"' in _cfg125,
      "download URLs are pinned to the official site")
check('validSha256(' in _cfg125 and 'i.downloadUrl() == null' in _cfg125,
      "manifest sha256 and download URL are validated before use")
check('setInstanceFollowRedirects(false)' in _chk125
      and 'UpdateConfig.HOST' in _chk125,
      "checker refuses cross-host redirects")
check('MessageDigest.getInstance("SHA-256")' in _ins125
      and 'info.sha256.equalsIgnoreCase(actual)' in _ins125,
      "downloaded APK is verified against the manifest sha256")
check('Intent.ACTION_VIEW' in _ins125
      and 'FLAG_GRANT_READ_URI_PERMISSION' in _ins125,
      "system installer is launched with a granted Uri")
check('REQUEST_INSTALL_PACKAGES' in _mf125
      and 'com.jager.tvquiz.update' in _mf125,
      "install permission and private update provider are declared")
check('android:exported="false"' in _mf125
      and 'grantUriPermissions="true"' in _mf125,
      "update provider is not exported and grants read access")
check('no-store' in _edge125 and '/update.json' in _edge125,
      "edgeone.json disables caching for the update manifest")
check('def check(strict=False)' in _check_script
      and 'no-store' in _check_script,
      "manifest gate checks structure and cache rules")
check('update_manifest.py --check' in read(BUILD),
      "build gate validates the update manifest")
check('startUpdateCheck(false)' in strip_comments(read(MAIN)),
      "console triggers the throttled version check on cold start")
# ---- v1.2.6：在线更新卡片不得破坏纵向焦点链 ----
_s126 = strip_comments(read(SET))
check('btnUpdateInstall.setEnabled(' not in _s126,
      "update buttons never become disabled (a disabled node breaks the DPAD chain)")
check('wireVerticalFocus();' in _s126.split('private void refreshRows()')[1]
      .split('private void checkUpdate()')[0],
      "refreshRows rebuilds the vertical focus chain after state changes")
check('正在检查版本，请稍候' in _s126 and '当前已是最新版本' in _s126,
      "both update buttons give immediate, explicit feedback")
check('updateChecking' in _s126 and 'updateDownloading' in _s126,
      "re-entrant taps are guarded without disabling focusable views")
check('下载并安装新版本（暂无更新）' in _s126,
      "the install button explains why it has nothing to install")


# ---- v1.2.5 后续：官网下载直链固定走 Makers ----
_dl125 = read(os.path.join(ROOT, "site/index.html"))
check(_dl125.count('href="https://pohw.zhangge.net/dl/latest-main.apk"') == 4,
      "all four download buttons use the Makers direct link")
check('href="https://github.com/jagerzhang/PoHW/releases/latest"' not in _dl125,
      "no download button points at the GitHub latest release")
check('https://github.com/jagerzhang/PoHW/releases"' in _dl125
      and '所有 Releases' in _dl125,
      "historical releases remain reachable")
check('must-revalidate' in _edge125,
      "latest APK aliases are revalidated so the CDN never serves a stale package")


# ---- v1.2.7：逐题复核成果必须真的随 APK 发布 ----
_bv127 = read(os.path.join(ROOT, "app/assets/quiz.db.version")).strip()
check("bank-v5" in _bv127, "shipped bank is the reviewed bank-v5")
check(os.path.isfile(os.path.join(ROOT, "tools/quizbank/apply_review.py")),
      "bank-v5 has a reproducible review-apply script")
for _f127 in ("disposition-final.csv", "supplement-v1.json", "per-question-flags.csv"):
    check(os.path.isfile(os.path.join(ROOT, "tools/quizbank/review", _f127)),
          "review evidence file " + _f127 + " is committed")


# ---- v1.3.0 未发布：按组答题 + 休眠唤醒 / HOME 兜底 ----
_src130 = os.path.join(ROOT, "app/src/com/jager/tvquiz")
_rp130 = read(os.path.join(_src130, "RoundPolicy.java"))
_gw130 = read(os.path.join(_src130, "GuardWatchdog.java"))
_bp130 = read(os.path.join(_src130, "BootPolicy.java"))
_o130 = strip_comments(read(os.path.join(_src130, "QuizOverlayService.java")))
_sto130 = strip_comments(read(os.path.join(_src130, "SettingsStore.java")))
_mf130 = read(os.path.join(ROOT, "app/AndroidManifest.xml"))
_ax130 = read(os.path.join(ROOT, "app/res/xml/guard_accessibility.xml"))
_sa130 = strip_comments(read(SET))
check("DEFAULT_TARGET = 5" in _rp130 and "DEFAULT_PASS_PERCENT = 60" in _rp130,
      "group quiz defaults to 5 questions at 60 percent")
check("minCorrect()" in _rp130 and "CONTINUE" in _rp130
      and "PASS" in _rp130 and "FAIL" in _rp130,
      "round policy exposes three verdicts and integer pass threshold")
check('getInt("roundMode", ROUND_GROUP)' in _sto130
      and 'getInt("questionsPerRound", 5)' in _sto130
      and 'getInt("passPercent", 60)' in _sto130,
      "settings defaults enable group quiz of 5 at 60 percent")
check("unlock.onWrong()" in _o130,
      "wrong answer still resets the streak in streak mode")
check("private RoundPolicy round" in _o130 and "round.onAnswer(" in _o130,
      "group progress lives in the service, not the per-question view")
check("grantUnlock(" in _o130 and "usedInRound" in _o130,
      "group pass has one unlock path and avoids repeating questions")
check("rowRoundSize = addRow" in _sa130 and "rowPassRate = addRow" in _sa130,
      "both group setting rows are declared")
check("rowRoundSize.setText" in _sa130 and "rowPassRate.setText" in _sa130,
      "both group setting rows refresh their values")
check("shouldRevive(" in _bp130 and "USER_PRESENT" in _bp130,
      "revive actions have their own policy")
check('android.intent.action.USER_PRESENT' in _mf130
      and 'com.jager.tvquiz.REVIVE' in _mf130,
      "revive receiver listens for wake, unlock and self-check")
check('.ReviveReceiver' in _mf130
      and 'android:permission="android.permission.RECEIVE_BOOT_COMPLETED"' in _mf130,
      "revive receiver is separate from the protected boot receiver")
check("setPersisted(true)" in _gw130 and "setInexactRepeating" in _gw130,
      "watchdog uses both JobScheduler and AlarmManager")
check("GuardWatchdog.schedule(this)" in _o130
      and "GuardWatchdog.scheduleAlarm(this)" in _o130,
      "guard service actually schedules both watchdog paths")
check("ACTION_CLOSE_SYSTEM_DIALOGS" in _o130 and "rebuildOverlay()" in _o130,
      "HOME is detected and the overlay is rebuilt to regain focus")
check('canRetrieveWindowContent="false"' in _ax130
      and "BIND_ACCESSIBILITY_SERVICE" in _mf130,
      "accessibility watchdog is read-only and permission bound")
check('stopWithTask="false"' in _mf130,
      "guard service survives task removal")
check('android:versionName="1.3.0"' in _mf130 and 'android:versionCode="44"' in _mf130,
      "v1.3.0 is the published version in manifest")


# ---- v1.3.0 未发布：答题选项长文本不得被固定高度网格裁切 ----
_ov130 = strip_comments(read(os.path.join(ROOT,
        "app/src/com/jager/tvquiz/OverlayQuizView.java")))
_qts130 = read(os.path.join(ROOT,
        "app/src/com/jager/tvquiz/QuizTextSizer.java"))
check("optionSp(" in _qts130 and "optionFits(" in _qts130,
      "option text has a dynamic size and a fit calculation")
check("QuizTextSizer.optionSp(q.options)" in _ov130
      and "opts[i].setTextSize(" in _ov130,
      "all four option buttons share the dynamic size")
check("setMaxLines(4)" in _ov130 and "setIncludeFontPadding(false)" in _ov130
      and "setEllipsize(null)" in _ov130,
      "option buttons allow four lines without font padding or ellipsis")
check("if (max > 48) return 14;" in _qts130
      and "if (max > 24) return 16;" in _qts130,
      "long options use the safe 16sp/14sp bands, never 19sp+")
check("Ui.dp(c, 200)" in _ov130 and "Ui.dp(c, 186)" not in _ov130,
      "option grid has enough height for wrapped text")
check(os.path.isfile(os.path.join(ROOT, "tests/src/QuizTextSizerTest.java")),
      "option layout has a JVM regression test")
check(os.path.isfile(os.path.join(ROOT, "tools/ui_check/analyze_dump.py")),
      "real-device UI dump analyzer is available")


# ---- v1.3.0 未发布：局域网双后台与二维码 ----
_src131 = os.path.join(ROOT, "app/src/com/jager/tvquiz")
_qr131 = read(os.path.join(_src131, "QrEncoder.java"))
_lan131 = read(os.path.join(_src131, "LanServer.java"))
_api131 = read(os.path.join(_src131, "AdminApi.java"))
_assets131 = read(os.path.join(_src131, "AdminAssets.java"))
_pair131 = read(os.path.join(_src131, "PairActivity.java"))
_sto131 = strip_comments(read(os.path.join(_src131, "SettingsStore.java")))
_mf131 = read(os.path.join(ROOT, "app/AndroidManifest.xml"))
check("versionFor(" in _qr131 and "reedSolomon(" in _qr131
      and "drawCodewords(" in _qr131,
      "zero-dependency QR encoder covers data, RS and placement")
check("text.length() == 0) return -1" in _qr131,
      "QR encoder rejects empty input")
check(os.path.isfile(os.path.join(ROOT, "tests/src/QrEncoderTest.java")),
      "QR encoder has a standard-library matrix comparison test")
check("ServerSocket(p)" in _lan131 and "PORT = 8099" in _lan131
      and "PORT_TRIES" in _lan131,
      "LAN admin prefers 8099 and only falls back when the port is occupied")
check("github.com" not in _lan131 and "http://" not in _api131,
      "LAN admin has no outbound internet endpoints")
check("authorized(" in _api131 and "lockUntil" in _api131
      and "checkPin(" in _api131,
      "LAN admin enforces token, PIN and failure lockout")
check('getBoolean("lanAdminEnabled", false)' in _sto131,
      "LAN admin is disabled by default")
check("if (!ok) return null;" in _assets131 and "ALLOWED" in _assets131,
      "admin assets use a filename whitelist")
check('android:name=".PairActivity"' in _mf131
      and 'android:exported="false"' in _mf131,
      "pairing screen is declared and not exported")
for _f131 in ("index.html", "app.js", "style.css"):
    check(os.path.isfile(os.path.join(ROOT, "app/assets/admin", _f131)),
          "admin page asset " + _f131 + " ships with the APK")
check("QrEncoder.encode(url)" in _pair131 and "Bitmap.createBitmap" in _pair131,
      "pairing screen renders the QR locally")
check("不访问互联网" in read(os.path.join(ROOT, "site/index.html")),
      "site copy no longer claims zero network traffic")


# ---- v1.3.0：遥控器四选一不接收会被裁切的超长选项题 ----
_o130 = strip_comments(read(os.path.join(ROOT,
        "app/src/com/jager/tvquiz/QuizOverlayService.java")))
check("QuizTextSizer.optionsFitTv(q.options)" in _o130,
      "guard skips questions whose options cannot fit a TV four-choice card")
check("TV_OPTION_MAX_CHARS = 48" in _qts130,
      "TV option length limit is pinned")


# ---- v1.3.0：手机管理后台回归修复 ----
_api130b = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/AdminApi.java"))
_lan130 = read(os.path.join(ROOT, "app/src/com/jager/tvquiz/LanServer.java"))
check("getPackageManager().getPackageInfo(" in _api130b
      and '\"version\":\"1.3.0\"' not in _api130b,
      "LAN status reads the real app version instead of a hardcoded string")
check("Character.isWhitespace(body.charAt(k))" in _api130b,
      "config API parses every numeric field, not just the last one")
check("PORT_TRIES" in _lan130 and "activePort" in _lan130,
      "LAN server falls back to another port when 8099 is occupied")
check('"/".equals(path) || "/index.html".equals(path)' in _lan130
      and 'lowPath.contains("%2e")' in _lan130,
      "LAN server only serves whitelisted paths and rejects traversal")
check("QuizTextSizer.questionFitTv(q.text)" in strip_comments(read(os.path.join(
        ROOT, "app/src/com/jager/tvquiz/QuizOverlayService.java"))),
      "guard skips stems that cannot be shown or answered on TV")

print("LAYOUT_CONTRACT_OK total=%d" % TOTAL)
