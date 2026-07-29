package ru.privatenull.cases.animation;

import org.bukkit.World;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.animation.legacy.LegacyCaseAnimation;
import ru.privatenull.cases.animation.legacy.LegacyFortuneRingAnimation;
import ru.privatenull.cases.animation.main.MobHuntAnimation;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.util.ServerCompatibility;

import java.lang.reflect.Constructor;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.logging.Level;

public class AnimationRegistry {

    private final PnCasesPlugin plugin;
    private final Map<AnimationType, CaseAnimation> animations = new EnumMap<>(AnimationType.class);

    public AnimationRegistry(PnCasesPlugin plugin) {
        this.plugin = plugin;
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            CaseAnimation fortuneRing = new LegacyFortuneRingAnimation(plugin);
            for (AnimationType type : AnimationType.values()) {
                animations.put(type, fortuneRing);
            }
            return;
        }

        boolean modern = ServerCompatibility.useModernAnimations();
        register(AnimationType.ANVIL, modern, "AnvilAnimation");
        register(AnimationType.DYNAMITE, modern, "DynamiteAnimation");
        register(AnimationType.PORTAL, modern, "PortalAnimation");
        register(AnimationType.POISON, modern, "PoisonAnimation");
        register(AnimationType.FORTUNE_RING, modern, "FortuneRingAnimation");
        register(AnimationType.PILLAGER_RAID, modern, "PillagerRaidAnimation");
        register(AnimationType.AQUARIUM, modern, "AquariumAnimation");
        register(AnimationType.DRAGON, modern, "DragonAnimation");
        register(AnimationType.MIMIC, modern, "MimicAnimation");
        animations.put(AnimationType.MOB_HUNT, new MobHuntAnimation(plugin));
    }

    public CaseAnimation get(AnimationType type) {
        return animations.get(type);
    }

    public void shutdownAll() {
        for (CaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.shutdown();
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE, "Could not shut down case animation", exception);
            }
        }
    }

    public void cancelWorld(World world) {
        if (world == null) return;
        for (CaseAnimation animation : new HashSet<>(animations.values())) {
            try {
                animation.cancelWorld(world);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.SEVERE,
                        "Could not cancel case animation state for world unload: " + world.getName(),
                        exception);
            }
        }
    }

    private void register(AnimationType type, boolean modern, String className) {
        animations.put(type, modern
                ? loadModern("ru.privatenull.cases.animation.main." + className)
                : new LegacyCaseAnimation(plugin, type));
    }

    private CaseAnimation loadModern(String className) {
        try {
            Class<?> rawClass = Class.forName(className);
            Constructor<?> constructor = rawClass.getConstructor(PnCasesPlugin.class);
            Object instance = constructor.newInstance(plugin);
            if (instance instanceof CaseAnimation animation) {
                return animation;
            }
            throw new IllegalStateException(className + " is not a CaseAnimation");
        } catch (ReflectiveOperationException | LinkageError exception) {
            throw new IllegalStateException("Не удалось загрузить современную анимацию " + className, exception);
        }
    }
}
