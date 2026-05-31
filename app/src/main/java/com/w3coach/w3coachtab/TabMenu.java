package com.w3coach.w3coachtab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.LayoutInflater;
import android.webkit.WebView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.w3coach.w3coachtab.GithubUpdateChecker.UpdateInfo;

public class TabMenu {

    private final Activity activity;
    private final WebView webView;
    private final Prefs prefs;
    private final WireGuardManager wgManager;

    // Admin-Session
    private static final long ADMIN_TIMEOUT_MS = 60 * 1000L; // 1 Minute
    private boolean adminUnlocked = false;
    private long adminUnlockedAt  = 0;
    private final Handler adminTimeoutHandler = new Handler(Looper.getMainLooper());
    private Runnable adminTimeoutRunnable;

    public TabMenu(Activity activity, WebView webView, Prefs prefs) {
        this.activity   = activity;
        this.webView    = webView;
        this.prefs      = prefs;
        this.wgManager  = new WireGuardManager(activity);
    }

    public void show() {
        java.util.List<String> itemList = new java.util.ArrayList<>();
        java.util.List<Runnable> actionList = new java.util.ArrayList<>();

        itemList.add(activity.getString(R.string.menu_switch_url));
        actionList.add(() -> ((MainActivity) activity).switchUrl());

        itemList.add(WireGuardService.isConnected
                ? activity.getString(R.string.wg_disconnect)
                : activity.getString(R.string.wg_connect));
        actionList.add(() -> wgManager.toggle());

        // App-Shortcuts nur anzeigen wenn konfiguriert
        if (!prefs.app1Package().isEmpty()) {
            itemList.add(prefs.app1Label().isEmpty()
                    ? activity.getString(R.string.menu_app1) : prefs.app1Label());
            actionList.add(() -> launchApp(prefs.app1Package()));
        }
        if (!prefs.app2Package().isEmpty()) {
            itemList.add(prefs.app2Label().isEmpty()
                    ? activity.getString(R.string.menu_app2) : prefs.app2Label());
            actionList.add(() -> launchApp(prefs.app2Package()));
        }

        itemList.add(activity.getString(R.string.menu_about));
        actionList.add(this::showAbout);

        itemList.add(activity.getString(R.string.menu_system));
        actionList.add(this::showSystemMenu);

        String[] items = itemList.toArray(new String[0]);
        Runnable[] actions = actionList.toArray(new Runnable[0]);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.app_name)
                .setItems(items, (dialog, which) -> actions[which].run())
                .show();
    }

    // ── System-Untermenü (PIN-geschützt) ──────────────────────────────────────

    private void lockAdmin() {
        adminUnlocked = false;
        adminUnlockedAt = 0;
        if (adminTimeoutRunnable != null) {
            adminTimeoutHandler.removeCallbacks(adminTimeoutRunnable);
        }
    }

    private void resetAdminTimeout() {
        if (adminTimeoutRunnable != null) {
            adminTimeoutHandler.removeCallbacks(adminTimeoutRunnable);
        }
        adminTimeoutRunnable = this::lockAdmin;
        adminTimeoutHandler.postDelayed(adminTimeoutRunnable, ADMIN_TIMEOUT_MS);
    }

    private void showSystemMenu() {
        // Session prüfen
        if (adminUnlocked && (System.currentTimeMillis() - adminUnlockedAt) < ADMIN_TIMEOUT_MS) {
            resetAdminTimeout();
            openSystemMenu();
            return;
        }
        adminUnlocked = false;
        new PinDialog(activity, () -> {
            adminUnlocked = true;
            adminUnlockedAt = System.currentTimeMillis();
            resetAdminTimeout();
            openSystemMenu();
        }).show();
    }

    private void openSystemMenu() {
        String usbLabel = prefs.usbRestricted()
                ? activity.getString(R.string.menu_usb_unlock)
                : activity.getString(R.string.menu_usb_lock);

        String[] items = {
            activity.getString(R.string.menu_web),
            activity.getString(R.string.menu_display),
            activity.getString(R.string.menu_software),
            activity.getString(R.string.menu_updates),
            activity.getString(R.string.wg_configure),
            "──────────────────────",
            usbLabel,
            activity.getString(R.string.menu_reboot),
            activity.getString(R.string.menu_settings),
        };

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<String>(
                activity, android.R.layout.select_dialog_item, items) {
            @Override public boolean areAllItemsEnabled() { return false; }
            @Override public boolean isEnabled(int position) {
                return !items[position].startsWith("──");
            }
        };

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_system)
                .setAdapter(adapter, (dialog, which) -> {
                    resetAdminTimeout();
                    dialog.dismiss();
                    switch (which) {
                        case 0: showWebMenu();           break;
                        case 1: showDisplayMenu();       break;
                        case 2: showSoftwareMenu();      break;
                        case 3: showUpdatesMenu();       break;
                        case 4: wgManager.showConfigDialog(); openSystemMenu(); break;
                        // case 5: Trennlinie
                        case 6: toggleUsbRestriction();  openSystemMenu(); break;
                        case 7: confirmReboot();         break;
                        case 8: openSystemSettings();    break;
                    }
                })
                .setNegativeButton(R.string.close, (d, w) -> lockAdmin())
                .show();
    }

    // ── Web-Untermenü ─────────────────────────────────────────────────────────

    private void showWebMenu() {
        String[] items = {
            activity.getString(R.string.menu_url1) + "  " + truncate(prefs.url1()),
            activity.getString(R.string.menu_url2) + "  " + truncate(prefs.url2()),
            activity.getString(R.string.menu_url3) + "  " + truncate(prefs.url3()),
            activity.getString(R.string.menu_autologin),
            activity.getString(R.string.menu_clear_cache),
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_web)
                .setItems(items, (dialog, which) -> {
                    resetAdminTimeout();
                    switch (which) {
                        case 0: editUrl(1);          break;
                        case 1: editUrl(2);          break;
                        case 2: editUrl(3);          break;
                        case 3: showAutoLogin();     break;
                        case 4: clearWebViewCache(); break;
                    }
                })
                .setPositiveButton(R.string.menu_back, (d, w) -> openSystemMenu())
                .setNegativeButton(R.string.close, (d, w) -> lockAdmin())
                .show();
    }

    // ── Display-Untermenü ─────────────────────────────────────────────────────

    private void showDisplayMenu() {
        String[] items = {
            activity.getString(R.string.menu_zoom),
            activity.getString(R.string.menu_marquee),
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_display)
                .setItems(items, (dialog, which) -> {
                    resetAdminTimeout();
                    switch (which) {
                        case 0: showZoom();          break;
                        case 1: showMarqueeConfig(); break;
                    }
                })
                .setPositiveButton(R.string.menu_back, (d, w) -> openSystemMenu())
                .setNegativeButton(R.string.close, (d, w) -> lockAdmin())
                .show();
    }

    // ── Software-Untermenü ────────────────────────────────────────────────────

    private void showSoftwareMenu() {
        String[] items = {
            activity.getString(R.string.menu_app_shortcuts),
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_software)
                .setItems(items, (dialog, which) -> {
                    resetAdminTimeout();
                    if (which == 0) showAppShortcuts();
                })
                .setPositiveButton(R.string.menu_back, (d, w) -> openSystemMenu())
                .setNegativeButton(R.string.close, (d, w) -> lockAdmin())
                .show();
    }

    // ── Updates-Untermenü ─────────────────────────────────────────────────────

    private void showUpdatesMenu() {
        String[] items = {
            activity.getString(R.string.menu_autoupdate),
            activity.getString(R.string.menu_check_update),
        };
        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_updates)
                .setItems(items, (dialog, which) -> {
                    resetAdminTimeout();
                    switch (which) {
                        case 0: showAutoUpdate();  break;
                        case 1: checkUpdateNow();  break;
                    }
                })
                .setPositiveButton(R.string.menu_back, (d, w) -> openSystemMenu())
                .setNegativeButton(R.string.close, (d, w) -> lockAdmin())
                .show();
    }

    // ── Zoom ──────────────────────────────────────────────────────────────────

    private void showZoom() {
        String[] levels = {"75%","80%","85%","90%","95%","100%","105%","110%","115%","120%","125%"};
        int[] values    = { 75,   80,   85,   90,   95,   100,   105,   110,   115,   120,   125};
        int current = prefs.zoom();
        int selected = 5;
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { selected = i; break; }
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_zoom)
                .setSingleChoiceItems(levels, selected, (d, which) -> {
                    prefs.setZoom(values[which]);
                    webView.getSettings().setTextZoom(values[which]);
                    d.dismiss();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── URL bearbeiten ────────────────────────────────────────────────────────

    private void editUrl(int num) {
        String current;
        switch (num) {
            case 2: current = prefs.url2(); break;
            case 3: current = prefs.url3(); break;
            default: current = prefs.url1(); break;
        }

        EditText et = new EditText(activity);
        et.setText(current);
        et.setSingleLine(true);

        new AlertDialog.Builder(activity)
                .setTitle("URL " + num)
                .setView(et)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String url = et.getText().toString().trim();
                    if (url.isEmpty()) return;
                    switch (num) {
                        case 2: prefs.setUrl2(url); break;
                        case 3: prefs.setUrl3(url); break;
                        default: prefs.setUrl1(url); break;
                    }
                    if (num == 1) ((MainActivity) activity).loadCurrentUrl();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── AutoLogin ─────────────────────────────────────────────────────────────

    private void showAutoLogin() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 8);

        CheckBox cbEnabled = new CheckBox(activity);
        cbEnabled.setText(R.string.autologin_enable);
        cbEnabled.setChecked(prefs.autoLogin());
        layout.addView(cbEnabled);

        TextView tvUser = new TextView(activity);
        tvUser.setText(R.string.autologin_user);
        tvUser.setPadding(0, 16, 0, 0);
        layout.addView(tvUser);

        EditText etUser = new EditText(activity);
        etUser.setText(prefs.autoLoginUser());
        etUser.setSingleLine(true);
        etUser.setInputType(InputType.TYPE_CLASS_TEXT);
        layout.addView(etUser);

        TextView tvPass = new TextView(activity);
        tvPass.setText(R.string.autologin_pass);
        tvPass.setPadding(0, 8, 0, 0);
        layout.addView(tvPass);

        EditText etPass = new EditText(activity);
        etPass.setText(prefs.autoLoginPass());
        etPass.setSingleLine(true);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        layout.addView(etPass);

        CheckBox cbClick = new CheckBox(activity);
        cbClick.setText(R.string.autologin_click);
        cbClick.setChecked(prefs.autoLoginClick());
        cbClick.setPadding(0, 8, 0, 0);
        layout.addView(cbClick);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_autologin)
                .setView(layout)
                .setPositiveButton(R.string.save, (d, w) -> {
                    prefs.setAutoLogin(cbEnabled.isChecked());
                    prefs.setAutoLoginUser(etUser.getText().toString().trim());
                    prefs.setAutoLoginPass(etPass.getText().toString());
                    prefs.setAutoLoginClick(cbClick.isChecked());
                    ToastHelper.success(activity, activity.getString(R.string.autologin_saved));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Marquee-Konfiguration ─────────────────────────────────────────────────

    private void showMarqueeConfig() {
        String[] options = {"5 min","10 min","15 min","20 min","25 min","30 min",
                            activity.getString(R.string.marquee_off)};
        int[]    values  = {5, 10, 15, 20, 25, 30, 0};
        int current = prefs.marqueeDelay();
        int selected = 1; // 10 min default
        for (int i = 0; i < values.length; i++) {
            if (values[i] == current) { selected = i; break; }
        }

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_marquee)
                .setSingleChoiceItems(options, selected, (d, which) -> {
                    prefs.setMarqueeDelay(values[which]);
                    d.dismiss();
                    ToastHelper.success(activity, activity.getString(R.string.saved));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── App Shortcuts ─────────────────────────────────────────────────────────

    private void showAppShortcuts() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 8);

        // App 1
        TextView tv1 = new TextView(activity);
        tv1.setText("App 1 - " + activity.getString(R.string.menu_app_label));
        layout.addView(tv1);
        EditText etLabel1 = new EditText(activity);
        etLabel1.setText(prefs.app1Label());
        etLabel1.setSingleLine(true);
        layout.addView(etLabel1);

        TextView tv1p = new TextView(activity);
        tv1p.setText("App 1 - " + activity.getString(R.string.menu_app_package));
        tv1p.setPadding(0, 8, 0, 0);
        layout.addView(tv1p);
        EditText etPkg1 = new EditText(activity);
        etPkg1.setText(prefs.app1Package());
        etPkg1.setSingleLine(true);
        etPkg1.setHint("com.example.app");
        layout.addView(etPkg1);

        // App 2
        TextView tv2 = new TextView(activity);
        tv2.setText("App 2 - " + activity.getString(R.string.menu_app_label));
        tv2.setPadding(0, 16, 0, 0);
        layout.addView(tv2);
        EditText etLabel2 = new EditText(activity);
        etLabel2.setText(prefs.app2Label());
        etLabel2.setSingleLine(true);
        layout.addView(etLabel2);

        TextView tv2p = new TextView(activity);
        tv2p.setText("App 2 - " + activity.getString(R.string.menu_app_package));
        tv2p.setPadding(0, 8, 0, 0);
        layout.addView(tv2p);
        EditText etPkg2 = new EditText(activity);
        etPkg2.setText(prefs.app2Package());
        etPkg2.setSingleLine(true);
        etPkg2.setHint("com.example.app");
        layout.addView(etPkg2);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_app_shortcuts)
                .setView(layout)
                .setPositiveButton(R.string.save, (d, w) -> {
                    prefs.setApp1Label(etLabel1.getText().toString().trim());
                    prefs.setApp1Package(etPkg1.getText().toString().trim());
                    prefs.setApp2Label(etLabel2.getText().toString().trim());
                    prefs.setApp2Package(etPkg2.getText().toString().trim());
                    // Kiosk-Whitelist aktualisieren
                    if (activity instanceof MainActivity) {
                        ((MainActivity) activity).initKioskMode();
                    }
                    ToastHelper.success(activity, activity.getString(R.string.saved));
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── App starten ───────────────────────────────────────────────────────────

    private void launchApp(String packageName) {
        if (packageName == null || packageName.isEmpty()) {
            ToastHelper.warning(activity, activity.getString(R.string.app_not_configured));
            return;
        }
        PackageManager pm = activity.getPackageManager();
        Intent intent = pm.getLaunchIntentForPackage(packageName);
        if (intent != null) {
            activity.startActivity(intent);
        } else {
            ToastHelper.error(activity, activity.getString(R.string.app_not_found));
        }
    }

    // ── Auto-Update ───────────────────────────────────────────────────────────

    private void showAutoUpdate() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 8);

        CheckBox cbEnabled = new CheckBox(activity);
        cbEnabled.setText(R.string.menu_autoupdate);
        cbEnabled.setChecked(prefs.autoUpdate());
        layout.addView(cbEnabled);

        TextView tvInterval = new TextView(activity);
        tvInterval.setText(R.string.dlg_autoupdate_interval);
        tvInterval.setPadding(0, 16, 0, 0);
        layout.addView(tvInterval);
        EditText etInterval = new EditText(activity);
        etInterval.setInputType(InputType.TYPE_CLASS_NUMBER);
        etInterval.setText(String.valueOf(prefs.updateInterval()));
        etInterval.setSingleLine(true);
        layout.addView(etInterval);

        TextView tvReboot = new TextView(activity);
        tvReboot.setText(R.string.update_reboot_title);
        tvReboot.setPadding(0, 16, 0, 0);
        layout.addView(tvReboot);

        CheckBox cbRebootNow = new CheckBox(activity);
        cbRebootNow.setText(R.string.update_reboot_now);
        cbRebootNow.setChecked(prefs.updateRebootNow());
        layout.addView(cbRebootNow);

        TextView tvRebootTime = new TextView(activity);
        tvRebootTime.setText(R.string.update_reboot_time);
        tvRebootTime.setPadding(0, 8, 0, 0);
        layout.addView(tvRebootTime);

        EditText etRebootTime = new EditText(activity);
        etRebootTime.setInputType(InputType.TYPE_CLASS_TEXT);
        etRebootTime.setText(prefs.updateRebootTime());
        etRebootTime.setSingleLine(true);
        etRebootTime.setEnabled(!prefs.updateRebootNow());
        layout.addView(etRebootTime);

        cbRebootNow.setOnCheckedChangeListener((btn, checked) ->
                etRebootTime.setEnabled(!checked));

        new AlertDialog.Builder(activity)
                .setTitle(R.string.dlg_autoupdate_title)
                .setView(layout)
                .setPositiveButton(R.string.save, (d, w) -> {
                    boolean enabled = cbEnabled.isChecked();
                    int interval = AutoUpdateJob.DEFAULT_INTERVAL_HOURS;
                    try {
                        int v = Integer.parseInt(etInterval.getText().toString().trim());
                        if (v >= AutoUpdateJob.MIN_INTERVAL_HOURS) interval = v;
                    } catch (NumberFormatException ignored) {}
                    prefs.setAutoUpdate(enabled);
                    prefs.setUpdateInterval(interval);
                    prefs.setUpdateRebootNow(cbRebootNow.isChecked());
                    prefs.setUpdateRebootTime(etRebootTime.getText().toString().trim());
                    AutoUpdateJob.schedule(activity);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Manueller Update-Check ────────────────────────────────────────────────

    // ── Alle Dialoge schließen ────────────────────────────────────────────────

    private void closeAllDialogs() {
        // Schließt alle offenen AlertDialogs der Activity
        // damit der Countdown-Overlay sichtbar wird
        try {
            java.lang.reflect.Field f = activity.getClass()
                    .getSuperclass().getDeclaredField("mFragments");
            f.setAccessible(true);
        } catch (Exception ignored) {}
        // Einfachste zuverlässige Methode: onBackPressed simulieren
        // bis keine Dialoge mehr offen sind – Android schließt immer
        // den obersten Dialog zuerst
        new Handler(Looper.getMainLooper()).post(() -> {
            try { activity.onBackPressed(); } catch (Exception ignored) {}
        });
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { activity.onBackPressed(); } catch (Exception ignored) {}
        }, 100);
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            try { activity.onBackPressed(); } catch (Exception ignored) {}
        }, 200);
    }

    // ── Update-Check ──────────────────────────────────────────────────────────

    private void checkUpdateNow() {
        ToastHelper.info(activity, activity.getString(R.string.menu_check_update) + "…");
        new Thread(() -> {
            try {
                UpdateInfo info = GithubUpdateChecker.checkForUpdate(
                        BuildConfig.UPDATE_URL, BuildConfig.VERSION_CODE);
                new Handler(Looper.getMainLooper()).post(() -> {
                    if (info == null) {
                        ToastHelper.success(activity, activity.getString(R.string.update_none));
                        return;
                    }
                    new AlertDialog.Builder(activity)
                            .setTitle(R.string.update_available)
                            .setMessage("Version " + info.tagName)
                            .setPositiveButton(R.string.yes, (d, w) -> {
                                // Alle Dialoge schließen damit Countdown sichtbar ist
                                d.dismiss();
                                closeAllDialogs();
                                new Handler(Looper.getMainLooper()).postDelayed(
                                        () -> installUpdateNow(info), 300);
                            })
                            .setNegativeButton(R.string.cancel, null)
                            .show();
                });
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        ToastHelper.error(activity, "Update-Check fehlgeschlagen: " + e.getMessage()));
            }
        }).start();
    }

    private void installUpdateNow(UpdateInfo info) {
        ToastHelper.info(activity, activity.getString(R.string.update_installing));

        new Thread(() -> {
            try {
                java.io.File apk = new java.io.File(activity.getCacheDir(), "w3coachtab_update.apk");
                GithubUpdateChecker.downloadApk(info.downloadUrl, apk);

                if (prefs.updateRebootNow()) {
                    // Timestamp speichern
                    prefs.setLastUpdateTimestamp(System.currentTimeMillis());
                    RebootReceiver.schedule(activity, 20000);
                    // Countdown-Overlay anzeigen
                    new Handler(Looper.getMainLooper()).post(() -> showRebootCountdown(20));
                    // 20 Sekunden warten – Overlay bleibt sichtbar
                    Thread.sleep(20000);
                } else {
                    long delayMs = AutoUpdateJob.getDelayMillis(prefs.updateRebootTime());
                    RebootReceiver.schedule(activity, delayMs);
                    new Handler(Looper.getMainLooper()).post(() ->
                            ToastHelper.info(activity,
                                    activity.getString(R.string.reboot_scheduled,
                                            prefs.updateRebootTime())));
                }

                // Installation NACH dem Countdown
                SilentInstaller.install(activity, apk);

            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() ->
                        ToastHelper.error(activity, "Update fehlgeschlagen: " + e.getMessage()));
            }
        }).start();
    }

    private void showRebootCountdown(int seconds) {
        // Vollbild-Overlay mit Countdown
        android.widget.FrameLayout overlay = new android.widget.FrameLayout(activity);
        overlay.setBackgroundColor(0xDD0B615E);

        android.widget.TextView tv = new android.widget.TextView(activity);
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(32f);
        tv.setGravity(android.view.Gravity.CENTER);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        android.widget.FrameLayout.LayoutParams lp = new android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT);
        overlay.addView(tv, lp);

        // Overlay über WebView legen
        android.widget.FrameLayout root = activity.findViewById(android.R.id.content);
        root.addView(overlay);

        // Countdown-Handler
        Handler handler = new Handler(Looper.getMainLooper());
        final int[] remaining = {seconds};
        Runnable tick = new Runnable() {
            @Override public void run() {
                if (remaining[0] <= 0) return;
                tv.setText(activity.getString(R.string.reboot_countdown, remaining[0]));
                remaining[0]--;
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(tick);
    }

    // ── USB-Speicher-Sperre ───────────────────────────────────────────────────

    private void toggleUsbRestriction() {
        boolean nowRestricted = !prefs.usbRestricted();

        android.app.admin.DevicePolicyManager dpm =
                (android.app.admin.DevicePolicyManager)
                activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
        android.content.ComponentName admin =
                new android.content.ComponentName(activity, KioskAdminReceiver.class);

        if (dpm != null && dpm.isDeviceOwnerApp(activity.getPackageName())) {
            if (nowRestricted) {
                dpm.addUserRestriction(admin,
                        android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
            } else {
                dpm.clearUserRestriction(admin,
                        android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
            }
            prefs.setUsbRestricted(nowRestricted);
            String msg = nowRestricted
                    ? activity.getString(R.string.usb_locked)
                    : activity.getString(R.string.usb_unlocked);
            ToastHelper.success(activity, msg);
        } else {
            ToastHelper.error(activity, activity.getString(R.string.usb_no_owner));
        }
    }

    // ── Cache leeren ──────────────────────────────────────────────────────────

    private void clearWebViewCache() {
        webView.clearCache(true);
        webView.clearHistory();
        ToastHelper.success(activity, activity.getString(R.string.cache_cleared));
        ((MainActivity) activity).loadCurrentUrl();
    }

    // ── Systemeinstellungen ───────────────────────────────────────────────────

    private void openSystemSettings() {
        try {
            activity.startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS));
        } catch (Exception e) {
            ToastHelper.error(activity, "Einstellungen nicht verfuegbar");
        }
    }

    // ── Neustart ──────────────────────────────────────────────────────────────

    private void confirmReboot() {
        new AlertDialog.Builder(activity)
                .setTitle(R.string.menu_reboot)
                .setMessage(R.string.reboot_confirm)
                .setPositiveButton(R.string.yes, (d, w) -> {
                    android.app.admin.DevicePolicyManager dpm =
                            (android.app.admin.DevicePolicyManager)
                            activity.getSystemService(Context.DEVICE_POLICY_SERVICE);
                    android.content.ComponentName admin =
                            new android.content.ComponentName(activity, KioskAdminReceiver.class);
                    if (dpm != null && dpm.isDeviceOwnerApp(activity.getPackageName())) {
                        dpm.reboot(admin);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Info ──────────────────────────────────────────────────────────────────

    private void showAbout() {
        activity.startActivity(new Intent(activity, AboutActivity.class));
    }

    // ── Hilfsfunktion ─────────────────────────────────────────────────────────

    private String truncate(String s) {
        if (s == null || s.length() <= 30) return s != null ? s : "";
        return s.substring(0, 27) + "...";
    }
}
