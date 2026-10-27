package ru.privatenull.pncases.gui;

import ru.privatenull.pncases.cases.model.AnimationType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Groups of animations for the selection windows. One button cycles through the groups that have at least one
 * animation to show, so an empty group (such as the seasonal one before it has animations) never appears.
 */
public enum AnimationCategory {
    ALL("Все", "FFE66D", Set.of()),
    SEASONAL("Сезонные", "9EFC65", EnumSet.of(AnimationType.NEW_YEAR_TREE, AnimationType.AUTUMN_MAPLE)),
    ROTATING("Крутящиеся", "FCA865", EnumSet.of(
            AnimationType.FORTUNE_RING, AnimationType.PRISM, AnimationType.STAR_CAROUSEL)),
    EFFECTS("Эффектные", "65D1FC", EnumSet.of(
            AnimationType.ANVIL, AnimationType.DYNAMITE, AnimationType.PORTAL, AnimationType.AQUARIUM,
            AnimationType.TIME_DISTORTION)),
    CREATURES("Существа и бои", "FC7165", EnumSet.of(
            AnimationType.MOB_HUNT, AnimationType.PILLAGER_RAID, AnimationType.POISON, AnimationType.DRAGON,
            AnimationType.MIMIC, AnimationType.STORM_DUEL));

    private final String label;
    private final String color;
    private final Set<AnimationType> members;

    AnimationCategory(String label, String color, Set<AnimationType> members) {
        this.label = label;
        this.color = color;
        this.members = members;
    }

    public String label() {
        return label;
    }

    public boolean contains(AnimationType type) {
        return this == ALL || members.contains(type);
    }

    /**
     * Every group is offered. The seasonal one stays in the list while it is empty; the other groups are left out
     * when none of the given animations belongs to them (for example on old servers).
     */
    public static List<AnimationCategory> present(Collection<AnimationType> types) {
        List<AnimationCategory> result = new ArrayList<>();
        result.add(ALL);
        for (AnimationCategory category : values()) {
            if (category == ALL) continue;
            if (category == SEASONAL || types.stream().anyMatch(category::contains)) result.add(category);
        }
        return result;
    }

    public boolean comingSoon() {
        return this == SEASONAL && members.isEmpty();
    }

    public static AnimationCategory at(List<AnimationCategory> present, int index) {
        return present.get(Math.floorMod(index, present.size()));
    }

    public static int cycle(int index, int size, boolean forward) {
        return Math.floorMod(index + (forward ? 1 : -1), size);
    }

    /** The list of groups with the current one marked, for the selector button. */
    public static List<String> selectorLines(List<AnimationCategory> present, AnimationCategory current) {
        List<String> lines = new ArrayList<>();
        for (AnimationCategory category : present) {
            lines.add(category == current
                    ? " &#" + category.color + "▸ " + category.label
                    : " &#69747E  " + category.label);
        }
        return lines;
    }
}
