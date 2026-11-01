package ru.privatenull.pncases;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.bukkit.bootstrap.PnLibraryBootstrap;
import ru.privatenull.pncases.bootstrap.PnLibraryLifecycleListener;
import ru.privatenull.pncases.cases.CaseManager;
import ru.privatenull.pncases.cases.animation.effect.RewardVisibilityGuard;
import ru.privatenull.pncases.command.CasesCommand;
import ru.privatenull.pncases.command.CasesTabCompleter;
import ru.privatenull.pncases.compat.HotReloadClassCache;
import ru.privatenull.pncases.config.ConfigurationSyncService;
import ru.privatenull.pncases.config.BundledConfigurationResources;
import ru.privatenull.pncases.config.ConfigBackup;
import ru.privatenull.pncases.config.UiConfig;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.gui.caseview.CaseGuiListener;
import ru.privatenull.pncases.gui.caseview.CaseMenuService;
import ru.privatenull.pncases.gui.machine.MachineGuiListener;
import ru.privatenull.pncases.gui.gift.KeyGiftMenu;
import ru.privatenull.pncases.hologram.CaseHologramManager;
import ru.privatenull.pncases.integration.CaseRuntimeData;
import ru.privatenull.pncases.integration.DeferredRuntimeData;
import ru.privatenull.pncases.support.task.PluginTasks;
import ru.privatenull.pncases.integration.PnLibraryRuntime;
import ru.privatenull.pncases.integration.PnRuntime;
import ru.privatenull.pncases.listener.RuntimeListener;
import ru.privatenull.pncases.support.economy.EconomyService;
import ru.privatenull.pncases.support.database.DatabaseRouter;
import ru.privatenull.pncases.support.database.DatabaseExecutor;
import ru.privatenull.pncases.support.gui.GuiAnimationProfile;
import ru.privatenull.pncases.support.gui.GuiOpenAnimationService;
import ru.privatenull.pncases.support.gui.GuiUpdateService;
import ru.privatenull.pncases.support.localization.ItemLocalization;
import ru.privatenull.pncases.support.localization.MinecraftLocale;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.storage.*;
import ru.privatenull.pncases.support.compat.ServerCapabilities;
import ru.privatenull.pncases.support.compat.ServerVersion;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

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
    private boolean pnLibraryReady;
    private PnRuntime pnRuntime;
    private DeferredRuntimeData runtimeData;
    private final PluginTasks tasks = new PluginTasks(this);
    private DatabaseExecutor databaseExecutor;
    private RewardVisibilityGuard rewardVisibility;
    private final Map<UUID, Integer> guiTransitionOrigins = new HashMap<>();

    @Override
    public void onLoad() {
        try {
            pnLibraryReady = PnLibraryBootstrap.ensureInstalled(this, PnLibraryRuntime.MINIMUM_VERSION);
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE,
                    "Не удалось автоматически подготовить pnLibrary. pnCases не будет запущен.", failure);
        }
        BundledConfigurationResources.capture(this);
        HotReloadClassCache.preload(this);
    }

    @Override
    public void onEnable() {
        if (!pnLibraryReady) {
            getLogger().severe("pnLibrary не подготовлена: проверьте доступ к GitHub и ошибку загрузки выше.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        try {
            pnRuntime = PnLibraryRuntime.connect(this);
        } catch (Throwable failure) {
            getLogger().log(Level.SEVERE,
                    "pnLibrary не удалось включить или подключить к pnCases.", failure);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getServer().getPluginManager().registerEvents(
                new PnLibraryLifecycleListener(this), this);
        if (!ServerVersion.current().isAtLeast(1, 16, 5)) {
            getLogger().severe("pnCases " + getDescription().getVersion()
                    + " поддерживает Minecraft 1.16.5 - 1.21.11. Плагин отключён.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        // Registered first: pnLibrary's task scope serves every task from here on.
        runtimeData = new DeferredRuntimeData(getDescription().getVersion());
        pnRuntime.register(() -> database == null ? "-" : database.type().name(), runtimeData);
        tasks.bind(pnRuntime.taskBackend());
        ServerCapabilities.logEnvironment(this);

        BundledConfigurationResources.capture(this);
        BundledConfigurationResources.installExamples(this);
        backupConfigurationOnUpgrade();
        ConfigurationSyncService.Result sync = synchronizeConfiguration();
        if (!sync.successful()) {
            getLogger().severe("Не удалось безопасно обновить конфигурацию: " + sync.error());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        reloadConfig();
        loadItemLocalization();
        economies = EconomyService.create(this);
        databaseExecutor = new DatabaseExecutor(this);
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
        rewardVisibility = new RewardVisibilityGuard(this);
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
        giftLimits = new ru.privatenull.pncases.gift.GiftLimits(this);
        var giftMenu = new KeyGiftMenu(caseManager);
        var exec = new CasesCommand(this, caseManager, machineGui, giftMenu);
        cmd.setExecutor(exec);
        cmd.setTabCompleter(new CasesTabCompleter(caseManager));

        getServer().getPluginManager().registerEvents(
                new CaseGuiListener(caseManager), this
        );
        getServer().getPluginManager().registerEvents(machineGui, this);
        getServer().getPluginManager().registerEvents(giftMenu, this);
        getServer().getPluginManager().registerEvents(
                new ru.privatenull.pncases.workbench.WorkbenchCraftListener(caseManager), this);
        var runtimeListener = new RuntimeListener(this, caseManager, database);
        getServer().getPluginManager().registerEvents(runtimeListener, this);
        runtimeListener.deliverPendingToOnlinePlayers();

        runtimeData.set(new CaseRuntimeData(this, caseManager));
        pnRuntime.started(database.type().name(), caseManager.getLoadedCaseCount(), holograms.providerName());
        applyLibraryTranslations();
    }

    @Override
    public void onDisable() {
        closePnCasesGuis();
        if (guiOpenAnimations != null) {
            guiOpenAnimations.shutdown();
        }
        if (guiUpdates != null) guiUpdates.close();
        if (rewardVisibility != null) rewardVisibility.close();
        if (holograms != null) {
            holograms.shutdown();
        }

        if (caseManager != null) {
            caseManager.shutdown();
        }

        // Drain ordered storage work before closing its connections.
        PnRuntime closingRuntime = pnRuntime;
        if (closingRuntime != null) {
            closingRuntime.stopped(database == null ? "-" : database.type().name(),
                    caseManager == null ? 0 : caseManager.getLoadedCaseCount());
        }
        pnRuntime = null;
        tasks.bind(null);
        if (closingRuntime != null) {
            try {
                closingRuntime.close();
            } catch (Exception exception) {
                getLogger().log(Level.WARNING, "Не удалось корректно закрыть контекст pnLibrary", exception);
            }
        }
        if (databaseExecutor != null) databaseExecutor.close();
        if (database != null) database.close();
        if (databaseRouter != null) databaseRouter.close();
    }

    /** All scheduled work of the plugin goes through here. */
    public PluginTasks tasks() {
        return tasks;
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
        if (databaseExecutor == null) throw new IllegalStateException("Database executor is not initialized");
        return databaseExecutor;
    }

    private ru.privatenull.pncases.gift.GiftLimits giftLimits;

    public ru.privatenull.pncases.gift.GiftLimits getGiftLimits() {
        return giftLimits;
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

    public RewardVisibilityGuard getRewardVisibility() {
        return rewardVisibility;
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

    public String updateStatus(String currentVersion) {
        return pnRuntime == null ? "&7проверяется" : pnRuntime.updateStatus(currentVersion);
    }

    public boolean rarityEnabled() {
        return getConfig().getBoolean("features.rarity", true);
    }

    public boolean previewEnabled() {
        return getConfig().getBoolean("features.preview", true);
    }

    /** Whether money amounts are written without a useless ".0" ("500 монет", not "500.0 монет"). */
    public boolean wholeNumbers() {
        return getConfig().getBoolean("features.whole-numbers", true);
    }

    /** Whether openings are recorded and shown in the case menu. */
    public boolean historyEnabled() {
        return getConfig().getBoolean("features.history", true);
    }

    public String giftPermission() {
        String permission = getConfig().getString("key-gifts.permission", "pncases.gift");
        return permission == null || permission.isBlank() ? "pncases.gift" : permission.trim();
    }

    public boolean setFeatureEnabled(String feature, boolean enabled) {
        if (!"rarity".equals(feature) && !"preview".equals(feature) && !"history".equals(feature)
                && !"whole-numbers".equals(feature)) return false;
        String path = "features." + feature;
        Object previous = getConfig().get(path);
        getConfig().set(path, enabled);
        if (new ru.privatenull.pncases.cases.config.CaseConfigRepository(this).saveMainConfig()) return true;
        getConfig().set(path, previous);
        return false;
    }

    public String getSupportDiscord() {
        return pnRuntime == null ? "https://discord.gg/TFdnyvMft" : pnRuntime.supportUrl();
    }

    public ItemLocalization getItemLocalization() {
        return itemLocalization;
    }

public void reloadRuntimeConfig() {
        if (giftLimits != null) giftLimits.load();
        loadItemLocalization();
        applyLibraryTranslations();
        if (economies != null) economies.refresh();
    }

    /** A failed backup is reported but never stops the plugin from starting. */
    private void backupConfigurationOnUpgrade() {
        try {
            ConfigBackup.backupIfVersionChanged(getDataFolder().toPath(), BundledConfigurationResources.DOCUMENTS,
                            getDescription().getVersion(), java.time.Instant.now())
                    .ifPresent(path -> getLogger().info("Версия плагина изменилась: конфиги сохранены в "
                            + getDataFolder().toPath().relativize(path) + " (хранятся 5 последних копий)."));
        } catch (java.io.IOException | RuntimeException failure) {
            getLogger().log(Level.WARNING, "Не удалось создать резервную копию конфигов перед обновлением.", failure);
        }
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

    /** Refines the bundled item names with pnLibrary's table for this server version; failures keep the bundled names. */
    private void applyLibraryTranslations() {
        ItemLocalization target = itemLocalization;
        if (pnRuntime == null || target == null) return;
        pnRuntime.loadTranslations(getServer().getBukkitVersion(), target.locale().id(), target::overlay);
    }

    private void closePnCasesGuis() {
        for (var player : getServer().getOnlinePlayers()) {
            var top = InventoryViewCompat.topInventory(player);
            Object holder = top == null ? null : top.getHolder();
            if (holder instanceof GuiSession || KeyGiftMenu.owns(holder)) {
                player.closeInventory();
            }
        }
    }
}
