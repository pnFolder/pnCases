package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.machine.MachineItemFactory.Hint;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.pncases.gui.machine.MachineGuiSupport.section;
import static ru.privatenull.pncases.gui.machine.RewardScreenLayout.CONTENT_SLOTS;

/**
 * "Кейсы внутри": picks the cases that open from this case. A click on a case puts it inside or takes it out;
 * the case then shows the list of these cases to the player instead of its own menu.
 */
final class MachineBundleScreen {

    static final int BACK = 45;
    static final int PREVIOUS = 48;
    static final int NEXT = 50;

    private final CaseManager caseManager;
    private final MachineGuiSupport support;
    private final MachineItemFactory items;

    MachineBundleScreen(CaseManager caseManager, MachineGuiSupport support, MachineItemFactory items) {
        this.caseManager = caseManager;
        this.support = support;
        this.items = items;
    }

    void open(Player player, String caseName, int requestedPage) {
        CaseDefinition definition = caseManager.getCaseByName(caseName);
        if (definition == null) return;
        List<CaseDefinition> candidates = candidates(definition);
        int pages = RewardScreenLayout.pageCount(candidates.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        if (MachineGuiSupport.refreshIfOpen(caseManager, player, GuiSession.Screen.MACHINE_BUNDLE,
                definition.name(), page, -1, inventory -> fill(inventory, definition, candidates, page, pages))) {
            return;
        }
        GuiSession session = GuiSession.machine(GuiSession.Screen.MACHINE_BUNDLE, definition.name());
        session.page(page);
        Inventory inventory = Bukkit.createInventory(session, 54,
                ColorUtil.colorize("&0Кейсы внутри | " + definition.displayName()));
        fill(inventory, definition, candidates, page, pages);
        caseManager.getPlugin().openGui(player, inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.18f, 1.25f);
    }

    void handleClick(GuiSession holder, Player player, CaseDefinition definition, int slot,
                     MachineExtrasScreen extrasScreen) {
        int page = holder.page();
        if (slot == BACK) {
            extrasScreen.open(player, definition.name());
            return;
        }
        if (slot == PREVIOUS) {
            open(player, definition.name(), page - 1);
            return;
        }
        if (slot == NEXT) {
            open(player, definition.name(), page + 1);
            return;
        }
        int position = CONTENT_SLOTS.indexOf(slot);
        if (position < 0) return;
        List<CaseDefinition> candidates = candidates(definition);
        int index = page * CONTENT_SLOTS.size() + position;
        if (index >= candidates.size()) return;

        String clicked = candidates.get(index).name();
        boolean self = clicked.equals(definition.name());
        List<String> inside = new ArrayList<>(definition.children());
        inside.remove(definition.name());
        boolean includeSelf = definition.children().contains(definition.name());
        if (self) includeSelf = !includeSelf;
        else if (!inside.remove(clicked)) inside.add(clicked);
        boolean keepSelf = includeSelf;
        support.update(player, definition.name(), root -> {
            var bundle = section(root, "bundle");
            bundle.set("cases", inside.isEmpty() ? null : inside);
            // The case itself is listed first by default; this only records that it was switched off.
            bundle.set("include_self", keepSelf ? null : false);
            if (inside.isEmpty() && keepSelf) root.set("bundle", null);
        });
        open(player, definition.name(), page);
    }

    /** The case itself first, then every other case that is not a bundle itself. */
    private List<CaseDefinition> candidates(CaseDefinition definition) {
        List<CaseDefinition> candidates = new ArrayList<>();
        if (!definition.rewards().isEmpty()) candidates.add(definition);
        for (CaseDefinition other : caseManager.getCases()) {
            if (!other.name().equals(definition.name()) && !other.isBundle()) candidates.add(other);
        }
        return candidates;
    }

    private void fill(Inventory inventory, CaseDefinition definition, List<CaseDefinition> candidates,
                      int page, int pages) {
        items.fill(inventory, items.pane(Material.BLACK_STAINED_GLASS_PANE, " ", List.of()));
        int start = page * CONTENT_SLOTS.size();
        for (int position = 0; position < CONTENT_SLOTS.size(); position++) {
            int index = start + position;
            if (index >= candidates.size()) break;
            inventory.setItem(CONTENT_SLOTS.get(position), caseButton(definition, candidates.get(index)));
        }
        inventory.setItem(BACK, items.backButton(definition));
        if (page > 0) {
            inventory.setItem(PREVIOUS, items.builtInButton(MachineIcon.REWARDS_PREVIOUS, "&#65D1FCПредыдущая", List.of()));
        }
        if (page + 1 < pages) {
            inventory.setItem(NEXT, items.builtInButton(MachineIcon.REWARDS_NEXT, "&#65D1FCСледующая", List.of()));
        }
        inventory.setItem(4, items.builtInButton(Material.ENDER_CHEST, items.name("Кейсы внутри"), items.hintLore(
                List.of("Отметьте кейсы, кнопки которых будут в меню этого кейса рядом друг с другом",
                        "Сам кейс стоит в меню первым, его можно убрать кликом по первой иконке",
                        "Если ничего не отмечено, кейс работает как обычно"),
                List.of("Выбрано", String.valueOf(definition.children().size())))));
        if (candidates.isEmpty()) {
            inventory.setItem(22, items.builtInButton(Material.BARRIER, items.name("Других кейсов нет"), items.hintLore(
                    List.of("Создайте ещё кейсы, чтобы поместить их сюда"), List.of())));
        }
    }

    private ItemStack caseButton(CaseDefinition bundle, CaseDefinition candidate) {
        boolean inside = bundle.children().contains(candidate.name());
        List<String> lore = items.hintLore(
                List.of(inside ? "&#A7F3D0✔ Есть в меню" : "&#A6B0BAНе входит",
                        candidate.name().equals(bundle.name()) ? "Это сам кейс: его кнопка встанет рядом с остальными" : ""),
                List.of("ID", candidate.name()),
                Hint.left(inside ? "убрать" : "положить внутрь"));
        ItemStack source = candidate.openButton();
        ItemStack icon = source == null || source.getType().isAir() ? new ItemStack(Material.CHEST) : source;
        String name = inside ? "&#A7F3D0✔ " + candidate.displayName() : items.name(candidate.displayName());
        return items.builtInButton(icon, name, lore);
    }
}
