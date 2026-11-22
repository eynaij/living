const store = require('../../utils/store');

Page({
  data: {
    stats: { total: 0, avg: 0, best: 0, days: 0 },
    bars: [],
    groups: [],
    hasData: false,
  },

  onShow() {
    this.refresh();
  },

  refresh() {
    const records = store.getRecords();
    this.setData({
      stats: store.stats(records),
      bars: store.weekBars(records),
      groups: store.groupByDay(records),
      hasData: records.length > 0,
    });
  },

  goTrain() {
    wx.switchTab({ url: '/pages/index/index' });
  },

  clearAll() {
    wx.showModal({
      title: '清空记录',
      content: '所有历史成绩将被删除，且无法恢复。',
      confirmText: '清空',
      confirmColor: '#ff6b6b',
      success: (res) => {
        if (!res.confirm) return;
        store.clearRecords();
        this.refresh();
        wx.showToast({ title: '已清空', icon: 'none' });
      },
    });
  },
});
