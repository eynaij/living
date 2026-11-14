import { FilesetResolver, PoseLandmarker } from './vendor/tasks-vision/vision_bundle.mjs';

/* ------------------------------------------------------------------ *
 * 羽姿 · 羽毛球正手高远球姿势矫正
 * 本地 MediaPipe PoseLandmarker + 规则化评分
 * ------------------------------------------------------------------ */

const LM = {
  NOSE: 0,
  LSHOULDER: 11, RSHOULDER: 12,
  LELBOW: 13, RELBOW: 14,
  LWRIST: 15, RWRIST: 16,
  LHIP: 23, RHIP: 24,
  LKNEE: 25, RKNEE: 26,
  LANKLE: 27, RANKLE: 28,
};

const CONNECTIONS = [
  [11, 12], [11, 23], [12, 24], [23, 24],
  [23, 25], [25, 27], [24, 26], [26, 28],
];

const els = {
  stage: document.getElementById('stage'),
  video: document.getElementById('video'),
  overlay: document.getElementById('overlay'),
  stageEmpty: document.getElementById('stageEmpty'),
  stageDemo: document.getElementById('stageDemo'),
  stageLoading: document.getElementById('stageLoading'),
  stageError: document.getElementById('stageError'),
  loadingText: document.getElementById('loadingText'),
  errorText: document.getElementById('errorText'),
  startBtn: document.getElementById('startBtn'),
  retryBtn: document.getElementById('retryBtn'),
  demoBtn: document.getElementById('demoBtn'),
  demoStartBtn: document.getElementById('demoStartBtn'),
  stopBtn: document.getElementById('stopBtn'),
  switchBtn: document.getElementById('switchBtn'),
  stageTools: document.getElementById('stageTools'),
  phasePill: document.getElementById('phasePill'),
  bodyPill: document.getElementById('bodyPill'),
  liveElbow: document.getElementById('liveElbow'),
  liveKnee: document.getElementById('liveKnee'),
  liveHeight: document.getElementById('liveHeight'),
  liveState: document.getElementById('liveState'),
  ringValue: document.getElementById('ringValue'),
  scoreNum: document.getElementById('scoreNum'),
  scoreGrade: document.getElementById('scoreGrade'),
  resultBadge: document.getElementById('resultBadge'),
  resultTitle: document.getElementById('resultTitle'),
  tipsList: document.getElementById('tipsList'),
  metricsList: document.getElementById('metricsList'),
  breakdownBadge: document.getElementById('breakdownBadge'),
  repCount: document.getElementById('repCount'),
  repAvg: document.getElementById('repAvg'),
  repBest: document.getElementById('repBest'),
  historyList: document.getElementById('historyList'),
  resetBtn: document.getElementById('resetBtn'),
  helpBtn: document.getElementById('helpBtn'),
  helpModal: document.getElementById('helpModal'),
};

const ctx = els.overlay.getContext('2d');
const RING_LEN = 2 * Math.PI * 50;

const SENS_SPEED = { low: 4.2, mid: 2.7, high: 1.6 };

const state = {
  dominant: 'right',
  sensitivity: 'mid',
  speedStart: SENS_SPEED.mid,
  speedEnd: 0.75,
  facing: 'user',
  running: false,
  model: null,
  stream: null,
  rafId: null,
  lastVideoTime: -1,
  smooth: null,
  lastFrame: null,
  history: [],
  rep: null,
  afterRepAt: 0,
  cssW: 0,
  cssH: 0,
  session: [],
  lastLiveUpdate: 0,
  lastPoseAt: 0,
  noPoseSince: 0,
  mirror: true,
};

/* ----------------------------- utils ----------------------------- */

const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const dist = (a, b) => Math.hypot(a.x - b.x, a.y - b.y);
const mid = (a, b) => ({ x: (a.x + b.x) / 2, y: (a.y + b.y) / 2 });

function angleAt(a, b, c) {
  const v1x = a.x - b.x, v1y = a.y - b.y;
  const v2x = c.x - b.x, v2y = c.y - b.y;
  const m = Math.hypot(v1x, v1y) * Math.hypot(v2x, v2y);
  if (m < 1e-6) return null;
  const cos = clamp((v1x * v2x + v1y * v2y) / m, -1, 1);
  return (Math.acos(cos) * 180) / Math.PI;
}

