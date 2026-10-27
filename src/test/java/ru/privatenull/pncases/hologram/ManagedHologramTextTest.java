package ru.privatenull.pncases.hologram;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.hologram.provider.HologramProvider;

import static org.junit.jupiter.api.Assertions.*;

class ManagedHologramTextTest {
    @Test
    void everyOrbitFrameIsForwardedWithoutWaitingForProviderTimer() {
        RecordingProvider provider = new RecordingProvider();
        ManagedHologramText text = label(provider);
        for (int frame = 1; frame <= 20; frame++) {
            double angle = frame * 0.06;
            Location position = new Location(null, Math.cos(angle) * 2.3, Math.sin(angle) * 2.3, 0);
            text.update(position, "Награда");
            assertEquals(frame, provider.positions.size());
            assertEquals(position, provider.positions.get(frame - 1));
            assertEquals(1, provider.movementTicks);
        }
        assertEquals(0, provider.textUpdates);
    }

    @Test
    void unchangedTextIsNotRebuiltAndRemovedLabelDoesNotSendFrames() {
        RecordingProvider provider = new RecordingProvider();
        ManagedHologramText text = label(provider);
        Location position = new Location(null, 1, 2, 3);
        text.update(position, "Награда");
        text.update(position, "Награда");
        assertEquals(1, provider.positions.size());
        text.update(position, "Новая награда");
        assertEquals(1, provider.textUpdates);
        text.remove();
        text.remove();
        text.update(new Location(null, 3, 2, 1), "После удаления");
        assertEquals(1, provider.positions.size());
        assertEquals(1, provider.removals);
        assertEquals(1, provider.textUpdates);
    }

    @Test
    void transientLabelsDoNotAlsoRunAnIndependentTextRefreshTimer() {
        var definition = HologramDefinition.animationText("label", new Location(null, 0, 0, 0),
                List.of("Награда"), new HologramTextStyle(0.86f, 48, 15, 1, 1));
        assertEquals(0, definition.textUpdateInterval());
        assertEquals(1, definition.interpolationDuration());
        assertEquals(List.of("Награда"), definition.lines());
    }

    private static ManagedHologramText label(RecordingProvider provider) {
        return new ManagedHologramText("label", provider, new Location(null, 0, 0, 0),
                List.of("Награда"), 1, () -> provider.remove("label"),
                (message, failure) -> fail(message, failure));
    }

    private static final class RecordingProvider implements HologramProvider {
        final List<Location> positions = new ArrayList<>();
        int movementTicks;
        int textUpdates;
        int removals;
        @Override public boolean isAvailable() { return true; }
        @Override public void create(HologramDefinition definition) { }
        @Override public void remove(String name) { removals++; }
        @Override public void move(String name, Location location, int ticks) {
            positions.add(location.clone());
            movementTicks = ticks;
        }
        @Override public void updateText(String name, List<String> lines) { textUpdates++; }
    }
}
