package com.w3coach.w3coachtab;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.IOException;
import java.io.InputStream;

/**
 * Vollbild-Overlay in Teal (#0B615E) mit rundem Logo zentriert.
 * Ausblenden durch Touch anywhere.
 */
@SuppressLint("ViewConstructor")
public class MarqueeOverlay extends FrameLayout {

    public interface DismissListener {
        void onDismiss();
    }

    private final DismissListener listener;

    public MarqueeOverlay(Context context, DismissListener listener) {
        super(context);
        this.listener = listener;

        setBackgroundColor(0xFF0B615E);

        // Logo laden und rund zuschneiden
        try {
            InputStream is = context.getAssets().open("marquee_logo.png");
            Bitmap original = BitmapFactory.decodeStream(is);
            is.close();

            if (original != null) {
                Bitmap rounded = toCircle(original);
                ImageView iv = new ImageView(context);
                iv.setImageBitmap(rounded);
                iv.setScaleType(ImageView.ScaleType.CENTER_INSIDE);

                int sizePx = dpToPx(context, 280);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(sizePx, sizePx);
                lp.gravity = android.view.Gravity.CENTER;
                addView(iv, lp);
            }
        } catch (IOException e) {
            // Fallback: nur Hintergrundfarbe
        }
    }

    private Bitmap toCircle(Bitmap src) {
        int size = Math.min(src.getWidth(), src.getHeight());
        Bitmap output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(output);

        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        Rect rect = new Rect(0, 0, size, size);
        RectF rectF = new RectF(rect);

        canvas.drawOval(rectF, paint);
        paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));

        int offset = (src.getWidth() - size) / 2;
        canvas.drawBitmap(src, new Rect(offset, offset, offset + size, offset + size), rect, paint);
        return output;
    }

    private int dpToPx(Context ctx, int dp) {
        return Math.round(dp * ctx.getResources().getDisplayMetrics().density);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (listener != null) listener.onDismiss();
        }
        return true;
    }
}