/** 落在 [lo, hi] 区间得满分，越界按 soft 斜率衰减 */
function rangeScore(v, lo, hi, soft) {
  if (v == null || Number.isNaN(v)) return null;
  if (v >= lo && v <= hi) return 100;
  const d = v < lo ? lo - v : v - hi;
  return clamp(100 - d * soft, 0, 100);
}

function setPhase(text, live = false) {
  els.phasePill.innerHTML = `<i></i>${text}`;
  els.phasePill.classList.toggle('live', live);
}

function setLiveState(text, active = false) {
  els.liveState.textContent = text;
  els.liveState.classList.toggle('active', active);
}

/* --------------------------- camera ------------------------------ */

function isSecure() {
  return window.isSecureContext || location.hostname === 'localhost' || location.hostname === '127.0.0.1';
}

async function ensureCamera() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    throw new Error(isSecure()
      ? '当前浏览器不支持摄像头调用，请使用 Safari / Chrome 最新版。'
      : '浏览器要求在 HTTPS 或 localhost 下才能使用摄像头。请用 https 地址打开本页面，或在本机用 localhost 访问。');
  }
  if (state.stream) {
    state.stream.getTracks().forEach((t) => t.stop());
    state.stream = null;
  }
  state.stream = await navigator.mediaDevices.getUserMedia({
    audio: false,
    video: {
      facingMode: state.facing,
      width: { ideal: 1280 },
      height: { ideal: 720 },
      frameRate: { ideal: 30 },
    },
  });
  els.video.srcObject = state.stream;
  await new Promise((resolve) => {
    if (els.video.readyState >= 1) return resolve();
    els.video.onloadedmetadata = () => resolve();
  });
  await els.video.play().catch(() => {});
  state.mirror = state.facing === 'user';
  els.video.classList.toggle('mirrored', state.mirror);
  resizeCanvas();
}

async function ensureModel() {
  if (state.model) return state.model;
  const base = import.meta.url;
  const wasmRoot = new URL('./vendor/tasks-vision/wasm', base).href;
  const modelPath = new URL('./models/pose_landmarker_lite.task', base).href;

  const fileset = await FilesetResolver.forVisionTasks(wasmRoot);
  const options = {
    baseOptions: { modelAssetPath: modelPath, delegate: 'GPU' },
    runningMode: 'VIDEO',
    numPoses: 1,
    minPoseDetectionConfidence: 0.5,
    minPosePresenceConfidence: 0.5,
    minTrackingConfidence: 0.5,
  };
  try {
    state.model = await PoseLandmarker.createFromOptions(fileset, options);
  } catch (err) {
    options.baseOptions.delegate = 'CPU';
    state.model = await PoseLandmarker.createFromOptions(fileset, options);
  }
  return state.model;
}

function resizeCanvas() {
  const rect = els.stage.getBoundingClientRect();
  if (!rect.width || !rect.height) return;
  const dpr = Math.min(window.devicePixelRatio || 1, 2);
  state.cssW = rect.width;
  state.cssH = rect.height;
  els.overlay.width = Math.round(rect.width * dpr);
  els.overlay.height = Math.round(rect.height * dpr);
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
}

/* --------------------------- app start --------------------------- */

async function start() {
  els.stageEmpty.hidden = true;
  els.stageDemo.hidden = true;
  els.stageError.hidden = true;
  showLoading('正在请求摄像头权限…');
  try {
    await ensureCamera();
    showLoading('正在加载姿态识别引擎…');
    await ensureModel();
    hideLoading();
    els.stageTools.hidden = false;
    resetTracking();
    resizeCanvas();
    state.running = true;
    setPhase('准备就绪', true);
    setLiveState('等待挥拍');
    loop();
  } catch (err) {
    console.error(err);
    showError(err);
  }
}

function showLoading(text) {
  els.stageLoading.hidden = false;
  els.stageError.hidden = true;
  els.loadingText.textContent = text;
}
function hideLoading() {
  els.stageLoading.hidden = true;
}

