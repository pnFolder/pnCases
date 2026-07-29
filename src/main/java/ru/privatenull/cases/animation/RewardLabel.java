package ru.privatenull.cases.animation;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.TextDisplay;

/**
 * The single reward-name presentation used by every modern animation.
 * It stays attached above the spinning reward instead of covering the screen.
 */
public final class RewardLabel {

    private final TextDisplay display;
    private final String text;

    private RewardLabel(TextDisplay display, String text) {
        this.display = display;
        this.text = text;
    }

    public static RewardLabel create(AnimationRun run, Location rewardCenter, String rewardName) {
        if (run == null || rewardCenter == null || rewardCenter.getWorld() == null) return null;
        RewardLabel label = create(rewardCenter, rewardName);
        if (label != null) run.track(label.display);
        return label;
    }

    public static RewardLabel create(Location rewardCenter, String rewardName) {
        if (rewardCenter == null || rewardCenter.getWorld() == null) return null;
        String text = rewardName == null || rewardName.isBlank() ? "§fНаграда" : rewardName;
        TextDisplay display = (TextDisplay) rewardCenter.getWorld().spawnEntity(
                rewardCenter.clone().add(0.0, -8.0, 0.0), EntityType.TEXT_DISPLAY);
        display.setText("");
        display.setBillboard(Display.Billboard.CENTER);
        display.setShadowed(true);
        display.setSeeThrough(true);
        display.setDefaultBackground(false);
        display.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        display.setPersistent(false);
        display.setInvulnerable(true);
        display.setViewRange(32.0f);
        return new RewardLabel(display, text);
    }

    public void showAbove(Location rewardLocation, double height) {
        if (display == null || display.isDead() || rewardLocation == null) return;
        display.teleport(rewardLocation.clone().add(0.0, height, 0.0));
        display.setText(text);
    }

    public void hide() {
        if (display == null || display.isDead()) return;
        display.setText("");
        display.teleport(display.getLocation().subtract(0.0, 8.0, 0.0));
    }

    public TextDisplay entity() {
        return display;
    }

    public void remove() {
        if (display != null && !display.isDead()) display.remove();
    }
}
