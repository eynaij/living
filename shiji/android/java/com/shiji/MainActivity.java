package com.shiji;

import android.app.Activity;
import android.app.AppOpsManager;
import android.app.usage.UsageEvents;
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

        // 每天每应用的前台毫秒数
        Map<String, long[]> perAppPerDay = new HashMap<>();   // pkg -> long[days]
        Map<String, int[]> launchesPerDay = new HashMap<>();  // pkg -> int[days]
        Map<String, Long> lastUsed = new HashMap<>();

        Set<String> excluded = new HashSet<>();
        excluded.add(getPackageName());
        excluded.add("com.android.systemui");
        excluded.add("com.android.settings");
        for (String home : launcherPackages()) excluded.add(home);

        UsageEvents events = usm.queryEvents(begin, now);
        if (events != null) {
            UsageEvents.Event e = new UsageEvents.Event();
            Map<String, Long> openSince = new HashMap<>();
            while (events.hasNextEvent()) {
                events.getNextEvent(e);
                String pkg = e.getPackageName();
                if (pkg == null) continue;
                int type = e.getEventType();
                long ts = e.getTimeStamp();

                if (type == UsageEvents.Event.ACTIVITY_RESUMED || type == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                    openSince.put(pkg, ts);
                    int idx = dayIndex(ts, begin, days);
                    if (idx >= 0 && idx < days) {
                        int[] arr = launchesPerDay.get(pkg);
                        if (arr == null) { arr = new int[days]; launchesPerDay.put(pkg, arr); }
                        arr[idx]++;
                    }
                } else if (type == UsageEvents.Event.ACTIVITY_PAUSED
                        || type == UsageEvents.Event.MOVE_TO_BACKGROUND
                        || type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                        || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                    if (type == UsageEvents.Event.SCREEN_NON_INTERACTIVE
                            || type == UsageEvents.Event.DEVICE_SHUTDOWN) {
                        // 息屏 / 关机：结束所有计时
                        for (Map.Entry<String, Long> entry : openSince.entrySet()) {
                            addDuration(perAppPerDay, entry.getKey(), entry.getValue(), ts, begin, days);
                        }
                        openSince.clear();
                    } else {
                        Long start = openSince.remove(pkg);
                        if (start != null) addDuration(perAppPerDay, pkg, start, ts, begin, days);
                    }
                }
            }
            // 仍未结束的会话，记到当前时刻
            for (Map.Entry<String, Long> entry : openSince.entrySet()) {
                addDuration(perAppPerDay, entry.getKey(), entry.getValue(), now, begin, days);
            }
        }

        // 兜底：queryEvents 可能为空（部分 ROM），用 queryUsageStats 补
        if (perAppPerDay.isEmpty()) {
            List<android.app.usage.UsageStats> stats = usm.queryUsageStats(
                    UsageStatsManager.INTERVAL_DAILY, begin, now);
            if (stats != null) {
                for (android.app.usage.UsageStats us : stats) {
                    long t = us.getTotalTimeInForeground();
                    if (t <= 0) continue;
                    long[] arr = perAppPerDay.get(us.getPackageName());
                    if (arr == null) { arr = new long[days]; perAppPerDay.put(us.getPackageName(), arr); }
                    arr[days - 1] += t;
                    lastUsed.put(us.getPackageName(), us.getLastTimeUsed());
                }
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
                String label = ai != null ? String.valueOf(pm.getApplicationLabel(ai)) : pkg;
                if (ai != null && (ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0
                        && label.toLowerCase(Locale.US).contains("android")) {
                    // 过滤掉明显的系统组件
                }
                app.put("pkg", pkg);
                app.put("label", label);
                app.put("ms", ms);
                app.put("cat", categoryOf(pkg, ai));
                int[] la = launchesPerDay.get(pkg);
                app.put("launches", la != null ? la[d] : 0);
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

    private void addDuration(Map<String, long[]> map, String pkg, long start, long end,
                             long begin, int days) {
        if (end <= start) return;
        long dur = Math.min(end - start, 6L * 3600L * 1000L); // 单段封顶 6 小时，防脏数据
        int idx = dayIndex(start, begin, days);
        if (idx < 0 || idx >= days) return;
        long[] arr = map.get(pkg);
        if (arr == null) { arr = new long[days]; map.put(pkg, arr); }
        arr[idx] += dur;
    }

    private int dayIndex(long ts, long begin, int days) {
        long diff = ts - begin;
        if (diff < 0) return -1;
        int idx = (int) (diff / 86400000L);
        if (idx >= days) idx = days - 1;
        return idx;
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
                "com.ss.android.ugc.aweme", "com.xingin.xhs", "com.zhihu.android",
                "org.telegram.messenger", "com.whatsapp", "com.facebook.katana",
                "com.instagram.android", "com.twitter.android", "com.discord"};
        for (String s : social) if (p.equals(s)) return "社交";

        String[] video = {"tv.danmaku.bili", "com.tencent.qqlive", "com.qiyi.video",
                "com.youku.phone", "com.zhiliaoapp.musically", "com.google.android.youtube",
                "com.netflix.mediaclient", "com.hunantv.imgo.activity", "com.ss.android.article.video"};
        for (String s : video) if (p.equals(s)) return "视频";

        String[] music = {"com.netease.cloudmusic", "com.tencent.qqmusic", "com.kugou.android",
                "com.ximalaya.ting.android", "com.google.android.apps.youtube.music",
                "com.spotify.music"};
        for (String s : music) if (p.equals(s)) return "音乐";

        String[] shop = {"com.taobao.taobao", "com.jingdong.app.mall", "com.xunmeng.pinduoduo",
                "com.tmall.wireless", "com.taobao.idlefish", "com.amazon.mShop.android.shopping"};
        for (String s : shop) if (p.equals(s)) return "购物";

        String[] read = {"com.tencent.weread", "com.duokan.reader", "com.ss.android.article.news",
                "com.iflytek.reading", "com.qidian.QDReader"};
        for (String s : read) if (p.equals(s)) return "阅读资讯";

        if (p.contains("browser") || p.contains("chrome") || p.contains("firefox")) return "浏览器";

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
