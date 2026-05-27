package com.w3coach.w3coachtab;

import android.annotation.SuppressLint;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SilentInstaller {

    private static final String TAG = "SilentInstaller";

    /** Installiert die eigene App (Auto-Update). */
    public static void install(Context context, File apkFile) throws IOException {
        install(context, apkFile, context.getPackageName());
    }

    /** Installiert eine beliebige APK (z.B. TeamViewer QuickSupport). */
    public static void installExternal(Context context, File apkFile) throws IOException {
        install(context, apkFile, null);
    }

    private static void install(Context context, File apkFile, String packageName)
            throws IOException {
        PackageInstaller packageInstaller =
                context.getPackageManager().getPackageInstaller();

        PackageInstaller.SessionParams params = new PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        if (packageName != null) params.setAppPackageName(packageName);

        int sessionId = packageInstaller.createSession(params);
        PackageInstaller.Session session = packageInstaller.openSession(sessionId);

        try (InputStream in  = new FileInputStream(apkFile);
             OutputStream out = session.openWrite(apkFile.getName(), 0, apkFile.length())) {
            byte[] buffer = new byte[65536];
            int len;
            while ((len = in.read(buffer)) != -1) out.write(buffer, 0, len);
            session.fsync(out);
        }

        Intent resultIntent = new Intent(context, InstallResultReceiver.class);
        resultIntent.setAction("com.w3coach.w3coachtab.INSTALL_COMPLETE");

        int flags = PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE;

        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                context, sessionId, resultIntent, flags);

        session.commit(pendingIntent.getIntentSender());
        session.close();
        Log.i(TAG, "Session committed (id=" + sessionId + ")");
    }
}
