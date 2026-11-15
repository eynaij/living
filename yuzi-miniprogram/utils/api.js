const config = require('../config');
const store = require('./store');
const { demoResult } = require('./demo');

function baseUrl() {
  const settings = store.getSettings();
  const raw = (settings.baseUrl || config.BASE_URL || '').trim();
  return raw.replace(/\/+$/, '');
}

function requestHealth() {
  return new Promise((resolve, reject) => {
    wx.request({
      url: `${baseUrl()}/api/health`,
      method: 'GET',
      timeout: 5000,
      success(res) {
        if (res.statusCode === 200 && res.data && res.data.ok) resolve(res.data);
        else reject(new Error(`服务返回异常（${res.statusCode}）`));
      },
      fail() {
        reject(new Error('连不上服务器，请检查地址和网络'));
      },
    });
  });
}

function analyzeVideo({ filePath, stroke, dominant, onProgress }) {
  const settings = store.getSettings();
  if (settings.demo || config.DEMO) {
    return new Promise((resolve) => {
      let step = 0;
      const timer = setInterval(() => {
        step += 20;
        if (step >= 100) {
          if (onProgress) onProgress(100);
          clearInterval(timer);
          resolve(demoResult(stroke));
          return;
        }
        if (onProgress) onProgress(step);
      }, 160);
    });
  }

  return new Promise((resolve, reject) => {
    const task = wx.uploadFile({
      url: `${baseUrl()}/api/analyze`,
      filePath,
      name: 'video',
      formData: {
        stroke,
        dominant,
      },
      timeout: 60000,
      success(res) {
        let data = null;
        try {
          data = JSON.parse(res.data);
        } catch (err) {
          reject(new Error('分析服务返回了无法解析的内容'));
          return;
        }
        if (res.statusCode === 413) {
          reject(new Error('视频太大，请截取 5–8 秒后再试'));
          return;
        }
        if (res.statusCode !== 200 || !data || data.ok !== true) {
          reject(new Error((data && data.message) || `分析失败（${res.statusCode}）`));
          return;
        }
        resolve(data);
      },
      fail() {
        reject(new Error('上传失败，请检查服务器地址与网络'));
      },
    });
    if (task && typeof task.onProgressUpdate === 'function' && onProgress) {
      task.onProgressUpdate((p) => onProgress(p.progress));
    }
  });
}

module.exports = { analyzeVideo, requestHealth, baseUrl };
