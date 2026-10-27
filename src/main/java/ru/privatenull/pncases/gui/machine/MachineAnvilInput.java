package ru.privatenull.pncases.gui.machine;

import ru.privatenull.pncases.PnCasesPlugin;
import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import ru.privatenull.pncases.support.text.ColorUtil;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;

/**
 * Asks for one line of text in an anvil window (AnvilGUI). If the library cannot start on this server version,
 * the reason is written to the console once and the player is told, instead of an error in the middle of a click.
 */
final class MachineAnvilInput {

    private static final AtomicBoolean FAILURE_REPORTED = new AtomicBoolean(false);

    private final PnCasesPlugin plugin;

    MachineAnvilInput(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Starts AnvilGUI once when the plugin is enabled and says in the console whether it works. A library that
     * fails to start is remembered by the JVM until the server restarts, so the real cause must be seen here
     * and not only when somebody clicks.
     */
    static void selfTest(PnCasesPlugin plugin) {
        try {
            Class.forName(AnvilGUI.class.getName(), true, MachineAnvilInput.class.getClassLoader());
            plugin.getLogger().info("Окно ввода (AnvilGUI) готово к работе.");
        } catch (LinkageError | ClassNotFoundException failure) {
            if (FAILURE_REPORTED.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Окно ввода (AnvilGUI) не запускается на этой версии сервера.", failure);
            }
        }
    }

    void open(Player player, String title, String initialValue, ItemStack icon,
              Function<String, String> validator, Consumer<String> accept, Runnable cancel) {
        try {
            openWindow(player, title, initialValue, icon, validator, accept, cancel);
        } catch (LinkageError | RuntimeException failure) {
            // The first failure carries the real cause; later ones only say the class could not be initialised.
            if (FAILURE_REPORTED.compareAndSet(false, true)) {
                plugin.getLogger().log(Level.SEVERE,
                        "Окно ввода (AnvilGUI) не запускается на этой версии сервера.", failure);
            }
            player.sendMessage(ColorUtil.colorize(
                    "&#FC7165Окно ввода недоступно на этой версии сервера. Подробности в консоли."));
            cancel.run();
        }
    }

    private void openWindow(Player player, String title, String initialValue, ItemStack icon,
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
                    plugin.tasks().run(() -> {
                        if (snapshot.getPlayer().isOnline()) cancel.run();
                    });
                })
                .open(player);
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
