// 早起森林 - 前端逻辑
(function () {
  const Bridge = (window.ZaoLin || {
    // WebView 环境注入这个对象。浏览器调试时走本地模拟。
    isCheckedToday: () => false,
    checkIn: () => '{}',
    getStats: () => '{}',
    getWakeTime: () => '{"hour":7,"minute":0}',
    setWakeTime: () => {},
    hasNotificationPermission: () => true,
    openNotificationSettings: () => {},
    canScheduleExactAlarm: () => true,
    openAlarmSettings: () => {},
  });

  const $ = (sel) => document.querySelector(sel);
  const trees = [0,1,2,3,4].map((i) => document.getElementById('tree'+i));
  const treeLabels = ['一颗种子','小嫩芽','一棵幼苗','茁壮小树','参天大树'];
  const stageNames  = ['种子','发芽','幼苗','小树','大树'];
  const nextTargets = ['','继续浇水 → 发芽','浇水 → 茁壮成长','浇水 → 长成小树','浇水 → 枝繁叶茂'];

  let stats = null;

  // --------- 初始化 ---------
  function init() {
    updateDate();
    refresh();
    bindEvents();
  }

  function updateDate() {
    const now = new Date();
    const week = ['日','一','二','三','四','五','六'][now.getDay()];
    $('#dateText').textContent = `${now.getFullYear()} 年 ${now.getMonth()+1} 月 ${now.getDate()} 日`;
    $('#weekText').textContent = `· 星期${week}`;
  }

  function refresh() {
    try {
      stats = JSON.parse(Bridge.getStats());
    } catch (e) {
      stats = {};
    }

    // 树
    const stage = stats.treeStage ?? 0;
    trees.forEach((el, i) => el.classList.toggle('active', i === stage));
    $('#treeStageLabel').textContent = treeLabels[stage] || '';

    // 进度条
    const progress = stats.progress ?? 0;
    $('#progressBar').style.width = progress + '%';
    $('#progressText').textContent = nextTargets[stage] || '🌲 枝繁叶茂！';

    // 打卡按钮
    const checked = !!stats.checkedToday;
    const btn = $('#checkBtn');
    btn.classList.toggle('done', checked);
    btn.textContent = checked ? '✅ 今天已浇水' : '💧 浇水打卡';
    $('#checkedHint').classList.toggle('hidden', !checked);

    // 统计
    $('#streakNum').textContent = stats.streak ?? 0;
    $('#totalNum').textContent = stats.total ?? 0;
    const h = String(stats.hour ?? 7).padStart(2,'0');
    const m = String(stats.minute ?? 0).padStart(2,'0');
    $('#wakeNum').textContent = `${h}:${m}`;
  }

  // --------- 事件绑定 ---------
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

  function onCheckIn() {
    if (stats && stats.checkedToday) return;
    // 水滴动画
    const drop = $('#waterDrop');
    drop.classList.remove('hidden');
    drop.style.animation = 'none';
    void drop.offsetWidth; // 重排
    drop.style.animation = '';
    setTimeout(() => drop.classList.add('hidden'), 950);

    const result = Bridge.checkIn();
    try { stats = JSON.parse(result); } catch (e) {}
    refresh();
  }

  function openModal() {
    const h = stats?.hour ?? 7;
    const m = stats?.minute ?? 0;
    $('#hIn').value = h;
    $('#mIn').value = m;
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
    let h = parseInt($('#hIn').value, 10);
    let m = parseInt($('#mIn').value, 10);
    if (isNaN(h) || h < 0 || h > 23) h = 7;
    if (isNaN(m) || m < 0 || m > 59) m = 0;
    Bridge.setWakeTime(h, m);
    closeModal();
    refresh();
  }

  // 退出页面时尝试恢复 permission 状态（用户可能从设置回来）
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden && !$('#modal').classList.contains('hidden')) {
      updatePermStatus();
    }
  });

  init();
})();
