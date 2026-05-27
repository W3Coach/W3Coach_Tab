package com.w3coach.w3coachtab;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.Gravity;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Einfacher PIN-Dialog für geschützte Menüpunkte.
 * PIN wird via BuildConfig.MENU_PIN konfiguriert (gradle.properties).
 */
public class PinDialog {

    public interface Callback { void onCorrect(); }

    private final Context  context;
    private final Callback callback;

    public PinDialog(Context context, Callback callback) {
        this.context  = context;
        this.callback = callback;
    }

    public void show() {
        // Layout: Label + PIN-Eingabe
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 8);

        TextView label = new TextView(context);
        label.setText(R.string.pin_enter);
        label.setTextSize(16f);
        label.setGravity(Gravity.CENTER);
        layout.addView(label);

        EditText pinInput = new EditText(context);
        pinInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinInput.setGravity(Gravity.CENTER);
        pinInput.setTextSize(24f);
        pinInput.setMaxLines(1);
        pinInput.setSingleLine(true);
        pinInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        layout.addView(pinInput);

        TextView error = new TextView(context);
        error.setTextColor(0xFFD32F2F);
        error.setGravity(Gravity.CENTER);
        error.setVisibility(android.view.View.GONE);
        layout.addView(error);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(R.string.pin_title)
                .setView(layout)
                .setPositiveButton(R.string.pin_ok, null) // null → manuell behandeln
                .setNegativeButton(R.string.cancel, null)
                .create();

        dialog.setOnShowListener(d -> {
            // Tastatur automatisch öffnen
            pinInput.requestFocus();
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null)
                imm.showSoftInput(pinInput, InputMethodManager.SHOW_IMPLICIT);

            // Enter im PIN-Feld = OK
            pinInput.setOnEditorActionListener((v, actionId, event) -> {
                checkPin(pinInput, error, dialog);
                return true;
            });

            // OK-Button manuell behandeln (damit Dialog bei falschem PIN offen bleibt)
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setOnClickListener(v -> checkPin(pinInput, error, dialog));
        });

        dialog.show();
    }

    private void checkPin(EditText input, TextView error, AlertDialog dialog) {
        String entered = input.getText().toString().trim();
        if (entered.equals(BuildConfig.MENU_PIN)) {
            // Tastatur schließen
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
            dialog.dismiss();
            callback.onCorrect();
        } else {
            input.setText("");
            error.setText(R.string.pin_wrong);
            error.setVisibility(android.view.View.VISIBLE);
        }
    }
}
