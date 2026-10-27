package ru.privatenull.pncases.cases.proximity;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.util.List;

/** One ambient scene instance bound to a single case block. */
interface ProximityScene {

    /** Creates the scene around {@code block}, the case block. */
    void spawn(World world, Location block);

    /**
     * Advances the scene by two ticks.
     *
     * @param progress 0 (hidden) to 1 (fully shown)
     * @param rising   true while a player is near and the scene is growing
     * @param viewers  players inside the trigger radius
     */
    void update(double progress, boolean rising, List<Player> viewers);

    /**
     * Brings a freshly built scene to {@code progress} at once and without sounds, so a rebuild after a
     * settings change replaces the old scene without any visible growth or jump.
     */
    void snap(double progress, boolean rising, List<Player> viewers);

    /** Resends the fake blocks around a block the player just clicked, which the server overwrote with the real ones. */
    void refresh(Player player, Location clicked);

    /** False once any scene entity was removed from outside, for example by a chunk unload. */
    boolean valid();

    /** Removes the scene; {@code silent} skips the vanishing sound. */
    void clear(boolean silent);
}
