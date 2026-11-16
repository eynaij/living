#!/usr/bin/env bash
# Google Play 上传脚本（戒股）
# 用法：
#   1. 先在 Google Cloud Console 建项目，开 Google Play Developer API，建 Service Account，下载 JSON 密钥
#   2. 把 JSON 密钥放到 secrets/service-account.json
#   3. 把 Service Account 邮箱加到 Play Console → 设置 → 开发者账号 → API 访问 → 添加新服务账号
#   4. 本脚本会创建 internal testing release track 并上传 AAB

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
AAB="$ROOT/jiegu-v1.0.0.aab"
PKG="com.nostock"
VERSION_NAME="1.0.0"
TRACK="${1:-internal}"   # internal | alpha | beta | production
NOTES_FILE="$ROOT/submission/release-notes.txt"

KEYS_FILE="${GOOGLE_SERVICE_ACCOUNT:-$ROOT/secrets/service-account.json}"

if [ ! -f "$AAB" ]; then echo "找不到 AAB: $AAB"; exit 1; fi
if [ ! -f "$KEYS_FILE" ]; then
  echo ""
  echo "❌ 找不到 Service Account JSON 密钥: $KEYS_FILE"
  echo ""
  echo "📋 请按以下步骤创建："
  echo "   1. 打开 https://console.cloud.google.com/ 并创建项目"
  echo "   2. APIs & Services → Enable APIs → 搜索 'Google Play Android Developer API' → Enable"
  echo "   3. IAM & Admin → Service Accounts → Create Service Account（名字随便，比如 'jiegu-upload'）"
  echo "   4. 点刚创建的服务账号 → Keys → Add Key → Create new key → JSON"
  echo "   5. 下载的 JSON 文件重命名为 service-account.json 放到 $KEYS_FILE"
  echo "   6. 打开 https://play.google.com/console/ → 选择你的应用"
  echo "      → 设置 → 开发者账号 → API 访问 → 添加新服务账号"
  echo "      → 粘贴 JSON 里的 client_email 值"
  echo "      → 权限选 '发布管理' 即可"
  echo ""
  echo "   完成后再运行本脚本。"
  exit 1
fi

echo "🔑 使用密钥: $KEYS_FILE"
echo "📦 AAB: $AAB"
echo "🎯 Track: $TRACK"

# —— 用 Python Google API 客户端上传 ——
pip3 install --quiet google-api-python-client google-auth-httplib2 google-auth-oauthlib 2>&1 | tail -3

python3 - "$AAB" "$PKG" "$TRACK" "$KEYS_FILE" "$NOTES_FILE" <<'PYEOF'
import sys, os, json, time
from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.http import MediaFileUpload

aab_path, package, track, keys_file, notes_file = sys.argv[1:6]

scopes = ['https://www.googleapis.com/auth/androidpublisher']
creds = service_account.Credentials.from_service_account_file(keys_file, scopes=scopes)
service = build('androidpublisher', 'v3', credentials=creds)

# 1. 创建编辑（一次只能有一个活跃的 edit）
edit_request = service.edits().insert(packageName=package, body={})
edit = edit_request.execute()
edit_id = edit['id']
print(f"✅ 创建 edit: {edit_id}")

# 2. 上传 AAB
print(f"📤 正在上传 AAB... ({os.path.getsize(aab_path)/1024:.0f} KB)")
media = MediaFileUpload(aab_path, mimetype='application/octet-stream', resumable=True)
upload = service.edits().bundles().upload(
    packageName=package, editId=edit_id, media_body=media
)
bundle = upload.execute()
version_code = bundle['versionCode']
print(f"✅ AAB 已上传，versionCode={version_code}")

# 3. 给这个版本配置 release notes / 追踪状态
notes_text = "戒股 1.0.0 首发。每日打卡、连续天数、冲动记录、交易墙、统计图表、每周复盘、全部本地存储。"
try:
    with open(notes_file) as f:
        notes_text = f.read().strip()
except Exception:
    pass

body = {
    'track': track,
    'releases': [{
        'versionCodes': [version_code],
        'releaseNotes': [{'language': 'zh-CN', 'text': notes_text}],
        'status': 'completed',
    }]
}
service.edits().tracks().update(
    packageName=package, editId=edit_id, track=track, body=body
).execute()
print(f"✅ Track '{track}' 已更新")

# 4. 确认 edit（最后一步，真正生效）
confirm = service.edits().commit(packageName=package, editId=edit_id).execute()
print(f"✅ Edit 已确认，发布 ID: {confirm.get('id','?')}")
print("🎉 上传完成！请打开 Play Console 查看。")
PYEOF
