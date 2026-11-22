"""羽姿评分模型（与网页版 app.js 的 STROKES / evaluate 保持一致）"""

import math


def clamp(v, lo, hi):
    return min(hi, max(lo, v))


def fmt_deg(v):
    return '--' if v is None else f"{round(v)}°"


def fmt_signed_l(v):
    if v is None:
        return '--'
    return f"{'+' if v >= 0 else ''}{v:.2f}L"


def fmt_l(v):
    return '--' if v is None else f"{v:.2f}L"


def fmt_speed(v):
    return '--' if v is None else f"{v:.1f}L/s"


def range_score(v, lo, hi, soft):
    """落在 [lo, hi] 区间得满分，越界按 soft 斜率衰减。"""
    if v is None:
        return None
    if lo <= v <= hi:
        return 100.0
    d = lo - v if v < lo else v - hi
    return clamp(100 - d * soft, 0, 100)


def _contact_low(c):
    strike = c['strike']
    return (strike['midHip']['y'] - strike['wrist']['y']) / strike['L']


STROKES = {
    'clear': {
        'name': '正手高远球',
        'short': '高远球',
        'strikeMode': 'high',
        'metrics': [
            {
                'key': 'elbowBack', 'name': '引拍屈肘', 'note': '架拍时肘部约 70–110°',
                'target': '70–110°', 'weight': 18,
                'value': lambda c: c['back']['elbowAngle'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 70, 110, 1.1),
                'tip': '引拍时先把肘抬到肩高、小臂后倒，别让手臂直着往后拉。',
            },
            {
                'key': 'strikeHeight', 'name': '击球点高度', 'note': '手腕应明显高于肩线',
                'target': '≥0.30L', 'weight': 24,
                'value': lambda c: c['strike']['above'],
                'display': fmt_signed_l,
                'score': lambda v: range_score(v, 0.3, 9, 90),
                'tip': '击球点要抢高，在肩前上方的最高点击球，别等球落到肩下再打。',
            },
            {
                'key': 'extension', 'name': '击球伸展', 'note': '击球瞬间手臂接近伸直',
                'target': '≥150°', 'weight': 20,
                'value': lambda c: c['strike']['elbowAngle'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 150, 181, 1.8),
                'tip': '击球瞬间大臂带小臂向上伸展，肘部接近伸直，别一直弯着肘打。',
            },
            {
                'key': 'follow', 'name': '随挥幅度', 'note': '击球后顺势向下收拍',
                'target': '≥0.50L', 'weight': 18,
                'value': lambda c: c['drop'],
                'display': fmt_l,
                'score': lambda v: range_score(v, 0.5, 9, 110),
                'tip': '击球后别急停，让球拍顺着惯性收到身体另一侧下方，随挥越完整发力越顺。',
            },
            {
                'key': 'knee', 'name': '屈膝蓄力', 'note': '准备与引拍保持微屈',
                'target': '120–158°', 'weight': 12,
                'value': lambda c: c['knee'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 120, 158, 1.3),
                'tip': '准备和引拍时膝盖保持微屈、重心稍降，靠蹬转发力而不是只用手臂。',
            },
            {
                'key': 'lean', 'name': '躯干前倾', 'note': '击球时身体略前倾',
                'target': '0–30°', 'weight': 8,
                'value': lambda c: c['lean'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 0, 30, 2.6),
                'tip': '击球时身体略向前压，别后仰，重心跟着动作从后脚转到前脚。',
            },
        ],
    },

    'lift': {
        'name': '正手挑球',
        'short': '正手挑球',
        'strikeMode': 'fast',
        'metrics': [
            {
                'key': 'contactLow', 'name': '击球点低位', 'note': '在腰膝之间低点接触球',
                'target': '腰以下', 'weight': 18,
                'value': _contact_low,
                'display': fmt_signed_l,
                'score': lambda v: range_score(v, -0.55, 0, 80),
                'tip': '挑球要在身体前下方的低点接触球，别等球落到身后，也别把击球点抬得太高。',
            },
            {
                'key': 'extension', 'name': '击球伸展', 'note': '击球瞬间手臂接近伸直',
                'target': '≥140°', 'weight': 20,
                'value': lambda c: c['strike']['elbowAngle'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 140, 181, 1.6),
                'tip': '向前上方挑球时手臂要伸展出去，别夹着肘只用手腕甩。',
            },
            {
                'key': 'knee', 'name': '屈膝蓄力', 'note': '准备时膝盖保持较深微屈',
                'target': '110–150°', 'weight': 18,
                'value': lambda c: c['knee'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 110, 150, 1.4),
                'tip': '挑球前先屈膝降重心，用蹬地的力量把球送高，别直腿弯腰去够球。',
            },
            {
                'key': 'rise', 'name': '随挥上送', 'note': '击球后球拍继续向上送',
                'target': '≥0.25L', 'weight': 18,
                'value': lambda c: c['rise'],
                'display': fmt_l,
                'score': lambda v: range_score(v, 0.25, 9, 100),
                'tip': '击球后球拍要继续向前上方送出，随挥方向决定球的深度。',
            },
            {
                'key': 'speed', 'name': '挥拍速度', 'note': '前摆到击球的拍头速度',
                'target': '≥2.2L/s', 'weight': 16,
                'value': lambda c: c['maxSpeed'],
                'display': fmt_speed,
                'score': lambda v: range_score(v, 2.2, 10, 40),
                'tip': '挑球也要有前摆加速，别只靠手臂慢慢抬，注意鞭打发力。',
            },
            {
                'key': 'lean', 'name': '躯干前倾', 'note': '击球时身体略前压',
                'target': '5–35°', 'weight': 10,
                'value': lambda c: c['lean'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 5, 35, 2.4),
                'tip': '击球时身体略向前跟，别后仰或直挺挺站着。',
            },
        ],
    },

    'backhandLift': {
        'name': '反手挑球',
        'short': '反手挑球',
        'strikeMode': 'fast',
        'metrics': [
            {
                'key': 'contactLow', 'name': '击球点低位', 'note': '在身体前下方低点接触球',
                'target': '腰以下', 'weight': 20,
                'value': _contact_low,
                'display': fmt_signed_l,
                'score': lambda v: range_score(v, -0.6, 0.02, 80),
                'tip': '反手挑球同样要在低点接触球，拍面朝前上方把球送出去。',
            },
            {
                'key': 'extension', 'name': '击球伸展', 'note': '击球瞬间手臂接近伸直',
                'target': '≥135°', 'weight': 18,
                'value': lambda c: c['strike']['elbowAngle'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 135, 181, 1.7),
                'tip': '反手击球时肘部要向前送出，手臂展开，别缩在身体前面。',
            },
            {
                'key': 'knee', 'name': '屈膝蓄力', 'note': '准备时膝盖保持较深微屈',
                'target': '105–148°', 'weight': 18,
                'value': lambda c: c['knee'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 105, 148, 1.4),
                'tip': '反手挑球更需要降重心，先屈膝再蹬地，别直腿弯腰。',
            },
            {
                'key': 'rise', 'name': '随挥上送', 'note': '击球后球拍继续向上送',
                'target': '≥0.22L', 'weight': 20,
                'value': lambda c: c['rise'],
                'display': fmt_l,
                'score': lambda v: range_score(v, 0.22, 9, 100),
                'tip': '击球后顺势向前上方送拍，随挥不足球容易只挑到中场。',
            },
            {
                'key': 'speed', 'name': '挥拍速度', 'note': '前摆到击球的拍头速度',
                'target': '≥2.0L/s', 'weight': 14,
                'value': lambda c: c['maxSpeed'],
                'display': fmt_speed,
                'score': lambda v: range_score(v, 2.0, 10, 40),
                'tip': '反手也要有前摆加速，靠手指手腕发力，别慢慢抬臂。',
            },
            {
                'key': 'lean', 'name': '躯干前倾', 'note': '击球时身体略前压',
                'target': '0–32°', 'weight': 10,
                'value': lambda c: c['lean'],
                'display': fmt_deg,
                'score': lambda v: range_score(v, 0, 32, 2.4),
                'tip': '击球时身体略向前压，重心随动作前移，别后仰。',
            },
        ],
    },
}


