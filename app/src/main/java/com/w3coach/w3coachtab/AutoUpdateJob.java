package com.w3coach.w3coachtab;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;
import android.util.Log;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AutoUpdateJob extends JobService {

    private static final String TAG    = "AutoUpdateJob";
    public  static final int    JOB_ID = 42;
    public  static final String PREF_ENABLED          = "autoUpdate";
    public  static final String PREF_INTERVAL_HOURS   = "updateIntervalHours";
    public  static final int    DEFAULT_INTERVAL_HOURS = 4;
    public  static final int    MIN_INTERVAL_HOURS     = 1;

    private ExecutorService executor;

    public static void schedule(Context context) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean enabled = prefs.getBoolean(PREF_ENABLED, false);

        JobScheduler scheduler =
                (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        if (scheduler == null) return;
        scheduler.cancel(JOB_ID);

        if (!enabled) { Log.i(TAG, "Auto-Update deaktiviert"); return; }

        int hours = prefs.getInt(PREF_INTERVAL_HOURS, DEFAULT_INTERVAL_HOURS);
        if (hours < MIN_INTERVAL_HOURS) hours = MIN_INTERVAL_HOURS;

        JobInfo job = new JobInfo.Builder(JOB_ID,
                new ComponentName(context, AutoUpdateJob.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPeriodic(hours * 60L * 60L * 1000L)
                .setPersisted(true)
                .build();

        if (scheduler.schedule(job) == JobScheduler.RESULT_SUCCESS)
            Log.i(TAG, "Job geplant: alle " + hours + "h");
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> runUpdate(params));
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        if (executor != null) executor.shutdownNow();
        return true;
    }

    public static void scheduleReboot(Context ctx, long delayMs) {
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            android.app.admin.DevicePolicyManager dpm =
                    (android.app.admin.DevicePolicyManager)
                    ctx.getSystemService(Context.DEVICE_POLICY_SERVICE);
            android.content.ComponentName admin =
                    new android.content.ComponentName(ctx, KioskAdminReceiver.class);
            if (dpm != null && dpm.isDeviceOwnerApp(ctx.getPackageName())) {
                dpm.reboot(admin);
            }
        }, delayMs);
    }

    public static long getDelayMillis(String time) {
        try {
            String[] parts = time.split(":");
            int hour   = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            java.util.Calendar now = java.util.Calendar.getInstance();
            java.util.Calendar target = (java.util.Calendar) now.clone();
            target.set(java.util.Calendar.HOUR_OF_DAY, hour);
            target.set(java.util.Calendar.MINUTE, minute);
            target.set(java.util.Calendar.SECOND, 0);
            if (target.before(now)) target.add(java.util.Calendar.DAY_OF_MONTH, 1);
            return target.getTimeInMillis() - now.getTimeInMillis();
        } catch (Exception e) {
            // Fallback: 03:00
            return 3 * 60 * 60 * 1000L;
        }
    }


    private void runUpdate(JobParameters params) {
        Context ctx = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
        if (!prefs.getBoolean(PREF_ENABLED, false)) { jobFinished(params, false); return; }

        try {
            GithubUpdateChecker.UpdateInfo info =
                    GithubUpdateChecker.checkForUpdate(BuildConfig.UPDATE_URL, BuildConfig.VERSION_CODE);

            if (info == null) { Log.i(TAG, "Aktuell"); jobFinished(params, false); return; }

            File apk = new File(ctx.getCacheDir(), "w3coachtab_update.apk");
            if (apk.exists()) apk.delete();
            GithubUpdateChecker.downloadApk(info.downloadUrl, apk);

            // Callback: nach erfolgreicher Installation Neustart auslösen
            Prefs appPrefs = new Prefs(ctx);
            InstallResultReceiver.postInstallPackage  = ctx.getPackageName();
            InstallResultReceiver.postInstallCallback = () -> {
                if (appPrefs.updateRebootNow()) {
                    scheduleReboot(ctx, 0);
                } else {
                    scheduleReboot(ctx, getDelayMillis(appPrefs.updateRebootTime()));
                }
            };

            SilentInstaller.install(ctx, apk);
            apk.deleteOnExit();

            jobFinished(params, false);
        } catch (Exception e) {
            Log.e(TAG, "Update-Fehler: " + e.getMessage(), e);
            jobFinished(params, true);
        }
    }
}
