package com.wither.nsmbw;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The first screen: where the game is, how it looks, and the way into it. */
public class LauncherActivity extends Activity {
    private static final String TAG = "WarioRecomp";
    private static final int PICK_FOLDER = 1;
    private static final int PICK_DRIVER = 2;
    private static final int PICK_DISC = 3;
    /** A full Wii disc twice, as a temporary copy and unpacked. */
    private static final long DISC_SPACE = 8L * 1024 * 1024 * 1024;
    /** Where an imported disc lives, relative to Config.toml. */
    private static final String IMPORTED_FOLDER = "GameData";
    /** The unmodified RWLE01 main.dol (projects/wario/recomp.yml). */
    private static final String MAIN_DOL_SHA256 =
            "053be60dde84c8042f45ed2f4ba80c17efb2d20fedf95a414c2c7e37d336912a";

    private static final int INK = Color.rgb(0x2E, 0x1B, 0x0C);
    private static final int RUST = Color.rgb(0x8A, 0x45, 0x18);
    private static final int GOLD = Color.rgb(0xD9, 0xA0, 0x35);
    private static final int GOLD_LIGHT = Color.rgb(0xF7, 0xDC, 0x8A);
    private static final int PARCHMENT = Color.rgb(0xF0, 0xD9, 0xA4);
    private static final int PARCHMENT_DARK = Color.rgb(0xC9, 0x96, 0x4A);

