/* ============== 戒股 App ==============
 * 戒除股票交易记录。纯原生 JS，无依赖。
 * 数据全部 localStorage。
 */

const STORE_KEYS = {
  checkins: 'ns.checkins.v1',
  triggers: 'ns.triggers.v1',
  trades: 'ns.trades.v1',
  journal: 'ns.journal.v1',
  settings: 'ns.settings.v1',
  installHidden: 'ns.install.hidden.v1',
  privacySeen: 'ns.privacy.seen.v1',
};

const DEFAULT_SETTINGS = {
  reminder: true,
  weekly: true,
  reminderHour: 22,
  reminderMinute: 0,
  quoteIndex: Math.floor(Math.random() * 1000),
};

const QUOTES = [
  ['交易的本质是概率，不是预测。', '本杰明·格雷厄姆'],
  ['不是市场不好，是我们总想一夜暴富。', '佚名'],
  ['90% 的时间应该在等待，10% 的时间在行动。', '杰西·利弗莫尔'],
  ['能不买，就已经赢了一半。', '老股民'],
  ['别让账户成为情绪的出口。', '佚名'],
  ['一年四倍的人到处有，五年一倍的人很少。', '彼得·林奇'],
  ['最大的亏损，是你以为自己不会亏。', '佚名'],
  ['冷静一天，比赌一次更值钱。', '佚名'],
  ['交易机会每天都有，本金不是。', '佚名'],
  ['不看盘的人，赚的最稳。', '老股民'],
  ['情绪激动时，按下「离开」比按下「买入」更难。', '佚名'],
  ['短线是情绪的修罗场。', '佚名'],
  ['先活下来，再谈盈利。', '股票作手'],
  ['错过不亏，做错才亏。', '佚名'],
  ['真正的高手，不会天天下单。', '佚名'],
];

/* ============ Storage ============ */

function loadList(key) {
  try {
    const raw = localStorage.getItem(key);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? arr : [];
  } catch (e) { return []; }
}
function saveList(key, arr) { localStorage.setItem(key, JSON.stringify(arr)); }

function getSettings() {
  try {
    return { ...DEFAULT_SETTINGS, ...JSON.parse(localStorage.getItem(STORE_KEYS.settings) || '{}') };
  } catch (e) { return { ...DEFAULT_SETTINGS }; }
}
function saveSettings(patch) {
  const s = { ...getSettings(), ...patch };
  localStorage.setItem(STORE_KEYS.settings, JSON.stringify(s));
  return s;
}

/* ============ Date helpers ============ */

function dayKey(ts = Date.now()) {
  const d = new Date(ts);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}
function startOfDay(ts = Date.now()) {
  const d = new Date(ts);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}
function fmtDate(ts) {
  const d = new Date(ts);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${m}-${day}`;
}
function fmtTime(ts) {
  const d = new Date(ts);
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${h}:${m}`;
}
function fmtMoney(v) {
  const n = Number(v) || 0;
  const sign = n >= 0 ? '' : '-';
  return `${sign}¥${Math.abs(Math.round(n)).toLocaleString('zh-CN')}`;
}

/* ============ Stats ============ */

function computeStreak(checkins) {
  // 去重到日期集合
  const days = new Set(checkins.map(c => c.date));
  const today = dayKey();
  let current = days.has(today) ? 1 : 0;
  if (current) {
    let prev = startOfDay() - 86400000;
    while (days.has(dayKey(prev))) { current++; prev -= 86400000; }
  }
  // 历史最长：从最早打卡往后扫
  const sorted = Array.from(days).sort();
  let longest = 0, run = 0, last = null;
  for (const d of sorted) {
    if (last === null) { run = 1; }
    else {
      const diff = Math.round((new Date(d) - new Date(last)) / 86400000);
      if (diff === 1) run++;
      else { longest = Math.max(longest, run); run = 1; }
    }
    last = d;
  }
  longest = Math.max(longest, run, current);
  return { current, longest, totalDays: days.size };
}

