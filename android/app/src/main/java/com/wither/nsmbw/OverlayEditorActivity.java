package com.wither.nsmbw;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Drag the on-screen buttons where they suit your hands. */
public class OverlayEditorActivity extends Activity {
    private TouchControls controls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        controls = new TouchControls(this, GameOptions.number(this, GameOptions.OVERLAY_SCALE, 1f), 1f,
                GameOptions.flag(this, GameOptions.TILT_BUTTONS, true),
                GameOptions.text(this, GameOptions.OVERLAY_LAYOUT, ""), () -> { }, () -> { });
        controls.setEditing(true);

        FrameLayout screen = new FrameLayout(this);
        screen.setBackgroundColor(Color.rgb(0x3A, 0x24, 0x10));
        screen.addView(controls);

        LinearLayout actions = new LinearLayout(this);
        actions.addView(action("SAVE", v -> {
            GameOptions.setText(this, GameOptions.OVERLAY_LAYOUT, controls.layout());
            finish();
        }));
        actions.addView(action("RESET", v -> controls.resetLayout()));
        actions.addView(action("CANCEL", v -> finish()));
        FrameLayout.LayoutParams place = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        screen.addView(actions, place);
        setContentView(screen);
    }

    private TextView action(String label, View.OnClickListener click) {
        float density = getResources().getDisplayMetrics().density;
        TextView view = new TextView(this);
        view.setText(label);
        view.setTextSize(14);
        view.setTextColor(Color.rgb(0x2E, 0x1B, 0x0C));
        view.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
        view.setGravity(Gravity.CENTER);
        view.setPadding((int) (20 * density), (int) (10 * density), (int) (20 * density), (int) (10 * density));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.rgb(0xF0, 0xD9, 0xA4));
        background.setStroke((int) (2 * density), Color.rgb(0x2E, 0x1B, 0x0C));
        background.setCornerRadius(6 * density);
        view.setBackground(background);
        view.setOnClickListener(click);
        LinearLayout.LayoutParams margins = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        margins.setMargins((int) (6 * density), 0, (int) (6 * density), 0);
        view.setLayoutParams(margins);
        return view;
    }
}
