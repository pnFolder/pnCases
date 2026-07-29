package ru.privatenull.cases.model;

public enum AnimationType {

    ANVIL,
    DYNAMITE,
    PORTAL,
    POISON,
    FORTUNE_RING,
    PILLAGER_RAID,
    AQUARIUM,
    MOB_HUNT,
    DRAGON,
    MIMIC;

    public Category category() {
        return this == POISON || this == PILLAGER_RAID || this == MOB_HUNT || this == DRAGON || this == MIMIC
                ? Category.MOBS
                : Category.STANDARD;
    }

    public enum Category {
        STANDARD,
        MOBS
    }
}
