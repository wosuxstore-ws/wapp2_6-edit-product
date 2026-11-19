package com.wl.orders;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

/**
 * Full-screen overlay for the scan page: soft dark surround with a rounded cut-out, glowing corner
 * brackets, and a blue scan line that glides up and down. The line's ends fade out to a soft point
 * and it leaves a faint light trail behind it, so the motion feels smooth rather than mechanical.
 */
public class QrScanOverlay extends View {

    private final float dp;
    private final int accent;
    private final int accentLight;

    private final RectF frame = new RectF();
    private final Path scrim = new Path();
    private final Path frameShape = new Path();
    private final Path corners = new Path();

    private final Paint scrimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cornerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint trailPaint = new Paint();
    private final Paint bandPaint = new Paint();
    private final Paint corePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint maskPaint = new Paint();

    private float cornerRadius;
    private float travelTop, travelBottom;
    private final float trailHeight;
    private final float bandHalf;

    private float progress = 0f;       // 0..1 position of the line inside the frame
    private boolean movingDown = true;
    private float fade = 1f;           // 1 = line visible, 0 = hidden (while the popup is open)

    private ValueAnimator sweep;
    private ValueAnimator fader;
    private boolean active = true;

    public QrScanOverlay(Context context) {
        this(context, null);
    }

    public QrScanOverlay(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = context.getResources().getDisplayMetrics().density;
        accent = ContextCompat.getColor(context, R.color.home_accent);
        accentLight = Color.parseColor("#BFDDFF");
        trailHeight = 56 * dp;
        bandHalf = 16 * dp;

        scrimPaint.setColor(Color.parseColor("#80000000"));

        cornerPaint.setStyle(Paint.Style.STROKE);
        cornerPaint.setStrokeCap(Paint.Cap.ROUND);
        cornerPaint.setStrokeJoin(Paint.Join.ROUND);
        cornerPaint.setColor(Color.WHITE);
        cornerPaint.setStrokeWidth(4 * dp);

        glowPaint.setStyle(Paint.Style.STROKE);
        glowPaint.setStrokeCap(Paint.Cap.ROUND);
        glowPaint.setStrokeJoin(Paint.Join.ROUND);

        corePaint.setStyle(Paint.Style.FILL);

        maskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);

        float size = Math.min(w * 0.72f, 300 * dp);
        float cx = w / 2f, cy = h / 2f;
        frame.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        cornerRadius = 28 * dp;

