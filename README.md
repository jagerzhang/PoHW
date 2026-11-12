<div align="center">

<img src="app/res/drawable-xhdpi/ic_launcher.png" width="92" alt="PoHW 图标">

# PoHW · Proof of Homework

**给 Android 电视加一道门槛：到点弹出一道题，答对才能继续看。**

不上架应用商店 · 家长自行侧载 · 默认离线 · 不收集任何数据

[![Release](https://img.shields.io/github/v/release/jagerzhang/PoHW?style=for-the-badge&logo=github&label=Release)](https://github.com/jagerzhang/PoHW/releases/latest)
[![官网](https://img.shields.io/badge/官网-在线体验-3DDC84?style=for-the-badge&logo=googlechrome)](https://pohw.zhangge.net/)
[![Android TV](https://img.shields.io/badge/Android%20TV-5.0%2B-3DDC84?style=for-the-badge&logo=android)](https://pohw.zhangge.net/#download)
[![内置题库](https://img.shields.io/badge/内置题库-9%2C544%20题-2F80ED?style=for-the-badge&logo=book)](docs/BANK_STATS.md)

[![默认离线](https://img.shields.io/badge/默认-零网络请求-6C4F9E?style=for-the-badge&logo=shield)](docs/ARCHITECTURE.md)
[![纯原生](https://img.shields.io/badge/纯%20Java-零第三方%20SDK-FF9800?style=for-the-badge&logo=openjdk)](docs/ARCHITECTURE.md)
[![CI](https://img.shields.io/github/actions/workflow/status/jagerzhang/PoHW/ci.yml?style=for-the-badge&logo=githubactions&label=CI)](https://github.com/jagerzhang/PoHW/actions/workflows/ci.yml)
[![License](https://img.shields.io/badge/license-MIT-green?style=for-the-badge&logo=open-source-initiative)](LICENSE)

</div>

---

## 它能做什么

- **跨应用答题**：到点用全屏遮罩盖住任意界面，四选一答对才放行；连错 2 次自动换题并记入错题库。
- **9,544 道内置题**：随 APK 发布、零网络请求，覆盖小学 1–6 年级语数外，按教材册次、单元与课标知识点组织。
- **按组闯关**：默认一组 5 题、答对 60% 通过，题量与门槛可配；答题时尽力暂停 / 恢复播放。
- **跟着进度走**：年级、上下学期、已学单元、教材学制（自动 / 六三制 / 五四制）都可设，不抽超纲题。
- **生效时段与节假日**：内置 2026–2027 法定节假日与调休补班，另有假期模式与「今天临时」，上学时段不打扰。
- **家长密码**：4–8 位数字密码，关闭守护需就地验证；悬浮窗授权被撤销时主动提示，可选无障碍备用通道。
- **两个后台**：电视遥控器设置 + 可选手机扫码管理（默认不监听、数据不出局域网），两端设置同步。
- **练习模式**：独立自由答题页，切学科 / 年级 / 难度并显示统计，退出自动恢复守护。

[功能细节](docs/ARCHITECTURE.md) · [题库统计](docs/BANK_STATS.md) · [官网](https://pohw.zhangge.net/)

## 安装（U 盘，不需要电脑连电视）

1. 到 [官网下载最新版](https://pohw.zhangge.net/#download)（历史版本见 [Releases](https://github.com/jagerzhang/PoHW/releases/latest)）。
2. APK 拷进 U 盘 → 插到电视 → 用电视自带「文件管理器」打开安装，按提示允许「未知来源」。
3. 打开 APP：授权悬浮窗 → 设置 4–8 位家长密码 → 设年级 / 频率 / 学科配比 → 首页点「开启守护」。

已有旧版**直接覆盖安装**即可（同一签名证书，密码、设置与错题库都保留）；换签名才需要卸载重装。

## 边界

- **开机 / 重启后守护没起来？** 先看首页状态栏：部分电视（海信 VIDAA、TCL 等）重启后会撤销悬浮窗授权，重新授权即可；「开机自启」默认关闭，还需在厂商设置里放行。「自动守护」只管打开 APP 时拉起，不等于开机自启。详见 [开机自启引导](docs/ARCHITECTURE.md) 与 [Issue #20](https://github.com/jagerzhang/PoHW/issues/20)。
- 不是设备锁 / 应用锁，不阻止卸载，也不阻止换设备看视频；暂停视频是尽力而为的系统级请求。
- 默认零请求：版本检查、在线题库源、手机管理三项都可关，全关后不产生对外请求；不含统计 / 广告 / 支付 SDK。
- 难度是倾向加权不是硬过滤；站点难度系数噪声大，选「高」仍可能遇到偏易题。完整边界见 [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)。

## 可选能力

| 能力 | 怎么开 | 默认 |
| --- | --- | --- |
| 在线题库（自建题目） | [格式模板 + 部署说明](docs/online-bank/README.md)，填目录 URL | 关闭，完全离线 |
| 手机扫码管理 | 家长设置 → 手机扫码管理 → 扫码打开 Web 后台 | 关闭，不监听 |
| 生效时段与节假日 | 家长设置 → 07 生效时段与节假日 | 关闭，全天可弹题 |
| Android 4.0 低配包 | [官网下载页](https://pohw.zhangge.net/#download) 选 legacy 通道 | 与主线同题库 |

> 在线题库是**可选的**：想补充自己的题目，直接抄 `docs/online-bank/` 里的 `index.json` 模板，
> 改完题目跑一次 `build_index.py` 重新生成 sha256，整目录托管到 HTTPS 静态服务即可。

## 文档

| 我想…… | 看这里 |
| --- | --- |
| 自己出包 / 复现构建 | [docs/BUILD.md](docs/BUILD.md) |
| 补自己的在线题库 | [docs/online-bank/README.md](docs/online-bank/README.md) |
| 看架构与边界 | [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| 看题库怎么来的 | [题库统计](docs/BANK_STATS.md) · [设计与校验](docs/QUESTION_BANK.md) · [数据来源](docs/DATA_SOURCES.md) |
| 看版本历史 | [CHANGELOG.md](CHANGELOG.md) · [官网版本记录](https://pohw.zhangge.net/changelog.html) · [Releases](https://github.com/jagerzhang/PoHW/releases) |
| 参与或反馈 | [Issue](https://github.com/jagerzhang/PoHW/issues) · 提交 PR 前请先看 CI 自动审核 |

## 发布流程

零 Gradle：JDK 17 + Android SDK 34，命令行 `aapt2 → javac → d8 → zipalign → apksigner`。

```bash
JAVA_HOME=/opt/jdk17 ANDROID_HOME=/path/to/sdk \
POHW_KEYSTORE=/path/to/release.keystore POHW_KEYSTORE_PASS=*** \
bash app/build.sh

bash tests/run.sh                  # JVM 单测
python3 tests/mutation_suite.py    # 发布前负向变异回归
```

所有改动走 feature 分支 → **Pull Request** → `.github/workflows/ci.yml` 的
`auto-review` 检查 → 合入 `main`；tag / Release 由维护者用同一发布密钥创建。
`build.sh` 会比对成品证书与已发布证书，不一致直接失败，保证旧设备能覆盖安装。
逐次变更见 [CHANGELOG.md](CHANGELOG.md)。

## 许可证与题库来源

代码沿用 [MIT](LICENSE)。题库内容权利归各自权利人所有，**不随 MIT 一并授权**；
如涉及版权或其他权益问题，请通过 GitHub Issue 联系，核实后会及时删除或替换。
完整说明见 [docs/DATA_SOURCES.md](docs/DATA_SOURCES.md)。
