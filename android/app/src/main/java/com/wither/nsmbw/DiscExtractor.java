package com.wither.nsmbw;

import android.content.Context;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.stream.Stream;

/** Unpacks a disc image with nodtool, which is packaged as libnodtool.so so Android lets it run. */
final class DiscExtractor {
    private DiscExtractor() {
    }

    interface Progress {
        void status(String message);
    }

    /**
     * Extracts into `target`, which must not exist yet. The picker only gives a stream, while nodtool
     * needs a file it can seek in, so the image is copied next to `target` first.
     */
    static void extract(Context context, Uri disc, File target, Progress progress)
            throws IOException, InterruptedException {
        File copy = new File(target.getParentFile(), target.getName() + ".disc");
        try {
            copyDisc(context, disc, copy, progress);
            progress.status("Extracting the disc\u2026 this can take a few minutes. Keep the app open.");
            run(context, copy, target);
        } finally {
            copy.delete();
        }
    }

    private static void copyDisc(Context context, Uri disc, File copy, Progress progress) throws IOException {
        long size;
        try (ParcelFileDescriptor file = context.getContentResolver().openFileDescriptor(disc, "r")) {
            size = file == null ? 0 : file.getStatSize();
        }
        try (InputStream in = context.getContentResolver().openInputStream(disc);
                OutputStream out = new FileOutputStream(copy)) {
            byte[] buffer = new byte[1 << 20];
            long done = 0;
            int shown = -1;
            for (int read = in.read(buffer); read >= 0; read = in.read(buffer)) {
                out.write(buffer, 0, read);
                done += read;
                int percent = size > 0 ? (int) (done * 100 / size) : 0;
                if (percent != shown) {
                    shown = percent;
                    progress.status("Copying the disc\u2026 " + percent + "%");
                }
            }
        }
    }

    private static void run(Context context, File disc, File target) throws IOException, InterruptedException {
        String tool = context.getApplicationInfo().nativeLibraryDir + "/libnodtool.so";
        Process process = new ProcessBuilder(tool, "extract", disc.getPath(), target.getPath(), "-q")
                .redirectErrorStream(true)
                .start();
        String lastLine = "";
        try (BufferedReader output = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            for (String line = output.readLine(); line != null; line = output.readLine()) {
                lastLine = line;
            }
        }
        if (process.waitFor() != 0) {
            throw new IOException(lastLine.isEmpty() ? "nodtool failed" : lastLine);
        }
    }

    static void delete(File folder) throws IOException {
        if (!folder.exists()) {
            return;
        }
        try (Stream<Path> paths = Files.walk(folder.toPath())) {
            for (Path path : (Iterable<Path>) paths.sorted(Comparator.reverseOrder())::iterator) {
                Files.delete(path);
            }
        }
    }
}
