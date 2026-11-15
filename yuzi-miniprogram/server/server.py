"""羽姿分析服务：接收小程序上传的视频，返回动作评分"""

import os
import tempfile
import time
import traceback

from fastapi import FastAPI, File, Form, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

from analyzer import analyze_video

MAX_BYTES = 80 * 1024 * 1024

app = FastAPI(title='羽姿分析服务', version='1.0.0')
app.add_middleware(
    CORSMiddleware,
    allow_origins=['*'],
    allow_methods=['*'],
    allow_headers=['*'],
)


@app.get('/api/health')
def health():
    return {'ok': True, 'service': 'yuzi-analyzer', 'version': '1.0.0', 'time': int(time.time() * 1000)}


@app.post('/api/analyze')
async def analyze(
    video: UploadFile = File(...),
    stroke: str = Form('clear'),
    dominant: str = Form('right'),
):
    data = await video.read()
    if not data:
        raise HTTPException(status_code=400, detail='视频为空')
    if len(data) > MAX_BYTES:
        raise HTTPException(status_code=413, detail='视频太大')

    suffix = os.path.splitext(video.filename or '')[1] or '.mp4'
    fd, path = tempfile.mkstemp(suffix=suffix)
    os.close(fd)
    try:
        with open(path, 'wb') as f:
            f.write(data)
        result = analyze_video(path, stroke=stroke, dominant=dominant)
        result['at'] = int(time.time() * 1000)
        return result
    except ValueError as err:
        return {'ok': False, 'message': str(err)}
    except Exception:
        traceback.print_exc()
        return {'ok': False, 'message': '分析失败，请换一段视频再试'}
    finally:
        try:
            os.remove(path)
        except OSError:
            pass
