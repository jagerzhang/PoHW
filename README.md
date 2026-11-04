# PoHW — Proof of Homework

> **答对一题，续租 30 分钟。**

一个装在自己电视上的反沉迷小实验：开启守护后，前台服务会按设定周期在**任意应用之上**弹出全屏答题页，答对才能继续看电视，答错就一直挡着。

**PoHW** 是 Proof of Work 的一个玩笑 —— 看电视的通行权不是靠算力，而是靠周期性交一次「作业量证明」来续租。租约到期，重新出题。

本项目是**个人非商业**的 Android TV 实验，只发布 APK，不上架任何应用商店。

---

## 它能做什么

| 能力 | 说明 |
| --- | --- |
| 跨应用全屏答题 | `TYPE_APPLICATION_OVERLAY` 全屏窗口，切到视频 App / 桌面后照样弹出 |
| 完全离线题库 | 内置 SQLite，本地 `ORDER BY RANDOM()` 抽题，**默认一次网络请求都不发** |
| 全量 K12 纲目 | 4,319 个知识点 / 19 个学科 / 21 个年级段，幼儿园到九年级全量导入 |
| 年级与学科 | 1-9 年级、教材版本、出题频率、数/语/英配比，以及「全学科」出题开关 |
| 家长密码 | PBKDF2 + 随机 salt，保护设置页；**关闭守护也必须先验证**，服务侧二次校验 |
| 就地验证 | 点「关闭守护」不跳页、不退出，只叠加验证层并改变按钮颜色/文字 |
| 开机自启 | 仅在家长已开启守护且悬浮窗已授权时拉起前台服务 |
| 可选在线源 | 预留 `index.json + packs/*.json` 同步协议，需家长自己填地址才启用 |

## 它不能做什么

- 拦不住 **Home 键**、系统「强制停止」、卸载、清除数据 —— 第三方应用没有这些能力。
- 没有 Device Admin / Device Owner / 锁屏任务 / 防卸载；本仓库明确禁止这类 API。
- Android 12+ 视频 App 可用 `HIDE_OVERLAY_WINDOWS` 隐藏非系统浮层，各家 ROM 与小米电视的实际表现**需要上机实测**。
- 题目是**由知识点纲目参数化生成**的，不是教材同步真题，也不是教辅题库。

---

## 快速开始

```bash
# 1) 装 APK（自己构建产物在 dist/，或到本仓库 Releases 页面下载）
adb install -r dist/PoHW-v5.0.0.apk

# 2) 打开 APP：点「授权悬浮窗」打开系统开关并允许
# 3) 进「家长设置」：首次进入会要求初始化 4-8 位数字密码
# 4) 设好年级 / 频率 / 学科配比，返回首页点「开启守护」
# 5) 按 Home 切到任意视频 App，到点会弹出全屏答题页
```

忘记密码：`adb shell pm clear com.jager.tvquiz`（会清空全部设置与统计）。
遮罩卡住卸载流程：`adb shell am force-stop com.jager.tvquiz`。

---

## 内置题库

