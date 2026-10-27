package ru.privatenull.pncases.cases;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.core.AnimationAvailability;
import ru.privatenull.pncases.cases.animation.core.AnimationCatalog;
import ru.privatenull.pncases.cases.config.CaseBlockCodec;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;
import ru.privatenull.pncases.cases.config.CaseDefinitionLoader;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.cases.opening.CaseBlockKey;
import ru.privatenull.pncases.cases.opening.CaseOpeningLocks;
import ru.privatenull.pncases.cases.opening.OpeningCoordinator;
import ru.privatenull.pncases.cases.opening.OpeningReservationService;
import ru.privatenull.pncases.cases.player.PlayerAnimationPreferences;
import ru.privatenull.pncases.cases.reward.RewardDeliveryService;
import ru.privatenull.pncases.cases.reward.RewardPresentationService;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pncases.storage.PendingBatch;
import ru.privatenull.pncases.support.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pncases.support.database.DatabaseExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Entry point for everything case related. It owns the shared services and delegates:
 * {@link CaseRegistry} (loaded cases), {@link CaseAdministration} (config changes),
 * {@link OpeningCoordinator} (openings and reward delivery) and {@link KeyService} (virtual keys).
 */
public class CaseManager {

    public enum UnbindCaseResult {
        REMOVED,
        NOT_FOUND,
        NOT_BOUND,
        SAVE_FAILED,
        REFRESH_FAILED
    }

    public enum BindCaseResult {
        BOUND,
        ALREADY_BOUND,
        NOT_FOUND,
        BLOCK_OCCUPIED,
        SAVE_FAILED,
        REFRESH_FAILED
    }

    public enum CreateCaseResult {
        CREATED,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED,
        RELOAD_FAILED
    }

    public enum CopyCaseResult {
        COPIED,
        SOURCE_NOT_FOUND,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED,
        RELOAD_FAILED
    }

    public enum DeleteCaseResult {
        DELETED,
        NOT_FOUND,
        DELETE_FAILED,
        RELOAD_FAILED
    }

    public interface View {
        ItemStack buildOpenButton(Player player, CaseDefinition definition);

        void open(Player player, CaseDefinition definition);

        void updateKeyBalance(UUID playerId, String caseId, int balance);

        void showBatchResults(Player player, String caseId, List<Reward> rewards);

        void clearPlayer(UUID playerId);

        void clearCase(String caseId);

        void clearAll();
    }

    private final PnCasesPlugin plugin;
    private final CaseRegistry registry = new CaseRegistry();
    private final KeyBalanceCache keyBalances = new KeyBalanceCache(10_000L);
    private final AnimationCatalog animationRegistry;
    private final AnimationAvailability animationAvailability = new AnimationAvailability();
    private final CaseConfigRepository configRepository;
    private final CaseBlockCodec blockCodec;
    private final RewardPresentationService rewardPresentation;
    private final RewardDeliveryService rewardDelivery;
    private final IdleParticleService idleParticles;
    private final ru.privatenull.pncases.cases.proximity.ProximityAnimationService proximityAnimations;
    private final PlayerAnimationPreferences animationPreferences;
    private final CaseOpeningLocks openingLocks;
    private final CaseAdministration administration;
    private final OpeningCoordinator openings;
    private final KeyService keys;
    private final BukkitTask openingBarrierTask;
    private View caseView;

    public CaseManager(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.configRepository = new CaseConfigRepository(plugin);
        this.blockCodec = new CaseBlockCodec();
        this.animationRegistry = new AnimationCatalog(plugin);
        this.rewardPresentation = new RewardPresentationService(plugin);
        CaseDefinitionLoader definitionLoader = new CaseDefinitionLoader(blockCodec, rewardPresentation);
        this.idleParticles = new IdleParticleService(plugin, this);
        this.proximityAnimations = new ru.privatenull.pncases.cases.proximity.ProximityAnimationService(plugin, this);
        CasesDatabase database = plugin.getDatabase();
        DatabaseExecutor databaseExecutor = plugin.getDatabaseExecutor();
        this.animationPreferences = new PlayerAnimationPreferences(plugin, database, databaseExecutor);
        this.openingLocks = new CaseOpeningLocks();
        this.rewardDelivery = new RewardDeliveryService(plugin, database, rewardPresentation);
        this.keys = new KeyService(plugin, registry, database, databaseExecutor);
        this.openings = new OpeningCoordinator(plugin, registry, openingLocks,
                new OpeningReservationService(database, plugin.getLogger()), rewardDelivery,
                database, databaseExecutor, animationRegistry, animationAvailability, animationPreferences, this::view);
        this.administration = new CaseAdministration(plugin, registry, configRepository, blockCodec,
                definitionLoader, new CaseAdministration.Effects() {
                    @Override
                    public void reloaded(CaseRegistry loaded) {
                        applyReload(loaded);
                    }

                    @Override
                    public void refreshed(CaseDefinition previous, CaseDefinition updated,
                                          boolean refreshHologram, boolean refreshShowcase) {
                        applyRefresh(previous, updated, refreshHologram, refreshShowcase);
                    }
                });
        this.openingBarrierTask = plugin.tasks().timer(openings::repelPlayersFromActiveCases, 1L, 2L);
    }

