package ru.privatenull.pncases.cases.animation.effect;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Function;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.hologram.HologramText;
import ru.privatenull.pncases.hologram.HologramTextStyle;

/** Eight reward visuals shared by the two orbit scenes; never selects a reward. */
public final class RewardOrbit {

    private final Location anchor;
    private final List<ItemDisplay> items = new ArrayList<>();
    private final List<HologramText> labels = new ArrayList<>();
    private final List<String> names = new ArrayList<>();
    private final Location[] positions = new Location[8];
    private final float[] spins = new float[8];
    private final int winnerIndex = ThreadLocalRandom.current().nextInt(8);
    private final ItemDisplay prize;
    private final RewardLabel prizeLabel;
    private boolean labelsRemoved;
    private float orbitScale;
    private float selectedSpin;

    public RewardOrbit(AnimationSession run, Location anchor, Function<Reward, ItemStack> visualOf,
                       Function<Reward, String> nameOf, boolean withLabels) {
        this.anchor = anchor.clone();
        List<Reward> candidates = candidates(run.definition().rewards(), run.reward(), winnerIndex);
        for (int i = 0; i < candidates.size(); i++) {
            ItemDisplay item = run.track(run.world().spawn(anchor, ItemDisplay.class));
            prepare(item);
            item.setItemStack(visualOf.apply(candidates.get(i)).clone());
            item.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            items.add(item);
            place(i, anchor, 0.0f, 0.0f);
            String name = nameOf.apply(candidates.get(i));
            names.add(name);
            if (withLabels) {
                HologramText label = run.holograms().createTransientText(anchor, List.of(" "),
                        new HologramTextStyle(0.86f, 48, 15, 1, 1));
                labels.add(label);
                run.onClose(label::remove);
            }
        }
        prize = run.track(run.world().spawn(anchor, ItemDisplay.class));
        prepare(prize);
        prize.setItemStack(visualOf.apply(run.reward()).clone());
        prize.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        placePrize(anchor, 0.0f, 0.0f);
        prizeLabel = RewardLabel.create(run, anchor, nameOf.apply(run.reward()));
    }

    static List<Reward> candidates(List<Reward> rewards, Reward winner, int winnerIndex) {
        List<Reward> pool = new ArrayList<>();
        for (Reward reward : rewards) {
            if (reward != null && reward != winner && reward.chance() > 0) pool.add(reward);
        }
        Collections.shuffle(pool);
        if (pool.isEmpty()) pool.add(winner);
        List<Reward> candidates = new ArrayList<>(8);
        for (int i = 0; i < 8; i++) candidates.add(pool.get(i % pool.size()));
        candidates.set(winnerIndex, winner);
        return candidates;
    }

    public int size() {
        return items.size();
    }

    public static Vector towardViewer(AnimationSession run, Location center) {
        Vector toward = run.player().getLocation().toVector().subtract(center.toVector()).setY(0.0);
        if (toward.lengthSquared() < 0.001) {
            toward = run.player().getLocation().getDirection().multiply(-1.0).setY(0.0);
        }
        if (toward.lengthSquared() < 0.001) toward = new Vector(0.0, 0.0, 1.0);
        return toward.normalize();
    }

    public double angle(int index, double rotation) {
        return angle(index, size(), rotation);
    }

    static double angle(int index, int count, double rotation) {
        return Math.PI / 2.0 - index * Math.PI * 2.0 / count - rotation;
    }

    /** Stop with the reserved winner at the top (vertical) or front (horizontal). */
    public double stopRotation(int turns) {
        return stopRotation(winnerIndex, size(), turns);
    }

    static double stopRotation(int winnerIndex, int count, int turns) {
        double full = Math.PI * 2.0;
        return turns * full + (full - winnerIndex * full / count) % full;
    }

    public void pose(int index, Location location, float scale, float spin, boolean showLabel) {
        orbitScale = scale;
        place(index, location, scale, spin);
        if (!labelsRemoved && index < labels.size()) {
            labels.get(index).update(location.clone().add(0.0, 0.44, 0.0),
                    showLabel ? names.get(index) : " ");
        }
    }

