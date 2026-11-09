(function () {
  "use strict";
  var token = "";
  function $(id) { return document.getElementById(id); }
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
  function render(d) {
    var rows = [
      ["守护服务", d.guard ? "运行中" : "未运行"],
      ["年级", d.grade + " 年级"],
      ["出题频率", d.interval + " 秒"],
      ["每组题量", d.questionsPerRound + " 题"],
      ["通过正确率", d.passPercent + "%"],
      ["答题模式", d.roundMode === 2 ? "按组" : (d.roundMode === 1 ? "连对" : "单题")],
      ["错题库", d.wrong + " 题"],
      ["版本", d.version]
    ];
    var html = "";
    for (var i = 0; i < rows.length; i++) {
      html += "<div><b>" + rows[i][0] + "</b>" + rows[i][1] + "</div>";
    }
    $("status").innerHTML = html;
    $("n").value = d.questionsPerRound;
    $("p").value = d.passPercent;
    $("i").value = d.interval;
  }
  function load() {
    api("GET", "/api/status", null, function (code, d) {
      if (code === 200) render(d);
      else show("loginMsg", "登录已失效，请重新验证");
    });
  }
  $("loginBtn").onclick = function () {
    api("POST", "/api/login", { pin: $("pin").value }, function (code, d) {
      if (code !== 200) { show("loginMsg", (d && d.error) || "验证失败"); return; }
      token = d.token;
      show("loginMsg", "");
      $("login").hidden = true;
      $("panel").hidden = false;
      load();
    });
  };
  $("saveBtn").onclick = function () {
    var body = {
      questionsPerRound: parseInt($("n").value, 10),
      passPercent: parseInt($("p").value, 10),
      interval: parseInt($("i").value, 10)
    };
    api("POST", "/api/config", body, function (code) {
      show("saveMsg", code === 200 ? "已保存并生效" : "保存失败");
      if (code === 200) load();
    });
  };
})();
