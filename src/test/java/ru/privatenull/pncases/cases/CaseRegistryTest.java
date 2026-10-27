package ru.privatenull.pncases.cases;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.opening.CaseBlockKey;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class CaseRegistryTest {

    private static final CaseBlockKey A = new CaseBlockKey("world", 1, 64, 1);
    private static final CaseBlockKey B = new CaseBlockKey("world", 2, 64, 2);
    private static final CaseBlockKey C = new CaseBlockKey("world", 3, 64, 3);

    @Test
    void movingACaseReleasesItsOldBlocksAndClaimsNewOnes() {
        Map<CaseBlockKey, String> current = Map.of(A, "daily", B, "vote");

        Map<CaseBlockKey, String> result = CaseRegistry.replaceOwnedBlocks(current, "daily", List.of(A), List.of(C));

        assertEquals(Map.of(B, "vote", C, "daily"), result);
    }

    @Test
    void claimingABlockOwnedByAnotherCaseIsRejectedWithoutChangingTheInput() {
        Map<CaseBlockKey, String> current = Map.of(A, "daily", B, "vote");

        assertNull(CaseRegistry.replaceOwnedBlocks(current, "daily", List.of(A), List.of(B)));
        assertEquals(Map.of(A, "daily", B, "vote"), current);
    }

    @Test
    void keepingTheSameBlockIsNotAConflict() {
        Map<CaseBlockKey, String> current = Map.of(A, "daily");

        assertEquals(current, CaseRegistry.replaceOwnedBlocks(current, "daily", List.of(A), List.of(A)));
    }

    @Test
    void emptyRegistryAnswersNullSafely() {
        CaseRegistry registry = new CaseRegistry();

        assertNull(registry.byName(null));
        assertNull(registry.byName("missing"));
        assertNull(registry.section("missing"));
        assertNull(registry.ownerOf(A));
        assertFalse(registry.contains(null));
        assertEquals(0, registry.size());
    }
}
