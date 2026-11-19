// 早起森林 - 前端逻辑
(function () {
  const Bridge = (window.ZaoLin || {
    // 浏览器调试时的本地模拟；安卓 WebView 会注入原生对象。
    isCheckedToday: () => false,
    checkIn: () => '{"ok":false,"message":"请在打卡时段内操作","stats":{}}',
    getStats: () => '{"hour":7,"minute":0,"endHour":8,"endMinute":0,"windowOpen":true,"windowStatus":0}',
    setWakeTime: () => true,
    hasNotificationPermission: () => true,
    openNotificationSettings: () => {},
    canScheduleExactAlarm: () => true,
    openAlarmSettings: () => {},
  });

  const $ = (sel) => document.querySelector(sel);
  const trees = [0, 1, 2, 3, 4].map((i) => document.getElementById('tree' + i));
  const treeLabels = ['一颗种子', '小嫩芽', '一棵幼苗', '茁壮小树', '参天大树'];
  const nextTargets = ['', '继续浇水 → 发芽', '浇水 → 茁壮成长', '浇水 → 长成小树', '浇水 → 枝繁叶茂'];

  let stats = null;
  let toastTimer = null;

  function init() {
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
    trees.forEach((el, i) => el.classList.toggle('active', i === stage));
    $('#treeStageLabel').textContent = treeLabels[stage] || '';
    $('#progressBar').style.width = (stats.progress ?? 0) + '%';
    $('#progressText').textContent = nextTargets[stage] || '🌲 枝繁叶茂！';

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
  }

  function bindEvents() {
    $('#checkBtn').addEventListener('click', onCheckIn);
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