def grade_of(total):
    return '优秀' if total >= 85 else '良好' if total >= 70 else '及格' if total >= 55 else '待改进'


def build_context(valid, cfg):
    """找出击球帧、引拍帧以及随挥位移等公共上下文。"""
    strike = valid[0]
    if cfg['strikeMode'] == 'fast':
        for f in valid:
            if f['speed'] > strike['speed']:
                strike = f
    else:
        for f in valid:
            if f['above'] > strike['above']:
                strike = f

    back = None
    for f in valid:
        if f['t'] >= strike['t'] - 30:
            continue
        if back is None or f['wrist']['y'] > back['wrist']['y']:
            back = f
    if back is None:
        back = strike

    drop = 0.0
    rise = 0.0
    max_speed = 0.0
    for f in valid:
        if f['speed'] > max_speed:
            max_speed = f['speed']
        if f['t'] <= strike['t'] + 40:
            continue
        drop = max(drop, (f['wrist']['y'] - strike['wrist']['y']) / f['L'])
        rise = max(rise, (strike['wrist']['y'] - f['wrist']['y']) / f['L'])

    return {
        'strike': strike,
        'back': back,
        'drop': drop,
        'rise': rise,
        'maxSpeed': max_speed,
        'knee': back['kneeAngle'] if back['kneeAngle'] is not None else strike['kneeAngle'],
        'lean': abs(strike['lean']),
    }


