// 轻影桌面版 - 简单 JSON 持久化（收藏 / 播放记录 / 设置）
const fs = require('fs');
const path = require('path');
const { app } = require('electron');

const FILE = path.join(app.getPath('userData'), 'qingying-store.json');

const DEFAULT = {
  lastFolder: null,
  recentFolders: [],      // 最近打开过的文件夹（最多 8 个）
  favorites: [],          // 收藏的视频绝对路径
  pinnedFolders: [],      // 置顶的文件夹
  recents: [],            // 最近播放 [{path,name,position,duration,date}]
  speed: 1,
  speedLocked: false,
  volume: 1,
  sortAsc: true,
  windowBounds: null
};

let cache = null;

function read() {
  if (cache) return cache;
  try {
    if (fs.existsSync(FILE)) {
      const raw = fs.readFileSync(FILE, 'utf-8');
      cache = Object.assign({}, DEFAULT, JSON.parse(raw));
    } else {
      cache = Object.assign({}, DEFAULT);
    }
  } catch (e) {
    cache = Object.assign({}, DEFAULT);
  }
  return cache;
}

function write() {
  try {
    const dir = path.dirname(FILE);
    if (!fs.existsSync(dir)) fs.mkdirSync(dir, { recursive: true });
    fs.writeFileSync(FILE, JSON.stringify(cache, null, 2));
  } catch (e) {
    console.error('store write failed', e);
  }
}

function getState() {
  return read();
}

function setLastFolder(p) {
  const s = read();
  s.lastFolder = p;
  if (p) {
    s.recentFolders = [p, ...s.recentFolders.filter((f) => f !== p)].slice(0, 8);
  }
  write();
  return s;
}

function toggleFavorite(p) {
  const s = read();
  const i = s.favorites.indexOf(p);
  if (i >= 0) s.favorites.splice(i, 1);
  else s.favorites.push(p);
  write();
  return s.favorites;
}

function togglePinFolder(p) {
  const s = read();
  const i = s.pinnedFolders.indexOf(p);
  if (i >= 0) s.pinnedFolders.splice(i, 1);
  else s.pinnedFolders.unshift(p);
  write();
  return s.pinnedFolders;
}

function addRecent(item) {
  const s = read();
  s.recents = [item, ...s.recents.filter((r) => r.path !== item.path)].slice(0, 30);
  write();
  return s.recents;
}

function saveProgress(path2, position, duration) {
  const s = read();
  const r = s.recents.find((x) => x.path === path2);
  if (r) {
    r.position = position;
    if (duration) r.duration = duration;
    r.date = Date.now();
    write();
  }
  return r;
}

function getProgress(path2) {
  const s = read();
  const r = s.recents.find((x) => x.path === path2);
  return r ? r.position : 0;
}

function saveSettings(patch) {
  const s = read();
  Object.assign(s, patch);
  write();
  return s;
}

function saveWindowBounds(bounds) {
  const s = read();
  s.windowBounds = bounds;
  write();
}

module.exports = {
  getState,
  setLastFolder,
  toggleFavorite,
  togglePinFolder,
  addRecent,
  saveProgress,
  getProgress,
  saveSettings,
  saveWindowBounds
};
