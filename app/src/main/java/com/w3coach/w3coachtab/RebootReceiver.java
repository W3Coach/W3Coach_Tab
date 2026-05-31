package com.w3coach.w3coachtab;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Wird per AlarmManager aufgerufen um nach einem Update neu zu starten.
 * Läuft im System-Prozess, unabhängig vom App-Prozess.
 */
public class RebootReceiver extends BroadcastReceiver {

    private static final String TAG = "RebootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i(TAG, "Reboot-Alarm ausgeloest");

        // Alarm canceln damit er nicht nochmal feuert
        android.app.AlarmManager am =
                (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent cancelIntent = new Intent(context, RebootReceiver.class);
        cancelIntent.setAction("com.w3coach.w3coachtab.REBOOT");
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                context, 0, cancelIntent,
                android.app.PendingIntent.FLAG_NO_CREATE |
                android.app.PendingIntent.FLAG_IMMUTABLE);
        if (pi != null && am != null) {
            am.cancel(pi);
            pi.cancel();
            Log.i(TAG, "Reboot-Alarm gecancelt");
        }

        DevicePolicyManager dpm =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin =
                new ComponentName(context, KioskAdminReceiver.class);
        if (dpm != null && dpm.isDeviceOwnerApp(context.getPackageName())) {
            dpm.reboot(admin);
        } else {
            Log.e(TAG, "Kein Device Owner – Reboot nicht moeglich");
        }
    }

    /** Plant einen Reboot in delayMs Millisekunden. */
    public static void schedule(Context ctx, long delayMs) {
        android.app.AlarmManager am =
                (android.app.AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, RebootReceiver.class);
        intent.setAction("com.w3coach.w3coachtab.REBOOT");
        android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                ctx, 0, intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT |
                android.app.PendingIntent.FLAG_IMMUTABLE);
        if (am != null) {
            am.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs, pi);
            Log.i(TAG, "Reboot geplant in " + delayMs / 1000 + "s");
        }
    }
}