function showError(err) {
  els.stageLoading.hidden = true;
  els.stageEmpty.hidden = true;
  els.stageError.hidden = false;
  const msg = String((err && err.message) || err || '未知错误');
  if (/Permission|denied|NotAllowed/i.test(msg)) {
    els.errorText.textContent = '摄像头权限被拒绝。请在浏览器的站点设置里允许摄像头，然后点重试。';
  } else if (/NotFound|Requested device/i.test(msg)) {
    els.errorText.textContent = '没有找到可用摄像头，请检查设备后重试。';
  } else if (/NotReadable|TrackStart/i.test(msg)) {
    els.errorText.textContent = '摄像头被其他应用占用，请关闭其他调用摄像头的应用后重试。';
  } else {
    els.errorText.textContent = msg;
  }
}

function stop() {
  state.running = false;
  if (state.rafId) cancelAnimationFrame(state.rafId);
  state.rafId = null;
  if (state.stream) {
    state.stream.getTracks().forEach((t) => t.stop());
    state.stream = null;
  }
  els.video.srcObject = null;
  els.stageTools.hidden = true;
  els.stageEmpty.hidden = false;
  els.stageDemo.hidden = true;
  els.stageError.hidden = true;
  ctx.clearRect(0, 0, state.cssW, state.cssH);
  setPhase('待机');
  setLiveState('等待挥拍');
  resetTracking();
}

function resetTracking() {
  state.lastVideoTime = -1;
  state.smooth = null;
  state.lastFrame = null;
  state.history = [];
  state.rep = null;
  state.noPoseSince = 0;
  els.bodyPill.textContent = '未识别到人体';
  els.bodyPill.classList.remove('warn');
}

function loop() {
  if (!state.running) return;
  const v = els.video;
  if (v.readyState >= 2 && v.videoWidth) {
    const now = performance.now();
    if (v.currentTime !== state.lastVideoTime) {
      state.lastVideoTime = v.currentTime;
      try {
        detect(v, now);
      } catch (err) {
        console.error('detect error', err);
      }
    }
  }
  state.rafId = requestAnimationFrame(loop);
}

function detect(video, now) {
  const result = state.model.detectForVideo(video, now);
  const lm = result && result.landmarks && result.landmarks[0];
  if (!lm) {
    onNoPose(now);
    return;
  }
  state.lastPoseAt = now;
  state.noPoseSince = 0;
  smoothLandmarks(lm);
  const aspect = video.videoWidth / video.videoHeight;
  const frame = buildFrame(aspect, now);
  if (!frame) return;
  computeSpeed(frame);
  drawSkeleton();
  updateLive(frame, now);
  feedRep(frame, now);
  state.lastFrame = frame;
}

function onNoPose(now) {
  if (!state.noPoseSince) state.noPoseSince = now;
  if (now - state.noPoseSince > 500) {
    els.bodyPill.textContent = '未识别到人体';
    els.bodyPill.classList.add('warn');
    if (state.rep) abortRep();
    setLiveState('请进入画面');
  }
}

function smoothLandmarks(lm) {
  if (!state.smooth) {
    state.smooth = lm.map((p) => ({ x: p.x, y: p.y, visibility: p.visibility ?? 0 }));
    return;
  }
  const a = 0.45;
  for (let i = 0; i < lm.length; i++) {
    const s = state.smooth[i];
    const p = lm[i];
    s.x += (p.x - s.x) * a;
    s.y += (p.y - s.y) * a;
    s.visibility = p.visibility ?? s.visibility;
  }
}

/* --------------------------- geometry ---------------------------- */

