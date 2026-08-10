package ru.privatenull;

import org.bstats.bukkit.Metrics;
import org.bstats.charts.SimplePie;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.cases.CaseManager;
import ru.privatenull.commands.CasesCommand;
import ru.privatenull.commands.CasesTabCompleter;
import ru.privatenull.config.ConfigValidator;
import ru.privatenull.config.UiConfig;
import ru.privatenull.gui.GuiSession;
import ru.privatenull.gui.caseview.CaseGuiListener;
import ru.privatenull.gui.caseview.CaseMenuService;
import ru.privatenull.gui.machine.MachineGuiListener;
import ru.privatenull.hologram.HologramService;
import ru.privatenull.integrations.PlayerPointsProvider;
import ru.privatenull.integrations.VaultEconomyProvider;
import ru.privatenull.listeners.RuntimeListener;
import ru.privatenull.modules.ModuleManager;
import ru.privatenull.modules.ModuleId;
import ru.privatenull.pnlibrary.database.DatabaseSettings;
import ru.privatenull.pnlibrary.database.JdbcSettings;
import ru.privatenull.pnlibrary.database.MongoSettings;
import ru.privatenull.pnlibrary.gui.GuiOpenAnimationService;
import ru.privatenull.pnlibrary.gui.GuiUpdateService;
import ru.privatenull.pnlibrary.gui.protocollib.ProtocolLibSlotPacketSender;
import ru.privatenull.pnlibrary.lifecycle.PluginBanner;
import ru.privatenull.pnlibrary.update.UpdateChecker;
import ru.privatenull.pnlibrary.update.UpdateSettings;
import ru.privatenull.storage.*;
import ru.privatenull.pnlibrary.compat.ServerCapabilities;
import ru.privatenull.pnlibrary.compat.ServerVersion;

public final class PnCasesPlugin extends JavaPlugin {

    public static final String SUPPORT_DISCORD = "https://discord.gg/rRbzq6cnc6";
    private static final String GITHUB_REPOSITORY = "Dy6HiLa/pnCases";
    private static final int BSTATS_PLUGIN_ID = 32592;

    private CaseManager caseManager;
    private HologramService holograms;
    private VaultEconomyProvider vaultEconomy;
    private PlayerPointsProvider playerPoints;
    private UiConfig ui;
    private GuiUpdateService guiUpdates;
    private AutoCloseable guiPacketSender;
    private GuiOpenAnimationService guiOpenAnimations;
    private CasesDatabase database;
    private UpdateChecker updateChecker;
    private ModuleManager modules;

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
        validateCurrentConfig();
        modules = ModuleManager.defaults(this);
        modules.reload();

