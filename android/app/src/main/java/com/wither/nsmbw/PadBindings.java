package com.wither.nsmbw;

import java.util.Arrays;
import java.util.List;

/** Gamepad actions and inputs, the same as launcher-avalonia/Core/Bindings.cs. */
final class PadBindings {
    static final String[] STYLES = {"xbox", "playstation", "nintendo"};
    static final String[] STYLE_NAMES = {"Xbox", "PlayStation", "Nintendo"};

    /** Inputs the runtime understands, by their SDL names; "" is no input. */
    static final String[] INPUTS = {
        "", "a", "b", "x", "y", "leftshoulder", "rightshoulder", "lefttrigger", "righttrigger",
        "leftstick", "rightstick", "start", "back", "guide", "touchpad", "misc1",
        "dpup", "dpdown", "dpleft", "dpright",
        "leftx-", "leftx+", "lefty-", "lefty+", "rightx-", "rightx+", "righty-", "righty+",
    };

    static final class Action {
        final String key;
        final String label;
        /** Default inputs for each style, in the order of STYLES. */
        final String[] defaults;

        Action(String key, String label, String xbox, String playstation, String nintendo) {
            this.key = key;
            this.label = label;
            defaults = new String[] {xbox, playstation, nintendo};
        }
    }

    static final Action[] ACTIONS = {
        new Action("up", "Up", "dpup", "dpup", "dpup"),
        new Action("down", "Down", "dpdown", "dpdown", "dpdown"),
        new Action("left", "Left", "dpleft", "dpleft", "dpleft"),
        new Action("right", "Right", "dpright", "dpright", "dpright"),
        new Action("jump", "Jump (2 button)", "a", "a", "a,b"),
        new Action("run", "Run / Attack (1 button)", "b,x", "b,x", "x"),
        new Action("a", "A button", "y", "y", "y"),
        new Action("shake", "Shake", "lefttrigger,righttrigger", "lefttrigger,righttrigger", "lefttrigger,righttrigger"),
        new Action("tilt_left", "Tilt left", "leftshoulder", "leftshoulder", "leftshoulder"),
        new Action("tilt_right", "Tilt right", "rightshoulder", "rightshoulder", "rightshoulder"),
        new Action("plus", "+ (Pause)", "start", "start", "start"),
        new Action("minus", "− (Minus)", "back", "back", "back"),
    };

    private PadBindings() {
    }

    static List<String> split(String text) {
        return Arrays.asList(text.isEmpty() ? new String[0] : text.split("\\s*,\\s*"));
    }

    /** The name an input has on this style of pad. */
    static String label(String input, int style) {
        boolean ps = style == 1;
        boolean nintendo = style == 2;
        switch (input) {
            case "": return "(none)";
            case "a": return ps ? "✕ Cross" : nintendo ? "B (bottom)" : "A";
            case "b": return ps ? "○ Circle" : nintendo ? "A (right)" : "B";
            case "x": return ps ? "□ Square" : nintendo ? "Y (left)" : "X";
            case "y": return ps ? "△ Triangle" : nintendo ? "X (top)" : "Y";
            case "leftshoulder": return ps ? "L1" : nintendo ? "L" : "LB";
            case "rightshoulder": return ps ? "R1" : nintendo ? "R" : "RB";
            case "lefttrigger": return ps ? "L2" : nintendo ? "ZL" : "LT";
            case "righttrigger": return ps ? "R2" : nintendo ? "ZR" : "RT";
            case "leftstick": return ps ? "L3" : "Left stick (click)";
            case "rightstick": return ps ? "R3" : "Right stick (click)";
            case "start": return ps ? "Options" : nintendo ? "+" : "Start / Menu";
            case "back": return ps ? "Create / Share" : nintendo ? "−" : "Back / View";
            case "guide": return ps ? "PS" : nintendo ? "Home" : "Guide";
            case "touchpad": return "Touchpad";
            case "misc1": return ps ? "Mic" : nintendo ? "Capture" : "Share";
            case "dpup": return "D-pad ↑";
            case "dpdown": return "D-pad ↓";
            case "dpleft": return "D-pad ←";
            case "dpright": return "D-pad →";
            case "leftx-": return "Left stick ←";
            case "leftx+": return "Left stick →";
            case "lefty-": return "Left stick ↑";
            case "lefty+": return "Left stick ↓";
            case "rightx-": return "Right stick ←";
            case "rightx+": return "Right stick →";
            case "righty-": return "Right stick ↑";
            case "righty+": return "Right stick ↓";
            default: return input;
        }
    }
}
