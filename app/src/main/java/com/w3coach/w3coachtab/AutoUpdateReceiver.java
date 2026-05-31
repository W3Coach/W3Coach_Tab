package com.w3coach.w3coachtab;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.concurrent.Executors;

/**
 * Ersetzt AutoUpdateJob – läuft per AlarmManager statt JobScheduler
 * damit das Intervall nicht vom Gerät auf 4h hochgerundet wird.
 */
public class AutoUpdateReceiver extends BroadcastReceiver {

    private static final String TAG    = "AutoUpdateReceiver";
    public  static final String ACTION = "com.w3coach.w3coachtab.AUTO_UPDATE";

    @Override
    public void onReceive(Context context, Intent intent) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        if (!prefs.getBoolean(AutoUpdateJob.PREF_ENABLED, false)) {
            Log.i(TAG, "Auto-Update deaktiviert");
            return;
        }

        Log.i(TAG, "Auto-Update Check gestartet");

        // Nächsten Alarm sofort planen damit er nicht verloren geht
        schedule(context);

        // Update-Check im Hintergrund
        final PendingResult result = goAsync();
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                GithubUpdateChecker.UpdateInfo info =
                        GithubUpdateChecker.checkForUpdate(
                                BuildConfig.UPDATE_URL, BuildConfig.VERSION_CODE);

                if (info == null) {
                    Log.i(TAG, "Kein Update verfügbar");
                    result.finish();
                    return;
                }

                Log.i(TAG, "Update gefunden: " + info.tagName);
                new Prefs(context).setLastUpdateTimestamp(System.currentTimeMillis());

                File apk = new File(context.getCacheDir(), "w3coachtab_update.apk");
                if (apk.exists()) apk.delete();
                GithubUpdateChecker.downloadApk(info.downloadUrl, apk);
                SilentInstaller.install(context, apk);
                apk.deleteOnExit();

            } catch (Exception e) {
                Log.e(TAG, "Update-Fehler: " + e.getMessage(), e);
            } finally {
                result.finish();
            }
        });
    }

    /** Plant den nächsten Update-Check in intervalHours Stunden. */
    public static void schedule(Context ctx) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        boolean enabled = prefs.getBoolean(AutoUpdateJob.PREF_ENABLED, false);

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, AutoUpdateReceiver.class);
        intent.setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (am == null) return;
        am.cancel(pi); // alten Alarm canceln

        if (!enabled) { Log.i(TAG, "Auto-Update deaktiviert – kein Alarm"); return; }

        int hours = prefs.getInt(Prefs.KEY_UPDATE_INTERVAL, 12);
        if (hours < AutoUpdateJob.MIN_INTERVAL_HOURS) hours = AutoUpdateJob.MIN_INTERVAL_HOURS;
        long intervalMs = hours * 60L * 60L * 1000L;

        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                System.currentTimeMillis() + intervalMs, pi);
        Log.i(TAG, "Nächster Update-Check in " + hours + "h");
    }

    /** Bricht den geplanten Alarm ab. */
    public static void cancel(Context ctx) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, AutoUpdateReceiver.class);
        intent.setAction(ACTION);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, intent,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (pi != null && am != null) { am.cancel(pi); pi.cancel(); }
    }
}
