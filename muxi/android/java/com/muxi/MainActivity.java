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
    private static final String KEY_HOUR = "wake_hour";
    private static final String KEY_MINUTE = "wake_minute";

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
        ws.setAllowFileAccessFromFileURLs(false);
        ws.setAllowUniversalAccessFromFileURLs(false);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
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

        webView.loadUrl("https://appassets.androidplatform.net/assets/index.html");
    }

    // ------------------------------------------------------------------ Bridge

    private class Bridge {

        /** 今天是否已打卡？ */
        @android.webkit.JavascriptInterface
        public boolean isCheckedToday() {
            return getCheckedDates().contains(todayKey());
        }

        /** 打卡浇水。返回更新后的统计 JSON。 */
        @android.webkit.JavascriptInterface
        public String checkIn() {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            Set<String> set = new HashSet<>(getCheckedDates());
            boolean added = set.add(todayKey());
            sp.edit().putString(KEY_DATES, new JSONArray(set).toString()).apply();
            if (added) vibrate();
            return buildStats().toString();
        }

        /** 返回当前完整统计：连续天数、总天数、树木阶段、起床时间等。 */
        @android.webkit.JavascriptInterface
        public String getStats() {
            return buildStats().toString();
        }

        /** 获取当前设定的起床时间 {"hour":7,"minute":0} */
        @android.webkit.JavascriptInterface
        public String getWakeTime() {
            SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
            try {
                JSONObject o = new JSONObject();
                o.put("hour", sp.getInt(KEY_HOUR, 7));
                o.put("minute", sp.getInt(KEY_MINUTE, 0));
                return o.toString();
            } catch (JSONException e) {
                return "{\"hour\":7,\"minute\":0}";
            }
        }

        /** 设置起床时间（24 小时制）。并立即重新调度闹钟。 */
        @android.webkit.JavascriptInterface
        public void setWakeTime(int hour, int minute) {
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return;
            getSharedPreferences(PREFS, MODE_PRIVATE)
                    .edit()
                    .putInt(KEY_HOUR, hour)
                    .putInt(KEY_MINUTE, minute)
                    .apply();
            scheduleReminder();
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
        SharedPreferences sp = getSharedPreferences(PREFS, MODE_PRIVATE);
        Set<String> dates = getCheckedDates();
        String today = todayKey();
        boolean checkedToday = dates.contains(today);

        int total = dates.size();
        int streak = computeStreak(dates);

        // 树木阶段：0 种子 / 1 发芽(1-6) / 2 幼苗(7-29) / 3 小树(30-99) / 4 大树(100+)
        int treeStage;
        if (total == 0) treeStage = 0;
        else if (total < 7) treeStage = 1;
        else if (total < 30) treeStage = 2;
        else if (total < 100) treeStage = 3;
        else treeStage = 4;

        // 当前阶段的进度百分比
        int progress;
        switch (treeStage) {
            case 0: progress = 0; break;
            case 1: progress = (total * 100) / 7; break;
            case 2: progress = ((total - 7) * 100) / 23; break;
            case 3: progress = ((total - 30) * 100) / 70; break;
            default: progress = Math.min(100, ((total - 100) * 100) / 265); break;
        }
        if (progress > 100) progress = 100;

        try {
            JSONObject o = new JSONObject();
            o.put("checkedToday", checkedToday);
            o.put("total", total);
            o.put("streak", streak);
            o.put("treeStage", treeStage);
            o.put("progress", progress);
            o.put("today", today);
            o.put("hour", sp.getInt(KEY_HOUR, 7));
            o.put("minute", sp.getInt(KEY_MINUTE, 0));
            return o;
        } catch (JSONException e) {
            return new JSONObject();
        }
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
