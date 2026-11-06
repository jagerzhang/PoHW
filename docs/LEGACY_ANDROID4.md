# PoHW 低配版（Android 4.0 / minSdk 14）

本文件对应独立分支 `feat/android4-legacy`，**不合入 `main`**。它是为
2013–2014 年前后的老电视（例如小米电视2）准备的一次性适配构建，
主线 `main` 仍要求 Android 5.0（API 21）及以上。

## 与主线版本的差异

| 项目 | 主线 v1.2.0 | 本低配版 |
| --- | --- | --- |
| `minSdkVersion` | 21（Android 5.0） | **14（Android 4.0）** |
| `versionCode` | 26 | **27** |
| `versionName` | 1.2.0 | 1.2.0（保持，避免发布元数据口径分叉） |
| APK 文件 | `PoHW-v1.2.0.apk` | `PoHW-v1.2.0-legacy.apk` |
| 主题 | `Theme.Material.Light.NoActionBar`（API 21+） | `Theme.Holo.Light.NoActionBar`（API 11+） |
| 暂停 / 恢复播放 | 音频焦点 + 媒体键派发 | **取消**；只保留短暂音频焦点抢占 |
| 常驻通知 | `NotificationChannel`（API 26+） | `Notification.Builder(Context)`（API 11+） |
| 悬浮窗 | `TYPE_APPLICATION_OVERLAY` / `TYPE_PHONE` 双分支 | `TYPE_PHONE` |
| 悬浮窗授权入口 | 跳 `ACTION_MANAGE_OVERLAY_PERMISSION` | 提示家长到系统设置手动开启 |
| 前台服务拉起 | `startForegroundService`（API 26+） | `startService` |
| 运行时权限申请 | `POST_NOTIFICATIONS`（API 33+） | 取消 |
| 计时守护、PIN、设置、错题库、内置题库、难度、四项可选加固 | 有 | **全部保留** |

Android 4.0 没有 `AudioManager.dispatchMediaKeyEvent()`（API 19+），
也没有 `AudioAttributes` / `AudioFocusRequest`（API 21 / 26+）。这些类引用
已从源码与 dex 中移除，否则 Dalvik 在类验证阶段就可能抛 `VerifyError`。

## 已知限制（务必先读）

1. **没有真机 / 模拟器验证**。构建环境只装了 `android-34` platform，
   没有 Android 4.x system image，因此本版本只做了静态与构建级验证。
   装上后能否正常弹出遮罩，需要你在小米电视2 上实际试用。
2. **不会暂停视频**。答题遮罩本身是全屏的，能挡住画面（4.0 也没有
   Android 12+ 的 `HIDE_OVERLAY_WINDOWS` 反制能力），但后台视频可能继续播放、
   声音继续走；答对后也不会主动恢复播放。
3. **MIUI 需要手动开悬浮窗权限**：4.x 没有统一的授权跳转入口，
   请到「设置 → 应用 / 应用管理 → 答题守护 → 显示悬浮窗」允许。
   不授权则弹不出遮罩。
4. 可选在线题库源在 4.0 的老 TLS 协议栈上**未验证**，建议只使用内置离线题库。
5. `<queries>`、`networkSecurityConfig`、`usesCleartextTraffic`、`banner`
   等高版本 manifest 元素已移除，避免旧版 PackageParser 解析异常。
6. 若安装仍失败，请先记录具体报错文案，并确认「设置 → 关于本机」里的
   Android 版本；4.0.x / 4.2 / 4.4 的可用 API 不同。

## 验证方式（本分支实际执行）

- `tests/layout_contract.py`、`tests/run.sh`、`build_syllabus.py --check`、
  `study_scope_check.py`、`bank_distribution_check.py`、
  `tools/quizbank/verify.py`、`tests/mutation_suite.py` 全套通过。
- `bash app/build.sh` 用同一发布密钥出包，证书 SHA-256
  `76eb6063b574…26172` 与主线连续。
- `aapt dump badging` 显示 `sdkVersion:'14'` / `versionCode='27'`。
- `dexdump classes.dex` 扫描确认无 `NotificationChannel` / `AudioFocusRequest` /
  `AudioAttributes` / `dispatchMediaKeyEvent` / `startForegroundService` /
  `canDrawOverlays` 等 API 21+ 符号。
- `apksigner verify --min-sdk-version 14` 校验 v1 签名在 4.0 上可用。

## 安装

与主线一致：U 盘拷入 APK → 电视自带「文件管理器」安装 → 首次允许「未知来源」
→ 打开 APP → 进「家长设置」设 PIN 与出题参数 → 回首页开启守护。
不需要电脑连接电视，也不需要 adb。

## 回到主线

`git checkout main` 即可；本分支的改动不会出现在主线的 Release 资产里。

---

题库来源说明见 [DATA_SOURCES.md](DATA_SOURCES.md)。
