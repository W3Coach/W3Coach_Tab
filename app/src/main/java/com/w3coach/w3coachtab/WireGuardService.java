package com.w3coach.w3coachtab;

import android.app.Notification;
import android.os.Handler;
import android.os.Looper;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.VpnService;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import com.wireguard.android.backend.Backend;
import com.wireguard.android.backend.GoBackend;
import com.wireguard.android.backend.Tunnel;
import com.wireguard.config.Config;
import com.wireguard.config.InetEndpoint;
import com.wireguard.config.InetNetwork;
import com.wireguard.config.Interface;
import com.wireguard.config.Peer;
import com.wireguard.crypto.Key;
import com.wireguard.crypto.KeyPair;

import java.net.InetAddress;
import java.util.Collections;

/**
 * WireGuard VPN Service – betreibt den Tunnel direkt in der App.
 * Keine separate WireGuard-App nötig.
 *
 * Split-Tunnel (default): Nur 10.0.0.0/24 durch VPN → nur scrcpy/ADB
 * Full-Tunnel: 0.0.0.0/0 durch VPN → alles durch VPN
 */
public class WireGuardService extends VpnService {

    private static final String TAG            = "WireGuardService";
    private static final String CHANNEL_ID     = "wireguard";
    private static final int    NOTIFICATION_ID = 100;

    public static final String ACTION_CONNECT    = "com.w3coach.w3coachtab.WG_CONNECT";
    public static final String ACTION_DISCONNECT = "com.w3coach.w3coachtab.WG_DISCONNECT";

    private Backend  backend;
    private Tunnel   tunnel;
    private boolean  connected = false;

    public static boolean isConnected = false;

    // ── Binder für Activity-Kommunikation ────────────────────────────────────

    public class LocalBinder extends Binder {
        WireGuardService getService() { return WireGuardService.this; }
    }
    private final IBinder binder = new LocalBinder();

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;

        if (ACTION_CONNECT.equals(intent.getAction())) {
            connect();
        } else if (ACTION_DISCONNECT.equals(intent.getAction())) {
            disconnect();
        }
        return START_NOT_STICKY;
    }

    // ── Verbinden ─────────────────────────────────────────────────────────────

    private void connect() {
        Prefs prefs = new Prefs(this);
        String privateKeyStr = prefs.wgPrivateKey();
        String serverPubKeyStr = prefs.wgPublicKey();
        String endpoint = prefs.wgEndpoint();
        String clientIp = prefs.wgClientIp();
        boolean splitTunnel = prefs.wgSplitTunnel();

        if (privateKeyStr.isEmpty() || serverPubKeyStr.isEmpty() || endpoint.isEmpty()) {
            Log.e(TAG, "WireGuard nicht konfiguriert");
            stopSelf();
            return;
        }

        new Thread(() -> {
            try {
                // Keys parsen
                KeyPair keyPair = new KeyPair(Key.fromBase64(privateKeyStr));
                Key serverPubKey = Key.fromBase64(serverPubKeyStr);

                // Client IP parsen
                String[] ipParts = clientIp.split("/");
                InetAddress clientAddr = InetAddress.getByName(ipParts[0]);
                int prefix = ipParts.length > 1 ? Integer.parseInt(ipParts[1]) : 32;

                // Endpoint parsen
                String[] epParts = endpoint.split(":");
                String epHost = epParts[0];
                int epPort = epParts.length > 1 ? Integer.parseInt(epParts[1]) : 51820;

                // AllowedIPs: Split-Tunnel oder Full-Tunnel
                String allowedIps = splitTunnel ? "10.0.0.0/24" : "0.0.0.0/0";

                // WireGuard Config aufbauen
                Interface.Builder ifBuilder = new Interface.Builder()
                        .setKeyPair(keyPair)
                        .addAddress(InetNetwork.parse(clientIp))
                        .addDnsServer(InetAddress.getByName("8.8.8.8"));

                Peer.Builder peerBuilder = new Peer.Builder()
                        .setPublicKey(serverPubKey)
                        .setEndpoint(InetEndpoint.parse(endpoint))
                        .setPersistentKeepalive(25)
                        .addAllowedIp(InetNetwork.parse(allowedIps));

                Config config = new Config.Builder()
                        .setInterface(ifBuilder.build())
                        .addPeer(peerBuilder.build())
                        .build();

                // GoBackend initialisieren und Tunnel starten
                Log.i(TAG, "Endpoint: " + endpoint);
                Log.i(TAG, "ClientIP: " + clientIp);
                Log.i(TAG, "SplitTunnel: " + splitTunnel);
                Log.i(TAG, "AllowedIPs: " + allowedIps);
                Log.i(TAG, "Config: " + config.toWgQuickString());

                backend = new GoBackend(this);
                Log.i(TAG, "GoBackend created");

                tunnel = new Tunnel() {
                    @Override public String getName() { return "W3Coach"; }
                    @Override public void onStateChange(State newState) {
                        isConnected = (newState == State.UP);
                        Log.i(TAG, "Tunnel state: " + newState);
                    }
                };

                Log.i(TAG, "Setting tunnel state UP...");
                backend.setState(tunnel, Tunnel.State.UP, config);
                Log.i(TAG, "Tunnel state set successfully");
                isConnected = true;
                new Handler(Looper.getMainLooper()).post(() ->
                        ToastHelper.success(WireGuardService.this, "Quicksupport verbunden"));
                try {
                    startForeground(NOTIFICATION_ID, buildNotification(true),
                            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
                } catch (Exception ignored) {}
                Log.i(TAG, "WireGuard verbunden");

            } catch (Exception e) {
                Log.e(TAG, "Verbindung fehlgeschlagen: " + e.getMessage(), e);
                if (!isConnected) {
                    new Handler(Looper.getMainLooper()).post(() ->
                            ToastHelper.error(WireGuardService.this,
                                    "Quicksupport fehlgeschlagen: " + e.getMessage()));
                    stopSelf();
                }
            }
        }).start();
    }

    // ── Trennen ───────────────────────────────────────────────────────────────

    private void disconnect() {
        new Thread(() -> {
            try {
                if (backend != null && tunnel != null) {
                    backend.setState(tunnel, Tunnel.State.DOWN, null);
                }
            } catch (Exception e) {
                Log.e(TAG, "Trennen fehlgeschlagen: " + e.getMessage());
            } finally {
                isConnected = false;
                new Handler(Looper.getMainLooper()).post(() ->
                        ToastHelper.info(WireGuardService.this, "Quicksupport getrennt"));
                stopForeground(true);
                stopSelf();
            }
        }).start();
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "WireGuard VPN",
                NotificationManager.IMPORTANCE_LOW);
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(boolean connected) {
        Intent disconnect = new Intent(this, WireGuardService.class);
        disconnect.setAction(ACTION_DISCONNECT);
        PendingIntent pi = PendingIntent.getService(this, 0, disconnect,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_lock_lock)
                .setContentTitle("W3Coach VPN")
                .setContentText(connected ? "Verbunden" : "Getrennt")
                .addAction(android.R.drawable.ic_delete, "Trennen", pi)
                .setOngoing(connected)
                .build();
    }
}