    public PnCasesPlugin getPlugin() { return plugin; }

    public RewardPresentationService getRewardPresentation() { return rewardPresentation; }

    public void setCaseView(View caseView) {
        this.caseView = Objects.requireNonNull(caseView, "caseView");
    }

    public void shutdown() {
        openings.markShuttingDown();
        openingBarrierTask.cancel();
        animationRegistry.shutdownAll();
        openings.shutdown();
        idleParticles.shutdown();
        proximityAnimations.shutdown();
        openingLocks.clear();
        registry.clear();
        keyBalances.clear();
        animationPreferences.clear();
    }

    public void onPlayerQuit(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        openings.onPlayerQuit(playerId);
        CaseBlockKey selected = openingLocks.clearSelection(playerId);
        if (selected != null) openingLocks.unlock(playerId, selected);
        animationPreferences.clearPlayer(playerId);
        keyBalances.forget(playerId);
        if (caseView != null) caseView.clearPlayer(playerId);
        plugin.clearGuiTransition(playerId);
    }

    public void onWorldLoad(World world) {
        if (world == null) return;
        String worldName = world.getName();
        for (String caseName : new ArrayList<>(registry.names())) {
            ConfigurationSection section = registry.section(caseName);
            if (section == null) continue;
            boolean belongsToWorld = blockCodec.readConfiguredBlocks(section).stream()
                    .map(block -> String.valueOf(block.getOrDefault("world", "")))
                    .anyMatch(worldName::equalsIgnoreCase);
            if (belongsToWorld) administration.refreshCaseFromConfig(caseName, true, true);
        }
    }

    public void onWorldUnload(World world) {
        if (world == null) return;
        try {
            openings.onWorldUnload(world);
        } finally {
            idleParticles.removeWorldDisplays(world);
            proximityAnimations.removeWorldDisplays(world);
        }
    }

    // --- registry ---

    public List<String> getCaseNames() { return new ArrayList<>(registry.names()); }

    public List<CaseDefinition> getCases() { return registry.sorted(); }

    public List<String> getConfiguredCaseNames() { return configRepository.configuredNames(); }

    public List<String> getKeyNames() { return new ArrayList<>(registry.sortedNames()); }

    public boolean keyExists(String keyId) { return registry.contains(keyId); }

    public int getLoadedCaseCount() { return registry.size(); }

    public int getConfiguredKeyCount() { return getKeyNames().size(); }

    public CaseDefinition getCaseByName(String name) { return registry.byName(name); }

    public ConfigurationSection getCaseSection(String name) { return registry.section(name); }

    /** Whether the keys of this case may be gifted to other players. Cases are giftable unless turned off. */
    public boolean giftable(String caseName) {
        ConfigurationSection root = getCaseSection(caseName);
        ConfigurationSection gift = root == null ? null : root.getConfigurationSection("gift");
        return gift == null || gift.getBoolean("enabled", true);
    }

    public CaseDefinition getCaseByBlock(Block block) { return registry.byBlock(CaseBlockKey.of(block)); }

    public boolean caseExists(String caseName) { return configRepository.exists(caseName); }

    // --- administration ---

    public boolean updateCaseConfig(String caseName, Consumer<ConfigurationSection> updater) {
        return administration.updateCaseConfig(caseName, updater, true, true);
    }

    public boolean updateCaseConfig(
            String caseName,
            Consumer<ConfigurationSection> updater,
            boolean refreshHologram,
            boolean refreshShowcase
    ) {
        return administration.updateCaseConfig(caseName, updater, refreshHologram, refreshShowcase);
    }

    public CopyCaseResult copyCase(String fromCase, String toCase) { return administration.copyCase(fromCase, toCase); }

    /** Number of saved changes of a case that can still be undone. */
    public int undoDepth(String caseName) { return administration.undoDepth(caseName); }

    public boolean undoLastCaseChange(String caseName) { return administration.undoLastChange(caseName); }

    public CreateCaseResult createCustomCase(String caseName) { return administration.createCustomCase(caseName); }

    public DeleteCaseResult deleteCase(String caseName) { return administration.deleteCase(caseName); }

    public BindCaseResult bindCaseToBlock(String caseName, Block target) {
        return administration.bindCaseToBlock(caseName, target);
    }

