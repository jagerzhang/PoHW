/* PoHW — Proof of Homework · 宣传首页脚本
   只做三件事：渲染版本更新记录、写入页脚年份、当前区块导航高亮。
   纯静态、零外部依赖、不发起任何网络请求。 */

(function () {
  "use strict";

  /* ---------------------------------------------------------------
     版本更新记录：按时间倒序维护，新增条目只需插到数组头部。
     version / title / code / badge / points / note 均为展示用文案。
     具体构建号与改动一律以仓库 CHANGELOG.md 为准。
     --------------------------------------------------------------- */
  const RELEASES = [
    {
      version: "v1.0.1",
      title: "品牌口号统一：答对题目，无限续租！",
      code: "versionCode 19",
      badge: "最新",
      points: [
        "官网标题、Hero 主标语、电视示意图说明、图标说明与页脚，以及 TV 横幅上的文字，统一改为「答对题目，无限续租！」。",
        "tools/branding/make_icons.py 重新生成 TV 横幅；APP 内置 banner 与官网展示图为同一张图，其余图标像素不变。",
        "新增构建门禁与源码契约，禁止旧「续租 30 分钟」文案回流；负向变异套件补一组回归（38/38）。展示版本仍为 v1.0.1，仅 versionCode 递增。"
      ],
      note: "「无限续租」指没有总次数上限：每次答对续一个家长设定的周期，到期仍会重新出题；不是答对一次就永久免答。具体以仓库 CHANGELOG.md 为准。"
    },
    {
      version: "v1.0.1",
      title: "学习进度：学期 + 单元过滤（方案 B）",
      code: "versionCode 18",
      points: [
        "家长设置新增「学习学期（全学年 / 上册 / 下册）」与「已学单元（不限 / 第 N 单元）」；默认全学年 + 不限，覆盖安装不改变原有抽题范围。",
        "新增 app/assets/syllabus.json：小学 1-6 年级数学上下册共 12 册有序单元表，把题库 topic 里的别名（如「时分秒」与「时、分、秒」）归并到同一单元。",
        "选上 / 下册时按题库 term 过滤，包含「全一册」并排除 810 道无学期的综合练习；单元按 topic 逐个参数化匹配。",
        "空池时依次放宽单元 → 学期 → 年级，前四层始终保留学科约束；语文 / 英语 / 其他学科与 7-9 年级暂无可靠单元顺序，诚实降级为仅学期过滤。",
        "新增零依赖 JSON 解析器、41 项 JVM 单测、真实 SQL 分层回归与 26 条源码契约；布局契约 114 → 140 条。"
      ],
      note: "单元进度需先选「上册 / 下册」；「全学年」没有单一教学顺序，此时单元选择自动禁用。具体以仓库 CHANGELOG.md 与 docs/QUESTION_BANK.md 为准。"
    },
    {
      version: "v1.0.1",
      title: "题库说明归位 + 题库粒度调研",
      code: "versionCode 17",
      points: [
        "家长设置「在线题库」卡片里，「默认完全离线：内置全量 K12 题库，不联网」说明从打赏按钮之后移到「检查题库更新」正下方，并新增源码级顺序契约（布局契约 113 → 114 条）。",
        "做了一次只读调研：内置 SQLite 题库能否按上下学期 / 教材单元限制出题范围；字段与题量实测、两个质量事实、A/B/C/D 四条可选路径写入 docs/QUESTION_BANK.md。",
        "本版没有改动任何抽题逻辑，QuizDb.random() 仍只按 grade + subject 抽题，等方案确认后再实现。"
      ]
    },
    {
      version: "v1.0.1",
      title: "设置页体验 / 启动即守护 / 打赏与图标",
      code: "versionCode 16",
      points: [
        "家长设置所有取值行、按钮、说明统一走 12dp 行间距；卡片标题行改 WRAP_CONTENT + 28dp 最小高度，系统大字模式不再裁切。",
        "文案统一为「启动即守护」；默认值调整：启动即守护 / 暂停视频 / 恢复播放默认开，开机自启默认关，覆盖安装不丢家长旧设置。",
        "新增纯 Java LaunchPolicy：APP 冷启动且四个条件同时满足才自动拉起守护；从后台返回前台不会把家长手动关掉的守护又打开。",
        "新增「打赏作者 · 请喝一杯咖啡」全屏收款码页，遥控器左右键切换微信 / 支付宝，返回键退出；无支付 SDK、无订单、无网络请求。",
        "新增可复现的品牌脚本，生成 5 档密度 APP 图标、640×360 TV banner 与状态栏小图标：深蓝底 + 琥珀计时弧 + 屏幕里的青绿对勾。"
      ]
    },
    {
      version: "v1.0.1",
      title: "四槽配比修订与复审加固",
      code: "versionCode 15",
      points: [
        "修复「出题范围 = 全学科」时配比被旁路的问题：此前会退化成 WHERE grade=? 抽样，5:3:2 完全不决定题目学科，且三科题被贴错学科标签。",
        "新增四槽权重（数 / 语 / 英 / 其他，默认 5:3:2:2，0-5 可调）与两条 SQL 池；关闭全学科或其他学科池不可用时第 4 槽自动失效。",
        "学科标签由题目真实 subject 反推，错题库、统计与题面不再串科；删除硬编码 15% 分支。",
        "独立只读复审加固：OTHER 路由条件、双次参数绑定、去注释同源 SQL、池保持兜底等全部钉死，布局契约 69 → 88 条，变异套件 21/21。"
      ]
    },
    {
      version: "v1.0.1",
      title: "3×2 首页 + 单列设置",
      code: "versionCode 14",
      points: [
        "首页 6 个主按钮从 2 列 × 3 排改为 3 列 × 2 排等宽网格，显式连接上下左右遥控器焦点链，边缘不绕回。",
        "家长设置三个并排 ScrollView 改为居中窄栏单列卡片流（5 张编号卡片），说明文字自然换行、不再挤压裁切。",
        "「修改家长密码 / 保存并返回」移到底部固定操作条；新增纵向焦点链与 40 条源码级布局契约断言，纳入构建门禁。"
      ]
    },
    {
      version: "v1.0.1",
      title: "控制台与设置界面整合",
      code: "versionCode 13",
      points: [
        "「开启守护 / 关闭守护」合并为一个按钮，用绿 / 红表达当前可执行的动作；关闭守护仍需就地 PIN 验证，界面不退出。",
        "首页重复的「检查题库更新」入口删除，统一归到家长设置的在线源卡片，并继承空地址时的内置离线题库提示。",
        "数学 / 语文 / 英语 / 其他学科各一行（学科名 | 教材版本 | 配比），原本分散两栏的 7 行压成 4 行。"
      ]
    },
    {
      version: "v1.0.0",
      title: "首个正式版",
      code: "versionCode 12",
      points: [
        "纠正版本口径：下架 v5.0.0 / v6.0.0 / v6.0.1 三个历史 Release，自 v1.0.0 起按 MAJOR.MINOR.PATCH 递增；versionCode 单调技术计数、不随展示版本重置。",
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

  /* ---------- 页脚年份 ---------- */
  const yearEl = document.getElementById("year");
  if (yearEl) {
    yearEl.textContent = String(new Date().getFullYear());
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
