package com.w3coach.w3coachtab;

import android.annotation.SuppressLint;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.os.BatteryManager;
import android.os.Bundle;
import android.provider.Settings;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.floatingactionbutton.FloatingActionButton;

public class MainActivity extends AppCompatActivity {

    private WebView kioskWeb;
    private Prefs prefs;
    private int currentUrl = 1;

    private FrameLayout noNetworkOverlay;
    private FrameLayout marqueeOverlay;
    private Handler marqueeHandler;
    private Runnable marqueeRunnable;

    private ConnectivityManager.NetworkCallback networkCallback;
    private TabMenu tabMenu;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );

        prefs = new Prefs(this);

        // Root layout
        FrameLayout root = new FrameLayout(this);
        setContentView(root);

        // WebView
        kioskWeb = new WebView(this);
        root.addView(kioskWeb, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        setupWebView();

        // No-Network Overlay
        noNetworkOverlay = new FrameLayout(this);
        noNetworkOverlay.setBackgroundColor(0xFF000000);
        noNetworkOverlay.setVisibility(View.GONE);
        TextView tvNoNet = new TextView(this);
        tvNoNet.setText(R.string.no_network);
        tvNoNet.setTextColor(0xFFFFFFFF);
        tvNoNet.setTextSize(24f);
        tvNoNet.setGravity(android.view.Gravity.CENTER);
        noNetworkOverlay.addView(tvNoNet, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        root.addView(noNetworkOverlay);

        // Marquee Overlay
        marqueeOverlay = new MarqueeOverlay(this, () -> hideMarquee());
        marqueeOverlay.setVisibility(View.GONE);
        FrameLayout.LayoutParams marqueeLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        marqueeLp.gravity = android.view.Gravity.BOTTOM;
        root.addView(marqueeOverlay, marqueeLp);

        // Floating Action Button
        FloatingActionButton fab = new FloatingActionButton(this);
        fab.setImageResource(R.drawable.ic_fab_logo);
        // Vollständig transparenter Hintergrund – Logo schwebt unsichtbar über der WebView
        fab.setBackgroundTintList(android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT));
        fab.setElevation(0f);
        fab.setCompatElevation(0f);
        // Kein Ripple-Effekt
        fab.setRippleColor(android.graphics.Color.TRANSPARENT);
        FrameLayout.LayoutParams fabParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        fabParams.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.END;
        fabParams.setMargins(0, 0, 48, 48);
        root.addView(fab, fabParams);

        tabMenu = new TabMenu(this, kioskWeb, prefs);
        fab.setOnClickListener(v -> tabMenu.show());

        // Täglichen Neustart planen falls konfiguriert
        tabMenu.scheduleDailyReboot();
        AutoUpdateReceiver.schedule(this);
        fab.setOnLongClickListener(v -> {
            String url1 = prefs.url1();
            if (!url1.isEmpty()) {
                currentUrl = 1;
                kioskWeb.loadUrl(url1);
            }
            return true;
        });

        // Kiosk-Modus
        initKioskMode();

        // Netzwerk-Callback
        setupNetworkCallback();

        // Marquee-Timer starten
        scheduleMarquee();

        // Erste URL laden
        loadCurrentUrl();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings ws = kioskWeb.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setLoadWithOverviewMode(true);
        ws.setUseWideViewPort(true);
        ws.setBuiltInZoomControls(false);
        ws.setSupportZoom(true);
        ws.setTextZoom(prefs.zoom());
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG);

        // User-Agent: Standard-UA + Kennung fuer Geraete-Erkennung in der Webapplikation
        ws.setUserAgentString(ws.getUserAgentString() + " w3coachtab");

        kioskWeb.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                performAutoLogin(view, url);
            }
        });
    }

    private void performAutoLogin(WebView view, String url) {
        if (!prefs.autoLogin()) return;
        // AutoLogin nur fuer URLs die mit dem konfigurierten Prefix beginnen
        if (url == null || !url.startsWith(BuildConfig.URL_PREFIX)) return;
        String user = prefs.autoLoginUser();
        String pass = prefs.autoLoginPass();
        if (user.isEmpty() || pass.isEmpty()) return;

        // Sonderzeichen in User/Pass fuer JS escapen (einfache Anfuehrungszeichen, Backslash)
        String userEsc = user.replace("\\", "\\\\").replace("'", "\\'");
        String passEsc = pass.replace("\\", "\\\\").replace("'", "\\'");

        boolean doClick = prefs.autoLoginClick();

        // Pruefe ob Frame "Mainpage" existiert → waehle passende Login-Variante
        StringBuilder js = new StringBuilder();
        js.append("(function(){");
        js.append("  var frame = window.frames['Mainpage'];");
        js.append("  if (frame && frame.document) {");
        // ── Frame-Variante ──
        js.append("    var d = frame.document;");
        js.append("    var u = d.getElementsByName('login')[0];");
        js.append("    var p = d.getElementsByName('pwd')[0];");
        js.append("    if (u) u.value='").append(userEsc).append("';");
        js.append("    if (p) p.value='").append(passEsc).append("';");
        if (doClick) {
            js.append("    var btn = d.getElementById('logon');");
            js.append("    if (btn) btn.click();");
        }
        js.append("  } else {");
        // ── Direkte Variante ──
        js.append("    var u = document.getElementsByName('login')[0];");
        js.append("    var p = document.getElementsByName('pwd')[0];");
        js.append("    if (u) u.value='").append(userEsc).append("';");
        js.append("    if (p) p.value='").append(passEsc).append("';");
        if (doClick) {
            js.append("    var btn = document.getElementById('logon');");
            js.append("    if (btn) btn.click();");
        }
        js.append("  }");
        js.append("})();");

        final String script = js.toString();
        new Handler(Looper.getMainLooper()).postDelayed(() ->
            view.evaluateJavascript(script, s -> {}), 500);
    }

    public void loadCurrentUrl() {
        String url;
        switch (currentUrl) {
            case 2:  url = prefs.url2(); break;
            case 3:  url = prefs.url3(); break;
            default: url = prefs.url1(); break;
        }
        kioskWeb.loadUrl(url);
        // Marquee-Timer neu starten bei URL-Wechsel
        scheduleMarquee();
    }

    public void switchUrl() {
        currentUrl = currentUrl % 3 + 1;
        loadCurrentUrl();
    }

    public void reloadCurrentUrl() {
        kioskWeb.reload();
    }

    // ── Marquee ───────────────────────────────────────────────────────────────

    public void scheduleMarquee() {
        if (marqueeHandler == null) marqueeHandler = new Handler(Looper.getMainLooper());
        if (marqueeRunnable != null) marqueeHandler.removeCallbacks(marqueeRunnable);

        int delayMinutes = prefs.marqueeDelay();
        android.widget.Toast.makeText(this, "Marquee delay: " + delayMinutes + " min", android.widget.Toast.LENGTH_LONG).show();
        if (delayMinutes <= 0) return;

        marqueeRunnable = this::showMarquee;
        marqueeHandler.postDelayed(marqueeRunnable, delayMinutes * 60 * 1000L);
    }

    private void showMarquee() {
        marqueeOverlay.setVisibility(View.VISIBLE);
    }

    private void hideMarquee() {
        marqueeOverlay.setVisibility(View.GONE);
        scheduleMarquee();
    }

    // ── Netzwerk ──────────────────────────────────────────────────────────────

    private void setupNetworkCallback() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        networkCallback = new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(Network n) {
                runOnUiThread(() -> {
                    noNetworkOverlay.setVisibility(View.GONE);
                    kioskWeb.reload();
                });
            }
            @Override public void onLost(Network n) {
                runOnUiThread(() -> noNetworkOverlay.setVisibility(View.VISIBLE));
            }
        };
        NetworkRequest req = new NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        cm.registerNetworkCallback(req, networkCallback);
    }

    // ── Kiosk-Modus ───────────────────────────────────────────────────────────

    public void initKioskMode() {
        DevicePolicyManager dpm = (DevicePolicyManager)
                getSystemService(Context.DEVICE_POLICY_SERVICE);
        ComponentName admin = new ComponentName(this, KioskAdminReceiver.class);

        if (dpm != null && dpm.isDeviceOwnerApp(getPackageName())) {
            java.util.List<String> packages = new java.util.ArrayList<>();
            packages.add(getPackageName());
            packages.add("com.android.settings");          // Systemeinstellungen im Menü
            if (!prefs.app1Package().isEmpty()) packages.add(prefs.app1Package());
            if (!prefs.app2Package().isEmpty()) packages.add(prefs.app2Package());
            dpm.setLockTaskPackages(admin, packages.toArray(new String[0]));
            dpm.setKeyguardDisabledFeatures(admin,
                    DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_ALL);
            dpm.setMaximumTimeToLock(admin, 0);

            // Automatische Zeitsynchronisation per NTP aktivieren
            try {
                android.provider.Settings.Global.putInt(getContentResolver(),
                        android.provider.Settings.Global.AUTO_TIME, 1);
                android.provider.Settings.Global.putInt(getContentResolver(),
                        android.provider.Settings.Global.AUTO_TIME_ZONE, 1);
            } catch (Exception ignored) {}

            // USB-Speicher-Sperre gemaess gespeicherter Einstellung
            if (prefs.usbRestricted()) {
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
            } else {
                dpm.clearUserRestriction(admin, android.os.UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA);
            }

            // Als Default-Launcher setzen damit nach Neustart kein Launcher-Dialog erscheint
            try {
                android.content.IntentFilter filter = new android.content.IntentFilter(android.content.Intent.ACTION_MAIN);
                filter.addCategory(android.content.Intent.CATEGORY_HOME);
                filter.addCategory(android.content.Intent.CATEGORY_DEFAULT);
                ComponentName activity = new ComponentName(getPackageName(),
                        MainActivity.class.getName());
                dpm.addPersistentPreferredActivity(admin, filter, activity);
            } catch (Exception ignored) {}

            // Display dauerhaft an (wie "Keep Display on")
            try {
                Settings.Global.putInt(getContentResolver(),
                        Settings.Global.STAY_ON_WHILE_PLUGGED_IN,
                        BatteryManager.BATTERY_PLUGGED_AC |
                        BatteryManager.BATTERY_PLUGGED_USB |
                        BatteryManager.BATTERY_PLUGGED_WIRELESS);
            } catch (Exception ignored) {}
        }

        try { startLockTask(); } catch (Exception ignored) {}
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Systemleiste versteckt halten
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        );
        // Screensaver deaktivieren
        try {
            android.provider.Settings.Secure.putString(
                    getContentResolver(), "screensaver_enabled", "0");
        } catch (Exception ignored) {}
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (networkCallback != null) {
            ConnectivityManager cm = (ConnectivityManager)
                    getSystemService(Context.CONNECTIVITY_SERVICE);
            try { cm.unregisterNetworkCallback(networkCallback); } catch (Exception ignored) {}
        }
        if (marqueeHandler != null && marqueeRunnable != null) {
            marqueeHandler.removeCallbacks(marqueeRunnable);
        }
    }
}
