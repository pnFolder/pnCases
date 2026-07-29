package ru.privatenull.cases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaseConfigRepositoryTest {

    @Test
    void newCaseHasItsOwnKeyAndMinimalAnimationSettings() {
        YamlConfiguration target = new YamlConfiguration();
        CaseConfigRepository.writeNewCaseDefaults(target, "donate");

        assertFalse(target.contains("id"));
        assertEquals("&x&4&2&9&F&9&1donate", target.getString("display-name"));
        assertTrue(target.contains("blocks"));
        assertFalse(target.contains("template"));
        assertFalse(target.contains("cost.type"));
        assertFalse(target.contains("cost.key"));
        assertFalse(target.contains("cost.amount"));
        assertFalse(target.getBoolean("cost.xp-purchase.enabled"));
        assertEquals(0, target.getInt("cost.xp-purchase.levels"));
        assertFalse(target.contains("cost.buy_xp_enabled"));
        assertFalse(target.contains("cost.buy_xp_levels"));
        assertTrue(target.getBoolean("gui.preview_enabled"));
        assertTrue(target.isConfigurationSection("gui.button"));
        assertEquals("{display-name}", target.getString("gui.button.name"));
        assertEquals("&#FF5A64Недостаточно ключей",
                target.getString("gui.insufficient-keys.name"));
        assertTrue(target.getStringList("gui.insufficient-keys.lore").contains(
                " &7- &fДоступно: &#FCA865{have}"));
        assertFalse(target.contains("gui.open-item"));
        assertTrue(target.getBoolean("broadcast.enabled"));
        assertTrue(target.getStringList("broadcast.lines").stream()
                .anyMatch(line -> line.contains("{player}") && line.contains("{case}")));
        assertFalse(target.contains("animation.duration_ticks"));
        assertFalse(target.contains("animation.cycle_every_ticks"));
        assertFalse(target.contains("animation.rise_blocks"));
        assertFalse(target.contains("animation.spin_degrees_per_tick"));
        assertFalse(target.contains("animation.items"));
        assertFalse(target.contains("idle-particles.interval_ticks"));
        assertFalse(target.contains("idle-particles.radius"));
        assertFalse(target.contains("idle-particles.height"));
        assertFalse(target.contains("idle-particles.speed"));
        assertFalse(target.contains("idle-particles.effects"));
        assertFalse(target.contains("animation.fixed"));
        assertFalse(target.contains("gui.animation-item"));
        assertFalse(target.contains("gui.history.empty-item"));
        assertTrue(target.getIntegerList("gui.history.slots").contains(50));
        assertEquals("{display-name}", target.getStringList("hologram.lines").get(0));
        assertEquals("ITEM", target.getMapList("rewards").get(0).get("type"));
    }

    @Test
    void legacyOpenItemMigratesToCaseOwnedButton() {
        YamlConfiguration target = new YamlConfiguration();
        ConfigurationSection gui = target.createSection("gui");
        ConfigurationSection legacy = gui.createSection("open-item");
        legacy.set("material", "ENDER_CHEST");
        legacy.set("name", "&dСвой кейс");
        legacy.set("lore", List.of("&7Свой lore"));

        assertTrue(CaseConfigRepository.migrateCaseGui(target));
        assertFalse(target.contains("gui.open-item"));
        assertEquals("ENDER_CHEST", target.getString("gui.button.material"));
        assertEquals("&dСвой кейс", target.getString("gui.button.name"));
        assertEquals(List.of("&7Свой lore"), target.getStringList("gui.button.lore"));
        assertFalse(CaseConfigRepository.migrateCaseGui(target));
    }

    @Test
    void legacyXpPurchaseFieldsMigrateToNestedSection() {
        YamlConfiguration target = new YamlConfiguration();
        target.set("cost.type", "KEY");
        target.set("cost.key", "donate");
        target.set("cost.amount", 2);
        target.set("cost.buy_xp_enabled", true);
        target.set("cost.buy_xp_levels", 15);

        assertTrue(CaseConfigRepository.migrateCaseGui(target));
        assertTrue(target.getBoolean("cost.xp-purchase.enabled"));
        assertEquals(15, target.getInt("cost.xp-purchase.levels"));
        assertFalse(target.contains("cost.type"));
        assertFalse(target.contains("cost.key"));
        assertFalse(target.contains("cost.amount"));
        assertFalse(target.contains("cost.buy_xp_enabled"));
        assertFalse(target.contains("cost.buy_xp_levels"));
        assertFalse(CaseConfigRepository.migrateCaseGui(target));
    }

    @Test
    void legacyEmptyHistoryItemMigratesWithoutBeingAddedToNewCases() {
        YamlConfiguration target = new YamlConfiguration();
        ConfigurationSection gui = target.createSection("gui");
        ConfigurationSection button = gui.createSection("button");
        button.set("material", "CHEST");
        button.set("name", "{display-name}");
        button.set("lore", List.of("&7Кейс"));
        ConfigurationSection history = gui.createSection("history");
        ConfigurationSection legacy = history.createSection("empty-item");
        legacy.set("material", "GRAY_DYE");
        legacy.set("name", "&8Пусто");

        assertTrue(CaseConfigRepository.migrateCaseGui(target));
        assertFalse(target.contains("gui.history.empty-item"));
        assertEquals("GRAY_DYE", target.getString("gui.history.empty.material"));
        assertEquals("&8Пусто", target.getString("gui.history.empty.name"));
    }

    @Test
    void savedSlotListsAreCompactedToYamlFlowStyle(@TempDir Path directory) throws Exception {
        Path file = directory.resolve("donate.yml");
        Files.writeString(file, """
                gui:
                  decor:
                    slots:
                    - 0
                    - 1
                    - 2
                  history:
                    slots:
                    - 45
                    - 46
                """, StandardCharsets.UTF_8);

        CaseConfigRepository.compactIntegerSlotLists(file);

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.contains("    slots: [0, 1, 2]"));
        assertTrue(saved.contains("    slots: [45, 46]"));

        YamlConfiguration readback = new YamlConfiguration();
        readback.load(file.toFile());
        assertEquals(List.of(0, 1, 2), readback.getIntegerList("gui.decor.slots"));
        assertEquals(List.of(45, 46), readback.getIntegerList("gui.history.slots"));
    }

    @Test
    void caseFormatterAddsSectionsPlaceholdersAndFlowMaps(@TempDir Path directory) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        CaseConfigRepository.writeNewCaseDefaults(yaml, "donate");
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("world", "world");
        block.put("x", 1);
        block.put("y", 64);
        block.put("z", 2);
        yaml.set("blocks", List.of(block));
        Path file = directory.resolve("donate.yml");
        yaml.save(file.toFile());

        CaseConfigRepository.formatCaseYaml(file);

        String saved = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(saved.startsWith("# pnCases — конфигурация кейса"));
        assertTrue(saved.contains("# {display-name} — цветное название кейса"));
        assertTrue(saved.contains("# {case_id} — ID кейса"));
        assertTrue(saved.contains("# {amount} — количество валюты"));
        assertTrue(saved.contains("# Доступные редкости rewards[].rarity:"));
        assertTrue(saved.contains("# COMMON — Обычная."));
        assertTrue(saved.contains("# MYTHIC — Мифическая."));
        assertTrue(saved.contains("gui.yml -> preview.rarities"));
        assertTrue(saved.contains("- {world: world, x: 1, y: 64, z: 2}"));
        assertTrue(saved.contains("idle-particles:"));
        assertTrue(saved.contains("  enabled: true"));
        assertFalse(saved.contains("idle-particles: {"));
        assertTrue(saved.contains("cost:"));
        assertTrue(saved.contains("  xp-purchase:"));
        assertFalse(saved.contains("  type: KEY"));
        assertFalse(saved.contains("  key: donate"));
        assertFalse(saved.contains("cost: {"));
        assertTrue(saved.contains("slots: [45, 46, 47, 48, 50, 51, 52, 53]"));
        assertFalse(saved.contains("\n# Голограмма\n"));
        assertFalse(saved.contains("\n# Меню\n"));

        CaseConfigRepository.formatCaseYaml(file);
        String savedAgain = Files.readString(file, StandardCharsets.UTF_8);
        assertEquals(savedAgain.indexOf("# pnCases — конфигурация кейса"),
                savedAgain.lastIndexOf("# pnCases — конфигурация кейса"));

        YamlConfiguration readback = new YamlConfiguration();
        readback.load(file.toFile());
        assertFalse(readback.contains("id"));
        assertEquals(50, readback.getIntegerList("gui.history.slots").get(4));
        assertEquals("world", readback.getMapList("blocks").get(0).get("world"));
    }

    @Test
    void fileNameIsTheCanonicalCaseId() {
        assertEquals("money", CaseConfigRepository.fileId("Money.yml"));
        assertEquals("custom_case", CaseConfigRepository.fileId("custom_case.YML"));
        assertTrue(CaseConfigRepository.isValidId(CaseConfigRepository.fileId("safe-case.yml")));
    }

    @Test
    void generatedGuiTitleFollowsCaseDisplayName() {
        assertEquals("&dДонат", CaseDefinitionLoader.resolveGuiTitle("&dДонат", "{display-name}"));
        assertEquals("&8Магазин", CaseDefinitionLoader.resolveGuiTitle("&dДонат", "&8Магазин"));
    }

    @Test
    void explicitGuiSizeIsNormalized() {
        YamlConfiguration gui = new YamlConfiguration();
        gui.set("size", 45);

        assertEquals(45, CaseViewConfigParser.resolveSize(gui));
    }

    @Test
    void casePreviewCanBeDisabledPerCaseAndDefaultsToEnabled() {
        YamlConfiguration gui = new YamlConfiguration();

        assertTrue(CaseViewConfigParser.previewEnabled(gui));

        gui.set("preview_enabled", false);
        assertFalse(CaseViewConfigParser.previewEnabled(gui));
    }

    @Test
    void rollbackRestoresNestedYamlSectionsInsteadOfPlainMaps() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("id", "rollback_case");
        yaml.set("gui.title", "Custom GUI");
        yaml.set("cost.type", "KEY");
        yaml.set("cost.key", "rollback_key");
        yaml.set("animation.fixed", "AQUARIUM");
        yaml.set("rewards", List.of(Map.of("type", "ITEM", "chance", 100)));

        Map<String, Object> snapshot = CaseConfigRepository.snapshotSection(yaml);
        for (String key : List.copyOf(yaml.getKeys(false))) yaml.set(key, null);
        CaseConfigRepository.restoreSection(yaml, snapshot);

        assertTrue(yaml.isConfigurationSection("gui"));
        assertTrue(yaml.isConfigurationSection("cost"));
        assertTrue(yaml.isConfigurationSection("animation"));
        assertEquals("Custom GUI", yaml.getString("gui.title"));
        assertEquals("rollback_key", yaml.getString("cost.key"));
        assertEquals("AQUARIUM", yaml.getString("animation.fixed"));
        assertEquals("ITEM", yaml.getMapList("rewards").get(0).get("type"));
    }

}