function buildFrame(aspect, t) {
  const s = state.smooth;
  if (!s) return null;
  const right = state.dominant === 'right';
  const shI = right ? LM.RSHOULDER : LM.LSHOULDER;
  const elI = right ? LM.RELBOW : LM.LELBOW;
  const wrI = right ? LM.RWRIST : LM.LWRIST;
  const hiI = right ? LM.RHIP : LM.LHIP;
  const knI = right ? LM.RKNEE : LM.LKNEE;
  const anI = right ? LM.RANKLE : LM.LANKLE;
  const oShI = right ? LM.LSHOULDER : LM.RSHOULDER;
  const oHiI = right ? LM.LHIP : LM.RHIP;
  const oKnI = right ? LM.LKNEE : LM.RKNEE;
  const oAnI = right ? LM.LANKLE : LM.RANKLE;

  const p = (i) => ({ x: s[i].x * aspect, y: s[i].y, v: s[i].visibility ?? 0 });
  const sh = p(shI), el = p(elI), wr = p(wrI), hi = p(hiI), kn = p(knI), an = p(anI);
  const oSh = p(oShI), oHi = p(oHiI), oKn = p(oKnI), oAn = p(oAnI);

  const mSh = mid(sh, oSh);
  const mHi = mid(hi, oHi);
  const L = dist(mSh, mHi);
  if (L < 0.02) return null;

  const vis = Math.min(sh.v, el.v, wr.v, hi.v, kn.v, oSh.v, oHi.v);
  const legVis = Math.min(kn.v, an.v, oKn.v, oAn.v, hi.v, oHi.v);

  return {
    t,
    L,
    wrist: wr,
    shoulder: sh,
    elbow: el,
    hip: hi,
    knee: kn,
    ankle: an,
    midShoulder: mSh,
    midHip: mHi,
    elbowAngle: angleAt(sh, el, wr),
    kneeAngle: angleAt(hi, kn, an),
    offKneeAngle: angleAt(oHi, oKn, oAn),
    lean: (Math.atan2(mSh.x - mHi.x, mHi.y - mSh.y) * 180) / Math.PI,
    above: (mSh.y - wr.y) / L,
    speed: 0,
    vis,
    legVis,
    ok: vis > 0.32,
    fullBody: legVis > 0.3,
    raw: s,
  };
}

function computeSpeed(frame) {
  const prev = state.lastFrame;
  if (!prev) {
    frame.speed = 0;
    return;
  }
  const dt = (frame.t - prev.t) / 1000;
  if (dt <= 0 || dt > 0.4) {
    frame.speed = 0;
    return;
  }
  const raw = dist(frame.wrist, prev.wrist) / dt / frame.L;
  frame.speed = prev.speed * 0.45 + raw * 0.55;
}

/* --------------------------- drawing ----------------------------- */

function drawSkeleton() {
  const s = state.smooth;
  const v = els.video;
  if (!s || !v.videoWidth) return;

  const cw = state.cssW, ch = state.cssH;
  const vw = v.videoWidth, vh = v.videoHeight;
  const scale = Math.max(cw / vw, ch / vh);
  const dw = vw * scale, dh = vh * scale;
  const ox = (cw - dw) / 2, oy = (ch - dh) / 2;
  const map = (p) => {
    let x = ox + p.x * dw;
    if (state.mirror) x = cw - x;
    return { x, y: oy + p.y * dh };
  };

  ctx.clearRect(0, 0, cw, ch);

  const right = state.dominant === 'right';
  const arm = right ? [LM.RSHOULDER, LM.RELBOW, LM.RWRIST] : [LM.LSHOULDER, LM.LELBOW, LM.LWRIST];
  const offArm = right ? [LM.LSHOULDER, LM.LELBOW, LM.LWRIST] : [LM.RSHOULDER, LM.RELBOW, LM.RWRIST];

  const seg = (a, b, color, width) => {
    const pa = map(s[a]), pb = map(s[b]);
    if ((s[a].visibility ?? 0) < 0.3 || (s[b].visibility ?? 0) < 0.3) return;
    ctx.strokeStyle = color;
    ctx.lineWidth = width;
    ctx.beginPath();
    ctx.moveTo(pa.x, pa.y);
    ctx.lineTo(pb.x, pb.y);
    ctx.stroke();
  };

  ctx.lineCap = 'round';
  ctx.shadowBlur = 10;
  ctx.shadowColor = 'rgba(216,255,74,0.35)';

  // 持拍臂
  for (let i = 0; i < arm.length - 1; i++) seg(arm[i], arm[i + 1], 'rgba(216,255,74,0.95)', 4);
  // 非持拍臂
  for (let i = 0; i < offArm.length - 1; i++) seg(offArm[i], offArm[i + 1], 'rgba(37,224,165,0.65)', 3);
  // 躯干与下肢
  ctx.shadowBlur = 0;
  for (const [a, b] of CONNECTIONS) {
    const isArm = (a === LM.LSHOULDER && b === LM.LELBOW) || (a === LM.RSHOULDER && b === LM.RELBOW);
    if (isArm) continue;
    seg(a, b, 'rgba(255,255,255,0.5)', 3);
  }

  // 关节
  const joints = [LM.LSHOULDER, LM.RSHOULDER, LM.LELBOW, LM.RELBOW, LM.LWRIST, LM.RWRIST, LM.LHIP, LM.RHIP, LM.LKNEE, LM.RKNEE, LM.LANKLE, LM.RANKLE];
  ctx.fillStyle = 'rgba(255,255,255,0.85)';
  for (const j of joints) {
    if ((s[j].visibility ?? 0) < 0.3) continue;
    const pt = map(s[j]);
    ctx.beginPath();
    ctx.arc(pt.x, pt.y, 3, 0, Math.PI * 2);
    ctx.fill();
  }

  // 持拍手腕强调
  const wristIdx = right ? LM.RWRIST : LM.LWRIST;
  if ((s[wristIdx].visibility ?? 0) > 0.3) {
    const pt = map(s[wristIdx]);
    ctx.fillStyle = '#d8ff4a';
    ctx.shadowBlur = 16;
    ctx.shadowColor = 'rgba(216,255,74,0.8)';
    ctx.beginPath();
    ctx.arc(pt.x, pt.y, 6, 0, Math.PI * 2);
    ctx.fill();
    ctx.shadowBlur = 0;
  }
}

