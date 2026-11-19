package com.muxi;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** 开机/解锁后重新调度起床提醒闹钟。 */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? "" : intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            MainActivity.scheduleReminder(ctx);
        }
    }
}
