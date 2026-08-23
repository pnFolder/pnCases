package ru.privatenull.pncases.gui.machine;

import net.wesjd.anvilgui.AnvilGUI;
import org.bukkit.Material;
import org.bukkit.entity.Player;
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

    MachineAnvilInput(Plugin plugin) {
        this.plugin = plugin;
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

        new AnvilGUI.Builder()
                .plugin(plugin)
                .title(ColorUtil.colorize(title))
                .text(initialValue == null || initialValue.isBlank() ? "Введите значение" : initialValue)
                .itemLeft(inputIcon)
                .onClick((slot, snapshot) -> {
                    if (slot != AnvilGUI.Slot.OUTPUT) return List.of();
                    String value = snapshot.getText() == null ? "" : snapshot.getText().trim();
                    String error = validator.apply(value);
                    if (error != null) {
                        return List.of(AnvilGUI.ResponseAction.replaceInputText(error));
                    }
                    submitted.set(true);
                    return List.of(
                            AnvilGUI.ResponseAction.close(),
                            AnvilGUI.ResponseAction.run(() -> accept.accept(value))
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
}
