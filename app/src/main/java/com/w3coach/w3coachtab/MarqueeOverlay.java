package com.w3coach.w3coachtab;

import android.animation.ObjectAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;

import java.io.IOException;
import java.io.InputStream;

/**
 * Marquee-Overlay: Logo + optionaler Text laufen von rechts nach links
 * über einen halbtransparenten Balken. Touch blendet aus.
 */
@SuppressLint("ViewConstructor")
public class MarqueeOverlay extends FrameLayout {

    public interface DismissListener {
        void onDismiss();
    }

    private final DismissListener listener;
    private ObjectAnimator animator;

    public MarqueeOverlay(Context context, DismissListener listener) {
        super(context);
        this.listener = listener;

        // Halbtransparenter Teal-Balken unten
        setBackgroundColor(0xDD0B615E);

        // Innerer Container: Logo + Text nebeneinander
        LinearLayout ticker = new LinearLayout(context);
        ticker.setOrientation(LinearLayout.HORIZONTAL);
        ticker.setGravity(Gravity.CENTER_VERTICAL);
        ticker.setPadding(dpToPx(context, 24), dpToPx(context, 12),
                          dpToPx(context, 24), dpToPx(context, 12));

        // Logo laden
        try {
            InputStream is = context.getAssets().open("marquee_logo.png");
            Bitmap original = BitmapFactory.decodeStream(is);
            is.close();

            if (original != null) {
                Bitmap rounded = toCircle(original);
                ImageView iv = new ImageView(context);
                iv.setImageBitmap(rounded);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                int sizePx = dpToPx(context, 56);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(sizePx, sizePx);
                lp.setMarginEnd(dpToPx(context, 16));
                ticker.addView(iv, lp);
            }
        } catch (IOException e) {
            // Fallback: nur Text
        }

        // Text-Label
        android.widget.TextView tv = new android.widget.TextView(context);
        tv.setText(context.getString(R.string.app_name));
        tv.setTextColor(0xFFFFFFFF);
        tv.setTextSize(22f);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);
        ticker.addView(tv, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        // Ticker in Overlay, zunächst außerhalb rechts positioniert
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.CENTER_VERTICAL;
        addView(ticker, flp);

        // Animation starten sobald Layout berechnet ist
        ticker.post(() -> startScroll(ticker));
    }

    private void startScroll(android.view.View ticker) {
        int screenWidth  = getWidth();
        int contentWidth = ticker.getWidth();

        // Start: rechts außerhalb; Ende: links außerhalb
        float startX = screenWidth;
        float endX   = -contentWidth;

        // Geschwindigkeit: ~120dp/s
        float distancePx = screenWidth + contentWidth;
        float density    = getResources().getDisplayMetrics().density;
        long  durationMs = (long) (distancePx / (120f * density) * 1000f);

        animator = ObjectAnimator.ofFloat(ticker, "translationX", startX, endX);
        animator.setDuration(durationMs);
        animator.setInterpolator(new LinearInterpolator());
        animator.setRepeatCount(ObjectAnimator.INFINITE);
        animator.setRepeatMode(ObjectAnimator.RESTART);
        animator.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (animator != null) animator.cancel();
    }

    private Bitmap toCircle(Bitmap src) {
        int size   = Math.min(src.getWidth(), src.getHeight());
        Bitmap out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(out);
        Paint  paint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect   rect   = new Rect(0, 0, size, size);
        canvas.drawOval(new RectF(rect), paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
        int offset = (src.getWidth() - size) / 2;
        canvas.drawBitmap(src,
                new Rect(offset, offset, offset + size, offset + size), rect, paint);
        return out;
    }

    private int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (animator != null) animator.cancel();
            if (listener != null) listener.onDismiss();
        }
        return true;
    }
}
