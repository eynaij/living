package com.muxi;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** 早起森林主 Activity：WebView 壳 + JS Bridge + 闹钟调度 + 通知权限。 */
public class MainActivity extends android.app.Activity {

    private static final String PREFS = "zaolin_prefs";
    private static final String KEY_DATES = "checked_dates";       // JSON 数组 YYYY-MM-DD
    private static final String KEY_TIMES = "checked_times";       // JSON 对象 {YYYY-MM-DD: HH:mm}
    private static final String KEY_HOUR = "wake_hour";
    private static final String KEY_MINUTE = "wake_minute";
    private static final String KEY_END_HOUR = "end_hour";
    private static final String KEY_END_MINUTE = "end_minute";
    private static final String KEY_GROWTH = "growth";           // 成长值：浇水+1，断浇每天-1
    private static final String KEY_SETTLE = "last_settle";      // 上次成长值结算日 YYYY-MM-DD
    private static final int DAYS_PER_TREE = 90;                 // 连续 90 成长值结一棵树

    private static final String CHANNEL_ID = "zaolin_reminder";
    private static final String ACTION_REMINDER = "com.muxi.REMINDER";
    private static final int REQUEST_CODE_REMINDER = 1001;

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setUserAgentString(ws.getUserAgentString() + " ZaoLin/1.0");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }
        });

        // 注入 Bridge 对象 window.ZaoLin
        webView.addJavascriptInterface(new Bridge(), "ZaoLin");

        createNotificationChannel();
        scheduleReminder();

        webView.loadUrl("file:///android_asset/index.html");
    }

    // ------------------------------------------------------------------ Bridge

    private class Bridge {

        /** 今天是否已打卡？ */
        @android.webkit.JavascriptInterface
        public boolean isCheckedToday() {
            return getCheckedDates().contains(todayKey());
        }

        /** 仅在设定的打卡时间段内浇水，原生层再次校验防止绕过页面。 */
        @android.webkit.JavascriptInterface
        public String checkIn() {
            JSONObject result = new JSONObject();
            try {
                if (!isWithinCheckWindow()) {
                    result.put("ok", false);
                    result.put("message", "请在 " + checkWindowText() + " 内打卡");
                } else {
                    settleGrowth();
                    SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
                    Set<String> set = new HashSet<>(getCheckedDates());
                    boolean added = set.add(todayKey());
                    sp.edit().putString(KEY_DATES, new JSONArray(set).toString()).apply();
                    if (added) {
                        JSONObject times = getCheckTimes();
                        times.put(todayKey(), new SimpleDateFormat("HH:mm", Locale.US).format(new Date()));
                        sp.edit()
                                .putString(KEY_TIMES, times.toString())
                                .putInt(KEY_GROWTH, sp.getInt(KEY_GROWTH, 0) + 1)
                                .putString(KEY_SETTLE, todayKey())
                                .apply();
                        vibrate();
                    }
                    result.put("ok", true);
                    result.put("message", added ? "打卡成功，小树喝到水啦" : "今天已经打过卡了");
                }
                result.put("stats", buildStats());
            } catch (JSONException ignore) { }
            return result.toString();
        }

        /** 返回全部打卡记录 {"2026-11-20":"07:06",...}，旧记录无时间则为空串。 */
        @android.webkit.JavascriptInterface
        public String getRecords() {
            JSONObject out = new JSONObject();
            JSONObject times = getCheckTimes();
            try {
                for (String d : getCheckedDates()) out.put(d, times.optString(d, ""));
            } catch (JSONException ignore) { }
            return out.toString();
        }

        /** 返回当前完整统计：连续天数、总天数、树木阶段、打卡时间段等。 */
        @android.webkit.JavascriptInterface
        public String getStats() {
            return buildStats().toString();
        }

        /** 获取当前设定的打卡时间段。 */
        @android.webkit.JavascriptInterface
        public String getWakeTime() {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            try {
                JSONObject o = new JSONObject();
                o.put("hour", sp.getInt(KEY_HOUR, 7));
                o.put("minute", sp.getInt(KEY_MINUTE, 0));
                o.put("endHour", sp.getInt(KEY_END_HOUR, 8));
                o.put("endMinute", sp.getInt(KEY_END_MINUTE, 0));
                return o.toString();
            } catch (JSONException e) {
                return "{\"hour\":7,\"minute\":0,\"endHour\":8,\"endMinute\":0}";
            }
        }

        /** 设置打卡时间段（24 小时制），结束时间必须晚于开始时间。 */
        @android.webkit.JavascriptInterface
        public boolean setWakeTime(int hour, int minute, int endHour, int endMinute) {
            if (!validTime(hour, minute) || !validTime(endHour, endMinute)) return false;
            if (endHour * 60 + endMinute <= hour * 60 + minute) return false;
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_HOUR, hour)
                    .putInt(KEY_MINUTE, minute)
                    .putInt(KEY_END_HOUR, endHour)
                    .putInt(KEY_END_MINUTE, endMinute)
                    .apply();
            scheduleReminder();
            return true;
        }

        /** 是否能发通知。Android 13+ 若未授权需要用户手动到设置开启。 */
        @android.webkit.JavascriptInterface
        public boolean hasNotificationPermission() {
            if (Build.VERSION.SDK_INT >= 33) {
                return checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED;
            }
            return true;
        }

        /** 打开系统通知设置页面（用户需手动允许通知）。 */
        @android.webkit.JavascriptInterface
        public void openNotificationSettings() {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS);
                i.putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, getPackageName());
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                try {
                    Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    i.setData(android.net.Uri.parse("package:" + getPackageName()));
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(i);
                } catch (Exception ignore) { }
            }
        }

        /** 打开精确闹钟设置页面（Android 12+）。 */
        @android.webkit.JavascriptInterface
        public void openAlarmSettings() {
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignore) { }
        }

        /** Android 12+ 是否能设置精确闹钟。 */
        @android.webkit.JavascriptInterface
        public boolean canScheduleExactAlarm() {
            if (Build.VERSION.SDK_INT >= 31) {
                AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
                return am == null || am.canScheduleExactAlarms();
            }
            return true;
        }

        /** 重置所有数据（调试用）。 */
        @android.webkit.JavascriptInterface
        public void resetAll() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().clear().apply();
            scheduleReminder();
        }
    }

    // ------------------------------------------------------------------ 统计

    private JSONObject buildStats() {
        settleGrowth();
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> dates = getCheckedDates();
        String today = todayKey();
        boolean checkedToday = dates.contains(today);

        int total = dates.size();
        int streak = computeStreak(dates);

        // 成长模型：growth 浇水+1/断浇每天-1；每 90 成长值结一棵大树，之后培育新树苗
        int growth = sp.getInt(KEY_GROWTH, 0);
        int bigTrees = growth / DAYS_PER_TREE;   // 已结果的大树数
        int cur = growth % DAYS_PER_TREE;        // 当前培育中树苗的成长值
        boolean fruitJust = bigTrees > 0 && cur == 0; // 刚好结果，展示大树

        // 当前树苗阶段：0 种子 / 1 发芽(1-6) / 2 幼苗(7-29) / 3 小树(30-89) / 4 大树(结果)
        int treeStage;
        if (fruitJust) treeStage = 4;
        else if (cur == 0) treeStage = 0;
        else if (cur < 7) treeStage = 1;
        else if (cur < 30) treeStage = 2;
        else treeStage = 3;

        int progress = fruitJust ? 100 : (cur * 100) / DAYS_PER_TREE;

        // 萎缩中：昨天没打卡（已开始衰减），且今天还没补打卡
        Calendar y = Calendar.getInstance();
        y.add(Calendar.DAY_OF_MONTH, -1);
        boolean checkedYesterday = dates.contains(new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(y.getTime()));
        boolean withering = growth > 0 && !checkedToday && !checkedYesterday;

        try {
            JSONObject o = new JSONObject();
            o.put("checkedToday", checkedToday);
            o.put("total", total);
            o.put("streak", streak);
            o.put("treeStage", treeStage);
            o.put("progress", progress);
            o.put("growth", growth);
            o.put("bigTrees", bigTrees);
            o.put("curGrowth", cur);
            o.put("daysToFruit", DAYS_PER_TREE - cur);
            o.put("withering", withering);
            o.put("today", today);
            o.put("hour", sp.getInt(KEY_HOUR, 7));
            o.put("minute", sp.getInt(KEY_MINUTE, 0));
            o.put("endHour", sp.getInt(KEY_END_HOUR, 8));
            o.put("endMinute", sp.getInt(KEY_END_MINUTE, 0));
            o.put("windowOpen", isWithinCheckWindow());
            o.put("windowStatus", checkWindowStatus());
            o.put("windowText", checkWindowText());
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private static boolean validTime(int hour, int minute) {
        return hour >= 0 && hour <= 23 && minute >= 0 && minute <= 59;
    }

    /** -1 未到打卡时间，0 时间段内，1 已过打卡时间。 */
    private int checkWindowStatus() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        int start = sp.getInt(KEY_HOUR, 7) * 60 + sp.getInt(KEY_MINUTE, 0);
        int end = sp.getInt(KEY_END_HOUR, 8) * 60 + sp.getInt(KEY_END_MINUTE, 0);
        Calendar now = Calendar.getInstance();
        int current = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE);
        if (current < start) return -1;
        if (current > end) return 1;
        return 0;
    }

    private boolean isWithinCheckWindow() {
        return checkWindowStatus() == 0;
    }

    private String checkWindowText() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        return String.format(Locale.US, "%02d:%02d-%02d:%02d",
                sp.getInt(KEY_HOUR, 7), sp.getInt(KEY_MINUTE, 0),
                sp.getInt(KEY_END_HOUR, 8), sp.getInt(KEY_END_MINUTE, 0));
    }

    private Set<String> getCheckedDates() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = sp.getString(KEY_DATES, "[]");
        Set<String> out = new HashSet<>();
        try {
            JSONArray arr = new JSONArray(raw == null ? "[]" : raw);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (JSONException ignore) { }
        return out;
    }

    private JSONObject getCheckTimes() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        String raw = sp.getString(KEY_TIMES, "{}");
        try {
            return new JSONObject(raw == null ? "{}" : raw);
        } catch (JSONException ignore) {
            return new JSONObject();
        }
    }

    private static String todayKey() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new Date());
    }

    /** 计算连续早起天数（从今天或昨天开始向前数）。 */
    private static int computeStreak(Set<String> dates) {
        if (dates.isEmpty()) return 0;
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        Calendar cal = Calendar.getInstance();
        int streak = 0;

        // 如果今天已打卡，从今天开始；否则从昨天开始判断是否连续
        String today = fmt.format(cal.getTime());
        boolean checkedToday = dates.contains(today);
        if (!checkedToday) cal.add(Calendar.DAY_OF_MONTH, -1);

        while (dates.contains(fmt.format(cal.getTime()))) {
            streak++;
            cal.add(Calendar.DAY_OF_MONTH, -1);
        }
        return streak;
    }

    /**
     * 惰性结算成长值：从上次结算日到昨天为止，有打卡 +1，断浇 -1（最低 0）。
     * 今天不结算——今天还来得及打卡；打卡时单独 +1。老版本首次升级按连续天数迁移。
     */
    private void settleGrowth() {
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> dates = getCheckedDates();
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        String today = fmt.format(new Date());
        String settle = sp.getString(KEY_SETTLE, null);

        if (settle == null) {
            sp.edit()
                    .putInt(KEY_GROWTH, computeStreak(dates))
                    .putString(KEY_SETTLE, today)
                    .apply();
            return;
        }
        if (settle.compareTo(today) >= 0) return;

        int growth = sp.getInt(KEY_GROWTH, 0);
        try {
            Calendar cal = Calendar.getInstance();
            cal.setTime(fmt.parse(settle));
            while (true) {
                cal.add(Calendar.DAY_OF_MONTH, 1);
                String day = fmt.format(cal.getTime());
                if (day.compareTo(today) >= 0) break; // 只结算到昨天
                if (dates.contains(day)) growth++;
                else growth = Math.max(0, growth - 1);
            }
        } catch (Exception ignore) { }
        sp.edit().putInt(KEY_GROWTH, growth).putString(KEY_SETTLE, today).apply();
    }

    // ------------------------------------------------------------------ 通知 & 闹钟
    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (nm != null && nm.getNotificationChannel(CHANNEL_ID) == null) {
                NotificationChannel ch = new NotificationChannel(
                        CHANNEL_ID, "早起提醒", NotificationManager.IMPORTANCE_DEFAULT);
                ch.setDescription("每天起床时间前提醒你起床打卡");
                nm.createNotificationChannel(ch);
            }
        }
    }

    /** 调度（或更新）下一次起床提醒。默认起床时间 7:00，提前 10 分钟提醒。 */
    static void scheduleReminder(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, MODE_PRIVATE);
        int hour = sp.getInt(KEY_HOUR, 7);
        int minute = sp.getInt(KEY_MINUTE, 0);

        // 提前 10 分钟提醒
        int notifyHour = hour;
        int notifyMinute = minute - 10;
        if (notifyMinute < 0) {
            notifyMinute += 60;
            notifyHour--;
            if (notifyHour < 0) notifyHour = 23;
        }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, notifyHour);
        cal.set(Calendar.MINUTE, notifyMinute);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (!cal.after(Calendar.getInstance())) {
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        Intent intent = new Intent(ctx, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        PendingIntent pi = PendingIntent.getBroadcast(
                ctx, REQUEST_CODE_REMINDER, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        AlarmManager am = (AlarmManager) ctx.getSystemService(ALARM_SERVICE);
        if (am == null) return;
        am.cancel(pi);
        try {
            if (Build.VERSION.SDK_INT >= 31 && am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else if (Build.VERSION.SDK_INT >= 23) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } catch (SecurityException e) {
            // 用户没给精确闹钟权限则退回到不精确提醒
            am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    private void scheduleReminder() { scheduleReminder(this); }

    private void vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                VibratorManager vm = (VibratorManager) getSystemService(VIBRATOR_MANAGER_SERVICE);
                Vibrator v = vm == null ? null : vm.getDefaultVibrator();
                if (v != null)
                    v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
                if (v != null) {
                    if (Build.VERSION.SDK_INT >= 26)
                        v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE));
                    else v.vibrate(150);
                }
            }
        } catch (Exception ignore) { }
    }

    // Back key → 回到 WebView 历史而不是退出
    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
            return;
        }
        super.onBackPressed();
    }
}
