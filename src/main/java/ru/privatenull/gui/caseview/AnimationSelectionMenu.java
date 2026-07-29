package ru.privatenull.gui.caseview;

import ru.privatenull.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.AnimationType;
import ru.privatenull.cases.model.AnimationType.Category;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.BukkitCompat.EnchantmentCompat;
import ru.privatenull.gui.GuiDesign;
import ru.privatenull.util.BukkitCompat.InventoryViewCompat;
import ru.privatenull.util.ServerCompatibility;
import ru.privatenull.util.BukkitCompat.SoundCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

final class AnimationSelectionMenu {

    private static final int BACK_SLOT = 40;
    private static final int STANDARD_CATEGORY_SLOT = 20;
    private static final int MOBS_CATEGORY_SLOT = 24;
    private static final int[] ANIMATION_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 26};

    private final CaseManager caseManager;

    AnimationSelectionMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    void open(Player player, String caseName) {
        open(player, caseName, null);
    }

    private void open(Player player, String caseName, Category category) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        GuiSession holder = GuiSession.playerAnimations(caseName, category);
        Inventory inventory = Bukkit.createInventory(
                holder,
                45,
                ColorUtil.colorize("&0Выбор анимации")
        );
        render(inventory, definition,
                caseManager.getPlayerAnimation(player.getUniqueId(), caseName), category);
        caseManager.getPlugin().getGuiOpenAnimations().open(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.28f, 1.15f);
    }

    private void render(Inventory inventory, CaseDefinition definition, AnimationType selected,
                        Category category) {
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);

        if (category == null) {
            inventory.setItem(STANDARD_CATEGORY_SLOT, categoryItem(Category.STANDARD));
            inventory.setItem(MOBS_CATEGORY_SLOT, categoryItem(Category.MOBS));
        } else {
            renderCategory(inventory, definition, selected, category, ANIMATION_SLOTS);
        }
        inventory.setItem(BACK_SLOT, backItem(definition));
    }

    void handleClick(Player player, GuiSession holder, int slot) {
        if (slot == BACK_SLOT) {
            if (holder.animationCategory() != null) {
                open(player, holder.caseName(), null);
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 0.95f);
                return;
            }
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition != null) caseManager.openCaseGui(player, definition);
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.24f, 0.95f);
            return;
        }

        if (holder.animationCategory() == null) {
            Category category = categoryAtSlot(slot);
            if (category == null) return;
            open(player, holder.caseName(), category);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
            return;
        }

        AnimationType chosen = typeAtSlot(slot, holder.animationCategory(), ANIMATION_SLOTS);
        if (chosen != null) select(player, holder, chosen);
    }

    private void select(Player player, GuiSession holder, AnimationType chosen) {
        if (caseManager.getPlayerAnimation(player.getUniqueId(), holder.caseName()) == chosen) return;
        caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
        caseManager.setPlayerAnimation(player.getUniqueId(), holder.caseName(), chosen);

        rerender(player, holder);
        playSelectSound(player, chosen);
        player.sendMessage(caseManager.getPlugin().getUi().get(
                "animation-changed",
                "animation", GuiDesign.animationName(chosen)
        ));
        caseManager.warnAboutMobAnimation(player, chosen);
    }

    private void rerender(Player player, GuiSession holder) {
        caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
        Inventory inventory = InventoryViewCompat.topInventory(player);
        CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
        if (inventory == null) return;
        Inventory rendered = Bukkit.createInventory(holder, inventory.getSize());
        render(rendered, definition,
                caseManager.getPlayerAnimation(player.getUniqueId(), holder.caseName()),
                holder.animationCategory());
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack desired = rendered.getItem(slot);
            if (!Objects.equals(current, desired)) {
                caseManager.getPlugin().getGuiUpdates().setTopSlot(player, inventory, slot, desired);
            }
        }
    }

    private ItemStack backItem(CaseDefinition definition) {
        ItemStack item = GuiDesign.head(GuiDesign.BACK, "&#FC7165Вернуться");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color("&#FC7165Вернуться"));
        meta.setLore(List.of());
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack animationItem(CaseDefinition definition, AnimationType type, boolean selected) {
        ItemStack item = GuiDesign.animation(type);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String prefix = selected ? "&#A0EFA1◆ " : "";
        meta.setDisplayName(color(prefix + GuiDesign.animationSelectionName(type)));
        meta.setLore(animationLore(type, selected).stream().map(this::color).toList());
        GuiDesign.hideAttributes(meta);
        if (selected) {
            var unbreaking = EnchantmentCompat.unbreaking();
            if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void renderCategory(Inventory inventory, CaseDefinition definition, AnimationType selected,
                                Category category, int[] slots) {
        AnimationType[] types = availableTypes(category);
        for (int index = 0; index < types.length && index < slots.length; index++) {
            AnimationType type = types[index];
            inventory.setItem(slots[index], animationItem(definition, type, type == selected));
        }
    }

    private ItemStack categoryItem(Category category) {
        ItemStack item = GuiDesign.head(GuiDesign.categoryTexture(category), GuiDesign.categoryName(category));
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(GuiDesign.categoryName(category)));
        meta.setLore(List.of(
                "",
                color("&#9EFC65 «Что находится внутри»"),
                color(category == Category.MOBS
                        ? " &7- &fСценарии с существами"
                        : " &7- &fЭффектные визуальные сценарии"),
                color(category == Category.MOBS
                        ? " &7- &fи интерактивным открытием."
                        : " &7- &fвокруг самого кейса."),
                "",
                color("&#FCA965 «Управление»"),
                color(" &7- &fЛКМ — открыть раздел"),
                ""
        ));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private Category categoryAtSlot(int slot) {
        if (slot == STANDARD_CATEGORY_SLOT) return Category.STANDARD;
        if (slot == MOBS_CATEGORY_SLOT) return Category.MOBS;
        return null;
    }

    private AnimationType typeAtSlot(int slot, Category category, int[] slots) {
        AnimationType[] types = availableTypes(category);
        for (int index = 0; index < types.length && index < slots.length; index++) {
            if (slot == slots[index]) return types[index];
        }
        return null;
    }

    private List<String> animationLore(AnimationType type, boolean selected) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.add("&#9EFC65 «Как выглядит»");
        for (String line : GuiDesign.animationDescription(type)) {
            lore.add(" &7- &f" + line);
        }
        lore.add("");
        lore.add(selected ? "&#65FC83 «Текущее состояние»" : "&#FCA965 «Управление»");
        lore.add(selected
                ? " &7- &fАнимация уже выбрана."
                : " &7- &fЛКМ — выбрать эту анимацию");
        lore.add("");
        return lore;
    }

    private void playSelectSound(Player player, AnimationType type) {
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.22f, 1.35f);
        switch (type) {
            case ANVIL -> player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_PLACE, 0.16f, 1.45f);
            case DYNAMITE -> player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 0.15f, 1.75f);
            case PORTAL -> player.playSound(player.getLocation(), Sound.BLOCK_PORTAL_AMBIENT, 0.12f, 1.65f);
            case POISON -> player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_AMBIENT, 0.15f, 1.6f);
            case FORTUNE_RING -> {
                SoundCompat.play(player, new String[]{"BLOCK_AMETHYST_BLOCK_CHIME", "BLOCK_NOTE_BLOCK_PLING"}, 0.14f, 1.55f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.10f, 1.85f);
            }
            case PILLAGER_RAID -> {
                SoundCompat.play(player, new String[]{"ENTITY_PILLAGER_AMBIENT", "ENTITY_VINDICATOR_AMBIENT"}, 0.13f, 0.85f);
                player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.12f, 0.75f);
            }
            case MOB_HUNT -> {
                player.playSound(player.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.16f, 0.85f);
                player.playSound(player.getLocation(), Sound.ENTITY_SKELETON_AMBIENT, 0.14f, 1.10f);
            }
            case DRAGON -> {
                player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.16f, 0.90f);
                player.playSound(player.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 0.12f, 1.40f);
            }
            case MIMIC -> {
                player.playSound(player.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.16f, 0.72f);
                player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.12f, 1.35f);
            }
        }
    }

    private AnimationType[] availableTypes() {
        if (ServerCompatibility.useMinecraft1165AnimationMode()) {
            return new AnimationType[]{AnimationType.FORTUNE_RING};
        }
        if (!ServerCompatibility.useModernAnimations()) {
            return Arrays.stream(AnimationType.values())
                    .filter(type -> type != AnimationType.PILLAGER_RAID
                            && type != AnimationType.DRAGON
                            && type != AnimationType.MIMIC)
                    .toArray(AnimationType[]::new);
        }
        return AnimationType.values();
    }

    private AnimationType[] availableTypes(Category category) {
        return Arrays.stream(availableTypes())
                .filter(type -> type.category() == category)
                .toArray(AnimationType[]::new);
    }

    private ItemStack pane(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(color(name));
            meta.setLore(lore.stream().map(this::color).toList());
            GuiDesign.hideAttributes(meta);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String color(String value) {
        return ColorUtil.colorize(value);
    }
}
