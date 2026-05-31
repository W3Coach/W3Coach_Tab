package com.w3coach.w3coachtab;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.PreferenceManager;

public class Prefs {

    // Keys
    public static final String KEY_URL1               = "url1";
    public static final String KEY_URL2               = "url2";
    public static final String KEY_URL3               = "url3";
    public static final String KEY_ZOOM               = "zoom";
    public static final String KEY_AUTO_UPDATE        = "autoUpdate";
    public static final String KEY_UPDATE_INTERVAL    = "updateInterval";
    public static final String KEY_UPDATE_REBOOT_NOW  = "updateRebootNow";
    public static final String KEY_UPDATE_REBOOT_TIME = "updateRebootTime";
    public static final String KEY_FIRST_RUN          = "firstRun";
    // AutoLogin
    public static final String KEY_AUTO_LOGIN         = "autoLogin";
    public static final String KEY_AUTO_LOGIN_USER    = "autoLoginUser";
    public static final String KEY_AUTO_LOGIN_PASS    = "autoLoginPass";
    public static final String KEY_AUTO_LOGIN_CLICK   = "autoLoginClick";
    // Marquee
    public static final String KEY_MARQUEE_DELAY      = "marqueeDelay";
    // App shortcuts
    public static final String KEY_APP1_PACKAGE       = "app1Package";
    public static final String KEY_APP1_LABEL         = "app1Label";
    public static final String KEY_APP2_PACKAGE       = "app2Package";
    public static final String KEY_APP2_LABEL         = "app2Label";
    // USB
    public static final String KEY_USB_RESTRICTED     = "usbRestricted";
    // WireGuard
    public static final String KEY_WG_ENDPOINT        = "wgEndpoint";
    public static final String KEY_WG_PUBLIC_KEY      = "wgPublicKey";
    public static final String KEY_WG_PRIVATE_KEY     = "wgPrivateKey";
    public static final String KEY_WG_CLIENT_IP       = "wgClientIp";
    public static final String KEY_WG_SPLIT_TUNNEL    = "wgSplitTunnel";

    private final SharedPreferences prefs;

    public Prefs(Context ctx) {
        prefs = PreferenceManager.getDefaultSharedPreferences(ctx);
    }

    // URLs
    public String url1()          { return prefs.getString(KEY_URL1, BuildConfig.URL_PREFIX); }
    public String url2()          { return prefs.getString(KEY_URL2, BuildConfig.URL_PREFIX); }
    public String url3()          { return prefs.getString(KEY_URL3, BuildConfig.URL_PREFIX); }
    public void setUrl1(String v) { prefs.edit().putString(KEY_URL1, v).apply(); }
    public void setUrl2(String v) { prefs.edit().putString(KEY_URL2, v).apply(); }
    public void setUrl3(String v) { prefs.edit().putString(KEY_URL3, v).apply(); }

    // Zoom
    public int  zoom()            { return prefs.getInt(KEY_ZOOM, 100); }
    public void setZoom(int v)    { prefs.edit().putInt(KEY_ZOOM, v).apply(); }

    // Auto-Update
    public boolean autoUpdate()             { return prefs.getBoolean(KEY_AUTO_UPDATE, false); }
    public int     updateInterval()         { return prefs.getInt(KEY_UPDATE_INTERVAL, 12); }
    public boolean updateRebootNow()        { return prefs.getBoolean(KEY_UPDATE_REBOOT_NOW, false); }
    public String  updateRebootTime()       { return prefs.getString(KEY_UPDATE_REBOOT_TIME, "03:00"); }
    public void setAutoUpdate(boolean v)    { prefs.edit().putBoolean(KEY_AUTO_UPDATE, v).apply(); }
    public void setUpdateInterval(int v)    { prefs.edit().putInt(KEY_UPDATE_INTERVAL, v).apply(); }
    public void setUpdateRebootNow(boolean v)  { prefs.edit().putBoolean(KEY_UPDATE_REBOOT_NOW, v).apply(); }
    public void setUpdateRebootTime(String v)  { prefs.edit().putString(KEY_UPDATE_REBOOT_TIME, v).apply(); }

