package ru.privatenull.pncases.storage;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import ru.privatenull.pncases.support.database.JdbcDatabase;
import ru.privatenull.pncases.support.database.JdbcSettings;

import static org.junit.jupiter.api.Assertions.*;

class JdbcBatchOpeningTest {
    @TempDir Path directory;
    private JdbcDatabase jdbc;
    private JdbcCasesDatabase database;
    private final UUID player = UUID.randomUUID();

    @BeforeEach
    void open() throws Exception {
        jdbc = new JdbcDatabase(JdbcSettings.sqlite(directory.resolve("cases.db"), 1000));
        jdbc.open();
        database = new JdbcCasesDatabase(jdbc);
        database.initialize();
    }

    @AfterEach
    void close() {
        if (jdbc != null) jdbc.close();
    }

    @Test
    void insufficientKeysRollBackBatchAndGuaranteeWithoutRequestingAnotherConnection() {
        database.addKeys(player, "donate", 3);
        database.setGuaranteeProgress(player, "donate", 7);
        var reservation = database.reserveBatch(player, batch(10, 0));
        assertFalse(reservation.reserved());
        assertEquals(3, reservation.balance());
        assertEquals(3, database.getKeys(player, "donate"));
        assertEquals(7, database.getGuaranteeProgress(player, "donate"));
        assertNull(database.loadBatch(player));
        assertTrue(database.getPendingPlayers().isEmpty());
    }

    @Test
    void singleOpeningWithoutKeysDoesNotExhaustSqlitePool() {
        var reservation = database.reserveOpening(player, "donate", PendingBatchTest.reward("single"));
        assertFalse(reservation.reserved());
        assertEquals(0, reservation.balance());
        assertNull(database.loadPending(player));
    }

    @Test
    void tenKeysAndGuaranteeCommitWithWholeSnapshotAndCannotBeReservedAgain() {
        database.addKeys(player, "donate", 12);
        PendingBatch batch = batch(10, 4);
        assertTrue(database.reserveBatch(player, batch).reserved());
        assertEquals(2, database.getKeys(player, "donate"));
        assertEquals(4, database.getGuaranteeProgress(player, "donate"));
        assertEquals(batch.id(), database.loadBatch(player).id());
        assertEquals(10, database.loadBatch(player).rewards().size());
        assertNotNull(database.reserveBatch(player, batch(2, 0)).pendingReward());
        assertNotNull(database.reserveOpening(player, "donate", PendingBatchTest.reward("single")).pendingReward());
        assertEquals(2, database.getKeys(player, "donate"));
        assertNull(database.loadPending(player));
    }

    @Test
    void legacyPendingRewardPreventsBatchDebit() {
        database.addKeys(player, "donate", 12);
        assertTrue(database.reserveOpening(player, "donate", PendingBatchTest.reward("legacy")).reserved());
        assertNotNull(database.reserveBatch(player, batch(10, 0)).pendingReward());
        assertEquals(11, database.getKeys(player, "donate"));
        assertNull(database.loadBatch(player));
        assertEquals("legacy", database.loadPending(player).displayName());
    }

    @Test
    void acknowledgementIsIdempotentAndCursorSurvivesDatabaseRestart() throws Exception {
        database.addKeys(player, "donate", 3);
        PendingBatch batch = batch(3, -1);
        assertTrue(database.reserveBatch(player, batch).reserved());
        database.acknowledgeBatchReward(player, UUID.randomUUID(), 0);
        database.acknowledgeBatchReward(player, batch.id(), 1);
        assertEquals(0, database.loadBatch(player).nextIndex());
        database.acknowledgeBatchReward(player, batch.id(), 0);
        database.acknowledgeBatchReward(player, batch.id(), 0);
        assertEquals(1, database.loadBatch(player).nextIndex());
        jdbc.close();
        open();
        PendingBatch recovered = database.loadBatch(player);
        assertEquals(batch.id(), recovered.id());
        assertEquals(1, recovered.nextIndex());
        assertEquals("reward-1", recovered.rewards().get(recovered.nextIndex()).displayName());
        assertEquals(0, database.getKeys(player, "donate"));
        assertEquals(java.util.Set.of(player), database.getPendingPlayers());
        database.acknowledgeBatchReward(player, batch.id(), 1);
        database.acknowledgeBatchReward(player, batch.id(), 2);
        database.acknowledgeBatchReward(player, batch.id(), 2);
        assertNull(database.loadBatch(player));
        assertTrue(database.getPendingPlayers().isEmpty());
    }

    private PendingBatch batch(int size, int progress) {
        return new PendingBatch(UUID.randomUUID(), "donate",
                IntStream.range(0, size).mapToObj(i -> PendingBatchTest.reward("reward-" + i)).toList(), 0, progress);
    }
}