function triggerStats(triggers, days = 30) {
  const cutoff = Date.now() - days * 86400000;
  const recent = triggers.filter(t => t.t >= cutoff);
  const reasons = {};
  const feelings = {};
  recent.forEach(t => {
    if (t.reason) reasons[t.reason] = (reasons[t.reason] || 0) + 1;
    if (t.feeling) feelings[t.feeling] = (feelings[t.feeling] || 0) + 1;
  });
  const topReason = Object.entries(reasons).sort((a, b) => b[1] - a[1])[0] || [null, 0];
  const topFeeling = Object.entries(feelings).sort((a, b) => b[1] - a[1])[0] || [null, 0];
  return {
    count: triggers.length,
    recent: recent.length,
    topReason: topReason[0],
    topFeeling: topFeeling[0],
  };
}

function tradeStats(trades) {
  let pnl = 0, totalAmount = 0;
  trades.forEach(t => {
    pnl += Number(t.pnl) || 0;
    totalAmount += Number(t.amount) || 0;
  });
  return { pnl, totalAmount, count: trades.length };
}

/* ============ DOM ============ */

const $$ = (sel) => document.querySelectorAll(sel);

function $(id) {
  return document.getElementById(id);
}

/* ============ Tabs ============ */

function switchTab(name) {
  $$('.screen').forEach(s => s.hidden = true);
  $(`screen${name}`).hidden = false;
  $$('.tab').forEach(t => t.classList.toggle('active', t.dataset.tab === name));
  window.scrollTo({ top: 0, behavior: 'instant' });
  if (name === 'Today') renderToday();
  if (name === 'Triggers') renderTriggers();
  if (name === 'Trades') renderTrades();
  if (name === 'Stats') renderStats();
  if (name === 'Settings') renderSettings();
}

/* ============ Today ============ */

function isCheckedToday(checkins) {
  const t = dayKey();
  return checkins.some(c => c.date === t);
}

function renderToday() {
  const checkins = loadList(STORE_KEYS.checkins);
  const { current, longest, totalDays } = computeStreak(checkins);
  const checkedToday = isCheckedToday(checkins);

  $('streakNum').textContent = current;
  $('longestNum').textContent = longest;
  $('totalDays').textContent = totalDays;

  const btn = $('checkinBtn');
  const ciPrimary = $('ciPrimary');
  const ciSub = $('ciSub');
  if (checkedToday) {
    btn.classList.add('done');
    ciPrimary.textContent = '今天已经算上了 ✓';
    ciSub.textContent = '明天这个时候再来一次';
  } else {
    btn.classList.remove('done');
    ciPrimary.textContent = '今天我忍住了';
    ciSub.textContent = '点一下，记这一天';
  }

  // 近 7 天柱状
  const weekbar = $('weekbar');
  weekbar.innerHTML = '';
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  const keys = new Set(checkins.map(c => c.date));
  const labels = ['日', '一', '二', '三', '四', '五', '六'];
  for (let i = 6; i >= 0; i--) {
    const day = new Date(d.getTime() - i * 86400000);
    const key = dayKey(day.getTime());
    const isToday = i === 0;
    const on = keys.has(key);
    const el = document.createElement('div');
    el.className = 'wk';
    el.innerHTML = `
      <div class="wk-bar-wrap"><div class="wk-bar ${on ? 'on' : ''}"></div></div>
      <div class="wk-label">${labels[day.getDay()]}</div>
    `;
    weekbar.appendChild(el);
  }

  // 每日一句
  const settings = getSettings();
  const idx = settings.quoteIndex % QUOTES.length;
  const [text, who] = QUOTES[idx];
  $('quoteBody').textContent = text;
  $('quoteFoot').textContent = `— ${who}`;
}

function doCheckin() {
  const checkins = loadList(STORE_KEYS.checkins);
  const today = dayKey();
  if (isCheckedToday(checkins)) {
    showToast('今天已经算上了，明天再来');
    return;
  }
  checkins.push({ date: today, t: Date.now() });
  saveList(STORE_KEYS.checkins, checkins);
  const { current } = computeStreak(checkins);
  haptic();
  showToast(`好的，现在连续 ${current} 天`);
  renderToday();
}

