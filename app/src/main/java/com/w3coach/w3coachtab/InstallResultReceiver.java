package com.w3coach.w3coachtab;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

public class InstallResultReceiver extends BroadcastReceiver {

    private static final String TAG = "InstallResultReceiver";

    /** Nach erfolgreicher Installation: dieses Package wurde installiert. */
    public static String   postInstallPackage  = null;
    /** Nach erfolgreicher Installation: diesen Callback ausführen. */
    public static Runnable postInstallCallback = null;

    @Override
    public void onReceive(Context context, Intent intent) {
        int status = intent.getIntExtra(
                PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);

        switch (status) {
            case PackageInstaller.STATUS_SUCCESS:
                Log.i(TAG, "Installation erfolgreich: " + postInstallPackage);
                if (postInstallCallback != null) {
                    Runnable cb = postInstallCallback;
                    postInstallPackage  = null;
                    postInstallCallback = null;
                    // Kurz warten damit Package Manager die Installation abschließt
                    new Handler(Looper.getMainLooper()).postDelayed(cb, 1000);
                }
                break;

            case PackageInstaller.STATUS_PENDING_USER_ACTION:
                Intent confirmIntent = intent.getParcelableExtra(Intent.EXTRA_INTENT);
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(confirmIntent);
                }
                break;

            default:
                Log.e(TAG, "Installation fehlgeschlagen (status=" + status + "): " + message);
                postInstallPackage  = null;
                postInstallCallback = null;
                break;
        }
    }
}
