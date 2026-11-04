package com.wither.nsmbw;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Reads and changes single keys of Config.toml, leaving every other line as the game wrote it. */
final class ConfigFile {
    private static final Pattern SECTION = Pattern.compile("^\\s*\\[([^\\]]+)]\\s*$");
    private static final Pattern ENTRY = Pattern.compile("^\\s*([A-Za-z0-9_]+)\\s*=\\s*(.*?)\\s*$");

    private final File file;
    private final List<String> lines = new ArrayList<>();

    ConfigFile(File file) throws IOException {
        this.file = file;
        if (file.exists()) {
            lines.addAll(Files.readAllLines(file.toPath(), StandardCharsets.UTF_8));
        }
    }

    /** The value as written in the file, without the quotes of a string; fallback when absent. */
    String get(String section, String key, String fallback) {
        int line = find(section, key);
        if (line < 0) {
            return fallback;
        }
        Matcher entry = ENTRY.matcher(lines.get(line));
        if (!entry.matches()) {
            return fallback;
        }
        String value = entry.group(2);
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return value;
    }

    /** Sets a number or boolean, written as given. */
    void set(String section, String key, String literal) throws IOException {
        String text = key + " = " + literal;
        int line = find(section, key);
        if (line >= 0) {
            lines.set(line, text);
        } else {
            int header = findSection(section);
            if (header < 0) {
                lines.add("[" + section + "]");
                lines.add(text);
            } else {
                lines.add(header + 1, text);
            }
        }
        Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
    }

    void setString(String section, String key, String value) throws IOException {
        set(section, key, "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
    }

    /** Drops a key, so the game goes back to its default. */
    void remove(String section, String key) throws IOException {
        int line = find(section, key);
        if (line >= 0) {
            lines.remove(line);
            Files.write(file.toPath(), lines, StandardCharsets.UTF_8);
        }
    }

    private int findSection(String section) {
        for (int i = 0; i < lines.size(); i++) {
            Matcher header = SECTION.matcher(lines.get(i));
            if (header.matches() && header.group(1).trim().equals(section)) {
                return i;
            }
        }
        return -1;
    }

    private int find(String section, String key) {
        int header = findSection(section);
        if (header < 0) {
            return -1;
        }
        for (int i = header + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (SECTION.matcher(line).matches()) {
                return -1;
            }
            Matcher entry = ENTRY.matcher(line);
            if (entry.matches() && entry.group(1).equals(key)) {
                return i;
            }
        }
        return -1;
    }
}
