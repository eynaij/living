const { contextBridge, ipcRenderer } = require('electron')

contextBridge.exposeInMainWorld('api', {
  start: () => ipcRenderer.invoke('start'),
  stop: () => ipcRenderer.invoke('stop'),
  status: () => ipcRenderer.invoke('status'),
  restoreProxy: () => ipcRenderer.invoke('restore-proxy'),
  openDownloads: () => ipcRenderer.invoke('open-downloads'),
  openBrowser: () => ipcRenderer.invoke('open-browser'),
  getArch: () => ipcRenderer.invoke('get-arch'),
  onStatus: (cb) => ipcRenderer.on('status', (_e, running) => cb(running))
})