    private ConfigFile config;
    private TextView folderPath;
    private TextView folderState;
    private TextView driverName;
    private LinearLayout padRows;
    private final List<TextView> tabButtons = new ArrayList<>();
    private final List<View> tabPages = new ArrayList<>();
    private TextView play;
    private TextView resume;
    private boolean pickAfterPermission;
    private final ExecutorService background = Executors.newSingleThreadExecutor();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        File file = new File(getExternalFilesDir(null), "Config.toml");
        try {
            config = new ConfigFile(file);
            if (config.get("paths", "dvd_root", null) == null) {
                // Relative to Config.toml: the DATA folder inside the app's own storage.
                config.setString("paths", "dvd_root", "DATA");
            }
        } catch (IOException e) {
            Log.e(TAG, "Could not open " + file, e);
            Toast.makeText(this, "Could not open Config.toml", Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        setContentView(buildScreen());
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (config == null) {
            return;
        }
        if (pickAfterPermission && Environment.isExternalStorageManager()) {
            pickAfterPermission = false;
            openFolderPicker();
        }
        refresh();
    }

    private View buildScreen() {
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.VERTICAL);
        FrameLayout pages = new FrameLayout(this);

        LinearLayout gamePage = tab(tabs, pages, "Game");
        LinearLayout graphicsPage = tab(tabs, pages, "Graphics");
        LinearLayout audioPage = tab(tabs, pages, "Audio");
        LinearLayout controlsPage = tab(tabs, pages, "Controls");
        LinearLayout savesPage = tab(tabs, pages, "Saves");
        LinearLayout driverPage = tab(tabs, pages, "GPU Driver");

        LinearLayout folder = section(gamePage, "Game folder");
        folderPath = text("", 13, INK, false);
        folder.addView(folderPath);
        folderState = text("", 13, RUST, true);
        folderState.setPadding(0, dp(2), 0, dp(8));
        folder.addView(folderState);
        LinearLayout folderActions = new LinearLayout(this);
        folderActions.addView(button("CHOOSE FOLDER", false, v -> chooseFolder()), weight());
        folderActions.addView(button("USE APP STORAGE", false, v -> setFolder("DATA")), weight());
        folder.addView(folderActions);
        LinearLayout discActions = new LinearLayout(this);
        discActions.setPadding(0, dp(8), 0, 0);
        discActions.addView(button("IMPORT DISC IMAGE (ISO, RVZ, WBFS...)", false, v -> pickDisc()), weight());
        folder.addView(discActions);


        LinearLayout game = section(gamePage, "Options");
        flag(game, "Short intro (skips the opening video)", GameOptions.SHORT_INTRO, false);

        LinearLayout video = section(graphicsPage, "Display");
        choice(video, "Resolution", "video", "resolution_multiplier", "2.25",
                new String[] {"480p (fastest)", "720p", "1080p", "1440p", "Screen size"},
                new String[] {"1.0", "1.5", "2.25", "3.0", "0.0"});
        // Without the key the game fills the screen.
        choice(video, "Aspect ratio", "video", "aspect_mode", "2",
                new String[] {"4:3", "16:9", "Fill the screen"}, new String[] {"0", "1", "2"});
        toggle(video, "True 16:9 in levels (experimental)", "video", "full_widescreen", "false");
        toggle(video, "V-Sync", "video", "vsync", "true");
        choice(video, "Frame interpolation", "video", "frame_interpolation_fps", "0",
                new String[] {"Off", "120 fps"}, new String[] {"0", "120"});
        toggle(video, "Show FPS", "video", "show_fps", "false");

        LinearLayout renderer = section(graphicsPage, "Renderer");
        toggle(renderer, "Don't wait for new shaders", "video", "skip_unready_pipelines", "true");
        choice(renderer, "GPU compatibility (for broken graphics)", "video", "gpu_compat", "0",
                new String[] {"Automatic", "On", "Off"}, new String[] {"0", "1", "2"});
        LinearLayout rendererActions = new LinearLayout(this);
        rendererActions.addView(button("CLEAR SHADER CACHE", false, v -> clearShaderCache()), weight());
        renderer.addView(rendererActions);


        LinearLayout audio = section(audioPage, "Sound");
        choice(audio, "Volume", "audio", "volume", "1.0",
                new String[] {"0%", "25%", "50%", "75%", "100%"},
                new String[] {"0.0", "0.25", "0.5", "0.75", "1.0"});
        toggle(audio, "Mute", "audio", "muted", "false");

        LinearLayout touch = section(controlsPage, "On-screen buttons");
        option(touch, "Button size", GameOptions.OVERLAY_SCALE, "1.0",
                new String[] {"Small", "Normal", "Large"}, new String[] {"0.85", "1.0", "1.15"});
        option(touch, "Button opacity", GameOptions.OVERLAY_OPACITY, "0.8",
                new String[] {"60%", "80%", "100%"}, new String[] {"0.6", "0.8", "1.0"});

        flag(touch, "Tilt buttons", GameOptions.TILT_BUTTONS, true);
        LinearLayout touchActions = new LinearLayout(this);
        touchActions.addView(button("EDIT BUTTON LAYOUT", false,
                v -> startActivity(new Intent(this, OverlayEditorActivity.class))), weight());
        touch.addView(touchActions);

        LinearLayout motion = section(controlsPage, "Phone motion");
        option(motion, "Phone tilt", GameOptions.TILT_FACTOR, "1.0",
                new String[] {"Off", "Normal", "1.5x", "2x"}, new String[] {"0.0", "1.0", "1.5", "2.0"});
        flag(motion, "Invert phone tilt", GameOptions.INVERT_TILT, false);
        option(motion, "Shake sensitivity", GameOptions.SHAKE_ACCELERATION, "15.0",
                new String[] {"Low", "Normal", "High"}, new String[] {"22.0", "15.0", "10.0"});


        LinearLayout pad = section(controlsPage, "Gamepad");
        flag(pad, "Hide on-screen buttons with a gamepad", GameOptions.HIDE_WITH_GAMEPAD, true);
        toggle(pad, "Rumble", "controller", "rumble", "true");
        pad.addView(row("Gamepad type to edit", new Row() {
            @Override
            public String value() {
                return PadBindings.STYLE_NAMES[padStyle()];
            }

            @Override
            public void click(Runnable changed) {
                new AlertDialog.Builder(LauncherActivity.this)
                        .setSingleChoiceItems(PadBindings.STYLE_NAMES, padStyle(), (dialog, which) -> {
                            GameOptions.setText(LauncherActivity.this, GameOptions.PAD_STYLE, PadBindings.STYLES[which]);
                            changed.run();
                            fillPadRows();
                            dialog.dismiss();
                        })
                        .show();
            }
        }));
        padRows = new LinearLayout(this);
        padRows.setOrientation(LinearLayout.VERTICAL);
        pad.addView(padRows);
        fillPadRows();
        LinearLayout padActions = new LinearLayout(this);
        padActions.addView(button("RESET GAMEPAD", false, v -> resetPad()), weight());
        pad.addView(padActions);

        LinearLayout saves = section(savesPage, "Save backups");
        flag(saves, "Back up saves before every start", GameOptions.BACKUP_ON_START, true);
        LinearLayout saveActions = new LinearLayout(this);
        saveActions.addView(button("BACK UP NOW", false, v -> backUpNow()), weight());
        saveActions.addView(button("RESTORE", false, v -> chooseBackup()), weight());
        saves.addView(saveActions);


        LinearLayout driver = section(driverPage, "Vulkan driver (Adreno)");
        LinearLayout driverRow = new LinearLayout(this);
        driverRow.setGravity(Gravity.CENTER_VERTICAL);
        driverRow.setMinimumHeight(dp(42));
        driverRow.addView(text("Vulkan driver", 15, INK, false),
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        driverName = text("", 15, RUST, true);
        driverRow.addView(driverName);
        driverRow.setOnClickListener(v -> chooseDriver());
        driver.addView(driverRow);
        LinearLayout driverActions = new LinearLayout(this);
        driverActions.addView(button("DOWNLOAD", false, v -> chooseDownloadSource()), weight());
        driverActions.addView(button("IMPORT .ZIP", false, v -> importDriver()), weight());
        driverActions.addView(button("REMOVE", false, v -> removeDriver()), weight());
        driver.addView(driverActions);

        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setPadding(dp(10), dp(10), dp(10), dp(10));
        rail.setBackgroundColor(INK);
        TextView title = text("WarioRecomp", 19, GOLD_LIGHT, true);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(8));
        rail.addView(title);
        ScrollView tabScroll = new ScrollView(this);
        tabScroll.addView(tabs);
        rail.addView(tabScroll, new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        resume = button("RESUME", false, v -> startGame());
        rail.addView(resume, railButton(dp(38)));
        play = button("PLAY", true, v -> restartGame());
        rail.addView(play, railButton(dp(50)));

        LinearLayout screen = new LinearLayout(this);
        screen.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {PARCHMENT, PARCHMENT_DARK}));
        screen.addView(rail, new LinearLayout.LayoutParams(dp(176), LinearLayout.LayoutParams.MATCH_PARENT));
        screen.addView(pages, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        selectTab(0);
        return screen;
    }

