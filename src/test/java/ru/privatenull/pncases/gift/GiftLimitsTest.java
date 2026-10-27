package ru.privatenull.pncases.gift;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.gift.GiftLimits.Limit;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GiftLimitsTest {

    @Test
    void aPlayerWithoutAnyRuleGetsTheDefaultLimit() {
        Limit def = new Limit(5, 10);

        assertEquals(def, GiftLimits.combine(def, List.of()));
    }

    @Test
    void thePlayerGetsTheMostGenerousOfTheirRules() {
        Limit result = GiftLimits.combine(new Limit(5, 10),
                List.of(new Limit(10, 25), new Limit(20, 15)));

        assertEquals(new Limit(20, 25), result);
    }

    @Test
    void unlimitedBeatsAnyNumber() {
        Limit result = GiftLimits.combine(new Limit(5, 10), List.of(new Limit(-1, 25), new Limit(20, -1)));

        assertEquals(Limit.UNLIMITED, result);
    }

    @Test
    void theRequestIsCutToBothLimits() {
        Limit limit = new Limit(5, 10);

        assertEquals(3, GiftLimits.allowed(3, limit, 0));
        assertEquals(5, GiftLimits.allowed(20, limit, 0));
        assertEquals(4, GiftLimits.allowed(20, limit, 6));
        assertEquals(0, GiftLimits.allowed(20, limit, 10));
        assertEquals(0, GiftLimits.allowed(20, limit, 15));
    }

    @Test
    void withoutLimitsEverythingIsAllowed() {
        assertEquals(64, GiftLimits.allowed(64, Limit.UNLIMITED, 1000));
        assertEquals(64, GiftLimits.allowed(64, new Limit(-1, -1), 0));
        assertEquals(7, GiftLimits.allowed(64, new Limit(-1, 10), 3));
    }

    @Test
    void aStepNeverGoesBelowZeroAndNoLimitStaysNoLimitOnTheWayDown() {
        assertEquals(4, GiftLimits.adjust(3, 1));
        assertEquals(0, GiftLimits.adjust(3, -10));
        assertEquals(0, GiftLimits.adjust(0, -1));
        assertEquals(1, GiftLimits.adjust(-1, 1));
        assertEquals(10, GiftLimits.adjust(-1, 10));
        assertEquals(-1, GiftLimits.adjust(-1, -1));
    }

    @Test
    void aPermissionBecomesARuleIdAndAnyPermissionNameIsAccepted() {
        assertEquals("group-donate", GiftLimits.ruleIdFor("group.donate"));
        assertEquals("group", GiftLimits.ruleIdFor("group.*"));
        assertEquals("rule", GiftLimits.ruleIdFor("*"));
        assertTrue(GiftLimits.validPermission("group.donate"));
        assertTrue(GiftLimits.validPermission("*"));
        assertTrue(GiftLimits.validPermission("my.plugin:special"));
        assertFalse(GiftLimits.validPermission("group donate"));
        assertFalse(GiftLimits.validPermission(""));
        assertFalse(GiftLimits.validPermission("   "));
    }
}
