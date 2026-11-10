# 真机 UI 自检

三层验证，从不需要设备到需要设备：

| 层 | 手段 | 位置 | 能发现什么 |
| --- | --- | --- | --- |
| L1 静态契约 | 源码符号断言 | `tests/layout_contract.py` | 固定高度、缺 `setMaxLines`、字号写死、缺 `includeFontPadding(false)` |
| L2 纯 JVM 布局估算 | 无 Android 依赖的字数/高度计算 | `tests/src/QuizTextSizerTest.java` | 长选项在给定网格高度里是否放得下 |
| L3 真机 | `adb uiautomator dump` + `screencap` | 本目录脚本 | 实际渲染的文字裁切、错行、焦点态 |

## 跑 L3

电视与本机同一 WiFi，开启「开发者选项 → 网络 ADB 调试」，本机装好
Android platform-tools（`adb` 在 PATH 中），然后：

```bash
TV_IP=192.168.1.50 APK=dist/PoHW-v1.3.1.apk \
  bash tools/ui_check/overlay_check.sh
```

产物在 `.build/ui_check/`：`window.xml`（UI 层级与 bounds）、`screen.png`（截图）、
`report.txt`（长文本可疑节点清单）。`--density` 可按电视实际密度调整
（1080p 电视通常是 2.0）。

## 局限

`uiautomator dump` 不输出字号，所以 `analyze_dump.py` 用 16sp 基准做启发式筛查，
**SUSPECT 不等于一定裁切**；把 `screen.png` 一起回传，用截图目测复核最准。
这一步目前需要人在本机执行，远程构建沙箱没有 adb、也没有 Android 模拟器镜像。
