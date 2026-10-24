package com.dabdavis.perfectdark;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.hardware.input.InputManager;
import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;

/**
 * The on-screen controls, drawn over SDL's surface.
 *
 * They are player 1's controller: what is held goes to the game as SDL pad
 * buttons and sticks (nativeSetPad, port/src/androidtouch.c), which the game
 * adds to the pad in slot 0, so the controller binds apply and a real pad
 * still plays as player 1. The left of the screen is the movement stick, put
 * down where the thumb lands; a drag anywhere else that is not a button looks
 * around, as far from where it started as the right stick would be pushed.
 *
 * The small TOUCH button top left switches them on and off for good (kept in
 * the app's preferences). While they are on they also hide by themselves when
 * a gamepad is connected, and come back at a touch.
 */
public class TouchControlsView extends View implements InputManager.InputDeviceListener {
    // SDL_GameControllerButton numbers, and the triggers as VK_JOY counts them
    private static final int BTN_A = 0;
    private static final int BTN_B = 1;
    private static final int BTN_X = 2;
    private static final int BTN_Y = 3;
    private static final int BTN_START = 6;
    private static final int BTN_LSTICK = 7;
    private static final int BTN_LSHOULDER = 9;
    private static final int BTN_RSHOULDER = 10;
    private static final int BTN_LTRIG = 30;
    private static final int BTN_RTRIG = 31;

    private static native void nativeSetPad(int buttons, float lx, float ly, float rx, float ry);

    private static class Button {
        final String label;
        final String sub;
        final int bit;
        final float fx, fy; // centre, as a fraction of the view
        final float rdp;    // radius in dp
        float cx, cy, r;
        int pointer = -1;

        Button(String label, String sub, int bit, float fx, float fy, float rdp) {
            this.label = label;
            this.sub = sub;
            this.bit = bit;
            this.fx = fx;
            this.fy = fy;
            this.rdp = rdp;
        }
    }

    private final ArrayList<Button> buttons = new ArrayList<>();
    private final float density;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint subText = new Paint(Paint.ANTI_ALIAS_FLAG);

    // the movement stick: where it was put down, and where the thumb is
    private int stickPointer = -1;
    private float stickOx, stickOy, stickX, stickY;
    private float stickRadius;

    // looking: where the drag started, and where it is
    private int lookPointer = -1;
    private float lookOx, lookOy, lookX, lookY;
    private float lookRadius;

    // the player's switch (the TOUCH button), and whether a pad hid them
    private static final String PREFS = "touchcontrols";
    private static final String PREF_ENABLED = "enabled";
    private final SharedPreferences prefs;
    private boolean enabled;
    private boolean padHidden;
    private boolean shown;
    private final Button toggle = new Button("TOUCH", "", -1, 0.035f, 0.07f, 22);
    private int sentButtons = 0;
    private final float[] sentAxes = new float[4];

    public TouchControlsView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        setFocusable(false);
        setFocusableInTouchMode(false);

        buttons.add(new Button("FIRE", "RT", BTN_RTRIG, 0.915f, 0.70f, 52));
        buttons.add(new Button("AIM", "LT", BTN_LTRIG, 0.795f, 0.84f, 38));
        buttons.add(new Button("RELOAD", "X", BTN_X, 0.795f, 0.57f, 30));
        buttons.add(new Button("WEAPON", "Y", BTN_Y, 0.915f, 0.42f, 30));
        buttons.add(new Button("USE", "A", BTN_A, 0.675f, 0.70f, 32));
        buttons.add(new Button("BACK", "B", BTN_B, 0.675f, 0.88f, 26));
        buttons.add(new Button("ALT", "RB", BTN_RSHOULDER, 0.80f, 0.33f, 26));
        buttons.add(new Button("RADIAL", "LB", BTN_LSHOULDER, 0.075f, 0.30f, 26));
        buttons.add(new Button("CROUCH", "L3", BTN_LSTICK, 0.185f, 0.30f, 26));
        buttons.add(new Button("PAUSE", "", BTN_START, 0.50f, 0.075f, 24));

