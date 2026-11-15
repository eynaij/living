/*
 * 后端地址配置
 *
 * 本地调试：先把 server/ 跑起来（默认 8000 端口），
 *   微信开发者工具 → 详情 → 本地设置 → 勾选「不校验合法域名」。
 * 上线前：改成已备案并配置到「request 合法域名」的 HTTPS 域名，
 *   同时把 DEMO 改为 false。
 */
module.exports = {
  BASE_URL: 'http://127.0.0.1:8000',
  // 没有可用后端时可设为 true，用演示数据预览完整流程
  DEMO: false,
};
