package com.w3coach.w3coachtab;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class GithubUpdateChecker {

    private static final String TAG = "GithubUpdateChecker";

    public static class UpdateInfo {
        public final String downloadUrl;
        public final String tagName;
        public final int    remoteVersionCode;

        UpdateInfo(String downloadUrl, String tagName, int remoteVersionCode) {
            this.downloadUrl       = downloadUrl;
            this.tagName           = tagName;
            this.remoteVersionCode = remoteVersionCode;
        }
    }

    public static UpdateInfo checkForUpdate(String apiUrl, int currentVersionCode)
            throws IOException {
        String json = fetchString(apiUrl);
        JSONObject release;
        try { release = new JSONObject(json); }
        catch (Exception e) { throw new IOException("JSON-Fehler: " + e.getMessage()); }

        String tagName = release.optString("tag_name", "");
        int remoteCode = parseVersionCode(release, currentVersionCode);

        if (remoteCode <= currentVersionCode) return null;

        String apkUrl = findApkUrl(release);
        if (apkUrl == null) throw new IOException("Kein APK-Asset in Release " + tagName);

        Log.i(TAG, "Update: " + tagName + " (" + remoteCode + ") → " + apkUrl);
        return new UpdateInfo(apkUrl, tagName, remoteCode);
    }

    public static void downloadApk(String apkUrl, File dest) throws IOException {
        URL url = new URL(apkUrl);
        HttpURLConnection conn = openConnection(url);
        int code = conn.getResponseCode();
        if (code == 301 || code == 302 || code == 307) {
            conn.disconnect();
            conn = openConnection(new URL(conn.getHeaderField("Location")));
        }
        try (InputStream in = conn.getInputStream();
             FileOutputStream out = new FileOutputStream(dest)) {
            byte[] buf = new byte[65536];
            int len;
            while ((len = in.read(buf)) != -1) out.write(buf, 0, len);
        } finally { conn.disconnect(); }
    }

    private static String fetchString(String apiUrl) throws IOException {
        HttpURLConnection conn = openConnection(new URL(apiUrl));
        conn.setRequestProperty("Accept", "application/vnd.github+json");
        int httpCode = conn.getResponseCode();
        if (httpCode == 403) throw new IOException("GitHub API Rate Limit erreicht (HTTP 403)");
        if (httpCode == 404) throw new IOException("Release nicht gefunden (HTTP 404)");
        if (httpCode < 200 || httpCode >= 300)
            throw new IOException("HTTP-Fehler " + httpCode);
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally { conn.disconnect(); }
    }

    private static HttpURLConnection openConnection(URL url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", "W3CoachTab-Updater/1.0");
        c.setInstanceFollowRedirects(true);
        return c;
    }

    private static int parseVersionCode(JSONObject release, int fallback) {
        try {
            JSONArray assets = release.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").equalsIgnoreCase("w3coachtab.json")) {
                    String json = fetchString(a.getString("browser_download_url"));
                    return new JSONObject(json).optInt("versionCode", fallback);
                }
            }
        } catch (Exception e) { Log.w(TAG, "versionCode-Parse: " + e.getMessage()); }
        return fallback;
    }

    private static String findApkUrl(JSONObject release) {
        try {
            JSONArray assets = release.getJSONArray("assets");
            for (int i = 0; i < assets.length(); i++) {
                JSONObject a = assets.getJSONObject(i);
                if (a.optString("name").toLowerCase().endsWith(".apk"))
                    return a.getString("browser_download_url");
            }
        } catch (Exception ignored) {}
        return null;
    }
}
