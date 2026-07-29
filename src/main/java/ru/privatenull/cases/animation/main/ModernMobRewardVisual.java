package ru.privatenull.cases.animation.main;

import org.bukkit.Location;
import org.bukkit.entity.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import ru.privatenull.cases.animation.RewardLabel;
import ru.privatenull.cases.animation.main.MobHuntAnimation.RewardVisual;

import java.util.ArrayList;
import java.util.List;

public final class ModernMobRewardVisual implements RewardVisual {
    private final ItemDisplay reward;
    private final RewardLabel label;
    public ModernMobRewardVisual(Location center, ItemStack item, String name) {
        reward = (ItemDisplay) center.getWorld().spawnEntity(center.clone().add(0.0, -3.0, 0.0), EntityType.ITEM_DISPLAY);
        reward.setItemStack(item);
        reward.setBillboard(Display.Billboard.FIXED);
        prepare(reward);
        setItemTransform(reward, 0.02f, 0.0f);
        label = RewardLabel.create(center, name);
    }

    @Override
    public List<Entity> entities() {
        List<Entity> result = new ArrayList<>(2);
        result.add(reward);
        if (label != null) result.add(label.entity());
        return result;
    }

    @Override
    public void tick(Location center, int tick) {
        double reveal = ease(Math.min(1.0, tick / 30.0));
        Location rewardLocation = center.clone().add(0.0,
                -0.72 + reveal * 0.90 + Math.sin(tick * 0.14) * 0.07 * reveal, 0.0);
        reward.teleport(rewardLocation);
        setItemTransform(reward, (float) (0.02 + reveal * 0.88), (float) (tick * 0.075));
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
        display.setTeleportDuration(2);
        display.setBrightness(new Display.Brightness(15, 15));
    }

    private static void setItemTransform(ItemDisplay display, float scale, float yaw) {
        display.setTransformation(new Transformation(
                new Vector3f(),
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
