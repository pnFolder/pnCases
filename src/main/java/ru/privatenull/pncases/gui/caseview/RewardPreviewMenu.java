package ru.privatenull.pncases.gui.caseview;

import ru.privatenull.pncases.gui.GuiSession;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pncases.gui.GuiDesign;
import ru.privatenull.pnlibrary.item.HeadUtil;
import ru.privatenull.pnlibrary.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pnlibrary.compat.BukkitCompat.SoundCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RewardPreviewMenu {

    private static final int[] DEFAULT_REWARD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };
    private static final int DEFAULT_PREVIOUS_SLOT = 45;
    private static final int DEFAULT_BACK_SLOT = 49;
    private static final int DEFAULT_NEXT_SLOT = 53;
    private final CaseManager caseManager;

    public RewardPreviewMenu(CaseManager caseManager) {
        this.caseManager = caseManager;
    }

    public void open(Player player, String caseName, int page) {
        open(player, caseName, page, false);
    }

    public void handleClick(Player player, GuiSession holder, int slot) {
        PreviewLayout layout = resolveLayout();
        if (slot == layout.backSlot()) {
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition != null) {
                caseManager.openCaseGui(player, definition);
            }
            SoundCompat.play(player, new String[]{"BLOCK_BARREL_CLOSE"}, 0.18f, 0.95f);
            return;
        }

        if (slot == layout.previousSlot() || slot == layout.nextSlot()) {
            CaseDefinition definition = caseManager.getCaseByName(holder.caseName());
            if (definition == null) return;
            int pageSize = layout.rewardSlots().size();
            int pages = Math.max(1, (int) Math.ceil(definition.rewards().size() / (double) pageSize));
            if (pages <= 1) return;

            if (slot == layout.previousSlot() && holder.page() > 0) {
                open(player, holder.caseName(), holder.page() - 1, true);
            } else if (slot == layout.nextSlot() && holder.page() + 1 < pages) {
                open(player, holder.caseName(), holder.page() + 1, true);
            }
        }
    }

    private void open(Player player, String caseName, int page, boolean pageChange) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;

        PreviewLayout layout = resolveLayout();
        List<Reward> rewards = definition.rewards();
        int pageSize = layout.rewardSlots().size();
        int pages = Math.max(1, (int) Math.ceil(rewards.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, pages - 1));
        String[] replacements = replacements(definition, safePage, pages,
                "rewards", String.valueOf(rewards.size()));

        Inventory inventory = org.bukkit.Bukkit.createInventory(
                GuiSession.rewardPreview(caseName, safePage),
                layout.size(),
                caseManager.getPlugin().getUi().text(
                        "preview.title", "&7Содержимое кейса", replacements)
        );

        fillDecor(inventory, replacements);

        int totalChance = rewards.stream().mapToInt(Reward::chance).sum();
        int start = safePage * pageSize;
        for (int index = 0; index < pageSize; index++) {
            int rewardIndex = start + index;
            if (rewardIndex >= rewards.size()) break;
            inventory.setItem(layout.rewardSlots().get(index),
                    caseManager.getRewardPresentation().buildPreviewItem(
                            definition, rewards.get(rewardIndex), totalChance));
        }

        inventory.setItem(layout.infoSlot(), buildInfoItem(definition, safePage, pages, rewards.size()));
        inventory.setItem(layout.backSlot(), navigationButton(
                GuiDesign.BACK, "&#FC7165Вернуться", List.of()));

        if (pages > 1) {
            if (safePage > 0) {
                inventory.setItem(layout.previousSlot(), navigationButton(GuiDesign.BACK, "&#5FD7FFПредыдущая страница",
                        List.of("", " &#A6B0BAПереход к предыдущим наградам", "",
                                " &#C2CBD3Страница", " &#69747E↳ &#A7F3D0" + safePage + " / " + pages,
                                "", " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fПерейти", "")));
            } else {
                inventory.setItem(layout.previousSlot(), navigationButton(GuiDesign.DISABLED, "&8Предыдущей страницы нет",
                        List.of("", " &#A6B0BAВы уже на первой странице", "")));
            }
            if (safePage + 1 < pages) {
                inventory.setItem(layout.nextSlot(), navigationButton(GuiDesign.NEXT, "&#5FD7FFСледующая страница",
                        List.of("", " &#A6B0BAПереход к следующим наградам", "",
                                " &#C2CBD3Страница", " &#69747E↳ &#A7F3D0" + (safePage + 2) + " / " + pages,
                                "", " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &fПерейти", "")));
            } else {
                inventory.setItem(layout.nextSlot(), navigationButton(GuiDesign.DISABLED, "&8Следующей страницы нет",
                        List.of("", " &#A6B0BAЭто последняя страница", "")));
            }
        }

        if (pageChange) {
            Inventory current = InventoryViewCompat.topInventory(player);
            if (current != null
                    && current.getSize() == inventory.getSize()
                    && current.getHolder() instanceof GuiSession currentHolder
                    && currentHolder.screen() == GuiSession.Screen.REWARD_PREVIEW
                    && currentHolder.caseName().equalsIgnoreCase(caseName)) {
                caseManager.getPlugin().getGuiOpenAnimations().cancel(player);
                currentHolder.page(safePage);
                for (int slot = 0; slot < current.getSize(); slot++) {
                    ItemStack previous = current.getItem(slot);
                    ItemStack desired = inventory.getItem(slot);
                    if (!Objects.equals(previous, desired)) {
                        caseManager.getPlugin().getGuiUpdates().setTopSlot(player, current, slot, desired);
                    }
                }
            } else {
                caseManager.getPlugin().openGui(player, inventory);
            }
        } else {
            caseManager.getPlugin().openGui(player, inventory);
        }
        SoundCompat.play(player,
                new String[]{pageChange ? "UI_BUTTON_CLICK" : "BLOCK_BARREL_OPEN"},
                pageChange ? 0.14f : 0.18f,
                pageChange ? 1.2f : 1.25f);
    }

    private void fillDecor(Inventory inventory, String[] replacements) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            GuiDesign.hideAttributes(meta);
            filler.setItemMeta(meta);
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack buildInfoItem(CaseDefinition definition, int page, int pages, int rewardCount) {
        return navigationButton(GuiDesign.CONTENT, "&#5FD7FFСодержимое кейса", List.of(
                "", " &#A6B0BAНаграды, которые могут выпасть игроку", "",
                " &#C2CBD3Добавлено наград", " &#69747E↳ &#A7F3D0" + rewardCount,
                " &#C2CBD3Страница", " &#69747E↳ &#A7F3D0" + (page + 1) + " / " + pages,
                ""));
    }

    private ItemStack navigationButton(String texture, String name, List<String> lore) {
        ItemStack item = HeadUtil.create(texture, name);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(ColorUtil.colorize(name));
        meta.setLore(lore.isEmpty() ? null : lore.stream().map(ColorUtil::colorize).toList());
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private PreviewLayout resolveLayout() {
        return new PreviewLayout(
                54,
                java.util.Arrays.stream(DEFAULT_REWARD_SLOTS).boxed().toList(),
                4,
                DEFAULT_PREVIOUS_SLOT,
                DEFAULT_BACK_SLOT,
                DEFAULT_NEXT_SLOT
        );
    }

    private String[] replacements(CaseDefinition definition, int page, int pages, String... extra) {
        List<String> replacements = new ArrayList<>();
        addReplacement(replacements, "case", ColorUtil.colorize(definition.displayName()));
        addReplacement(replacements, "case_id", definition.name());
        addReplacement(replacements, "page", String.valueOf(page + 1));
        addReplacement(replacements, "pages", String.valueOf(pages));
        addReplacement(replacements, "prev_page", String.valueOf(Math.max(1, page)));
        addReplacement(replacements, "prev-page", String.valueOf(Math.max(1, page)));
        addReplacement(replacements, "next_page", String.valueOf(Math.min(pages, page + 2)));
        addReplacement(replacements, "next-page", String.valueOf(Math.min(pages, page + 2)));
        for (int index = 0; index + 1 < extra.length; index += 2) {
            addReplacement(replacements, extra[index], extra[index + 1]);
        }
        return replacements.toArray(String[]::new);
    }

    private static void addReplacement(List<String> replacements, String key, String value) {
        replacements.add(key);
        replacements.add(value);
    }

    private record PreviewLayout(
            int size,
            List<Integer> rewardSlots,
            int infoSlot,
            int previousSlot,
            int backSlot,
            int nextSlot
    ) {
    }
}
