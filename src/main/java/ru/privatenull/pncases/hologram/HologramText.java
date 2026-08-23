package ru.privatenull.pncases.hologram;

import org.bukkit.Location;

import java.util.List;

/** Mutable short-lived text owned by an animation session. */
public interface HologramText {
    HologramText EMPTY = new HologramText() {
        @Override public void update(Location location, List<String> lines) { }
        @Override public void remove() { }
    };

    void update(Location location, List<String> lines);

    default void update(Location location, String text) {
        update(location, normalize(text == null ? List.of(" ") : text.lines().toList()));
    }

    void remove();

    static List<String> normalize(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of(" ");
        return lines.stream().map(line -> line == null || line.isEmpty() ? " " : line).toList();
    }
}
