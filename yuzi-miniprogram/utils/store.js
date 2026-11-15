const KEYS = {
  settings: 'yuzi.mp.settings.v1',
  records: 'yuzi.mp.records.v1',
  lastResult: 'yuzi.mp.last.v1',
};

const DEFAULT_SETTINGS = {
  dominant: 'right',
  stroke: 'clear',
  vibrate: true,
  baseUrl: '',
  // 默认开启演示模式：装好后立刻能看到完整流程；部署服务后到「设置」里关闭
  demo: true,
};

function getSettings() {
  let raw = null;
  try {
    raw = wx.getStorageSync(KEYS.settings);
  } catch (err) {
    raw = null;
  }
  const patch = raw && typeof raw === 'object' ? raw : {};
  return Object.assign({}, DEFAULT_SETTINGS, patch);
}

function saveSettings(patch) {
  const next = Object.assign({}, getSettings(), patch || {});
  wx.setStorageSync(KEYS.settings, next);
  return next;
}

function getRecords() {
  let raw = null;
  try {
    raw = wx.getStorageSync(KEYS.records);
  } catch (err) {
    raw = null;
  }
  if (!Array.isArray(raw)) return [];
  return raw
    .filter((r) => r && Number.isFinite(r.score) && Number.isFinite(r.t))
    .sort((a, b) => b.t - a.t);
}

function addRecord(record) {
  const list = getRecords();
  list.unshift(record);
  wx.setStorageSync(KEYS.records, list.slice(0, 300));
  return list;
}

function clearRecords() {
  wx.removeStorageSync(KEYS.records);
}

function saveLastResult(result) {
  wx.setStorageSync(KEYS.lastResult, result);
}

function getLastResult() {
  try {
    return wx.getStorageSync(KEYS.lastResult) || null;
  } catch (err) {
    return null;
  }
}

function dayKey(ts) {
  const d = new Date(ts);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${d.getFullYear()}-${m}-${day}`;
}

function fmtDay(key) {
  const today = dayKey(Date.now());
  const yesterday = dayKey(Date.now() - 86400000);
  if (key === today) return '今天';
  if (key === yesterday) return '昨天';
  const parts = key.split('-').map(Number);
  if (parts[0] === new Date().getFullYear()) return `${parts[1]} 月 ${parts[2]} 日`;
  return `${parts[0]}/${parts[1]}/${parts[2]}`;
}

function gradeOf(total) {
  return total >= 85 ? '优秀' : total >= 70 ? '良好' : total >= 55 ? '及格' : '待改进';
}

function gradeColor(total) {
  if (total >= 85) return '#d8ff4a';
  if (total >= 70) return '#25e0a5';
  if (total >= 55) return '#ffb547';
  return '#ff6b6b';
}

function stats(records) {
  const total = records.length;
  const sum = records.reduce((acc, r) => acc + r.score, 0);
  const best = records.reduce((m, r) => Math.max(m, r.score), 0);
  const days = new Set(records.map((r) => dayKey(r.t))).size;
  return {
    total,
    avg: total ? Math.round(sum / total) : 0,
    best,
    days,
  };
}

function hhmm(ts) {
  const d = new Date(ts);
  const h = String(d.getHours()).padStart(2, '0');
  const m = String(d.getMinutes()).padStart(2, '0');
  return `${h}:${m}`;
}

function weekBars(records) {
  const bars = [];
  for (let i = 6; i >= 0; i -= 1) {
    const ts = Date.now() - i * 86400000;
    const key = dayKey(ts);
    const count = records.filter((r) => dayKey(r.t) === key).length;
    const d = new Date(ts);
    bars.push({
      key,
      label: `${d.getMonth() + 1}/${d.getDate()}`,
      count,
    });
  }
  const max = bars.reduce((m, b) => Math.max(m, b.count), 0);
  return bars.map((b) => ({
    ...b,
    height: max ? Math.max(8, Math.round((b.count / max) * 100)) : 8,
    empty: b.count === 0,
  }));
}

function groupByDay(records) {
  const map = new Map();
  records.forEach((r) => {
    const key = dayKey(r.t);
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(r);
  });
  return Array.from(map.entries()).map(([key, items]) => ({
    key,
    label: fmtDay(key),
    count: items.length,
    avg: Math.round(items.reduce((a, r) => a + r.score, 0) / items.length),
    items: items.map((r) => ({
      t: r.t,
      time: hhmm(r.t),
      score: r.score,
      grade: gradeOf(r.score),
      color: gradeColor(r.score),
      stroke: r.stroke || '挥拍',
      demo: !!r.demo,
    })),
  }));
}

module.exports = {
  KEYS,
  getSettings,
  saveSettings,
  getRecords,
  addRecord,
  clearRecords,
  saveLastResult,
  getLastResult,
  gradeOf,
  gradeColor,
  stats,
  weekBars,
  groupByDay,
};
