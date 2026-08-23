package ru.privatenull.pncases;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.command.CasesCommand;
import ru.privatenull.pncases.command.CasesTabCompleter;
import ru.privatenull.pncases.config.ConfigurationSyncService;
import ru.privatenull.pncases.config.UiConfig;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.caseview.CaseGuiListener;
import ru.privatenull.pncases.gui.caseview.CaseMenuService;
import ru.privatenull.pncases.gui.machine.MachineGuiListener;
import ru.privatenull.pncases.hologram.CaseHologramManager;
import ru.privatenull.pncases.listener.RuntimeListener;
import ru.privatenull.pnlibrary.economy.EconomyService;
import ru.privatenull.pnlibrary.database.DatabaseRouter;
import ru.privatenull.pnlibrary.database.DatabaseExecutor;
import ru.privatenull.pnlibrary.gui.GuiAnimationProfile;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;
import ru.privatenull.pnlibrary.localization.ItemLocalization;
import ru.privatenull.pnlibrary.localization.MinecraftLocale;
import ru.privatenull.pnlibrary.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pnlibrary.lifecycle.PluginRuntime;
import ru.privatenull.pnlibrary.update.GitHubUpdater;
import ru.privatenull.pncases.storage.*;
import ru.privatenull.pnlibrary.compat.ServerCapabilities;
import ru.privatenull.pnlibrary.compat.ServerVersion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PnCasesPlugin extends JavaPlugin {

    private CaseManager caseManager;
    private CaseHologramManager holograms;
    private EconomyService economies;
    private UiConfig ui;
    private GuiUpdateService guiUpdates;
    private GuiOpenAnimationService guiOpenAnimations;
    private CasesDatabase database;
    private DatabaseRouter databaseRouter;
    private ItemLocalization itemLocalization;
    private PluginRuntime runtime;
    private final Map<UUID, Integer> guiTransitionOrigins = new HashMap<>();

    @Override
    public void onEnable() {
        if (!ServerVersion.current().isAtLeast(1, 16, 5)) {
            getLogger().severe("pnCases " + getDescription().getVersion()
                    + " поддерживает Minecraft 1.16.5 - 1.21.11. Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ServerCapabilities.logEnvironment(this);

        saveDefaultConfig();
        ConfigurationSyncService.Result sync = synchronizeConfiguration();
        if (!sync.successful()) {
            getLogger().severe("Не удалось безопасно обновить конфигурацию: " + sync.error());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reloadConfig();
        loadItemLocalization();
        economies = EconomyService.create(this);
        runtime = PluginRuntime.start(this);
        try {
            databaseRouter = DatabaseRouter.from(
                    getConfig().getConfigurationSection("database"), getDataFolder());
            database = databaseRouter.route(
                    JdbcCasesDatabase::new,
                    MongoCasesDatabase::new,
                    RedisCasesDatabase::new);
            database.initialize();
            getLogger().info("Database connected: " + database.type());
        } catch (Exception exception) {
            getLogger().severe("Could not initialize pnCases database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ui = new UiConfig(this);
        guiUpdates = GuiUpdateService.protocolLib(this);
        guiOpenAnimations = new GuiOpenAnimationService(this, guiUpdates);
        holograms = new CaseHologramManager(this);
        if (!holograms.hasProvider()) {
            getLogger().severe("pnCases требует FancyHolograms или DecentHolograms для анимаций. "
                    + "Установите совместимый провайдер и полностью перезапустите сервер.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("Hologram provider connected: " + holograms.providerName());

        caseManager = new CaseManager(this);
        caseManager.setCaseView(new CaseMenuService(this, caseManager));
        caseManager.reloadFromConfig();
        var cmd = getCommand("pncases");
        if (cmd == null) {
            throw new IllegalStateException("Команда pncases отсутствует в plugin.yml");
        }
        var machineGui = new MachineGuiListener(caseManager);
        var exec = new CasesCommand(this, caseManager, machineGui);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(new CasesTabCompleter(caseManager));

        getServer().getPluginManager().registerEvents(
                new CaseGuiListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(machineGui, this);
        var runtimeListener = new RuntimeListener(this, caseManager, database);
        getServer().getPluginManager().registerEvents(runtimeListener, this);
        runtimeListener.deliverPendingToOnlinePlayers();

        runtime.simplePie("database_type", () -> database.type().name());
    }

    @Override
    public void onDisable() {
        closePnCasesGuis();
        if (guiOpenAnimations != null) {
            guiOpenAnimations.shutdown();
        }
        if (guiUpdates != null) guiUpdates.close();
        if (holograms != null) {
            holograms.shutdown();
        }

        if (caseManager != null) {
            caseManager.shutdown();
        }

        // Drain ordered storage work before closing its connections.
        if (runtime != null) runtime.close();
        if (database != null) database.close();
        if (databaseRouter != null) databaseRouter.close();
    }

    public CaseManager getCaseManager() {
        return caseManager;
    }

    public CasesDatabase getDatabase() {
        return database;
    }

    public CaseHologramManager getHolograms() {
        return holograms;
    }

    public EconomyService getEconomies() {
        return economies;
    }

    public DatabaseExecutor getDatabaseExecutor() {
        if (runtime == null) throw new IllegalStateException("PluginRuntime is not initialized");
        return runtime.databaseExecutor();
    }

    public UiConfig getUi() {
        return ui;
    }

    public GuiUpdateService getGuiUpdates() {
        return guiUpdates;
    }

    public GuiOpenAnimationService getGuiOpenAnimations() {
        return guiOpenAnimations;
    }

    public GuiAnimationProfile guiAnimationProfile() {
        return GuiAnimationProfile.standard();
    }

    public void openGui(Player player, Inventory inventory) {
        if (guiOpenAnimations == null) {
            player.openInventory(inventory);
            return;
        }
        guiOpenAnimations.open(player, inventory, true,
                guiAnimationProfile(), guiTransitionOrigin(player));
    }

    public int guiTransitionOrigin(Player player) {
        return player == null ? -1 : guiTransitionOrigins.getOrDefault(player.getUniqueId(), -1);
    }

    public void withGuiTransition(Player player, int sourceSlot, Runnable action) {
        if (player == null || action == null) return;
        UUID playerId = player.getUniqueId();
        Integer previous = guiTransitionOrigins.put(playerId, sourceSlot);
        try {
            action.run();
        } finally {
            if (previous == null) guiTransitionOrigins.remove(playerId);
            else guiTransitionOrigins.put(playerId, previous);
        }
    }

    public void clearGuiTransition(UUID playerId) {
        if (playerId != null) guiTransitionOrigins.remove(playerId);
    }

    public GitHubUpdater getUpdater() {
        return runtime == null ? null : runtime.updates();
    }

    public boolean rarityEnabled() {
        return getConfig().getBoolean("features.rarity", true);
    }

    public boolean previewEnabled() {
        return getConfig().getBoolean("features.preview", true);
    }

    public boolean setFeatureEnabled(String feature, boolean enabled) {
        if (!"rarity".equals(feature) && !"preview".equals(feature)) return false;
        String path = "features." + feature;
        Object previous = getConfig().get(path);
        getConfig().set(path, enabled);
        if (new ru.privatenull.pncases.cases.config.CaseConfigRepository(this).saveMainConfig()) return true;
        getConfig().set(path, previous);
        return false;
    }

    public String getSupportDiscord() {
        return PluginRuntime.supportUrl();
    }

    public ItemLocalization getItemLocalization() {
        return itemLocalization;
    }

    public void reloadRuntimeConfig() {
        loadItemLocalization();
        if (economies != null) economies.refresh();
        if (runtime != null) runtime.reload();
    }

    public ConfigurationSyncService.Result synchronizeConfiguration() {
        return ConfigurationSyncService.synchronize(this);
    }

    private void loadItemLocalization() {
        String configured = getConfig().getString("localization.locale", "ru_ru");
        try {
            itemLocalization = ItemLocalization.load(configured);
        } catch (IllegalArgumentException exception) {
            getLogger().warning("Неизвестная локализация предметов '" + configured
                    + "', используется ru_ru.");
            itemLocalization = ItemLocalization.load(MinecraftLocale.RU_RU);
        }
    }

    private void closePnCasesGuis() {
        for (var player : getServer().getOnlinePlayers()) {
            var top = InventoryViewCompat.topInventory(player);
            Object holder = top == null ? null : top.getHolder();
            if (holder instanceof GuiSession) {
                player.closeInventory();
            }
        }
    }
}