    /** Adds a button to the side rail and its page, which scrolls on its own. */
    private LinearLayout tab(LinearLayout tabs, FrameLayout pages, String name) {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(18), dp(2), dp(18), dp(16));
        ScrollView scroll = new ScrollView(this);
        scroll.addView(page);
        pages.addView(scroll);
        tabPages.add(scroll);

        int index = tabButtons.size();
        TextView button = text(name, 15, PARCHMENT, true);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(8), 0);
        button.setOnClickListener(v -> selectTab(index));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(38));
        params.setMargins(0, 0, 0, dp(3));
        tabs.addView(button, params);
        tabButtons.add(button);
        return page;
    }

    private void selectTab(int index) {
        for (int i = 0; i < tabButtons.size(); i++) {
            boolean selected = i == index;
            tabPages.get(i).setVisibility(selected ? View.VISIBLE : View.GONE);
            TextView button = tabButtons.get(i);
            button.setTextColor(selected ? INK : PARCHMENT);
            GradientDrawable background = new GradientDrawable();
            background.setColor(selected ? GOLD : Color.TRANSPARENT);
            background.setCornerRadius(dp(6));
            button.setBackground(background);
        }
    }

    private LinearLayout.LayoutParams railButton(int height) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, height);
        params.setMargins(0, dp(6), 0, 0);
        return params;
    }

    // --- Game folder ---

    private File gameFolder() {
        String path = config.get("paths", "dvd_root", "DATA");
        File folder = new File(path);
        return folder.isAbsolute() ? folder : new File(getExternalFilesDir(null), path);
    }

    private void refresh() {
        File folder = gameFolder();
        folderPath.setText(folder.getPath());
        String problem = folderProblem(folder);
        folderState.setText(problem == null ? "✔ Wario Land: Shake It! (RWLE01)" : "✖ " + problem);
        play.setEnabled(problem == null);
        play.setAlpha(problem == null ? 1f : 0.5f);
        GpuDrivers.Driver driver = GpuDrivers.selected(this);
        driverName.setText(driver == null ? "System default" : driver.name);
        boolean running = gameProcess() != 0;
        resume.setVisibility(running ? View.VISIBLE : View.GONE);
        play.setText(running ? "RESTART" : "PLAY");
    }

    /** Why this folder can't be played, or null when it holds the right game. */
    private static String folderProblem(File folder) {
        File dol = new File(folder, "sys/main.dol");
        if (!dol.isFile()) {
            return "No extracted game here (sys/main.dol is missing)";
        }
        try (InputStream in = new DigestInputStream(Files.newInputStream(dol.toPath()),
                MessageDigest.getInstance("SHA-256"))) {
            byte[] buffer = new byte[1 << 16];
            while (in.read(buffer) >= 0) {
                continue;
            }
            StringBuilder hash = new StringBuilder();
            for (byte b : ((DigestInputStream) in).getMessageDigest().digest()) {
                hash.append(String.format("%02x", b));
            }
            return MAIN_DOL_SHA256.contentEquals(hash) ? null : "This is not unmodified RWLE01 (USA)";
        } catch (IOException | NoSuchAlgorithmException e) {
            return "Can't read this folder: " + e.getMessage();
        }
    }

    private void chooseFolder() {
        if (Environment.isExternalStorageManager()) {
            openFolderPicker();
            return;
        }
        // The game reads its files by path, which outside the app's storage takes this permission.
        new AlertDialog.Builder(this)
                .setTitle("Allow file access")
                .setMessage("To read a game folder outside the app's storage, turn on \"Allow access to manage all files\" on the next screen.")
                .setPositiveButton("Open settings", (d, w) -> {
                    pickAfterPermission = true;
                    startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:" + getPackageName())));
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void openFolderPicker() {
        startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE), PICK_FOLDER);
    }

    @Override
    protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (request == PICK_DRIVER) {
            installDriver(data.getData());
            return;
        }
        if (request == PICK_DISC) {
            importDisc(data.getData());
            return;
        }
        File picked = pathOfTree(data.getData());
        // Accept the folder that holds DATA as well as DATA itself.
        File nested = new File(picked, "DATA");
        setFolder((new File(nested, "sys/main.dol").isFile() ? nested : picked).getPath());
    }

    /** A tree id reads "primary:Games/Wario" for the phone's storage, "ABCD-1234:Games" for a card. */
    private static File pathOfTree(Uri tree) {
        String id = DocumentsContract.getTreeDocumentId(tree);
        int colon = id.indexOf(':');
        String volume = colon < 0 ? id : id.substring(0, colon);
        String path = colon < 0 ? "" : id.substring(colon + 1);
        File root = volume.equals("primary")
                ? Environment.getExternalStorageDirectory() : new File("/storage", volume);
        return new File(root, path);
    }

    private void setFolder(String path) {
        save(() -> config.setString("paths", "dvd_root", path));
        refresh();
    }

    // --- Disc images ---

    private void pickDisc() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("*/*");
        startActivityForResult(pick, PICK_DISC);
    }

    private void importDisc(Uri disc) {
        File storage = getExternalFilesDir(null);
        if (storage.getUsableSpace() < DISC_SPACE) {
            Toast.makeText(this, "Importing a disc needs about 8 GB of free space", Toast.LENGTH_LONG).show();
            return;
        }
        AlertDialog dialog = busy("Copying the disc\u2026");
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        background.execute(() -> {
            File temporary = new File(storage, IMPORTED_FOLDER + ".tmp");
            File target = new File(storage, IMPORTED_FOLDER);
            String problem;
            try {
                DiscExtractor.delete(temporary);
                DiscExtractor.extract(this, disc, temporary,
                        message -> runOnUiThread(() -> dialog.setMessage(message)));
                problem = folderProblem(temporary);
                if (problem == null) {
                    DiscExtractor.delete(target);
                    Files.move(temporary.toPath(), target.toPath());
                }
            } catch (IOException | InterruptedException e) {
                Log.e(TAG, "Could not import the disc", e);
                problem = e.getMessage();
            }
            try {
                DiscExtractor.delete(temporary);
            } catch (IOException e) {
                Log.e(TAG, "Could not clean up after the import", e);
            }
            String result = problem;
            runOnUiThread(() -> {
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
                dialog.dismiss();
                if (result == null) {
                    setFolder(IMPORTED_FOLDER);
                } else {
                    new AlertDialog.Builder(this)
                            .setTitle("Could not import this disc")
                            .setMessage(result)
                            .setPositiveButton("OK", null)
                            .show();
                }
            });
        });
    }

    // --- GPU driver ---

    private void chooseDriver() {
        List<GpuDrivers.Driver> drivers = GpuDrivers.installed(this);
        String[] names = new String[drivers.size() + 1];
        names[0] = "System default";
        for (int i = 0; i < drivers.size(); i++) {
            names[i + 1] = drivers.get(i).name;
        }
        new AlertDialog.Builder(this)
                .setItems(names, (dialog, which) -> {
                    GpuDrivers.select(this, which == 0 ? null : drivers.get(which - 1));
                    refresh();
                })
                .show();
    }

    private void importDriver() {
        Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        pick.addCategory(Intent.CATEGORY_OPENABLE);
        pick.setType("application/zip");
        startActivityForResult(pick, PICK_DRIVER);
    }

    private void installDriver(Uri zip) {
        try {
            GpuDrivers.Driver driver = GpuDrivers.install(this, zip);
            GpuDrivers.select(this, driver);
            refresh();
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Could not import the driver", e);
            Toast.makeText(this, "This is not a driver package (no valid meta.json)", Toast.LENGTH_LONG).show();
        }
    }

    private void chooseDownloadSource() {
        String[] names = new String[GpuDownloads.SOURCES.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = GpuDownloads.SOURCES[i][0];
        }
        new AlertDialog.Builder(this)
                .setTitle("Download from GitHub")
                .setItems(names, (dialog, which) -> listPackages(GpuDownloads.SOURCES[which][1]))
                .show();
    }

    private AlertDialog busy(String message) {
        return new AlertDialog.Builder(this).setMessage(message).setCancelable(false).show();
    }

    private void downloadFailed(AlertDialog dialog, Exception e) {
        Log.e(TAG, "Driver download failed", e);
        dialog.dismiss();
        Toast.makeText(this, "Download failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    private void listPackages(String repository) {
        AlertDialog dialog = busy("Looking for drivers\u2026");
        background.execute(() -> {
            try {
                List<GpuDownloads.Package> packages = GpuDownloads.list(repository);
                runOnUiThread(() -> {
                    dialog.dismiss();
                    showPackages(packages);
                });
            } catch (IOException | JSONException e) {
                runOnUiThread(() -> downloadFailed(dialog, e));
            }
        });
    }

    private void showPackages(List<GpuDownloads.Package> packages) {
        if (packages.isEmpty()) {
            Toast.makeText(this, "No driver packages found", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[packages.size()];
        for (int i = 0; i < names.length; i++) {
            names[i] = packages.get(i).label;
        }
        new AlertDialog.Builder(this)
                .setTitle("Choose a driver")
                .setItems(names, (dialog, which) -> confirmDownload(packages.get(which)))
                .show();
    }

    /** A driver is code from its publisher that will run inside the game, so the player agrees first. */
    private void confirmDownload(GpuDownloads.Package item) {
        new AlertDialog.Builder(this)
                .setTitle("Install this driver?")
                .setMessage(item.label + "\n\nDrivers are third-party code that runs inside the game. "
                        + "If the game stops starting, choose \"System default\" here.")
                .setPositiveButton("Download", (dialog, which) -> downloadPackage(item))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void downloadPackage(GpuDownloads.Package item) {
        AlertDialog dialog = busy("Downloading\u2026");
        background.execute(() -> {
            try {
                File zip = GpuDownloads.download(this, item, percent -> runOnUiThread(
                        () -> dialog.setMessage("Downloading\u2026 " + percent + "%")));
                runOnUiThread(() -> {
                    dialog.dismiss();
                    installDriver(Uri.fromFile(zip));
                    zip.delete();
                });
            } catch (IOException e) {
                runOnUiThread(() -> downloadFailed(dialog, e));
            }
        });
    }

    private void removeDriver() {
        GpuDrivers.Driver driver = GpuDrivers.selected(this);
        if (driver != null) {
            GpuDrivers.remove(this, driver);
            refresh();
        }
    }

    // --- Graphics ---

    private interface Change {
        void run() throws IOException;
    }

    private void save(Change change) {
        try {
            change.run();
        } catch (IOException e) {
            Log.e(TAG, "Could not save the settings", e);
            Toast.makeText(this, "Could not save the settings", Toast.LENGTH_LONG).show();
        }
    }

    private void choice(LinearLayout parent, String label, String section, String key, String fallback,
            String[] names, String[] values) {
        parent.addView(row(label, pick(names, values, section, key, fallback)));
    }

    private void toggle(LinearLayout parent, String label, String section, String key, String fallback) {
        parent.addView(row(label, new Row() {
            @Override
            public String value() {
                return config.get(section, key, fallback).equals("true") ? "On" : "Off";
            }

            @Override
            public void click(Runnable changed) {
                boolean on = config.get(section, key, fallback).equals("true");
                save(() -> config.set(section, key, on ? "false" : "true"));
                changed.run();
            }
        }));
    }

    /** A setting kept by the launcher rather than in Config.toml. */
    private void option(LinearLayout parent, String label, String key, String fallback, String[] names, String[] values) {
        parent.addView(row(label, new Row() {
            private int index() {
                String current = GameOptions.text(LauncherActivity.this, key, fallback);
                for (int i = 0; i < values.length; i++) {
                    if (sameNumber(values[i], current)) {
                        return i;
                    }
                }
                return -1;
            }

            @Override
            public String value() {
                int index = index();
                return index >= 0 ? names[index] : "Custom";
            }

            @Override
            public void click(Runnable changed) {
                new AlertDialog.Builder(LauncherActivity.this)
                        .setSingleChoiceItems(names, index(), (dialog, which) -> {
                            GameOptions.setText(LauncherActivity.this, key, values[which]);
                            changed.run();
                            dialog.dismiss();
                        })
                        .show();
            }
        }));
    }

    private void flag(LinearLayout parent, String label, String key, boolean fallback) {
        parent.addView(row(label, new Row() {
            @Override
            public String value() {
                return GameOptions.flag(LauncherActivity.this, key, fallback) ? "On" : "Off";
            }

            @Override
            public void click(Runnable changed) {
                GameOptions.setFlag(LauncherActivity.this, key, !GameOptions.flag(LauncherActivity.this, key, fallback));
                changed.run();
            }
        }));
    }

    private interface Row {
        String value();

        void click(Runnable changed);
    }

    private Row pick(String[] names, String[] values, String section, String key, String fallback) {
        return new Row() {
            private int index() {
                String current = config.get(section, key, fallback);
                for (int i = 0; i < values.length; i++) {
                    if (sameNumber(values[i], current)) {
                        return i;
                    }
                }
                return -1;
            }

            @Override
            public String value() {
                int index = index();
                return index >= 0 ? names[index] : "Custom";
            }

            @Override
            public void click(Runnable changed) {
                new AlertDialog.Builder(LauncherActivity.this)
                        .setSingleChoiceItems(names, index(), (dialog, which) -> {
                            save(() -> {
                                config.set(section, key, values[which]);
                                if (key.equals("aspect_mode")) {
                                    // The game also has to draw for a wide screen (same as the PC launcher).
                                    config.set("video", "widescreen", which != 0 ? "true" : "false");
                                }
                            });
                            changed.run();
                            dialog.dismiss();
                        })
                        .show();
            }
        };
    }

    /** The game may rewrite 2.25 as 2.250000, so numbers compare by value. */
    private static boolean sameNumber(String a, String b) {
        try {
            return Double.parseDouble(a) == Double.parseDouble(b);
        } catch (NumberFormatException e) {
            return a.equals(b);
        }
    }

    // --- Starting the game ---

    /** The pid of the game's process (android:process=":game"), or 0 when it isn't running. */
    private int gameProcess() {
        ActivityManager manager = getSystemService(ActivityManager.class);
        for (ActivityManager.RunningAppProcessInfo info : manager.getRunningAppProcesses()) {
            if (info.processName.endsWith(":game")) {
                return info.pid;
            }
        }
        return 0;
    }

    private void startGame() {
        startActivity(new Intent(this, GameActivity.class));
    }

    private void backUpBeforeStart() {
        if (GameOptions.flag(this, GameOptions.BACKUP_ON_START, true)) {
            save(backups()::backUp);
        }
    }

    /** Settings are read when the game starts, so a running game has to start over to see them. */
    private void restartGame() {
        int running = gameProcess();
        stopGame();
        backUpBeforeStart();
        if (running == 0) {
            startGame();
        } else {
            new Handler(Looper.getMainLooper()).postDelayed(this::startGame, 400);
        }
    }

    private void stopGame() {
        int running = gameProcess();
        if (running != 0) {
            Process.killProcess(running);
        }
    }

    // --- Saves and cache ---

    private SaveBackups backups() {
        return new SaveBackups(getExternalFilesDir(null));
    }

    private void backUpNow() {
        save(backups()::backUp);
        Toast.makeText(this, "Saves backed up", Toast.LENGTH_SHORT).show();
    }

    private void chooseBackup() {
        List<File> list = backups().list();
        if (list.isEmpty()) {
            Toast.makeText(this, "No backups yet", Toast.LENGTH_LONG).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) {
            String name = list.get(i).getName();
            names[i] = name.length() == 15
                    ? name.substring(0, 4) + "-" + name.substring(4, 6) + "-" + name.substring(6, 8) + "  "
                            + name.substring(9, 11) + ":" + name.substring(11, 13) + ":" + name.substring(13)
                    : name;
        }
        new AlertDialog.Builder(this)
                .setTitle("Restore saves from")
                .setItems(names, (dialog, which) -> confirmRestore(list.get(which)))
                .show();
    }

    private void confirmRestore(File backup) {
        new AlertDialog.Builder(this)
                .setTitle("Restore this backup?")
                .setMessage("The game will close. Your current saves are backed up first.")
                .setPositiveButton("Restore", (dialog, which) -> {
                    stopGame();
                    save(() -> backups().restore(backup));
                    refresh();
                    Toast.makeText(this, "Saves restored", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearShaderCache() {
        new AlertDialog.Builder(this)
                .setTitle("Clear the shader cache?")
                .setMessage("The game will close, and the next session will stutter a bit while it is rebuilt.")
                .setPositiveButton("Clear", (dialog, which) -> {
                    stopGame();
                    save(() -> DiscExtractor.delete(new File(getExternalFilesDir(null), "Cache")));
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // --- Gamepad ---

    private int padStyle() {
        String style = GameOptions.text(this, GameOptions.PAD_STYLE, PadBindings.STYLES[0]);
        return Math.max(0, Arrays.asList(PadBindings.STYLES).indexOf(style));
    }

    private String padValue(PadBindings.Action action) {
        int style = padStyle();
        return config.get("gamepad", PadBindings.STYLES[style] + "_" + action.key, action.defaults[style]);
    }

    private void fillPadRows() {
        padRows.removeAllViews();
        for (PadBindings.Action action : PadBindings.ACTIONS) {
            padRows.addView(row(action.label, new Row() {
                @Override
                public String value() {
                    StringBuilder shown = new StringBuilder();
                    for (String input : PadBindings.split(padValue(action))) {
                        shown.append(shown.length() == 0 ? "" : " / ").append(PadBindings.label(input, padStyle()));
                    }
                    return shown.length() == 0 ? PadBindings.label("", padStyle()) : shown.toString();
                }

                @Override
                public void click(Runnable changed) {
                    choosePadInput(action, changed);
                }
            }));
        }
    }

    private void choosePadInput(PadBindings.Action action, Runnable changed) {
        int style = padStyle();
        String[] names = new String[PadBindings.INPUTS.length];
        for (int i = 0; i < names.length; i++) {
            names[i] = PadBindings.label(PadBindings.INPUTS[i], style);
        }
        new AlertDialog.Builder(this)
                .setTitle(action.label)
                .setItems(names, (dialog, which) -> {
                    String input = PadBindings.INPUTS[which];
                    String key = PadBindings.STYLES[style] + "_" + action.key;
                    save(() -> {
                        if (input.equals(action.defaults[style])) {
                            config.remove("gamepad", key);
                        } else {
                            config.setString("gamepad", key, input);
                        }
                    });
                    changed.run();
                    warnIfShared(action, input);
                })
                .show();
    }

    private void warnIfShared(PadBindings.Action changed, String input) {
        for (PadBindings.Action other : PadBindings.ACTIONS) {
            if (other != changed && !input.isEmpty() && PadBindings.split(padValue(other)).contains(input)) {
                Toast.makeText(this, "Also assigned to: " + other.label, Toast.LENGTH_LONG).show();
                return;
            }
        }
    }

    private void resetPad() {
        String style = PadBindings.STYLES[padStyle()];
        save(() -> {
            for (PadBindings.Action action : PadBindings.ACTIONS) {
                config.remove("gamepad", style + "_" + action.key);
            }
        });
        fillPadRows();
    }

    // --- Views ---

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout.LayoutParams weight() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(46), 1f);
        params.setMargins(dp(4), 0, dp(4), 0);
        return params;
    }

    private TextView text(String value, int sp, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        view.setTextColor(color);
        view.setTypeface(Typeface.create(Typeface.SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return view;
    }

    private LinearLayout section(LinearLayout page, String title) {
        TextView heading = text(title.toUpperCase(), 14, RUST, true);
        heading.setPadding(dp(4), dp(14), 0, dp(4));
        page.addView(heading);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(10), dp(14), dp(10));
        GradientDrawable background = new GradientDrawable();
        background.setColor(Color.argb(0x90, 0xFB, 0xEE, 0xCC));
        background.setStroke(dp(2), INK);
        background.setCornerRadius(dp(6));
        card.setBackground(background);
        page.addView(card);
        return card;
    }

    private LinearLayout row(String label, Row row) {
        LinearLayout line = new LinearLayout(this);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setMinimumHeight(dp(42));
        TextView name = text(label, 15, INK, false);
        line.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView value = text(row.value(), 15, RUST, true);
        line.addView(value);
        line.setOnClickListener(v -> row.click(() -> value.setText(row.value())));
        return line;
    }

    private TextView button(String label, boolean primary, View.OnClickListener click) {
        TextView view = text(label, primary ? 17 : 13, INK, true);
        view.setGravity(Gravity.CENTER);
        GradientDrawable background = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                primary ? new int[] {GOLD_LIGHT, GOLD} : new int[] {PARCHMENT, PARCHMENT_DARK});
        background.setStroke(dp(2), INK);
        background.setCornerRadius(dp(6));
        view.setBackground(background);
        view.setOnClickListener(click);
        return view;
    }
}
