# PoHW — Proof of Homework

> 给 Android 电视加一道门槛：**到点弹出一道题，答对才能继续看**。
> 一个不上架应用商店、供家长自行侧载的家庭小工具，默认完全离线。

| | |
| --- | --- |
| 当前版本 | **v1.0.1**（`versionCode 18`，展示版本不再随构建滚动） |
| 运行平台 | Android TV / 电视盒子，`minSdk 21`，遥控器操作 |
| 分发方式 | 自行侧载 APK，**不在任何应用商店上架** |
| 签名证书 | SHA-256 `76eb6063…6172`，历次构建连续，可 `adb install -r` 覆盖 |
| 仓库 | https://github.com/jagerzhang/PoHW |
| 官网 | 腾讯云 EdgeOne Makers（见下方「官网与部署」） |

![PoHW 图标](site/assets/ic_launcher.png)

---

## 它能做什么

- **跨应用全屏答题遮罩**：守护服务在后台计时，到点把一道四选一题盖在任意界面之上；答对才放行，遮罩吞掉媒体键与返回键。
- **答错两次自动换题**：同一题连错 2 次原地换题，并写入独立的错题库（`pohw_wrong.db`），家长可翻页查看、删除或清空。
- **内置 5,129 题离线 K12 题库**：`assets/quiz.db` 随 APK 发布，首次启动复制到本地数据库，之后只读、零网络；另有按年级分档的参数化算式生成器兜底。
- **四槽学科配比**：数 / 语 / 英 / 其他默认 `5:3:2:2`，`0–5` 可调；开启「全学科」才加入第 4 槽，且题库确实有非三科题时才生效。
- **学习进度（方案 B）**：按「上册 / 下册」与「已学到第 N 单元」限制出题范围，解决「刚开学却抽到整学年题」的问题，详见下一节。
- **家长设置 + PIN**：首次进入设置要求初始化 4–8 位数字密码（PBKDF2 + 随机盐），关闭守护需就地验证密码，界面不退出。
- **答题时暂停 / 恢复播放**：音频焦点 + 媒体键请求播放器暂停，答完尝试恢复（best-effort，见「已知边界」）。
- **启动即守护**：APP 冷启动自动拉起守护（默认开）；开机自启默认关，需家长自行打开并放行厂商自启动权限。
- **错题库、统计与打赏**：首页看答对率与最近错题；设置页有静态收款码页（微信 / 支付宝，遥控器左右切换），无支付 SDK、无订单、无联网。

## 它不能做什么

- 不是家长控制 / 设备锁 / 应用锁，也不阻止卸载、不阻止用其他设备看视频。
- 不联网收集任何数据；`INTERNET` 权限仅供家长**自行配置**的可选在线题库源，地址为空时一次请求都不发。
- 不控制第三方视频 App 的播放状态，暂停/恢复是尽力而为的系统级请求。
- 不是教材同步练习题库：内置题是知识点纲目题与参数化算式，不含出版社真题。

---

## 快速开始

```bash
# 1) 拿到 APK：到 Releases 下载，或按下方「构建」自行出包
adb install -r PoHW-v1.0.1.apk

# 2) 打开 APP，点「授权悬浮窗」，在系统设置里允许「显示在其他应用上层」
# 3) 进「家长设置」：首次会要求初始化 4-8 位数字密码
# 4) 设好年级 / 出题频率 / 学科配比 / 学习进度，点「保存并返回」
# 5) 回首页点「开启守护」，按 Home 切到任意视频 App，到点会弹题
```

忘记密码只能在电脑执行 `adb shell pm clear com.jager.tvquiz`（会清空设置、统计与错题库）。

---

## 学习进度：学期 + 单元（方案 B）

内置题库的 `questions` 表有 `grade`、`term`（上 / 下 / 全 / 空）和 `topic`（形如 `几何：时分秒`），
但**没有教材版本、单元序号和教学顺序**。因此 APP 用一份人工维护的有序进度表补齐数学部分：

| 设置项 | 取值 | 默认 | 效果 |
| --- | --- | --- | --- |
| 学习学期 | 全学年 / 上册 / 下册 | 全学年 | 加 `term IN ('上','全')` 或 `('下','全')`；顺带排除 810 道无学期的「综合练习」（里面混有超纲题） |
| 已学单元 | 不限 / 第 1…N 单元 | 不限 | 按 `assets/syllabus.json` 前 N 个单元，逐个参数化匹配 `topic` 里的单元名 |