/* --------------------------- live UI ----------------------------- */

function updateLive(frame, now) {
  if (now - state.lastLiveUpdate < 110) return;
  state.lastLiveUpdate = now;

  els.bodyPill.textContent = frame.fullBody ? '已锁定全身' : '请退后让全身入镜';
  els.bodyPill.classList.toggle('warn', !frame.fullBody);

  els.liveElbow.innerHTML = frame.elbowAngle == null ? '--<small>°</small>' : `${Math.round(frame.elbowAngle)}<small>°</small>`;
  els.liveKnee.innerHTML = frame.kneeAngle == null ? '--<small>°</small>' : `${Math.round(frame.kneeAngle)}<small>°</small>`;
  els.liveHeight.innerHTML = `${frame.above >= 0 ? '+' : ''}${frame.above.toFixed(2)}<small>L</small>`;

  if (state.rep) {
    setLiveState('记录挥拍中');
    els.liveState.classList.add('active');
    if (state.rep.struck) setPhase('击球完成', true);
    else setPhase('引拍中', true);
  } else {
    setLiveState(now - state.afterRepAt < 1200 ? '等待下一次' : '等待挥拍');
    els.liveState.classList.remove('active');
    if (now - state.afterRepAt > 1400) setPhase('准备就绪', true);
  }
}

/* --------------------------- rep logic --------------------------- */

function feedRep(frame, now) {
  state.history.push(frame);
  if (state.history.length > 26) state.history.shift();

  if (!state.rep) {
    if (frame.ok && frame.speed > state.speedStart && frame.above > -1.6 && now - state.afterRepAt > 450) {
      state.rep = {
        frames: state.history.slice(-20),
        t0: now,
        struck: frame.above > 0.05,
        slow: 0,
      };
    }
    return;
  }

  const rep = state.rep;
  rep.frames.push(frame);
  if (frame.above > 0.08) rep.struck = true;

  const dur = (now - rep.t0) / 1000;
  if (dur > 3.2) {
    abortRep();
    return;
  }
  if (rep.struck && dur > 0.35) {
    if (frame.speed < state.speedEnd) {
      rep.slow += 1;
      if (rep.slow >= 3) finishRep();
    } else {
      rep.slow = 0;
    }
  }
}

function abortRep() {
  state.rep = null;
  setPhase('准备就绪', true);
}

