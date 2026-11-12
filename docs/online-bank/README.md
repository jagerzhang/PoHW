# 自建在线题库 · 格式模板与部署说明

PoHW 默认只用 APK 内置的 9,544 道离线题，**在线题库是完全可选的**。
如果你想给孩子补自己的题（学校错题、校本教材、寒暑假专项），只要把这份目录
整包放到一个静态文件服务上，再在「家长设置 → 05 在线题库源」里填目录地址即可。

App 端的真实解析契约在
[`BankRepository.java`](../../app/src/com/jager/tvquiz/BankRepository.java) 与
[`QuizEngine.Question.fromBank()`](../../app/src/com/jager/tvquiz/QuizEngine.java)；
本文和模板与它逐字段对齐，不需要读 Java 代码也能配置。

## 目录里有什么

```
docs/online-bank/
├── README.md               ← 本文
├── index.json              ← 清单：版本 + 题目包列表 + 每个包的 sha256
└── pack_grade3_math.json   ← 题目包示例：3 道三年级数学题
```

`index.json` 是模板，`pack_*.json` 是可直接改的示例。**一个题目包 = 一个年级 + 一个学科 + 一个教材版本**。

## 三步用起来

1. **改题目**：复制 `pack_grade3_math.json`，按下面的字段写自己的题；想分多个包就多建几个 `pack_*.json`。
2. **重新生成清单**（sha256 不用手算）：
   ```bash
   python3 tools/onlinebank/build_index.py --version 2026-11-12
   ```
   每次改完题目都要重跑；否则 App 会因校验不通过而拒绝这个包。
3. **整目录托管**：把 `index.json` 和所有 `pack_*.json` 放到同一个静态目录下
   （GitHub Pages / 对象存储 / 自家 NAS / Nginx 都行），然后在 App 里填**目录 URL**（不带 `index.json`）：
   ```
   https://example.com/my-bank/
   ```
   点「检查题库更新」，状态栏出现「在线题库 · 更新到 2026-11-12，成功 1 个包」就成了。

> ⚠️ **正式部署必须用 HTTPS**。App 的 `network_security_config.xml` 默认禁止明文 HTTP，
> 只对 `localhost` / `127.0.0.1` / `10.0.2.2` 三个调试主机放行。
> 本机自测可以在本目录执行 `python3 -m http.server 8000`，然后点设置页的
> 「填入示例地址」切到 `http://127.0.0.1:8000/`。

## index.json 字段

