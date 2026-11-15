#!/usr/bin/env bash
# 启动羽姿视频分析服务（默认 http://0.0.0.0:8000）
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -d .venv ]; then
  python3 -m venv .venv
fi
. .venv/bin/activate
pip install -q -r requirements.txt

exec uvicorn server:app --host 0.0.0.0 --port 8000
