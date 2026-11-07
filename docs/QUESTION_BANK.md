# 内置题库设计与校验口径

## 一句话结论

`app/assets/quiz.db` 当前为 **`pohw-2026.11.07-bank-v4`**，共 **3964 道题**，
覆盖小学 1–6 年级语数外，每题带明确的 `grade`、`difficulty` 与 `difficulty_level`。
题目由本项目按课标自编的低年级题，与整理自互联网公开渠道、经逐题校验清洗后的题目组成。

> 来源说明见 [DATA_SOURCES.md](DATA_SOURCES.md)；重建脚本见
> [`tools/quizbank/rebuild_bank.py`](../tools/quizbank/rebuild_bank.py)，门禁见
> [`tests/bank_quality_check.py`](../tests/bank_quality_check.py)。

## 表结构

| 表 | 用途 |
| --- | --- |
| `questions` | 题目正文、四个选项、答案下标、解析、年级、学科、难度、qhash |
| `knowledge` | 按 (年级, 学科, 主题) 聚合的知识点索引 |
| `chapters` | 按 (年级, 学科, 章节) 聚合的册次索引 |
| `meta` | 版本戳、题数、知识点数、年级数、来源与版权说明 |

Schema 仍为旧版兼容的 17 列 + `difficulty` / `difficulty_level` 两列，
覆盖安装时以 `app/assets/quiz.db.version` 与本地 `meta.version` 比对，
不一致才原子替换；家长 PIN、设置与错题库不受影响。

## 年级标签怎么来的

早期题库里的年级标签来自题面自带信息，**不可信**：曾把代数等式
`a×5=a×10` 标成三年级上册，另有 3490 道题完全没有年级标签（`grade=-1`）。
设置里却能选 1–9 年级，而 3 年级以上语文 / 英语专属题为 0，
抽题兜底会放开年级约束，把无标签题整包放出——这是超纲的结构性根因。

现在由 `rebuild_bank.py` 逐题重新定级：

1. 程序自编题（`SG` 前缀）保留原年级，幼儿园统一归到 1 年级。
2. 其余题按小学课标知识点白名单匹配（数学六档、语文四档、英语四档），
   **只有明确且单纯命中 1–3 年级课标的题**才允许进入低年级池；
   命中任何高年级特征的题按最高命中年级定级，无关键词可依的题
   一律保守标到 4 年级及以上。
3. 明显超出小学范围的内容（函数、方程组的解法、中学课文、
   高中政治与文学常识、相似三角形等）直接剔除。

## 文本清洗

| 规则 | 处理 |
| --- | --- |
| 英文单词间空格丢失 | 词表切分还原（`Sheisagirl` → `She is a girl`），专有名词首字母还原；误切启发式避免 `Hebei` 被拆成 `he be i` |
| 修复后选项重复 | 大小写归一后四个选项可能变得相同，答案不再唯一，直接剔除 |
| 无法解释的粘连 | 既切不开又不像合法英文词的残留，整题剔除 |
| 题干截断 / 依赖缺失材料 | 剔除 |
| 重复题 | 按 (学科, 归一化题干) 去重，保留首次 |
| 解析前缀 | 清理「参考答案：」 |

## 题型标签与「仅知识 / 计算题」偏好

`questions` 表带 `qtype` 列，由 `rebuild_bank.py` 逐题判定：

| 值 | 题数 | 说明 |
| --- | ---: | --- |
| `knowledge` | 3950 | 计算、应用、古诗词语、英语对话与语法等真知识 / 能力题 |
| `recognition` | 14 | 「下面哪个是长方形？」「属于哪一类」这类辨识 / 分类模板题 |

`QuizDb.KNOWLEDGE_ONLY_SQL` 为 ` AND qtype='knowledge'`：家长开启出题偏好时
只抽知识 / 计算题，关闭时仍可用全库。**不物理删除任何题目**，只是加筛选条件；
`bank_quality_check.py` 会校验该列、两种取值与 meta 计数。

## 抽题与年级上限

`QuizDb` 的 `POOL_CORE_GRADE` / `POOL_CORE_ALL` / `POOL_OTHER_GRADE` /
`POOL_OTHER_ALL` 以及最后一层保底查询**全部**带 `grade<=?`，
任何兜底层都不会为了凑题而放宽到更高年级。`countFor` / `countForGrade`
用同样的上限，保证首页显示的题数与实际可抽池一致。

## 可复现与验证

```bash
python3 tools/quizbank/rebuild_bank.py \
  --src <旧库> --out app/assets/quiz.db --version pohw-2026.11.07-bank-v3 \
  --write-version-file app/assets/quiz.db.version
python3 tests/bank_quality_check.py     # 年级/空格/重复/超纲门禁
python3 tools/quizbank/verify.py app/assets/quiz.db
```

`bank_quality_check.py` 已挂进 `app/build.sh` 与 `auto-review`，
任何题库改动都会先过这一关。

## 历史说明

本文件早期描述的是已退役的 5129 题纲目定位库（`knowledge_graph_*.json`
全量导入 + 参数化生成）。那套数据在 v1.2.0 被整体替换，
当前随 APK 发布的题库与本文件现在描述的一致。
