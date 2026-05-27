package com.w3coach.w3coachtab;

import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())
                && !"android.intent.action.QUICKBOOT_POWERON".equals(intent.getAction())) {
            return;
        }

        // Setup-Wizard sofort beim Boot deaktivieren – bevor er starten kann
        disableSetupWizard(context);

        // App starten
        Intent launch = new Intent(context, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(launch);

        // Auto-Update Job planen
        AutoUpdateJob.schedule(context);
    }

    private void disableSetupWizard(Context context) {
        DevicePolicyManager dpm = (DevicePolicyManager)
                context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(context, KioskAdminReceiver.class);

        if (dpm == null || !dpm.isDeviceOwnerApp(context.getPackageName())) return;

        String[] setupPackages = {
            "com.google.android.tungsten.setupwraith",
            "com.google.android.partnersetup",
            "com.google.android.chromecast.setupcustomization"
        };
        for (String pkg : setupPackages) {
            try {
                dpm.setApplicationHidden(admin, pkg, true);
            } catch (Exception ignored) {}
        }
    }
}
