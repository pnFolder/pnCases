package ru.privatenull.pncases.cases.config;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseCopyTest {

    @Test
    void aCopyKeepsSettingsButDropsBoundBlocks() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("display-name", "&aDaily");
        source.put("rewards", List.of(Map.of("type", "VAULT")));
        source.put("block", Map.of("world", "world"));
        source.put("blocks", List.of(Map.of("world", "world", "x", 1)));

        Map<String, Object> copy = CaseConfigRepository.copiedValues(source, "weekly");

        assertEquals("&aDaily", copy.get("display-name"));
        assertEquals(List.of(Map.of("type", "VAULT")), copy.get("rewards"));
        assertFalse(copy.containsKey("block"));
        assertEquals(List.of(), copy.get("blocks"));
    }

    @Test
    void anExplicitIdIsReplacedByTheNewCaseId() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("id", "daily");

        assertEquals("weekly", CaseConfigRepository.copiedValues(source, "weekly").get("id"));
    }

    @Test
    void aCaseWithoutAnIdGetsNoIdKey() {
        assertFalse(CaseConfigRepository.copiedValues(new LinkedHashMap<>(), "weekly").containsKey("id"));
    }

    @Test
    void theSourceIsNotModified() {
        Map<String, Object> source = new LinkedHashMap<>();
        source.put("blocks", List.of(Map.of("x", 1)));

        CaseConfigRepository.copiedValues(source, "weekly");

        assertTrue(source.get("blocks") instanceof List<?> list && list.size() == 1);
    }
}