/* ============ Triggers ============ */

let selectedReason = null;
let selectedFeeling = null;

function renderTriggers() {
  const triggers = loadList(STORE_KEYS.triggers).sort((a, b) => b.t - a.t);
  $('trigCount').textContent = `${triggers.length} 次`;
  const list = $('trigList');
  list.innerHTML = '';
  if (!triggers.length) {
    $('trigEmpty').hidden = false;
    return;
  }
  $('trigEmpty').hidden = true;
  triggers.forEach(t => {
    const el = document.createElement('div');
    el.className = 'item';
    el.innerHTML = `
      <div class="item-top">
        <span>${fmtDate(t.t)} ${fmtTime(t.t)}</span>
        <span class="item-del" data-del="${t.t}">删除</span>
      </div>
      <div class="item-meta">
        ${t.amount ? `<span class="tag">诱惑 ≈ ${fmtMoney(t.amount)}</span>` : ''}
        ${t.reason ? `<span class="tag">${t.reason}</span>` : ''}
        ${t.feeling ? `<span class="tag green">${t.feeling}</span>` : ''}
      </div>
      ${t.note ? `<div class="item-note">${escapeHtml(t.note)}</div>` : ''}
    `;
    list.appendChild(el);
  });
  bindDeleters(list, triggers, STORE_KEYS.triggers, renderTriggers);
}

function setupChipGroup(containerId, onPick, multi = false) {
  const box = $(containerId);
  if (!box) return;
  box.addEventListener('click', (e) => {
    const btn = e.target.closest('.chip');
    if (!btn) return;
    const val = btn.dataset.val;
    if (!multi) {
      box.querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
    }
    btn.classList.toggle('on');
    onPick(btn.classList.contains('on') ? val : null);
  });
}

function saveTrigger() {
  const amount = parseFloat($('trigAmount').value);
  const note = $('trigNote').value.trim();
  const triggers = loadList(STORE_KEYS.triggers);
  triggers.push({
    t: Date.now(),
    amount: isNaN(amount) ? null : amount,
    reason: selectedReason,
    feeling: selectedFeeling,
    note: note || null,
  });
  saveList(STORE_KEYS.triggers, triggers);
  haptic();
  // 清空表单
  $('trigAmount').value = '';
  $('trigNote').value = '';
  selectedReason = null;
  selectedFeeling = null;
  $('trigReasons').querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
  $('trigFeelings').querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
  showToast('记下了，下次再忍一次');
  renderTriggers();
}

/* ============ Trades ============ */

let tradeReason = null;
let tradeRegret = null;

function renderTrades() {
  const trades = loadList(STORE_KEYS.trades).sort((a, b) => b.t - a.t);
  $('tradeCount').textContent = `${trades.length} 笔`;
  const { pnl, totalAmount } = tradeStats(trades);
  $('wallPnl').textContent = fmtMoney(pnl);
  $('wallSub').textContent = trades.length
    ? `共 ${trades.length} 笔交易，累计投入 ${fmtMoney(totalAmount)}`
    : '这里空着最好';

  const list = $('tradeList');
  list.innerHTML = '';
  if (!trades.length) {
    $('tradeEmpty').hidden = false;
    return;
  }
  $('tradeEmpty').hidden = true;
  trades.forEach(t => {
    const el = document.createElement('div');
    const loss = (Number(t.pnl) || 0) < 0;
    const gain = (Number(t.pnl) || 0) > 0;
    const cls = loss ? 'loss' : gain ? 'gain' : 'neutral';
    const regretColor = /后悔/.test(t.regret || '') ? 'red' : 'green';
    el.className = 'item';
    el.innerHTML = `
      <div class="item-top">
        <span>${fmtDate(t.t)} ${fmtTime(t.t)}</span>
        <span class="item-amt ${cls}">${fmtMoney(t.pnl || 0)}</span>
      </div>
      <div class="item-meta">
        ${t.symbol ? `<span class="tag">${escapeHtml(t.symbol)}</span>` : ''}
        ${t.amount ? `<span class="tag">投入 ${fmtMoney(t.amount)}</span>` : ''}
        ${t.reason ? `<span class="tag">${t.reason}</span>` : ''}
        ${t.regret ? `<span class="tag ${regretColor}">${t.regret}</span>` : ''}
      </div>
      <div class="item-del" data-del="${t.t}" style="margin-top:8px;display:inline-block;">删除</div>
    `;
    list.appendChild(el);
  });
  bindDeleters(list, trades, STORE_KEYS.trades, renderTrades);
}

