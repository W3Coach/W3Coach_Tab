package com.w3coach.w3coachtab;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.text.InputType;
import android.util.Log;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * Verwaltet WireGuard VPN – Konfiguration und Ein/Ausschalten.
 * Nutzt die native wireguard-android Tunnel Library.
 */
public class WireGuardManager {

    private static final String TAG = "WireGuardManager";
    private static final int VPN_REQUEST_CODE = 1001;

    private final Activity activity;
    private final Prefs    prefs;

    public WireGuardManager(Activity activity) {
        this.activity = activity;
        this.prefs    = new Prefs(activity);
    }

    private void showVpnIpDialog() {
        // Kurz warten bis VPN verbunden ist
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
            if (!WireGuardService.isConnected) return;

            // VPN-IP aus Prefs lesen
            String vpnIp = prefs.wgClientIp()
                    .replace("/32", "").replace("/24", "").trim();
            if (vpnIp.isEmpty()) vpnIp = "unbekannt";

            // Dialog aufbauen
            android.widget.TextView tv = new android.widget.TextView(activity);
            tv.setText(vpnIp);
            tv.setTextSize(48f);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            tv.setTextColor(0xFF0B615E);
            tv.setGravity(android.view.Gravity.CENTER);
            tv.setPadding(48, 48, 48, 48);

            new androidx.appcompat.app.AlertDialog.Builder(activity)
                    .setTitle("Quicksupport aktiv")
                    .setMessage("Bitte nennen Sie dem Supporter folgende IP-Adresse:")
                    .setView(tv)
                    .setPositiveButton("OK", null)
                    .setCancelable(false)
                    .show();
        }, 2000);
    }


    // ── Konfigurationsdialog ──────────────────────────────────────────────────

    public void showConfigDialog() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 16, 48, 8);

        EditText etEndpoint   = addField(layout, "Server (IP:Port)",           prefs.wgEndpoint());
        EditText etPublicKey  = addField(layout, "Server Public Key",           prefs.wgPublicKey());
        EditText etClientIp   = addField(layout, "Client IP (z.B. 10.0.0.3/32)", prefs.wgClientIp());
        EditText etPrivateKey = addField(layout, "Client Private Key",          prefs.wgPrivateKey());

        CheckBox cbSplitTunnel = new CheckBox(activity);
        cbSplitTunnel.setText("Split-Tunnel (nur Fernwartung durch VPN)");
        cbSplitTunnel.setChecked(prefs.wgSplitTunnel());
        layout.addView(cbSplitTunnel);

        new AlertDialog.Builder(activity)
                .setTitle("WireGuard VPN")
                .setView(layout)
                .setPositiveButton(R.string.save, (d, w) -> {
                    String ep  = etEndpoint.getText().toString().trim();
                    String pk  = etPublicKey.getText().toString().trim();
                    String cip = etClientIp.getText().toString().trim();
                    String prk = etPrivateKey.getText().toString().trim();

                    if (ep.isEmpty() || pk.isEmpty() || cip.isEmpty() || prk.isEmpty()) {
                        Toast.makeText(activity, "Alle Felder ausfüllen",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    prefs.setWgEndpoint(ep);
                    prefs.setWgPublicKey(pk);
                    prefs.setWgClientIp(cip);
                    prefs.setWgPrivateKey(prk);
                    prefs.setWgSplitTunnel(cbSplitTunnel.isChecked());

                    Toast.makeText(activity, "WireGuard konfiguriert",
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // ── Verbinden / Trennen ───────────────────────────────────────────────────

    public void toggle() {
        if (WireGuardService.isConnected) {
            disconnect();
        } else {
            connect();
        }
    }

    private void connect() {
        if (prefs.wgPrivateKey().isEmpty() || prefs.wgPublicKey().isEmpty()) {
            ToastHelper.warning(activity, "Quicksupport zuerst konfigurieren");
            showConfigDialog();
            return;
        }

        // VPN-Permission anfordern
        Intent vpnIntent = VpnService.prepare(activity);
        if (vpnIntent != null) {
            activity.startActivityForResult(vpnIntent, VPN_REQUEST_CODE);
        } else {
            startVpnService();
        }
    }

    public void onVpnPermissionResult(int resultCode) {
        if (resultCode == Activity.RESULT_OK) {
            startVpnService();
        } else {
            ToastHelper.error(activity, "Quicksupport-Berechtigung verweigert");
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(activity, WireGuardService.class);
        intent.setAction(WireGuardService.ACTION_CONNECT);
        activity.startService(intent);
        ToastHelper.info(activity, "Quicksupport verbindet…");
                showVpnIpDialog();
    }

    private void disconnect() {
        Intent intent = new Intent(activity, WireGuardService.class);
        intent.setAction(WireGuardService.ACTION_DISCONNECT);
        activity.startService(intent);
        ToastHelper.info(activity, "Quicksupport wird getrennt…");
    }

    // ── Hilfsmethoden ─────────────────────────────────────────────────────────

    private EditText addField(LinearLayout parent, String hint, String value) {
        TextView label = new TextView(activity);
        label.setText(hint);
        label.setTextSize(12f);
        label.setPadding(0, 16, 0, 0);
        parent.addView(label);

        EditText et = new EditText(activity);
        et.setHint(hint);
        et.setText(value);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT);
        et.setOnEditorActionListener((v, actionId, event) -> {
            InputMethodManager imm = (InputMethodManager)
                    activity.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            return false;
        });
        parent.addView(et);
        return et;
    }
}
