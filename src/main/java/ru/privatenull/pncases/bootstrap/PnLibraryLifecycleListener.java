package ru.privatenull.pncases.bootstrap;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import ru.privatenull.pncases.PnCasesPlugin;

/** Keeps pnCases from outliving the shared runtime during shutdown or manual unload. */
public final class PnLibraryLifecycleListener implements Listener {

    private final PnCasesPlugin plugin;

    public PnLibraryLifecycleListener(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPluginDisable(PluginDisableEvent event) {
        if (!event.getPlugin().getName().equalsIgnoreCase("pnLibrary") || !plugin.isEnabled()) return;
        plugin.getLogger().warning("pnLibrary отключается; pnCases будет безопасно остановлен первым.");
        plugin.getServer().getPluginManager().disablePlugin(plugin);
    }
}
