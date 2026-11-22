// 轻影桌面版 - 预加载脚本（安全桥）
const { contextBridge, ipcRenderer } = require('electron');

contextBridge.exposeInMainWorld('api', {
  selectFolder: () => ipcRenderer.invoke('select-folder'),
  scanFolder: (dir) => ipcRenderer.invoke('scan-folder', dir),
  getState: () => ipcRenderer.invoke('get-state'),
  setLastFolder: (p) => ipcRenderer.invoke('set-last-folder', p),
  toggleFavorite: (p) => ipcRenderer.invoke('toggle-favorite', p),
  togglePinFolder: (p) => ipcRenderer.invoke('toggle-pin-folder', p),
  addRecent: (item) => ipcRenderer.invoke('add-recent', item),
  saveProgress: (p, pos, dur) => ipcRenderer.invoke('save-progress', p, pos, dur),
  getProgress: (p) => ipcRenderer.invoke('get-progress', p),
  saveSettings: (patch) => ipcRenderer.invoke('save-settings', patch),
  setAlwaysOnTop: (flag) => ipcRenderer.invoke('set-always-on-top', flag),
  isAlwaysOnTop: () => ipcRenderer.invoke('is-always-on-top'),
  captureScreenshot: (dataUrl, name) => ipcRenderer.invoke('capture-screenshot', dataUrl, name),
  openPath: (p) => ipcRenderer.invoke('open-path', p),
  platform: process.platform
});
