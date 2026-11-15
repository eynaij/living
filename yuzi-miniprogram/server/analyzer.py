"""视频姿态分析：抽帧 → MediaPipe Pose → 几何特征 → 评分"""

import base64
import math
import os

import cv2
import mediapipe as mp
import numpy as np
from mediapipe.tasks import python as mp_python
from mediapipe.tasks.python import vision

from scoring import STROKES, evaluate

MODEL_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), 'models', 'pose_landmarker_lite.task')

LM = {
    'LSHOULDER': 11, 'RSHOULDER': 12,
    'LELBOW': 13, 'RELBOW': 14,
    'LWRIST': 15, 'RWRIST': 16,
    'LHIP': 23, 'RHIP': 24,
    'LKNEE': 25, 'RKNEE': 26,
    'LANKLE': 27, 'RANKLE': 28,
}

SMOOTH_ALPHA = 0.45
SAMPLE_FPS = 15
MAX_SAMPLES = 480


def _dist(a, b):
    return math.hypot(a['x'] - b['x'], a['y'] - b['y'])


def _mid(a, b):
    return {'x': (a['x'] + b['x']) / 2, 'y': (a['y'] + b['y']) / 2}


def _angle_at(a, b, c):
    v1x, v1y = a['x'] - b['x'], a['y'] - b['y']
    v2x, v2y = c['x'] - b['x'], c['y'] - b['y']
    m = math.hypot(v1x, v1y) * math.hypot(v2x, v2y)
    if m < 1e-6:
        return None
    cos = max(-1.0, min(1.0, (v1x * v2x + v1y * v2y) / m))
    return math.acos(cos) * 180.0 / math.pi


def _smooth(prev, landmarks):
    points = [{
        'x': float(p.x),
        'y': float(p.y),
        'visibility': float(p.visibility or 0.0),
    } for p in landmarks]
    if prev is None:
        return points
    for i, p in enumerate(points):
        s = prev[i]
        s['x'] += (p['x'] - s['x']) * SMOOTH_ALPHA
        s['y'] += (p['y'] - s['y']) * SMOOTH_ALPHA
        s['visibility'] = p['visibility']
    return prev


def _build_frame(points, aspect, t, dominant):
    right = dominant != 'left'
    sh_i = LM['RSHOULDER'] if right else LM['LSHOULDER']
    el_i = LM['RELBOW'] if right else LM['LELBOW']
    wr_i = LM['RWRIST'] if right else LM['LWRIST']
    hi_i = LM['RHIP'] if right else LM['LHIP']
    kn_i = LM['RKNEE'] if right else LM['LKNEE']
    an_i = LM['RANKLE'] if right else LM['LANKLE']
    o_sh_i = LM['LSHOULDER'] if right else LM['RSHOULDER']
    o_hi_i = LM['LHIP'] if right else LM['RHIP']
    o_kn_i = LM['LKNEE'] if right else LM['RKNEE']
    o_an_i = LM['LANKLE'] if right else LM['RANKLE']

    def p(i):
        return {
            'x': points[i]['x'] * aspect,
            'y': points[i]['y'],
            'v': points[i]['visibility'],
        }

    sh, el, wr, hi, kn, an = p(sh_i), p(el_i), p(wr_i), p(hi_i), p(kn_i), p(an_i)
    o_sh, o_hi, o_kn, o_an = p(o_sh_i), p(o_hi_i), p(o_kn_i), p(o_an_i)

    m_sh = _mid(sh, o_sh)
    m_hi = _mid(hi, o_hi)
    length = _dist(m_sh, m_hi)
    if length < 0.02:
        return None

    vis = min(sh['v'], el['v'], wr['v'], hi['v'], kn['v'], o_sh['v'], o_hi['v'])
    leg_vis = min(kn['v'], an['v'], o_kn['v'], o_an['v'], hi['v'], o_hi['v'])

    return {
        't': t,
        'L': length,
        'wrist': wr,
        'shoulder': sh,
        'elbow': el,
        'hip': hi,
        'knee': kn,
        'ankle': an,
        'midShoulder': m_sh,
        'midHip': m_hi,
        'elbowAngle': _angle_at(sh, el, wr),
        'kneeAngle': _angle_at(hi, kn, an),
        'offKneeAngle': _angle_at(o_hi, o_kn, o_an),
        'lean': math.degrees(math.atan2(m_sh['x'] - m_hi['x'], m_hi['y'] - m_sh['y'])),
        'above': (m_sh['y'] - wr['y']) / length,
        'speed': 0.0,
        'vis': vis,
        'legVis': leg_vis,
        'ok': vis > 0.32,
        'fullBody': leg_vis > 0.3,
    }


def _compute_speed(frame, prev):
    if prev is None:
        frame['speed'] = 0.0
        return
    dt = (frame['t'] - prev['t']) / 1000.0
    if dt <= 0 or dt > 0.4:
        frame['speed'] = 0.0
        return
    raw = _dist(frame['wrist'], prev['wrist']) / dt / frame['L']
    frame['speed'] = prev['speed'] * 0.45 + raw * 0.55


