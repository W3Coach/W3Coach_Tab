package com.w3coach.w3coachtab;

import android.content.Context;
import android.widget.Toast;

/**
 * Toast-Nachrichten mit Emoji-Icons für bessere Lesbarkeit auf dem TV.
 */
public class ToastHelper {

    public static void success(Context ctx, String msg) {
        Toast.makeText(ctx, "✅  " + msg, Toast.LENGTH_SHORT).show();
    }

    public static void error(Context ctx, String msg) {
        Toast.makeText(ctx, "❌  " + msg, Toast.LENGTH_LONG).show();
    }

    public static void info(Context ctx, String msg) {
        Toast.makeText(ctx, "ℹ️  " + msg, Toast.LENGTH_SHORT).show();
    }

    public static void warning(Context ctx, String msg) {
        Toast.makeText(ctx, "⚠️  " + msg, Toast.LENGTH_LONG).show();
    }
}
