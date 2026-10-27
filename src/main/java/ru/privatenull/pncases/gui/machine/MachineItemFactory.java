package ru.privatenull.pncases.gui.machine;

import org.bukkit.Material;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.compat.BukkitCompat.EnchantmentCompat;
import ru.privatenull.pncases.gui.GuiDesign;

import java.util.ArrayList;
import java.util.List;

final class MachineItemFactory {

    private static final String NAME_COLOR = "&#5FD7FF";
    private static final String DESCRIPTION_COLOR = " &#A6B0BA";
    private static final String PARAMETER_COLOR = " &#C2CBD3";
    private static final String VALUE_PREFIX = " &#69747E↳ &#A7F3D0";
    private static final String LEFT_ACTION = " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &f";
    private static final String RIGHT_ACTION = " &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &f";
    // Same letter-by-letter gradient style as the two above: blue for Shift + left, violet for Shift + right,
    // green for an item on the cursor.
    private static final String SHIFT_LEFT_ACTION =
            " &#65D1FC[&#5FC7F6 Shift&#59BCF0 +&#53B1EA Л&#4DA6E4К&#479BDEМ &#4190D8] &f";
    private static final String SHIFT_RIGHT_ACTION =
            " &#C4A7FF[&#BC9CFA Shift&#B491F5 +&#AC86F0 П&#A47BEBК&#9C70E6М &#9465E1] &f";
    private static final String MIDDLE_ACTION =
            " &#EFF7B9[&#E3EDAC Колёсико&#D7E39F ] &f";
    private static final String ITEM_ACTION =
            " &#A7F3D0[&#9CEAC4 Предмет&#91E1B8 +&#86D8AC Л&#7BCFA0К&#70C694М &#65BD88] &f";

    private final CaseManager caseManager;
    private final MachineGuiSupport state;

    MachineItemFactory(CaseManager caseManager, MachineGuiSupport state) {
        this.caseManager = caseManager;
        this.state = state;
    }

    ItemStack selectable(String path, Material material, boolean selected, String name, List<String> lore,
                         CaseDefinition definition, String... extra) {
        return selectable(path, new ItemStack(material), selected, name, lore, definition, extra);
    }

    ItemStack selectable(String path, String texture, boolean selected, String name, List<String> lore,
                         CaseDefinition definition, String... extra) {
        return selectable(path, GuiDesign.head(texture, name), selected, name, lore, definition, extra);
    }

    ItemStack selectable(String path, MachineIcon icon, boolean selected, String name, List<String> lore,
                         CaseDefinition definition, String... extra) {
        return selectable(path, icon.item(name), selected, name, lore, definition, extra);
    }