        try {
            database = createDatabase();
            database.open(getDataFolder());
            getLogger().info("Database connected: " + database.type());
        } catch (Exception exception) {
            getLogger().severe("Could not initialize pnCases database: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ui = new UiConfig(this);
        GuiUpdateService.SlotPacketSender packetSender = null;
        if (getServer().getPluginManager().isPluginEnabled("ProtocolLib")) {
            try {
                ProtocolLibSlotPacketSender protocolSender = new ProtocolLibSlotPacketSender(this);
                packetSender = protocolSender;
                guiPacketSender = protocolSender;
            } catch (RuntimeException | LinkageError exception) {
                getLogger().warning("Не удалось включить ProtocolLib SET_SLOT: " + exception.getMessage());
            }
        }
        guiUpdates = new GuiUpdateService(packetSender);
        guiOpenAnimations = new GuiOpenAnimationService(this, guiUpdates);
        holograms = new HologramService(this);

        caseManager = new CaseManager(this);
        caseManager.setCaseView(new CaseMenuService(this, caseManager));
        caseManager.reloadFromConfig();
        setupUpdateChecker();
        Metrics metrics = new Metrics(this, BSTATS_PLUGIN_ID);
        metrics.addCustomChart(new SimplePie("database_type", () -> database.type().name()));

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

        PluginBanner.enabled(this, SUPPORT_DISCORD);
    }

    @Override
    public void onDisable() {
        closePnCasesGuis();
        if (modules != null) {
            modules.shutdown();
        }
        if (guiOpenAnimations != null) {
            guiOpenAnimations.shutdown();
        }
        if (guiPacketSender != null) {
            try {
                guiPacketSender.close();
            } catch (Exception exception) {
                getLogger().warning("Не удалось остановить ProtocolLib SET_SLOT: " + exception.getMessage());
            }
        }
        if (updateChecker != null) {
            updateChecker.cancel();
        }

        if (holograms != null) {
            holograms.shutdown();
        }

        if (caseManager != null) {
            caseManager.shutdown();
        }

        if (database != null) {
            database.close();
        }

        PluginBanner.disabled(this, SUPPORT_DISCORD);
    }

    public CaseManager getCaseManager() {
        return caseManager;
    }

    public CasesDatabase getDatabase() {
        return database;
    }

    public HologramService getHolograms() {
        return holograms;
    }

    public VaultEconomyProvider getVaultEconomy() {
        if (vaultEconomy == null && getServer().getPluginManager().isPluginEnabled("Vault")) {
            try {
                vaultEconomy = new VaultEconomyProvider(this);
            } catch (RuntimeException | LinkageError ex) {
                getLogger().warning("Не удалось подключить Vault: " + ex.getMessage());
                vaultEconomy = null;
            }
        }
        return vaultEconomy;
    }

    public PlayerPointsProvider getPlayerPoints() {
        if (playerPoints == null && getServer().getPluginManager().isPluginEnabled("PlayerPoints")) {
            try {
                playerPoints = new PlayerPointsProvider();
            } catch (RuntimeException | LinkageError ex) {
                getLogger().warning("Не удалось подключить PlayerPoints: " + ex.getMessage());
                playerPoints = null;
            }
        }
        return playerPoints;
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

    public void openGui(Player player, Inventory inventory) {
        if (guiOpenAnimations == null || modules == null || !modules.isEnabled(ModuleId.GUI_ANIMATION)) {
            if (guiOpenAnimations != null) {
                guiOpenAnimations.cancel(player);
            }
            player.openInventory(inventory);
            return;
        }
        guiOpenAnimations.open(player, inventory, modules.isEnabled(ModuleId.TITLE));
    }

    public UpdateChecker getUpdateChecker() {
        return updateChecker;
    }

    public ModuleManager getModules() {
        return modules;
    }

    public String getSupportDiscord() {
        return SUPPORT_DISCORD;
    }

    public void reloadRuntimeConfig() {
        if (modules != null) {
            modules.reload();
        }
        if (updateChecker != null) {
            updateChecker.restart(updateSettings());
        }
    }

    public ConfigValidator.Result validateCurrentConfig() {
        return ConfigValidator.validateAndPatch(this);
    }

    private void setupUpdateChecker() {
        updateChecker = new UpdateChecker(this, updateSettings());
        updateChecker.start();
    }

    private UpdateSettings updateSettings() {
        return new UpdateSettings(true, GITHUB_REPOSITORY, "pncases.admin", 6L, SUPPORT_DISCORD);
    }

    private CasesDatabase createDatabase() {
        DatabaseSettings settings = DatabaseSettings.from(getConfig().getConfigurationSection("database"), getDataFolder());
        if (settings instanceof JdbcSettings jdbc) return new JdbcCasesDatabase(jdbc);
        if (settings instanceof MongoSettings mongo) return new MongoCasesDatabase(mongo);
        throw new IllegalStateException("Unsupported database type: " + settings.type());
    }

    private void closePnCasesGuis() {
        for (var player : getServer().getOnlinePlayers()) {
            var top = player.getOpenInventory().getTopInventory();
            Object holder = top == null ? null : top.getHolder();
            if (holder instanceof GuiSession) {
                player.closeInventory();
            }
        }
    }
}
