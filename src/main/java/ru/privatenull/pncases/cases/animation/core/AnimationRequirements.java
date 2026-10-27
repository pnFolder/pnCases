package ru.privatenull.pncases.cases.animation.core;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Pillager;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Zombie;
import ru.privatenull.pncases.cases.model.AnimationType;

import java.util.List;
import java.util.Map;

/** Which animations need real mobs, and which. Display entities and particles need nothing from the world. */
public final class AnimationRequirements {

    private static final Map<AnimationType, List<Class<? extends Mob>>> MOBS = Map.of(
            AnimationType.MOB_HUNT, List.of(Zombie.class, Skeleton.class),
            AnimationType.PILLAGER_RAID, List.of(Pillager.class),
            AnimationType.POISON, List.of(Slime.class));

    private AnimationRequirements() {
    }

    public static boolean needsMobs(AnimationType type) {
        return type != null && MOBS.containsKey(type);
    }

    public static List<Class<? extends Mob>> mobTypes(AnimationType type) {
        return type == null ? List.of() : MOBS.getOrDefault(type, List.of());
    }
}