function finishRep() {
  const rep = state.rep;
  state.rep = null;
  state.afterRepAt = performance.now();
  if (!rep) return;
  const result = evaluate(rep.frames);
  if (!result) {
    setPhase('未识别完整挥拍');
    return;
  }
  state.session.push({ score: result.total, at: Date.now() });
  renderResult(result, state.session.length);
  renderSession();
  setPhase('挥拍完成', true);
}

/* --------------------------- scoring ----------------------------- */

function evaluate(frames) {
  const valid = frames.filter((f) => f.ok && f.elbowAngle != null);
  if (valid.length < 8) return null;

  let strike = valid[0];
  for (const f of valid) if (f.above > strike.above) strike = f;

  let back = null;
  for (const f of valid) {
    if (f.t < strike.t - 30 && (!back || f.wrist.y > back.wrist.y)) back = f;
  }
  if (!back) back = strike;

  let drop = 0;
  for (const f of valid) {
    if (f.t > strike.t + 40) drop = Math.max(drop, (f.wrist.y - strike.wrist.y) / f.L);
  }

  const leanAbs = Math.abs(strike.lean);
  const knee = back.kneeAngle != null ? back.kneeAngle : null;

  const defs = [
    {
      key: 'elbowBack', name: '引拍屈肘', note: '架拍时肘部约 70–110°',
      value: back.elbowAngle, display: fmtDeg(back.elbowAngle), target: '70–110°',
      score: rangeScore(back.elbowAngle, 70, 110, 1.1), weight: 18,
      tip: '引拍时先把肘抬到肩高、小臂后倒，别让手臂直着往后拉。',
    },
    {
      key: 'strikeHeight', name: '击球点高度', note: '手腕应明显高于肩线',
      value: strike.above, display: `${strike.above >= 0 ? '+' : ''}${strike.above.toFixed(2)}L`, target: '≥0.30L',
      score: rangeScore(strike.above, 0.3, 9, 90), weight: 24,
      tip: '击球点要抢高，在右肩前上方的最高点击球，别等球落到肩下再打。',
    },
    {
      key: 'extension', name: '击球伸展', note: '击球瞬间手臂接近伸直',
      value: strike.elbowAngle, display: fmtDeg(strike.elbowAngle), target: '≥150°',
      score: rangeScore(strike.elbowAngle, 150, 181, 1.8), weight: 20,
      tip: '击球瞬间大臂带小臂向上伸展，肘部接近伸直，别一直弯着肘打。',
    },
    {
      key: 'follow', name: '随挥幅度', note: '击球后顺势向下收拍',
      value: drop, display: `${drop.toFixed(2)}L`, target: '≥0.50L',
      score: rangeScore(drop, 0.5, 9, 110), weight: 18,
      tip: '击球后别急停，让球拍顺着惯性收到身体左下方，随挥越完整发力越顺。',
    },
    {
      key: 'knee', name: '屈膝蓄力', note: '准备与引拍保持微屈',
      value: knee, display: fmtDeg(knee), target: '120–158°',
      score: rangeScore(knee, 120, 158, 1.3), weight: 12,
      tip: '准备和引拍时膝盖保持微屈、重心稍降，靠蹬转发力而不是只用手臂。',
    },
    {
      key: 'lean', name: '躯干前倾', note: '击球时身体略前倾',
      value: leanAbs, display: fmtDeg(leanAbs), target: '0–30°',
      score: rangeScore(leanAbs, 0, 30, 2.6), weight: 8,
      tip: '击球时身体略向前压，别后仰，重心跟着动作从后脚转到前脚。',
    },
  ];

  let sum = 0, wsum = 0;
  for (const d of defs) {
    if (d.score == null) continue;
    sum += d.score * d.weight;
    wsum += d.weight;
  }
  if (!wsum) return null;

  const total = Math.round(sum / wsum);
  const grade = total >= 85 ? '优秀' : total >= 70 ? '良好' : total >= 55 ? '及格' : '待改进';

  const sorted = defs
    .filter((d) => d.score != null && d.score < 72)
    .sort((a, b) => a.score - b.score);

  const tips = [];
  if (sorted.length) {
    for (const d of sorted.slice(0, total >= 55 ? 2 : 3)) tips.push({ text: d.tip, good: false });
  }
  if (tips.length === 0) tips.push({ text: '六个维度都很到位，保持这个击球点高度和随挥节奏。', good: true });
  if (total >= 70 && tips.length < 2) {
    const best = [...defs].filter((d) => d.score != null).sort((a, b) => b.score - a.score)[0];
    if (best) tips.push({ text: `${best.name}完成得很好，继续稳住这一项。`, good: true });
  }

  return { total, grade, metrics: defs, tips, at: Date.now() };
}

