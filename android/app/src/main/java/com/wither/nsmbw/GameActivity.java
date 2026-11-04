package com.wither.nsmbw;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.system.ErrnoException;
import android.system.Os;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/** Prepares what the native runtime expects, then lets SDL run libmain.so. */
public class GameActivity extends SDLActivity
        implements SensorEventListener, InputManager.InputDeviceListener {
    private static final String TAG = "WarioRecomp";

    /** Tilt 1.0 is this many degrees (kWarioMaxTilt in runtime/src/virtual_remote.cpp). */
    private static final float MAX_TILT_DEGREES = 85f;
    /** A phone is never held perfectly level; below this it counts as level. */
    private static final float TILT_DEAD_ZONE_DEGREES = 6f;
    /** Below this much sideways gravity the phone lies flat and has no meaningful tilt. */
    private static final float MIN_GRAVITY = 3f;
    private static final long SHAKE_HOLD_MS = 180;
    /** What the tilt buttons hold, the same as a gamepad's (kWarioButtonTilt). */
    private static final float BUTTON_TILT = 30f / MAX_TILT_DEGREES;

    private TouchControls controls;
    private float shakeAcceleration;
    private float tiltFactor;
    private SensorManager sensors;
    private InputManager inputs;
    private boolean hideWithGamepad;
    private float tilt;
    private long shakeUntil;
    private int sentButtons = -1;
    private boolean sentShake;
    private AlertDialog exitDialog;
    private float sentTilt;

    /** android_bridge.cpp: the on-screen remote and the phone's motion. */
    private static native void nativeSetTouchState(int buttons, boolean shake, float tilt);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            File runtime = new File(getFilesDir(), "runtime");
            File data = getExternalFilesDir(null);
            unpackRuntimeFiles(runtime);
            // Read by runtime/src/platform/host_platform.cpp.
            Os.setenv("WIICOMPILED_RUNTIME_DIR", runtime.getPath(), true);
            Os.setenv("WIICOMPILED_DATA_DIR", data.getPath(), true);
            // Read by runtime/src/hle/storage/dvd.cpp.
            if (GameOptions.flag(this, GameOptions.SHORT_INTRO, false)) {
                Os.setenv("WARIO_SHORT_INTRO", "1", true);
            }
            // Read by runtime/src/android/custom_driver.cpp.
            GpuDrivers.Driver driver = GpuDrivers.selected(this);
            if (driver != null) {
                Os.setenv("WIICOMPILED_HOOK_DIR", getApplicationInfo().nativeLibraryDir + "/", true);
                Os.setenv("WIICOMPILED_GPU_DRIVER_DIR", driver.folder.getPath() + "/", true);
                Os.setenv("WIICOMPILED_GPU_DRIVER_NAME", driver.library, true);
            }
        } catch (IOException | ErrnoException | PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Could not prepare the runtime files", e);
        }
        super.onCreate(savedInstanceState);
        if (mBrokenLibraries) {
            return;
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensors = getSystemService(SensorManager.class);
        inputs = getSystemService(InputManager.class);
        hideWithGamepad = GameOptions.flag(this, GameOptions.HIDE_WITH_GAMEPAD, true);
        shakeAcceleration = GameOptions.number(this, GameOptions.SHAKE_ACCELERATION, 15f);
        tiltFactor = GameOptions.number(this, GameOptions.TILT_FACTOR, 1f);
        if (GameOptions.flag(this, GameOptions.INVERT_TILT, false)) {
            tiltFactor = -tiltFactor;
        }
        controls = new TouchControls(this, GameOptions.number(this, GameOptions.OVERLAY_SCALE, 1f),
                GameOptions.number(this, GameOptions.OVERLAY_OPACITY, 0.8f),
                GameOptions.flag(this, GameOptions.TILT_BUTTONS, true),
                GameOptions.text(this, GameOptions.OVERLAY_LAYOUT, ""), this::sendInput, this::confirmExit);
        addContentView(controls, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controls == null) {
            return;
        }
        for (int type : new int[] {Sensor.TYPE_GRAVITY, Sensor.TYPE_LINEAR_ACCELERATION}) {
            Sensor sensor = sensors.getDefaultSensor(type);
            if (sensor != null) {
                sensors.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
            }
        }
        inputs.registerInputDeviceListener(this, null);
        updateOverlay();
    }

    @Override
    public void onInputDeviceAdded(int deviceId) {
        updateOverlay();
    }

    @Override
    public void onInputDeviceRemoved(int deviceId) {
        updateOverlay();
    }

    @Override
    public void onInputDeviceChanged(int deviceId) {
        updateOverlay();
    }

    /** A real pad reports both sources; some built-in keys report one of them. */
    private static boolean gamepadConnected() {
        int pad = InputDevice.SOURCE_GAMEPAD | InputDevice.SOURCE_JOYSTICK;
        for (int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            if (device != null && !device.isVirtual() && (device.getSources() & pad) == pad) {
                return true;
            }
        }
        return false;
    }

    private void updateOverlay() {
        boolean hide = hideWithGamepad && gamepadConnected();
        if (hide) {
            controls.release();
        }
        controls.setVisibility(hide ? View.GONE : View.VISIBLE);
    }

    @Override
    protected void onPause() {
        if (controls != null) {
            sensors.unregisterListener(this);
            inputs.unregisterInputDeviceListener(this);
            tilt = 0f;
            shakeUntil = 0;
            controls.release();
            sendInput();
        }
        super.onPause();
    }

    /** Back (key or gesture) asks to leave; the game itself never sees it. */
    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
            if (event.getAction() == KeyEvent.ACTION_UP) {
                confirmExit();
            }
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private void confirmExit() {
        if (exitDialog != null && exitDialog.isShowing()) {
            return;
        }
        controls.release();
        exitDialog = new AlertDialog.Builder(this)
                .setTitle("Exit to the launcher?")
                .setMessage("Progress since your last save will be lost.")
                .setPositiveButton("Exit", (dialog, which) -> exitToLauncher())
                .setNegativeButton("Keep playing", null)
                .show();
    }

    /** The game has its own process (android:process=":game"), so leaving ends it cleanly. */
    private void exitToLauncher() {
        startActivity(new Intent(this, LauncherActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        Process.killProcess(Process.myPid());
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
            tilt = tiltFromGravity(event.values[0], event.values[1]);
        } else if (Math.sqrt(event.values[0] * event.values[0] + event.values[1] * event.values[1]
                + event.values[2] * event.values[2]) > shakeAcceleration) {
            shakeUntil = SystemClock.uptimeMillis() + SHAKE_HOLD_MS;
        }
        sendInput();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    /** How far the screen is turned from level: -1 counter-clockwise .. 1 clockwise. */
    private float tiltFromGravity(float x, float y) {
        // Gravity in screen axes; the sensors report it in the phone's portrait axes.
        boolean flipped = getDisplay().getRotation() == Surface.ROTATION_270;
        float screenX = flipped ? -y : y;
        float screenY = flipped ? x : -x;
        if (Math.hypot(screenX, screenY) < MIN_GRAVITY) {
            return 0f;
        }
        float degrees = (float) Math.toDegrees(Math.atan2(-screenX, screenY));
        if (Math.abs(degrees) < TILT_DEAD_ZONE_DEGREES) {
            return 0f;
        }
        return Math.max(-1f, Math.min(1f, degrees * tiltFactor / MAX_TILT_DEGREES));
    }

    private void sendInput() {
        int buttons = controls.buttons();
        boolean shake = controls.shakeHeld() || SystemClock.uptimeMillis() < shakeUntil;
        int held = controls.tiltHeld();
        float angle = held != 0 ? held * BUTTON_TILT : tilt;
        if (buttons != sentButtons || shake != sentShake || angle != sentTilt) {
            sentButtons = buttons;
            sentShake = shake;
            sentTilt = angle;
            nativeSetTouchState(buttons, shake, angle);
        }
    }

    /** SDL is linked statically into libmain.so, which also loads libpng16.so as a dependency. */
    @Override
    protected String[] getLibraries() {
        return new String[] {"main"};
    }

    /** The files a desktop build keeps beside its executable, unpacked again after each update. */
    private void unpackRuntimeFiles(File runtime) throws IOException, PackageManager.NameNotFoundException {
        String installed = String.valueOf(
                getPackageManager().getPackageInfo(getPackageName(), 0).lastUpdateTime);
        File marker = new File(runtime, ".installed");
        if (marker.isFile() && installed.equals(
                new String(Files.readAllBytes(marker.toPath()), StandardCharsets.UTF_8))) {
            return;
        }
        copyAssets(getAssets(), "runtime", runtime);
        Files.write(marker.toPath(), installed.getBytes(StandardCharsets.UTF_8));
    }

    private static void copyAssets(AssetManager assets, String path, File target) throws IOException {
        String[] children = assets.list(path);
        if (children == null || children.length == 0) {
            try (InputStream in = assets.open(path); OutputStream out = new FileOutputStream(target)) {
                in.transferTo(out);
            }
            return;
        }
        if (!target.isDirectory() && !target.mkdirs()) {
            throw new IOException("Could not create " + target);
        }
        for (String child : children) {
            copyAssets(assets, path + "/" + child, new File(target, child));
        }
    }
}
