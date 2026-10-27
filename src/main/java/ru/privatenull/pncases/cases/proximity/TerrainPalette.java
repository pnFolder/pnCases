package ru.privatenull.pncases.cases.proximity;

import org.bukkit.Material;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Snow;

import java.util.List;
import java.util.SplittableRandom;

/**
 * What a scene turns the surroundings into: which blocks stay as they are, what thin cover lies on the
 * ground and which block a stair, slab, wall, fence or leaf block becomes.
 */
final class TerrainPalette {

    static final TerrainPalette WINTER = new TerrainPalette(true);
    static final TerrainPalette AUTUMN = new TerrainPalette(false);

    /** A replacement: {@code target} is a namespaced block id, {@code keepState} copies stairs facing and the like. */
    record Morph(String target, boolean keepState) {
    }

    private static final List<String> AUTUMN_LEAVES = List.of(
            "minecraft:orange_wool", "minecraft:red_wool", "minecraft:yellow_wool",
            "minecraft:orange_concrete_powder", "minecraft:red_concrete_powder", "minecraft:brown_wool");

    private final boolean winter;

    private TerrainPalette(boolean winter) {
        this.winter = winter;
    }

    /** Blocks that already fit the scene or must never change. */
    boolean keeps(String name) {
        if (name.contains("SLIME") || name.contains("HONEY") || name.contains("SOUL_") || name.equals("BARRIER")) {
            return true;
        }
        if (winter) {
            return name.contains("ICE") || name.equals("SNOW_BLOCK") || name.equals("POWDER_SNOW");
        }
        return name.contains("MUD_BRICK") || name.equals("PODZOL") || name.equals("COARSE_DIRT")
                || name.equals("ROOTED_DIRT") || name.equals("LEAF_LITTER") || name.equals("HAY_BLOCK")
                || name.endsWith("PUMPKIN");
    }

    boolean isWinter() {
        return winter;
    }

    /** A block that is already the scene's thin ground cover, so it needs no cover on top. */
    boolean isCover(Material type) {
        return type == Material.SNOW || (!winter && "LEAF_LITTER".equals(type.name()));
    }

    /** The thin cover that lies on the ground. */
    BlockData cover() {
        if (winter) {
            Snow layer = (Snow) Material.SNOW.createBlockData();
            layer.setLayers(1);
            return layer;
        }
        // Leaf litter exists since 1.21.5; older servers get a flat brown carpet.
        Material litter = Material.matchMaterial("LEAF_LITTER");
        return (litter != null ? litter : Material.BROWN_CARPET).createBlockData();
    }

    /** The replacement for a block with a special shape, or null to leave it alone. */
    Morph morph(String name, SplittableRandom random) {
        if (winter) {
            if (name.endsWith("_STAIRS")) return new Morph("minecraft:quartz_stairs", true);
            if (name.endsWith("_SLAB")) return new Morph("minecraft:quartz_slab", true);
            if (name.endsWith("_WALL")) return new Morph("minecraft:diorite_wall", true);
            if (name.endsWith("_FENCE_GATE")) return new Morph("minecraft:spruce_fence_gate", true);
            if (name.endsWith("_FENCE")) return new Morph("minecraft:spruce_fence", true);
            if (name.endsWith("_LEAVES")) return new Morph("minecraft:spruce_leaves", true);
            return null;
        }
        if (name.endsWith("_STAIRS")) return new Morph("minecraft:mud_brick_stairs", true);
        if (name.endsWith("_SLAB")) return new Morph("minecraft:mud_brick_slab", true);
        if (name.endsWith("_WALL")) return new Morph("minecraft:mud_brick_wall", true);
        if (name.endsWith("_FENCE_GATE")) return new Morph("minecraft:dark_oak_fence_gate", true);
        if (name.endsWith("_FENCE")) return new Morph("minecraft:dark_oak_fence", true);
        // Leaves are full blocks too, so warm solid blocks keep the collision and give the canopy its colour.
        if (name.endsWith("_LEAVES")) return new Morph(AUTUMN_LEAVES.get(random.nextInt(AUTUMN_LEAVES.size())), false);
        return null;
    }
}
