package ru.privatenull.pncases.cases.animation.reveal;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;

import java.util.List;

public final class DisplayRewardReveal implements RewardReveal {
    private final ItemDisplay reward;
    private final RewardLabel label;
    public DisplayRewardReveal(AnimationSession run, Location center, ItemStack item, String name) {
        reward = (ItemDisplay) center.getWorld().spawnEntity(center, EntityType.ITEM_DISPLAY);
        reward.setItemStack(item);
        reward.setBillboard(Display.Billboard.FIXED);
        prepare(reward);
        setItemTransform(reward, 0.02f, 0.0f, -3.0f);
        label = RewardLabel.create(run, center, name);
    }

    @Override
    public List<Entity> entities() {
        return List.of(reward);
    }

    @Override
    public void tick(Location center, int tick) {
        double reveal = ease(Math.min(1.0, tick / 30.0));
        Location rewardLocation = center.clone().add(0.0,
                -0.72 + reveal * 0.90 + Math.sin(tick * 0.14) * 0.07 * reveal, 0.0);
        setItemTransform(reward, (float) (0.02 + reveal * 0.88),
                (float) (tick * 0.075), (float) (rewardLocation.getY() - center.getY()));
        if (label != null) label.showAbove(rewardLocation, 0.88);
    }

    @Override
    public void remove() {
        reward.remove();
        if (label != null) label.remove();
    }

    private static void prepare(Display display) {
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setViewRange(32.0f);
        display.setShadowRadius(0.0f);
        display.setInterpolationDuration(2);
        display.setBrightness(new Display.Brightness(15, 15));
    }

    private static void setItemTransform(ItemDisplay display, float scale, float yaw, float y) {
        display.setTransformation(new Transformation(
                new Vector3f(0.0f, y, 0.0f),
                new AxisAngle4f(yaw, 0.0f, 1.0f, 0.0f),
                new Vector3f(scale, scale, scale),
                new AxisAngle4f()
        ));
        display.setInterpolationDelay(0);
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }
}
