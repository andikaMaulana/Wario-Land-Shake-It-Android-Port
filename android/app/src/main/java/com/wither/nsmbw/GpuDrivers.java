package com.wither.nsmbw;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** Custom Vulkan drivers: AdrenoTools packages (a zip with meta.json), kept in the app's private storage. */
final class GpuDrivers {
    private static final String PREFERENCES = "gpu";
    private static final String SELECTED = "driver";

    static final class Driver {
        final String name;
        final String library;
        final File folder;

        Driver(String name, String library, File folder) {
            this.name = name;
            this.library = library;
            this.folder = folder;
        }
    }

    private GpuDrivers() {
    }

    /** libadrenotools can only load a driver from here, never from shared storage. */
    private static File root(Context context) {
        return new File(context.getFilesDir(), "gpu_drivers");
    }

    private static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
    }

    static List<Driver> installed(Context context) {
        List<Driver> drivers = new ArrayList<>();
        File[] folders = root(context).listFiles(File::isDirectory);
        if (folders != null) {
            for (File folder : folders) {
                try {
                    drivers.add(read(folder));
                } catch (IOException | JSONException e) {
                    continue;  // an unfinished or broken import
                }
            }
        }
        return drivers;
    }

    private static Driver read(File folder) throws IOException, JSONException {
        JSONObject meta = new JSONObject(new String(
                Files.readAllBytes(new File(folder, "meta.json").toPath()), StandardCharsets.UTF_8));
        String library = new File(meta.getString("libraryName")).getName();
        if (!new File(folder, library).isFile()) {
            throw new IOException("The package has no " + library);
        }
        return new Driver(meta.optString("name", library), library, folder);
    }

    /** The chosen driver, or null for the phone's own. */
    static Driver selected(Context context) {
        String folder = preferences(context).getString(SELECTED, null);
        if (folder != null) {
            for (Driver driver : installed(context)) {
                if (driver.folder.getName().equals(folder)) {
                    return driver;
                }
            }
        }
        return null;
    }

    static void select(Context context, Driver driver) {
        preferences(context).edit().putString(SELECTED, driver == null ? null : driver.folder.getName()).commit();
    }

    /** Unpacks a package and returns it; the files land flat in their own folder. */
    static Driver install(Context context, Uri zip) throws IOException, JSONException {
        File folder = new File(root(context), "driver_" + System.currentTimeMillis());
        if (!folder.mkdirs()) {
            throw new IOException("Could not create " + folder);
        }
        try (InputStream in = context.getContentResolver().openInputStream(zip);
                ZipInputStream entries = new ZipInputStream(in)) {
            for (ZipEntry entry = entries.getNextEntry(); entry != null; entry = entries.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                // Only the file name: an entry can't climb out of the folder.
                File target = new File(folder, new File(entry.getName()).getName());
                try (OutputStream out = new FileOutputStream(target)) {
                    entries.transferTo(out);
                }
            }
            return read(folder);
        } catch (IOException | JSONException e) {
            remove(folder);
            throw e;
        }
    }

    static void remove(Context context, Driver driver) {
        if (driver.folder.getName().equals(preferences(context).getString(SELECTED, null))) {
            select(context, null);
        }
        remove(driver.folder);
    }

    private static void remove(File folder) {
        File[] files = folder.listFiles();
        if (files != null) {
            for (File file : files) {
                file.delete();
            }
        }
        folder.delete();
    }
}
