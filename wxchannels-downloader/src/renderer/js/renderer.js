const { api } = window

const badge = document.getElementById('badge')
const btnStart = document.getElementById('btn-start')
const btnStop = document.getElementById('btn-stop')
const btnDownloads = document.getElementById('btn-downloads')
const btnBrowser = document.getElementById('btn-browser')
const btnRestore = document.getElementById('btn-restore')
const view = document.getElementById('view')
const placeholder = document.getElementById('placeholder')
const phError = document.getElementById('ph-error')
const toast = document.getElementById('toast')

let isRunning = false
let isStarting = false

function showToast(msg, ms = 2600) {
  toast.textContent = msg
  toast.style.display = 'block'
  clearTimeout(showToast._t)
  showToast._t = setTimeout(() => { toast.style.display = 'none' }, ms)
}

function setBadge(state) {
  badge.className = 'badge ' + (state === 'on' ? 'badge-on' : state === 'starting' ? 'badge-starting' : 'badge-off')
  badge.textContent = state === 'on' ? '运行中' : state === 'starting' ? '启动中' : '未启动'
}

function applyRunning(running) {
  isRunning = running
  if (running) {
    placeholder.style.display = 'none'
    view.style.display = 'block'
    if (!view.src || view.src === 'about:blank' || !view.src.includes('2022')) {
      view.src = 'http://127.0.0.1:2022/'
    }
    btnStart.style.display = 'none'
    btnStop.style.display = ''
    setBadge('on')
  } else {
    view.style.display = 'none'
    view.src = 'about:blank'
    placeholder.style.display = 'flex'
    btnStart.style.display = ''
    btnStop.style.display = 'none'
    setBadge(isStarting ? 'starting' : 'off')
  }
}

async function refresh() {
  const { running } = await api.status()
  applyRunning(running)
}

btnStart.addEventListener('click', async () => {
  if (isStarting) return
  isStarting = true
  setBadge('starting')
  phError.style.display = 'none'
  btnStart.disabled = true
  showToast('正在启动服务，请在弹出的密码框输入开机密码…')
  const res = await api.start()
  if (!res.ok) {
    isStarting = false
    btnStart.disabled = false
    setBadge('off')
    phError.textContent = '启动失败：' + (res.error || '未知错误') + '（若取消了密码框，请重新点击启动）'
    phError.style.display = 'block'
    showToast('启动失败')
    return
  }
  // 轮询等待服务起来
  let waited = 0
  const iv = setInterval(async () => {
    waited += 1500
    const { running } = await api.status()
    if (running) {
      clearInterval(iv)
      isStarting = false
      btnStart.disabled = false
      applyRunning(true)
      showToast('服务已启动，打开微信视频号即可下载')
    } else if (waited > 45000) {
      clearInterval(iv)
      isStarting = false
      btnStart.disabled = false
      setBadge('off')
      phError.textContent = '服务未能在 45 秒内启动。请查看「下载目录」同级日志，或确认已在密码框授权。'
      phError.style.display = 'block'
    }
  }, 1500)
})

btnStop.addEventListener('click', async () => {
  btnStop.disabled = true
  showToast('正在停止服务…')
  await api.stop()
  btnStop.disabled = false
  applyRunning(false)
  showToast('服务已停止，系统代理已恢复')
})

btnDownloads.addEventListener('click', () => api.openDownloads())
btnBrowser.addEventListener('click', () => api.openBrowser())
btnRestore.addEventListener('click', async () => {
  showToast('正在恢复系统代理…')
  const res = await api.restoreProxy()
  showToast(res.ok ? '系统代理已恢复' : '恢复失败：' + (res.error || ''))
})

// 主进程定时推送状态
api.onStatus((running) => {
  if (isStarting) return // 启动中由轮询控制
  applyRunning(running)
})

refresh()
