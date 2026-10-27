package ru.privatenull.pncases.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Copies the user's configuration and case files once whenever the plugin version changes, before the
 * YAML synchronization may rewrite them. Only the newest few backups are kept.
 */
public final class ConfigBackup {

    static final String VERSION_FILE = ".pncases/last-version.txt";
    static final String BACKUP_DIRECTORY = "backups";
    static final String CASES_DIRECTORY = "cases";
    static final int KEEP = 5;

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private ConfigBackup() {
    }

    /**
     * Backs up {@code documents} and {@code cases/*.yml} when {@code version} differs from the version
     * that last ran. Returns the backup directory, or empty when nothing needed to be copied.
     * The new version is recorded only after the copy succeeded.
     */
    public static Optional<Path> backupIfVersionChanged(
            Path dataFolder, Collection<String> documents, String version, Instant now) throws IOException {
        Path versionFile = dataFolder.resolve(VERSION_FILE);
        String previous = Files.isRegularFile(versionFile)
                ? Files.readString(versionFile, StandardCharsets.UTF_8).trim() : null;
        if (version.equals(previous)) return Optional.empty();

        List<Path> sources = new ArrayList<>();
        for (String document : documents) {
            Path file = dataFolder.resolve(document);
            if (Files.isRegularFile(file)) sources.add(file);
        }
        Path cases = dataFolder.resolve(CASES_DIRECTORY);
        if (Files.isDirectory(cases)) {
            try (Stream<Path> files = Files.list(cases)) {
                files.filter(file -> Files.isRegularFile(file) && file.getFileName().toString().endsWith(".yml"))
                        .sorted().forEach(sources::add);
            }
        }

        Path backup = null;
        if (!sources.isEmpty()) {
            backup = dataFolder.resolve(BACKUP_DIRECTORY).resolve(
                    STAMP.format(now) + "-" + safe(previous == null ? "unknown" : previous) + "-to-" + safe(version));
            for (Path source : sources) {
                Path target = backup.resolve(dataFolder.relativize(source).toString());
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        Files.createDirectories(versionFile.getParent());
        Files.writeString(versionFile, version, StandardCharsets.UTF_8);
        prune(dataFolder.resolve(BACKUP_DIRECTORY));
        return Optional.ofNullable(backup);
    }

    private static void prune(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) return;
        List<Path> backups;
        try (Stream<Path> entries = Files.list(directory)) {
            backups = entries.filter(Files::isDirectory)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
        for (int index = KEEP; index < backups.size(); index++) deleteTree(backups.get(index));
    }

    private static void deleteTree(Path root) throws IOException {
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private static String safe(String version) {
        return version.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
