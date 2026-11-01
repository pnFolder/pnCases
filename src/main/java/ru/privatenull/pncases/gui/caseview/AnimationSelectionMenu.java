package ru.privatenull.pncases.gui.caseview;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.event.inventory.ClickType;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.compat.BukkitCompat.EnchantmentCompat;
import ru.privatenull.pncases.gui.AnimationCategory;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.support.compat.ServerCapabilities;
import ru.privatenull.pncases.support.compat.ServerVersion;
import ru.privatenull.pncases.support.compat.BukkitCompat.SoundCompat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

final class AnimationSelectionMenu {

    private static final int BACK_SLOT = 49;
    private static final int CATEGORY_SLOT = 53;

    private final CaseManager caseManager;

    AnimationSelectionMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    void open(Player player, String caseName) {
        GuiSession holder = GuiSession.playerAnimations(caseName);
        Inventory inventory = Bukkit.createInventory(
                holder,
                54,
                ColorUtil.colorize("&0Выбор анимации")
        );
        render(inventory, caseManager.getCaseByName(caseName), 0,
                caseManager.getPlayerAnimation(player.getUniqueId(), caseName),
                caseManager.getPlayerFavoriteAnimations(player.getUniqueId(), caseName));
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.BLOCK_BARREL_OPEN, 0.28f, 1.15f);
    }

    private void render(Inventory inventory, CaseDefinition definition, int page, AnimationType selected,
                        Set<AnimationType> favorites) {
        ItemStack filler = pane(Material.GRAY_STAINED_GLASS_PANE, " ", Collections.emptyList());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);

        renderAnimations(inventory, definition, page, selected, favorites);
        inventory.setItem(BACK_SLOT, backItem());
        inventory.setItem(CATEGORY_SLOT, categoryItem(definition, page));
    }

    void handleClick(Player player, GuiSession holder, int slot, ClickType click) {
        if (slot == BACK_SLOT) {
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition != null) caseManager.openCaseGui(player, definition);
            player.playSound(player.getLocation(), Sound.BLOCK_BARREL_CLOSE, 0.24f, 0.95f);
            return;
        }

        if (slot == CATEGORY_SLOT) {
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            int size = AnimationCategory.present(Arrays.asList(visibleTypes(definition))).size();
            holder.page(AnimationCategory.cycle(holder.page(), size, !click.isRightClick()));
            rerender(player, holder);
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.22f, click.isRightClick() ? 1.05f : 1.35f);
            return;
        }
        AnimationType chosen = typeAtSlot(caseManager.getCaseByName(holder.caseName()), holder.page(), slot);
        if (chosen == null) return;
        if (click.isRightClick()) toggleFavorite(player, holder, chosen);
        else if (click.isLeftClick()) select(player, holder, chosen);
    }

    private void toggleFavorite(Player player, GuiSession holder, AnimationType chosen) {
        caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
        boolean added = caseManager.togglePlayerFavoriteAnimation(
                player.getUniqueId(), holder.caseName(), chosen);
        rerender(player, holder);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.22f, added ? 1.55f : 0.85f);
        player.sendMessage(caseManager.getPlugin().getUi().get(
                added ? "animation-favorite-added" : "animation-favorite-removed",
                "animation", GuiDesign.animationName(chosen)));
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
    }

    private void rerender(Player player, GuiSession holder) {
        caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
        Inventory inventory = InventoryViewCompat.topInventory(player);
        if (inventory == null) return;
        Inventory rendered = Bukkit.createInventory(holder, inventory.getSize());
        render(rendered, caseManager.getCaseByName(holder.caseName()), holder.page(),
                caseManager.getPlayerAnimation(player.getUniqueId(), holder.caseName()),
                caseManager.getPlayerFavoriteAnimations(player.getUniqueId(), holder.caseName()));
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            ItemStack current = inventory.getItem(slot);
            ItemStack desired = rendered.getItem(slot);
            if (!Objects.equals(current, desired)) {
                caseManager.getPlugin().getGuiUpdates().setTopSlot(player, inventory, slot, desired);
            }
        }
    }

    private ItemStack backItem() {
        ItemStack item = GuiDesign.head(GuiDesign.BACK, "&#FC7165Вернуться");
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color("&#FC7165Вернуться"));
        meta.setLore(List.of());
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack animationItem(AnimationType type, boolean selected, boolean favorite) {
        ItemStack item = GuiDesign.animation(type);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setDisplayName(color("&#5FD7FF" + GuiDesign.animationSelectionName(type)));
        meta.setLore(animationLore(type, selected, favorite).stream().map(this::color).toList());
        GuiDesign.hideAttributes(meta);
        if (selected || favorite) {
            var unbreaking = EnchantmentCompat.unbreaking();
            if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        item.setItemMeta(meta);
        return item;
    }

    private void renderAnimations(Inventory inventory, CaseDefinition definition, int page,
                                  AnimationType selected, Set<AnimationType> favorites) {
        AnimationType[] types = categoryTypes(definition, page);
        if (types.length == 0) {
            ItemStack soon = GuiDesign.head(GuiDesign.INFO, "&#9EFC65Скоро");
            ItemMeta soonMeta = soon.getItemMeta();
            if (soonMeta != null) {
                soonMeta.setDisplayName(color("&#9EFC65Скоро"));
                soonMeta.setLore(List.of(color(" &#A6B0BAВ этой категории пока нет анимаций.")));
                GuiDesign.hideAttributes(soonMeta);
                soon.setItemMeta(soonMeta);
            }
            inventory.setItem(22, soon);
        }
        for (int index = 0; index < types.length && index < GuiDesign.ANIMATION_LIST_SLOTS.size(); index++) {
            AnimationType type = types[index];
            inventory.setItem(GuiDesign.ANIMATION_LIST_SLOTS.get(index),
                    animationItem(type, type == selected, favorites.contains(type)));
        }
    }

    /** Animations the entities of which cannot spawn at this case are left out, the rest move up. */
    private AnimationType[] visibleTypes(CaseDefinition definition) {
        return Arrays.stream(availableTypes())
                .filter(type -> caseManager.animationAvailability(definition, type).available())
                .toArray(AnimationType[]::new);
    }

    private AnimationType[] categoryTypes(CaseDefinition definition, int page) {
        AnimationType[] all = visibleTypes(definition);
        AnimationCategory current = AnimationCategory.at(AnimationCategory.present(Arrays.asList(all)), page);
        return Arrays.stream(all).filter(current::contains).toArray(AnimationType[]::new);
    }

    private ItemStack categoryItem(CaseDefinition definition, int page) {
        List<AnimationCategory> present = AnimationCategory.present(Arrays.asList(visibleTypes(definition)));
        AnimationCategory current = AnimationCategory.at(present, page);
        ItemStack item = GuiDesign.head(GuiDesign.MENU, "&#FFE66DКатегория: " + current.label());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color("&#FFE66DКатегория: " + current.label()));
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        lore.addAll(AnimationCategory.selectorLines(present, current));
        lore.add("");
        lore.add(" &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fСледующая");
        lore.add(" &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &fПредыдущая");
        lore.add("");
        meta.setLore(lore.stream().map(this::color).toList());
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private AnimationType typeAtSlot(CaseDefinition definition, int page, int slot) {
        AnimationType[] types = categoryTypes(definition, page);
        for (int index = 0; index < types.length && index < GuiDesign.ANIMATION_LIST_SLOTS.size(); index++) {
            if (slot == GuiDesign.ANIMATION_LIST_SLOTS.get(index)) return types[index];
        }
        return null;
    }

    private List<String> animationLore(AnimationType type, boolean selected, boolean favorite) {
        List<String> lore = new java.util.ArrayList<>();
        lore.add("");
        for (String line : GuiDesign.animationDescription(type)) {
            lore.add(" &#A6B0BA" + line);
        }
        lore.add("");
        lore.add(" &#A6B0BAИз набора выбирается случайная анимация");
        lore.add(" &#A6B0BAОсновная используется, если набор пуст");
        lore.add("");
        lore.add(" &#C2CBD3Основная");
        lore.add(" &#69747E↳ " + (selected ? "&#A7F3D0Да" : "&#A6B0BAНет"));
        lore.add(" &#C2CBD3В случайном наборе");
        lore.add(" &#69747E↳ " + (favorite ? "&#A7F3D0Да" : "&#A6B0BAНет"));
        lore.add("");
        lore.add(" &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fСделать основной");
        lore.add(" &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &f"
                + (favorite ? "Убрать из набора" : "Добавить в набор"));
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
            case STORM_DUEL -> {
                player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.15f, 1.35f);
                player.playSound(player.getLocation(), Sound.ITEM_SHIELD_BLOCK, 0.13f, 1.20f);
            }
            case TIME_DISTORTION -> {
                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.15f, 1.55f);
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.12f, 0.72f);
            }
            case PRISM -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.14f, 1.65f);
            case STAR_CAROUSEL -> player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_TWINKLE, 0.14f, 1.5f);
            case NEW_YEAR_TREE -> player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.14f, 1.8f);
            case SANTA_SLEIGH -> player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.16f, 1.6f);
            case ICE_BLOCK -> player.playSound(player.getLocation(), Sound.BLOCK_GLASS_PLACE, 0.18f, 1.4f);
            case AUTUMN_MAPLE -> player.playSound(player.getLocation(), Sound.BLOCK_AZALEA_LEAVES_PLACE, 0.2f, 1.0f);
        }
    }

    private AnimationType[] availableTypes() {
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            return new AnimationType[]{AnimationType.FORTUNE_RING};
        }
        if (!ServerVersion.current().isAtLeast(1, 20, 0)
                || !ServerCapabilities.hasDisplayEntities()) {
            return Arrays.stream(AnimationType.values())
                    .filter(type -> type != AnimationType.PILLAGER_RAID
                            && type != AnimationType.DRAGON
                            && type != AnimationType.MIMIC
                            && type != AnimationType.STORM_DUEL
                            && type != AnimationType.PRISM
                            && type != AnimationType.STAR_CAROUSEL
                            && type != AnimationType.NEW_YEAR_TREE
                            && type != AnimationType.SANTA_SLEIGH
                            && type != AnimationType.ICE_BLOCK
                            && type != AnimationType.AUTUMN_MAPLE)
                    .toArray(AnimationType[]::new);
        }
        return AnimationType.values();
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
