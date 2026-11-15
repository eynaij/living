/* 演示数据：没有可用后端时用于预览完整流程，结果页会标记「演示数据」 */

const DEMO = {
  clear: {
    stroke: '高远球',
    total: 82,
    grade: '良好',
    metrics: [
      { key: 'elbowBack', name: '引拍屈肘', note: '架拍时肘部约 70–110°', target: '70–110°', value: 96, display: '96°', score: 96, weight: 18, tip: '引拍时先把肘抬到肩高、小臂后倒，别让手臂直着往后拉。' },
      { key: 'strikeHeight', name: '击球点高度', note: '手腕应明显高于肩线', target: '≥0.30L', value: 0.31, display: '+0.31L', score: 92, weight: 24, tip: '击球点要抢高，在肩前上方的最高点击球。' },
      { key: 'extension', name: '击球伸展', note: '击球瞬间手臂接近伸直', target: '≥150°', value: 141, display: '141°', score: 84, weight: 20, tip: '击球瞬间大臂带小臂向上伸展，肘部接近伸直。' },
      { key: 'follow', name: '随挥幅度', note: '击球后顺势向下收拍', target: '≥0.50L', value: 0.28, display: '0.28L', score: 48, weight: 18, tip: '击球后别急停，让球拍顺着惯性收到身体另一侧下方。' },
      { key: 'knee', name: '屈膝蓄力', note: '准备与引拍保持微屈', target: '120–158°', value: 132, display: '132°', score: 100, weight: 12, tip: '准备和引拍时膝盖保持微屈、重心稍降。' },
      { key: 'lean', name: '躯干前倾', note: '击球时身体略前倾', target: '0–30°', value: 41, display: '41°', score: 72, weight: 8, tip: '击球时身体略向前压，别后仰。' },
    ],
    tips: [
      { text: '击球后别急停，让球拍顺着惯性收到身体另一侧下方，随挥越完整发力越顺。', good: false },
      { text: '躯干前倾完成得很好，继续稳住这一项。', good: true },
    ],
  },

  lift: {
    stroke: '正手挑球',
    total: 88,
    grade: '优秀',
    metrics: [
      { key: 'contactLow', name: '击球点低位', note: '在腰膝之间低点接触球', target: '腰以下', value: -0.28, display: '-0.28L', score: 94, weight: 18, tip: '挑球要在身体前下方的低点接触球。' },
      { key: 'extension', name: '击球伸展', note: '击球瞬间手臂接近伸直', target: '≥140°', value: 152, display: '152°', score: 96, weight: 20, tip: '向前上方挑球时手臂要伸展出去。' },
      { key: 'knee', name: '屈膝蓄力', note: '准备时膝盖保持较深微屈', target: '110–150°', value: 128, display: '128°', score: 100, weight: 18, tip: '挑球前先屈膝降重心，用蹬地的力量把球送高。' },
      { key: 'rise', name: '随挥上送', note: '击球后球拍继续向上送', target: '≥0.25L', value: 0.18, display: '0.18L', score: 58, weight: 18, tip: '击球后球拍要继续向前上方送出。' },
      { key: 'speed', name: '挥拍速度', note: '前摆到击球的拍头速度', target: '≥2.2L/s', value: 3.1, display: '3.1L/s', score: 88, weight: 16, tip: '挑球也要有前摆加速，注意鞭打发力。' },
      { key: 'lean', name: '躯干前倾', note: '击球时身体略前压', target: '5–35°', value: 22, display: '22°', score: 92, weight: 10, tip: '击球时身体略向前跟。' },
    ],
    tips: [
      { text: '击球后球拍要继续向前上方送出，随挥方向决定球的深度。', good: false },
      { text: '击球伸展完成得很好，继续稳住这一项。', good: true },
    ],
  },

  backhandLift: {
    stroke: '反手挑球',
    total: 79,
    grade: '良好',
    metrics: [
      { key: 'contactLow', name: '击球点低位', note: '在身体前下方低点接触球', target: '腰以下', value: -0.34, display: '-0.34L', score: 96, weight: 20, tip: '反手挑球同样要在低点接触球。' },
      { key: 'extension', name: '击球伸展', note: '击球瞬间手臂接近伸直', target: '≥135°', value: 143, display: '143°', score: 88, weight: 18, tip: '反手击球时肘部要向前送出，手臂展开。' },
      { key: 'knee', name: '屈膝蓄力', note: '准备时膝盖保持较深微屈', target: '105–148°', value: 118, display: '118°', score: 100, weight: 18, tip: '反手挑球更需要降重心，先屈膝再蹬地。' },
      { key: 'rise', name: '随挥上送', note: '击球后球拍继续向上送', target: '≥0.22L', value: 0.16, display: '0.16L', score: 52, weight: 20, tip: '击球后顺势向前上方送拍，随挥不足球容易只挑到中场。' },
      { key: 'speed', name: '挥拍速度', note: '前摆到击球的拍头速度', target: '≥2.0L/s', value: 2.8, display: '2.8L/s', score: 86, weight: 14, tip: '反手也要有前摆加速，靠手指手腕发力。' },
      { key: 'lean', name: '躯干前倾', note: '击球时身体略前压', target: '0–32°', value: 18, display: '18°', score: 94, weight: 10, tip: '击球时身体略向前压，重心随动作前移。' },
    ],
    tips: [
      { text: '击球后顺势向前上方送拍，随挥不足球容易只挑到中场。', good: false },
      { text: '击球点低位完成得很好，继续稳住这一项。', good: true },
    ],
  },
};

function demoResult(stroke) {
  const base = DEMO[stroke] || DEMO.clear;
  return {
    ok: true,
    demo: true,
    stroke: base.stroke,
    total: base.total,
    grade: base.grade,
    metrics: base.metrics.map((m) => ({ ...m })),
    tips: base.tips.map((t) => ({ ...t })),
    meta: { frames: 96, validFrames: 88 },
    at: Date.now(),
  };
}

module.exports = { demoResult };
