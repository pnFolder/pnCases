package ru.privatenull.pncases.cases.animation.reveal;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.effect.RewardLabel;
import ru.privatenull.pnlibrary.entity.VisualEntity;

import java.util.List;

/** Armor-stand compatible reward reveal for servers without display entities. */
public final class LegacyRewardReveal implements RewardReveal {
    private final VisualEntity reward;
    private final RewardLabel label;

    public LegacyRewardReveal(AnimationSession run, Location center, ItemStack item, String name) {
        reward = VisualEntity.item(center.clone().add(0.0, -3.0, 0.0), item);
        label = RewardLabel.create(run, center, name);
    }

    @Override
    public List<Entity> entities() {
        return List.of(reward.entity());
    }

    @Override
    public void tick(Location center, int tick) {
        double reveal = ease(Math.min(1.0, tick / 28.0));
        reward.teleport(center.clone().add(0.0, -0.65 + reveal * 0.82
                + Math.sin(tick * 0.14) * 0.06 * reveal, 0.0));
        reward.setRotation((float) ((tick * 5.0) % 360.0), 0.0f);
        reward.setScale((float) (0.20 + reveal * 0.55));
        if (label != null && reveal > 0.72) {
            label.showAbove(reward.entity().getLocation(), 0.88);
        }
    }

    @Override
    public void remove() {
        reward.remove();
        if (label != null) label.remove();
    }

    private static double ease(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return 1.0 - Math.pow(1.0 - clamped, 3.0);
    }
}
