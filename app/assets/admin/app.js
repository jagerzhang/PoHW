(function () {
  "use strict";
  var token = "";
  var last = null;

  function $(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function api(method, path, body, cb) {
    var x = new XMLHttpRequest();
    x.open(method, path, true);
    x.setRequestHeader("Content-Type", "application/json");
    if (token) x.setRequestHeader("Authorization", "Bearer " + token);
    x.onreadystatechange = function () {
      if (x.readyState !== 4) return;
      var d = {};
      try { d = JSON.parse(x.responseText); } catch (e) { }
      cb(x.status, d);
    };
    x.send(body ? JSON.stringify(body) : null);
  }
  function show(id, msg) { $(id).textContent = msg || ""; }

  // 与 AdminApi.NUM_KEYS / BOOL_KEYS 一一对应；敏感与破坏性项有意不列入。
  function fields(d) {
    var grades = [];
    for (var g = 1; g <= (d.maxGrade || 9); g++) {
      grades.push([g, g <= 6 ? ("小学 " + g + " 年级") : ("初中 " + (g - 6) + " 年级")]);
    }
    var intervals = (d.intervals || [15, 30, 60, 300, 600, 1200, 1800]).map(function (v, i) {
      return [v, (d.intervalLabels && d.intervalLabels[i]) || (v + " 秒")];
    });
    var editions = (d.editionLabels || ["自动（题量最多）", "六三制", "五四制"])
      .map(function (v, i) { return [i, v]; });
    var syncs = (d.syncLabels || ["每 1 小时", "每 6 小时", "每 12 小时", "每天"])
      .map(function (v, i) { return [i, v]; });
    var maxW = d.maxWeight || 5;
    var out = [
      { group: "01 出题基础", key: "grade", label: "所在年级", type: "sel", opts: grades },
      { group: "01 出题基础", key: "interval", label: "出题频率", type: "sel", opts: intervals },
      { group: "01 出题基础", key: "termIndex", label: "学习学期", type: "sel",
        opts: [[0, "全学年"], [1, "上册"], [2, "下册"]] },
      { group: "01 出题基础", key: "editionMode", label: "教材学制", type: "sel", opts: editions },
      { group: "01 出题基础", key: "unitLimit", label: "已学单元（0 = 不限）", type: "num", min: 0, max: 24 },
      { group: "01 出题基础", key: "questionsPerRound", label: "每组题量", type: "num", min: 1, max: 10 },
      { group: "01 出题基础", key: "passPercent", label: "通过正确率 %", type: "num", min: 1, max: 100 },
      { group: "01 出题基础", key: "roundMode", label: "答题模式", type: "sel",
        opts: [[0, "单题"], [1, "连续答对"], [2, "按组"]] },
      { group: "01 出题基础", key: "unlockStreak", label: "连续答对题数", type: "num", min: 1, max: 5 },
      { group: "01 出题基础", key: "questionSource", label: "出题偏好", type: "sel",
        opts: [[0, "全部题目"], [1, "仅知识/计算题"]] },
      { group: "01 出题基础", key: "difficultyLevel", label: "题目难度", type: "sel",
        opts: [[0, "不限"], [1, "低 · 偏易"], [2, "中"], [3, "高 · 偏难"]] },
      { group: "01 出题基础", key: "syncIndex", label: "自动更新频率", type: "sel", opts: syncs },

      { group: "02 可选开关", key: "allSubjects", label: "全学科（含物理/历史等）", type: "bool" },
      { group: "02 可选开关", key: "strictOneShot", label: "严格模式（每题 1 次机会）", type: "bool" },
      { group: "02 可选开关", key: "cooldownEnabled", label: "冷却惩罚（答错递增冷却）", type: "bool" },
      { group: "02 可选开关", key: "autoContinueOnCorrect", label: "答对后自动继续观看", type: "bool" },
      { group: "02 可选开关", key: "bootAutostart", label: "开机自启", type: "bool" },
      { group: "02 可选开关", key: "autoGuardOnBoot", label: "自动守护（打开 APP 时）", type: "bool" },
      { group: "02 可选开关", key: "pauseOnQuiz", label: "答题时暂停视频", type: "bool" },
      { group: "02 可选开关", key: "resumePlayback", label: "答对后恢复播放", type: "bool" },
      { group: "02 可选开关", key: "autoCheckUpdate", label: "自动检查版本", type: "bool" },

      { group: "03 学科配比", key: "w0", label: "数学配比", type: "num", min: 0, max: maxW },
      { group: "03 学科配比", key: "w1", label: "语文配比", type: "num", min: 0, max: maxW },
      { group: "03 学科配比", key: "w2", label: "英语配比", type: "num", min: 0, max: maxW },
      { group: "03 学科配比", key: "w3", label: "其他学科配比（需开启全学科）", type: "num", min: 0, max: maxW },

      { group: "04 生效时段与节假日", key: "scheduleEnabled", label: "启用生效时段（关闭则全天可弹题）", type: "bool" },
      { group: "04 生效时段与节假日", key: "skipHolidays", label: "智能跳过节假日（含调休补班）", type: "bool" },
      { group: "04 生效时段与节假日", key: "schoolWindows", label: "上学日时段 HH:mm-HH:mm:掩码（可 | 分两段）", type: "text" },
      { group: "04 生效时段与节假日", key: "homeWindows", label: "在家日时段（周末 / 法定假日 / 寒暑假）", type: "text" },
      { group: "04 生效时段与节假日", key: "vacationRange", label: "假期模式 YYYY-MM-DD~YYYY-MM-DD（留空关闭，到期自动恢复）", type: "text" },
      { group: "04 生效时段与节假日", key: "todayOverride", label: "今天临时 YYYY-MM-DD:allow 或 :block（留空无）", type: "text" }
    ];
    return out;
  }

  function renderForm(d) {
    var list = fields(d);
    var html = "", group = "";
    for (var i = 0; i < list.length; i++) {
      var f = list[i];
      if (f.group !== group) {
        if (group) html += "</div>";
        group = f.group;
        html += "<h2>" + esc(group) + "</h2><div class=\"fields\">";
      }
      var id = "f_" + f.key;
      if (f.type === "bool") {
        html += "<label class=\"row\" for=\"" + id + "\"><span>" + esc(f.label) +
          "</span><input type=\"checkbox\" id=\"" + id + "\"></label>";
      } else if (f.type === "sel") {
        html += "<label class=\"field\" for=\"" + id + "\"><span>" + esc(f.label) +
          "</span><select id=\"" + id + "\">";
        for (var j = 0; j < f.opts.length; j++) {
          html += "<option value=\"" + esc(f.opts[j][0]) + "\">" + esc(f.opts[j][1]) + "</option>";
        }
        html += "</select></label>";
      } else if (f.type === "text") {
        html += "<label class=\"field\" for=\"" + id + "\"><span>" + esc(f.label) +
          "</span><input type=\"text\" id=\"" + id + "\" spellcheck=\"false\"></label>";
      } else {
        html += "<label class=\"field\" for=\"" + id + "\"><span>" + esc(f.label) +
          "</span><input type=\"number\" id=\"" + id + "\" min=\"" + f.min +
          "\" max=\"" + f.max + "\"></label>";
      }
    }
    if (group) html += "</div>";
    $("form").innerHTML = html;
    fillForm(d, list);
  }

  function fillForm(d, list) {
    list = list || fields(d);
    for (var i = 0; i < list.length; i++) {
      var f = list[i], el = $("f_" + f.key), v = d[f.key];
      if (!el || v === undefined) continue;
      if (f.type === "bool") el.checked = !!v;
      else el.value = String(v);
    }
  }

  function collect(d) {
    var body = {}, list = fields(d);
    for (var i = 0; i < list.length; i++) {
      var f = list[i], el = $("f_" + f.key);
      if (!el) continue;
      if (f.type === "bool") body[f.key] = el.checked ? 1 : 0;
      else if (f.type === "text") body[f.key] = String(el.value || "").trim();
      else {
        var n = parseInt(el.value, 10);
        if (!isNaN(n)) body[f.key] = n;
      }
    }
    return body;
  }

  function renderStatus(d) {
    var rows = [
      ["守护服务", d.guard ? "运行中" : "未运行"],
      ["错题库", (d.wrong || 0) + " 题"],
      ["版本", d.version || "-"],
      ["当前学制", (d.editionLabels || [])[d.editionMode] || "自动"],
      ["此刻是否允许弹题", d.scheduleAllowedNow === false ? "否（静默时段）" : "是"],
      ["节假日日历", d.holidayCoverageEnd
        ? (d.holidayCoverageStart + "–" + d.holidayCoverageEnd + " 年")
        : "不可用（按星期）"]
    ];
    var html = "";
    for (var i = 0; i < rows.length; i++) {
      html += "<div><b>" + esc(rows[i][0]) + "</b>" + esc(rows[i][1]) + "</div>";
    }
    $("status").innerHTML = html;
  }

  function load(rebuild) {
    api("GET", "/api/status", null, function (code, d) {
      if (code !== 200) { show("loginMsg", "登录已失效，请重新验证"); return; }
      last = d;
      renderStatus(d);
      if (rebuild || !$("form").innerHTML) renderForm(d);
      else fillForm(d);
    });
  }

  $("loginBtn").onclick = function () {
    api("POST", "/api/login", { pin: $("pin").value }, function (code, d) {
      if (code !== 200) { show("loginMsg", (d && d.error) || "验证失败"); return; }
      token = d.token;
      show("loginMsg", "");
      $("login").hidden = true;
      $("panel").hidden = false;
      load(true);
    });
  };
  $("saveBtn").onclick = function () {
    if (!last) return;
    api("POST", "/api/config", collect(last), function (code, d) {
      show("saveMsg", code === 200 ? "已保存并生效" : ((d && d.error) || "保存失败"));
      if (code === 200) load(false);
    });
  };
  $("reloadBtn").onclick = function () {
    show("saveMsg", "");
    load(false);
  };
})();
