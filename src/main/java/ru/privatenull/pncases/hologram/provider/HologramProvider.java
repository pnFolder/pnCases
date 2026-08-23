package ru.privatenull.pncases.hologram.provider;

import org.bukkit.Location;
import ru.privatenull.pncases.hologram.HologramDefinition;

import java.util.List;

/** Minimal provider contract consumed by pnCases. */
public interface HologramProvider {
    boolean isAvailable();

    void create(HologramDefinition definition);

    void remove(String name);

    void move(String name, Location location, int movementTicks);

    void updateText(String name, List<String> lines);
}
