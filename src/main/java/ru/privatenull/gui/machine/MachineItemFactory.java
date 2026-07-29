package ru.privatenull.gui.machine;

import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.cases.model.CaseDefinition;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.util.BukkitCompat.EnchantmentCompat;
import ru.privatenull.gui.GuiDesign;

import java.util.ArrayList;
import java.util.List;

import static ru.privatenull.util.ItemFactory.readableItemName;

final class MachineItemFactory {

    private final CaseManager caseManager;
    private final MachineGuiSupport state;

    MachineItemFactory(CaseManager caseManager, MachineGuiSupport state) {
        this.caseManager = caseManager;
        this.state = state;
    }

    ItemStack selectable(String path, Material material, boolean selected, String name, List<String> lore,
                         CaseDefinition definition, String... extra) {
        String prefix = selected ? "&a◆ " : "&7◇ ";
        List<String> replacements = new ArrayList<>(List.of(
                "prefix", prefix,
                "selected_prefix", prefix,
                "selected", selected ? "&aда" : "&7нет",
                "name", name,
                "display", name,
                "display_name", name));
        append(replacements, extra);
        String[] values = definition == null
                ? replacements.toArray(String[]::new)
                : replacements(definition, replacements.toArray(String[]::new));
        ItemStack item = button(material,
                caseManager.getPlugin().getUi().text(path + ".name", prefix + name, values),
                caseManager.getPlugin().getUi().list(path + ".lore", lore, values));
        if (selected) addGlow(item);
        return item;
    }

