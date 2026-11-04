package com.wither.nsmbw;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/** Copies of the game's save folder, in the same layout as the PC launcher's SaveBackups. */
final class SaveBackups {
    private static final int KEEP = 10;

    private final File saves;
    private final File backups;

    SaveBackups(File dataDirectory) {
        // Wii title RWLE: 52574c45.
        saves = new File(dataDirectory, "NAND/title/00010000/52574c45/data");
        backups = new File(dataDirectory, "SaveBackups");
    }

    /** Newest first; the folder names sort by date. */
    List<File> list() {
        File[] folders = backups.listFiles(File::isDirectory);
        List<File> list = new ArrayList<>(folders == null ? Collections.emptyList() : Arrays.asList(folders));
        list.sort(Collections.reverseOrder());
        return list;
    }

    /** Does nothing when there is no save yet. */
    void backUp() throws IOException {
        String[] files = saves.list();
        if (files == null || files.length == 0) {
            return;
        }
        copy(saves, new File(backups, new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ROOT).format(new Date())));
        List<File> all = list();
        for (File old : all.subList(Math.min(KEEP, all.size()), all.size())) {
            DiscExtractor.delete(old);
        }
    }

    /** Backs up the current saves first, so a restore can itself be undone. */
    void restore(File backup) throws IOException {
        backUp();
        DiscExtractor.delete(saves);
        copy(backup, saves);
    }

    private static void copy(File from, File to) throws IOException {
        Path source = from.toPath();
        Path target = to.toPath();
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path copy = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(copy);
                } else {
                    Files.copy(path, copy, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }
}