def _draw_snapshot(image, points, dominant='right'):
    canvas = image.copy()
    h, w = canvas.shape[:2]
    accent = (74, 255, 216)   # #d8ff4a
    mint = (165, 224, 37)     # #25e0a5
    white = (235, 245, 238)
    right = dominant != 'left'
    main_arm = [(12, 14), (14, 16)] if right else [(11, 13), (13, 15)]
    off_arm = [(11, 13), (13, 15)] if right else [(12, 14), (14, 16)]
    wrist_index = 16 if right else 15

    def pt(i):
        return int(points[i]['x'] * w), int(points[i]['y'] * h)

    def seg(a, b, color, width):
        if points[a]['visibility'] < 0.3 or points[b]['visibility'] < 0.3:
            return
        cv2.line(canvas, pt(a), pt(b), color, width, cv2.LINE_AA)

    for a, b in main_arm:
        seg(a, b, accent, 5)
    for a, b in off_arm:
        seg(a, b, mint, 4)
    for a, b in [(11, 12), (11, 23), (12, 24), (23, 24), (23, 25), (25, 27), (24, 26), (26, 28)]:
        seg(a, b, white, 4)

    for i in [11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28]:
        if points[i]['visibility'] < 0.3:
            continue
        cv2.circle(canvas, pt(i), 5, white, -1, cv2.LINE_AA)
    if points[wrist_index]['visibility'] > 0.3:
        cv2.circle(canvas, pt(wrist_index), 10, accent, -1, cv2.LINE_AA)

    if w > 720:
        scale = 720.0 / w
        canvas = cv2.resize(canvas, (720, int(h * scale)), interpolation=cv2.INTER_AREA)

    ok, buf = cv2.imencode('.jpg', canvas, [int(cv2.IMWRITE_JPEG_QUALITY), 78])
    if not ok:
        return None
    return 'data:image/jpeg;base64,' + base64.b64encode(buf.tobytes()).decode('ascii')


def analyze_video(path, stroke='clear', dominant='right'):
    cap = cv2.VideoCapture(path)
    if not cap.isOpened():
        raise ValueError('无法读取这个视频文件')

    fps = cap.get(cv2.CAP_PROP_FPS) or 30.0
    if not (1 <= fps <= 240):
        fps = 30.0
    step = max(1, int(round(fps / SAMPLE_FPS)))

    mode = (STROKES.get(stroke) or STROKES['clear'])['strikeMode']
    options = vision.PoseLandmarkerOptions(
        base_options=mp_python.BaseOptions(model_asset_path=MODEL_PATH),
        running_mode=vision.RunningMode.VIDEO,
        num_poses=1,
        min_pose_detection_confidence=0.5,
        min_pose_presence_confidence=0.5,
        min_tracking_confidence=0.5,
    )

    frames = []
    smooth = None
    prev = None
    best_value = -1e9
    best_image = None
    best_points = None
    frame_index = 0
    sampled = 0
    last_ts = -1

    with vision.PoseLandmarker.create_from_options(options) as landmarker:
        while sampled < MAX_SAMPLES:
            ok, image = cap.read()
            if not ok:
                break
            if frame_index % step == 0:
                ts = int(round(frame_index * 1000.0 / fps))
                if ts <= last_ts:
                    ts = last_ts + 1
                last_ts = ts
                rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
                mp_image = mp.Image(image_format=mp.ImageFormat.SRGB, data=rgb)
                detection = landmarker.detect_for_video(mp_image, ts)
                if detection.pose_landmarks:
                    smooth = _smooth(smooth, detection.pose_landmarks[0])
                    aspect = image.shape[1] / float(image.shape[0])
                    frame = _build_frame(smooth, aspect, ts, dominant)
                    if frame:
                        _compute_speed(frame, prev)
                        prev = frame
                        frames.append(frame)
                        sampled += 1
                        if frame['ok']:
                            key_value = frame['speed'] if mode == 'fast' else frame['above']
                            if key_value > best_value:
                                best_value = key_value
                                best_image = image.copy()
                                best_points = [dict(p) for p in smooth]
            frame_index += 1

    cap.release()

    if not frames:
        return {'ok': False, 'message': '视频里没有识别到人体，请确认全身入镜、光线充足'}

    result = evaluate(frames, stroke)
    if not result:
        return {'ok': False, 'message': '没有识别到完整挥拍，请只录一次挥拍并让全身入镜'}

    duration = (frame_index / fps) if fps else 0
    result['meta'] = {
        'frames': len(frames),
        'validFrames': len([f for f in frames if f['ok']]),
        'duration': round(duration, 2),
        'sampledFps': round(fps / step, 1),
    }
    if best_image is not None:
        thumb = _draw_snapshot(best_image, best_points, dominant)
        if thumb:
            result['thumbnail'] = thumb
    return result
