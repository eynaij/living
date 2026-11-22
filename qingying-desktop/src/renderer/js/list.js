// 轻影桌面版 - 列表页逻辑 + 共享状态
// 注意：contextBridge 暴露的 window.api 是不可配置全局属性，不能再用 const api 声明，否则 SyntaxError。
const qingApi = window.api;
const $ = (s) => document.querySelector(s);

const fmt = {
  duration(s) {
    if (!s || !isFinite(s) || s < 0) return '--:--';
    s = Math.floor(s);
    const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60;
    const pad = (n) => String(n).padStart(2, '0');
    return h > 0 ? `${h}:${pad(m)}:${pad(sec)}` : `${pad(m)}:${pad(sec)}`;
  },
  size(b) {
    if (!b) return '';
    const u = ['B', 'KB', 'MB', 'GB'];
    let i = 0, v = b;
    while (v >= 1024 && i < u.length - 1) { v /= 1024; i++; }
    return `${v.toFixed(v >= 10 || i === 0 ? 0 : 1)} ${u[i]}`;
  }
};

function fileUrl(p) {
  let s = String(p).replace(/\\/g, '/');
  if (!s.startsWith('/')) s = '/' + s;
  return 'file://' + encodeURI(s).replace(/#/g, '%23').replace(/\?/g, '%3F');
}

const App = (window.App = {
  state: null,
  videos: [],
  folder: null,
  favorites: new Set(),
  thumbCache: new Map(),
  durCache: new Map(),
  thumbQueue: [],
  thumbBusy: 0,
  observer: null,
  openPlayer(idx) { if (window.Player) window.Player.open(idx); }
});

// ---------------- 缩略图生成 ----------------
function requestThumb(path, cb) {
  if (App.thumbCache.has(path)) {
    cb(App.thumbCache.get(path), App.durCache.get(path));
    return;
  }
  App.thumbQueue.push({ path, cb });
  pumpQueue();
}

function pumpQueue() {
  while (App.thumbBusy < 3 && App.thumbQueue.length) {
    const job = App.thumbQueue.shift();
    App.thumbBusy++;
    loadThumb(job);
  }
}

function loadThumb(job) {
  const v = document.createElement('video');
  v.muted = true;
  v.preload = 'metadata';
  v.playsInline = true;
  let done = false;
  const finish = (url, dur) => {
    if (done) return;
    done = true;
    if (url) App.thumbCache.set(job.path, url);
    if (dur && isFinite(dur)) App.durCache.set(job.path, dur);
    job.cb(url || null, dur && isFinite(dur) ? dur : null);
    try { v.removeAttribute('src'); v.load(); } catch (e) {}
    App.thumbBusy--;
    pumpQueue();
  };
  v.addEventListener('loadedmetadata', () => {
    const dur = v.duration && isFinite(v.duration) ? v.duration : 0;
    try { v.currentTime = Math.min(3, dur ? dur * 0.15 : 1); } catch (e) { finish(null, dur); }
  });
  v.addEventListener('seeked', () => {
    const canvas = document.createElement('canvas');
    canvas.width = 320; canvas.height = 180;
    const ctx = canvas.getContext('2d');
    try {
      ctx.drawImage(v, 0, 0, 320, 180);
      finish(canvas.toDataURL('image/jpeg', 0.72), v.duration);
    } catch (e) { finish(null, v.duration); }
  });
  v.addEventListener('error', () => finish(null, 0));
  setTimeout(() => finish(null, 0), 8000);
  try { v.src = fileUrl(job.path); v.load(); } catch (e) { finish(null, 0); }
}

// ---------------- 渲染 ----------------
function renderFolderChips() {
  const box = $('#folderChips');
  const folders = Array.from(new Set([...(App.state.pinnedFolders || []), ...(App.state.recentFolders || [])]));
  if (!folders.length) { box.innerHTML = ''; return; }
  box.innerHTML = folders.map((f) => {
    const pinned = (App.state.pinnedFolders || []).includes(f);
    const active = f === App.folder ? 'active' : '';
    const name = f.split(/[\\/]/).filter(Boolean).pop() || f;
    return `<div class="chip ${active}" data-path="${escapeAttr(f)}" title="${escapeAttr(f)}">${pinned ? '📌 ' : ''}${escapeHtml(name)}</div>`;
  }).join('');
  box.querySelectorAll('.chip').forEach((el) => {
    el.addEventListener('click', () => openFolder(el.dataset.path));
  });
}

function renderRecents() {
  const sec = $('#recentSection');
  const bar = $('#recentBar');
  const recents = (App.state.recents || []).slice(0, 12);
  if (!recents.length) { sec.classList.add('hidden'); return; }
  sec.classList.remove('hidden');
  bar.innerHTML = recents.map((r) => {
    const pct = r.duration ? Math.min(100, (r.position / r.duration) * 100) : 0;
    return `<div class="recent-item" data-path="${escapeAttr(r.path)}" title="${escapeAttr(r.path)}">
      <div class="rname">${escapeHtml(r.name)}</div>
      <div class="rmeta">${fmt.duration(r.position)} / ${fmt.duration(r.duration)}</div>
      <div class="rprog"><i style="width:${pct.toFixed(0)}%"></i></div>
    </div>`;
  }).join('');
  bar.querySelectorAll('.recent-item').forEach((el) => {
    el.addEventListener('click', () => playByPath(el.dataset.path));
  });
}

function renderGrid() {
  const grid = $('#videoGrid');
  const hint = $('#emptyHint');
  const title = $('#folderTitle');
  const count = $('#videoCount');

  if (!App.videos.length) {
    grid.innerHTML = '';
    hint.classList.remove('hidden');
    title.textContent = App.folder ? '该文件夹没有视频' : '未选择文件夹';
    count.textContent = '';
    return;
  }
  hint.classList.add('hidden');
  const list = [...App.videos];
  if (!(App.state.sortAsc)) list.reverse();
  title.textContent = App.folder;
  count.textContent = `${list.length} 个视频`;

  grid.innerHTML = list.map((v, i) => {
    const fav = App.favorites.has(v.path) ? 'on' : '';
    const realIdx = App.videos.indexOf(v);
    return `<div class="video-card" data-idx="${realIdx}">
      <div class="thumb">
        <span class="ph">🎞️</span>
        <span class="dur" data-dur="${escapeAttr(v.path)}">--:--</span>
        <span class="fav ${fav}" data-fav="${escapeAttr(v.path)}">${fav ? '♥' : '♡'}</span>
      </div>
      <div class="vmeta">
        <div class="vname" title="${escapeAttr(v.name)}">${escapeHtml(v.name)}</div>
        <div class="vsub">${fmt.size(v.size)}</div>
      </div>
    </div>`;
  }).join('');

  grid.querySelectorAll('.video-card').forEach((card) => {
    card.addEventListener('click', (e) => {
      if (e.target.classList.contains('fav')) {
        e.stopPropagation();
        toggleFav(e.target.dataset.fav, e.target);
        return;
      }
      App.openPlayer(parseInt(card.dataset.idx, 10));
    });
  });

  observeThumbs();
}

function observeThumbs() {
  if (App.observer) App.observer.disconnect();
  App.observer = new IntersectionObserver((entries) => {
    entries.forEach((en) => {
      if (en.isIntersecting) {
        const card = en.target;
        App.observer.unobserve(card);
        const v = App.videos[parseInt(card.dataset.idx, 10)];
        if (!v) return;
        requestThumb(v.path, (url, dur) => {
          const img = document.createElement('img');
          img.alt = '';
          if (url) img.src = url; else card.querySelector('.ph').style.opacity = '.5';
          card.querySelector('.thumb').insertBefore(img, card.querySelector('.ph'));
          if (dur) {
            const el = card.querySelector(`[data-dur="${cssEscape(v.path)}"]`);
            if (el) el.textContent = fmt.duration(dur);
          }
        });
      }
    });
  }, { root: $('#videoGrid'), rootMargin: '200px' });
  gridCards().forEach((c) => App.observer.observe(c));
}

function gridCards() { return Array.from($('#videoGrid').querySelectorAll('.video-card')); }

async function toggleFav(path, el) {
  await qingApi.toggleFavorite(path);
  App.favorites = new Set((await qingApi.getState()).favorites);
  const on = App.favorites.has(path);
  el.classList.toggle('on', on);
  el.textContent = on ? '♥' : '♡';
}

function playByPath(path) {
  const i = App.videos.findIndex((v) => v.path === path);
  if (i >= 0) App.openPlayer(i);
}

async function openFolder(dir) {
  if (!dir) return;
  App.folder = dir;
  await qingApi.setLastFolder(dir);
  App.state = await qingApi.getState();
  App.videos = await qingApi.scanFolder(dir);
  renderFolderChips();
  renderGrid();
}

// ---------------- 工具 ----------------
function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function escapeAttr(s) { return escapeHtml(s); }
function cssEscape(s) {
  return String(s).replace(/(["\\])/g, '\\$1');
}
function showToast(msg) {
  const t = $('#toast');
  t.textContent = msg; t.classList.remove('hidden');
  clearTimeout(showToast._t);
  showToast._t = setTimeout(() => t.classList.add('hidden'), 1800);
}
window.showToast = showToast;
window.fileUrl = fileUrl;
window.fmt = fmt;

// ---------------- 初始化 ----------------
document.addEventListener('DOMContentLoaded', async () => {
  App.state = await qingApi.getState();
  App.favorites = new Set(App.state.favorites || []);

  $('#openFolderBtn').addEventListener('click', async () => {
    const dir = await qingApi.selectFolder();
    if (dir) openFolder(dir);
  });
  $('#sortBtn').addEventListener('click', async () => {
    App.state.sortAsc = !App.state.sortAsc;
    await qingApi.saveSettings({ sortAsc: App.state.sortAsc });
    $('#sortBtn').textContent = App.state.sortAsc ? '⇅ 按名称 ↑' : '⇅ 按名称 ↓';
    renderGrid();
  });
  $('#sortBtn').textContent = App.state.sortAsc ? '⇅ 按名称 ↑' : '⇅ 按名称 ↓';

  renderFolderChips();
  renderRecents();

  // 恢复上次文件夹
  if (App.state.lastFolder) {
    openFolder(App.state.lastFolder);
  }
});
