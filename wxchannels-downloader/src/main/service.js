const { app, shell } = require('electron')
const path = require('path')
const fs = require('fs')
const http = require('http')
const os = require('os')
const { execFile } = require('child_process')

const API_URL = 'http://127.0.0.1:2022/'
const ARCH = process.arch === 'arm64' ? 'arm64' : 'x86_64'

function resourceBase() {
  // 打包后 extraResources 位于 process.resourcesPath；开发时位于项目根
  return app.isPackaged ? process.resourcesPath : path.join(__dirname, '../..')
}

function binPath() {
  return path.join(resourceBase(), 'bin', ARCH, 'wx_video_download')
}

function defaultConfigPath() {
  return app.isPackaged
    ? path.join(process.resourcesPath, 'config.yaml')
    : path.join(resourceBase(), 'resources', 'config.yaml')
}

function workDir() {
  return app.getPath('userData')
}

function ensureWorkDir() {
  const dir = workDir()
  fs.mkdirSync(dir, { recursive: true })
  const cfg = path.join(dir, 'config.yaml')
  if (!fs.existsSync(cfg)) {
    try { fs.copyFileSync(defaultConfigPath(), cfg) } catch (e) { /* 忽略，二进制会用内置默认 */ }
  }
  return dir
}

/** 检测核心服务是否在运行（2022 端口可访问） */
function checkStatus() {
  return new Promise((resolve) => {
    const req = http.get(API_URL, { timeout: 1500 }, (res) => {
      res.destroy()
      resolve(res.statusCode === 200)
    })
    req.on('error', () => resolve(false))
    req.on('timeout', () => { req.destroy(); resolve(false) })
  })
}

/** 以管理员权限启动核心二进制（首次会安装证书、设置系统代理） */
function start() {
  return new Promise(async (resolve, reject) => {
    if (await checkStatus()) return resolve(true)
    const dir = ensureWorkDir()
    const bin = binPath()
    if (!fs.existsSync(bin)) return reject(new Error('未找到核心程序: ' + bin))
    try { fs.chmodSync(bin, 0o755) } catch (e) { /* 忽略 */ }

    const logPath = path.join(dir, 'service.log')
    const launchSh = path.join(dir, 'launch.sh')
    const script = `#!/bin/bash
cd "${dir}"
nohup "${bin}" >> "${logPath}" 2>&1 &
`
    fs.writeFileSync(launchSh, script)
    fs.chmodSync(launchSh, 0o755)

    // 用 osascript 弹系统密码框提权运行；quoted form 保证含空格/中文的路径安全
    const apple =
      `do shell script ("/bin/sh " & quoted form of ${JSON.stringify(launchSh)}) with administrator privileges`
    execFile('osascript', ['-e', apple], { timeout: 120000 }, (err, _stdout, stderr) => {
      if (err) {
        // 用户取消授权也会走这里
        return reject(new Error((stderr && stderr.trim()) || err.message))
      }
      resolve(true)
    })
  })
}

/** 停止核心服务（优雅停止，二进制会自动恢复系统代理） */
function stop() {
  return new Promise((resolve) => {
    execFile('pkill', ['-f', 'wx_video_download'], () => {
      let tries = 0
      const iv = setInterval(async () => {
        tries++
        const running = await checkStatus()
        if (!running || tries > 12) {
          clearInterval(iv)
          if (running) {
            execFile('pkill', ['-9', '-f', 'wx_video_download'], () => resolve())
          } else {
            resolve()
          }
        }
      }, 800)
    })
  })
}

/** 兜底：手动恢复系统代理（关闭所有网络服务的 HTTP/HTTPS 代理） */
function restoreProxy() {
  return new Promise((resolve, reject) => {
    execFile('networksetup', ['-listallnetworkservices'], (err, stdout) => {
      if (err) return reject(err)
      const services = stdout.split('\n').slice(1)
        .map(s => s.trim())
        .filter(s => s && !s.startsWith('*'))
      const parts = []
      for (const svc of services) {
        parts.push(`networksetup -setwebproxystate ${JSON.stringify(svc)} off`)
        parts.push(`networksetup -setsecurewebproxystate ${JSON.stringify(svc)} off`)
      }
      const cmds = parts.join('; ')
      const apple = `do shell script ${JSON.stringify(cmds)} with administrator privileges`
      execFile('osascript', ['-e', apple], { timeout: 60000 }, (e2, _o, se) => {
        if (e2) return reject(new Error((se && se.trim()) || e2.message))
        resolve()
      })
    })
  })
}

function openDownloads() {
  return shell.openPath(path.join(os.homedir(), 'Downloads'))
}

module.exports = { checkStatus, start, stop, restoreProxy, openDownloads, ARCH }
