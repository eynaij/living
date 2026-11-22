const api = require('../../utils/api');
const store = require('../../utils/store');
const config = require('../../config');

Page({
  data: {
    dominant: 'right',
    baseUrl: '',
    demo: false,
    vibrate: true,
    defaultUrl: config.BASE_URL,
    testText: '',
    testing: false,
  },

  onShow() {
    const settings = store.getSettings();
    this.setData({
      dominant: settings.dominant,
      baseUrl: settings.baseUrl || '',
      demo: settings.demo,
      vibrate: settings.vibrate,
    });
  },

  setDominant(e) {
    const { value } = e.currentTarget.dataset;
    store.saveSettings({ dominant: value });
    this.setData({ dominant: value });
  },

  onBaseUrlInput(e) {
    this.setData({ baseUrl: e.detail.value });
  },

  saveBaseUrl() {
    const url = (this.data.baseUrl || '').trim();
    store.saveSettings({ baseUrl: url });
    wx.showToast({ title: '地址已保存', icon: 'none' });
  },

  resetBaseUrl() {
    store.saveSettings({ baseUrl: '' });
    this.setData({ baseUrl: '' });
    wx.showToast({ title: '已恢复默认地址', icon: 'none' });
  },

  testServer() {
    if (this.data.testing) return;
    store.saveSettings({ baseUrl: (this.data.baseUrl || '').trim() });
    this.setData({ testing: true, testText: '' });
    api
      .requestHealth()
      .then(() => {
        this.setData({ testing: false, testText: '连接正常' });
        wx.showToast({ title: '服务可用', icon: 'success' });
      })
      .catch((err) => {
        this.setData({ testing: false, testText: (err && err.message) || '连接失败' });
        wx.showModal({
          title: '连接失败',
          content: `${(err && err.message) || '未知错误'}\n\n请确认 server/ 已启动，且开发者工具已勾选「不校验合法域名」。`,
          showCancel: false,
        });
      });
  },

  toggleDemo(e) {
    const demo = !!e.detail.value;
    store.saveSettings({ demo });
    this.setData({ demo });
  },

  toggleVibrate(e) {
    const vibrate = !!e.detail.value;
    store.saveSettings({ vibrate });
    this.setData({ vibrate });
  },
});
