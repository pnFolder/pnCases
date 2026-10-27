package ru.privatenull.pncases.cases.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigHistoryTest {

    @Test
    void undoReturnsTheMostRecentStateFirst() {
        ConfigHistory history = new ConfigHistory(5);
        history.push("daily", Map.of("v", 1));
        history.push("daily", Map.of("v", 2));

        assertEquals(2, history.depth("daily"));
        assertEquals(Map.of("v", 2), history.pop("daily"));
        assertEquals(Map.of("v", 1), history.pop("daily"));
        assertNull(history.pop("daily"));
        assertEquals(0, history.depth("daily"));
    }

    @Test
    void theOldestStateIsDroppedPastTheLimit() {
        ConfigHistory history = new ConfigHistory(2);
        history.push("daily", Map.of("v", 1));
        history.push("daily", Map.of("v", 2));
        history.push("daily", Map.of("v", 3));

        assertEquals(2, history.depth("daily"));
        assertEquals(Map.of("v", 3), history.pop("daily"));
        assertEquals(Map.of("v", 2), history.pop("daily"));
        assertNull(history.pop("daily"));
    }

    @Test
    void casesDoNotShareHistory() {
        ConfigHistory history = new ConfigHistory(3);
        history.push("daily", Map.of("v", 1));

        assertEquals(0, history.depth("vote"));
        assertNull(history.pop("vote"));
        assertEquals(1, history.depth("daily"));
    }

    @Test
    void clearingForgetsACase() {
        ConfigHistory history = new ConfigHistory(3);
        history.push("daily", Map.of("v", 1));

        history.clear("daily");

        assertEquals(0, history.depth("daily"));
    }

    @Test
    void aLimitBelowOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ConfigHistory(0));
    }
}