function fmtDeg(v) {
  return v == null ? '--' : `${Math.round(v)}°`;
}

/* --------------------------- render ------------------------------ */

function renderResult(result, index) {
  const offset = RING_LEN * (1 - result.total / 100);
  els.ringValue.style.strokeDashoffset = String(offset);
  els.ringValue.style.opacity = '1';
  animateScore(result.total);
  els.scoreGrade.textContent = result.grade;
  els.scoreGrade.style.color = result.total >= 85 ? '#d8ff4a' : result.total >= 70 ? '#25e0a5' : result.total >= 55 ? '#ffb547' : '#ff6b6b';
  els.resultBadge.textContent = `第 ${index} 拍`;
  els.resultBadge.classList.remove('ghost');
  els.resultTitle.textContent =
    result.total >= 85 ? '动作相当标准，可以加难度了。'
      : result.total >= 70 ? '整体不错，把这几个细节再抠一下。'
        : result.total >= 55 ? '基本成型，重点改下面这两点。'
          : '动作框架还要调整，先慢速分解练。';

  els.tipsList.innerHTML = result.tips
    .map((t) => `<li class="tip ${t.good ? 'good' : ''}">${t.text}</li>`)
    .join('');

  renderMetrics(result.metrics, false);
  els.breakdownBadge.textContent = `第 ${index} 拍分解`;
  els.breakdownBadge.classList.remove('ghost');
}

function renderMetrics(metrics, demo) {
  els.metricsList.innerHTML = metrics
    .map((m) => {
      const score = m.score == null ? 0 : Math.round(m.score);
      const cls = score >= 78 ? '' : score >= 55 ? 'warn' : 'bad';
      return `<li class="metric">
        <div class="metric-top">
          <span class="metric-name">${m.name}<em>${m.note}</em></span>
          <span class="metric-val"><b>${m.display}</b> · ${score}分</span>
        </div>
        <div class="metric-bar"><span class="metric-fill ${cls}" style="width:${score}%"></span></div>
      </li>`;
    })
    .join('');
}

let scoreAnim = 0;
function animateScore(target) {
  cancelAnimationFrame(scoreAnim);
  const start = performance.now();
  const from = Number(els.scoreNum.textContent) || 0;
  const dur = 600;
  const tick = (now) => {
    const p = clamp((now - start) / dur, 0, 1);
    const eased = 1 - Math.pow(1 - p, 3);
    els.scoreNum.textContent = String(Math.round(from + (target - from) * eased));
    if (p < 1) scoreAnim = requestAnimationFrame(tick);
  };
  scoreAnim = requestAnimationFrame(tick);
}

function renderSession() {
  const list = state.session;
  els.repCount.textContent = String(list.length);
  if (!list.length) {
    els.repAvg.textContent = '--';
    els.repBest.textContent = '--';
    els.historyList.innerHTML = '<span class="history-empty">评分会按顺序出现在这里</span>';
    return;
  }
  const scores = list.map((r) => r.score);
  const avg = Math.round(scores.reduce((a, b) => a + b, 0) / scores.length);
  const best = Math.max(...scores);
  els.repAvg.textContent = String(avg);
  els.repBest.textContent = String(best);
  const recent = list.slice(-12).reverse();
  els.historyList.innerHTML = recent
    .map((r) => {
      const cls = r.score >= 85 ? 'good' : r.score >= 55 ? 'mid' : 'low';
      return `<span class="history-chip ${cls}">${r.score}</span>`;
    })
    .join('');
}

/* --------------------------- demo -------------------------------- */

