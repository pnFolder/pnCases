package ru.privatenull.pncases.cases;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBalanceCacheTest {

    private final AtomicLong now = new AtomicLong(1_000);
    private final KeyBalanceCache cache = new KeyBalanceCache(10_000, now::get);
    private final UUID player = UUID.randomUUID();

    @Test
    void aBalanceIsFreshOnlyForTheConfiguredTime() {
        cache.put(player, "daily", 5);

        assertEquals(5, cache.fresh(player, "daily"));
        now.addAndGet(10_001);
        assertNull(cache.fresh(player, "daily"));
        assertEquals(5, cache.any(player, "daily"));
    }

    @Test
    void unknownBalancesAreAbsentRatherThanZero() {
        assertNull(cache.fresh(player, "daily"));
        assertNull(cache.any(player, "daily"));
        assertNull(cache.any(null, "daily"));
    }

    @Test
    void negativeBalancesAreStoredAsZero() {
        cache.put(player, "daily", -3);

        assertEquals(0, cache.any(player, "daily"));
    }

    @Test
    void onlyReasonableCaseIdsAreStored() {
        assertTrue(KeyBalanceCache.isStorableId("daily-case_2"));
        assertFalse(KeyBalanceCache.isStorableId("Daily"));
        assertFalse(KeyBalanceCache.isStorableId("a b"));
        assertFalse(KeyBalanceCache.isStorableId(""));
        assertFalse(KeyBalanceCache.isStorableId(null));

        cache.put(player, "has space", 1);
        assertNull(cache.any(player, "has space"));
    }

    @Test
    void aPlayerCannotGrowTheCacheWithoutBound() {
        for (int index = 0; index < 200; index++) cache.put(player, "case" + index, index);

        assertEquals(63, cache.any(player, "case63"));
        assertNull(cache.any(player, "case64"));
        // an already stored case can still be updated
        cache.put(player, "case0", 42);
        assertEquals(42, cache.any(player, "case0"));
    }

    @Test
    void onlyOneLoadRunsAtATimePerBalance() {
        assertTrue(cache.beginLoad(player, "daily"));
        assertFalse(cache.beginLoad(player, "daily"));
        assertTrue(cache.beginLoad(player, "vote"));

        cache.endLoad(player, "daily");
        assertTrue(cache.beginLoad(player, "daily"));
    }

    @Test
    void forgettingAPlayerDropsBalancesAndRunningLoads() {
        cache.put(player, "daily", 5);
        cache.beginLoad(player, "daily");

        cache.forget(player);

        assertNull(cache.any(player, "daily"));
        assertTrue(cache.beginLoad(player, "daily"));
    }
}