    // First run
    public boolean firstRun()             { return prefs.getBoolean(KEY_FIRST_RUN, true); }
    public void setFirstRun(boolean v)    { prefs.edit().putBoolean(KEY_FIRST_RUN, v).apply(); }

    // AutoLogin
    public boolean autoLogin()               { return prefs.getBoolean(KEY_AUTO_LOGIN, false); }
    public String  autoLoginUser()           { return prefs.getString(KEY_AUTO_LOGIN_USER, ""); }
    public String  autoLoginPass()           { return prefs.getString(KEY_AUTO_LOGIN_PASS, ""); }
    public boolean autoLoginClick()          { return prefs.getBoolean(KEY_AUTO_LOGIN_CLICK, true); }
    public void setAutoLogin(boolean v)      { prefs.edit().putBoolean(KEY_AUTO_LOGIN, v).apply(); }
    public void setAutoLoginUser(String v)   { prefs.edit().putString(KEY_AUTO_LOGIN_USER, v).apply(); }
    public void setAutoLoginPass(String v)   { prefs.edit().putString(KEY_AUTO_LOGIN_PASS, v).apply(); }
    public void setAutoLoginClick(boolean v) { prefs.edit().putBoolean(KEY_AUTO_LOGIN_CLICK, v).apply(); }

    // Marquee
    public int  marqueeDelay()          { return prefs.getInt(KEY_MARQUEE_DELAY, 10); }
    public void setMarqueeDelay(int v)  { prefs.edit().putInt(KEY_MARQUEE_DELAY, v).apply(); }

    // App shortcuts
    public String app1Package()              { return prefs.getString(KEY_APP1_PACKAGE, ""); }
    public String app1Label()               { return prefs.getString(KEY_APP1_LABEL, "App 1"); }
    public String app2Package()              { return prefs.getString(KEY_APP2_PACKAGE, ""); }
    public String app2Label()               { return prefs.getString(KEY_APP2_LABEL, "App 2"); }
    public void setApp1Package(String v)    { prefs.edit().putString(KEY_APP1_PACKAGE, v).apply(); }
    public void setApp1Label(String v)      { prefs.edit().putString(KEY_APP1_LABEL, v).apply(); }
    public void setApp2Package(String v)    { prefs.edit().putString(KEY_APP2_PACKAGE, v).apply(); }
    public void setApp2Label(String v)      { prefs.edit().putString(KEY_APP2_LABEL, v).apply(); }

    // WireGuard
    public String  wgEndpoint()              { return prefs.getString(KEY_WG_ENDPOINT, ""); }
    public String  wgPublicKey()             { return prefs.getString(KEY_WG_PUBLIC_KEY, ""); }
    public String  wgPrivateKey()            { return prefs.getString(KEY_WG_PRIVATE_KEY, ""); }
    public String  wgClientIp()             { return prefs.getString(KEY_WG_CLIENT_IP, ""); }
    public boolean wgSplitTunnel()          { return prefs.getBoolean(KEY_WG_SPLIT_TUNNEL, true); }
    public void setWgEndpoint(String v)     { prefs.edit().putString(KEY_WG_ENDPOINT, v).apply(); }
    public void setWgPublicKey(String v)    { prefs.edit().putString(KEY_WG_PUBLIC_KEY, v).apply(); }
    public void setWgPrivateKey(String v)   { prefs.edit().putString(KEY_WG_PRIVATE_KEY, v).apply(); }
    public void setWgClientIp(String v)     { prefs.edit().putString(KEY_WG_CLIENT_IP, v).apply(); }
    public void setWgSplitTunnel(boolean v) { prefs.edit().putBoolean(KEY_WG_SPLIT_TUNNEL, v).apply(); }

    // USB-Speicher-Sperre
    public boolean usbRestricted()             { return prefs.getBoolean(KEY_USB_RESTRICTED, false); }
    public void setUsbRestricted(boolean v)    { prefs.edit().putBoolean(KEY_USB_RESTRICTED, v).apply(); }

    // Letztes Update
    public long lastUpdateTimestamp()          { return prefs.getLong("lastUpdateTimestamp", 0); }
    public void setLastUpdateTimestamp(long v) { prefs.edit().putLong("lastUpdateTimestamp", v).apply(); }
}
