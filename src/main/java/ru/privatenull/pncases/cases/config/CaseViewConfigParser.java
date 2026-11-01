package ru.privatenull.pncases.cases.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.model.CaseGuiLayout;
import ru.privatenull.pncases.cases.model.CaseGuiLayoutRules;
import ru.privatenull.pncases.cases.model.IdleParticleSettings;
import ru.privatenull.pncases.cases.model.ProximitySettings;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.item.ItemFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class CaseViewConfigParser {

    CaseGuiLayout parseLayout(ConfigurationSection gui) {
        CaseGuiLayout defaults = CaseGuiLayout.defaults();
        if (gui == null) return defaults;

        int size = resolveSize(gui);

        int openSlot = gui.getInt("open_slot", defaults.openSlot());
        int animationSlot = gui.getInt("animation_slot", defaults.animationSlot());

        ConfigurationSection decor = gui.getConfigurationSection("decor");
        ConfigurationSection history = gui.getConfigurationSection("history");
        CaseGuiLayoutRules.ResolvedSlots slots = CaseGuiLayoutRules.resolveSlots(
                size,
                openSlot, defaults.openSlot(),
                animationSlot, defaults.animationSlot(),
                readSlots(history, defaults.historySlots(), "slots"),
                readSlots(decor, defaults.decorSlots(), "slots")
        );
        return new CaseGuiLayout(
                size,
                slots.openSlot(),
                slots.animationSlot(),
                previewEnabled(gui),
                slots.historySlots(),
                readItem(history, "empty", defaults.emptyHistoryItem(), Material.BARRIER, "&8История пуста"),
                slots.decorSlots(),
                readItem(decor, "item", null, Material.GRAY_STAINED_GLASS_PANE, " ")
        );
    }

    static int resolveSize(ConfigurationSection gui) {
        if (gui == null) return CaseGuiLayout.DEFAULT_SIZE;
        return CaseGuiLayoutRules.normalizeSize(gui.getInt("size", CaseGuiLayout.DEFAULT_SIZE));
    }

    static boolean previewEnabled(ConfigurationSection gui) {
        return gui == null || gui.getBoolean("preview_enabled", true);
    }

    IdleParticleSettings parseIdleParticles(ConfigurationSection section) {
        IdleParticleSettings defaults = IdleParticleSettings.defaults();
        if (section == null) return defaults;

        return new IdleParticleSettings(
                section.getBoolean("enabled", defaults.enabled()),
                enumValue(section.getString("theme"), defaults.theme()),
                Math.max(2, section.getInt("interval_ticks", defaults.intervalTicks())),
                clamp(section.getDouble("radius", defaults.radius()), 0.25, 2.50),
                clamp(section.getDouble("height", defaults.height()), 0.30, 3.00),
                clamp(section.getDouble("speed", defaults.speed()), 0.02, 0.80),
                clamp(section.getDouble("view_distance", defaults.viewDistance()), 4.0, 64.0)
        );
    }

    ProximitySettings parseProximity(ConfigurationSection section) {
        if (section == null) return ProximitySettings.defaults();

        ProximitySettings.Scene scene = ProximitySettings.defaults().scene();
        String raw = section.getString("scene");
        if (raw != null && !raw.isBlank()) {
            try {
                scene = ProximitySettings.Scene.valueOf(normalizeEnum(raw));
            } catch (IllegalArgumentException ignored) {
                // Unknown scene names keep the default scene.
            }
        }

        // Each scene brings its own defaults, so the scene is read before anything else.
        ProximitySettings d = ProximitySettings.defaults(scene);

        ConfigurationSection music = section.getConfigurationSection("music");
        ConfigurationSection sounds = section.getConfigurationSection("sounds");
        ConfigurationSection particles = section.getConfigurationSection("particles");
        ConfigurationSection decor = section.getConfigurationSection("decor");
        ConfigurationSection santa = section.getConfigurationSection(
                section.isConfigurationSection("santa") ? "santa" : "scarecrow");
        ConfigurationSection terrain = section.getConfigurationSection("terrain");

        ProximitySettings.Music dm = d.music();
        ProximitySettings.Sounds ds = d.sounds();
        ProximitySettings.Particles dp = d.particles();
        ProximitySettings.Decor dd = d.decor();
        ProximitySettings.Santa dsa = d.santa();
        ProximitySettings.Terrain dt = d.terrain();

        return new ProximitySettings(
                section.getBoolean("enabled", d.enabled()),
                scene,
                clamp(section.getDouble("radius", d.radius()), 3.0, 32.0),
                clamp(section.getDouble("spread", d.spread()), 0.6, 2.0),
                Math.floorMod(Math.round(section.getInt("rotation", d.rotation()) / 90.0) * 90, 360),
                clamp(section.getDouble("fade_seconds", d.fadeSeconds()), 0.5, 15.0),
                new ProximitySettings.Music(
                        music == null ? dm.enabled() : music.getBoolean("enabled", dm.enabled()),
                        music == null ? dm.volume() : clamp(music.getDouble("volume", dm.volume()), 0.0, 1.0),
                        music == null ? dm.pauseSeconds() : clamp(music.getDouble("pause_seconds", dm.pauseSeconds()), 0.0, 120.0)),
                new ProximitySettings.Sounds(
                        sounds == null ? ds.enabled() : sounds.getBoolean("enabled", ds.enabled()),
                        sounds == null ? ds.volume() : clamp(sounds.getDouble("volume", ds.volume()), 0.0, 2.0)),
                new ProximitySettings.Particles(
                        particles == null ? dp.density() : clamp(particles.getDouble("density", dp.density()), 0.0, 3.0),
                        particles == null ? dp.snowfall() : particles.getBoolean("snowfall", dp.snowfall()),
                        particles == null ? dp.swirl() : particles.getBoolean("swirl", dp.swirl()),
                        particles == null ? dp.frostRing() : particles.getBoolean("frost_ring", dp.frostRing()),
                        particles == null ? dp.glints() : particles.getBoolean("glints", dp.glints()),
                        particles == null ? dp.aurora() : particles.getBoolean("aurora", dp.aurora())),
                new ProximitySettings.Decor(
                        count(decor, key(decor, "mounds", "leaf_piles"), dd.mounds(), 30),
                        count(decor, key(decor, "trees", "maples"), dd.trees(), 12),
                        count(decor, key(decor, "snowmen", "pumpkins"), dd.snowmen(), 6),
                        count(decor, key(decor, "crystals", "mushrooms"), dd.crystals(), 10),
                        count(decor, key(decor, "garland", "lanterns"), dd.garland(), 40),
                        count(decor, key(decor, "snowflakes", "leaves"), dd.snowflakes(), 30),
                        count(decor, key(decor, "gifts", "hay"), dd.gifts(), 16)),
                new ProximitySettings.Santa(
                        santa == null ? dsa.enabled() : santa.getBoolean("enabled", dsa.enabled()),
                        santa == null ? dsa.coat() : block(santa.getString("coat"), dsa.coat()),
                        santa != null && santa.isList("messages") ? santa.getStringList("messages") : dsa.messages()),
                new ProximitySettings.Terrain(
                        terrain == null ? dt.enabled() : terrain.getBoolean("enabled", dt.enabled()),
                        terrain == null ? dt.radius() : count(terrain, "radius", dt.radius(), 12),
                        terrain == null ? dt.caseStyle() : caseStyle(terrain.getString("case_style"), dt.caseStyle()),
                        terrain == null ? dt.caseColor() : block(terrain.getString("case_color"), dt.caseColor()),
                        terrain == null ? dt.caseBlock() : caseBlock(terrain.getString("case_block", dt.caseBlock() == null ? "none" : dt.caseBlock().name()), dt.caseBlock()),
                        terrain == null ? dt.caseScale() : clamp(terrain.getDouble("case_scale", dt.caseScale()), 0.5, 2.0),
                        terrain == null ? dt.keepCase() : terrain.getBoolean("keep_case", dt.keepCase()),
                        terrain == null ? dt.keepDuringOpening() : terrain.getBoolean("keep_during_opening", dt.keepDuringOpening()),
                        terrain == null ? dt.ground() : ground(terrain.getStringList("ground"), dt.ground()),
                        terrain == null ? dt.snowLayer() : terrain.getBoolean(key(terrain, "snow_layer", "leaf_layer"), dt.snowLayer()),
                        terrain == null ? dt.allBlocks() : terrain.getBoolean("replace_all_blocks", dt.allBlocks()),
                        terrain == null ? dt.morphShapes() : terrain.getBoolean("morph_shapes", dt.morphShapes()),
                        terrain == null ? dt.sideCoverage() : clamp(terrain.getDouble("side_coverage", dt.sideCoverage()), 0.0, 1.0))
        );
    }

    /** The first of two key spellings that is present, so a scene can use its own words for the same option. */
    private static String key(ConfigurationSection section, String primary, String alias) {
        return section != null && !section.contains(primary) && section.contains(alias) ? alias : primary;
    }

    private static int count(ConfigurationSection section, String key, int fallback, int max) {
        if (section == null) return fallback;
        return Math.max(0, Math.min(max, section.getInt(key, fallback)));
    }

    private static Material block(String raw, Material fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        Material material = Material.matchMaterial(raw.trim());
        return material != null && material.isBlock() ? material : fallback;
    }

    private static ProximitySettings.CaseStyle caseStyle(String raw, ProximitySettings.CaseStyle fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return ProximitySettings.CaseStyle.valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private static Material caseBlock(String raw, Material fallback) {
        if (raw == null || raw.isBlank() || raw.equalsIgnoreCase("none") || raw.equalsIgnoreCase("off")) {
            return null;
        }
        return block(raw, fallback);
    }

    private static List<ProximitySettings.Weighted> ground(List<String> raw, List<ProximitySettings.Weighted> fallback) {
        List<ProximitySettings.Weighted> result = new ArrayList<>();
        for (String entry : raw) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.trim().split("[:\s]+");
            Material material = Material.matchMaterial(parts[0]);
            if (material == null || !material.isBlock()) continue;
            int weight = 1;
            if (parts.length > 1) {
                try {
                    weight = Integer.parseInt(parts[1]);
                } catch (NumberFormatException ignored) {
                    // A broken weight falls back to 1.
                }
            }
            if (weight > 0) result.add(new ProximitySettings.Weighted(material, weight));
        }
        return result.isEmpty() ? fallback : result;
    }

    private ItemStack readItem(ConfigurationSection root, String key, ItemStack fallback, Material material, String name) {
        if (root != null) {
            ItemStack item = ItemFactory.fromSection(root.getConfigurationSection(key));
            if (item != null) return item;
        }
        if (fallback != null) return fallback.clone();
        if (material == null) return null;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null && name != null) {
            meta.setDisplayName(ColorUtil.colorize(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private List<Integer> readSlots(ConfigurationSection section, List<Integer> fallback, String key) {
        if (section == null || !section.isList(key)) return fallback;
        List<Integer> result = new ArrayList<>();
        for (Object raw : section.getList(key, Collections.emptyList())) {
            if (raw instanceof Number number) {
                result.add(number.intValue());
            } else {
                try {
                    result.add(Integer.parseInt(String.valueOf(raw)));
                } catch (NumberFormatException ignored) {
                    // Invalid slots are skipped and the remaining layout stays usable.
                }
            }
        }
        return result;
    }

    private IdleParticleSettings.Theme enumValue(String raw, IdleParticleSettings.Theme fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return IdleParticleSettings.Theme.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private String normalizeEnum(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
