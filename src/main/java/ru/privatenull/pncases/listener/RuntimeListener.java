package ru.privatenull.pncases.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.storage.CasesDatabase;

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
        plugin.getDatabaseExecutor().completeSync(
                plugin.getDatabaseExecutor().supply("pending-players", database::getPendingPlayers),
                pendingPlayers -> pendingPlayers.forEach(playerId -> {
                    var player = plugin.getServer().getPlayer(playerId);
                    if (player == null || !player.isOnline()) return;
                    caseManager.deliverPendingRewardAsync(player, delivered -> {
                        if (delivered) plugin.getLogger().info("Выдал отложенную награду игроку "
                                + player.getName() + " после перезапуска.");
                    });
                }),
                failure -> plugin.getLogger().log(Level.SEVERE,
                        "Не удалось прочитать отложенные награды.", failure));
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
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> caseManager.onWorldLoad(event.getWorld()), 20L);
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        caseManager.onWorldUnload(event.getWorld());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            caseManager.deliverPendingRewardAsync(event.getPlayer(), ignored -> { });
        }, 20L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        caseManager.onPlayerQuit(event.getPlayer());
    }
}
