package ru.privatenull.pncases.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigBackupTest {

    private static final List<String> DOCUMENTS = List.of("config.yml", "messages.yml");

    @TempDir
    Path dataFolder;

    private void write(String relative, String content) throws IOException {
        Path file = dataFolder.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }

    private Optional<Path> backup(String version, long epochSecond) throws IOException {
        return ConfigBackup.backupIfVersionChanged(dataFolder, DOCUMENTS, version, Instant.ofEpochSecond(epochSecond));
    }

    @Test
    void aNewVersionCopiesConfigsAndCasesAndKeepsTheirContent() throws IOException {
        write("config.yml", "database: sqlite");
        write("messages.yml", "hello: привет");
        write("cases/daily.yml", "name: daily");
        write("cases/notes.txt", "ignored");

        Path backup = backup("1.5.4", 1_000).orElseThrow();

        assertEquals("database: sqlite", Files.readString(backup.resolve("config.yml")));
        assertEquals("hello: привет", Files.readString(backup.resolve("messages.yml")));
        assertEquals("name: daily", Files.readString(backup.resolve("cases").resolve("daily.yml")));
        assertFalse(Files.exists(backup.resolve("cases").resolve("notes.txt")));
        assertTrue(backup.getFileName().toString().contains("-unknown-to-1.5.4"));
    }

    @Test
    void theSameVersionDoesNotBackUpAgain() throws IOException {
        write("config.yml", "a: 1");
        assertTrue(backup("1.5.4", 1_000).isPresent());

        assertTrue(backup("1.5.4", 2_000).isEmpty());
    }

    @Test
    void anUpgradeNamesBothVersions() throws IOException {
        write("config.yml", "a: 1");
        backup("1.5.3", 1_000);

        Path second = backup("1.5.4", 2_000).orElseThrow();

        assertTrue(second.getFileName().toString().endsWith("-1.5.3-to-1.5.4"));
    }

    @Test
    void aFreshInstallOnlyRecordsTheVersion() throws IOException {
        assertTrue(backup("1.5.4", 1_000).isEmpty());
        assertFalse(Files.exists(dataFolder.resolve("backups")));
        assertEquals("1.5.4", Files.readString(dataFolder.resolve(ConfigBackup.VERSION_FILE)));
    }

    @Test
    void onlyTheNewestBackupsAreKept() throws IOException {
        write("config.yml", "a: 1");
        for (int index = 0; index < 8; index++) {
            backup("1.5." + index, 1_000L * (index + 1));
        }

        try (Stream<Path> backups = Files.list(dataFolder.resolve("backups"))) {
            List<String> names = backups.map(path -> path.getFileName().toString()).sorted().toList();
            assertEquals(ConfigBackup.KEEP, names.size());
            assertTrue(names.get(names.size() - 1).endsWith("-1.5.6-to-1.5.7"));
        }
    }
}
