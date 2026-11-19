/* 时迹 · 屏幕时间统计 */
(function () {
  'use strict';

  const CAT_COLORS = {
    '社交': '#60A5FA',
    '视频': '#F472B6',
    '游戏': '#A78BFA',
    '音乐': '#34D399',
    '购物': '#FBBF24',
    '阅读资讯': '#22D3EE',
    '浏览器': '#F87171',
    '工具': '#94A3B8',
    '出行': '#4ADE80',
    '其他': '#64748B'
  };

  const $ = (id) => document.getElementById(id);
  const state = {
    days: [],
    period: 'day',
    loading: false,
    granted: false
  };

  // ---------- 工具 ----------
  function parseDay(str) {
    const [y, m, d] = str.split('-').map(Number);
    return new Date(y, m - 1, d);
  }

  function dayKey(d) {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const dd = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${dd}`;
  }

  function startOfWeek(d) {
    const x = new Date(d.getFullYear(), d.getMonth(), d.getDate());
    const wd = (x.getDay() + 6) % 7; // 周一为 0
    x.setDate(x.getDate() - wd);
    return x;
  }

  function hm(ms) {
    const total = Math.round(ms / 60000);
    return { h: Math.floor(total / 60), m: total % 60 };
  }

  function short(ms) {
    if (!ms) return '0 分';
    const t = hm(ms);
    if (t.h === 0) return `${t.m} 分`;
    if (t.m === 0) return `${t.h} 小时`;
    return `${t.h} 小时 ${t.m} 分`;
  }

  function shortCompact(ms) {
    if (!ms) return '0m';
    const t = hm(ms);
    return t.h ? `${t.h}h${t.m ? t.m + 'm' : ''}` : `${t.m}m`;
  }

  function pctDelta(cur, prev) {
    if (!prev) return null;
    return Math.round(((cur - prev) / prev) * 100);
  }

  function el(tag, cls, text) {
    const e = document.createElement(tag);
    if (cls) e.className = cls;
    if (text != null) e.textContent = text;
    return e;
  }

  // ---------- 数据 ----------
  function loadData() {
    if (state.loading) return;
    if (!window.Shiji) {
      // 浏览器调试环境
      renderEmpty('需要在时迹 App 内打开');
      return;
    }
    state.granted = window.Shiji.isGranted();
    if (!state.granted) {
      $('gate').hidden = false;
      $('main').hidden = true;
      return;
    }
    state.loading = true;
    try {
      const raw = JSON.parse(window.Shiji.loadUsage(400));
      if (!raw.ok) {
        $('gate').hidden = false;
        $('main').hidden = true;
        state.loading = false;
        return;
      }
      state.days = raw.days || [];
      $('gate').hidden = true;
      $('main').hidden = false;
      render();
    } catch (e) {
      renderEmpty('读取失败：' + e.message);
    } finally {
      state.loading = false;
    }
  }

  function renderEmpty(msg) {
    $('gate').hidden = true;
    $('main').hidden = false;
    $('barChart').innerHTML = '';
    $('rank').innerHTML = '';
    $('rankEmpty').hidden = false;
    $('rankEmpty').textContent = msg;
  }

  function dayByKey(key) {
    return state.days.find((d) => d.date === key);
  }

  function sumApps(list) {
    const map = new Map();
    for (const day of list) {
      for (const a of day.apps || []) {
        const cur = map.get(a.pkg) || { pkg: a.pkg, label: a.label, cat: a.cat, ms: 0, launches: 0 };
        cur.ms += a.ms;
        cur.launches += a.launches || 0;
        map.set(a.pkg, cur);
      }
    }
    return Array.from(map.values()).sort((x, y) => y.ms - x.ms);
  }

  // ---------- 周期数据 ----------
  function buildBuckets() {
    const days = state.days;
    if (!days.length) return { buckets: [], range: [] };
    const today = parseDay(days[days.length - 1].date);

    if (state.period === 'day') {
      const tail = days.slice(-7);
      const buckets = tail.map((d) => {
        const dt = parseDay(d.date);
        const isToday = d.date === dayKey(today);
        return {
          label: isToday ? '今天' : ['日', '一', '二', '三', '四', '五', '六'][dt.getDay()],
          ms: d.total,
          today: isToday,
          dim: d.total === 0
        };
      });
      return { buckets, range: [days[days.length - 1]] };
    }

    if (state.period === 'week') {
      const byWeek = new Map();
      for (const d of days) {
        const key = dayKey(startOfWeek(parseDay(d.date)));
        byWeek.set(key, (byWeek.get(key) || 0) + d.total);
      }
      const keys = Array.from(byWeek.keys()).sort().slice(-8);
      const thisWeek = dayKey(startOfWeek(today));
      const buckets = keys.map((k) => {
        const dt = parseDay(k);
        return {
          label: `${dt.getMonth() + 1}/${dt.getDate()}`,
          ms: byWeek.get(k) || 0,
          today: k === thisWeek,
          dim: (byWeek.get(k) || 0) === 0
        };
      });
      const range = days.filter((d) => dayKey(startOfWeek(parseDay(d.date))) === thisWeek);
      return { buckets, range };
    }

    // month
    const byMonth = new Map();
    for (const d of days) {
      const dt = parseDay(d.date);
      const key = `${dt.getFullYear()}-${dt.getMonth()}`;
      byMonth.set(key, (byMonth.get(key) || 0) + d.total);
    }
    const keys = Array.from(byMonth.keys()).sort().slice(-6);
    const thisMonth = `${today.getFullYear()}-${today.getMonth()}`;
    const buckets = keys.map((k) => {
      const [y, m] = k.split('-').map(Number);
      return {
        label: `${m + 1}月`,
        ms: byMonth.get(k) || 0,
        today: k === thisMonth,
        dim: (byMonth.get(k) || 0) === 0
      };
    });
    const range = days.filter((d) => {
      const dt = parseDay(d.date);
      return `${dt.getFullYear()}-${dt.getMonth()}` === thisMonth;
    });
    return { buckets, range };
  }

  // ---------- 渲染 ----------
  function render() {
    if (!state.days.length) {
      renderEmpty('还没有使用记录，过一会儿再来看看');
      return;
    }
    const todayStr = state.days[state.days.length - 1].date;
    const today = parseDay(todayStr);
    $('todayLabel').textContent = `${today.getFullYear()}年${today.getMonth() + 1}月${today.getDate()}日`;

    const { buckets, range } = buildBuckets();
    const periodTotal = range.reduce((s, d) => s + d.total, 0);
    const activeDays = range.filter((d) => d.total > 0).length || 1;

    // Hero
    const labelMap = { day: '今日屏幕时间', week: '本周屏幕时间', month: '本月屏幕时间' };
    $('heroLabel').textContent = labelMap[state.period];
    const t = hm(periodTotal);
    $('heroH').textContent = t.h;
    $('heroM').textContent = t.m;

    const sub = $('heroSub');
    sub.innerHTML = '';
    const avg = Math.round(periodTotal / (state.period === 'day' ? 1 : activeDays));
    const avgText = state.period === 'day'
      ? `近 7 天日均 <b>${shortCompact(Math.round(state.days.slice(-7).reduce((s, d) => s + d.total, 0) / 7))}</b>`
      : `日均 <b>${shortCompact(avg)}</b> · 共 ${activeDays} 天有记录`;

    sub.insertAdjacentHTML('beforeend', `<span>${avgText}</span>`);

    // 环比
    const cur = buckets[buckets.length - 1];
    const prev = buckets[buckets.length - 2];
    if (cur && prev && prev.ms > 0) {
      const p = pctDelta(cur.ms, prev.ms);
      if (p !== null && isFinite(p)) {
        const cls = p > 0 ? 'up' : 'down';
        const word = state.period === 'day' ? '较前日' : state.period === 'week' ? '较上周' : '较上月';
        sub.insertAdjacentHTML('beforeend',
          ` · <span class="${cls}">${word} ${p > 0 ? '+' : ''}${p}%</span>`);
      }
    }

    renderChart(buckets);
    renderCategories(range);
    renderRank(range);
  }

  function renderChart(buckets) {
    const wrap = $('barChart');
    wrap.innerHTML = '';
    const max = Math.max.apply(null, buckets.map((b) => b.ms).concat([1]));
    const titles = { day: '最近 7 天', week: '最近 8 周', month: '最近 6 个月' };
    $('chartTitle').textContent = titles[state.period];
    $('chartHint').textContent = `峰值 ${shortCompact(max)}`;

    buckets.forEach((b) => {
      const col = el('div', 'bar-col');
      const slot = el('div', 'bar-slot');
      const bar = el('div', 'bar' + (b.today ? ' today' : b.dim ? ' dim' : ''));
      const ratio = max > 0 ? b.ms / max : 0;
      bar.style.height = Math.max(3, Math.round(ratio * 100)) + '%';
      slot.appendChild(bar);
      col.appendChild(slot);
      const label = el('div', 'bar-label' + (b.today ? ' on' : ''), b.label);
      col.appendChild(label);
      wrap.appendChild(col);
    });
  }

  function renderCategories(range) {
    const apps = sumApps(range);
    const map = new Map();
    for (const a of apps) {
      const c = a.cat || '其他';
      map.set(c, (map.get(c) || 0) + a.ms);
    }
    let list = Array.from(map.entries())
      .map(([name, ms]) => ({ name, ms }))
      .sort((a, b) => b.ms - a.ms);

    const total = list.reduce((s, x) => s + x.ms, 0);
    $('catTotal').textContent = total ? short(total) : '';

    // 超过 6 类时合并尾部，且只保留一个“其他”
    if (list.length > 6) {
      const head = list.slice(0, 5);
      const restMs = list.slice(5).reduce((s, x) => s + x.ms, 0);
      const other = head.find((x) => x.name === '其他');
      if (other) other.ms += restMs;
      else head.push({ name: '其他', ms: restMs });
      list = head;
    }

    const donut = $('donut');
    if (!total) {
      donut.style.background = 'conic-gradient(#2A3A55 0% 100%)';
    } else {
      let acc = 0;
      const stops = list.map((x) => {
        const from = (acc / total) * 100;
        acc += x.ms;
        const to = (acc / total) * 100;
        const color = CAT_COLORS[x.name] || '#64748B';
        return `${color} ${from.toFixed(2)}% ${to.toFixed(2)}%`;
      });
      donut.style.background = `conic-gradient(${stops.join(',')})`;
    }
    $('donutMain').textContent = shortCompact(total);

    const legend = $('legend');
    legend.innerHTML = '';
    list.slice(0, 6).forEach((x) => {
      const li = el('li');
      const dot = el('span', 'dot');
      dot.style.background = CAT_COLORS[x.name] || '#64748B';
      li.appendChild(dot);
      li.appendChild(el('span', 'name', x.name));
      li.appendChild(el('span', 'pct', total ? Math.round((x.ms / total) * 100) + '%' : '0%'));
      legend.appendChild(li);
    });
  }

  function renderRank(range) {
    const apps = sumApps(range).filter((a) => a.ms >= 60000);
    const list = $('rank');
    list.innerHTML = '';
    $('rankCount').textContent = apps.length ? `${apps.length} 个应用` : '';
    $('rankEmpty').hidden = apps.length > 0;

    const max = apps.length ? apps[0].ms : 1;
    apps.slice(0, 12).forEach((a, i) => {
      const li = el('li');

      const row = el('div', 'rank-row');
      row.appendChild(el('span', 'rank-no', String(i + 1)));
      row.appendChild(el('span', 'rank-name', a.label));
      if (a.cat) row.appendChild(el('span', 'rank-cat', a.cat));
      row.appendChild(el('span', 'rank-time', shortCompact(a.ms)));
      li.appendChild(row);

      const bar = el('div', 'rank-bar');
      const fill = el('i');
      fill.style.width = Math.max(2, Math.round((a.ms / max) * 100)) + '%';
      const color = CAT_COLORS[a.cat] || '#60A5FA';
      fill.style.background = `linear-gradient(90deg, ${color}, ${color}aa)`;
      bar.appendChild(fill);
      li.appendChild(bar);

      list.appendChild(li);
    });
  }

  // ---------- 事件 ----------
  function bind() {
    $('refreshBtn').addEventListener('click', loadData);

    $('grantBtn').addEventListener('click', function () {
      if (window.Shiji) {
        window.Shiji.openSettings();
      } else {
        alert('请在时迹 App 内开启权限');
      }
    });

    $('seg').addEventListener('click', function (e) {
      const btn = e.target.closest('button[data-p]');
      if (!btn) return;
      state.period = btn.dataset.p;
      Array.from($('seg').children).forEach((b) => b.classList.toggle('active', b === btn));
      render();
    });

    window.ShijiOnResume = function () {
      const granted = window.Shiji ? window.Shiji.isGranted() : false;
      if (granted && !state.granted) loadData();
    };
  }

  bind();
  loadData();
})();
