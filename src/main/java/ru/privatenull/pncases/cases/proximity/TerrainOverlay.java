package ru.privatenull.pncases.cases.proximity;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import ru.privatenull.pncases.cases.model.ProximitySettings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;
import java.util.UUID;

/**
 * Turns the whole area around a case into a winter landscape, but only on the screens of nearby
 * players. Ground, walls, roofs, stairs, slabs, fences and leaves get their snowy counterparts, plants
 * are buried under snow and the case block itself can be replaced by a model. The world is never
 * modified: every change is a block-change packet that is reverted when the scene fades or the player
 * walks away. Only blocks that keep the shape and friction of the original are used, so movement stays
 * in sync with the server.
 */
final class TerrainOverlay {

    private static final int MAX_CELLS = 6000;
    private static final int REFRESH_TICKS = 60;
    /** The case block is resent on every update: opening animations hide and restore the real block. */
    private static final int CASE_REFRESH_TICKS = 2;

    private final List<Cell> cells = new ArrayList<>();
    private final Set<UUID> viewerIds = new HashSet<>();
    private World world;
    private long age;

    private final TerrainPalette palette;
    private ProximitySettings.Terrain settings;
    private SplittableRandom random;
    private Set<Long> used;
    private int totalWeight;
    private int caseX;
    private int caseY;
    private int caseZ;
    private boolean caseHidden;
    private boolean groundBuilt;

    TerrainOverlay(TerrainPalette palette) {
        this.palette = palette;
    }

