package ru.privatenull.pncases.hologram;

import org.bukkit.Location;
import ru.privatenull.pncases.hologram.provider.HologramProvider;

import java.util.List;
import java.util.function.BiConsumer;

/** Provider-backed implementation used for animation labels. */
final class ManagedHologramText implements HologramText {
    private final String name;
    private final HologramProvider provider;
    private final int movementTicks;
    private final Runnable removeAction;
    private final BiConsumer<String, Throwable> failureHandler;
    private Location location;
    private List<String> lines;
    private boolean removed;

    ManagedHologramText(
            String name,
            HologramProvider provider,
            Location location,
            List<String> lines,
            int movementTicks,
            Runnable removeAction,
            BiConsumer<String, Throwable> failureHandler
    ) {
        this.name = name;
        this.provider = provider;
        this.location = location.clone();
        this.lines = List.copyOf(lines);
        this.movementTicks = movementTicks;
        this.removeAction = removeAction;
        this.failureHandler = failureHandler;
    }

    @Override
    public void update(Location nextLocation, List<String> nextLines) {
        if (removed || nextLocation == null) return;
        try {
            List<String> normalized = HologramText.normalize(nextLines);
            if (!sameLocation(location, nextLocation)) {
                provider.move(name, nextLocation, movementTicks);
                location = nextLocation.clone();
            }
            if (!lines.equals(normalized)) {
                provider.updateText(name, normalized);
                lines = List.copyOf(normalized);
            }
        } catch (Throwable failure) {
            failureHandler.accept("update animation text " + name, failure);
        }
    }

    @Override
    public void remove() {
        if (removed) return;
        removed = true;
        removeAction.run();
    }

    private boolean sameLocation(Location first, Location second) {
        return first.getWorld() == second.getWorld()
                && first.toVector().distanceSquared(second.toVector()) < 0.000001;
    }
}