function renderDemo() {
  const metrics = [
    { name: '引拍屈肘', note: '架拍时肘部约 70–110°', display: '96°', score: 100 },
    { name: '击球点高度', note: '手腕应明显高于肩线', display: '+0.31L', score: 92 },
    { name: '击球伸展', note: '击球瞬间手臂接近伸直', display: '141°', score: 84 },
    { name: '随挥幅度', note: '击球后顺势向下收拍', display: '0.28L', score: 48 },
    { name: '屈膝蓄力', note: '准备与引拍保持微屈', display: '132°', score: 100 },
    { name: '躯干前倾', note: '击球时身体略前倾', display: '41°', score: 72 },
  ];
  const result = {
    total: 81,
    grade: '良好',
    metrics,
    tips: [
      { text: '击球后别急停，让球拍顺着惯性收到身体左下方，随挥越完整发力越顺。', good: false },
      { text: '击球时身体略向前压，别后仰，重心跟着动作从后脚转到前脚。', good: false },
    ],
  };
  els.stageEmpty.hidden = true;
  els.stageDemo.hidden = false;
  els.stageTools.hidden = true;
  setPhase('示例模式');
  els.bodyPill.textContent = '示例数据';
  els.bodyPill.classList.remove('warn');
  els.resultBadge.textContent = '示例报告';
  els.resultTitle.textContent = '这是一份示例分析，开启摄像头后请以真实识别结果为准。';
  renderResult(result, 1);
  els.resultBadge.textContent = '示例报告';
  els.breakdownBadge.textContent = '示例数据';
  els.tipsList.innerHTML = result.tips.map((t) => `<li class="tip">${t.text}</li>`).join('');
}

/* --------------------------- events ------------------------------ */

els.startBtn.addEventListener('click', start);
els.retryBtn.addEventListener('click', start);
els.stopBtn.addEventListener('click', stop);
els.demoBtn.addEventListener('click', renderDemo);
els.demoStartBtn.addEventListener('click', start);

els.switchBtn.addEventListener('click', async () => {
  state.facing = state.facing === 'user' ? 'environment' : 'user';
  try {
    showLoading('正在切换镜头…');
    await ensureCamera();
    hideLoading();
    resetTracking();
    state.lastVideoTime = -1;
    els.video.classList.toggle('mirrored', state.mirror);
  } catch (err) {
    showError(err);
  }
});

els.resetBtn.addEventListener('click', () => {
  state.session = [];
  renderSession();
  els.scoreNum.textContent = '--';
  els.scoreGrade.textContent = '暂无';
  els.scoreGrade.style.color = '';
  els.ringValue.style.strokeDashoffset = String(RING_LEN);
  els.ringValue.style.opacity = '0';
  els.resultBadge.textContent = '等待第一次挥拍';
  els.resultBadge.classList.add('ghost');
  els.resultTitle.textContent = '挥拍后自动给出六维分析';
  els.tipsList.innerHTML = '<li class="tip muted">把手机架好，做一次完整的正手高远球挥拍。</li>';
  els.metricsList.innerHTML = '<li class="metric empty">暂未采集到动作数据</li>';
  els.breakdownBadge.textContent = '最近一次';
  els.breakdownBadge.classList.add('ghost');
});

document.querySelectorAll('.segmented').forEach((group) => {
  group.addEventListener('click', (e) => {
    const btn = e.target.closest('button');
    if (!btn) return;
    const key = group.dataset.key;
    group.querySelectorAll('button').forEach((b) => b.classList.toggle('active', b === btn));
    if (key === 'dominant') {
      state.dominant = btn.dataset.value;
      resetTracking();
    } else if (key === 'sensitivity') {
      state.sensitivity = btn.dataset.value;
      state.speedStart = SENS_SPEED[state.sensitivity];
    }
  });
});

els.helpBtn.addEventListener('click', () => {
  els.helpModal.hidden = false;
});
els.helpModal.addEventListener('click', (e) => {
  if (e.target.closest('[data-close]')) els.helpModal.hidden = true;
});

window.addEventListener('resize', resizeCanvas);
if ('ResizeObserver' in window) {
  new ResizeObserver(() => resizeCanvas()).observe(els.stage);
}
document.addEventListener('visibilitychange', () => {
  if (document.hidden && state.rep) abortRep();
});

els.ringValue.style.strokeDashoffset = String(RING_LEN);
renderSession();
