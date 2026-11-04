package com.wither.nsmbw;

import android.content.Context;
import android.content.SharedPreferences;

/** Launcher options the game reads when it starts; they aren't part of Config.toml. */
final class GameOptions {
    static final String SHORT_INTRO = "short_intro";
    static final String BACKUP_ON_START = "backup_on_start";
    static final String INVERT_TILT = "invert_tilt";
    /** Linear acceleration, in m/s^2, that counts as shaking the phone. */
    static final String SHAKE_ACCELERATION = "shake_acceleration";
    /** Screen degrees to remote degrees; 0 turns phone tilt off. */
    static final String TILT_FACTOR = "tilt_factor";
    static final String OVERLAY_SCALE = "overlay_scale";
    static final String OVERLAY_OPACITY = "overlay_opacity";
    static final String PAD_STYLE = "pad_style";
    static final String TILT_BUTTONS = "tilt_buttons";
    static final String HIDE_WITH_GAMEPAD = "hide_with_gamepad";
    /** Moved controls, as TouchControls.layout() writes them. */
    static final String OVERLAY_LAYOUT = "overlay_layout";

    private GameOptions() {
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("game", Context.MODE_PRIVATE);
    }

    static boolean flag(Context context, String key, boolean fallback) {
        return preferences(context).getBoolean(key, fallback);
    }

    /** Written at once: the game reads these from its own process. */
    static void setFlag(Context context, String key, boolean value) {
        preferences(context).edit().putBoolean(key, value).commit();
    }

    static String text(Context context, String key, String fallback) {
        return preferences(context).getString(key, fallback);
    }

    static void setText(Context context, String key, String value) {
        preferences(context).edit().putString(key, value).commit();
    }

    static float number(Context context, String key, float fallback) {
        return Float.parseFloat(text(context, key, String.valueOf(fallback)));
    }
}