function saveTrade() {
  const symbol = $('tradeSymbol').value.trim();
  const amount = parseFloat($('tradeAmount').value);
  const pnl = parseFloat($('tradePnl').value);
  const trades = loadList(STORE_KEYS.trades);
  trades.push({
    t: Date.now(),
    symbol: symbol || null,
    amount: isNaN(amount) ? null : amount,
    pnl: isNaN(pnl) ? null : pnl,
    reason: tradeReason,
    regret: tradeRegret,
  });
  saveList(STORE_KEYS.trades, trades);
  haptic();
  // 清空
  $('tradeSymbol').value = '';
  $('tradeAmount').value = '';
  $('tradePnl').value = '';
  tradeReason = null;
  tradeRegret = null;
  $('tradeReasons').querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
  $('tradeRegret').querySelectorAll('.chip').forEach(c => c.classList.remove('on'));
  showToast('记下了，把这笔当做学费');
  renderTrades();
}

function bindDeleters(listEl, arr, key, rerender) {
  listEl.querySelectorAll('[data-del]').forEach(btn => {
    btn.addEventListener('click', (e) => {
      e.stopPropagation();
      const ts = Number(btn.dataset.del);
      if (!confirm('删除这一条记录？')) return;
      const next = arr.filter(x => x.t !== ts);
      saveList(key, next);
      rerender();
    });
  });
}

/* ============ Stats ============ */

function renderStats() {
  renderHeatmap();
  renderStreakChart();
  renderTrigChart();
  renderWeekSummary();
}

function renderHeatmap() {
  const el = $('heatmap');
  el.innerHTML = '';
  const checkins = loadList(STORE_KEYS.checkins);
  const days = new Set(checkins.map(c => c.date));
  const today = new Date();
  // 从本月 1 号到今天
  const first = new Date(today.getFullYear(), today.getMonth(), 1);
  const cols = 7;
  // 先补月初空白
  const firstDay = first.getDay(); // 0 = Sunday
  for (let i = 0; i < firstDay; i++) {
    const s = document.createElement('div');
    s.className = 'heat empty';
    el.appendChild(s);
  }
  let cur = new Date(first);
  while (cur <= today) {
    const key = dayKey(cur.getTime());
    const on = days.has(key);
    const isToday = key === dayKey();
    const s = document.createElement('div');
    s.className = `heat ${on ? 'on' : ''} ${isToday ? 'today' : ''}`;
    el.appendChild(s);
    cur.setDate(cur.getDate() + 1);
  }
}

function canvasSetup(canvas) {
  const dpr = window.devicePixelRatio || 1;
  const rect = canvas.getBoundingClientRect();
  canvas.width = rect.width * dpr;
  canvas.height = rect.height * dpr;
  const ctx = canvas.getContext('2d');
  ctx.scale(dpr, dpr);
  return { ctx, w: rect.width, h: rect.height };
}

