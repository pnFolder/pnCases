package ru.privatenull.pncases.cases.opening;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.Locale;

/** Stable, world-aware key for one physical case block. */
public record CaseBlockKey(String world, int x, int y, int z) {
    public CaseBlockKey {
        world = world == null ? "" : world.toLowerCase(Locale.ROOT);
    }

    public static CaseBlockKey of(Block block) {
        return new CaseBlockKey(block.getWorld().getName(), block.getX(), block.getY(), block.getZ());
    }

    public static CaseBlockKey of(Location location) {
        return new CaseBlockKey(location.getWorld().getName(),
                location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
}
