package ru.privatenull.gui.machine;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.cases.model.CaseGuiLayout;
import ru.privatenull.gui.GuiDesign;
import ru.privatenull.util.BukkitCompat.InventoryViewCompat;
import ru.privatenull.util.ServerCompatibility;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

final class MachineGuiSupport {

    static final int SLOT_MAIN_ANIMATION = 11;
    static final int SLOT_MAIN_MENU = 13;
    static final int SLOT_MAIN_HOLOGRAM = 15;
    static final int SLOT_MAIN_PARTICLES = 30;
    static final int SLOT_MAIN_PURCHASE = 32;
    static final int SLOT_MAIN_CLOSE = 49;
    static final int SLOT_ANIMATION_MODE = 13;
    static final int SLOT_HOLOGRAM_TOGGLE = 20;
    static final int SLOT_HOLOGRAM_HEIGHT = 22;
    static final int SLOT_HOLOGRAM_LINES = 24;
    static final int SLOT_PARTICLES_TOGGLE = 20;
    static final int SLOT_PARTICLES_THEME = 24;
    static final int SLOT_XP_BUY = 21;
    static final int SLOT_XP_LEVELS = 23;
    static final int SLOT_GUI_SIZE = 11;
    static final int SLOT_GUI_TITLE = 13;
    static final int SLOT_LAYOUT = 15;
    static final int SLOT_OPEN_ITEM = 20;
    static final int SLOT_DECOR_ITEM = 22;
    static final int SLOT_EMPTY_HISTORY_ITEM = 24;
    static final int SLOT_CONTENT_TOGGLE = 30;
    static final int SLOT_PREVIEW_CASE = 32;
    static final int SLOT_BACK = 49;

    private final CaseManager caseManager;

    MachineGuiSupport(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    static ConfigurationSection section(ConfigurationSection parent, String path) {
        ConfigurationSection existing = parent.getConfigurationSection(path);
        return existing == null ? parent.createSection(path) : existing;
    }

    boolean update(Player player, String caseName, Consumer<ConfigurationSection> updater) {
        return update(player, caseName, updater, false, false);
    }

    boolean updateAnimation(Player player, String caseName, Consumer<ConfigurationSection> updater) {
        return update(player, caseName, updater, false, false);
    }

    boolean updateHologram(Player player, String caseName, Consumer<ConfigurationSection> updater) {
        return update(player, caseName, updater, true, false);
    }

    boolean updateShowcase(Player player, String caseName, Consumer<ConfigurationSection> updater) {
        return update(player, caseName, updater, false, true);
    }

    SlotRole roleAt(CaseDefinition definition, int slot) {
        CaseGuiLayout layout = definition.guiLayout();
        if (slot == layout.openSlot()) return SlotRole.OPEN;
        if (layout.animationSlot() >= 0 && slot == layout.animationSlot()) return SlotRole.ANIMATION;
        if (layout.historySlots().contains(slot)) return SlotRole.HISTORY;
        if (layout.decorSlots().contains(slot)) return SlotRole.DECOR;
        return SlotRole.EMPTY;
    }

    SlotRole stepRole(SlotRole role, boolean backwards) {
        return backwards ? role.previous() : role.next();
    }

    String currentAnimationLabel(CaseDefinition definition) {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            return GuiDesign.animationName(AnimationType.FORTUNE_RING) + " &8(режим 1.16.5)";
        }
        AnimationType fixed = definition.fixedAnimation();
        return fixed == null ? "&aвыбор игрока" : GuiDesign.animationName(fixed);
    }

    double hologramHeight(CaseDefinition definition) {
        ConfigurationSection hologram = caseSection(definition, "hologram");
        return hologram == null ? 1.5
                : round1(hologram.getDouble("y", hologram.getDouble("height", 1.5)));
    }

    List<String> hologramLines(CaseDefinition definition) {
        ConfigurationSection hologram = caseSection(definition, "hologram");
        if (hologram == null) return List.of();
        List<String> lines = hologram.getStringList("lines");
        if (!lines.isEmpty()) return lines;
        String line = hologram.getString("line");
        return line == null || line.isBlank() ? List.of() : List.of(line);
    }

    List<String> parseHologramLines(String message) {
        List<String> lines = new ArrayList<>();
        for (String part : message.split("\\|")) {
            String line = part.trim();
            if (!line.isEmpty()) lines.add(line);
        }
        return lines;
    }

    boolean hologramEnabled(CaseDefinition definition) {
        ConfigurationSection hologram = caseSection(definition, "hologram");
        return hologram != null && hologram.getBoolean("enabled", false);
    }

    boolean xpBuyEnabled(CaseDefinition definition) {
        ConfigurationSection cost = caseSection(definition, "cost");
        if (cost == null) return false;
        ConfigurationSection purchase = cost.getConfigurationSection("xp-purchase");
        if (purchase != null) {
            return purchase.getBoolean("enabled", definition.buyKeyWithXpLevels() > 0);
        }
        return false;
    }

    boolean previewEnabled(CaseDefinition definition) {
        return definition != null && definition.guiLayout().previewEnabled();
    }

    private boolean update(Player player, String caseName, Consumer<ConfigurationSection> updater,
                           boolean refreshHologram, boolean refreshShowcase) {
        boolean saved = caseManager.updateCaseConfig(caseName, updater, refreshHologram, refreshShowcase);
        player.playSound(player.getLocation(),
                saved ? Sound.UI_BUTTON_CLICK : Sound.ENTITY_VILLAGER_NO,
                0.18f,
                saved ? 1.35f : 1.0f);
        if (!saved) player.sendMessage(caseManager.getPlugin().getUi().get("machine-save-failed"));
        return saved;
    }

    static boolean refreshIfOpen(
            CaseManager caseManager,
            Player player,
            GuiSession.Screen expectedType,
            String caseName,
            Consumer<Inventory> renderer
    ) {
        Inventory inventory = InventoryViewCompat.topInventory(player);
        if (inventory == null || !(inventory.getHolder() instanceof GuiSession holder)) return false;
        if (holder.screen() != expectedType || !holder.caseName().equalsIgnoreCase(caseName)) return false;

        caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
        Inventory rendered = Bukkit.createInventory(holder, inventory.getSize());
        renderer.accept(rendered);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack previous = inventory.getItem(slot);
            ItemStack current = rendered.getItem(slot);
            if (!Objects.equals(previous, current)) {
                caseManager.getPlugin().getGuiUpdates().setTopSlot(player, inventory, slot, current);
            }
        }
        return true;
    }

    private ConfigurationSection caseSection(CaseDefinition definition, String path) {
        ConfigurationSection root = caseManager.getCaseSection(definition.name());
        return root == null ? null : root.getConfigurationSection(path);
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    enum SlotRole {
        EMPTY("Пусто", "&8"),
        DECOR("Декор", "&7"),
        HISTORY("История", "&e"),
        OPEN("Открытие кейса", "&a"),
        ANIMATION("Кнопка анимации", "&b");

        final String displayName;
        final String color;

        SlotRole(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        SlotRole next() {
            return switch (this) {
                case EMPTY -> DECOR;
                case DECOR -> HISTORY;
                case HISTORY -> OPEN;
                case OPEN -> ANIMATION;
                case ANIMATION -> EMPTY;
            };
        }

        SlotRole previous() {
            return switch (this) {
                case EMPTY -> ANIMATION;
                case DECOR -> EMPTY;
                case HISTORY -> DECOR;
                case OPEN -> HISTORY;
                case ANIMATION -> OPEN;
            };
        }
    }
}
