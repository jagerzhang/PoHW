# 构建指南

## 1. 环境

| 依赖 | 版本 / 用途 |
| --- | --- |
| JDK | 17（`javac` / `keytool` / `java`） |
| Android SDK | `platforms/android-34`（`android.jar`）、`build-tools/34.0.0`（`aapt2` / `d8` / `zipalign` / `apksigner` / `aapt`） |
| Python | 3.8+，仅题库脚本，零第三方依赖 |
| 其他 | `bash` / `zip` / `sha256sum` |

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
command -v aapt2 javac d8 zipalign apksigner   # 应全部能找到
```

不需要 Android Studio、Gradle、Kotlin 或任何 Maven 依赖。

## 2. 完整构建

```bash
bash tests/run.sh                                   # 174 个 JVM 单测（7 组）
python3 tools/quizbank/verify.py app/assets/quiz.db  # 题库独立校验
bash app/build.sh                                    # 产出 dist/PoHW-v1.0.0.apk
```

`app/build.sh` 的九个阶段：

| 阶段 | 动作 |
| --- | --- |
| 0 | 静态守卫：版本一致性、禁止写死 `v4.1 控制台`、禁止 DeviceAdmin / 防卸载 API、禁止第三方题包残留、`allowBackup=false` |
| 1 | 题库校验：表结构、每题选项合法性、9 年级 × 3 科 × 40 次 + 全学科 360 次随机抽题、答案重算、篡改检测 |
| 2 | JVM 单测：PinGate / QuizLayout / StopGuard / StopState / GradeScope |
| 3 | `aapt2 compile --dir res` |
| 4 | `aapt2 link`（生成 `R.java`，打包 `assets/`） |
| 5 | `javac -source 8 -target 8 -classpath android.jar` |
| 6 | `d8 --min-api 21` |
| 7 | `zip` 塞入 `classes.dex` + `zipalign -f 4` |
| 8 | `apksigner sign`（debug keystore 自动生成在 `.build/`，已 gitignore）+ `apksigner verify` |
| 9 | 校验 `versionName`、`assets/quiz.db(.version)`，打包 `dist/PoHW-src.zip` |

> **顺序注意**：必须先 zipalign 再签名，APK 内 `assets/*.db` 为已压缩资源，
> 不要再做二次对齐或重签名之外的修改。

## 3. 版本管理

`app/AndroidManifest.xml` 的 `android:versionCode` / `android:versionName`
必须与 `app/build.sh` 顶部的 `VERSION_CODE` / `VERSION_NAME` 一致（脚本会 grep 校验）。
首页版本文字由 `MainActivity.readVersion()` 通过 `PackageManager.getPackageInfo()`
动态读取，**禁止写死版本号**。

## 4. 重新生成题库

```bash
git clone https://github.com/RedSamerai/learning-content /path/to/learning-content
python3 tools/quizbank/build_bank.py \
    --lc /path/to/learning-content \
    --out app/assets/quiz.db \
    --stats docs/BANK_STATS.md \
    --seed 20261104
python3 tools/quizbank/verify.py app/assets/quiz.db
```

上游原始仓库（约 20 MB）不要提交进本仓库，`.gitignore` 已排除 `/data/` 与 `/third_party/`。
`--seed` 固定时生成结果完全可复现。

## 5. 常见问题

| 现象 | 原因 / 处理 |
| --- | --- |
| `MISSING TOOL: apksigner` | `JAVA_HOME` / `ANDROID_HOME` 没设，或 build-tools 版本不是 34.0.0 |
| `MISSING android.jar` | 缺 `platforms/android-34`，用 sdkmanager 安装 |
| `apksigner` 报找不到 java | `apksigner` 依赖 `JAVA_HOME/bin/java` |
| `aapt2 link` 报资源错误 | `res/values/strings.xml`、`res/xml/network_security_config.xml` 是否齐全 |
| 覆盖安装后仍要求输入旧密码 | SharedPreferences 被保留；`adb shell pm clear com.jager.tvquiz` 重置 |
| 遮罩卡住卸载确认框 | `adb shell am force-stop com.jager.tvquiz`，或先关守护 |
| 题库升级不生效 | 确认 `app/assets/quiz.db.version` 与库内 `meta.version` 一致 |

## 6. 为什么不用 Gradle

这个工程只有一个模块、27 个 Java 文件、零第三方依赖，用 Gradle 需要 AGP、
Gradle Wrapper、联网拉依赖和一套额外的构建缓存。命令行链路把每一步都显式暴露出来，
便于审查、便于在离线环境复现，也让「APK 里到底装了什么」一目了然。

## 7. v1.0.0 构建门禁

`app/build.sh` 的 `[0/9] static guards` 改为**语义符号检查**，不再依赖精确行文本
（旧版把 `FLAG_NOT_FOCUSABLE` 清零当成「窗口焦点已保证」是错误理解）。当前检查：
`QuizFlow` / `flow.submit`（判题状态机确实被推进）、`KeyRouter.decide`（按键自主路由）、
`new OverlayQuizView`（每次弹题不复用 View）、`requestContinue` + `onWindowFocusChanged`
（不在按键栈内摘窗口、失焦有回调）、`fgReady = enterForeground()`（FGS 契约在入口覆盖全分支）、
`updateViewLayout` / `rebuildOverlay`（焦点重抢状态机）、`issueStopToken` /
`consumeStopToken` / `EXTRA_STOP_TOKEN`（一次性 STOP 令牌），并**禁止**残留
`EXTRA_STOP_VERIFIED`。另外确认 `QuickBootReceiver` 已注册、
`KeyRouter` / `QuizFlow` / `BehaviorV601Test` 真的被编译与执行。

`[2/9]` 现在跑 7 组共 **174** 个 JVM 单测（新增 `BehaviorV601Test` 51 项，
覆盖按键 ACTION_DOWN/UP 与 repeat、2x2 高亮邻接、答题状态机、令牌 TTL/重放/进程重启）；
`[6/9]` 仍检查 dex 中含「错题库」字符串。

覆盖安装保持签名一致：

```bash
JAVA_HOME=/opt/jdk17 ANDROID_HOME=/path/to/sdk POHW_KEYSTORE=/path/to/debug.keystore POHW_KEYSTORE_PASS=android bash app/build.sh
```

不指定 `POHW_KEYSTORE` 时脚本会在 `.build/` 随机生成密钥，
自行构建的 APK 与官方 Release 签名不同，覆盖安装前需先卸载。
