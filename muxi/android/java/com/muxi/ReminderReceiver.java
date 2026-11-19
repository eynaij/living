package com.muxi;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;

/** 每天起床时间前推送提醒通知。 */
public class ReminderReceiver extends BroadcastReceiver {

    private static final String CHANNEL_ID = "zaolin_reminder";
    private static final int NOTIFY_ID = 1;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Intent launch = new Intent(ctx, MainActivity.class);
        launch.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                ctx, 0, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(ctx)
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setContentTitle("该起床啦 🌅")
                .setContentText("小树等你浇水，坚持早起，它会慢慢长大～")
                .setStyle(new Notification.BigTextStyle().bigText("今天也要坚持早起打卡哦，小树正在等你浇水呢！"))
                .setPriority(Notification.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .setColor(Color.parseColor("#4CAF50"));

        if (Build.VERSION.SDK_INT >= 26) {
            b.setChannelId(CHANNEL_ID);
        }

        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NOTIFY_ID, b.build());

        MainActivity.scheduleReminder(ctx);
    }
}
