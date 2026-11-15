const store = require('../../utils/store');

function scoreColor(score) {
  if (score == null) return '#7b8a80';
  if (score >= 85) return '#d8ff4a';
  if (score >= 70) return '#25e0a5';
  if (score >= 55) return '#ffb547';
  return '#ff6b6b';
}

function scoreNote(total) {
  if (total >= 85) return '动作相当标准，可以加难度了。';
  if (total >= 70) return '整体不错，把这几个细节再抠一下。';
  if (total >= 55) return '基本成型，先把最薄弱的一项改过来。';
  return '别急，先放慢动作，把击球点和随挥做完整。';
}

Page({
  data: {
    result: null,
    metrics: [],
    tips: [],
    scoreColor: '#d8ff4a',
    scoreNote: '',
    demo: false,
    thumbnail: '',
    thumbError: false,
  },

  onLoad() {
    const app = getApp();
    let result = app.globalData.lastResult;
    if (!result) result = store.getLastResult();

    if (!result || !Array.isArray(result.metrics) || !result.metrics.length) {
      wx.showToast({ title: '没有找到分析结果', icon: 'none' });
      setTimeout(() => {
        wx.navigateBack();
      }, 900);
      return;
    }

    const metrics = result.metrics.map((m) => ({
      key: m.key,
      name: m.name,
      note: m.note,
      target: m.target,
      display: m.display,
      score: m.score,
      width: m.score == null ? 0 : Math.max(2, Math.min(100, Math.round(m.score))),
      scoreText: m.score == null ? '--' : String(Math.round(m.score)),
      color: scoreColor(m.score),
    }));

    this.setData({
      result,
      metrics,
      tips: Array.isArray(result.tips) ? result.tips : [],
      scoreColor: scoreColor(result.total),
      scoreNote: scoreNote(result.total),
      demo: !!result.demo,
      thumbnail: result.thumbnail || '',
    });
  },

  onThumbError() {
    this.setData({ thumbError: true });
  },

  again() {
    wx.navigateBack();
  },

  goRecords() {
    wx.switchTab({ url: '/pages/records/records' });
  },

  onShareAppMessage() {
    const { result } = this.data;
    return {
      title: result ? `这一拍 ${result.total} 分 · ${result.grade}` : '羽姿 · 羽毛球姿势分析',
      path: '/pages/index/index',
    };
  },
});
