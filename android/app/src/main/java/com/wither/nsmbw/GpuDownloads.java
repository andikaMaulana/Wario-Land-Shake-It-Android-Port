package com.wither.nsmbw;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Finds and fetches driver packages from the GitHub releases of the projects that publish them. */
final class GpuDownloads {
    /** Repositories that publish AdrenoTools packages, as {shown name, owner/repository}. */
    static final String[][] SOURCES = {
        {"AdrenoToolsDrivers (K11MCH1)", "K11MCH1/AdrenoToolsDrivers"},
        {"freedreno_turnip-CI (Weab-chan)", "Weab-chan/freedreno_turnip-CI"},
    };

    private static final int RELEASES = 15;
    private static final long MAX_SIZE = 64L * 1024 * 1024;
    private static final String DOWNLOAD_PREFIX = "https://github.com/";

    static final class Package {
        final String label;
        final String url;
        final long size;

        Package(String label, String url, long size) {
            this.label = label;
            this.url = url;
            this.size = size;
        }
    }

    interface Progress {
        void percent(int value);
    }

    private GpuDownloads() {
    }

    private static HttpURLConnection open(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(30000);
        connection.setRequestProperty("User-Agent", "WarioRecomp");
        int status = connection.getResponseCode();
        if (status != HttpURLConnection.HTTP_OK) {
            connection.disconnect();
            throw new IOException("GitHub answered " + status);
        }
        return connection;
    }

    /** The zip files of the latest releases, newest first. */
    static List<Package> list(String repository) throws IOException, JSONException {
        HttpURLConnection connection = open(
                "https://api.github.com/repos/" + repository + "/releases?per_page=" + RELEASES);
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        try (InputStream in = connection.getInputStream()) {
            in.transferTo(body);
        } finally {
            connection.disconnect();
        }
        List<Package> packages = new ArrayList<>();
        JSONArray releases = new JSONArray(body.toString(StandardCharsets.UTF_8.name()));
        for (int i = 0; i < releases.length(); i++) {
            JSONObject release = releases.getJSONObject(i);
            String date = release.optString("published_at", "");
            date = date.length() >= 10 ? date.substring(0, 10) : date;
            JSONArray assets = release.getJSONArray("assets");
            for (int j = 0; j < assets.length(); j++) {
                JSONObject asset = assets.getJSONObject(j);
                String name = asset.getString("name");
                String url = asset.getString("browser_download_url");
                long size = asset.getLong("size");
                if (name.endsWith(".zip") && url.startsWith(DOWNLOAD_PREFIX) && size <= MAX_SIZE) {
                    packages.add(new Package(name + "  (" + date + ")", url, size));
                }
            }
        }
        return packages;
    }

    /** Saves a package in the app's cache; the caller deletes the file. */
    static File download(Context context, Package item, Progress progress) throws IOException {
        if (!item.url.startsWith(DOWNLOAD_PREFIX)) {
            throw new IOException("Not a GitHub download");
        }
        File target = File.createTempFile("driver", ".zip", context.getCacheDir());
        HttpURLConnection connection = open(item.url);
        try (InputStream in = connection.getInputStream(); OutputStream out = new FileOutputStream(target)) {
            byte[] buffer = new byte[1 << 16];
            long done = 0;
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                done += read;
                if (done > MAX_SIZE) {
                    throw new IOException("The package is too large");
                }
                out.write(buffer, 0, read);
                if (item.size > 0) {
                    progress.percent((int) (done * 100 / item.size));
                }
            }
        } catch (IOException e) {
            target.delete();
            throw e;
        } finally {
            connection.disconnect();
        }
        return target;
    }
}