```json
{
  "schema": 1,
  "version": "2026-11-12",
  "updatedAt": "2026-11-12",
  "packs": [
    {
      "id": "grade3_math",
      "grade": 3,
      "edition": "通用(课标)",
      "subject": "数学",
      "url": "pack_grade3_math.json",
      "sha256": "…64 位十六进制…",
      "count": 3
    }
  ]
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `schema` | ✅ | 必须是 `1`，否则同步直接失败 |
| `version` | ✅ | 任意非空字符串。**只有它变了 App 才会重新下载**（手动点「检查题库更新」可强制刷新） |
| `updatedAt` | 否 | 展示在状态栏，建议写日期，缺省显示 `-` |
| `packs` | ✅ | 数组，每项对应一个题目包 |

`packs[]` 每项：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `id` | ✅ | 唯一标识，只建议用字母 / 数字 / `-` / `_`；App 会缓存成 `pack_<id>.json` |
| `url` | ✅ | 题目包路径，相对目录即可；绝对 URL 必须与源地址**同主机**，跨主机会被拒绝 |
| `sha256` | ✅ | 该 pack 文件**原始字节**的 SHA-256，64 位 hex（大小写都行） |
| `grade` / `edition` / `subject` | 建议 | 供人工核对；App 实际以 pack 文件内部的值为准 |
| `count` | 建议 | 题量，仅人工核对用，App 不读取 |

## pack_*.json 字段

```json
{
  "schema": 1,
  "id": "grade3_math",
  "grade": 3,
  "edition": "通用(课标)",
  "subject": "数学",
  "questions": [
    {
      "topic": "两位数进位加法",
      "text": "小明有 36 张卡片，又买来 27 张，一共有多少张？",
      "options": ["53 张", "63 张", "62 张", "73 张"],
      "answerIndex": 1,
      "explain": "36 + 27：个位 6 + 7 = 13，向十位进 1，共 63 张。"
    }
  ]
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `schema` / `id` | ✅ | 建议与 `index.json` 里的 `id` 一致 |
| `grade` | ✅ | 整数。**只加载与家长设置的年级相等的包**，别的年级会被跳过 |
| `edition` | ✅ | `*` 或 `通用(课标)` = 通用包；写具体教材名时要和 App 里该学科的版本匹配 |
| `subject` | ✅ | `数学` / `语文` / `英语`；其他值归到「其他」学科槽 |
| `questions` | ✅ | 题目数组，非空 |

`questions[]` 每题：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `text` | ✅ | 题干，去空白后至少 3 个字符 |
| `options` | ✅ | **恰好 4 个**字符串，非空且互不重复 |
| `answerIndex` | ✅ | 正确选项下标 `0`–`3`（对应 `options[0]`–`options[3]`） |
| `topic` | 否 | 知识点标签，缺省显示「同步题库」 |
| `explain` | 否 | 解析，答完后展示；缺省为空 |

**注意是 `answerIndex`（驼峰），不是 `answer_index`。** 仓库里
`tools/quizbank/review/*.json` 是内置题库的补题源文件，用的是 snake_case，
两者不通用，别直接拷过来当在线包。

## App 会静默丢弃哪些题

`Question.fromBank()` 对下面这些情况**不报错、直接跳过**，所以题目写错了只会发现「题变少了」：

- 题干少于 3 个字符；
- `options` 不是 4 项、有空串、有重复项；
- `answerIndex` 缺失、不是整数或不在 `0..3`；
- 字段类型不对（比如把 `answerIndex` 写成字符串 `"1"`）。

`build_index.py` 会提前把这些都查出来并报错，改完题务必跑一遍：

```bash
python3 tools/onlinebank/build_index.py --check
# ONLINE_BANK_CHECK_OK packs=1 questions=3 version=2026-11-12
```

## 其他必须知道的边界

| 项 | 限制 |
| --- | --- |
| 单文件大小 | `index.json` 与每个 pack 都不得超过 **4 MB** |
| 协议 | 只支持 `http` / `https`；正式部署用 HTTPS（见上文） |
| 重定向 | 最多 3 跳，且**每一跳都必须留在同一主机**，跨主机 CDN 会被拒绝 |
| 更新触发 | 改 `version` + 等自动同步（周期在「自动更新」行设置，1 / 6 / 12 / 24 小时可选，默认 6 小时），或手动点「检查题库更新」 |
| 失败降级 | 某个包下载 / 校验失败不影响其他包，失败包沿用旧缓存；全部失败则整次同步失败并保留旧缓存 |
| 出题回退 | 在线题只是「优先抽」，抽不到会自动回退内置 SQLite 题库与参数化算式，永远能出题 |
| 隐私 | 目录里不要放任何密钥；App 只发 GET，不上传设备信息；地址为空时一次请求都不发 |

## 校验与复现

```bash
# 生成（修改题目后）
python3 tools/onlinebank/build_index.py --version 2026-11-12

# 只校验不写入（CI / 发布前）
python3 tools/onlinebank/build_index.py --check

# 用别的目录
python3 tools/onlinebank/build_index.py --dir /path/to/my-bank --version v2
```

脚本与 App 端使用同一套规则：`schema == 1`、`sha256` 为原始字节、
4 个互异非空选项、`answerIndex ∈ [0,3]`、题干 ≥3 字、4 MB 上限、id 字符集。
`--check` 还会确认 `index.json` 里的 `packs` 与磁盘上的 pack 文件逐字段一致。
