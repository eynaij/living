// 轻影桌面版 - 播放页逻辑
// 注意：api / $ 由 list.js 在全局词法作用域声明，本脚本在其后加载，直接复用，勿重复声明。
const SPEEDS = [0.5, 0.75, 1, 1.25, 1.5, 2];
const BOOST_SPEED = 2.5;
const LONG_PRESS_MS = 350;
const DOUBLE_TAP_MS = 300;

const LOCK_SVG =
  '<svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px"><rect x="5" y="11" width="14" height="9" rx="1.5"/><path d="M8 11V8a4 4 0 0 1 8 0v3"/></svg>';

const Player = (window.Player = {
  el: null,
  index: 0,
  boosting: false,
  rewinding: false,
  scrubbing: false,
  frameMs: 1000 / 24,
  hideTimer: null,
  lastTapAt: 0,
  pendingTap: null,
  downX: 0, downY: 0, downAt: 0,
  rightZone: false, leftZone: false,
  fpsFrames: 0, fpsLast: 0,
  saveTimer: null,

  open(idx) {
    const videos = window.App.videos;
    if (!videos.length) return;
    this.index = Math.max(0, Math.min(idx, videos.length - 1));
    $('#listView').classList.remove('active');
    $('#playerView').classList.add('active');
    this.el = $('#video');
    this.bindOnce();
    this.loadVideo(true);
    this.renderPlaylist();
    this.applyPersisted();
    this.showControls();
  },

  back() {
    this.pause();
    $('#playerView').classList.remove('active');
    $('#listView').classList.add('active');
    try { if (document.fullscreenElement) document.exitFullscreen(); } catch (e) {}
  },

  bindOnce() {
    if (this._bound) return;
    this._bound = true;
    const v = this.el;
    const root = $('#playerRoot');

    v.addEventListener('loadedmetadata', () => this.onMeta());
    v.addEventListener('timeupdate', () => this.onTime());
    v.addEventListener('play', () => this.onPlay());
    v.addEventListener('pause', () => this.onPause());
    v.addEventListener('ended', () => this.onEnded());
    v.addEventListener('error', () => this.onError());

    // RVFC 估算帧率，用于逐帧步进
    if ('requestVideoFrameCallback' in HTMLVideoElement.prototype) {
      const cb = (now, meta) => {
        if (meta && meta.mediaTime > 0) {
          // 用相邻帧的 mediaFramesReceived 估算
        }
        if (this.fpsFrames === 0) this.fpsLast = now;
        this.fpsFrames++;
        if (this.fpsFrames >= 24) {
          const fps = (this.fpsFrames * 1000) / (now - this.fpsLast);
          if (fps > 10 && fps < 120) this.frameMs = 1000 / fps;
          this.fpsFrames = 0; this.fpsLast = now;
        }
        v.requestVideoFrameCallback(cb);
      };
      v.requestVideoFrameCallback(cb);
    }

    // 控制按钮
    $('#playBtn').addEventListener('click', () => this.togglePlay());
    $('#prevBtn').addEventListener('click', () => this.playAt(this.index - 1));
    $('#nextBtn').addEventListener('click', () => this.playAt(this.index + 1));
    $('#back10').addEventListener('click', () => this.jump(-10));
    $('#forward10').addEventListener('click', () => this.jump(10));
    $('#framePrev').addEventListener('click', () => this.stepFrame(-1));
    $('#frameNext').addEventListener('click', () => this.stepFrame(1));
    $('#backBtn').addEventListener('click', () => this.back());
    $('#fsBtn').addEventListener('click', () => this.toggleFullscreen());
    $('#captureBtn').addEventListener('click', () => this.capture());
    $('#pinBtn').addEventListener('click', () => this.togglePin());
    $('#playlistClose').addEventListener('click', () => this.hidePlaylist());

    // 倍速按钮：单击循环，长按切换锁定
    const sb = $('#speedBtn');
    sb.addEventListener('click', () => this.cycleSpeed());
    let lpTimer = null;
    sb.addEventListener('pointerdown', () => { lpTimer = setTimeout(() => this.toggleSpeedLock(), LONG_PRESS_MS); });
    sb.addEventListener('pointerup', () => { clearTimeout(lpTimer); lpTimer = null; });
    sb.addEventListener('pointerleave', () => { clearTimeout(lpTimer); lpTimer = null; });

    // 进度条
    const bar = $('#seekBar');
    bar.addEventListener('input', () => {
      const dur = v.duration || 0;
      const t = (bar.value / 100) * dur;
      $('#curTime').textContent = window.fmt.duration(t);
    });
    bar.addEventListener('change', () => {
      const dur = v.duration || 0;
      v.currentTime = (bar.value / 100) * dur;
      this.showControls();
    });

    // 全屏变化
    document.addEventListener('fullscreenchange', () => {
      $('#fsBtn').textContent = document.fullscreenElement ? '⛶ 退出全屏' : '⛶ 全屏';
    });

    // 鼠标移动显示控件
    root.addEventListener('mousemove', () => this.showControls());
    root.addEventListener('mouseleave', () => { /* keep */ });

    // 手势
    root.addEventListener('pointerdown', (e) => this.onDown(e));
    root.addEventListener('pointermove', (e) => this.onMove(e));
    root.addEventListener('pointerup', (e) => this.onUp(e));
    root.addEventListener('pointercancel', (e) => this.onUp(e));

    // 键盘
    document.addEventListener('keydown', (e) => this.onKey(e));

    // 关闭/离开前保存进度
    window.addEventListener('beforeunload', () => this.saveProgressNow());
  },

  current() { return window.App.videos[this.index]; },

  async loadVideo(resume) {
    const v = this.el;
    const item = this.current();
    if (!item) return;
    $('#playerTitle').textContent = item.name;
    v.src = window.fileUrl(item.path);
    try { v.load(); } catch (e) {}
    v.play().catch(() => {});

    // 加入最近播放
    const pos = resume ? await api.getProgress(item.path) : 0;
    await api.addRecent({ path: item.path, name: item.name, position: pos || 0, duration: 0, date: Date.now() });
    this._resumePos = pos || 0;
    this.startSaveTimer();
  },

  onMeta() {
    const v = this.el;
    const dur = v.duration || 0;
    $('#durTime').textContent = window.fmt.duration(dur);
    if (this._resumePos > 0 && this._resumePos < dur - 3) {
      try { v.currentTime = this._resumePos; } catch (e) {}
    }
    this._resumePos = 0;
    this.updateSpeedButton();
  },

  onTime() {
    const v = this.el;
    if (this.scrubbing) return;
    const dur = v.duration || 0;
    const pos = v.currentTime || 0;
    $('#curTime').textContent = window.fmt.duration(pos);
    if (dur > 0) $('#seekBar').value = (pos / dur) * 100;
  },

  onPlay() {
    $('#playBtn').textContent = '❚❚';
    this.showControls();
  },
  onPause() {
    $('#playBtn').textContent = '▶';
    this.showControls();
    this.saveProgressNow();
  },
  onEnded() {
    this.saveProgressNow();
    if (this.index < window.App.videos.length - 1) this.playAt(this.index + 1);
  },
  onError() {
    window.showToast('无法播放该视频（格式不支持）');
  },

  playAt(idx) {
    const n = window.App.videos.length;
    if (idx < 0 || idx >= n || idx === this.index) return;
    this.saveProgressNow();
    this.index = idx;
    this.loadVideo(false);
    this.renderPlaylist();
  },

  togglePlay() {
    const v = this.el;
    if (!v) return;
    if (v.paused) v.play().catch(() => {}); else v.pause();
  },

  jump(sec) {
    const v = this.el;
    if (!v) return;
    const dur = v.duration || 0;
    v.currentTime = Math.max(0, Math.min(dur, v.currentTime + sec));
    this.flashBadge((sec > 0 ? '▶ +' : '◀ −') + Math.abs(sec) + 's');
    this.showControls();
  },

  stepFrame(dir) {
    const v = this.el;
    if (!v) return;
    this.pause();
    const dur = v.duration || 0;
    v.currentTime = Math.max(0, Math.min(dur, v.currentTime + dir * this.frameMs / 1000));
    this.flashBadge(dir > 0 ? '下一帧' : '上一帧');
    this.showControls();
  },

  pause() { if (this.el && !this.el.paused) this.el.pause(); },

  // ---------------- 倍速 ----------------
  applySpeed(val) {
    const v = this.el;
    if (!v) return;
    try { v.playbackRate = val; } catch (e) {}
  },
  speedText(val) {
    let t = String(val);
    if (t.endsWith('.0')) t = t.slice(0, -2);
    return t + 'x';
  },
  async cycleSpeed() {
    const st = window.App.state;
    if (st.speedLocked) { window.showToast('倍速已锁定，长按倍速按钮解锁'); this.showControls(); return; }
    let best = Infinity, idx = 0;
    SPEEDS.forEach((s, i) => { const d = Math.abs(s - (st.speed || 1)); if (d < best) { best = d; idx = i; } });
    st.speed = SPEEDS[(idx + 1) % SPEEDS.length];
    this.applySpeed(st.speed);
    await api.saveSettings({ speed: st.speed });
    this.updateSpeedButton();
    this.flashBadge('倍速 ' + this.speedText(st.speed));
    this.showControls();
  },
  async toggleSpeedLock() {
    const st = window.App.state;
    st.speedLocked = !st.speedLocked;
    await api.saveSettings({ speedLocked: st.speedLocked });
    this.updateSpeedButton();
    this.flashBadge(st.speedLocked ? '倍速已锁定' : '倍速已解锁');
    this.showControls();
  },
  updateSpeedButton() {
    const st = window.App.state;
    const locked = !!st.speedLocked;
    $('#speedBtn').innerHTML = (locked ? '<span class="lock-ico">' + LOCK_SVG + '</span> ' : '') + this.speedText(st.speed || 1);
  },
  applyPersisted() {
    const st = window.App.state;
    this.applySpeed(st.speed || 1);
    if (this.el) this.el.volume = typeof st.volume === 'number' ? st.volume : 1;
    this.updateSpeedButton();
  },

  // ---------------- 截图 ----------------
  async capture() {
    const v = this.el;
    if (!v || !v.videoWidth) { window.showToast('暂无可截取画面'); return; }
    const canvas = document.createElement('canvas');
    canvas.width = v.videoWidth; canvas.height = v.videoHeight;
    try {
      canvas.getContext('2d').drawImage(v, 0, 0);
      const dataUrl = canvas.toDataURL('image/png');
      const name = (this.current()?.name || 'video').replace(/\.[^.]+$/, '') + '-' + Date.now() + '.png';
      const saved = await api.captureScreenshot(dataUrl, name);
      window.showToast(saved ? '截图已保存' : '已取消');
    } catch (e) {
      window.showToast('截图失败');
    }
    this.showControls();
  },

  // ---------------- 置顶 ----------------
  async togglePin() {
    const on = !(await api.isAlwaysOnTop());
    await api.setAlwaysOnTop(on);
    $('#pinBtn').classList.toggle('on', on);
    window.showToast(on ? '窗口已置顶' : '已取消置顶');
    this.showControls();
  },

  // ---------------- 全屏 ----------------
  toggleFullscreen() {
    const root = $('#playerRoot');
    if (!document.fullscreenElement) root.requestFullscreen().catch(() => {});
    else document.exitFullscreen().catch(() => {});
  },

  // ---------------- 播放列表 ----------------
  renderPlaylist() {
    const box = $('#playlist');
    const list = window.App.videos;
    $('#playlistTitle').textContent = `播放列表 ${this.index + 1}/${list.length}`;
    box.innerHTML = list.map((v, i) =>
      `<div class="pl-item ${i === this.index ? 'cur' : ''}" data-i="${i}">
        <span class="idx">${i + 1}</span><span class="pname">${escapeHtml(v.name)}</span>
      </div>`
    ).join('');
    box.querySelectorAll('.pl-item').forEach((el) => {
      el.addEventListener('click', () => { this.hidePlaylist(); this.playAt(parseInt(el.dataset.i, 10)); });
    });
  },
  showPlaylist() { $('#playlistPanel').classList.remove('hidden'); this.showControls(); },
  hidePlaylist() { $('#playlistPanel').classList.add('hidden'); },

  // ---------------- 控件显隐 ----------------
  showControls() {
    const root = $('#playerRoot');
    root.classList.remove('hide-ctrl');
    clearTimeout(this.hideTimer);
    if (this.el && this.el.paused) return;
    this.hideTimer = setTimeout(() => {
      if (this.el && this.el.played && !this.boosting && !this.scrubbing) root.classList.add('hide-ctrl');
    }, 2600);
  },

  flashBadge(text) {
    const b = $('#badge');
    b.textContent = text; b.classList.remove('hidden');
    clearTimeout(this._badgeT);
    this._badgeT = setTimeout(() => b.classList.add('hidden'), 700);
  },

  // ---------------- 进度保存 ----------------
  startSaveTimer() {
    clearInterval(this.saveTimer);
    this.saveTimer = setInterval(() => this.saveProgressNow(), 5000);
  },
  saveProgressNow() {
    const v = this.el;
    const item = this.current();
    if (!v || !item) return;
    api.saveProgress(item.path, Math.floor(v.currentTime || 0), Math.floor(v.duration || 0));
  },

  // ---------------- 手势 ----------------
  onDown(e) {
    if (e.target.closest('button, input, #playlistPanel')) return;
    this.downX = e.clientX; this.downY = e.clientY; this.downAt = performance.now();
    const root = $('#playerRoot');
    const w = root.clientWidth;
    const x = e.clientX - root.getBoundingClientRect().left;
    this.leftZone = x < w * 0.4;
    this.rightZone = x > w * 0.6;
    if (this.rightZone) this._boostT = setTimeout(() => this.startBoost(), LONG_PRESS_MS);
    else if (this.leftZone) this._rewindT = setTimeout(() => this.startRewind(), LONG_PRESS_MS);
  },
  onMove(e) {
    if (!this.downAt) return;
    const dx = e.clientX - this.downX, dy = e.clientY - this.downY;
    if (!this.scrubbing && Math.abs(dx) > 24 && Math.abs(dx) > Math.abs(dy) && this.el) {
      this.scrubbing = true;
      this.cancelLongPress();
      this.showControls();
    }
    if (this.scrubbing) {
      const root = $('#playerRoot');
      const w = root.clientWidth;
      const dur = this.el.duration || 0;
      const full = Math.max(1000, Math.min(dur / 5, 120000));
      const delta = (dx / w) * full;
      const start = this._scrubStart ?? (this._scrubStart = this.el.currentTime);
      const target = Math.max(0, Math.min(dur, start + delta));
      $('#seekBar').value = dur ? (target / dur) * 100 : 0;
      $('#curTime').textContent = window.fmt.duration(target);
      this._scrubTarget = target;
    } else if (Math.abs(dx) > 36 || Math.abs(dy) > 36) {
      this.cancelLongPress();
    }
  },
  onUp(e) {
    const acted = this.boosting || this.rewinding || this.scrubbing;
    this.cancelLongPress();
    this.stopBoost(); this.stopRewind();
    if (this.scrubbing) {
      if (this.el && this._scrubTarget != null) this.el.currentTime = this._scrubTarget;
      this.scrubbing = false; this._scrubStart = null; this._scrubTarget = null;
    }
    const quick = performance.now() - this.downAt < 280;
    this.downAt = 0;
    if (acted) return;
    if (!quick) return;
    // 双击判定
    const now = performance.now();
    if (now - this.lastTapAt < DOUBLE_TAP_MS) {
      this.lastTapAt = 0;
      clearTimeout(this.pendingTap);
      this.togglePlay();
      return;
    }
    this.lastTapAt = now;
    this.pendingTap = setTimeout(() => {
      this.pendingTap = null;
      if (this.leftZone) this.showPlaylist();
      else this.showControls();
    }, DOUBLE_TAP_MS);
  },
  cancelLongPress() {
    clearTimeout(this._boostT); clearTimeout(this._rewindT);
  },
  startBoost() {
    const v = this.el;
    if (!v || v.paused) return;
    this.boosting = true;
    this.applySpeed(BOOST_SPEED);
    this.flashBadge('▶▶ ' + BOOST_SPEED + 'x 快进');
    $('#playerRoot').classList.add('hide-ctrl');
  },
  stopBoost() {
    if (!this.boosting) return;
    this.boosting = false;
    this.applySpeed(window.App.state.speed || 1);
    $('#playerRoot').classList.remove('hide-ctrl');
    this.showControls();
  },
  startRewind() {
    this.rewinding = true;
    this.pause();
    this._rewindInterval = setInterval(() => {
      if (this.el) {
        const dur = this.el.duration || 0;
        this.el.currentTime = Math.max(0, this.el.currentTime - 1.2);
        $('#curTime').textContent = window.fmt.duration(this.el.currentTime);
        if (dur) $('#seekBar').value = (this.el.currentTime / dur) * 100;
      }
    }, 130);
    this.flashBadge('◀◀ 快退中');
    $('#playerRoot').classList.add('hide-ctrl');
  },
  stopRewind() {
    if (!this.rewinding) return;
    this.rewinding = false;
    clearInterval(this._rewindInterval);
    $('#playerRoot').classList.remove('hide-ctrl');
    this.showControls();
  },

  // ---------------- 键盘 ----------------
  onKey(e) {
    if (!$('#playerView').classList.contains('active')) return;
    const tag = (e.target.tagName || '').toLowerCase();
    if (tag === 'input') return;
    switch (e.key) {
      case ' ': case 'k': e.preventDefault(); this.togglePlay(); break;
      case 'ArrowLeft': e.preventDefault(); this.jump(-10); break;
      case 'ArrowRight': e.preventDefault(); this.jump(10); break;
      case 'ArrowUp': e.preventDefault(); this.changeVolume(0.1); break;
      case 'ArrowDown': e.preventDefault(); this.changeVolume(-0.1); break;
      case 'f': case 'F': this.toggleFullscreen(); break;
      case 's': case 'S': this.capture(); break;
      case 'm': case 'M': this.muteToggle(); break;
      case '[': this.stepFrame(-1); break;
      case ']': this.stepFrame(1); break;
      case 'Escape':
        if (document.fullscreenElement) { document.exitFullscreen().catch(() => {}); }
        else if (!$('#playlistPanel').classList.contains('hidden')) { this.hidePlaylist(); }
        else { this.back(); }
        break;
    }
  },
  changeVolume(d) {
    const v = this.el; if (!v) return;
    v.volume = Math.max(0, Math.min(1, v.volume + d));
    api.saveSettings({ volume: v.volume });
    this.flashBadge('音量 ' + Math.round(v.volume * 100) + '%');
    this.showControls();
  },
  muteToggle() {
    const v = this.el; if (!v) return;
    v.muted = !v.muted;
    this.flashBadge(v.muted ? '已静音' : '取消静音');
    this.showControls();
  }
});

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
