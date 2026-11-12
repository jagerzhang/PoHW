(function () {
  "use strict";
  var token = "";
  var last = null;
  var WEEK = ["一", "二", "三", "四", "五", "六", "日"];

  function $(id) { return document.getElementById(id); }
  function esc(s) {
    return String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;")
      .replace(/>/g, "&gt;").replace(/"/g, "&quot;");
  }
  function pad(n) { return (n < 10 ? "0" : "") + n; }
  function today() {
    var d = new Date();
    return d.getFullYear() + "-" + pad(d.getMonth() + 1) + "-" + pad(d.getDate());
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

  // 与 AdminApi.NUM_KEYS / BOOL_KEYS / STR_KEYS 一一对应；敏感项有意不列入。
  // windows / range / override 三种可视化控件在提交时仍拼回原来的紧凑字符串，
  // 因此 AdminApi 与 SchedulePolicy 的格式校验完全不用改。
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
      { group: "02 可选开关", key: "readMode", label: "语音朗读（需设备自带中文语音引擎）",
        type: "sel", opts: [[0, "关闭"], [1, "手动：播放键朗读"], [2, "自动读题 + 播放键朗读"]] },

      { group: "03 学科配比", key: "w0", label: "数学配比", type: "num", min: 0, max: maxW },
      { group: "03 学科配比", key: "w1", label: "语文配比", type: "num", min: 0, max: maxW },
      { group: "03 学科配比", key: "w2", label: "英语配比", type: "num", min: 0, max: maxW },
      { group: "03 学科配比", key: "w3", label: "其他学科配比（需开启全学科）", type: "num", min: 0, max: maxW },

      { group: "04 生效时段与节假日", key: "scheduleEnabled", label: "启用生效时段（关闭则全天可弹题）", type: "bool" },
      { group: "04 生效时段与节假日", key: "skipHolidays", label: "智能跳过节假日（含调休补班）", type: "bool" },
      { group: "04 生效时段与节假日", key: "schoolWindows", label: "上学日可答题时段",
        type: "windows", def: "17:00-21:00:1111111", hint: "工作日，以及调休补班的周末" },
      { group: "04 生效时段与节假日", key: "homeWindows", label: "在家日可答题时段",
        type: "windows", def: "09:00-21:00:1111111", hint: "周末、法定假日与寒暑假" },
      { group: "04 生效时段与节假日", key: "vacationRange", label: "假期模式（寒暑假）",
        type: "range", hint: "留空关闭；区间结束自动恢复，无需手动取消" },
      { group: "04 生效时段与节假日", key: "todayOverride", label: "今天临时",
        type: "override", hint: "只影响今天，隔天自动失效" }
    ];
    return out;
  }

  function windowsHtml(f) {
    var h = '<div class="field sched-field"><span>' + esc(f.label) + "</span>" +
      '<p class="hint">' + esc(f.hint) + "</p>";
    for (var i = 0; i < 2; i++) {
      h += '<div class="win-row"><div class="win-time">' +
        '<input type="time" lang="zh-CN" id="f_' + f.key + "_s" + i + '" aria-label="开始时间">' +
        "<em>至</em>" +
        '<input type="time" lang="zh-CN" id="f_' + f.key + "_e" + i + '" aria-label="结束时间">' +
        "</div>" + '<div class="week-picks">';
      for (var d = 0; d < 7; d++) {
        h += '<label><input type="checkbox" id="f_' + f.key + "_d" + i + "_" + d +
          '" checked><span>周' + WEEK[d] + "</span></label>";
      }
      h += "</div></div>";
    }
    h += '<p class="hint">第二行留空表示只用一段；支持跨午夜（如 22:00 至 02:00）。</p></div>';
    return h;
  }

  function rangeHtml(f) {
    return '<div class="field sched-field"><span>' + esc(f.label) + "</span>" +
      '<p class="hint">' + esc(f.hint) + "</p>" +
      '<div class="win-time"><input type="date" id="f_' + f.key + '_from" aria-label="假期开始">' +
      "<em>至</em>" +
      '<input type="date" id="f_' + f.key + '_to" aria-label="假期结束"></div></div>';
  }

  function overrideHtml(f) {
    return '<label class="field sched-field" for="f_' + f.key + '"><span>' +
      esc(f.label) + "（" + esc(today()) + "）</span>" +
      '<select id="f_' + f.key + '">' +
      '<option value="">按日程</option>' +
      '<option value="allow">今天全天放行</option>' +
      '<option value="block">今天全天屏蔽</option></select>' +
      '<p class="hint">' + esc(f.hint) + "</p></label>";
  }

  function renderForm(d) {
    var list = fields(d);
    var html = "", group = "";
    for (var i = 0; i < list.length; i++) {
      var f = list[i];
      if (f.group !== group) {
        if (group) html += "</div>";
        group = f.group;
        // 日程分组的控件较宽，即便在两列断点下也整组单列，避免被压窄裁字。
        var cls = group.indexOf("04 生效时段") === 0
          ? "fields fields-schedule" : "fields";
        html += "<h2>" + esc(group) + "</h2><div class=\"" + cls + "\">";
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
      } else if (f.type === "windows") {
        html += windowsHtml(f);
      } else if (f.type === "range") {
        html += rangeHtml(f);
      } else if (f.type === "override") {
        html += overrideHtml(f);
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

  // 内部串 "17:00-21:00:1111111|09:00-12:00:0000011" -> 两行控件。
  // 时间里也有冒号，必须从最后一个冒号切，跟 Java 侧 SchedulePolicy 一致。
  function fillWindows(key, raw) {
    var parts = String(raw || "").split("|");
    for (var i = 0; i < 2; i++) {
      var p = parts[i] || "";
      var last = p.lastIndexOf(":");
      var mask = "1111111", times = ["", ""];
      if (last >= 0) {
        mask = p.substring(last + 1);
        times = p.substring(0, last).split("-");
      }
      $("f_" + key + "_s" + i).value = times[0] || "";
      $("f_" + key + "_e" + i).value = times[1] || "";
      for (var d = 0; d < 7; d++) {
        $("f_" + key + "_d" + i + "_" + d).checked = mask.charAt(d) === "1";
      }
    }
  }
  function fillRange(key, raw) {
    var p = String(raw || ""), i = p.indexOf("~");
    $("f_" + key + "_from").value = i > 0 ? p.substring(0, i) : "";
    $("f_" + key + "_to").value = i > 0 ? p.substring(i + 1) : "";
  }
  function fillOverride(key, raw) {
    var p = String(raw || ""), el = $("f_" + key);
    el.value = p === today() + ":allow" ? "allow"
      : (p === today() + ":block" ? "block" : "");
  }

  function fillForm(d, list) {
    list = list || fields(d);
    for (var i = 0; i < list.length; i++) {
      var f = list[i], v = d[f.key];
      if (v === undefined) continue;
      if (f.type === "windows") { fillWindows(f.key, v); continue; }
      if (f.type === "range") { fillRange(f.key, v); continue; }
      if (f.type === "override") { fillOverride(f.key, v); continue; }
      var el = $("f_" + f.key);
      if (!el) continue;
      if (f.type === "bool") el.checked = !!v;
      else el.value = String(v);
    }
  }

  function collectWindows(f) {
    var parts = [];
    for (var i = 0; i < 2; i++) {
      var s = $("f_" + f.key + "_s" + i).value;
      var e = $("f_" + f.key + "_e" + i).value;
      if (!s || !e) continue;
      var mask = "";
      for (var d = 0; d < 7; d++) {
        mask += $("f_" + f.key + "_d" + i + "_" + d).checked ? "1" : "0";
      }
      if (mask.indexOf("1") < 0) mask = "1111111";
      parts.push(s + "-" + e + ":" + mask);
    }
    return parts.length ? parts.join("|") : f.def;
  }
  function collectRange(key) {
    var a = $("f_" + key + "_from").value, b = $("f_" + key + "_to").value;
    return (a && b) ? (a + "~" + b) : "";
  }
  function collectOverride(key) {
    var v = $("f_" + key).value;
    return v ? (today() + ":" + v) : "";
  }

  function collect(d) {
    var body = {}, list = fields(d);
    for (var i = 0; i < list.length; i++) {
      var f = list[i];
      if (f.type === "windows") { body[f.key] = collectWindows(f); continue; }
      if (f.type === "range") { body[f.key] = collectRange(f.key); continue; }
      if (f.type === "override") { body[f.key] = collectOverride(f.key); continue; }
      var el = $("f_" + f.key);
      if (!el) continue;
      if (f.type === "bool") body[f.key] = el.checked ? 1 : 0;
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