        frameShape.reset();
        frameShape.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW);

        scrim.reset();
        scrim.setFillType(Path.FillType.EVEN_ODD);
        scrim.addRect(0, 0, w, h, Path.Direction.CW);
        scrim.addRoundRect(frame, cornerRadius, cornerRadius, Path.Direction.CW);

        buildCorners(frame, cornerRadius, 40 * dp);

        travelTop = frame.top + 14 * dp;
        travelBottom = frame.bottom - 14 * dp;

        // Everything below is drawn around y = 0 and moved to the line's position when drawing.
        trailPaint.setShader(null);
        bandPaint.setShader(new LinearGradient(0, -bandHalf, 0, bandHalf,
                new int[]{withAlpha(accent, 0), withAlpha(accent, 110), withAlpha(accent, 0)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));

        // Left/right ends of the line (and its glow) melt away into a soft point.
        maskPaint.setShader(new LinearGradient(frame.left, 0, frame.right, 0,
                new int[]{0x00FFFFFF, 0xFFFFFFFF, 0xFFFFFFFF, 0x00FFFFFF},
                new float[]{0f, 0.22f, 0.78f, 1f}, Shader.TileMode.CLAMP));

        corePaint.setShader(new LinearGradient(0, -1.5f * dp, 0, 1.5f * dp,
                new int[]{withAlpha(accent, 0), accentLight, withAlpha(accent, 0)},
                new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP));
    }

    /** Four rounded bracket corners, each with two straight arms. */
    private void buildCorners(RectF f, float r, float arm) {
        corners.reset();
        float l = f.left, t = f.top, rt = f.right, b = f.bottom;
        // top-left
        corners.moveTo(l, t + arm);
        corners.lineTo(l, t + r);
        corners.arcTo(new RectF(l, t, l + 2 * r, t + 2 * r), 180, 90);
        corners.lineTo(l + arm, t);
        // top-right
        corners.moveTo(rt - arm, t);
        corners.lineTo(rt - r, t);
        corners.arcTo(new RectF(rt - 2 * r, t, rt, t + 2 * r), 270, 90);
        corners.lineTo(rt, t + arm);
        // bottom-right
        corners.moveTo(rt, b - arm);
        corners.lineTo(rt, b - r);
        corners.arcTo(new RectF(rt - 2 * r, b - 2 * r, rt, b), 0, 90);
        corners.lineTo(rt - arm, b);
        // bottom-left
        corners.moveTo(l + arm, b);
        corners.lineTo(l + r, b);
        corners.arcTo(new RectF(l, b - 2 * r, l + 2 * r, b), 90, 90);
        corners.lineTo(l, b - arm);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (frame.isEmpty()) return;

        // dark surround with the scan window cut out
        canvas.drawPath(scrim, scrimPaint);

        // corner brackets: a few wide, faint strokes underneath fake a soft glow
        glowPaint.setColor(withAlpha(accent, 28));
        glowPaint.setStrokeWidth(14 * dp);
        canvas.drawPath(corners, glowPaint);
        glowPaint.setColor(withAlpha(accent, 56));
        glowPaint.setStrokeWidth(9 * dp);
        canvas.drawPath(corners, glowPaint);
        canvas.drawPath(corners, cornerPaint);

        drawBeam(canvas);
    }

    private void drawBeam(Canvas canvas) {
        // fade near the top/bottom turnaround so the line never "bounces" visibly
        float edge = smoothstep(0f, 0.07f, progress) * smoothstep(0f, 0.07f, 1f - progress);
        float alpha = edge * fade;
        if (alpha <= 0.01f) return;

        float y = travelTop + (travelBottom - travelTop) * progress;

        int save = canvas.save();
        canvas.clipPath(frameShape);
        int layer = canvas.saveLayerAlpha(frame, (int) (alpha * 255));

        canvas.translate(0, y);
        // light trail behind the line (above it when moving down, below it when moving up)
        float t0 = movingDown ? -trailHeight : 0;
        float t1 = movingDown ? 0 : trailHeight;
        trailPaint.setShader(new LinearGradient(0, movingDown ? t0 : t1, 0, movingDown ? t1 : t0,
                withAlpha(accent, 0), withAlpha(accent, 70), Shader.TileMode.CLAMP));
        canvas.drawRect(frame.left, t0, frame.right, t1, trailPaint);
        // soft glow band + thin bright core
        canvas.drawRect(frame.left, -bandHalf, frame.right, bandHalf, bandPaint);
        canvas.drawRect(frame.left, -1.5f * dp, frame.right, 1.5f * dp, corePaint);
        canvas.translate(0, -y);

        // melt the left and right ends
        canvas.drawRect(frame, maskPaint);

        canvas.restoreToCount(layer);
        canvas.restoreToCount(save);
    }

    // ---- animation --------------------------------------------------------------------------

    /** Line visible and moving (true) or hidden and paused, e.g. while the form popup is open. */
    public void setActive(boolean on) {
        if (active == on) return;
        active = on;
        if (fader != null) fader.cancel();
        fader = ValueAnimator.ofFloat(fade, on ? 1f : 0f);
        fader.setDuration(on ? 280 : 180);
        fader.addUpdateListener(a -> {
            fade = (float) a.getAnimatedValue();
            invalidate();
        });
        fader.start();
    }

    private void startSweep() {
        if (sweep != null) return;
        sweep = ValueAnimator.ofFloat(0f, 1f);
        sweep.setDuration(2400);
        sweep.setRepeatCount(ValueAnimator.INFINITE);
        sweep.setRepeatMode(ValueAnimator.REVERSE);
        // ease-in-out sine: slows gently at both ends, fastest in the middle
        sweep.setInterpolator(new PathInterpolator(0.37f, 0f, 0.63f, 1f));
        sweep.addUpdateListener(a -> {
            float p = (float) a.getAnimatedValue();
            if (p != progress) movingDown = p > progress;
            progress = p;
            invalidate();
        });
        sweep.start();
    }

    private void stopSweep() {
        if (sweep != null) {
            sweep.cancel();
            sweep = null;
        }
        if (fader != null) fader.cancel();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startSweep();
    }

    @Override
    protected void onDetachedFromWindow() {
        stopSweep();
        super.onDetachedFromWindow();
    }

    // ---- helpers ----------------------------------------------------------------------------

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static float smoothstep(float a, float b, float x) {
        float t = Math.max(0f, Math.min(1f, (x - a) / (b - a)));
        return t * t * (3f - 2f * t);
    }
}
