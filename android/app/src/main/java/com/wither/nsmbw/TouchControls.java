package com.wither.nsmbw;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.util.SparseIntArray;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The on-screen remote, drawn in code in the colours of an old treasure map. */
public class TouchControls extends View {
    // VirtualRemote::TouchButton (runtime/include/virtual_remote.h).
    private static final int LEFT = 0x001, RIGHT = 0x002, UP = 0x004, DOWN = 0x008;
    private static final int ONE = 0x010, TWO = 0x020, A = 0x040, PLUS = 0x080, MINUS = 0x100;
    /** Not a remote button: reported apart, as the shake flag. */
    private static final int SHAKE = 0x10000;
    /** Not a remote button either: asks to leave the game. */
    private static final int EXIT = 0x20000;
    /** Tilt the remote while held, reported apart like shake. */
    private static final int TILT_LEFT = 0x40000, TILT_RIGHT = 0x80000;
    private static final int NOT_REMOTE = SHAKE | EXIT | TILT_LEFT | TILT_RIGHT;
    private static final String DPAD_ID = "dpad";
    private static final int DPAD = LEFT | RIGHT | UP | DOWN;

    private static final int INK = Color.rgb(0x2E, 0x1B, 0x0C);
    private static final int RUST = Color.rgb(0x8A, 0x45, 0x18);
    private static final int GOLD = Color.rgb(0xD9, 0xA0, 0x35);
    private static final int GOLD_LIGHT = Color.rgb(0xF7, 0xDC, 0x8A);
    private static final int PARCHMENT = Color.rgb(0xF0, 0xD9, 0xA4);
    private static final int PARCHMENT_DARK = Color.rgb(0xC9, 0x96, 0x4A);

    private static final class Medallion {
        final int bit;
        final String label;
        /** The remote input this stands for, shown small under the label; null for none. */
        final String mapping;
        final float xFromRight, y, radius;
        float x, cy, r;
        Path teeth;
        Shader rim, face, facePressed;

        /** Positions are fractions of the view height, so the layout scales with the screen. */
        Medallion(int bit, String label, String mapping, float xFromRight, float y, float radius) {
            this.bit = bit;
            this.label = label;
            this.mapping = mapping;
            this.xFromRight = xFromRight;
            this.y = y;
            this.radius = radius;
        }
    }

    private final Medallion[] medallions = {
        new Medallion(TWO, "JUMP", "2", 0.17f, 0.64f, 0.125f),
        new Medallion(ONE, "RUN", "1", 0.43f, 0.82f, 0.125f),
        new Medallion(SHAKE, "SHAKE", "Shake", 0.42f, 0.50f, 0.105f),
        new Medallion(A, "A", "A", 0.13f, 0.32f, 0.075f),
    };
    /** Centred at the top of the screen, so these place themselves apart from the others. */
    private final Medallion plus = new Medallion(PLUS, "+", "+", 0, 0.085f, 0.055f);
    private final Medallion minus = new Medallion(MINUS, "−", "−", 0, 0.085f, 0.055f);

    private final Medallion exit = new Medallion(EXIT, "EXIT", null, 0, 0.085f, 0.055f);
    private final Medallion tiltLeft = new Medallion(TILT_LEFT, "\u21BA", "Tilt", 0, 0.27f, 0.075f);
    private final Medallion tiltRight = new Medallion(TILT_RIGHT, "\u21BB", "Tilt", 0, 0.27f, 0.075f);
    /** Offsets from the default places, in fractions of the view height, by control. */
    private final Map<String, float[]> moved = new HashMap<>();

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseIntArray pointers = new SparseIntArray();
    private final Runnable onChange;
    private final Runnable onExit;
    private final float scale;
    private final boolean tiltButtons;
    private boolean editing;
    private String dragged;
    private float grabX, grabY;

    private float dpadX, dpadY, dpadR;
    private Path dpadCross, dpadInner;
    private Shader dpadRim;
    private int pressed;

    public TouchControls(Context context, float scale, float opacity, boolean tiltButtons, String layout,
            Runnable onChange, Runnable onExit) {
        super(context);
        this.scale = scale;
        this.tiltButtons = tiltButtons;
        readLayout(layout);
        this.onChange = onChange;
        this.onExit = onExit;
        setAlpha(opacity);
        text.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        text.setTextAlign(Paint.Align.CENTER);
        caption.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        caption.setTextAlign(Paint.Align.CENTER);
        caption.setTextSize(10f * getResources().getDisplayMetrics().scaledDensity);
    }

    /** The remote buttons held now, as VirtualRemote::TouchButton bits. */
    public int buttons() {
        return pressed & ~NOT_REMOTE;
    }

