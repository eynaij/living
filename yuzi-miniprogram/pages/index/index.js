const api = require('../../utils/api');
const store = require('../../utils/store');
const config = require('../../config');

const STROKE_META = {
  clear: {
    name: '正手高远球',
    short: '高远球',
    desc: '高点击球，练架拍与随挥',
    hint: '架拍 → 转体引拍 → 高点击球 → 顺势随挥',
  },
  lift: {
    name: '正手挑球',
    short: '正手挑球',
    desc: '低点向上，练降重心与送拍',
    hint: '降重心 → 引拍 → 低点向前上方挑球 → 向上随挥',
  },
  backhandLift: {
    name: '反手挑球',
    short: '反手挑球',
    desc: '反手低点，练展臂与随挥',
    hint: '反手握拍 → 降重心 → 低点向上挑 → 向前上方随挥',
  },
};

const STROKE_LIST = ['clear', 'lift', 'backhandLift'].map((key) => ({
  key,
  name: STROKE_META[key].short,
  desc: STROKE_META[key].desc,
}));

function fmtDate(ts) {
  const d = new Date(ts);
  const m = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${m}-${day}`;
}

Page({
  data: {
    strokes: STROKE_LIST,
    stroke: 'clear',
    strokeName: '正手高远球',
    hint: STROKE_META.clear.hint,
    videoPath: '',
    duration: 0,
    analyzing: false,
    progress: 0,
    progressText: '',
    recent: [],
    demoOn: false,
  },

  onShow() {
    const settings = store.getSettings();
    const stroke = STROKE_META[settings.stroke] ? settings.stroke : 'clear';
    this.setData({
      stroke,
      strokeName: STROKE_META[stroke].name,
      hint: STROKE_META[stroke].hint,
      demoOn: !!(settings.demo || config.DEMO),
      recent: this.buildRecent(),
    });
  },

  buildRecent() {
    return store
      .getRecords()
      .slice(0, 3)
      .map((r) => ({
        t: r.t,
        date: fmtDate(r.t),
        score: r.score,
        grade: store.gradeOf(r.score),
        color: store.gradeColor(r.score),
        stroke: r.stroke || '挥拍',
      }));
  },

  selectStroke(e) {
    const { key } = e.currentTarget.dataset;
    if (!STROKE_META[key]) return;
    store.saveSettings({ stroke: key });
    this.setData({
      stroke: key,
      strokeName: STROKE_META[key].name,
      hint: STROKE_META[key].hint,
    });
  },

  shoot() {
    this.chooseVideo('camera');
  },

  pick() {
    this.chooseVideo('album');
  },

  chooseVideo(source) {
    wx.chooseMedia({
      count: 1,
      mediaType: ['video'],
      sourceType: source === 'camera' ? ['camera'] : ['album'],
      maxDuration: 15,
      camera: 'back',
      success: (res) => {
        const file = res.tempFiles && res.tempFiles[0];
        if (!file) return;
        if (file.size > 60 * 1024 * 1024) {
          wx.showToast({ title: '视频过大，请截取 5–8 秒', icon: 'none' });
          return;
        }
        this.setData({
          videoPath: file.tempFilePath,
          duration: Math.max(0, Math.round(file.duration || 0)),
        });
      },
      fail: (err) => {
        const msg = (err && err.errMsg) || '';
        if (msg.indexOf('cancel') === -1) {
          wx.showToast({ title: '没有拿到视频，请重试', icon: 'none' });
        }
      },
    });
  },

  clearVideo() {
    this.setData({ videoPath: '', duration: 0 });
  },

  analyze() {
    const { videoPath, stroke, analyzing, duration } = this.data;
    if (!videoPath || analyzing) return;
    if (duration > 0 && duration < 1) {
      wx.showToast({ title: '视频太短，至少录一次完整挥拍', icon: 'none' });
      return;
    }

    const settings = store.getSettings();
    this.setData({ analyzing: true, progress: 0, progressText: '准备上传…' });

    api
      .analyzeVideo({
        filePath: videoPath,
        stroke,
        dominant: settings.dominant,
        onProgress: (p) => {
          const value = Math.max(1, Math.min(100, Math.round(p)));
          this.setData({
            progress: value,
            progressText: value >= 100 ? '正在分析动作…' : `上传中 ${value}%`,
          });
        },
      })
      .then((result) => {
        const strokeName = STROKE_META[stroke] ? STROKE_META[stroke].short : '挥拍';
        const record = {
          t: Date.now(),
          score: result.total,
          grade: result.grade,
          stroke: strokeName,
          strokeKey: stroke,
          demo: !!result.demo,
        };
        store.addRecord(record);
        store.saveLastResult(result);
        getApp().globalData.lastResult = result;
        if (settings.vibrate) {
          wx.vibrateShort({ type: 'light' });
        }
        this.setData({ analyzing: false });
        wx.navigateTo({ url: '/pages/result/result' });
      })
      .catch((err) => {
        this.setData({ analyzing: false });
        const message = (err && err.message) || '请稍后重试';
        wx.showModal({
          title: '分析失败',
          content: `${message}\n\n本地调试：先在 server/ 启动分析服务；没有后端可在「设置」里打开演示模式。`,
          showCancel: false,
          confirmText: '知道了',
        });
      });
  },

  goRecords() {
    wx.switchTab({ url: '/pages/records/records' });
  },
});