    public void select(double progress) {
        for (int i = 0; i < size(); i++) {
            float scale = i == winnerIndex
                    ? orbitScale + (0.48f - orbitScale) * (float) smooth(progress)
                            + (float) Math.sin(progress * Math.PI) * 0.06f
                    : orbitScale + (0.36f - orbitScale) * (float) smooth(progress);
            place(i, positions[i], scale, spins[i]);
        }
    }

    /** Shrinks the other reward visuals and carries only the winner to the clear result area. */
    public void gather(Location selectedStart, Location target, double progress) {
        if (!labelsRemoved) selectedSpin = spins[winnerIndex];
        removeLabels();
        double eased = smooth(progress);
        for (int i = 0; i < size(); i++) {
            if (i == winnerIndex) {
                Location position = selectedStart.clone().add(target.toVector()
                        .subtract(selectedStart.toVector()).multiply(eased));
                float scale = (float) (0.48 * (1.0 - smooth((progress - 0.45) / 0.30)));
                place(i, position, scale, selectedSpin + (float) (eased * Math.PI));
            } else {
                place(i, positions[i], (float) (0.36 * (1.0 - smooth(progress / 0.6))), spins[i]);
            }
        }
        float prizeScale = (float) smooth((progress - 0.78) / 0.22);
        placePrize(target, prizeScale, 0.0f);
        if (prizeScale > 0.3f && prizeLabel != null) prizeLabel.showAbove(target, 0.68);
    }

    public Location winnerPosition() {
        return positions[winnerIndex].clone();
    }

    public void showPrize(Location target, int tick) {
        placePrize(target, 1.0f, tick * 0.035f);
        if (prizeLabel != null) prizeLabel.showAbove(target, 0.68);
    }

    private void removeLabels() {
        if (labelsRemoved) return;
        labelsRemoved = true;
        labels.forEach(HologramText::remove);
    }

    private static void prepare(Display display) {
        display.setPersistent(false);
        display.setGravity(false);
        display.setInvulnerable(true);
        display.setBillboard(Display.Billboard.FIXED);
        display.setBrightness(new Display.Brightness(15, 15));
        display.setInterpolationDuration(1);
        display.setShadowRadius(0.0f);
        // The model moves relative to a stationary entity; keep its whole orbit in view.
        display.setDisplayWidth(12.0f);
        display.setDisplayHeight(12.0f);
    }

    private void place(int index, Location location, float scale, float spin) {
        positions[index] = location.clone();
        spins[index] = spin;
        ItemDisplay item = items.get(index);
        item.setInterpolationDelay(0);
        Quaternionf rotation = new Quaternionf().rotateY(spin).rotateZ(0.22f).rotateX(0.18f);
        item.setTransformation(new Transformation(offset(location), rotation,
                new Vector3f(scale * 1.5f), new Quaternionf()));
    }

    private void placePrize(Location location, float scale, float spin) {
        prize.setInterpolationDelay(0);
        prize.setTransformation(new Transformation(offset(location), new Quaternionf().rotateY(spin),
                new Vector3f(scale), new Quaternionf()));
    }

    private Vector3f offset(Location location) {
        return new Vector3f((float) (location.getX() - anchor.getX()),
                (float) (location.getY() - anchor.getY()), (float) (location.getZ() - anchor.getZ()));
    }

    public static double smooth(double progress) {
        double value = Math.max(0.0, Math.min(1.0, progress));
        return value * value * (3.0 - 2.0 * value);
    }

    public static double spinProgress(double progress) {
        double value = Math.max(0.0, Math.min(1.0, progress));
        // Short acceleration, then continuous deceleration; neither endpoint snaps in speed.
        double acceleration = 0.16;
        double peakSpeed = 6.0 / (2.0 + acceleration);
        if (value < acceleration) return peakSpeed * value * value / (2.0 * acceleration);
        double remaining = (1.0 - value) / (1.0 - acceleration);
        return peakSpeed * acceleration / 2.0
                + peakSpeed * (1.0 - acceleration) / 3.0 * (1.0 - remaining * remaining * remaining);
    }
}