function renderStreakChart() {
  const canvas = $('streakChart');
  if (!canvas) return;
  const { ctx, w, h } = canvasSetup(canvas);
  const checkins = loadList(STORE_KEYS.checkins);
  const days = new Set(checkins.map(c => c.date));

  // 过去 60 天每天连续值
  const n = 60;
  const series = [];
  let run = 0;
  const first = new Date();
  first.setHours(0, 0, 0, 0);
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(first.getTime() - i * 86400000);
    const key = dayKey(d.getTime());
    if (days.has(key)) run++; else run = 0;
    series.push(run);
  }

  const max = Math.max(1, ...series);
  const pad = { l: 30, r: 12, t: 10, b: 20 };
  const cw = w - pad.l - pad.r;
  const ch = h - pad.t - pad.b;

  ctx.fillStyle = 'rgba(107, 122, 148, 0.25)';
  ctx.strokeStyle = 'rgba(107, 122, 148, 0.25)';
  ctx.lineWidth = 1;
  for (let i = 0; i <= 3; i++) {
    const y = pad.t + ch * (i / 3);
    ctx.beginPath(); ctx.moveTo(pad.l, y); ctx.lineTo(w - pad.r, y); ctx.stroke();
    ctx.fillStyle = '#6B7A94'; ctx.font = '10px sans-serif'; ctx.textAlign = 'right';
    ctx.fillText(String(Math.round(max * (1 - i / 3))), pad.l - 4, y + 3);
  }

  const step = cw / (series.length - 1);
  ctx.beginPath();
  ctx.strokeStyle = '#60A5FA';
  ctx.lineWidth = 2;
  series.forEach((v, i) => {
    const x = pad.l + i * step;
    const y = pad.t + ch - (v / max) * ch;
    if (i === 0) ctx.moveTo(x, y); else ctx.lineTo(x, y);
  });
  ctx.stroke();

  // 渐变填充
  const grad = ctx.createLinearGradient(0, pad.t, 0, pad.t + ch);
  grad.addColorStop(0, 'rgba(59, 130, 246, 0.35)');
  grad.addColorStop(1, 'rgba(59, 130, 246, 0)');
  ctx.lineTo(pad.l + (series.length - 1) * step, pad.t + ch);
  ctx.lineTo(pad.l, pad.t + ch);
  ctx.closePath();
  ctx.fillStyle = grad;
  ctx.fill();

  // 今天的点
  const lastX = pad.l + (series.length - 1) * step;
  const lastY = pad.t + ch - (series[series.length - 1] / max) * ch;
  ctx.beginPath(); ctx.arc(lastX, lastY, 3.5, 0, Math.PI * 2);
  ctx.fillStyle = '#60A5FA'; ctx.fill();
}

function renderTrigChart() {
  const canvas = $('trigChart');
  if (!canvas) return;
  const { ctx, w, h } = canvasSetup(canvas);
  const triggers = loadList(STORE_KEYS.triggers);
  const now = Date.now();
  const week = 7 * 86400000;
  const counts = [];
  for (let i = 7; i >= 0; i--) {
    const from = now - i * week;
    const to = from + week;
    counts.push(triggers.filter(t => t.t >= from && t.t < to).length);
  }
  const max = Math.max(1, ...counts);
  const pad = { l: 24, r: 8, t: 10, b: 18 };
  const cw = w - pad.l - pad.r;
  const ch = h - pad.t - pad.b;
  const bw = cw / counts.length * 0.6;

  counts.forEach((c, i) => {
    const x = pad.l + i * (cw / counts.length) + (cw / counts.length - bw) / 2;
    const bh = (c / max) * ch;
    const y = pad.t + ch - bh;
    const grd = ctx.createLinearGradient(0, y, 0, pad.t + ch);
    grd.addColorStop(0, '#60A5FA');
    grd.addColorStop(1, '#3B82F6');
    ctx.fillStyle = grd;
    ctx.fillRect(x, y, bw, bh);
    if (c > 0) {
      ctx.fillStyle = '#E6EDF7'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center';
      ctx.fillText(String(c), x + bw / 2, y - 4);
    }
    if (i === counts.length - 1) {
      ctx.fillStyle = '#6B7A94'; ctx.font = '10px sans-serif'; ctx.textAlign = 'center';
      ctx.fillText('本周', x + bw / 2, pad.t + ch + 14);
    }
  });
}

