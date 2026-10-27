package ru.privatenull.pncases.gui;

import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.cases.model.AnimationType;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnimationCategoryTest {

    @Test
    void seasonalSitsRightAfterAll() {
        assertEquals(
                List.of(AnimationCategory.ALL, AnimationCategory.SEASONAL, AnimationCategory.ROTATING,
                        AnimationCategory.EFFECTS, AnimationCategory.CREATURES),
                AnimationCategory.present(List.of(AnimationType.values())));
    }

    @Test
    void everyAnimationBelongsToExactlyOneRegularGroup() {
        for (AnimationType type : AnimationType.values()) {
            long groups = Arrays.stream(AnimationCategory.values())
                    .filter(category -> category != AnimationCategory.ALL && category.contains(type))
                    .count();
            assertEquals(1, groups, type.name());
        }
        assertTrue(AnimationCategory.SEASONAL.contains(AnimationType.NEW_YEAR_TREE));
        assertTrue(AnimationCategory.SEASONAL.contains(AnimationType.AUTUMN_MAPLE));
        assertFalse(AnimationCategory.SEASONAL.comingSoon());
    }

    @Test
    void seasonalStaysVisibleWhileOtherEmptyGroupsAreLeftOut() {
        assertEquals(List.of(AnimationCategory.ALL, AnimationCategory.SEASONAL, AnimationCategory.ROTATING),
                AnimationCategory.present(List.of(AnimationType.FORTUNE_RING)));
    }

    @Test
    void cyclingWrapsInBothDirections() {
        assertEquals(0, AnimationCategory.cycle(4, 5, true));
        assertEquals(4, AnimationCategory.cycle(0, 5, false));
        assertEquals(AnimationCategory.ALL, AnimationCategory.at(List.of(AnimationCategory.ALL), 7));
    }
}