// 文件夹扫描：递归查找视频文件
const fs = require('fs');
const path = require('path');

const VIDEO_EXTS = new Set([
  'mp4', 'mkv', 'webm', 'mov', 'avi', 'm4v', 'flv', 'wmv',
  'mpeg', 'mpg', 'ts', '3gp', 'm2ts', 'mts', 'vob', 'ogv',
  'rmvb', 'rm', 'asf', 'f4v', 'divx', 'mpe'
]);

function isVideo(name) {
  const i = name.lastIndexOf('.');
  if (i < 0) return false;
  return VIDEO_EXTS.has(name.slice(i + 1).toLowerCase());
}

function walk(dir, out) {
  let entries;
  try {
    entries = fs.readdirSync(dir, { withFileTypes: true });
  } catch (e) {
    return;
  }
  for (const e of entries) {
    if (e.name.startsWith('.')) continue;
    const full = path.join(dir, e.name);
    if (e.isDirectory()) {
      walk(full, out);
    } else if (e.isFile() && isVideo(e.name)) {
      try {
        const st = fs.statSync(full);
        out.push({
          path: full,
          name: e.name,
          dir: path.dirname(full),
          size: st.size,
          mtime: st.mtimeMs
        });
      } catch (e2) {
        // ignore
      }
    }
  }
}

function scan(dir) {
  const out = [];
  walk(dir, out);
  out.sort((a, b) => a.name.localeCompare(b.name, undefined, { numeric: true, sensitivity: 'base' }));
  return out;
}

module.exports = { scan };
