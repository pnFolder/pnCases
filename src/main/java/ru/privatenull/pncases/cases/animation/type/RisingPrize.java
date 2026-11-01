package ru.privatenull.pncases.cases.animation.type;

import org.bukkit.Location;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.animation.effect.RewardOrbit;

/**
 * The prize of a winter scene: it comes out of the scene centre and hangs on the side of the player, following them
 * as they walk round, so it is never hidden behind the scene.
 */
final class RisingPrize {

    private static final double REACH = 1.75;
    private static final double HEIGHT = 1.45;

    private final AnimationSession run;
    private final Location ground;
    private final Location from;
    private final ItemDisplay display;
    private final RewardLabel label;
    private Vector side;

    RisingPrize(AnimationSession run, ItemStack visual, String name, Location from, Location ground, Vector side) {
        this.run = run;
        this.ground = ground.clone();
        this.from = from.clone();
        this.side = side.clone();
        this.display = SceneParts.spawnPrize(run, from, visual);
        this.label = RewardLabel.create(run, target(), name);
    }

    private Location target() {
        return ground.clone().add(side.clone().multiply(REACH)).add(0.0, HEIGHT, 0.0);
    }

    /** Places the prize {@code tick} ticks after it appeared and returns where it is. */
    Location step(int tick) {
        side = SceneParts.followSide(side, ground, run.player());
        Location target = target();
        if (tick % 8 == 0) run.protectReward(target);
        double fly = RewardOrbit.smooth(tick / 30.0);
        Location at = from.clone().add(target.toVector().subtract(from.toVector()).multiply(fly));
        at.add(0.0, Math.sin(fly * Math.PI) * 0.45, 0.0);
        float scale = (float) (0.2 + 0.8 * RewardOrbit.smooth(tick / 14.0));
        SceneParts.placePrize(display, from, at, scale, tick * 0.12f);
        if (tick > 24 && label != null) label.showAbove(at, 0.68);
        return at;
    }
}
