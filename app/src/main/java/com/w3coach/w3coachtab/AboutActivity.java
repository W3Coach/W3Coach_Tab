package com.w3coach.w3coachtab;

import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AboutActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER);
        layout.setBackgroundColor(0xFF000000);
        layout.setPadding(64, 64, 64, 64);
        setContentView(layout);

        Prefs prefs = new Prefs(this);

        addLabel(layout, getString(R.string.app_name), 32f, 0xFFFFFFFF, true);

        // Version: Name + Code
        addLabel(layout, getString(R.string.about_version) + ": "
                + BuildConfig.VERSION_NAME
                + " (" + BuildConfig.VERSION_CODE + ")",
                18f, 0xFFAAAAAA, false);

        // Gerätemodell
        addLabel(layout, getString(R.string.about_device) + ": " + Build.MODEL,
                16f, 0xFFAAAAAA, false);

        // IP-Adressen (alle Interfaces)
        for (String line : getIpAddresses()) {
            addLabel(layout, line, 16f, 0xFFAAAAAA, false);
        }

        // WireGuard Status
        if (WireGuardService.isConnected) {
            String vpnIp = prefs.wgClientIp().replace("/32", "").replace("/24", "");
            addLabel(layout, getString(R.string.about_vpn_ip) + ": " + vpnIp,
                    16f, 0xFF2ECC71, false);
        } else {
            addLabel(layout, getString(R.string.about_vpn_disconnected),
                    16f, 0xFFE74C3C, false);
        }

        // Letztes Update-Datum
        long lastUpdate = prefs.lastUpdateTimestamp();
        if (lastUpdate > 0) {
            String date = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    .format(new Date(lastUpdate));
            addLabel(layout, getString(R.string.about_last_update) + ": " + date,
                    16f, 0xFFAAAAAA, false);
        }
    }

    private List<String> getIpAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                String name = iface.getName();
                if (name.startsWith("tun") || name.startsWith("wg")) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (!(addr instanceof Inet4Address)) continue;
                    if (addr.isLoopbackAddress()) continue;
                    if (addr.isLinkLocalAddress()) continue;
                    result.add(getString(R.string.about_ip)
                            + " (" + name + "): "
                            + addr.getHostAddress());
                }
            }
        } catch (Exception ignored) {}
        if (result.isEmpty()) result.add(getString(R.string.about_ip) + ": –");
        return result;
    }

    private void addLabel(LinearLayout parent, String text, float sizeSp,
                          int color, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(sizeSp);
        tv.setTextColor(color);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, 8, 0, 8);
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD);
        parent.addView(tv);
    }
}
