package ru.privatenull.pncases.gui.machine;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MenuType;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.view.AnvilView;
import org.bukkit.plugin.Plugin;
import ru.privatenull.pnlibrary.text.ColorUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;

final class PaperAnvilInput implements MachineAnvilBackend, Listener {

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    PaperAnvilInput(Plugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public void open(Player player, String title, String initialValue, ItemStack icon,
                     Function<String, String> validator, Consumer<String> accept, Runnable cancel) {
        String text = initialValue == null || initialValue.isBlank()
                ? "Введите значение"
                : initialValue;
        ItemStack input = icon.clone();
        ItemMeta meta = input.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ColorUtil.colorize(text));
            input.setItemMeta(meta);
        }

        AnvilView view = MenuType.ANVIL.builder()
                .checkReachable(false)
                .title(ColorUtil.component(title))
                .build(player);
        view.setRepairCost(0);
        view.setMaximumRepairCost(0);

        Session previous = sessions.remove(player.getUniqueId());
        if (previous != null) previous.submitted = true;
        sessions.put(player.getUniqueId(), new Session(view, validator, accept, cancel));
        player.openInventory(view);
        view.getTopInventory().setItem(0, input);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Session session = session(player, event.getView().getTopInventory());
        if (session == null) return;

        event.setCancelled(true);
        if (event.getRawSlot() != 2 || event.getCurrentItem() == null
                || event.getCurrentItem().getType() == Material.AIR) return;

        String value = session.view.getRenameText();
        String submittedValue = value == null ? "" : value.trim();
        String error = session.validator.apply(submittedValue);
        if (error != null) {
            replaceInput(session.view, error);
            return;
        }

        session.submitted = true;
        sessions.remove(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> session.accept.accept(submittedValue));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    private void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (session(player, event.getView().getTopInventory()) != null) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    private void onClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        Session session = session(player, event.getView().getTopInventory());
        if (session == null || !sessions.remove(player.getUniqueId(), session) || session.submitted) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (plugin.isEnabled() && player.isOnline()) session.cancel.run();
        });
    }

    @EventHandler
    private void onQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private Session session(Player player, org.bukkit.inventory.Inventory topInventory) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.view.getTopInventory() == topInventory ? session : null;
    }

    private static void replaceInput(AnvilView view, String text) {
        ItemStack input = view.getTopInventory().getItem(0);
        if (input == null) return;
        input = input.clone();
        ItemMeta meta = input.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(ColorUtil.colorize(text));
        input.setItemMeta(meta);
        view.getTopInventory().setItem(0, input);
    }

    private static final class Session {
        private final AnvilView view;
        private final Function<String, String> validator;
        private final Consumer<String> accept;
        private final Runnable cancel;
        private boolean submitted;

        private Session(AnvilView view, Function<String, String> validator,
                        Consumer<String> accept, Runnable cancel) {
            this.view = view;
            this.validator = validator;
            this.accept = accept;
            this.cancel = cancel;
        }
    }
}