        fill.setStyle(Paint.Style.FILL);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2 * density);
        text.setTextAlign(Paint.Align.CENTER);
        text.setFakeBoldText(true);
        text.setColor(0xD0FFFFFF);
        subText.setTextAlign(Paint.Align.CENTER);
        subText.setColor(0x90FFFFFF);

        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        enabled = prefs.getBoolean(PREF_ENABLED, true);

        InputManager im = (InputManager) context.getSystemService(Context.INPUT_SERVICE);
        if (im != null) {
            im.registerInputDeviceListener(this, new Handler(Looper.getMainLooper()));
        }
        padHidden = gamepadConnected();
        shown = enabled && !padHidden;
    }

    private static boolean gamepadConnected() {
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice dev = InputDevice.getDevice(id);
            if (dev == null || dev.isVirtual()) {
                continue;
            }
            int src = dev.getSources();
            if ((src & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD
                    || (src & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
                return true;
            }
        }
        return false;
    }

    private void updateShown() {
        boolean on = enabled && !padHidden;
        if (shown == on) {
            return;
        }
        shown = on;
        if (!on) {
            releaseAll();
        }
        invalidate();
    }

    private void setPadHidden(boolean hidden) {
        padHidden = hidden;
        updateShown();
    }

    private void setTouchEnabled(boolean on) {
        enabled = on;
        prefs.edit().putBoolean(PREF_ENABLED, on).apply();
        // switched on by hand: shown, pad or not
        padHidden = false;
        updateShown();
        invalidate();
    }

    @Override public void onInputDeviceAdded(int deviceId) { setPadHidden(gamepadConnected()); }
    @Override public void onInputDeviceRemoved(int deviceId) { setPadHidden(gamepadConnected()); }
    @Override public void onInputDeviceChanged(int deviceId) { }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        // scaled down on a small screen so the cluster still fits
        float scale = Math.min(1f, Math.min(w, h) / (360f * density));
        for (Button b : buttons) {
            b.cx = b.fx * w;
            b.cy = b.fy * h;
            b.r = b.rdp * density * scale;
        }
        toggle.cx = Math.max(toggle.fx * w, toggle.rdp * density * scale * 1.3f);
        toggle.cy = Math.max(toggle.fy * h, toggle.rdp * density * scale * 1.3f);
        toggle.r = toggle.rdp * density * scale;
        stickRadius = 64 * density * scale;
        lookRadius = 56 * density * scale;
        text.setTextSize(12 * density * scale);
        subText.setTextSize(10 * density * scale);
    }

    private float defaultStickX() { return 0.15f * getWidth(); }
    private float defaultStickY() { return 0.72f * getHeight(); }

    private Button buttonAt(float x, float y) {
        Button best = null;
        float bestd = Float.MAX_VALUE;
        for (Button b : buttons) {
            float dx = x - b.cx, dy = y - b.cy;
            float d = dx * dx + dy * dy;
            float reach = b.r * 1.25f;
            if (d <= reach * reach && d < bestd) {
                best = b;
                bestd = d;
            }
        }
        return best;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getActionMasked();
        int idx = ev.getActionIndex();

        // the TOUCH button, whatever else is up
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            float dx = ev.getX(idx) - toggle.cx, dy = ev.getY(idx) - toggle.cy;
            float reach = toggle.r * 1.4f;
            if (dx * dx + dy * dy <= reach * reach) {
                setTouchEnabled(!enabled);
                return true;
            }
        }

        if (!enabled) {
            return true;
        }

        if (!shown) {
            // hidden by a pad: the first touch brings them back, pressing nothing
            if (action == MotionEvent.ACTION_DOWN) {
                setPadHidden(false);
            }
            return true;
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN: {
                int id = ev.getPointerId(idx);
                float x = ev.getX(idx), y = ev.getY(idx);
                Button b = buttonAt(x, y);
                if (b != null && b.pointer < 0) {
                    b.pointer = id;
                } else if (stickPointer < 0 && x < 0.40f * getWidth() && y > 0.40f * getHeight()) {
                    stickPointer = id;
                    stickOx = stickX = x;
                    stickOy = stickY = y;
                } else if (lookPointer < 0 && x >= 0.40f * getWidth()) {
                    lookPointer = id;
                    lookOx = lookX = x;
                    lookOy = lookY = y;
                }
                break;
            }
            case MotionEvent.ACTION_MOVE:
                for (int i = 0; i < ev.getPointerCount(); i++) {
                    int id = ev.getPointerId(i);
                    if (id == stickPointer) {
                        stickX = ev.getX(i);
                        stickY = ev.getY(i);
                    } else if (id == lookPointer) {
                        lookX = ev.getX(i);
                        lookY = ev.getY(i);
                    }
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL: {
                if (action == MotionEvent.ACTION_CANCEL) {
                    releaseAll();
                    break;
                }
                int id = ev.getPointerId(idx);
                release(id);
                break;
            }
        }

        send();
        invalidate();
        return true;
    }

    private void release(int id) {
        if (id == stickPointer) {
            stickPointer = -1;
        }
        if (id == lookPointer) {
            lookPointer = -1;
        }
        for (Button b : buttons) {
            if (b.pointer == id) {
                b.pointer = -1;
            }
        }
    }

    private void releaseAll() {
        stickPointer = -1;
        lookPointer = -1;
        for (Button b : buttons) {
            b.pointer = -1;
        }
        send();
    }

    private static float clampUnit(float v) {
        return v < -1f ? -1f : (v > 1f ? 1f : v);
    }

    // a drag as a stick: its offset over the radius, kept inside the circle
    private static void toStick(float dx, float dy, float radius, float[] out, int at) {
        float x = dx / radius, y = dy / radius;
        float len = (float) Math.sqrt(x * x + y * y);
        if (len > 1f) {
            x /= len;
            y /= len;
        }
        out[at] = clampUnit(x);
        out[at + 1] = clampUnit(y);
    }

    private void send() {
        int mask = 0;
        for (Button b : buttons) {
            if (b.pointer >= 0) {
                mask |= 1 << b.bit;
            }
        }

        float[] axes = new float[4];
        if (stickPointer >= 0) {
            toStick(stickX - stickOx, stickY - stickOy, stickRadius, axes, 0);
        }
        if (lookPointer >= 0) {
            toStick(lookX - lookOx, lookY - lookOy, lookRadius, axes, 2);
        }

        boolean same = mask == sentButtons;
        for (int i = 0; i < 4 && same; i++) {
            same = axes[i] == sentAxes[i];
        }
        if (same) {
            return;
        }

        sentButtons = mask;
        System.arraycopy(axes, 0, sentAxes, 0, 4);
        nativeSetPad(mask, axes[0], axes[1], axes[2], axes[3]);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // the switch, faint when the controls are off or hidden
        fill.setColor(shown ? 0x30FFFFFF : 0x18FFFFFF);
        stroke.setColor(shown ? 0x70FFFFFF : 0x40FFFFFF);
        canvas.drawCircle(toggle.cx, toggle.cy, toggle.r, fill);
        canvas.drawCircle(toggle.cx, toggle.cy, toggle.r, stroke);
        text.setAlpha(shown ? 0xD0 : 0x70);
        canvas.drawText(toggle.label, toggle.cx, toggle.cy, text);
        text.setAlpha(0xD0);
        subText.setAlpha(shown ? 0x90 : 0x60);
        canvas.drawText(enabled ? "ON" : "OFF", toggle.cx, toggle.cy - subText.ascent() + 2 * density, subText);
        subText.setAlpha(0x90);

        if (!shown) {
            return;
        }

        // the movement stick, at rest bottom left or where it was put down
        float ox = stickPointer >= 0 ? stickOx : defaultStickX();
        float oy = stickPointer >= 0 ? stickOy : defaultStickY();
        fill.setColor(0x30FFFFFF);
        stroke.setColor(0x70FFFFFF);
        canvas.drawCircle(ox, oy, stickRadius, fill);
        canvas.drawCircle(ox, oy, stickRadius, stroke);
        float kx = ox, ky = oy;
        if (stickPointer >= 0) {
            kx = ox + sentAxes[0] * stickRadius;
            ky = oy + sentAxes[1] * stickRadius;
        }
        fill.setColor(stickPointer >= 0 ? 0x90FFFFFF : 0x60FFFFFF);
        canvas.drawCircle(kx, ky, stickRadius * 0.45f, fill);

        // the look drag, while there is one
        if (lookPointer >= 0) {
            stroke.setColor(0x50FFFFFF);
            canvas.drawCircle(lookOx, lookOy, lookRadius, stroke);
            fill.setColor(0x50FFFFFF);
            canvas.drawCircle(lookOx + sentAxes[2] * lookRadius, lookOy + sentAxes[3] * lookRadius,
                    lookRadius * 0.35f, fill);
        }

        for (Button b : buttons) {
            boolean held = b.pointer >= 0;
            fill.setColor(held ? 0x80FFFFFF : 0x30FFFFFF);
            stroke.setColor(held ? 0xC0FFFFFF : 0x70FFFFFF);
            canvas.drawCircle(b.cx, b.cy, b.r, fill);
            canvas.drawCircle(b.cx, b.cy, b.r, stroke);
            if (b.sub.isEmpty()) {
                canvas.drawText(b.label, b.cx, b.cy - (text.ascent() + text.descent()) / 2, text);
            } else {
                canvas.drawText(b.label, b.cx, b.cy, text);
                canvas.drawText(b.sub, b.cx, b.cy - subText.ascent() + 2 * density, subText);
            }
        }
    }
}
