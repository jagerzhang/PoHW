# 内置题库设计与数据来源

## 一句话结论

`app/assets/quiz.db` 里的**题目不是从任何题库抄来的**，而是把开源仓库
[RedSamerai/learning-content](https://github.com/RedSamerai/learning-content)
的**全部知识点纲目**导入 SQLite 后，由 `tools/quizbank/build_bank.py`
参数化生成的；正确答案由纲目数据本身或程序计算确定。

上游仓库只有「年级段 / 学科 / 章节 / 知识点」这类纲目字段，
**没有题目、选项、答案**（实测 4,026 个 JSON 中大部分 `description` / `example` /
`explanation` 为空）。因此不存在「把现成真题搬进来」这回事。

## 全量导入口径

| 来源 | 内容 |
| --- | --- |
| `knowledge_graph_complete.json` | 总图谱（4,118 条） |
| `knowledge_graph_backup_4375.json` | 备份总图谱（4,375 条，含九年级全一册） |
| `output/<年级段>/<学科>/*.json` | 逐知识点文件（4,024 个） |

脚本读取以上 **4,026 个文件**，按
`(年级段, 学科, 章节, 知识点)` 去重，逐字段保留信息最全的一份，
合并出 **4,319 条**知识点，覆盖 **19 个学科 / 21 个年级段**
（幼儿园、幼小衔接、小学一至六年级上下、初中七至九年级上下及全一册）。
原始 `id / description / example / explanation / key_points / content / 来源文件`
全部存进 `knowledge` 表，不做裁剪。

## 表结构

| 表 | 用途 |
| --- | --- |
| `knowledge` | 全量纲目，保留上游全部非空字段与来源路径 |
| `chapters` | 兼容旧版 App 的精简纲目视图 |
| `questions` | 四选一题：`grade / grade_label / subject / subject_group / chapter / topic / text / opt_a..opt_d / answer_index / explain / source / qhash` |
| `meta` | 版本戳、题数、知识点数、学科数、年级段数、来源与授权说明 |

`subject_group` 把 19 个学科归到 `math / chinese / english / other`，
使「仅三科」与「全学科」两种出题模式都能用索引查询。

## 题目生成

| 类型 | 数量 | 正确答案来源 |
| --- | ---: | --- |
| 纲目题 | 4,319 | 三种题型轮换，答案全部由纲目字段确定：① 该年级段某学科的知识点辨识；② 知识点所属学科；③ 知识点所属年级段 |
| 程序出题 | 810 | 1-9 年级 × 数/语/英各 30 道：数学为实际计算的加减乘除、周长、质数合数、百分数；语文为古诗/反义词/成语；英语为词汇/句型。答案由 Python 计算或固定词表给出 |

干扰项取自同学科不同章节、不同学科或不同年级段的真实知识点，
保证 4 个选项互异且正确答案唯一；题干超过 24 字会截断，
App 端 `QuizTextSizer` 还会按长度自适应字号。

> **质量说明**：纲目题偏「知识点辨识」，不是教材同步真题，也不是教辅题。
> 它的作用是打断连续观看、让孩子回忆一下学过什么；如果你要真题，
> 请自行准备有授权的数据，按同一 `questions` 表结构导入即可。

## 校验

`tools/quizbank/verify.py` 独立于构建脚本复跑以下检查：

1. 表结构与 `meta` 行数一致；
2. 每题 4 个非空且互异选项、`answer_index ∈ [0,3]`、题干与解析非空；
3. 复现 App 端 `QuizEngine.Question.fromBank()` 的合法性规则；
4. 9 个年级 × 3 科 × 40 次 + 全学科 360 次随机抽题，共 1,440 次，取不到即失败；
5. 纯算式题用 Python 重算答案索引；
6. 知识点覆盖：学科数与年级段数下限；
7. 篡改检测：翻转一个字节后 SHA-256 必须变化。

## 重建与升级

```bash
python3 tools/quizbank/build_bank.py \
    --lc /path/to/learning-content \
    --out app/assets/quiz.db \
    --stats docs/BANK_STATS.md --seed 20261104
python3 tools/quizbank/verify.py app/assets/quiz.db
```

脚本同时写出 `app/assets/quiz.db.version`。App 启动时把该版本戳与本地库中
`meta.version` 比对，不一致才复制新库（写 `.tmp` → `fsync` → 校验可读 →
`renameTo` 原子替换），避免重复复制 4.5 MB 和半截数据库。

## 许可证边界

上游 `learning-content` 仓库**未附 LICENSE**。本仓库的 MIT 许可证**只覆盖代码**，
不包含 `app/assets/quiz.db` 中的纲目数据；该数据仅供个人非商业学习使用。
仓库与 APK 中不含任何火花 K12 / 付费接口 / 爬取所得的真题文本。
如需公开再分发，请先确认上游授权状态或换成自己有权利使用的数据源。