    public boolean shakeHeld() {
        return (pressed & SHAKE) != 0;
    }

    /** -1 for the tilt-left button, 1 for tilt-right, 0 for neither. */
    public int tiltHeld() {
        return ((pressed & TILT_RIGHT) != 0 ? 1 : 0) - ((pressed & TILT_LEFT) != 0 ? 1 : 0);
    }

    /** In this mode a finger moves the controls instead of pressing them. */
    public void setEditing(boolean value) {
        editing = value;
    }

    /** The moved controls as "id x y;id x y", for saving. */
    public String layout() {
        StringBuilder text = new StringBuilder();
        for (Map.Entry<String, float[]> entry : moved.entrySet()) {
            text.append(String.format(Locale.ROOT, "%s %.4f %.4f;",
                    entry.getKey(), entry.getValue()[0], entry.getValue()[1]));
        }
        return text.toString();
    }

    public void resetLayout() {
        moved.clear();
        arrange(getWidth(), getHeight());
        invalidate();
    }

    private void readLayout(String layout) {
        for (String entry : layout.split(";")) {
            String[] parts = entry.trim().split(" ");
            if (parts.length == 3) {
                try {
                    moved.put(parts[0], new float[] {Float.parseFloat(parts[1]), Float.parseFloat(parts[2])});
                } catch (NumberFormatException e) {
                    continue;  // a damaged entry: that control stays in its default place
                }
            }
        }
    }

    /** Lets go of everything, for when the game leaves the screen with fingers still down. */
    public void release() {
        pointers.clear();
        update(false);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        arrange(w, h);
    }

    private void arrange(int w, int h) {
        float[] offset = moved.getOrDefault(DPAD_ID, new float[2]);
        dpadR = 0.235f * h * scale;
        dpadX = (0.29f + offset[0]) * h;
        dpadY = (0.68f + offset[1]) * h;
        dpadCross = cross(dpadX, dpadY, dpadR, 0.36f);
        dpadInner = cross(dpadX, dpadY, dpadR * 0.9f, 0.27f);
        dpadRim = new RadialGradient(dpadX, dpadY, dpadR * 1.1f, GOLD_LIGHT, RUST, Shader.TileMode.CLAMP);

        for (Medallion m : medallions) {
            place(m, w - m.xFromRight * h, m.radius * scale, h);
        }
        place(plus, w / 2f + 0.085f * h, plus.radius, h);
        place(minus, w / 2f - 0.085f * h, minus.radius, h);
        place(exit, 0.12f * h, exit.radius, h);
        place(tiltLeft, 0.12f * h, tiltLeft.radius * scale, h);
        place(tiltRight, 0.33f * h, tiltRight.radius * scale, h);
    }

    private void place(Medallion m, float defaultX, float radius, int h) {
        float[] offset = moved.getOrDefault(m.label, new float[2]);
        float x = defaultX + offset[0] * h;
        float y = (m.y + offset[1]) * h;
        float r = radius * h;
        m.x = x;
        m.cy = y;
        m.r = r;
        m.teeth = teeth(x, y, r, Math.max(14, Math.round(r / 7f)));
        m.rim = new RadialGradient(x, y - r * 0.2f, r, GOLD_LIGHT, GOLD, Shader.TileMode.CLAMP);
        m.face = new RadialGradient(x, y - r * 0.25f, r * 0.85f, PARCHMENT, PARCHMENT_DARK, Shader.TileMode.CLAMP);
        m.facePressed = new RadialGradient(x, y, r * 0.8f, RUST, INK, Shader.TileMode.CLAMP);
    }

    /** A plus shape whose arms reach `r` from the centre and are 2 * half * r wide. */
    private static Path cross(float x, float y, float r, float half) {
        float a = r * half;
        Path path = new Path();
        path.moveTo(x - a, y - r);
        path.lineTo(x + a, y - r);
        path.lineTo(x + a, y - a);
        path.lineTo(x + r, y - a);
        path.lineTo(x + r, y + a);
        path.lineTo(x + a, y + a);
        path.lineTo(x + a, y + r);
        path.lineTo(x - a, y + r);
        path.lineTo(x - a, y + a);
        path.lineTo(x - r, y + a);
        path.lineTo(x - r, y - a);
        path.lineTo(x - a, y - a);
        path.close();
        return path;
    }