function renderWeekSummary() {
  const checkins = loadList(STORE_KEYS.checkins);
  const triggers = loadList(STORE_KEYS.triggers);
  const trades = loadList(STORE_KEYS.trades);
  const weekStart = Date.now() - 7 * 86400000;
  const wkC = checkins.filter(c => c.t >= weekStart).length;
  const wkT = triggers.filter(t => t.t >= weekStart).length;
  const wkTr = trades.filter(t => t.t >= weekStart);
  const pnl = wkTr.reduce((a, t) => a + (Number(t.pnl) || 0), 0);

  const totalDays = new Set(checkins.map(c => c.date)).size;
  const { current, longest } = computeStreak(checkins);

  const lines = [];
  lines.push(`最近 7 天，打卡 <b>${wkC}</b> 天，冲动 <b>${wkT}</b> 次`);
  if (wkTr.length) {
    lines.push(`交易 <b>${wkTr.length}</b> 笔，累计 <b style="color:${pnl < 0 ? '#F87171' : '#34D399'}">${fmtMoney(pnl)}</b>`);
  }
  lines.push(`当前连续 <b>${current}</b> 天，历史最长 <b>${longest}</b> 天，总清醒 <b>${totalDays}</b> 天`);
  if (wkT > 0) {
    const ts = triggerStats(triggers, 7);
    if (ts.topReason) lines.push(`最常被诱惑的点：<b>${ts.topReason}</b>`);
  }

  const body = lines.join('。 ');
  $('weekSummary').innerHTML = body;
  $('weekNote').value = '';
}

/* ============ Settings ============ */

function renderSettings() {
  const s = getSettings();
  $('sReminder').checked = !!s.reminder;
  $('sWeekly').checked = !!s.weekly;
}

/* ============ Chip helpers ============ */

/* ============ Toast / confirm ============ */

function showToast(msg, dur = 1600) {
  let el = document.getElementById('__ns_toast');
  if (!el) {
    el = document.createElement('div');
    el.id = '__ns_toast';
    el.style.cssText = 'position:fixed;left:50%;top:42%;transform:translate(-50%,-50%);background:rgba(19,28,47,0.95);border:1px solid rgba(59,130,246,0.3);color:#E6EDF7;padding:12px 20px;border-radius:10px;font-size:14px;z-index:200;opacity:0;transition:opacity 0.2s;pointer-events:none;';
    document.body.appendChild(el);
  }
  el.textContent = msg;
  el.style.opacity = '1';
  clearTimeout(el._t);
  el._t = setTimeout(() => el.style.opacity = '0', dur);
}

function haptic() {
  try { navigator.vibrate && navigator.vibrate(10); } catch (e) {}
}

/* ============ Export / Import ============ */

function exportData() {
  const data = {
    checkins: loadList(STORE_KEYS.checkins),
    triggers: loadList(STORE_KEYS.triggers),
    trades: loadList(STORE_KEYS.trades),
    journal: loadList(STORE_KEYS.journal),
    settings: getSettings(),
    exportedAt: Date.now(),
  };
  const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `nostock-backup-${dayKey()}.json`;
  a.click();
  URL.revokeObjectURL(url);
}

function importData(file) {
  const reader = new FileReader();
  reader.onload = () => {
    try {
      const data = JSON.parse(reader.result);
      if (data.checkins) saveList(STORE_KEYS.checkins, data.checkins);
      if (data.triggers) saveList(STORE_KEYS.triggers, data.triggers);
      if (data.trades) saveList(STORE_KEYS.trades, data.trades);
      if (data.journal) saveList(STORE_KEYS.journal, data.journal);
      if (data.settings) saveSettings(data.settings);
      showToast('数据恢复完成');
      renderAll();
    } catch (e) {
      showToast('文件格式不对');
    }
  };
  reader.readAsText(file);
}

function resetAll() {
  if (!confirm('确定要清空所有打卡、冲动、交易记录？这个操作无法撤销。')) return;
  Object.values(STORE_KEYS).forEach(k => localStorage.removeItem(k));
  showToast('已清空，重新开始吧');
  renderAll();
}

/* ============ helpers ============ */

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, ch => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[ch]);
}

function renderAll() { renderToday(); renderTriggers(); renderTrades(); renderStats(); renderSettings(); }

/* ============ PWA install ============ */

