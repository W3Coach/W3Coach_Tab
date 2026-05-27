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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

        addLabel(layout, getString(R.string.app_name),                              32f, 0xFFFFFFFF, true);
        addLabel(layout, getString(R.string.about_version) + ": " + BuildConfig.VERSION_NAME, 18f, 0xFFAAAAAA, false);
        addLabel(layout, getString(R.string.about_device)  + ": " + Build.MODEL,   16f, 0xFFAAAAAA, false);

        // Alle aktiven IPv4-Adressen anzeigen (Ethernet/PoE + WLAN)
        for (String line : getIpAddresses()) {
            addLabel(layout, line, 16f, 0xFFAAAAAA, false);
        }

        // VPN-IP anzeigen wenn Tunnel aktiv
        if (WireGuardService.isConnected) {
            Prefs prefs = new Prefs(this);
            String vpnIp = prefs.wgClientIp().replace("/32", "").replace("/24", "");
            addLabel(layout, getString(R.string.about_vpn_ip) + ": " + vpnIp, 16f, 0xFF2ECC71, false);
        }
    }

    /**
     * Liefert alle aktiven IPv4-Adressen aller Netzwerk-Interfaces
     * (eth0/PoE, wlan0/WLAN, usw.) als formatierte Strings.
     * Loopback (127.x), Link-Local (169.254.x) und Tunnel-Interfaces werden uebersprungen.
     */
    private List<String> getIpAddresses() {
        List<String> result = new ArrayList<>();
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                String name = iface.getName(); // z.B. "eth0", "wlan0"
                // WireGuard-/Tunnel-Interface ausblenden (wird separat angezeigt)
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