    private ItemStack selectable(String path, ItemStack source, boolean selected, String name, List<String> lore,
                                 CaseDefinition definition, String... extra) {
        String prefix = "";
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
        ItemStack item = button(source,
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

        List<String> lore = hintLore(
                List.of(hint),
                List.of("Предмет", itemName(item)),
                Hint.item("заменить предметом с курсора"));
        String[] replacements = replacements(definition,
                "hint", hint,
                "item", itemName(item));
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

    ItemStack sectionButton(String path, MachineIcon icon, String name, List<String> lore,
                            CaseDefinition definition, String... extra) {
        return sectionButton(path, icon.item(name), name, lore, definition, extra);
    }

    private ItemStack sectionButton(String path, ItemStack source, String name, List<String> lore,
                                    CaseDefinition definition, String... extra) {
        return button(source,
                caseManager.getPlugin().getUi().text(path + ".name", name,
                        replacements(definition, extra)),
                caseManager.getPlugin().getUi().list(path + ".lore", lore,
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

    ItemStack configuredButton(String path, MachineIcon icon, String name, List<String> lore,
                               CaseDefinition definition, String... extra) {
        return configuredButton(path, icon.item(name), name, lore, definition, extra);
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

    ItemStack builtInButton(MachineIcon icon, String name, List<String> lore) {
        return button(icon.item(name), name, lore);
    }

    ItemStack backButton(CaseDefinition definition) {
        return button(GuiDesign.head(GuiDesign.BACK, "&#FC7165Вернуться"),
                "&#FC7165Вернуться", List.of());
    }

    ItemStack closeButton(CaseDefinition definition) {
        return button(GuiDesign.head(GuiDesign.BACK, "&#FC7165Закрыть"),
                "&#FC7165Закрыть", List.of());
    }

    String name(String value) {
        return color(NAME_COLOR) + value;
    }

    String plain(String value) {
        String plain = ChatColor.stripColor(color(value));
        return plain == null ? "" : plain;
    }

    /** One line of the click guide at the bottom of a button: which click does what. */
    record Hint(String prefix, String action) {
        static Hint left(String action) {
            return new Hint(LEFT_ACTION, action);
        }

        static Hint right(String action) {
            return new Hint(RIGHT_ACTION, action);
        }

        static Hint shiftLeft(String action) {
            return new Hint(SHIFT_LEFT_ACTION, action);
        }

        static Hint shiftRight(String action) {
            return new Hint(SHIFT_RIGHT_ACTION, action);
        }

        static Hint middle(String action) {
            return new Hint(MIDDLE_ACTION, action);
        }

        /** An item taken from the cursor and dropped onto the button. */
        static Hint item(String action) {
            return new Hint(ITEM_ACTION, action);
        }
    }

    /** Lore with an explicit click guide: every supported click gets its own coloured line. */
    List<String> hintLore(List<String> description, List<String> parameters, Hint... hints) {
        return designHintLore(description, parameters, List.of(hints));
    }

    List<String> hintLore(String description, Hint... hints) {
        return designHintLore(description == null || description.isBlank() ? List.of() : List.of(description),
                List.of(), List.of(hints));
    }

    static List<String> designHintLore(List<String> description, List<String> parameters, List<Hint> hints) {
        List<String> result = new ArrayList<>();
        result.add("");
        for (String line : description) result.add(color(DESCRIPTION_COLOR) + line);

        if (!description.isEmpty() && !parameters.isEmpty()) result.add("");
        for (int index = 0; index + 1 < parameters.size(); index += 2) {
            result.add(color(PARAMETER_COLOR) + parameters.get(index));
            result.add(color(VALUE_PREFIX) + parameters.get(index + 1));
        }

        if (!hints.isEmpty()) result.add("");
        for (Hint hint : hints) {
            result.add(color(hint.prefix()) + hint.action());
        }
        result.add("");
        return result;
    }

    List<String> lore(String description, String parameter, String value,
                      String leftAction, String rightAction) {
        return lore(
                description == null || description.isBlank() ? List.of() : List.of(description),
                parameter == null || parameter.isBlank() ? List.of() : List.of(parameter, value),
                leftAction,
                rightAction
        );
    }

    List<String> lore(List<String> description, List<String> parameters,
                      String leftAction, String rightAction) {
        return designLore(description, parameters, leftAction, rightAction);
    }

    static List<String> designLore(List<String> description, List<String> parameters,
                                   String leftAction, String rightAction) {
        List<String> result = new ArrayList<>();
        result.add("");
        for (String line : description) result.add(color(DESCRIPTION_COLOR) + line);

        if (!description.isEmpty() && !parameters.isEmpty()) result.add("");
        for (int index = 0; index + 1 < parameters.size(); index += 2) {
            result.add(color(PARAMETER_COLOR) + parameters.get(index));
            // Parse the prefix before appending digits; otherwise RGB + "0.12" can become RGBA.
            result.add(color(VALUE_PREFIX) + parameters.get(index + 1));
        }

        if (leftAction != null || rightAction != null) result.add("");
        if (leftAction != null) result.add(LEFT_ACTION + leftAction);
        if (rightAction != null) result.add(RIGHT_ACTION + rightAction);
        result.add("");
        return result;
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

    static void addGlow(ItemStack item) {
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

    private String itemName(ItemStack item) {
        return item == null || item.getType().isAir()
                ? "нет"
                : caseManager.getPlugin().getItemLocalization().getPlainName(item);
    }
}
