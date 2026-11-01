package ru.privatenull.pncases.cases.animation.core;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.World;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.legacy.LegacyCaseAnimation;
import ru.privatenull.pncases.cases.animation.legacy.LegacyFortuneRingAnimation;
import ru.privatenull.pncases.cases.animation.type.MobHuntAnimation;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.support.compat.ServerCapabilities;
import ru.privatenull.pncases.support.compat.ServerVersion;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

public class AnimationCatalog {

    private final PnCasesPlugin plugin;
    private final Map<AnimationType, AbstractCaseAnimation> animations = new EnumMap<>(AnimationType.class);

    public AnimationCatalog(PnCasesPlugin plugin) {
        this.plugin = plugin;
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            AbstractCaseAnimation fortuneRing = new LegacyFortuneRingAnimation(plugin);
            for (AnimationType type : AnimationType.values()) {
                animations.put(type, fortuneRing);
            }
            return;
        }

        boolean modern = ServerVersion.current().isAtLeast(1, 20, 0)
                && ServerCapabilities.hasDisplayEntities();
        register(AnimationType.ANVIL, modern, "AnvilAnimation");
        register(AnimationType.DYNAMITE, modern, "DynamiteAnimation");
        register(AnimationType.PORTAL, modern, "PortalAnimation");
        register(AnimationType.POISON, modern, "PoisonAnimation");
        register(AnimationType.FORTUNE_RING, modern, "FortuneRingAnimation");
        register(AnimationType.PILLAGER_RAID, modern, "PillagerRaidAnimation");
        register(AnimationType.AQUARIUM, modern, "AquariumAnimation");
        register(AnimationType.DRAGON, modern, "DragonAnimation");
        register(AnimationType.MIMIC, modern, "MimicAnimation");
        register(AnimationType.STORM_DUEL, modern, "StormDuelAnimation");
        register(AnimationType.TIME_DISTORTION, modern, "TimeDistortionAnimation");
        register(AnimationType.PRISM, modern, "PrismAnimation");
        register(AnimationType.STAR_CAROUSEL, modern, "StarCarouselAnimation");
        register(AnimationType.NEW_YEAR_TREE, modern, "NewYearTreeAnimation");
        register(AnimationType.AUTUMN_MAPLE, modern, "AutumnMapleAnimation");
        animations.put(AnimationType.MOB_HUNT, new MobHuntAnimation(plugin));
    }

    public AbstractCaseAnimation get(AnimationType type) {
        return animations.get(type);
    }

    public void shutdownAll() {
        for (AbstractCaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.shutdown();
            } catch (RuntimeException exception) {
                Log.error("Could not shut down case animation", exception);
            }
        }
    }

    public void cancelWorld(World world) {
        if (world == null) return;
        for (AbstractCaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.cancelWorld(world);
            } catch (RuntimeException exception) {
                Log.error("Could not cancel case animation state for world unload: " + world.getName(),
                        exception);
            }
        }
    }

    private void register(AnimationType type, boolean modern, String className) {
        animations.put(type, modern
                ? loadModern("ru.privatenull.pncases.cases.animation.type." + className)
                : new LegacyCaseAnimation(plugin, type));
    }

    private AbstractCaseAnimation loadModern(String className) {
        try {
            Class<?> rawClass = Class.forName(className);
            Constructor<?> constructor = rawClass.getConstructor(PnCasesPlugin.class);
            Object instance = constructor.newInstance(plugin);
            if (instance instanceof AbstractCaseAnimation animation) {
                return animation;
            }
            throw new IllegalStateException(className + " is not an AbstractCaseAnimation");
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Не удалось загрузить современную анимацию " + className, exception);
        }
    }
}
