const { app, BrowserWindow, ipcMain, shell } = require('electron')
const path = require('path')
const service = require('./service')

let mainWindow = null
let statusTimer = null

function createWindow() {
  mainWindow = new BrowserWindow({
    width: 1200,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: '视频号下载器',
    backgroundColor: '#1a1a2e',
    titleBarStyle: 'hiddenInset',
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      webviewTag: true,
      contextIsolation: true,
      nodeIntegration: false
    }
  })

  mainWindow.loadFile(path.join(__dirname, '../renderer/index.html'))

  // 定时检测服务状态并推送到渲染层
  statusTimer = setInterval(async () => {
    if (!mainWindow || mainWindow.isDestroyed()) return
    const running = await service.checkStatus()
    mainWindow.webContents.send('status', running)
  }, 2000)

  // 外链用默认浏览器打开
  mainWindow.webContents.setWindowOpenHandler(({ url }) => {
    shell.openExternal(url)
    return { action: 'deny' }
  })
}

app.whenReady().then(() => {
  createWindow()
  app.on('activate', () => {
    if (BrowserWindow.getAllWindows().length === 0) createWindow()
  })
})

app.on('window-all-closed', () => {
  clearInterval(statusTimer)
  // 注意：关闭窗口不停止核心下载服务（下载由独立进程管理，避免中断进行中的下载）
  app.quit()
})

ipcMain.handle('start', async () => {
  try {
    await service.start()
    return { ok: true }
  } catch (e) {
    return { ok: false, error: e.message }
  }
})

ipcMain.handle('stop', async () => {
  await service.stop()
  return { ok: true }
})

ipcMain.handle('status', async () => ({ running: await service.checkStatus() }))

ipcMain.handle('restore-proxy', async () => {
  try {
    await service.restoreProxy()
    return { ok: true }
  } catch (e) {
    return { ok: false, error: e.message }
  }
})

ipcMain.handle('open-downloads', () => service.openDownloads())
ipcMain.handle('open-browser', () => shell.openExternal('http://127.0.0.1:2022'))
ipcMain.handle('get-arch', () => service.ARCH)