let deferredPrompt = null;
function setupPwa() {
  if ('serviceWorker' in navigator && !/androidplatform\.net/.test(location.hostname)) {
    navigator.serviceWorker.register('./sw.js').catch(() => {});
  }
  window.addEventListener('beforeinstallprompt', (e) => {
    if (localStorage.getItem(STORE_KEYS.installHidden)) return;
    e.preventDefault();
    deferredPrompt = e;
    $('installBanner').hidden = false;
  });
  $('installNow').addEventListener('click', async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    await deferredPrompt.userChoice;
    deferredPrompt = null;
    $('installBanner').hidden = true;
  });
  $('installClose').addEventListener('click', () => {
    $('installBanner').hidden = true;
    localStorage.setItem(STORE_KEYS.installHidden, '1');
  });
}

/* ============ Wire up ============ */

function wire() {
  // Tabs
  $$('.tab').forEach(t => t.addEventListener('click', () => switchTab(t.dataset.tab)));
  // Quick jump
  $$('.quick').forEach(q => q.addEventListener('click', () => {
    const target = q.dataset.jump;
    if (target === 'trigger') switchTab('Triggers');
    else if (target === 'trade') switchTab('Trades');
    else if (target === 'journal') showToast('版本 1.1 再做日记');
  }));

  // Today
  $('checkinBtn').addEventListener('click', doCheckin);
  $('quoteRefresh').addEventListener('click', () => {
    saveSettings({ quoteIndex: (getSettings().quoteIndex + 1) % QUOTES.length });
    renderToday();
  });

  // Triggers
  setupChipGroup('trigReasons', v => { selectedReason = v; });
  setupChipGroup('trigFeelings', v => { selectedFeeling = v; });
  $('trigSave').addEventListener('click', saveTrigger);

  // Trades
  setupChipGroup('tradeReasons', v => { tradeReason = v; });
  setupChipGroup('tradeRegret', v => { tradeRegret = v; });
  $('tradeSave').addEventListener('click', () => {
    const pnl = parseFloat($('tradePnl').value);
    if (isNaN(pnl)) { showToast('请填盈亏金额，哪怕是 0'); return; }
    saveTrade();
  });

  // Settings
  $('sReminder').addEventListener('change', (e) => saveSettings({ reminder: e.target.checked }));
  $('sWeekly').addEventListener('change', (e) => saveSettings({ weekly: e.target.checked }));
  $('btnExport').addEventListener('click', exportData);
  $('btnImport').addEventListener('click', () => $('importFile').click());
  $('importFile').addEventListener('change', (e) => {
    const f = e.target.files[0];
    if (f) importData(f);
    e.target.value = '';
  });
  $('btnReset').addEventListener('click', resetAll);

  // 周报 Modal
  $('weekClose').addEventListener('click', () => $('weekModal').hidden = true);

  // 隐私 Modal
  $('privacyOpen').addEventListener('click', () => $('privacyModal').hidden = false);
  $('privacyClose').addEventListener('click', () => {
    $('privacyModal').hidden = true;
    localStorage.setItem(STORE_KEYS.privacySeen, '1');
  });

  // 首次启动强制看隐私
  maybeShowPrivacy();

  // 首次提醒
  maybeShowWeekly();

  setupPwa();
}

function maybeShowPrivacy() {
  if (!localStorage.getItem(STORE_KEYS.privacySeen)) {
    setTimeout(() => { $('privacyModal').hidden = false; }, 300);
  }
}

function maybeShowWeekly() {
  const s = getSettings();
  if (!s.weekly) return;
  const today = new Date();
  // 周一（1）弹
  if (today.getDay() !== 1) return;
  const flag = 'ns.weekly.shown.' + dayKey();
  if (localStorage.getItem(flag)) return;
  localStorage.setItem(flag, '1');
  setTimeout(() => {
    const body = $('weekSummary').innerHTML;
    $('weekModalBody').innerHTML = body;
    $('weekModal').hidden = false;
  }, 400);
}

/* ============ Bootstrap ============ */

document.addEventListener('DOMContentLoaded', () => {
  wire();
  renderAll();
});