    ItemStack copyItemButton(String path, ItemStack source, Material fallback, CaseDefinition definition,
                             String title, String hint) {
        ItemStack item = source == null || source.getType().isAir() ? new ItemStack(fallback) : source.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<String> lore = List.of(
                "",
                "&#9EFC65 «Назначение»",
                " &7- &f" + hint,
                "",
                "&#65D1FC «Сейчас»",
                " &7- &fПредмет: &#FCA865" + readableItemName(item),
                "",
                "&#FCA965 «Управление»",
                " &7- &fВозьмите новый предмет на курсор",
                " &7- &fи нажмите по кнопке для замены.",
                ""
        );
        String[] replacements = replacements(definition,
                "hint", hint,
                "item", readableItemName(item));
        meta.setDisplayName(caseManager.getPlugin().getUi().text(path + ".name", title, replacements));
        meta.setLore(caseManager.getPlugin().getUi().list(path + ".lore", lore, replacements));
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack sectionButton(String path, Material material, String name, List<String> lore,
                            CaseDefinition definition, String... extra) {
        return sectionButton(path, new ItemStack(material), name, lore, definition, extra);
    }

    ItemStack sectionButton(String path, String texture, String name, List<String> lore,
                            CaseDefinition definition, String... extra) {
        return sectionButton(path, GuiDesign.head(texture, name), name, lore, definition, extra);
    }

    private ItemStack sectionButton(String path, ItemStack source, String name, List<String> lore,
                                    CaseDefinition definition, String... extra) {
        List<String> formatted = new ArrayList<>(lore.size() + 2);
        formatted.add("");
        formatted.addAll(lore);
        formatted.add("");
        return button(source,
                caseManager.getPlugin().getUi().text(path + ".name", name,
                        replacements(definition, extra)),
                caseManager.getPlugin().getUi().list(path + ".lore", formatted,
                        replacements(definition, extra)));
    }

    ItemStack configuredButton(String path, Material material, String name, List<String> lore,
                               CaseDefinition definition, String... extra) {
        return configuredButton(path, new ItemStack(material), name, lore, definition, extra);
    }

    ItemStack configuredButton(String path, String texture, String name, List<String> lore,
                               CaseDefinition definition, String... extra) {
        return configuredButton(path, GuiDesign.head(texture, name), name, lore, definition, extra);
    }

    ItemStack configuredButton(String path, ItemStack source, String name, List<String> lore,
                               CaseDefinition definition, String... extra) {
        return button(source,
                caseManager.getPlugin().getUi().text(path + ".name", name,
                        replacements(definition, extra)),
                caseManager.getPlugin().getUi().list(path + ".lore", lore,
                        replacements(definition, extra)));
    }

    ItemStack builtInButton(Material material, String name, List<String> lore) {
        return button(material, name, lore);
    }

    ItemStack builtInButton(ItemStack source, String name, List<String> lore) {
        return button(source, name, lore);
    }

    ItemStack builtInButton(String texture, String name, List<String> lore) {
        return button(GuiDesign.head(texture, name), name, lore);
    }

    ItemStack backButton(CaseDefinition definition) {
        return button(GuiDesign.head(GuiDesign.BACK, "&#FC7165Вернуться"),
                "&#FC7165Вернуться", List.of());
    }

    ItemStack closeButton(CaseDefinition definition) {
        return button(GuiDesign.head(GuiDesign.BACK, "&#FC7165Выйти"),
                "&#FC7165Выйти", List.of());
    }

    String[] replacements(CaseDefinition definition, String... extra) {
        List<String> replacements = new ArrayList<>();
        add(replacements, "case", color(definition.displayName()));
        add(replacements, "display-name", color(definition.displayName()));
        add(replacements, "case_id", definition.name());
        add(replacements, "title", definition.guiTitle());
        add(replacements, "gui_title", definition.guiTitle());
        String plainTitle = ChatColor.stripColor(color(definition.guiTitle()));
        add(replacements, "gui_title_plain", plainTitle == null ? "" : plainTitle);
        Location block = definition.blockLocation();
        add(replacements, "world", block == null || block.getWorld() == null
                ? "world" : block.getWorld().getName());
        add(replacements, "x", block == null ? "0" : String.valueOf(block.getBlockX()));
        add(replacements, "y", block == null ? "0" : String.valueOf(block.getBlockY()));
        add(replacements, "z", block == null ? "0" : String.valueOf(block.getBlockZ()));
        add(replacements, "size", String.valueOf(definition.guiLayout().size()));
        add(replacements, "rows", String.valueOf(definition.guiLayout().size() / 9));
        String hologram = state.hologramEnabled(definition) ? "&aвключена" : "&cвыключена";
        add(replacements, "hologram_status", hologram);
        add(replacements, "hologram_height", String.valueOf(state.hologramHeight(definition)));
        String showcase = definition.idleParticles().enabled() ? "&aвключена" : "&cвыключена";
        add(replacements, "showcase_status", showcase);
        add(replacements, "theme", definition.idleParticles().theme().displayName());
        String xp = state.xpBuyEnabled(definition) ? "&aвключена" : "&cвыключена";
        add(replacements, "xp_status", xp);
        add(replacements, "xp_levels", String.valueOf(Math.max(0, definition.buyKeyWithXpLevels())));
        add(replacements, "animation", state.currentAnimationLabel(definition));
        append(replacements, extra);
        return replacements.toArray(String[]::new);
    }

    String format(CaseDefinition definition, String value) {
        String formatted = value == null ? "" : value;
        String[] replacements = replacements(definition);
        for (int index = 0; index < replacements.length; index += 2) {
            formatted = formatted.replace(
                    "{" + replacements[index] + "}", replacements[index + 1]);
        }
        return color(formatted);
    }

    ItemStack pane(Material material, String name, List<String> lore) {
        return button(material, name, lore);
    }

    void fill(Inventory inventory, ItemStack item) {
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, item.clone());
        }
    }

    private static ItemStack button(Material material, String name, List<String> lore) {
        return button(new ItemStack(material), name, lore);
    }

    private static ItemStack button(ItemStack source, String name, List<String> lore) {
        ItemStack item = source == null || source.getType().isAir()
                ? new ItemStack(Material.STONE_BUTTON) : source.clone();
        item.setAmount(1);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.setDisplayName(color(name));
        meta.setLore(lore.stream().map(MachineItemFactory::color).toList());
        GuiDesign.hideAttributes(meta);
        item.setItemMeta(meta);
        return item;
    }

    private static void addGlow(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        var unbreaking = EnchantmentCompat.unbreaking();
        if (unbreaking != null) meta.addEnchant(unbreaking, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private static void append(List<String> values, String... extra) {
        for (int index = 0; index + 1 < extra.length; index += 2) {
            add(values, extra[index], extra[index + 1]);
        }
    }

    private static void add(List<String> values, String key, String value) {
        values.add(key);
        values.add(value);
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }
}
