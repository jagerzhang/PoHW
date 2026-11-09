/* PoHW — Proof of Homework · 宣传首页脚本
   只做两件事：渲染版本更新记录、当前区块导航高亮。页脚版权年份固定为 2026。
   纯静态、零外部依赖、不发起任何网络请求。 */

(function () {
  "use strict";

  /* ---------------------------------------------------------------
     版本更新记录：与 GitHub Releases 一一对应，目前共 11 个正式版本。
     versionCode 是单调技术计数；同一版本线内的内部构建合并到一张卡片。
     具体构建号与改动一律以仓库 CHANGELOG.md 为准。
     --------------------------------------------------------------- */
  const RELEASES = [
    {
      version: "v1.2.7",
      title: "题库逐题复核与原创补题",
      code: "versionCode 43",
      badge: "最新",
      points: [
        "对内置题库 3,964 道题做了全量逐题复核：自动打标后分批人工核读可疑题，剔除 34 道确证损坏题（高中语文赏析、选项截断拼接、多子项字母题、答案错误与重复录入）。",
        "修复 12 道学段倒挂题（拼音音序内容下移到低年级）、4 道英文单词粘连、2 道错别字、270 处册次标签与年级冲突，以及 775 处题干空白与占位符噪声。",
        "新增 59 道按课标学段原创的四选一题，补齐 6 年级语文、2 年级英语等薄格；每题带「判据 → 步骤 → 迁移提示」三段式解析，并对 1 道已有同题升级解析。",
        "题库版本 bank-v4 → bank-v5，最终 3,989 道；1–6 年级、三档难度与 144 组练习参数全部通过门禁。全量语义润色与解析升级仍在进行，57 道待课标复核题本版保留不动。",
        "签名证书连续，旧版可直接覆盖安装；官网下载直链与在线更新清单同步指向本版。"
      ],
      note: "展示版本 v1.2.7 / versionCode 43；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.6",
      title: "家长设置版本更新交互修复",
      code: "versionCode 41",
      points: [
        "修复「立即检查版本」与「下载并安装新版本」点击后像没反应：两个按钮现在都会立即弹出提示，检查结果（含已是最新 / 检查失败）与下载结果都有明确的 Toast 和状态文字。",
        "修复遥控器无法移动到「修改家长密码」「保存并返回」：版本更新卡片不再把按钮设为禁用状态——禁用节点会截断单列纵向焦点链；状态变化后会重建焦点链。",
        "没有新版本时，「下载并安装」会提示「当前已是最新版本；如需重新检查请点立即检查版本」，不再是静默禁用。",
        "签名证书连续，旧版可直接覆盖安装；官网下载按钮已切换为本站 Makers 直链，固定别名始终指向最新版。"
      ],
      note: "展示版本 v1.2.6 / versionCode 41；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.5",
      title: "在线版本更新",
      code: "versionCode 39",
      points: [
        "新增可选的版本在线更新：默认每 24 小时检查一次版本清单，发现新版本只提示，下载与安装需家长主动点击；系统安装界面仍需手动确认。",
        "联网只用于读取版本清单与下载安装包，题库、守护与答题全程离线；不上传任何设备信息，不含统计或广告 SDK。",
        "APK 同时提供带版本号的固定包与 latest 别名；下载后先校验 SHA-256 与包大小，不一致直接丢弃。",
        "官网新增缓存规则：版本清单禁止缓存、安装包长缓存；每次发布只保留最新版，历史版本仍可在 Releases 获取。",
        "Android 4.0 低配版本次不接入在线更新，仍可手动下载；签名证书连续，旧版可直接覆盖安装。"
      ],
      note: "展示版本 v1.2.5 / versionCode 39；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.4",
      title: "出题偏好修复、题库再校准与自由练习显示修复",
      code: "versionCode 37",
      points: [
        "修复「仅知识 / 计算题」出题偏好长期无效：题库里每道题的来源标签都相同，筛选条件等于没筛。现改为逐题标注题型（知识 / 计算题与辨识分类题），选择偏好时只抽真正的知识题，关闭时仍是全量题库。",
        "题库二轮质量校准：52 道低年级超纲题（方位角、最简真分数、比例放大等）上调到 4–6 年级，短英文单词粘连全部修复，题库精简为 3,964 道，1–6 年级与难度三档保持完整。",
        "修复自由练习页题干与选项空白：题目卡片的三处布局参数写成了零高度，题干和 A/B/C/D 按钮被压到 0 像素，只剩学科标签和空白卡片。现在题干、选项、解析都有明确高度；练习年级上限与小学 1–6 年级题库对齐。",
        "新增门禁：静态契约禁止零高度布局；题库校验按 144 组练习参数（6 个年级 × 3 学科 × 4 难度 × 加权开关）实跑抽题 SQL，逐题确认题干与四个选项非空且互异，并用负向变异锁住修复。",
        "签名证书连续，旧版可直接覆盖安装；同步发布 Android 4.0 低配变体 v1.2.4-legacy。"
      ],
      note: "展示版本 v1.2.4 / versionCode 37；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.3",
      title: "题库质量重建与年级防超纲",
      code: "versionCode 35",
      points: [
        "全量重打年级标签：不再信任题面自带标签，按小学课标知识点白名单逐题判定；无可靠依据的题保守标到 4 年级以上，低年级不再出现代数、比例、几何等高年级内容。",
        "抽题链路每一层都加年级上限（只抽不高于当前年级的题）。此前 3 年级及以上语文 / 英语专属题为空，兜底会把 3490 道无年级标签题整包放出，这是超纲的结构性根因。",
        "修复英文单词间空格丢失（如 Sheisagirl、Imgoingto），无法可靠修复的粘连题直接剔除；同时清理重复题、截断题干与中学课文内容。",
        "题库由 4733 道精简为 3965 道，全部带 1–6 年级标签与难度分档；新增题库质量门禁，把年级标签、英文空格、重复题与超纲规则固化进自动审核。",
        "签名证书连续，旧版可直接覆盖安装；同步发布 Android 4.0 低配变体 v1.2.3-legacy。"
      ],
      note: "展示版本 v1.2.3 / versionCode 35；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.2",
      title: "控制台按钮定稿与正式发版",
      code: "versionCode 33",
      points: [
        "控制台第 1 行第 2 个「立即出题」改为「练习模式」：独立全屏自由答题页，可切学科 / 年级 / 难度，答对自动出下一题并显示已练 / 正确 / 连对统计。",
        "第 2 行第 1 个恢复「授权悬浮窗」按钮，点击直接打开系统授权页；「家长设置 → 03 开机与播放」里也保留同样入口。",
        "承接 v1.2.1 的练习模式、暂停时序修复与默认开启的「答对自动继续」；进入练习模式仅挂起守护弹题，服务不停止，避免变成绕过口子。",
        "内置 4733 道离线题库、难度倾向与四项可选加固保持不变；签名证书连续，旧版可直接覆盖安装。",
        "同步发布 Android 4.0 低配变体 v1.2.2-legacy（minSdk 14，不含暂停 / 恢复播放）。"
      ],
      note: "展示版本 v1.2.2 / versionCode 33；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.1",
      title: "练习模式、暂停时序修复与控制台改版",
      code: "versionCode 28–29",
      points: [
        "控制台「授权悬浮窗」改为「练习模式」：独立全屏自由答题页，可切学科 / 年级 / 难度，答对自动出下一题并显示已练 / 正确 / 连对统计。",
        "进入练习模式自动挂起守护弹题，退出后自动恢复；守护服务本身不停止，避免练习页变成绕过口子。",
        "悬浮窗授权入口移到「家长设置 → 开机与播放」，与开机自启、厂商自启动设置放在一起。",
        "修复答题时视频没暂停、点「继续观看」后反而暂停的时序问题：暂停与恢复信号改为互斥且成对。",
        "新增「答对自动继续」开关（默认开启），答对后无需再按一次遥控器，可在家长设置里关闭。",
        "签名证书连续，旧版可直接覆盖安装；同步发布 Android 4.0 低配变体。"
      ],
      note: "展示版本 v1.2.1 / versionCode 29，覆盖 versionCode 28–29。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.2.0",
      title: "全新题库、难度倾向与来源说明中性化",
      code: "versionCode 25–26",
      points: [
        "内置题库整体替换为 4733 道逐题校验后的题目，覆盖幼儿园～六年级语数外；保留原有表结构并新增难度字段。",
        "新增「题目难度」设置（不限 / 低 / 中 / 高），采用 80/20 倾向加权而非硬过滤，默认不限，覆盖安装行为不变。",
        "四项可选加固（连对解锁、单题一次机会、答错冷却、出题偏好）默认全关，家长按需开启；修复出题偏好回退到纲目定位题的问题。",
        "题库来源说明统一为中性表述：部分内容整理自互联网公开渠道，如有侵权可通过 GitHub Issue 联系删除。",
        "签名证书连续，旧版可直接覆盖安装，家长密码、设置与错题库全部保留。"
      ],
      note: "展示版本 v1.2.0 / versionCode 26；tag 与 Release 资产对应该构建。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.0.2",
      title: "发布规范化：版本切分 / U 盘侧载 / 官网收敛 / MR 流程",
      code: "versionCode 18–21",
      points: [
        "当时对外收敛为 3 个 Release：v1.0.0（build 12）、v1.0.1（build 13–17）、v1.0.2（build 18–21）；官网更新记录按版本聚合，不再出现一长串同名的 v1.0.1。",
        "新增「学习进度」：按上册 / 下册与已学到第 N 单元限制出题范围，内置 12 册、105 个有序单元表；空池逐层放宽，且前四层始终保留学科约束。",
        "新增纯静态宣传首页；品牌口号统一为「答对题目，无限续租！」，TV 横幅、APP 图标与官网同口径。",
        "官网撤下额外展示区块与两张静态图片；真机确认可 U 盘直装，文档与 APP 提示改为电视系统「应用管理」里清除数据 / 强制停止，不再依赖电脑连电视。",
        "设置页「自动更新」与「检查题库更新」同一行左右并排，遥控器左右键在行内切换；页脚改为 © 2026 张戈博客 保留所有权利 · Proof of Homework。",
        "APP 内支付宝渠道静态展示图换新，署名统一为 Jager(*戈)；建立 feature 分支 → Pull Request → 自动审核 → 合入 main → 由维护者指定 tag / Release 的发布流程。"
      ],
      note: "展示版本 v1.0.2 / versionCode 21；签名证书连续，旧版可直接覆盖安装。具体构建号与改动以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.0.1",
      title: "界面整合与配比修订",
      code: "versionCode 13–17",
      points: [
        "守护开关合并为一个按钮（绿 / 红表达当前可执行的动作），关闭守护需就地 PIN 验证、界面不退出；首页重复的题库更新入口归位到家长设置。",
        "首页改为 3 列 × 2 排等宽网格，家长设置改为居中单列卡片流：12dp 行间距、底部固定「保存并返回」操作条，遥控器焦点链显式串联。",
        "修复「全学科」时配比被旁路的问题：新增四槽权重（数 / 语 / 英 / 其他，默认 5:3:2:2）与双 SQL 池，学科标签由题目真实 subject 反推，错题库不再串科。",
        "默认值调整：启动即守护 / 暂停视频 / 恢复播放默认开，开机自启默认关；新增纯 Java LaunchPolicy，仅冷启动才自动拉起守护。",
        "新增全屏静态展示页与可复现的品牌脚本（5 档图标 + 640×360 TV banner）；「默认完全离线」说明移到「检查题库更新」下方。",
        "对内置离线题库做了一次只读粒度调研（方案 A/B/C/D），本版本线未改动抽题逻辑。"
      ],
      note: "展示版本 v1.0.1，覆盖 versionCode 13–17；tag 指向本版本线的起点，Release 资产为该版本线最后一次构建。"
    },
    {
      version: "v1.0.0",
      title: "首个正式版",
      code: "versionCode 12",
      points: [
        "纠正版本口径：下架 v5.0.0 / v6.0.0 / v6.0.1 三个开发期 Release，自 v1.0.0 起按 MAJOR.MINOR.PATCH 递增；versionCode 单调技术计数、不随展示版本重置。",
        "修复 P0：遮罩窗口焦点状态机、前台服务 5 秒契约覆盖全部分支；接通 QuizFlow.submit()，「继续观看」不再永远不可点。",
        "新增一次性 STOP 令牌（24 字节、60 秒 TTL、消费即删）、KeyRouter 按键时序、AudioFocusRequest 音频焦点、错题库事务与非破坏式迁移。",
        "明确写出边界：视频暂停与窗口焦点重抢均为 best-effort，内置离线题库无出版社版本字段，不再表述为「已修复」。"
      ]
    }
  ];

  /* ---------- 渲染版本更新记录 ---------- */
  const list = document.getElementById("releases-list");
  if (list) {
    const esc = function (s) {
      return String(s)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
    };
    list.innerHTML = RELEASES.map(function (r) {
      const code = r.code ? '<span class="release-code">' + esc(r.code) + "</span>" : "";
      const badge = r.badge ? '<span class="release-badge">' + esc(r.badge) + "</span>" : "";
      const items = r.points.map(function (p) { return "<li>" + esc(p) + "</li>"; }).join("");
      const note = r.note ? '<p class="release-note">' + esc(r.note) + "</p>" : "";
      return '<article class="release">' +
          '<div class="release-meta">' +
            '<span class="release-version">' + esc(r.version) + "</span>" + code + badge +
          "</div>" +
          '<div class="release-body">' +
            '<h3 class="release-title">' + esc(r.title) + "</h3>" +
            "<ul>" + items + "</ul>" + note +
          "</div>" +
        "</article>";
    }).join("");
  }

  /* ---------- 当前区块导航高亮 ---------- */
  const navLinks = Array.prototype.slice.call(
    document.querySelectorAll(".nav-links a")
  );
  if ("IntersectionObserver" in window && navLinks.length) {
    const sections = navLinks
      .map(function (a) {
        const id = a.getAttribute("href");
        return id && id.charAt(0) === "#" ? document.getElementById(id.slice(1)) : null;
      })
      .filter(Boolean);

    const observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (!entry.isIntersecting) return;
        navLinks.forEach(function (a) {
          const active = a.getAttribute("href") === "#" + entry.target.id;
          a.classList.toggle("active", active);
        });
      });
    }, { rootMargin: "-35% 0px -60% 0px" });

    sections.forEach(function (s) { observer.observe(s); });
  }
})();