题库由 `tools/quizbank/build_bank.py` 从开源仓库
[RedSamerai/learning-content](https://github.com/RedSamerai/learning-content) **全量**生成，
随 APK 放在 `app/assets/quiz.db`，首次启动原子复制到应用 `databases/` 目录后只读。

| 指标 | 数值 |
| --- | ---: |
| 源文件 | 4,026 个 JSON（两份总图谱 + `output/` 全量逐知识点文件） |
| 去重后知识点 | **4,319** 条（`knowledge` 表，原始字段完整保留） |
| 学科 / 年级段 | **19** 个 / **21** 个 |
| 四选一题目 | **5,129** 道（4,319 纲目题 + 810 程序出题） |
| 数据库大小 | 约 4.5 MB（APK 内压缩后约 1 MB） |

> 上游仓库只有知识点纲目，**没有题目、选项和答案**。本项目不引用任何第三方真题文本；
> 所有题目由脚本参数化生成，正确答案由纲目数据或程序计算确定，并通过独立校验脚本
> 复现 App 端的合法性检查、随机抽题压测、答案重算与篡改检测。详见
> [`docs/QUESTION_BANK.md`](docs/QUESTION_BANK.md) 与 [`docs/BANK_STATS.md`](docs/BANK_STATS.md)。

抽题链路（每一级都会兜底，保证「一定能出题」）：

```
内置 SQLite 随机抽题  ->  在线源缓存题目  ->  QuizEngine 参数化生成器
```

### 在线题库源（可选，默认关闭）

火花 K12 这类需要用户自己申请账号 / 配额 / 密钥的在线题源，本项目**不内置、不代管、不打包**。
App 只在「家长设置」里预留了一个 URL 输入框与同步协议：

```
GET <base>/index.json        { schema, version, updatedAt, packs:[{id,grade,edition,subject,url,sha256,count}] }
GET <base>/packs/<file>      { schema, id, grade, edition, subject, questions:[{topic,text,options[4],answerIndex,explain}] }
```

- 地址为空时：App 完全离线、不发起任何请求；首页「检查题库更新」会直接提示当前使用内置题库。
- 填写地址后：按设定周期比对版本、分块下载、SHA-256 校验、原子落盘缓存；失败一律沿用旧缓存。
- 出于安全，默认禁止明文 HTTP（仅本机调试地址在 `network_security_config.xml` 白名单内）。
- APK 里**没有**任何 access-key / token / 付费题数据。

---

## 家长设置

| 项 | 取值 |
| --- | --- |
| 所在年级 | 小学 1-6 / 初中 1-3（内部 1-9） |
| 教材版本 | 通用(课标) / 人教版 / 部编版 / 北师大版 / 苏教版 / 外研版 / 译林版 / 沪教版 |
| 出题频率 | 15 秒 / 30 秒（演示）、1 / 5 / 10 / 20 / 30 分钟 |
| 学科配比 | 数学 / 语文 / 英语各 0-5，0 表示该科不出题 |
| 出题范围 | 仅数/语/英（默认）或该年级**全学科**（含物理、化学、历史、地理等） |
| 自动更新 | 每 1 / 6 / 12 / 24 小时（仅在配置了在线源时生效） |
| 家长密码 | 4-8 位数字，PBKDF2（60,000 次迭代）+ 16 字节随机 salt，同步落盘并回读校验 |

---

## 构建

零 Gradle：直接用 Android SDK 命令行工具 `aapt2 -> javac -> d8 -> zipalign -> apksigner`。
需要 JDK 17、Android SDK platform `android-34`、build-tools `34.0.0`。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
bash tests/run.sh          # 78 个 JVM 单测
python3 tools/quizbank/verify.py app/assets/quiz.db
bash app/build.sh          # -> dist/PoHW-v5.0.0.apk + dist/PoHW-src.zip
```

`app/build.sh` 会做静态守卫（禁止写死版本号、防卸载 API、第三方题包残留）、
校验题库版本戳、跑单测、签名并校验 v1/v2/v3 签名与 `assets/quiz.db`。

签名密钥会在 `.build/` 里**随机生成并 gitignore**；正式分发请用
`POHW_KEYSTORE` / `POHW_KEYSTORE_PASS` 指向自己的 keystore，
避免任何人都能用同一把公开密钥重签 APK。
详细步骤与常见报错见 [`docs/BUILD.md`](docs/BUILD.md)。

### 重新生成题库

```bash
git clone https://github.com/RedSamerai/learning-content /path/to/learning-content
python3 tools/quizbank/build_bank.py \
    --lc /path/to/learning-content \
    --out app/assets/quiz.db \
    --stats docs/BANK_STATS.md \
    --seed 20261104
python3 tools/quizbank/verify.py app/assets/quiz.db
```

`--seed` 固定后结果可复现；脚本会同时写出 `app/assets/quiz.db.version`，
App 用它判断内置库是否需要升级替换。

---

## 权限

| 权限 | 用途 | 必需 |
| --- | --- | --- |
| `SYSTEM_ALERT_WINDOW` | 在其他应用上层弹出全屏答题页 | 是（需手动授权） |
| `FOREGROUND_SERVICE` | 常驻计时服务 | 是（Android 8+） |
| `POST_NOTIFICATIONS` | Android 13+ 显示前台服务通知 | 运行时申请，可拒绝 |
| `RECEIVE_BOOT_COMPLETED` | 开机恢复守护 | 可选 |
| `INTERNET` | **仅**可选在线题库源 | 默认不使用 |

清单里 `allowBackup=false`（避免家长密码哈希随备份导出）、
`usesCleartextTraffic=false` + 网络安全配置（默认禁止明文 HTTP）、
`exported` 最小化，服务与设置页均不对外部应用开放。

更多组件与安全边界见 [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md)。

---

## 项目结构

```
PoHW/
├── README.md · LICENSE · CHANGELOG.md · .gitignore
├── app/
│   ├── AndroidManifest.xml
│   ├── build.sh                 # 零 Gradle 构建链
│   ├── src/com/jager/tvquiz/    # 16 个 Java 源文件
│   ├── res/                     # 图标 / TV banner / strings / network_security_config
│   └── assets/quiz.db(.version) # 内置离线题库
├── tests/
│   ├── run.sh                   # JVM 单测入口
│   └── src/                     # 4+1 组测试 + Android 轻量 stub
├── tools/quizbank/
│   ├── build_bank.py            # 全量纲目 -> SQLite
│   └── verify.py                # 独立校验
└── docs/
    ├── BUILD.md · ARCHITECTURE.md · QUESTION_BANK.md · BANK_STATS.md
```

---

## 数据与许可证

- **代码**：MIT，见 [`LICENSE`](LICENSE)。
- **题库数据**：上游 `learning-content` 仓库**未附 LICENSE**。该文件不包含在 MIT 授权范围内，
  仅用于个人非商业学习；如需再分发，请先确认上游授权并自行重建题库。
- 仓库与 APK 中**不含**任何火花 K12 / 付费接口 / 爬取所得的真题文本，也不含任何密钥。

这是一个自己家里用的玩具项目，不是商用家长控制产品；请在自己设备上、在自己孩子知情的前提下使用。
