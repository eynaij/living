// 早起森林 - 前端逻辑
(function () {
  const Bridge = (window.ZaoLin || {
    // 浏览器调试时的本地模拟；安卓 WebView 会注入原生对象。
    isCheckedToday: () => false,
    checkIn: () => '{"ok":false,"message":"请在打卡时段内操作","stats":{}}',
    getStats: () => '{"hour":7,"minute":0,"endHour":8,"endMinute":0,"windowOpen":true,"windowStatus":0}',
    getRecords: () => '{}',
    setWakeTime: () => true,
    hasNotificationPermission: () => true,
    openNotificationSettings: () => {},
    canScheduleExactAlarm: () => true,
    openAlarmSettings: () => {},
  });

  const $ = (sel) => document.querySelector(sel);
  const trees = [0, 1, 2, 3, 4].map((i) => document.getElementById('tree' + i));
  const treeLabels = ['一颗种子', '小嫩芽', '一棵幼苗', '茁壮小树', '参天大树'];

  let stats = null;
  let toastTimer = null;
  let records = {};
  let calDate = null; // 日历当前显示月份（固定为当月 1 号）

  function init() {
    calDate = new Date();
    calDate.setDate(1);
    updateDate();
    refresh();
    bindEvents();
    setInterval(refresh, 30000);
  }

  function updateDate() {
    const now = new Date();
    const week = ['日', '一', '二', '三', '四', '五', '六'][now.getDay()];
    $('#dateText').textContent = `${now.getFullYear()} 年 ${now.getMonth() + 1} 月 ${now.getDate()} 日`;
    $('#weekText').textContent = `· 星期${week}`;
  }

  function pad(value) { return String(value).padStart(2, '0'); }

  function dayKey(d) { return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`; }

  function windowText() {
    return stats.windowText || `${pad(stats.hour ?? 7)}:${pad(stats.minute ?? 0)}-${pad(stats.endHour ?? 8)}:${pad(stats.endMinute ?? 0)}`;
  }

  function refresh() {
    try {
      stats = JSON.parse(Bridge.getStats());
    } catch (e) {
      stats = {};
    }

    const stage = stats.treeStage ?? 0;
    const bigTrees = stats.bigTrees ?? 0;
    const cur = stats.curGrowth ?? 0;          // 当前树苗已成长天数（0-89）
    const fullTree = stage === 4;              // 刚好结果，展示大树

    // 树苗随天数连续变大：0 天 0.80 → 90 天 1.14，每天都有变化
    const grow = fullTree ? 1.14 : 0.80 + Math.min(1, cur / 90) * 0.34;
    trees.forEach((el, i) => {
      const active = i === stage;
      el.classList.toggle('active', active);
      if (active) el.style.setProperty('--grow', grow.toFixed(3));
    });

    if (stage === 4) {
      $('#treeStageLabel').textContent = '参天大树 · 已结果 🍎';
    } else if (cur > 0) {
      $('#treeStageLabel').textContent = `${treeLabels[stage] || ''} · 第 ${cur} 天`;
    } else {
      $('#treeStageLabel').textContent = treeLabels[0];
    }

    $('#progressBar').style.width = (stats.progress ?? 0) + '%';
    $('#growthFill').style.width = `${Math.min(100, (cur / 90) * 100)}%`;
    $('#growthDays').textContent = `${cur} / 90 天`;

    let progressText;
    if ((stats.growth ?? 0) === 0) progressText = '第一滴水就能让种子发芽';
    else if (stage === 4) progressText = `第 ${bigTrees} 棵树已结果 🍎 继续浇水培育新树苗`;
    else progressText = `已成长 ${cur} 天 · 再浇 ${stats.daysToFruit ?? 90} 天结果`;
    $('#progressText').textContent = progressText;

    // 森林：已结果的大树
    const forest = $('#forest');
    if (bigTrees > 0) {
      forest.textContent = `${'🌳'.repeat(Math.min(bigTrees, 10))} 已成大树 ${bigTrees} 棵`;
      forest.classList.remove('hidden');
    } else {
      forest.classList.add('hidden');
    }

    // 萎缩提醒 + 枯萎视觉
    const withering = !!stats.withering;
    $('#witherHint').classList.toggle('hidden', !withering);
    $('#treeStage').classList.toggle('withering', withering);

    const checked = !!stats.checkedToday;
    const windowOpen = stats.windowOpen !== false;
    const btn = $('#checkBtn');
    const hint = $('#checkedHint');
    btn.classList.toggle('done', checked);
    btn.classList.toggle('locked', !checked && !windowOpen);

    if (checked) {
      btn.textContent = '✅ 今天已浇水';
      hint.textContent = '今天已浇水，明天继续 🌱';
      hint.classList.remove('hidden');
    } else if (!windowOpen) {
      btn.textContent = stats.windowStatus === -1 ? '🕒 未到打卡时间' : '⌛ 今日打卡已结束';
      hint.textContent = `有效打卡时段 ${windowText()}`;
      hint.classList.remove('hidden');
    } else {
      btn.textContent = '💧 浇水打卡';
      hint.classList.add('hidden');
    }

    $('#streakNum').textContent = stats.streak ?? 0;
    $('#totalNum').textContent = stats.total ?? 0;
    $('#wakeNum').textContent = windowText();

    loadRecords();
    renderCalendar();
    renderStats();
  }

  function loadRecords() {
    try {
      records = JSON.parse(Bridge.getRecords()) || {};
    } catch (e) {
      records = {};
    }
  }

  function renderCalendar() {
    const y = calDate.getFullYear();
    const m = calDate.getMonth();
    $('#calTitle').textContent = `${y}年${m + 1}月`;

    const first = (new Date(y, m, 1).getDay() + 6) % 7; // 周一开头的前置空格数
    const days = new Date(y, m + 1, 0).getDate();
    const today = stats?.today || dayKey(new Date());

    let html = '';
    for (let i = 0; i < first; i++) html += '<div class="cal-cell"></div>';
    let monthCount = 0;
    for (let d = 1; d <= days; d++) {
      const key = `${y}-${pad(m + 1)}-${pad(d)}`;
      const rec = records[key];
      const checked = rec !== undefined;
      if (checked) monthCount++;
      let cls = 'cal-cell';
      if (checked) cls += ' checked';
      if (key === today) cls += ' today';
      html += `<div class="${cls}"><span class="day">${d}</span>${checked && rec ? `<span class="time">${rec}</span>` : ''}</div>`;
    }
    $('#calGrid').innerHTML = html;
    $('#calMonthCount').textContent = `本月 ${monthCount} 天`;
  }

  function shiftMonth(delta) {
    calDate = new Date(calDate.getFullYear(), calDate.getMonth() + delta, 1);
    renderCalendar();
  }

  function computeStats() {
    const dates = Object.keys(records).sort();
    const result = { best: 0, week: 0, total: dates.length, avgTime: '', first: '' };
    if (!dates.length) return result;

    result.first = dates[0];

    // 最高连续：逐日比较相邻记录
    let best = 1, run = 1;
    const dayMs = 86400000;
    for (let i = 1; i < dates.length; i++) {
      const prev = new Date(dates[i - 1] + 'T00:00:00');
      const cur = new Date(dates[i] + 'T00:00:00');
      if (Math.round((cur - prev) / dayMs) === 1) {
        run++;
      } else {
        run = 1;
      }
      if (run > best) best = run;
    }
    result.best = best;

    // 本周（周一起）打卡次数
    const now = new Date();
    const monday = new Date(now.getFullYear(), now.getMonth(), now.getDate());
    monday.setDate(monday.getDate() - ((monday.getDay() + 6) % 7));
    const weekStart = dayKey(monday);
    const todayKey = dayKey(now);
    result.week = dates.filter((d) => d >= weekStart && d <= todayKey).length;

    // 平均打卡时间（仅统计有时间的记录）
    const minutes = dates
      .map((d) => records[d])
      .filter((t) => /^\d{1,2}:\d{2}$/.test(t || ''))
      .map((t) => {
        const [h, m] = t.split(':').map(Number);
        return h * 60 + m;
      });
    if (minutes.length) {
      const avg = Math.round(minutes.reduce((a, b) => a + b, 0) / minutes.length);
      result.avgTime = `${pad(Math.floor(avg / 60))}:${pad(avg % 60)}`;
    }
    return result;
  }

  function renderStats() {
    const s = computeStats();
    $('#statStreak').textContent = stats?.streak ?? 0;
    $('#statBest').textContent = s.best;
    $('#statWeek').textContent = s.week;
    $('#statTotal').textContent = s.total;
    $('#statAvgTime').textContent = s.avgTime || '--';
    $('#statFirst').textContent = s.first || '--';
  }

  function bindEvents() {
    $('#checkBtn').addEventListener('click', onCheckIn);
    $('#calPrev').addEventListener('click', () => shiftMonth(-1));
    $('#calNext').addEventListener('click', () => shiftMonth(1));
    $('#gearBtn').addEventListener('click', openModal);
    $('#saveBtn').addEventListener('click', saveSettings);
    $('#closeBtn').addEventListener('click', closeModal);
    $('#openNotifBtn').addEventListener('click', () => Bridge.openNotificationSettings());
    $('#openAlarmBtn').addEventListener('click', () => Bridge.openAlarmSettings());
    $('#modal').addEventListener('click', (e) => {
      if (e.target.id === 'modal') closeModal();
    });
    document.addEventListener('keydown', (e) => {
      if (e.key === 'Escape') closeModal();
    });
  }

  function showToast(message) {
    const toast = $('#toast');
    toast.textContent = message;
    toast.classList.remove('hidden');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => toast.classList.add('hidden'), 2400);
  }

  function playWaterAnimation() {
    const drop = $('#waterDrop');
    drop.classList.remove('hidden');
    drop.style.animation = 'none';
    void drop.offsetWidth;
    drop.style.animation = '';
    setTimeout(() => drop.classList.add('hidden'), 950);
  }

  function onCheckIn() {
    if (stats?.checkedToday) {
      showToast('今天已经打过卡了，明天继续');
      return;
    }
    if (stats?.windowOpen === false) {
      showToast(`请在 ${windowText()} 内打卡`);
      return;
    }

    let result;
    try {
      result = JSON.parse(Bridge.checkIn());
    } catch (e) {
      showToast('打卡失败，请重试');
      return;
    }
    if (result.stats) stats = result.stats;
    refresh();
    if (!result.ok) {
      showToast(result.message || `请在 ${windowText()} 内打卡`);
      return;
    }
    playWaterAnimation();
    showToast(result.message || '打卡成功，小树喝到水啦');
  }

  function openModal() {
    $('#hIn').value = stats?.hour ?? 7;
    $('#mIn').value = stats?.minute ?? 0;
    $('#endHIn').value = stats?.endHour ?? 8;
    $('#endMIn').value = stats?.endMinute ?? 0;
    $('#settingsError').classList.add('hidden');
    updatePermStatus();
    $('#modal').classList.remove('hidden');
  }

  function closeModal() { $('#modal').classList.add('hidden'); }

  function updatePermStatus() {
    const notif = Bridge.hasNotificationPermission();
    const alarm = Bridge.canScheduleExactAlarm();

    $('#notifIcon').textContent = notif ? '✅' : '⚠️';
    $('#notifStatus').textContent = notif ? '已开启' : '未开启';
    $('#notifStatus').className = 'status ' + (notif ? 'on' : 'off');
    $('#openNotifBtn').disabled = notif;

    $('#alarmIcon').textContent = alarm ? '✅' : '⚠️';
    $('#alarmStatus').textContent = alarm ? '已开启' : '未开启';
    $('#alarmStatus').className = 'status ' + (alarm ? 'on' : 'off');
    $('#openAlarmBtn').disabled = alarm;
  }

  function saveSettings() {
    const h = Number($('#hIn').value);
    const m = Number($('#mIn').value);
    const endH = Number($('#endHIn').value);
    const endM = Number($('#endMIn').value);
    const valid = [h, m, endH, endM].every(Number.isInteger)
      && h >= 0 && h <= 23 && endH >= 0 && endH <= 23
      && m >= 0 && m <= 59 && endM >= 0 && endM <= 59
      && endH * 60 + endM > h * 60 + m;

    if (!valid || !Bridge.setWakeTime(h, m, endH, endM)) {
      $('#settingsError').classList.remove('hidden');
      return;
    }
    closeModal();
    refresh();
    showToast(`打卡时段已设为 ${pad(h)}:${pad(m)}-${pad(endH)}:${pad(endM)}`);
  }

  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) {
      refresh();
      if (!$('#modal').classList.contains('hidden')) updatePermStatus();
    }
  });

  init();
})();