    /**
     * @param ground   whether the surroundings are converted
     * @param caseCell whether the case block itself is replaced
     */
    void build(World world, Location caseBlock, ProximitySettings.Terrain settings, SplittableRandom random,
               boolean ground, boolean caseCell) {
        this.world = world;
        this.settings = settings;
        this.random = random;
        this.used = new HashSet<>();
        this.totalWeight = settings.ground().stream().mapToInt(ProximitySettings.Weighted::weight).sum();

        int cx = caseBlock.getBlockX();
        int cy = caseBlock.getBlockY();
        int cz = caseBlock.getBlockZ();
        this.caseX = cx;
        this.caseY = cy;
        this.caseZ = cz;
        // A model or an invisible barrier takes the place of the case block, so it must not count as solid.
        this.caseHidden = settings.caseStyle() == ProximitySettings.CaseStyle.GIFT
                || settings.caseStyle() == ProximitySettings.CaseStyle.GLOBE
                || settings.caseStyle() == ProximitySettings.CaseStyle.CRYSTAL
                || settings.caseStyle() == ProximitySettings.CaseStyle.BASKET;

        BlockData caseData = caseCell ? caseData() : null;
        if (caseData != null) {
            Cell cell = new Cell(cx, cy, cz, caseData, 0.15);
            cell.priority = true;
            cells.add(cell);
            used.add(key(cx, cy, cz));
        }
        if (!ground || !settings.enabled()) {
            used = null;
            return;
        }
        groundBuilt = true;

        int radius = settings.radius();
        int up = Math.min(8, radius + 2);
        int down = 4;
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 2;

        for (int dx = -radius; dx <= radius && cells.size() < MAX_CELLS; dx++) {
            for (int dz = -radius; dz <= radius && cells.size() < MAX_CELLS; dz++) {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > radius + 0.4) continue;
                double edge = Math.min(1.0, (radius + 1.0 - distance) / 2.5);
                int x = cx + dx;
                int z = cz + dz;
                if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
                double ripple = 0.1 + (radius == 0 ? 0.0 : distance / (radius + 0.5)) * 0.55;

                for (int y = Math.min(maxY, cy + up); y >= Math.max(minY, cy - down); y--) {
                    if (used.contains(key(x, y, z))) continue;
                    if (random.nextDouble() > edge) continue;
                    convert(world.getBlockAt(x, y, z), cx, cy, cz, ripple + Math.abs(y - cy) * 0.008);
                }
            }
        }
        used = null;
    }

    private BlockData caseData() {
        return switch (settings.caseStyle()) {
            case NONE -> null;
            case BLOCK -> settings.caseBlock() == null ? null : settings.caseBlock().createBlockData();
            // An invisible block keeps the spot clickable while a display model stands in its place.
            case GIFT, GLOBE, CRYSTAL, BASKET -> Material.BARRIER.createBlockData();
        };
    }

    // -------------------------------------------------------------- mapping

    private void convert(Block block, int cx, int cy, int cz, double threshold) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid() || palette.isCover(type)) return;
        if (block.getX() == cx && block.getY() == cy && block.getZ() == cz) return;
        if (block.getState(false) instanceof TileState) return;

        String name = type.name();
        if (palette.keeps(name)) return;

        int x = block.getX();
        int y = block.getY();
        int z = block.getZ();
        Block above = block.getRelative(0, 1, 0);

        if (block.isReplaceable()) {
            // Grass, flowers, ferns and other plants disappear under the snow.
            Block below = block.getRelative(0, -1, 0);
            if (isSolidGround(below) && !used.contains(key(x, y, z))) {
                add(x, y, z, snowLayer(), threshold + 0.05);
            } else {
                add(x, y, z, Material.AIR.createBlockData(), threshold);
            }
            return;
        }

        boolean topFree = isFreeAbove(above);
        if (type.isOccluding()) {
            boolean exposed = topFree || hasOpenSide(block);
            if (!exposed) return;
            if (topFree) {
                add(x, y, z, pickGround(), threshold);
                addSnowAbove(x, y, z, above, threshold + 0.05);
            } else if (random.nextDouble() < settings.sideCoverage()) {
                add(x, y, z, pickGround(), threshold);
            }
            return;
        }

        if (name.equals("DIRT_PATH") || name.equals("FARMLAND") || name.equals("GRASS_PATH")) {
            add(x, y, z, pickGround(), threshold);
            addSnowAbove(x, y, z, above, threshold + 0.05);
            return;
        }

        if (!settings.morphShapes()) return;
        BlockData morphed = morph(block, name);
        if (morphed == null) return;
        add(x, y, z, morphed, threshold);
        if (topFree && (name.endsWith("_LEAVES") || name.endsWith("_SLAB") || name.endsWith("_STAIRS"))) {
            addSnowAbove(x, y, z, above, threshold + 0.05);
        }
    }

    /** The same shape in the scene's material: stairs, slabs, walls and fences keep their facing and state. */
    private BlockData morph(Block block, String name) {
        TerrainPalette.Morph morph = palette.morph(name, random);
        if (morph == null) return null;
        String target = morph.target();
        if (block.getType().name().toLowerCase(Locale.ROOT).equals(target.substring(10))) return null;

        String original = block.getBlockData().getAsString();
        int state = original.indexOf('[');
        try {
            return Bukkit.createBlockData(target + (!morph.keepState() || state < 0 ? "" : original.substring(state)));
        } catch (IllegalArgumentException incompatible) {
            return null;
        }
    }
    private boolean isOpaque(Block block) {
        if (caseHidden && block.getX() == caseX && block.getY() == caseY && block.getZ() == caseZ) return false;
        return block.getType().isOccluding();
    }

    private boolean isFreeAbove(Block above) {
        if (caseHidden && above.getX() == caseX && above.getY() == caseY && above.getZ() == caseZ) return true;
        Material type = above.getType();
        if (above.isLiquid()) return false;
        return type.isAir() || above.isReplaceable() || !type.isOccluding();
    }

    private boolean hasOpenSide(Block block) {
        return !isOpaque(block.getRelative(1, 0, 0))
                || !isOpaque(block.getRelative(-1, 0, 0))
                || !isOpaque(block.getRelative(0, 0, 1))
                || !isOpaque(block.getRelative(0, 0, -1))
                || !isOpaque(block.getRelative(0, -1, 0));
    }

    private boolean isSolidGround(Block block) {
        return block.getType().isOccluding() && !block.isLiquid();
    }

    private void addSnowAbove(int x, int y, int z, Block above, double threshold) {
        if (!settings.snowLayer()) return;
        if (above.getX() == caseX && above.getY() == caseY && above.getZ() == caseZ) return;
        if (!(above.getType().isAir() || above.isReplaceable()) || above.isLiquid()) return;
        if (used.contains(key(x, y + 1, z))) return;
        add(x, y + 1, z, snowLayer(), threshold);
    }

    /**
     * How high the thin cover on the ground is, so decorations stand on it and not inside it. Only the
     * winter snow layer is tall enough to matter; the autumn litter is flat.
     */
    double coverHeight() {
        return groundBuilt && palette.isWinter() && settings.snowLayer() ? 0.125 : 0.0;
    }

    private BlockData snowLayer() {
        return palette.cover();
    }

    private BlockData pickGround() {
        int roll = random.nextInt(Math.max(1, totalWeight));
        for (ProximitySettings.Weighted entry : settings.ground()) {
            roll -= entry.weight();
            if (roll < 0) return entry.material().createBlockData();
        }
        return settings.ground().get(0).material().createBlockData();
    }

    private void add(int x, int y, int z, BlockData fake, double threshold) {
        if (cells.size() >= MAX_CELLS || !used.add(key(x, y, z))) return;
        cells.add(new Cell(x, y, z, fake, Math.min(0.95, threshold + random.nextDouble() * 0.05)));
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    // --------------------------------------------------------------- viewers

    void update(double progress, List<Player> nearby) {
        if (cells.isEmpty()) return;
        age += 2;

        Set<UUID> current = new HashSet<>();
        for (Player player : nearby) {
            if (player.getWorld().equals(world)) current.add(player.getUniqueId());
        }

        for (UUID id : new ArrayList<>(viewerIds)) {
            if (!current.contains(id)) {
                Player gone = Bukkit.getPlayer(id);
                if (gone != null) revert(gone);
                viewerIds.remove(id);
            }
        }

        List<Player> joined = new ArrayList<>();
        for (Player player : nearby) {
            if (current.contains(player.getUniqueId()) && viewerIds.add(player.getUniqueId())) {
                joined.add(player);
            }
        }
        Set<UUID> joinedIds = new HashSet<>();
        for (Player player : joined) joinedIds.add(player.getUniqueId());

        for (Cell cell : cells) {
            boolean want = progress >= cell.threshold;
            if (want != cell.shown) {
                cell.shown = want;
                for (UUID id : viewerIds) {
                    if (!joinedIds.contains(id)) send(Bukkit.getPlayer(id), cell, want);
                }
            }
        }

        for (Player player : joined) {
            for (Cell cell : cells) {
                if (cell.shown) send(player, cell, true);
            }
        }

        boolean full = age % REFRESH_TICKS == 0;
        boolean caseOnly = age % CASE_REFRESH_TICKS == 0;
        if (full || caseOnly) {
            for (UUID id : viewerIds) {
                Player player = Bukkit.getPlayer(id);
                if (player == null) continue;
                for (Cell cell : cells) {
                    if (cell.shown && (full || cell.priority)) send(player, cell, true);
                }
            }
        }
    }

    /** Puts the fake blocks around a clicked position back on a player's screen after the server reset them. */
    void resendNear(Player player, Location clicked) {
        if (player == null || !viewerIds.contains(player.getUniqueId())) return;
        for (Cell cell : cells) {
            if (cell.shown && Math.abs(cell.x - clicked.getBlockX()) <= 2
                    && Math.abs(cell.y - clicked.getBlockY()) <= 2
                    && Math.abs(cell.z - clicked.getBlockZ()) <= 2) {
                send(player, cell, true);
            }
        }
    }

    void clear() {
        for (UUID id : viewerIds) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) revert(player);
        }
        viewerIds.clear();
        cells.clear();
    }

    private void revert(Player player) {
        if (!player.getWorld().equals(world)) return;
        for (Cell cell : cells) {
            if (cell.shown) send(player, cell, false);
        }
    }

    private void send(Player player, Cell cell, boolean fake) {
        if (player == null || !player.isOnline() || !player.getWorld().equals(world)) return;
        if (!world.isChunkLoaded(cell.x >> 4, cell.z >> 4)) return;
        BlockData data = fake ? cell.fake : world.getBlockAt(cell.x, cell.y, cell.z).getBlockData();
        player.sendBlockChange(new Location(world, cell.x, cell.y, cell.z), data);
    }

    private static final class Cell {
        private final int x;
        private final int y;
        private final int z;
        private final BlockData fake;
        private final double threshold;
        private boolean shown;
        private boolean priority;

        private Cell(int x, int y, int z, BlockData fake, double threshold) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.fake = fake;
            this.threshold = threshold;
        }
    }
}