- **进度表**：`app/assets/syllabus.json` 覆盖小学 **1–6 年级数学 × 上/下册 = 12 册、105 个有序单元、134 个别名**。别名解决题库里「时、分、秒 / 时分秒」「万以内的加法和减法(一)(二)」这类同单元异名问题。
- **必须先选上/下册**：全学年没有单一教学顺序，此时「已学单元」自动禁用，仅按学期过滤。
- **诚实降级**：语文的 `topic` 是课文名、英语是主题词，7–9 年级也没有可靠顺序，这些组合**只做学期过滤**，不假装精确到单元；「其他学科」池同理。
- **兜底顺序**：同年级+同学科+学期+单元 → 放宽单元 → 放宽学期 → 放宽年级（仍同学科）→ 全库。前四层始终保留学科约束，不会退回串科题。
- **升级安全**：两个设置都是新键，默认「全学年 / 不限」，覆盖安装不改变原有抽题范围。

改进度表请同步运行 `python3 tools/quizbank/build_syllabus.py`（重新生成）或 `--check`（门禁校验）：
脚本会拒绝任何在题库里不存在、遗漏、重复或含 SQL 通配符的别名。

---

## 内置题库与许可证

`app/assets/quiz.db`（4.5 MB，SHA-256 `283e86fc…f6`）含 4 张表、`questions` 17 列、5,129 题：
4,319 道知识点纲目题 + 810 道程序出题，覆盖 1–9 年级与 19 个学科。构建时 `tools/quizbank/verify.py`
会重算算式、随机抽样校验答案、做篡改检测。

