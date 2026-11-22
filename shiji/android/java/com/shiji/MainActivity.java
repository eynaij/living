package com.shiji;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MainActivity extends Activity {

    private static final String HOST = "appassets.androidplatform.net";
    private static final String BASE_URL = "https://" + HOST + "/";
    private static final Map<String, String> KNOWN_APP_NAMES = new HashMap<>();

    static {
        KNOWN_APP_NAMES.put("com.tencent.mm", "微信");
        KNOWN_APP_NAMES.put("com.tencent.mobileqq", "QQ");
        KNOWN_APP_NAMES.put("com.sina.weibo", "微博");
        KNOWN_APP_NAMES.put("com.xingin.xhs", "小红书");
        KNOWN_APP_NAMES.put("com.ss.android.ugc.aweme", "抖音");
        KNOWN_APP_NAMES.put("com.zhihu.android", "知乎");
        KNOWN_APP_NAMES.put("com.bytedance.trae.cn", "Trae");
        KNOWN_APP_NAMES.put("ai.x.grok.bot", "Grok");
        KNOWN_APP_NAMES.put("cn.ticktick.task", "滴答清单");
        KNOWN_APP_NAMES.put("com.coloros.note", "便签");
        KNOWN_APP_NAMES.put("com.heytap.browser", "浏览器");
        KNOWN_APP_NAMES.put("com.phoenix.read", "阅读");
        KNOWN_APP_NAMES.put("com.heytap.market", "软件商店");
        KNOWN_APP_NAMES.put("me.ele", "饿了么");
        KNOWN_APP_NAMES.put("com.taobao.taobao", "淘宝");
        KNOWN_APP_NAMES.put("com.jingdong.app.mall", "京东");
        KNOWN_APP_NAMES.put("com.xunmeng.pinduoduo", "拼多多");
        KNOWN_APP_NAMES.put("com.tmall.wireless", "天猫");
        KNOWN_APP_NAMES.put("com.taobao.idlefish", "闲鱼");
        KNOWN_APP_NAMES.put("tv.danmaku.bili", "哔哩哔哩");
        KNOWN_APP_NAMES.put("com.tencent.qqlive", "腾讯视频");
        KNOWN_APP_NAMES.put("com.qiyi.video", "爱奇艺");
        KNOWN_APP_NAMES.put("com.youku.phone", "优酷");
        KNOWN_APP_NAMES.put("com.hunantv.imgo.activity", "芒果TV");
        KNOWN_APP_NAMES.put("com.ss.android.article.video", "西瓜视频");
        KNOWN_APP_NAMES.put("com.netease.cloudmusic", "网易云音乐");
        KNOWN_APP_NAMES.put("com.tencent.qqmusic", "QQ音乐");
        KNOWN_APP_NAMES.put("com.kugou.android", "酷狗音乐");
        KNOWN_APP_NAMES.put("com.ximalaya.ting.android", "喜马拉雅");
        KNOWN_APP_NAMES.put("com.tencent.weread", "微信读书");
        KNOWN_APP_NAMES.put("com.duokan.reader", "多看阅读");
        KNOWN_APP_NAMES.put("com.ss.android.article.news", "今日头条");
        KNOWN_APP_NAMES.put("com.qidian.QDReader", "起点读书");
    }

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Window window = getWindow();
        window.setStatusBarColor(0xFF0B1220);
        window.setNavigationBarColor(0xFF070B12);

        webView = new WebView(this);
        webView.setBackgroundColor(0xFF0B1220);
        setContentView(webView);

        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);
        s.setTextZoom(100);

        webView.setWebChromeClient(new WebChromeClient());
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                if (url.startsWith(BASE_URL)) {
                    return serveAsset(url.substring(BASE_URL.length()));
                }
                return null;
            }
        });

        webView.addJavascriptInterface(new Bridge(), "Shiji");
        webView.loadUrl(BASE_URL + "index.html");
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (webView != null) {
            webView.evaluateJavascript("window.ShijiOnResume && window.ShijiOnResume()", null);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.removeJavascriptInterface("Shiji");
            webView.destroy();
        }
        super.onDestroy();
    }

    // ---------------- WebView 本地资源 ----------------

    private WebResourceResponse serveAsset(String rawPath) {
        try {
            String path = rawPath.split("[?#]")[0];
            path = URLDecoder.decode(path, "UTF-8");
            while (path.startsWith("/")) path = path.substring(1);
            if (path.length() == 0 || path.endsWith("/")) path = path + "index.html";
            if (path.contains("..")) return null;

            InputStream in = getAssets().open(path);
            Map<String, String> headers = new HashMap<>();
            headers.put("Access-Control-Allow-Origin", "*");
            headers.put("Cache-Control", "no-cache");
            return new WebResourceResponse(mimeOf(path), null, 200, "OK", headers, in);
        } catch (Exception e) {
            return null;
        }
    }

    private String mimeOf(String path) {
        String p = path.toLowerCase(Locale.US);
        if (p.endsWith(".html")) return "text/html";
        if (p.endsWith(".js") || p.endsWith(".mjs")) return "text/javascript";
        if (p.endsWith(".css")) return "text/css";
        if (p.endsWith(".json") || p.endsWith(".webmanifest")) return "application/json";
        if (p.endsWith(".png")) return "image/png";
        if (p.endsWith(".svg")) return "image/svg+xml";
        if (p.endsWith(".woff2")) return "font/woff2";
        return "application/octet-stream";
    }

    // ---------------- 权限 ----------------

    private boolean hasUsageAccess() {
        try {
            AppOpsManager ops = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            if (ops == null) return false;
            int mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(), getPackageName());
            return mode == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------------- JS Bridge ----------------

    private class Bridge {
        @JavascriptInterface
        public boolean isGranted() {
            return hasUsageAccess();
        }

        @JavascriptInterface
        public void openSettings() {
            try {
                Intent i = new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception e2) {
                    runOnUiThread(new Runnable() {
                        public void run() {
                            Toast.makeText(MainActivity.this, "无法打开设置，请手动到「设置 → 应用 → 特殊权限」开启", Toast.LENGTH_LONG).show();
                        }
                    });
                }
            }
        }

        @JavascriptInterface
        public String loadUsage(int days) {
            try {
                return queryUsageJson(days).toString();
            } catch (Exception e) {
                try {
                    JSONObject err = new JSONObject();
                    err.put("ok", false);
                    err.put("reason", "error");
                    err.put("message", String.valueOf(e.getMessage()));
                    return err.toString();
                } catch (Exception ignore) {
                    return "{\"ok\":false,\"reason\":\"error\"}";
                }
            }
        }
    }

    // ---------------- 统计核心 ----------------

    private JSONObject queryUsageJson(int days) throws Exception {
        JSONObject out = new JSONObject();
        if (!hasUsageAccess()) {
            out.put("ok", false);
            out.put("reason", "permission");
            return out;
        }
        if (days < 1) days = 1;
        if (days > 400) days = 400;

        UsageStatsManager usm = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
        if (usm == null) {
            out.put("ok", false);
            out.put("reason", "unavailable");
            return out;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        long todayStart = cal.getTimeInMillis();
        long begin = todayStart - (long) (days - 1) * 86400000L;
        long now = System.currentTimeMillis();

        // 直接使用系统按天聚合的前台时长，但需要处理跨零点的记录：
        // 每条 UsageStats 可能覆盖多天（如昨晚 23:00 → 今早 01:00），
        // 按时间比例拆分 totalTimeInForeground 到各天，避免多算或漏算。
        Map<String, long[]> perAppPerDay = new HashMap<>();   // pkg -> long[days]
        Map<String, Long> lastUsed = new HashMap<>();

        Set<String> excluded = new HashSet<>();
        excluded.add(getPackageName());
        excluded.add("com.android.systemui");
        excluded.add("com.android.settings");
        for (String home : launcherPackages()) excluded.add(home);

        List<android.app.usage.UsageStats> stats = usm.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY, begin, now);
        if (stats != null) {
            for (android.app.usage.UsageStats us : stats) {
                String pkg = us.getPackageName();
                long total = us.getTotalTimeInForeground();
                if (pkg == null || total <= 0) continue;

                long first = us.getFirstTimeStamp();
                long last = us.getLastTimeUsed();
                if (last <= first) last = first + total;
                if (last < begin || first > now) continue;

                // 把 [first, last] 裁剪到 [begin, now]
                long segStart = Math.max(first, begin);
                long segEnd = Math.min(last, now);
                if (segEnd <= segStart) continue;

                // 按自然日边界拆分
                // 精确：逐天累加该段在前台占用的时间
                long cursor = segStart;
                while (cursor < segEnd) {
                    long nextMidnight = nextMidnightTs(cursor);
                    long dayEndTs = Math.min(nextMidnight, segEnd);
                    long slice = dayEndTs - cursor;
                    int idx = dayIndex(cursor, begin, days);
                    if (idx >= 0 && idx < days && slice > 0) {
                        long[] arr = perAppPerDay.get(pkg);
                        if (arr == null) {
                            arr = new long[days];
                            perAppPerDay.put(pkg, arr);
                        }
                        // 按比例分摊 totalTimeInForeground
                        long share = total * slice / Math.max(1, (last - first));
                        arr[idx] += share;
                    }
                    cursor = dayEndTs;
                }

                long lu = us.getLastTimeUsed();
                Long previous = lastUsed.get(pkg);
                if (previous == null || lu > previous) lastUsed.put(pkg, lu);
            }
        }

        PackageManager pm = getPackageManager();
        JSONArray daysArr = new JSONArray();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);

        for (int d = 0; d < days; d++) {
            JSONObject dayObj = new JSONObject();
            long dayStart = begin + (long) d * 86400000L;
            dayObj.put("date", fmt.format(new Date(dayStart)));

            JSONArray appsArr = new JSONArray();
            long total = 0;
            List<String> pkgs = new ArrayList<>(perAppPerDay.keySet());
            for (String pkg : pkgs) {
                long ms = perAppPerDay.get(pkg)[d];
                if (ms < 1000L) continue;
                if (excluded.contains(pkg)) continue;
                JSONObject app = new JSONObject();
                ApplicationInfo ai = null;
                try {
                    ai = pm.getApplicationInfo(pkg, 0);
                } catch (Exception ignore) { }
                app.put("pkg", pkg);
                app.put("label", labelOf(pm, pkg, ai));
                app.put("ms", ms);
                app.put("cat", categoryOf(pkg, ai));
                app.put("launches", 0);
                Long lu = lastUsed.get(pkg);
                app.put("last", lu != null ? lu : 0L);
                appsArr.put(app);
                total += ms;
            }

            JSONArray sorted = sortApps(appsArr);
            dayObj.put("total", total);
            dayObj.put("apps", sorted);
            daysArr.put(dayObj);
        }

        out.put("ok", true);
        out.put("days", daysArr);
        out.put("generatedAt", now);
        return out;
    }

    private JSONArray sortApps(JSONArray arr) throws Exception {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) list.add(arr.getJSONObject(i));
        Collections.sort(list, new Comparator<JSONObject>() {
            public int compare(JSONObject a, JSONObject b) {
                try {
                    return Long.compare(b.getLong("ms"), a.getLong("ms"));
                } catch (Exception e) {
                    return 0;
                }
            }
        });
        JSONArray res = new JSONArray();
        for (JSONObject o : list) res.put(o);
        return res;
    }

    private String labelOf(PackageManager pm, String pkg, ApplicationInfo ai) {
        String known = KNOWN_APP_NAMES.get(pkg);
        if (known != null) return known;
        if (ai != null) {
            try {
                String label = String.valueOf(pm.getApplicationLabel(ai));
                if (label.length() > 0 && !label.equals(pkg)) return label;
            } catch (Exception ignore) { }
        }
        return pkg;
    }

    private int dayIndex(long ts, long begin, int days) {
        long diff = ts - begin;
        if (diff < 0) return -1;
        int idx = (int) (diff / 86400000L);
        if (idx >= days) idx = days - 1;
        return idx;
    }

    /** 返回 ts 所在自然日的下一个零点时间戳（本地时区）。 */
    private long nextMidnightTs(long ts) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(ts);
        cal.add(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private Set<String> launcherPackages() {
        Set<String> set = new HashSet<>();
        try {
            Intent home = new Intent(Intent.ACTION_MAIN);
            home.addCategory(Intent.CATEGORY_HOME);
            for (android.content.pm.ResolveInfo ri : getPackageManager().queryIntentActivities(home, 0)) {
                set.add(ri.activityInfo.packageName);
            }
        } catch (Exception ignore) { }
        return set;
    }

    // ---------------- 分类 ----------------

    private String categoryOf(String pkg, ApplicationInfo ai) {
        String p = pkg.toLowerCase(Locale.US);
        String label = "";
        if (ai != null) {
            try { label = String.valueOf(getPackageManager().getApplicationLabel(ai)); } catch (Exception ignore) { }
        }

        if (p.contains("game") || p.contains("miHoYo") || p.contains("hypergryph")
                || p.contains("tencent.tmgp") || p.contains("netease.games")
                || label.contains("游戏")) return "游戏";

        String[] social = {"com.tencent.mm", "com.tencent.mobileqq", "com.sina.weibo",
                "com.xingin.xhs", "com.zhihu.android", "org.telegram.messenger",
                "com.whatsapp", "com.facebook.katana", "com.instagram.android",
                "com.twitter.android", "com.discord"};
        for (String s : social) if (p.equals(s)) return "社交";

        String[] video = {"com.ss.android.ugc.aweme", "tv.danmaku.bili", "com.tencent.qqlive",
                "com.qiyi.video", "com.youku.phone", "com.zhiliaoapp.musically",
                "com.google.android.youtube", "com.netflix.mediaclient",
                "com.hunantv.imgo.activity", "com.ss.android.article.video"};
        for (String s : video) if (p.equals(s)) return "视频";

        String[] music = {"com.netease.cloudmusic", "com.tencent.qqmusic", "com.kugou.android",
                "com.ximalaya.ting.android", "com.google.android.apps.youtube.music",
                "com.spotify.music"};
        for (String s : music) if (p.equals(s)) return "音乐";

        String[] shop = {"com.taobao.taobao", "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
                "com.tmall.wireless", "com.taobao.idlefish", "com.amazon.mShop.android.shopping"};
        for (String s : shop) if (p.equals(s)) return "购物";

        String[] read = {"com.tencent.weread", "com.duokan.reader", "com.ss.android.article.news",
                "com.iflytek.reading", "com.qidian.QDReader", "com.phoenix.read"};
        for (String s : read) if (p.equals(s)) return "阅读资讯";

        if (p.contains("browser") || p.contains("chrome") || p.contains("firefox")) return "浏览器";

        String[] tools = {"com.bytedance.trae.cn", "ai.x.grok.bot", "cn.ticktick.task",
                "com.coloros.note", "com.heytap.market"};
        for (String s : tools) if (p.equals(s)) return "工具";

        if (Build.VERSION.SDK_INT >= 26 && ai != null) {
            switch (ai.category) {
                case ApplicationInfo.CATEGORY_SOCIAL: return "社交";
                case ApplicationInfo.CATEGORY_VIDEO: return "视频";
                case ApplicationInfo.CATEGORY_AUDIO: return "音乐";
                case ApplicationInfo.CATEGORY_GAME: return "游戏";
                case ApplicationInfo.CATEGORY_NEWS: return "阅读资讯";
                case ApplicationInfo.CATEGORY_MAPS: return "出行";
                case ApplicationInfo.CATEGORY_PRODUCTIVITY: return "工具";
                default: break;
            }
        }
        if (p.contains("map") || p.contains("baidu") || p.contains("amap")) return "出行";
        if (p.contains("mail") || p.contains("office") || p.contains("note")) return "工具";
        return "其他";
    }
}