    public UnbindCaseResult unbindCaseFromBlock(String caseName) {
        return administration.unbindCaseFromBlock(caseName);
    }

    public boolean reloadFromConfig() { return administration.reloadFromConfig(); }

    private void applyReload(CaseRegistry loaded) {
        var holograms = plugin.getHolograms();
        if (holograms != null) {
            holograms.syncCases(loaded.all());
            openings.hideActiveOpeningHolograms(holograms, null);
        }
        idleParticles.syncCases(loaded.all());
        proximityAnimations.syncCases(loaded.all());
        if (caseView != null) caseView.clearAll();
        closePnCasesGuis(null, true);
    }

    private void applyRefresh(CaseDefinition previous, CaseDefinition updated,
                              boolean refreshHologram, boolean refreshShowcase) {
        String name = updated.name();
        if (caseView != null) caseView.clearCase(name);
        if (refreshHologram) {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                holograms.refreshCase(previous, updated);
                openings.hideActiveOpeningHolograms(holograms, name);
            }
        }
        if (refreshShowcase) {
            idleParticles.refreshCase(previous, updated);
            proximityAnimations.refreshCase(previous, updated);
        }
        closePnCasesGuis(name, false);
    }

    private void closePnCasesGuis(String caseName, boolean includeMachine) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = InventoryViewCompat.topInventory(player);
            Object holder = top == null ? null : top.getHolder();
            boolean matchingSession = holder instanceof GuiSession session
                    && (caseName == null || caseName.equalsIgnoreCase(session.caseName()))
                    && (includeMachine || !session.machine());
            if (matchingSession || includeMachine && ru.privatenull.pncases.gui.gift.KeyGiftMenu.owns(holder)) {
                if (plugin.getGuiOpenAnimations() != null) plugin.getGuiOpenAnimations().cancel(player);
                player.closeInventory();
            }
        }
    }

    // --- animations ---

    public AnimationCatalog getAnimationCatalog() { return animationRegistry; }

    public AnimationType getPlayerAnimation(UUID uuid, String caseId) {
        return animationPreferences.get(uuid, caseId);
    }

    /** The case itself and, for a bundle, every case inside it: they share one animation choice. */
    private List<String> animationTargets(String caseId) {
        List<String> targets = new ArrayList<>();
        targets.add(caseId);
        CaseDefinition definition = registry.byName(caseId);
        if (definition != null) {
            for (CaseDefinition child : bundleCases(definition)) targets.add(child.name());
        }
        return targets;
    }

    public void setPlayerAnimation(UUID uuid, String caseId, AnimationType type) {
        for (String target : animationTargets(caseId)) animationPreferences.set(uuid, target, type);
    }

    public Set<AnimationType> getPlayerFavoriteAnimations(UUID uuid, String caseId) {
        return animationPreferences.favorites(uuid, caseId);
    }

    public void cacheAnimationPreferences(
            UUID uuid,
            String caseId,
            AnimationType selected,
            Set<AnimationType> favorites
    ) {
        animationPreferences.cache(uuid, caseId, selected, favorites);
    }

    public boolean togglePlayerFavoriteAnimation(UUID uuid, String caseId, AnimationType type) {
        boolean result = animationPreferences.toggleFavorite(uuid, caseId, type);
        List<String> targets = animationTargets(caseId);
        for (String target : targets.subList(1, targets.size())) {
            // The cases inside follow the bundle: the same type ends up in the same state.
            if (animationPreferences.favorites(uuid, target).contains(type) != result) {
                animationPreferences.toggleFavorite(uuid, target, type);
            }
        }
        return result;
    }

    public void clearPlayerFavoriteAnimations(UUID uuid, String caseId) {
        for (String target : animationTargets(caseId)) animationPreferences.clearFavorites(uuid, target);
    }

    /**
     * Whether {@code type} can run where the case stands: animations with real mobs need a world that is not on
     * Peaceful and a point where mobs may appear. Other animations always can.
     */
    public AnimationAvailability.Availability animationAvailability(CaseDefinition definition, AnimationType type) {
        if (definition == null) return AnimationAvailability.Availability.OK;
        return animationAvailability.check(type, definition.blockLocation());
    }

    // --- GUI ---

    public ItemStack buildGuiOpenItem(Player p, CaseDefinition def) {
        return view().buildOpenButton(p, def);
    }

    public void updateCachedKeyBalance(UUID playerId, String caseId, int balance) {
        keyBalances.put(playerId, caseId, balance);
        view().updateKeyBalance(playerId, caseId, Math.max(0, balance));
    }

    /** Last known key balances, also used by the PlaceholderAPI placeholders. */
    public KeyBalanceCache getKeyBalances() { return keyBalances; }

    public void openCaseGui(Player p, CaseDefinition def) {
        CaseBlockKey selected = openingLocks.selected(p.getUniqueId());
        if (selected == null || !registry.serves(selected, def.name())) {
            CaseBlockKey fallback = def.blockLocation() == null ? null : CaseBlockKey.of(def.blockLocation());
            if (fallback != null && registry.serves(fallback, def.name())) {
                openingLocks.select(p.getUniqueId(), fallback);
            } else {
                openingLocks.clearSelection(p.getUniqueId());
            }
        }
        view().open(p, menuCase(p, def));
    }

    /**
     * The case whose menu shows {@code def}: a case inside a bundle is picked in the menu of the bundle, so
     * "back" from its rewards, quantity and animation screens returns there. Otherwise it is {@code def}.
     */
    private CaseDefinition menuCase(Player p, CaseDefinition def) {
        CaseBlockKey selected = openingLocks.selected(p.getUniqueId());
        String owner = selected == null ? null : registry.ownerOf(selected);
        if (owner == null || owner.equals(def.name()) || !registry.serves(selected, def.name())) return def;
        CaseDefinition bundle = registry.byName(owner);
        return bundle == null || bundleCases(bundle).isEmpty() ? def : bundle;
    }

    public void openCaseGui(Player p, CaseDefinition def, Block sourceBlock) {
        if (sourceBlock != null) {
            CaseBlockKey source = CaseBlockKey.of(sourceBlock);
            if (registry.serves(source, def.name())) {
                openingLocks.select(p.getUniqueId(), source);
            }
        }
        openCaseGui(p, def);
    }

    // --- bundle cases ---

    /** The cases a bundle lists that are loaded right now, in the order of the bundle. */
    public List<CaseDefinition> bundleCases(CaseDefinition bundle) {
        List<CaseDefinition> cases = new ArrayList<>();
        for (String child : bundle.children()) {
            CaseDefinition definition = registry.byName(child);
            if (definition != null) cases.add(definition);
        }
        return cases;
    }

    private View view() {
        if (caseView == null) {
            throw new IllegalStateException("Case view is not configured");
        }
        return caseView;
    }

    // --- openings and rewards ---

    public void tryOpenCase(Player p, CaseDefinition def) { openings.tryOpenCases(p, def, 1); }

    public void tryOpenCases(Player player, CaseDefinition definition, int amount) {
        openings.tryOpenCases(player, definition, amount);
    }

    static PendingBatch selectBatch(CaseDefinition definition, int progress, int amount) {
        return OpeningReservationService.selectBatch(definition, progress, amount);
    }

    public void deliverPendingRewardAsync(Player player, Consumer<Boolean> completion) {
        openings.deliverPendingRewardAsync(player, completion);
    }

    public boolean giveReward(Player p, Reward reward) { return rewardDelivery.deliver(p, null, reward); }

    public boolean giveReward(Player p, CaseDefinition def, Reward reward) {
        return rewardDelivery.deliver(p, def, reward);
    }

    // --- keys ---

    public void giveKey(Player p, String keyId, int amount) {
        keys.give(p, keyId, amount, balance -> updateCachedKeyBalance(p.getUniqueId(), keyId.toLowerCase(java.util.Locale.ROOT), balance));
    }

    /** Gives keys and runs {@code onDone} on the main thread when they are stored, or {@code onFailure} when not. */
    public void giveKey(Player p, String keyId, int amount, Runnable onDone,
                        java.util.function.Consumer<Throwable> onFailure) {
        String id = keyId.toLowerCase(java.util.Locale.ROOT);
        keys.give(p, id, amount, balance -> {
            updateCachedKeyBalance(p.getUniqueId(), id, balance);
            onDone.run();
        }, onFailure);
    }

    public String getKeyDisplayName(String keyId) { return keys.displayName(keyId); }

    /** All key recipes of a case, in the order they are written; empty when the case has none. */
    public java.util.List<ru.privatenull.pncases.workbench.WorkbenchRecipe> workbenchRecipes(String caseName) {
        ConfigurationSection root = getCaseSection(caseName);
        return ru.privatenull.pncases.workbench.WorkbenchRecipe.readAll(
                root == null ? null : root.getConfigurationSection("workbench"));
    }

    // --- busy state ---

    public boolean isOpening(UUID playerId) { return openingLocks.isOpening(playerId); }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer) {
        CaseBlockKey key = def.blockLocation() == null ? null : CaseBlockKey.of(def.blockLocation());
        return openingLocks.isBusy(key, viewer);
    }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer, Block block) {
        CaseBlockKey key = block == null ? null : CaseBlockKey.of(block);
        return openingLocks.isBusy(key, viewer);
    }

    public boolean isCaseBlockBusy(org.bukkit.Location blockLocation) {
        return blockLocation != null && openingLocks.isBusy(CaseBlockKey.of(blockLocation));
    }
}