    /** The zigzag edge of a medallion, like the border of a map. */
    private static Path teeth(float x, float y, float r, int count) {
        Path path = new Path();
        for (int i = 0; i < count * 2; i++) {
            double angle = Math.PI * i / count;
            float radius = (i % 2 == 0) ? r : r * 0.88f;
            float px = x + radius * (float) Math.cos(angle);
            float py = y + radius * (float) Math.sin(angle);
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.close();
        return path;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawDpad(canvas);
        for (Medallion m : medallions) {
            drawMedallion(canvas, m);
        }
        drawMedallion(canvas, plus);
        drawMedallion(canvas, minus);
        drawMedallion(canvas, exit);
        if (tiltButtons) {
            drawMedallion(canvas, tiltLeft);
            drawMedallion(canvas, tiltRight);
        }
    }

    private void drawDpad(Canvas canvas) {
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(dpadR * 0.07f);
        paint.setColor(INK);
        canvas.drawPath(dpadCross, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setShader(dpadRim);
        canvas.drawPath(dpadCross, paint);
        paint.setShader(null);
        paint.setColor(PARCHMENT);
        canvas.drawPath(dpadInner, paint);

        drawArrow(canvas, UP, 0, -1);
        drawArrow(canvas, DOWN, 0, 1);
        drawArrow(canvas, LEFT, -1, 0);
        drawArrow(canvas, RIGHT, 1, 0);

        float d = dpadR * 0.15f;
        Path diamond = new Path();
        diamond.moveTo(dpadX, dpadY - d);
        diamond.lineTo(dpadX + d, dpadY);
        diamond.lineTo(dpadX, dpadY + d);
        diamond.lineTo(dpadX - d, dpadY);
        diamond.close();
        paint.setColor(GOLD);
        canvas.drawPath(diamond, paint);

        drawCaption(canvas, "D-Pad", dpadX + dpadR * 0.68f, dpadY + dpadR * 0.68f);
    }

    /** A small dark tag with the remote input a control stands for. */
    private void drawCaption(Canvas canvas, String value, float x, float y) {
        float halfWidth = caption.measureText(value) / 2f + caption.getTextSize() * 0.45f;
        float halfHeight = caption.getTextSize() * 0.72f;
        paint.setShader(null);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(INK);
        canvas.drawRoundRect(x - halfWidth, y - halfHeight, x + halfWidth, y + halfHeight,
                halfHeight, halfHeight, paint);
        caption.setColor(GOLD_LIGHT);
        canvas.drawText(value, x, y - (caption.ascent() + caption.descent()) / 2f, caption);
    }


    private void drawArrow(Canvas canvas, int bit, int dx, int dy) {
        boolean down = (pressed & bit) != 0;
        float a = dpadR * 0.27f;
        if (down) {
            RectF arm = new RectF(
                    dpadX + (dx < 0 ? -dpadR * 0.9f : dx > 0 ? a : -a),
                    dpadY + (dy < 0 ? -dpadR * 0.9f : dy > 0 ? a : -a),
                    dpadX + (dx > 0 ? dpadR * 0.9f : dx < 0 ? -a : a),
                    dpadY + (dy > 0 ? dpadR * 0.9f : dy < 0 ? -a : a));
            paint.setColor(RUST);
            canvas.drawRect(arm, paint);
        }
        float tip = dpadR * 0.82f;
        float base = dpadR * 0.5f;
        float wing = dpadR * 0.18f;
        Path arrow = new Path();
        arrow.moveTo(dpadX + dx * tip, dpadY + dy * tip);
        arrow.lineTo(dpadX + dx * base + dy * wing, dpadY + dy * base + dx * wing);
        arrow.lineTo(dpadX + dx * base - dy * wing, dpadY + dy * base - dx * wing);
        arrow.close();
        paint.setColor(down ? GOLD_LIGHT : INK);
        canvas.drawPath(arrow, paint);
    }

    private void drawMedallion(Canvas canvas, Medallion m) {
        boolean down = (pressed & m.bit) != 0;
        float y = m.cy;
        canvas.save();
        if (down) {
            canvas.scale(0.94f, 0.94f, m.x, y);
        }
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(null);
        paint.setColor(INK);
        canvas.drawPath(m.teeth, paint);

        paint.setShader(m.rim);
        canvas.drawCircle(m.x, y, m.r * 0.84f, paint);

        paint.setShader(down ? m.facePressed : m.face);
        canvas.drawCircle(m.x, y, m.r * 0.7f, paint);

        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(m.r * 0.04f);
        paint.setColor(INK);
        canvas.drawCircle(m.x, y, m.r * 0.7f, paint);
        paint.setStyle(Paint.Style.FILL);

        // Sized so the longest word still sits inside the face.
        text.setTextSize(m.r * (m.label.length() > 2 ? 0.42f : m == plus || m == minus ? 1.15f : 0.85f));
        float width = text.measureText(m.label);
        if (width > m.r * 1.24f) {
            text.setTextSize(text.getTextSize() * m.r * 1.24f / width);
        }
        text.setColor(down ? GOLD_LIGHT : INK);
        float baseline = y - (text.ascent() + text.descent()) / 2f;
        canvas.drawText(m.label, m.x, baseline, text);
        if (m.mapping != null) {
            drawCaption(canvas, m.mapping, m.x, y + m.r * 0.86f);
        }
        canvas.restore();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (editing) {
            drag(event);
            return true;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < event.getPointerCount(); i++) {
                    pointers.put(event.getPointerId(i), hit(event.getX(i), event.getY(i)));
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                pointers.delete(event.getPointerId(event.getActionIndex()));
                break;
            case MotionEvent.ACTION_CANCEL:
                pointers.clear();
                break;
            default:
                return true;
        }
        update(true);
        return true;
    }

    private Medallion[] allMedallions() {
        return new Medallion[] {medallions[0], medallions[1], medallions[2], medallions[3],
            plus, minus, exit, tiltLeft, tiltRight};
    }

    private void drag(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                dragged = null;
                for (Medallion m : allMedallions()) {
                    boolean shown = tiltButtons || (m != tiltLeft && m != tiltRight);
                    if (shown && Math.hypot(x - m.x, y - m.cy) < m.r * 1.2f) {
                        dragged = m.label;
                        grabX = x - m.x;
                        grabY = y - m.cy;
                    }
                }
                if (dragged == null && Math.hypot(x - dpadX, y - dpadY) < dpadR * 1.1f) {
                    dragged = DPAD_ID;
                    grabX = x - dpadX;
                    grabY = y - dpadY;
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (dragged != null) {
                    moveDragged(x - grabX, y - grabY);
                }
                break;
            default:
                dragged = null;
                break;
        }
    }

    /** Takes the control to this centre, kept on the screen. */
    private void moveDragged(float x, float y) {
        int w = getWidth();
        int h = getHeight();
        float cx = Math.max(0, Math.min(w, x));
        float cy = Math.max(0, Math.min(h, y));
        float[] offset = moved.computeIfAbsent(dragged, key -> new float[2]);
        if (dragged.equals(DPAD_ID)) {
            offset[0] += (cx - dpadX) / h;
            offset[1] += (cy - dpadY) / h;
        } else {
            for (Medallion m : allMedallions()) {
                if (m.label.equals(dragged)) {
                    offset[0] += (cx - m.x) / h;
                    offset[1] += (cy - m.cy) / h;
                }
            }
        }
        arrange(w, h);
        invalidate();
    }

    /** What a finger at this point holds; a finger may slide from one control to another. */
    private int hit(float x, float y) {
        float dx = x - dpadX;
        float dy = y - dpadY;
        float distance = (float) Math.hypot(dx, dy);
        if (distance < dpadR * 1.45f) {
            return distance < dpadR * 0.18f ? 0 : direction(dx, dy);
        }
        int bits = hitMedallion(plus, x, y) | hitMedallion(minus, x, y) | hitMedallion(exit, x, y);
        if (tiltButtons) {
            bits |= hitMedallion(tiltLeft, x, y) | hitMedallion(tiltRight, x, y);
        }
        for (Medallion m : medallions) {
            bits |= hitMedallion(m, x, y);
        }
        return bits;
    }

    private int hitMedallion(Medallion m, float x, float y) {
        return Math.hypot(x - m.x, y - m.cy) < m.r * 1.1f ? m.bit : 0;
    }

    /** Eight even sectors: tan(22.5 degrees) separates a straight push from a diagonal one. */
    private static int direction(float dx, float dy) {
        final float slope = 0.414f;
        int bits = 0;
        if (Math.abs(dx) > Math.abs(dy) * slope) {
            bits |= dx < 0 ? LEFT : RIGHT;
        }
        if (Math.abs(dy) > Math.abs(dx) * slope) {
            bits |= dy < 0 ? UP : DOWN;
        }
        return bits;
    }

    private void update(boolean feedback) {
        int now = 0;
        for (int i = 0; i < pointers.size(); i++) {
            now |= pointers.valueAt(i);
        }
        if (now == pressed) {
            return;
        }
        if (feedback && (now & ~pressed) != 0) {
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);
        }
        boolean exitPressed = (now & ~pressed & EXIT) != 0;
        pressed = now;
        invalidate();
        onChange.run();
        if (exitPressed) {
            onExit.run();
        }
    }
}
