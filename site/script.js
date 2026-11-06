/* PoHW — Proof of Homework · 宣传首页脚本
   只做两件事：渲染版本更新记录、当前区块导航高亮。页脚版权年份固定为 2026。
   纯静态、零外部依赖、不发起任何网络请求。 */

(function () {
  "use strict";

  /* ---------------------------------------------------------------
     版本更新记录：与 GitHub Releases 一一对应，目前共 7 个正式版本。
     versionCode 是单调技术计数；同一版本线内的内部构建合并到一张卡片。
     具体构建号与改动一律以仓库 CHANGELOG.md 为准。
     --------------------------------------------------------------- */
  const RELEASES = [
    {
      version: "v1.2.3",
      title: "题库质量重建与年级防超纲",
      code: "versionCode 35",
      badge: "最新",
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
