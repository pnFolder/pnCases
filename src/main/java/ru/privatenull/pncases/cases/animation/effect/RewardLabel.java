package ru.privatenull.pncases.cases.animation.effect;

import org.bukkit.Location;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.hologram.HologramText;

/** Reward-name text backed by the configured hologram provider. */
public final class RewardLabel {

    private final HologramText hologram;
    private final String text;
    private final RewardHologramStyle style;
    private Location location;
    private int revealCalls;

    private RewardLabel(HologramText hologram, String text, Location location,
                        RewardHologramStyle style) {
        this.hologram = hologram;
        this.text = text;
        this.location = location.clone();
        this.style = style;
    }

    public static RewardLabel create(AnimationSession run, Location rewardCenter, String rewardName) {
        if (run == null || rewardCenter == null || rewardCenter.getWorld() == null) return null;
        String text = rewardName == null || rewardName.isBlank() ? "§fНаграда" : rewardName;
        RewardHologramStyle style = RewardHologramStyle.read(run.plugin().getConfig());
        Location hidden = rewardCenter.clone().subtract(0.0, 8.0, 0.0);
        HologramText hologram = run.holograms().createTransientText(
                hidden, java.util.List.of(" "), style.textStyle());
        RewardLabel label = new RewardLabel(hologram, text, hidden, style);
        run.onClose(label::remove);
        return label;
    }

    public void showAbove(Location rewardLocation, double height) {
        if (rewardLocation == null) return;
        if (revealCalls++ < style.revealDelayTicks()) return;
        location = rewardLocation.clone().add(0.0, height + style.heightOffset(), 0.0);
        hologram.update(location, text);
    }

    public void hide() {
        location = location.clone().subtract(0.0, 8.0, 0.0);
        hologram.update(location, "");
    }

    public void remove() {
        hologram.remove();
    }
}