题库上游是开源的 [RedSamerai/learning-content](https://github.com/RedSamerai/learning-content)
知识点纲目，**仓库与 APK 不含任何第三方真题文本或付费题数据**；分学科教材版本只用于在线题包筛选
与题面标注，不改变离线抽题结果。详见 `docs/QUESTION_BANK.md`、`docs/BANK_STATS.md`。

在线题库源是**可选预留**：家长填了自己的 `index.json` 地址后才会按周期联网，默认关闭。

---

## 家长设置速查

| 分组 | 设置 | 默认 |
| --- | --- | --- |
| 出题基础 | 年级（小学 1–6 / 初中 1–3）、频率（15 秒–30 分钟）、出题范围、学习学期、已学单元、自动更新 | 三年级 / 30 秒 / 仅数语英 / 全学年 / 不限 |
| 学科设置 | 数 / 语 / 英 / 其他各自教材版本与配比 `0–5` | `5:3:2:2`，「通用(课标)」 |
| 开机与播放 | 开机自启、自动守护（启动即守护）、暂停视频、恢复播放、厂商自启设置入口 | 关 / 开 / 开 / 开 |
| 错题库 | 查看错题库（答错 2 次记录） | — |
| 在线题库源 | 源地址、检查题库更新、打赏作者 | 源为空（完全离线） |

设置页是单列卡片流 + 底部固定「保存并返回」操作条，所有取值行显式串好遥控器上下焦点链。

---

## 构建

零 Gradle：`aapt2 → javac → d8 → zipalign → apksigner`，需要 JDK 17 与 Android SDK 34。

```bash
JAVA_HOME=/opt/jdk17 ANDROID_HOME=/path/to/sdk \
POHW_KEYSTORE=/path/to/debug.keystore POHW_KEYSTORE_PASS=android \
bash app/build.sh
```

**签名硬门禁**：`build.sh` 会从成品 APK 读取证书 SHA-256，与已发布证书
`76eb6063b574…26172` 比对，不一致直接失败。用错 keystore 会导致电视上覆盖安装失败，
所以这一步不依赖人记得传环境变量。本地实验构建须显式 `POHW_ALLOW_DEV_SIGN=1`。
完整说明见 `docs/BUILD.md`。

版本策略：`versionName` 语义化（当前 1.0.1），`versionCode` 单调递增（当前 18）且**不随展示版本重置**，
否则已安装旧版的设备会报 `INSTALL_FAILED_VERSION_DOWNGRADE`。

## 验证与门禁

`bash app/build.sh` 的 `[0/9]` 会先跑完下面 5 组检查，任何一条失败都不会出包：

| 检查 | 当前结果 | 守什么 |
| --- | --- | --- |
| `tests/layout_contract.py` | **140** 条 | 3×2 首页、单列设置、焦点链、证书门禁、方案 B 全链路符号与数据 |
| `tests/bank_distribution_check.py` | `BANK_DISTRIBUTION_OK` | 用真实 SQL 复刻四槽配比、池隔离与池保持兜底 |
| `tools/quizbank/build_syllabus.py --check` | `SYLLABUS_CHECK_OK books=12` | 进度表与题库同源、无遗漏别名 |
| `tests/study_scope_check.py` | `STUDY_SCOPE_OK` | 学期/单元范围不外溢、空池逐层放宽顺序、默认行为兼容 |
| `tests/run.sh` | **245** 项 JVM 单测（9 组） | PIN、按键时序、启动策略、权重、JSON / Syllabus、设置默认值 |

发布前另跑 `python3 tests/mutation_suite.py`：故意回退 36 处实现，再加 1 次不带发布密钥构建（37/37），
确认门禁每一处都能拦下，最后校验源码哈希完全恢复并用发布密钥重建 `dist/`。

## 权限

| 权限 | 用途 |
| --- | --- |
| `SYSTEM_ALERT_WINDOW` | 跨应用全屏答题遮罩（需家长手动在系统设置授权） |
| `FOREGROUND_SERVICE` / `POST_NOTIFICATIONS` | 后台计时守护与常驻通知 |
| `RECEIVE_BOOT_COMPLETED` | 可选的开机自启（默认关） |
| `INTERNET` | 仅家长自配在线题库源时才用，默认零请求 |

`allowBackup="false"`，默认禁止明文 HTTP，不含任何统计 / 广告 / 支付 SDK。

---

## 项目结构

```
app/
  AndroidManifest.xml  build.sh
  src/com/jager/tvquiz/   # 28+ 个 Java 类：守护服务、遮罩、PIN、
                          # QuizEngine / QuizDb / SubjectWeights /
                          # Syllabus + Json + SyllabusAssets
  res/                    # 5 档图标、TV banner、收款码、布局资源
  assets/                 # quiz.db / quiz.db.version / syllabus.json
tests/                    # JVM 单测 + 4 组 Python 契约 / 回归 / 变异脚本
docs/                     # ARCHITECTURE / BUILD / QUESTION_BANK / BANK_STATS
tools/quizbank/           # 题库生成、校验、进度表生成
tools/branding/           # 可复现图标生成脚本
site/                     # 纯静态宣传首页（直接上传 EdgeOne Makers）
CHANGELOG.md              # 逐次构建的完整变更与验证证据
```

## 官网与部署

宣传首页在 `site/`（`index.html` + `style.css` + `script.js` + 本地图片），纯静态、零构建依赖，
包含产品介绍、功能亮点、侧载步骤、下载入口、版本更新记录与 FAQ。

已部署到**腾讯云 EdgeOne Makers**：项目 `pohw-home`，`ProjectId: makers-optiyqkj3jjq`，
稳定域名 `https://pohw-home-qxpdwxk6.edgeone.cool`。国内默认域名直访需带控制台签发的
`eo_token` 鉴权参数（有时效），当前可分享链接形如：

```
https://pohw-home-qxpdwxk6.edgeone.cool?eo_token=<控制台签发>&eo_time=<时间戳>
```

绑定自定义域名后可免鉴权直接访问。重新部署（`.edgeone/project.json` 记录项目绑定）：

```bash
edgeone makers deploy ./site -n pohw-home --json
```

`src.zip` 也会带上 `site/`，便于从源码包复现官网。

## 变更记录

逐次构建的改动、验证数据与已知边界以 [CHANGELOG.md](CHANGELOG.md) 为准；官网「版本更新记录」
区块是同一份内容的可视化摘要。最近的几次构建：

| versionCode | 内容 |
| --- | --- |
| 18 | 学习进度（学期 + 单元，方案 B）、静态官网 |
| 17 | 离线说明归位；题库粒度只读调研 |
| 16 | 12dp 行间距、启动即守护、打赏页、APP 图标 |
| 15 | 四槽配比修订与独立复审加固 |
| 14 | 3×2 首页、单列卡片设置页 |
| 13 | 守护开关合并、设置项聚合 |
| 12 | v1.0.0 首个正式版，版本口径重置 |

## 已知边界 / FAQ

- **暂停视频是 best-effort**：靠音频焦点 + 媒体键请求，不保证所有视频 App 响应；小米等 ROM 的自启动权限也需家长手动放行。
- **题库不是教材同步练习**：纲目定位题 + 参数化算式，无出版社版本、无单元序号；数学单元进度靠内置 `syllabus.json` 补偿，语文 / 英语 / 初中暂不支持单元级。
- **全学年不能选单元**：需要先选上/下册，否则单元按钮会自动禁用。
- **真机待确认**：不同 ROM 的悬浮窗权限、`HIDE_OVERLAY_WINDOWS`、字体 150% 下的长文案、二维码远距离可扫、DPAD 切渠道。
- **覆盖安装**：务必使用同一签名证书；换 keystore 只能先卸载，会丢设置与错题库。

## 打赏与许可证

设置页「打赏作者 · 请喝一杯咖啡」是纯静态收款码，完全自愿、不影响任何功能。
代码许可证见 [LICENSE](LICENSE)；题库上游许可证边界见 `docs/QUESTION_BANK.md`。
