package ru.privatenull.listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.storage.CasesDatabase;

import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class RuntimeListener implements Listener {

    private final PnCasesPlugin plugin;
    private final CaseManager caseManager;
    private final CasesDatabase database;

    public RuntimeListener(PnCasesPlugin plugin, CaseManager caseManager,
                           CasesDatabase database) {
        this.plugin = plugin;
        this.caseManager = caseManager;
        this.database = database;
    }

    public void deliverPendingToOnlinePlayers() {
        Set<UUID> pendingPlayers;
        try {
            pendingPlayers = database.getPendingPlayers();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось прочитать отложенные награды.", exception);
            return;
        }
        for (UUID playerId : pendingPlayers) {
            var player = plugin.getServer().getPlayer(playerId);
            if (player == null || !player.isOnline()) continue;

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !caseManager.hasPendingReward(playerId)) return;
                if (caseManager.deliverPendingReward(player)) {
                    plugin.getLogger().info("Выдал отложенную награду игроку " + player.getName()
                            + " после перезапуска.");
                }
            });
        }
    }

    @EventHandler
    public void onCaseInteract(PlayerInteractEvent event) {
        if (event.getHand() == EquipmentSlot.OFF_HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                && event.getAction() != Action.LEFT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        var definition = caseManager.getCaseByBlock(event.getClickedBlock());
        if (definition == null) return;
        event.setCancelled(true);

        var player = event.getPlayer();
        if (caseManager.isOpening(player.getUniqueId())) {
            player.sendMessage(plugin.getUi().get("already-opening"));
            return;
        }
        if (caseManager.isCaseBusy(definition, player.getUniqueId(), event.getClickedBlock())) {
            player.sendMessage(plugin.getUi().get("case-busy"));
            return;
        }
        caseManager.openCaseGui(player, definition, event.getClickedBlock());
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, caseManager::reloadFromConfig, 20L);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        caseManager.onWorldUnload(event.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (plugin.getUpdateChecker() != null) {
            plugin.getUpdateChecker().notifyAdminOnJoin(event.getPlayer());
        }

        UUID playerId = event.getPlayer().getUniqueId();
        if (!caseManager.hasPendingReward(playerId)) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            if (!caseManager.hasPendingReward(playerId)) return;
            if (caseManager.deliverPendingReward(event.getPlayer())) {
                plugin.getLogger().info("Выдал отложенную награду игроку " + event.getPlayer().getName()
                        + " после перезапуска во время анимации.");
            }
        }, 20L);
    }
}
