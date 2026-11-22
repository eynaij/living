// 轻影桌面版 - 主进程
const { app, BrowserWindow, ipcMain, dialog, shell } = require('electron');
const path = require('path');
const fs = require('fs');
const store = require('./store');
const { scan } = require('./scanner');

let mainWindow = null;

function createWindow() {
  const state = store.getState();
  const b = state.windowBounds;
  mainWindow = new BrowserWindow({
    width: b && b.width ? b.width : 1180,
    height: b && b.height ? b.height : 760,
    x: b && b.x !== undefined ? b.x : undefined,
    y: b && b.y !== undefined ? b.y : undefined,
    minWidth: 720,
    minHeight: 480,
    backgroundColor: '#F5F7FA',
    title: '轻影',
    autoHideMenuBar: true,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      nodeIntegration: false,
      sandbox: false
    }
  });

  mainWindow.loadFile(path.join(__dirname, '..', 'renderer', 'index.html'));

  mainWindow.on('resize', () => {
    if (mainWindow && !mainWindow.isMaximized()) {
      store.saveWindowBounds(mainWindow.getBounds());
    }
  });
  mainWindow.on('move', () => {
    if (mainWindow && !mainWindow.isMaximized()) {
      store.saveWindowBounds(mainWindow.getBounds());
    }
  });

  mainWindow.on('closed', () => { mainWindow = null; });
}

// ---------------- IPC ----------------

ipcMain.handle('select-folder', async () => {
  const res = await dialog.showOpenDialog(mainWindow, {
    title: '选择视频文件夹',
    properties: ['openDirectory']
  });
  if (res.canceled || !res.filePaths.length) return null;
  return res.filePaths[0];
});

ipcMain.handle('scan-folder', async (_e, dir) => {
  if (!dir || !fs.existsSync(dir)) return [];
  return scan(dir);
});

ipcMain.handle('get-state', () => store.getState());

ipcMain.handle('set-last-folder', (_e, p) => store.setLastFolder(p));

ipcMain.handle('toggle-favorite', (_e, p) => store.toggleFavorite(p));

ipcMain.handle('toggle-pin-folder', (_e, p) => store.togglePinFolder(p));

ipcMain.handle('add-recent', (_e, item) => store.addRecent(item));

ipcMain.handle('save-progress', (_e, p, pos, dur) => store.saveProgress(p, pos, dur));

ipcMain.handle('get-progress', (_e, p) => store.getProgress(p));

ipcMain.handle('save-settings', (_e, patch) => store.saveSettings(patch));

ipcMain.handle('save-window-bounds', (_e, bounds) => store.saveWindowBounds(bounds));

ipcMain.handle('set-always-on-top', (_e, flag) => {
  if (mainWindow) mainWindow.setAlwaysOnTop(!!flag);
  return !!flag;
});

ipcMain.handle('is-always-on-top', () => (mainWindow ? mainWindow.isAlwaysOnTop() : false));

ipcMain.handle('capture-screenshot', async (_e, dataUrl, defaultName) => {
  try {
    const base64 = dataUrl.replace(/^data:image\/\w+;base64,/, '');
    const buf = Buffer.from(base64, 'base64');
    const res = await dialog.showSaveDialog(mainWindow, {
      title: '保存截图',
      defaultPath: defaultName || 'qingying-screenshot.png',
      filters: [{ name: 'PNG 图片', extensions: ['png'] }]
    });
    if (res.canceled || !res.filePath) return null;
    fs.writeFileSync(res.filePath, buf);
    return res.filePath;
  } catch (e) {
    console.error('capture-screenshot failed', e);
    return null;
  }
});

ipcMain.handle('open-path', (_e, p) => {
  if (!p) return false;
  shell.showItemInFolder(p);
  return true;
});

ipcMain.handle('reload-window', () => {
  if (mainWindow) mainWindow.reload();
});

// ---------------- lifecycle ----------------

app.whenReady().then(createWindow);

app.on('window-all-closed', () => {
  if (process.platform !== 'darwin') app.quit();
});

app.on('activate', () => {
  if (BrowserWindow.getAllWindows().length === 0) createWindow();
});