def evaluate(frames, stroke='clear'):
    valid = [f for f in frames if f['ok'] and f['elbowAngle'] is not None]
    if len(valid) < 8:
        return None

    cfg = STROKES.get(stroke) or STROKES['clear']
    c = build_context(valid, cfg)

    defs = []
    for m in cfg['metrics']:
        value = None
        try:
            value = m['value'](c)
        except Exception:
            value = None
        display = '--'
        if value is not None:
            try:
                display = m['display'](value)
            except Exception:
                display = '--'
        score = None
        if value is not None:
            score = m['score'](value)
        defs.append({
            'key': m['key'],
            'name': m['name'],
            'note': m['note'],
            'target': m['target'],
            'value': value,
            'display': display,
            'score': score,
            'weight': m['weight'],
            'tip': m['tip'],
        })

    total_sum = 0.0
    weight_sum = 0.0
    for d in defs:
        if d['score'] is None:
            continue
        total_sum += d['score'] * d['weight']
        weight_sum += d['weight']
    if not weight_sum:
        return None

    total = int(math.floor(total_sum / weight_sum + 0.5))
    grade = grade_of(total)

    weak = sorted([d for d in defs if d['score'] is not None and d['score'] < 72],
                  key=lambda d: d['score'])
    tips = []
    if weak:
        for d in weak[:2] if total >= 55 else weak[:3]:
            tips.append({'text': d['tip'], 'good': False})
    if not tips:
        tips.append({'text': '六个维度都很到位，保持这个击球点和随挥节奏。', 'good': True})
    if total >= 70 and len(tips) < 2:
        best = sorted([d for d in defs if d['score'] is not None],
                      key=lambda d: d['score'], reverse=True)
        if best:
            tips.append({'text': f"{best[0]['name']}完成得很好，继续稳住这一项。", 'good': True})

    return {
        'ok': True,
        'total': total,
        'grade': grade,
        'stroke': cfg['short'],
        'metrics': defs,
        'tips': tips,
    }
