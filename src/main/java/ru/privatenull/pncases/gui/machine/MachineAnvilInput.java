package ru.privatenull.pncases.gui.machine;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

final class MachineAnvilInput {

    private final Plugin plugin;
    private final MachineAnvilBackend paperBackend;

    MachineAnvilInput(Plugin plugin) {
        this.plugin = plugin;
        this.paperBackend = createPaperBackend(plugin);
    }

    void open(Player player, String title, String initialValue, ItemStack icon,
              Function<String, String> validator, Consumer<String> accept, Runnable cancel) {
        AtomicBoolean submitted = new AtomicBoolean(false);
        ItemStack inputIcon = icon == null ? new ItemStack(Material.PAPER) : icon.clone();
        inputIcon.setAmount(1);
        ItemMeta meta = inputIcon.getItemMeta();
        if (meta != null) {
            meta.setLore(MachineItemFactory.designLore(
                    List.of("Измените текст в строке названия предмета"),
                    List.of("Текущее значение", initialValue == null ? "не задано" : initialValue),
                    "Сохранить предметом справа", null
            ).stream().map(ColorUtil::colorize).toList());
            inputIcon.setItemMeta(meta);
        }

        if (paperBackend != null) {
            paperBackend.open(player, title, initialValue, inputIcon, validator, accept, cancel);
            return;
        }

        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(ColorUtil.colorize(title))
                .text(initialValue == null || initialValue.isBlank() ? "Введите значение" : initialValue)
                .itemLeft(inputIcon)
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return List.of();
                    String value = renameText(player, snapshot);
                    String submittedValue = value == null ? "" : value.trim();
                    String error = validator.apply(submittedValue);
                    if (error != null) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText(error));
                    }
                    submitted.set(true);
                    return List.of(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> accept.accept(submittedValue))
                    );
                })
                .onClose(snapshot -> {
                    if (submitted.get() || !snapshot.getPlayer().isOnline() || !plugin.isEnabled()) return;
                    org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                        if (snapshot.getPlayer().isOnline()) cancel.run();
                    });
                })
                .open(player);
    }

    private static MachineAnvilBackend createPaperBackend(Plugin plugin) {
        if (!org.bukkit.Bukkit.getBukkitVersion().startsWith("1.21.11-")) return null;
        try {
            Class<?> type = Class.forName(
                    "ru.privatenull.pncases.gui.machine.PaperAnvilInput",
                    true,
                    MachineAnvilInput.class.getClassLoader()
            );
            return (MachineAnvilBackend) type.getDeclaredConstructor(Plugin.class).newInstance(plugin);
        } catch (ReflectiveOperationException | LinkageError exception) {
            plugin.getLogger().severe("Не удалось подключить Paper Anvil API: " + exception.getMessage());
            return null;
        }
    }

    private static String renameText(Player player, AnvilGUI.StateSnapshot snapshot) {
        Object view = player.getOpenInventory();
        try {
            Object value = view.getClass().getMethod("getRenameText").invoke(view);
            if (value instanceof String text) return text;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // AnvilView exists only in newer server APIs; legacy versions use AnvilInventory.
        }
        try {
            Object topInventory = view.getClass().getMethod("getTopInventory").invoke(view);
            if (topInventory instanceof AnvilInventory anvilInventory) {
                String text = anvilInventory.getRenameText();
                if (text != null) return text;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // The AnvilGUI snapshot remains the final cross-version fallback.
        }
        return snapshot.getText();
    }
}

interface MachineAnvilBackend {

    void open(Player player, String title, String initialValue, ItemStack icon,
              Function<String, String> validator, Consumer<String> accept, Runnable cancel);
}
