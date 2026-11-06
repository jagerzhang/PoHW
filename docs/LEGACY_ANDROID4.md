# PoHW 低配版 v1.2.2（Android 4.0 / minSdk 14）

本文件对应独立分支 `feat/android4-legacy-v122`，**不合入 `main`**。它基于主线
v1.2.2 的功能集，面向 2013–2014 年前后的老电视（例如小米电视2），主线仍要求
Android 5.0（API 21）及以上。

## 与主线 v1.2.2 的差异

| 项目 | 主线 | 本低配版 |
| --- | --- | --- |
| `minSdkVersion` | 21 | **14** |
| `versionCode` | 33 | **34** |
| `versionName` | 1.2.2 | 1.2.2 |
| APK 文件 | `PoHW-v1.2.2.apk` | `PoHW-v1.2.2-legacy.apk` |
| 主题 | Material（API 21+） | Holo（API 11+） |
| 暂停 / 恢复播放 | 焦点 + 媒体键互斥 | **取消**，只保留短暂音频焦点抢占 |
| 常驻通知 | `NotificationChannel`（API 26+） | `Notification.Builder(Context)` |
| 悬浮窗 | 双分支 | `TYPE_PHONE` |
| 悬浮窗授权入口 | 跳系统设置页 | 提示家长到 MIUI 设置里手动开启 |
| 练习模式、控制台按钮定稿、家长设置、错题库、4733 道离线题库、难度、四项可选加固、答对自动继续 | 有 | **全部保留** |

Android 4.0 没有 `dispatchMediaKeyEvent()`（API 19+）、`AudioAttributes` /
`AudioFocusRequest`（API 21/26+），这些引用已从源码与 dex 中移除。

## 已知限制

1. **没有真机 / 模拟器验证**：构建环境只有 `android-34` platform，无 4.x 镜像。
   只做了静态与构建级验证（badging `sdkVersion:'14'`、dex 扫描无 API 21+ 符号、
   `apksigner verify --min-sdk-version 14`、全套 209 契约 / 308 JVM / 74 变异）。
   装上后能否正常弹出遮罩、练习模式能否使用，需实机试用。
2. **不会暂停视频**：全屏遮罩仍能挡住画面，但后台视频可能继续播放。
3. **MIUI 需手动开悬浮窗权限**：到「设置 → 应用 → 答题守护 → 显示悬浮窗」允许。
4. 可选在线题库源在 4.0 的老 TLS 栈上未验证，建议只用内置离线题库。
5. 若安装仍失败，请记录报错文案并确认「设置 → 关于本机」的 Android 版本。

## 安装

U 盘拷入 APK → 电视自带「文件管理器」安装 → 允许「未知来源」→ 打开 APP。
不需要电脑连接电视，也不需要 adb。

---

题库来源说明见 [DATA_SOURCES.md](DATA_SOURCES.md)。
