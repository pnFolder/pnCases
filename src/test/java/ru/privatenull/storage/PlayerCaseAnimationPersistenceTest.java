package ru.privatenull.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.pnlibrary.database.JdbcSettings;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PlayerCaseAnimationPersistenceTest {

    @TempDir
    Path directory;

    @Test
    void storesIndependentAnimationForEveryCaseId() throws Exception {
        UUID playerId = UUID.randomUUID();
        Path file = directory.resolve("animations.db");

        try (JdbcCasesDatabase database = open(file)) {
            database.setAnimation(playerId, "test", AnimationType.DRAGON);
            database.setAnimation(playerId, "donate", AnimationType.PORTAL);

            assertEquals(AnimationType.DRAGON, database.getAnimation(playerId, "test"));
            assertEquals(AnimationType.PORTAL, database.getAnimation(playerId, "DONATE"));
        }

        try (JdbcCasesDatabase database = open(file)) {
            assertEquals(AnimationType.DRAGON, database.getAnimation(playerId, "test"));
            assertEquals(AnimationType.PORTAL, database.getAnimation(playerId, "donate"));
        }
    }

    private JdbcCasesDatabase open(Path file) throws Exception {
        JdbcCasesDatabase database = new JdbcCasesDatabase(JdbcSettings.sqlite(file, 5_000L));
        database.open(directory.toFile());
        return database;
    }
}
