package ru.privatenull.pncases.cases;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.animation.core.AnimationCatalog;
import ru.privatenull.pncases.cases.animation.core.AnimationSession;
import ru.privatenull.pncases.cases.animation.core.AbstractCaseAnimation;
import ru.privatenull.pncases.cases.config.CaseBlockCodec;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;
import ru.privatenull.pncases.cases.config.CaseDefinitionLoader;
import ru.privatenull.pncases.cases.model.AnimationType;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.model.Reward;
import ru.privatenull.pncases.cases.reward.RewardDeliveryService;
import ru.privatenull.pncases.cases.reward.RewardPresentationService;
import ru.privatenull.pncases.gui.GuiSession;
import ru.privatenull.pnlibrary.compat.BukkitCompat.InventoryViewCompat;
import ru.privatenull.pnlibrary.text.ColorUtil;
import ru.privatenull.pnlibrary.database.DatabaseExecutor;
import ru.privatenull.pncases.storage.CasesDatabase;
import ru.privatenull.pnlibrary.compat.ServerVersion;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.logging.Level;

public class CaseManager {

    private static final long ANIMATION_WATCHDOG_TICKS = 20L * 120L;
    private static final double SPECTATOR_REPEL_RADIUS = 5.0D;
    private static final double OPENER_REPEL_RADIUS = 3.0D;

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

        void clearPlayer(UUID playerId);

        void clearCase(String caseId);

        void clearAll();
    }

    private final PnCasesPlugin plugin;

    private final Map<String, CaseDefinition> casesByName = new HashMap<>();
    private final Map<BlockKey, String> caseByBlock = new HashMap<>();
    private final Map<String, ConfigurationSection> caseSections = new HashMap<>();

    private final Set<UUID> openingPlayers = ConcurrentHashMap.newKeySet();
    private final Map<BlockKey, UUID> busyCases = new ConcurrentHashMap<>();
    private final Map<UUID, BlockKey> selectedCaseBlocks = new ConcurrentHashMap<>();
    private final Map<PlayerAnimationKey, AnimationType> playerAnimations = new ConcurrentHashMap<>();
    private final Map<UUID, ActiveOpening> activeOpenings = new ConcurrentHashMap<>();
    private final Set<UUID> pendingDeliveries = ConcurrentHashMap.newKeySet();

    private final AnimationCatalog animationRegistry;
    private final CaseConfigRepository configRepository;
    private final CaseBlockCodec blockCodec;
    private final CaseDefinitionLoader definitionLoader;
    private final RewardPresentationService rewardPresentation;
    private final RewardDeliveryService rewardDelivery;
    private View caseView;
    private final IdleParticleService idleParticles;
    private final CasesDatabase database;
    private final DatabaseExecutor databaseExecutor;
    private final BukkitTask openingBarrierTask;
    private volatile boolean shuttingDown;

    public CaseManager(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.configRepository = new CaseConfigRepository(plugin);
        this.blockCodec = new CaseBlockCodec();
        this.animationRegistry = new AnimationCatalog(plugin);
        this.rewardPresentation = new RewardPresentationService(plugin);
        this.definitionLoader = new CaseDefinitionLoader(blockCodec, rewardPresentation);
        this.idleParticles = new IdleParticleService(plugin, this);
        this.database = plugin.getDatabase();
        this.databaseExecutor = plugin.getDatabaseExecutor();
        this.rewardDelivery = new RewardDeliveryService(plugin, database, rewardPresentation);
        this.openingBarrierTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::repelPlayersFromActiveCases, 1L, 2L);
    }

    public PnCasesPlugin getPlugin() { return plugin; }

    public RewardPresentationService getRewardPresentation() { return rewardPresentation; }

    public void setCaseView(View caseView) {
        this.caseView = Objects.requireNonNull(caseView, "caseView");
    }

    public void shutdown() {
        shuttingDown = true;
        openingBarrierTask.cancel();
        animationRegistry.shutdownAll();
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            completeOpening(opening, false, null);
        }
        idleParticles.shutdown();
        openingPlayers.clear();
        busyCases.clear();
        activeOpenings.clear();
        casesByName.clear();
        caseByBlock.clear();
        caseSections.clear();
        selectedCaseBlocks.clear();
        playerAnimations.clear();
    }

    public void onPlayerQuit(Player player) {
        if (player == null) return;
        UUID playerId = player.getUniqueId();
        ActiveOpening opening = activeOpenings.get(playerId);
        if (opening != null) cancelAnimationSession(opening, false);
        BlockKey selected = selectedCaseBlocks.remove(playerId);
        if (selected != null) unlockCase(selected, playerId);
        playerAnimations.keySet().removeIf(key -> key.playerId().equals(playerId));
        if (caseView != null) caseView.clearPlayer(playerId);
        plugin.clearGuiTransition(playerId);
    }

    public void onWorldLoad(World world) {
        if (world == null) return;
        String worldName = world.getName();
        for (String caseName : new ArrayList<>(casesByName.keySet())) {
            ConfigurationSection section = caseSections.get(caseName);
            if (section == null) continue;
            boolean belongsToWorld = blockCodec.readConfiguredBlocks(section).stream()
                    .map(block -> String.valueOf(block.getOrDefault("world", "")))
                    .anyMatch(worldName::equalsIgnoreCase);
            if (belongsToWorld) refreshCaseFromConfig(caseName, true, true);
        }
    }

    public List<String> getCaseNames() { return new ArrayList<>(casesByName.keySet()); }

    private void repelPlayersFromActiveCases() {
        if (activeOpenings.isEmpty()) return;
        for (ActiveOpening opening : activeOpenings.values()) {
            if (opening.completed.get() || opening.caseBlockLocation == null) continue;
            World world = Bukkit.getWorld(opening.worldId);
            if (world == null) continue;

            Location center = opening.caseBlockLocation.clone().add(0.5D, 0.0D, 0.5D);
            for (Player target : world.getPlayers()) {
                if (!target.isOnline() || target.isDead()) continue;
                double radius = target.getUniqueId().equals(opening.playerId)
                        ? OPENER_REPEL_RADIUS : SPECTATOR_REPEL_RADIUS;
                repelIfTooClose(target, center, radius);
            }
        }
    }

    private void repelIfTooClose(Player player, Location center, double radius) {
        Location location = player.getLocation();
        double dx = location.getX() - center.getX();
        double dz = location.getZ() - center.getZ();
        if (dx * dx + dz * dz >= radius * radius) return;

        Vector outward = new Vector(dx, 0.0D, dz);
        if (outward.lengthSquared() < 0.0001D) {
            outward = player.getLocation().getDirection().multiply(-1.0D);
            outward.setY(0.0D);
        }
        if (outward.lengthSquared() < 0.0001D) outward = new Vector(1.0D, 0.0D, 0.0D);
        outward.normalize().multiply(0.75D).setY(0.12D);
        player.setVelocity(outward);
        player.setFallDistance(0.0F);
    }
    public List<String> getConfiguredCaseNames() {
        return configRepository.configuredNames();
    }

    public List<String> getKeyNames() {
        return new ArrayList<>(new TreeSet<>(casesByName.keySet()));
    }

    public boolean keyExists(String keyId) {
        return keyId != null && casesByName.containsKey(keyId.toLowerCase(Locale.ROOT));
    }

    public int getLoadedCaseCount() { return casesByName.size(); }
    public int getConfiguredKeyCount() { return getKeyNames().size(); }

    public CaseDefinition getCaseByName(String name) {
        return name == null ? null : casesByName.get(name.toLowerCase(Locale.ROOT));
    }
    public ConfigurationSection getCaseSection(String name) {
        return name == null ? null : caseSections.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean updateCaseConfig(String caseName, Consumer<ConfigurationSection> updater) {
        return updateCaseConfig(caseName, updater, true, true);
    }

    public boolean updateCaseConfig(
            String caseName,
            Consumer<ConfigurationSection> updater,
            boolean refreshHologram,
            boolean refreshShowcase
    ) {
        if (caseName == null || caseName.isBlank() || updater == null) {
            return false;
        }

        if (!configRepository.update(caseName, updater)) return false;
        return refreshCaseFromConfig(caseName, refreshHologram, refreshShowcase);
    }

    public boolean caseExists(String caseName) {
        return configRepository.exists(caseName);
    }

    public CreateCaseResult createCustomCase(String caseName) {
        if (!CaseConfigRepository.isValidId(caseName)) return CreateCaseResult.INVALID_ID;
        if (configRepository.exists(caseName)) return CreateCaseResult.ALREADY_EXISTS;

        CaseConfigRepository.CreateResult result = configRepository.create(caseName);
        if (result == CaseConfigRepository.CreateResult.CREATED) {
            return reloadFromConfig() ? CreateCaseResult.CREATED : CreateCaseResult.RELOAD_FAILED;
        }
        return CreateCaseResult.valueOf(result.name());
    }

    public DeleteCaseResult deleteCase(String caseName) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        if (!CaseConfigRepository.isValidId(normalized) || !configRepository.exists(normalized)) {
            return DeleteCaseResult.NOT_FOUND;
        }
        if (!configRepository.delete(normalized)) {
            return DeleteCaseResult.DELETE_FAILED;
        }

        return reloadFromConfig() ? DeleteCaseResult.DELETED : DeleteCaseResult.RELOAD_FAILED;
    }

    public CaseDefinition getCaseByBlock(Block block) {
        String name = caseByBlock.get(BlockKey.of(block));
        return name == null ? null : casesByName.get(name);
    }

    public AnimationCatalog getAnimationCatalog() { return animationRegistry; }

    public AnimationType getPlayerAnimation(UUID uuid, String caseId) {
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            return AnimationType.FORTUNE_RING;
        }
        PlayerAnimationKey key = new PlayerAnimationKey(uuid, caseId);
        AnimationType cached = playerAnimations.putIfAbsent(key, AnimationType.ANVIL);
        if (cached == null) {
            databaseExecutor.completeSync(
                    databaseExecutor.supply(uuid, () -> database.getAnimation(uuid, key.caseId())),
                    loaded -> playerAnimations.replace(key, AnimationType.ANVIL, loaded),
                    failure -> plugin.getLogger().log(Level.WARNING,
                            "Не удалось загрузить выбранную анимацию игрока " + uuid + ".", failure));
            return AnimationType.ANVIL;
        }
        return cached;
    }

    public void setPlayerAnimation(UUID uuid, String caseId, AnimationType type) {
        if (type == null) return;
        if (ServerVersion.current().isReleaseLine(1, 16)) {
            type = AnimationType.FORTUNE_RING;
        }
        PlayerAnimationKey key = new PlayerAnimationKey(uuid, caseId);
        playerAnimations.put(key, type);
        AnimationType selected = type;
        databaseExecutor.completeSync(
                databaseExecutor.run(uuid, () -> database.setAnimation(uuid, key.caseId(), selected)),
                ignored -> { },
                failure -> plugin.getLogger().log(Level.SEVERE,
                        "Не удалось сохранить выбранную анимацию игрока " + uuid + ".", failure));
    }

    public void warnAboutMobAnimation(Player player, AnimationType type) {
        if (player == null || !player.hasPermission("pncases.admin")) return;
        if (type != AnimationType.MOB_HUNT && type != AnimationType.PILLAGER_RAID) return;
        player.sendMessage(plugin.getUi().get("animation-mob-warning-self"));
    }

    public BindCaseResult bindCaseToBlock(String caseName, Block target) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        if (target == null || !configRepository.exists(normalized)) return BindCaseResult.NOT_FOUND;

        BlockKey targetKey = BlockKey.of(target);
        String owner;
        try {
            owner = configuredOwner(targetKey, normalized);
        } catch (IllegalStateException ex) {
            plugin.getLogger().severe(ex.getMessage());
            return BindCaseResult.REFRESH_FAILED;
        }
        if (owner != null && !owner.equals(normalized)) return BindCaseResult.BLOCK_OCCUPIED;
        if (normalized.equals(owner)) return BindCaseResult.ALREADY_BOUND;

        boolean saved = configRepository.update(normalized, cs -> {
            List<Map<String, Object>> blocks = blockCodec.readConfiguredBlocks(cs);
            blockCodec.addBlock(blocks, Map.of(
                    "world", target.getWorld().getName(),
                    "x", target.getX(),
                    "y", target.getY(),
                    "z", target.getZ()
            ));
            cs.set("block", null);
            cs.set("blocks", blocks);
        });
        if (!saved) return BindCaseResult.SAVE_FAILED;
        return refreshCaseFromConfig(normalized, true, true)
                ? BindCaseResult.BOUND
                : BindCaseResult.REFRESH_FAILED;
    }

    private String configuredOwner(BlockKey target, String requestedCase) {
        CaseConfigRepository.LoadResult loadResult = configRepository.loadAll();
        if (!loadResult.successful()) {
            throw new IllegalStateException("Нельзя привязать блок: в конфигах кейсов есть ошибки id, дубликаты или повреждённый YAML.");
        }

        String requestedOwner = null;
        for (CaseConfigRepository.Source source : loadResult.sources()) {
            for (Map<String, Object> configured : blockCodec.readConfiguredBlocks(source.section())) {
                BlockKey configuredKey = blockKey(configured);
                if (!target.equals(configuredKey)) continue;
                if (!source.name().equals(requestedCase)) return source.name();
                requestedOwner = source.name();
            }
        }
        return requestedOwner;
    }

    private BlockKey blockKey(Map<String, Object> configured) {
        if (configured == null) return null;
        return new BlockKey(
                String.valueOf(configured.getOrDefault("world", "")),
                integer(configured.get("x")),
                integer(configured.get("y")),
                integer(configured.get("z"))
        );
    }

    private int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }

    public UnbindCaseResult unbindCaseFromBlock(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return UnbindCaseResult.NOT_FOUND;
        }

        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.findSource(normalized);
        if (source == null || source.section() == null) {
            return UnbindCaseResult.NOT_FOUND;
        }

        if (blockCodec.readConfiguredBlocks(source.section()).isEmpty()) {
            return UnbindCaseResult.NOT_BOUND;
        }

        boolean saved = configRepository.update(normalized, section -> {
            section.set("block", null);
            section.set("blocks", null);
        });
        if (!saved) return UnbindCaseResult.SAVE_FAILED;
        return refreshCaseFromConfig(normalized, true, true)
                ? UnbindCaseResult.REMOVED
                : UnbindCaseResult.REFRESH_FAILED;
    }

    public boolean reloadFromConfig() {
        Map<String, CaseDefinition> loadedCases = new HashMap<>();
        Map<BlockKey, String> loadedBlocks = new HashMap<>();
        Map<BlockKey, String> configuredBlocks = new HashMap<>();
        Map<String, ConfigurationSection> loadedSections = new HashMap<>();

        CaseConfigRepository.LoadResult loadResult = configRepository.loadAll();
        if (!loadResult.successful()) {
            plugin.getLogger().severe("Reload кейсов отменён: исправьте ошибки id, дубликаты или повреждённые YAML. Старые кейсы продолжают работать.");
            return false;
        }
        try {
            for (CaseConfigRepository.Source source : loadResult.sources()) {
                ConfigurationSection section = source.section();
                if (section == null) throw new IllegalStateException("Пустая секция кейса " + source.name());
                CaseDefinition definition = definitionLoader.load(source.name(), section);
                if (loadedCases.putIfAbsent(definition.name(), definition) != null) {
                    throw new IllegalStateException("Дубликат id кейса '" + definition.name() + "'.");
                }
                loadedSections.put(definition.name(), section);
                for (Map<String, Object> configured : blockCodec.readConfiguredBlocks(section)) {
                    BlockKey blockKey = blockKey(configured);
                    String previousOwner = configuredBlocks.putIfAbsent(blockKey, definition.name());
                    if (previousOwner != null && !previousOwner.equals(definition.name())) {
                        throw new IllegalStateException("Блок " + blockKey + " одновременно принадлежит кейсам '"
                                + previousOwner + "' и '" + definition.name() + "'.");
                    }
                }
                for (Location location : definition.blockLocations()) {
                    BlockKey blockKey = BlockKey.of(location);
                    String previousOwner = loadedBlocks.putIfAbsent(blockKey, definition.name());
                    if (previousOwner != null && !previousOwner.equals(definition.name())) {
                        throw new IllegalStateException("Блок " + blockKey + " одновременно принадлежит кейсам '"
                                + previousOwner + "' и '" + definition.name() + "'.");
                    }
                }
            }
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Reload кейсов отменён: " + ex.getMessage() + " Старые кейсы продолжают работать.");
            return false;
        }

        casesByName.clear();
        casesByName.putAll(loadedCases);
        caseByBlock.clear();
        caseByBlock.putAll(loadedBlocks);
        caseSections.clear();
        caseSections.putAll(loadedSections);

        var holograms = plugin.getHolograms();
        if (holograms != null) {
            holograms.syncCases(casesByName.values());
            hideActiveOpeningHolograms(holograms, null);
        }
        idleParticles.syncCases(casesByName.values());
        if (caseView != null) caseView.clearAll();
        closePnCasesGuis(null, true);
        plugin.getLogger().info("Loaded cases: " + casesByName.size() + ", active blocks: " + caseByBlock.size()
                + ", virtual keys: " + casesByName.size());
        return true;
    }

    /**
     * Applies an edit made through the machine GUI without rebuilding visuals of
     * unrelated cases.  A full reload is reserved for /pncases reload and world
     * discovery, where rebuilding the complete registry is intentional.
     */
    private boolean refreshCaseFromConfig(String caseName, boolean refreshHologram, boolean refreshShowcase) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.findSource(normalized);
        if (source == null || source.section() == null) {
            return false;
        }

        CaseDefinition previous = casesByName.get(normalized);
        CaseDefinition updated;
        try {
            updated = definitionLoader.load(source.name(), source.section());
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Не удалось обновить кейс '" + normalized + "': " + ex.getMessage());
            return false;
        }

        List<BlockKey> previousBlocks = previous == null
                ? List.of()
                : previous.blockLocations().stream().map(BlockKey::of).toList();
        List<BlockKey> newBlocks = updated.blockLocations().stream().map(BlockKey::of).toList();
        Map<BlockKey, String> updatedBlockMap = replaceOwnedBlocks(
                caseByBlock, normalized, previousBlocks, newBlocks);
        if (updatedBlockMap == null) {
            plugin.getLogger().severe("Кейс '" + normalized
                    + "' не обновлён: один из его блоков уже принадлежит другому кейсу.");
            return false;
        }

        caseByBlock.clear();
        caseByBlock.putAll(updatedBlockMap);
        casesByName.put(updated.name(), updated);
        caseSections.put(updated.name(), source.section());
        if (caseView != null) caseView.clearCase(normalized);

        if (refreshHologram) {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                holograms.refreshCase(previous, updated);
                hideActiveOpeningHolograms(holograms, normalized);
            }
        }
        if (refreshShowcase) {
            idleParticles.refreshCase(previous, updated);
        }
        closePnCasesGuis(normalized, false);
        return true;
    }

    private void closePnCasesGuis(String caseName, boolean includeMachine) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            Inventory top = InventoryViewCompat.topInventory(player);
            Object holder = top == null ? null : top.getHolder();
            boolean matchingSession = holder instanceof GuiSession session
                    && (caseName == null || caseName.equalsIgnoreCase(session.caseName()))
                    && (includeMachine || !session.machine());
            if (matchingSession) {
                if (plugin.getGuiOpenAnimations() != null) plugin.getGuiOpenAnimations().cancel(player);
                player.closeInventory();
            }
        }
    }

    static Map<BlockKey, String> replaceOwnedBlocks(
            Map<BlockKey, String> current,
            String caseName,
            Collection<BlockKey> previous,
            Collection<BlockKey> updated
    ) {
        Map<BlockKey, String> candidate = new HashMap<>(current);
        for (BlockKey block : previous) candidate.remove(block, caseName);
        for (BlockKey block : updated) {
            String owner = candidate.putIfAbsent(block, caseName);
            if (owner != null && !owner.equals(caseName)) return null;
        }
        return candidate;
    }

    public void onWorldUnload(World world) {
        if (world == null) return;
        UUID unloadingWorldId = world.getUID();

        try {
            // Animation instances are shared across worlds. Cleanup is therefore
            // scoped by the tracked world instead of cancelling the whole type.
            animationRegistry.cancelWorld(world);
            for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
                if (belongsToWorld(opening.worldId, unloadingWorldId)) {
                    completeOpening(opening, true, unloadingWorldId);
                }
            }
        } finally {
            idleParticles.removeWorldDisplays(world);
        }
    }

    public ItemStack buildGuiOpenItem(Player p, CaseDefinition def) {
        return view().buildOpenButton(p, def);
    }

    public void updateCachedKeyBalance(UUID playerId, String caseId, int balance) {
        view().updateKeyBalance(playerId, caseId, Math.max(0, balance));
    }

    public void openCaseGui(Player p, CaseDefinition def) {
        BlockKey selected = selectedCaseBlocks.get(p.getUniqueId());
        if (selected == null || !def.name().equals(caseByBlock.get(selected))) {
            BlockKey fallback = def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
            if (fallback != null && def.name().equals(caseByBlock.get(fallback))) {
                selectedCaseBlocks.put(p.getUniqueId(), fallback);
            } else {
                selectedCaseBlocks.remove(p.getUniqueId());
            }
        }
        view().open(p, def);
    }

    public void openCaseGui(Player p, CaseDefinition def, Block sourceBlock) {
        if (sourceBlock != null) {
            BlockKey source = BlockKey.of(sourceBlock);
            if (def.name().equals(caseByBlock.get(source))) {
                selectedCaseBlocks.put(p.getUniqueId(), source);
            }
        }
        openCaseGui(p, def);
    }

    public void tryOpenCase(Player p, CaseDefinition def) {
        UUID playerId = p.getUniqueId();
        if (openingPlayers.contains(playerId) || pendingDeliveries.contains(playerId)) {
            p.sendMessage(plugin.getUi().get("already-opening"));
            return;
        }
        BlockKey sourceBlock = selectedBlockKey(p, def);
        PreparedOpening prepared = prepareOpening(p, def, sourceBlock);
        if (prepared == null) {
            releaseOpeningState(p.getUniqueId(), sourceBlock);
            return;
        }
        if (!tryLockCase(p, sourceBlock)) {
            p.sendMessage(plugin.getUi().get("case-busy"));
            return;
        }
        openingPlayers.add(playerId);
        databaseExecutor.completeSync(
                databaseExecutor.supply(playerId,
                        () -> database.reserveOpening(playerId, def.name(), prepared.reward)),
                result -> finishOpeningReservation(p, def, sourceBlock, prepared, result),
                failure -> {
                    releaseOpeningState(playerId, sourceBlock);
                    plugin.getLogger().log(Level.SEVERE,
                            "Не удалось подготовить storage для открытия игрока " + p.getName() + ".", failure);
                    if (p.isOnline()) p.sendMessage(plugin.getUi().get("case-open-storage-error"));
                });
    }

    private void finishOpeningReservation(
            Player player,
            CaseDefinition definition,
            BlockKey sourceBlock,
            PreparedOpening prepared,
            CasesDatabase.OpeningReservation reservation
    ) {
        UUID playerId = player.getUniqueId();
        if (!player.isOnline() || shuttingDown || !plugin.isEnabled()) {
            // A ready reservation already owns a durable pending reward. Keep it
            // for the join recovery path instead of refunding the consumed key.
            releaseOpeningState(playerId, sourceBlock);
            return;
        }
        if (reservation.pendingReward() != null) {
            releaseOpeningState(playerId, sourceBlock);
            player.sendMessage(plugin.getUi().get("pending-reward-wait"));
            deliverPendingRewardAsync(player, ignored -> { });
            return;
        }
        if (!reservation.reserved()) {
            releaseOpeningState(playerId, sourceBlock);
            player.sendMessage(plugin.getUi().get("not-enough-keys",
                    "need", "1", "have", String.valueOf(reservation.balance())));
            return;
        }
        CostReceipt cost = CostReceipt.key(definition.name());
        try {
            player.closeInventory();
        } catch (RuntimeException exception) {
            cancelReservationAsync(playerId, cost);
            releaseOpeningState(playerId, sourceBlock);
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось закрыть GUI перед открытием кейса игроку " + player.getName() + ".", exception);
            return;
        }
        startAnimationAndReward(player, definition, sourceBlock, prepared, cost);
    }

    private PreparedOpening prepareOpening(Player p, CaseDefinition def, BlockKey sourceBlock) {
        UUID playerId = p.getUniqueId();
        if (sourceBlock == null || !def.name().equals(caseByBlock.get(sourceBlock))) {
            return null;
        }
        Location sourceLocation = locationFromBlockKey(sourceBlock);
        if (sourceLocation == null) {
            return null;
        }

        Location caseBlockLocation = sourceLocation.clone();
        Location base = sourceLocation.clone().add(0.5, 0.0, 0.5);
        World w = base.getWorld();
        if (w == null) {
            return null;
        }

        Reward finalReward;
        AbstractCaseAnimation animation;
        try {
            finalReward = Reward.select(def.rewards());
            AnimationType animationType = def.fixedAnimation() == null
                    ? getPlayerAnimation(playerId, def.name())
                    : def.fixedAnimation();
            if (ServerVersion.current().isReleaseLine(1, 16)) {
                animationType = AnimationType.FORTUNE_RING;
            }
            animation = animationRegistry.get(animationType);
            if (animation == null) {
                throw new IllegalStateException("Animation is not registered: " + animationType);
            }
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось подготовить анимацию кейса '" + def.name() + "'.", exception);
            return null;
        }

        return new PreparedOpening(finalReward, animation, base, caseBlockLocation, w.getUID());
    }

    private boolean startAnimationAndReward(
            Player p,
            CaseDefinition def,
            BlockKey sourceBlock,
            PreparedOpening prepared,
            CostReceipt cost
    ) {
        UUID playerId = p.getUniqueId();
        ActiveOpening opening = new ActiveOpening(
                playerId,
                p,
                def,
                prepared.reward,
                sourceBlock,
                prepared.caseBlockLocation,
                prepared.worldId
        );
        ActiveOpening previous = activeOpenings.putIfAbsent(playerId, opening);
        if (previous != null) {
            plugin.getLogger().severe("Повторный активный сеанс открытия для игрока " + p.getName()
                    + " был отклонён.");
            cancelReservationAsync(playerId, cost);
            if (!Objects.equals(previous.sourceBlock, sourceBlock)) {
                selectedCaseBlocks.remove(playerId, sourceBlock);
                unlockCase(sourceBlock, playerId);
            }
            p.sendMessage(plugin.getUi().get("already-opening"));
            return false;
        }
        openingPlayers.add(playerId);
        opening.pendingStored = true;
        Runnable completion = () -> completeOpening(opening, true, null);

        try {
            var holograms = plugin.getHolograms();
            if (holograms != null) {
                holograms.hideCase(def, prepared.caseBlockLocation);
                opening.hologramHidden = true;
            }
            opening.watchdog = plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (opening.completed.get()) return;
                plugin.getLogger().warning("Анимация кейса '" + opening.caseName
                        + "' не завершилась вовремя; выполняется безопасная отмена.");
                cancelAnimationSession(opening, true);
            }, ANIMATION_WATCHDOG_TICKS);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось запустить окружение анимации кейса '" + def.name() + "'.", exception);
            completeOpening(opening, true, null);
            return true;
        }

        try {
            opening.animationRun = prepared.animation.play(
                    p, def, prepared.reward, prepared.base, completion);
        } catch (RuntimeException | LinkageError exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Анимация кейса '" + def.name() + "' аварийно остановлена.", exception);
            cancelAnimationSession(opening, true);
        }
        return true;
    }

    /**
     * Delivers a reward left by a previous interrupted opening. A new opening
     * must never overwrite that single pending-reward slot.
     */
    public void deliverPendingRewardAsync(Player player, Consumer<Boolean> completion) {
        if (player == null || !player.isOnline() || shuttingDown || !plugin.isEnabled()) {
            completion.accept(false);
            return;
        }
        UUID playerId = player.getUniqueId();
        if (pendingDeliveryBlocked(activeOpenings.containsKey(playerId), openingPlayers.contains(playerId))
                || !pendingDeliveries.add(playerId)) {
            completion.accept(false);
            return;
        }
        databaseExecutor.completeSync(
                databaseExecutor.supply(playerId, () -> database.loadPending(playerId)),
                pending -> {
                    if (pending == null) {
                        pendingDeliveries.remove(playerId);
                        completion.accept(true);
                        return;
                    }
                    boolean delivered;
                    try {
                        delivered = player.isOnline() && giveReward(player, pending);
                    } catch (RuntimeException exception) {
                        delivered = false;
                        plugin.getLogger().log(Level.SEVERE,
                                "Не удалось выдать отложенную награду игроку " + player.getName() + ".", exception);
                    }
                    if (!delivered) {
                        pendingDeliveries.remove(playerId);
                        completion.accept(false);
                        return;
                    }
                    clearDeliveredPending(playerId, player.getName(),
                            () -> completion.accept(true),
                            () -> completion.accept(false), 0);
                },
                failure -> {
                    pendingDeliveries.remove(playerId);
                    plugin.getLogger().log(Level.SEVERE,
                            "Не удалось проверить отложенную награду игрока " + player.getName() + ".", failure);
                    completion.accept(false);
                });
    }

    static boolean pendingDeliveryBlocked(boolean activeOpening, boolean markedOpening) {
        return activeOpening || markedOpening;
    }

    static boolean belongsToWorld(UUID sessionWorldId, UUID targetWorldId) {
        return sessionWorldId != null && sessionWorldId.equals(targetWorldId);
    }

    private void cancelAnimationSession(ActiveOpening opening, boolean deliverReward) {
        if (opening == null) return;
        try {
            if (opening.animationRun != null) opening.animationRun.cancel();
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE, "Не удалось полностью отменить анимацию кейса.", exception);
        }
        completeOpening(opening, deliverReward, null);
    }

    private void completeOpening(ActiveOpening opening, boolean deliverReward, UUID unloadingWorldId) {
        if (opening == null || !opening.completed.compareAndSet(false, true)) return;

        boolean delivered = false;
        try {
            boolean mayDeliver = deliverReward
                    && !shuttingDown
                    && plugin.isEnabled()
                    && opening.player.isOnline();
            delivered = mayDeliver && giveReward(opening.player, opening.definition, opening.reward);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.SEVERE,
                    "Не удалось завершить выдачу награды игроку " + opening.player.getName()
                            + "; награда оставлена в pending storage.", exception);
        }
        if (delivered && opening.pendingStored && !shuttingDown && plugin.isEnabled()) {
            pendingDeliveries.add(opening.playerId);
            clearDeliveredPending(opening.playerId, opening.player.getName(),
                    () -> finalizeOpening(opening, unloadingWorldId),
                    () -> finalizeOpening(opening, unloadingWorldId), 0);
            return;
        }
        finalizeOpening(opening, unloadingWorldId);
    }

    /**
     * A delivered reward must not become deliverable again just because the
     * database had a short outage while acknowledging it. The player remains
     * locked until the acknowledgement succeeds or the plugin is restarted.
     */
    private void clearDeliveredPending(
            UUID playerId,
            String playerName,
            Runnable success,
            Runnable exhausted,
            int attempt
    ) {
        databaseExecutor.completeSync(
                databaseExecutor.run(playerId, () -> database.clearPending(playerId)),
                ignored -> {
                    pendingDeliveries.remove(playerId);
                    success.run();
                },
                failure -> {
                    int nextAttempt = attempt + 1;
                    if (nextAttempt < 4 && !shuttingDown && plugin.isEnabled()) {
                        long delay = switch (nextAttempt) {
                            case 1 -> 20L;
                            case 2 -> 60L;
                            default -> 200L;
                        };
                        plugin.getServer().getScheduler().runTaskLater(plugin,
                                () -> clearDeliveredPending(playerId, playerName, success, exhausted, nextAttempt),
                                delay);
                        return;
                    }
                    plugin.getLogger().log(Level.SEVERE,
                            "Награда уже выдана, но pending-запись не удалось очистить для "
                                    + playerName + " после " + (attempt + 1) + " попыток. "
                                    + "Повторная выдача заблокирована до перезапуска.", failure);
                    exhausted.run();
                });
    }

    private void finalizeOpening(ActiveOpening opening, UUID unloadingWorldId) {
        BukkitTask watchdog = opening.watchdog;
        if (watchdog != null) {
            try {
                watchdog.cancel();
            } catch (RuntimeException ignored) {
            }
        }
        activeOpenings.remove(opening.playerId, opening);
        releaseOpeningState(opening.playerId, opening.sourceBlock);
        restoreOpeningHologram(opening, unloadingWorldId);
    }

    private void restoreOpeningHologram(ActiveOpening opening, UUID unloadingWorldId) {
        if (!opening.hologramHidden || shuttingDown) return;
        if (unloadingWorldId != null && unloadingWorldId.equals(opening.worldId)) return;
        World loadedWorld = Bukkit.getWorld(opening.worldId);
        if (loadedWorld == null) return;

        CaseDefinition current = getCaseByName(opening.caseName);
        if (current == null || !opening.caseName.equals(caseByBlock.get(opening.sourceBlock))) return;

        var holograms = plugin.getHolograms();
        if (holograms == null) return;
        try {
            Location currentLocation = new Location(loadedWorld,
                    opening.sourceBlock.x(), opening.sourceBlock.y(), opening.sourceBlock.z());
            holograms.showCase(current, currentLocation);
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING,
                    "Не удалось восстановить голограмму кейса '" + opening.caseName + "'.", exception);
        }
    }

    private void hideActiveOpeningHolograms(
            ru.privatenull.pncases.hologram.CaseHologramManager holograms,
            String onlyCaseName
    ) {
        if (holograms == null) return;
        for (ActiveOpening opening : new ArrayList<>(activeOpenings.values())) {
            if (opening.completed.get() || !opening.hologramHidden) continue;
            if (onlyCaseName != null && !onlyCaseName.equals(opening.caseName)) continue;
            if (!opening.caseName.equals(caseByBlock.get(opening.sourceBlock))) continue;

            CaseDefinition current = getCaseByName(opening.caseName);
            World world = Bukkit.getWorld(opening.worldId);
            if (current == null || world == null) continue;
            Location location = new Location(world,
                    opening.sourceBlock.x(), opening.sourceBlock.y(), opening.sourceBlock.z());
            holograms.hideCase(current, location);
        }
    }

    public void giveKey(Player p, String keyId, int amount) {
        if (amount <= 0) return;
        UUID playerId = p.getUniqueId();
        String normalizedKey = keyId.toLowerCase(Locale.ROOT);
        databaseExecutor.completeSync(
                databaseExecutor.supply(playerId, () -> {
                    database.addKeys(playerId, normalizedKey, amount);
                    return database.getKeys(playerId, normalizedKey);
                }),
                balance -> updateCachedKeyBalance(playerId, normalizedKey, balance),
                failure -> plugin.getLogger().log(Level.SEVERE,
                        "Не удалось выдать ключи игроку " + p.getName() + ".", failure));
    }

    public String getKeyDisplayName(String keyId) {
        if (keyId == null || keyId.isBlank()) {
            return "";
        }
        String normalized = keyId.toLowerCase(Locale.ROOT);
        CaseDefinition matchingCase = casesByName.get(normalized);
        if (matchingCase != null) {
            return color(matchingCase.displayName());
        }
        return humanizeKeyName(keyId);
    }

    public boolean giveReward(Player p, Reward reward) {
        return rewardDelivery.deliver(p, null, reward);
    }

    public boolean giveReward(Player p, CaseDefinition def, Reward reward) {
        return rewardDelivery.deliver(p, def, reward);
    }

    private String color(String s) {
        return ColorUtil.colorize(s);
    }

    private View view() {
        if (caseView == null) {
            throw new IllegalStateException("Case view is not configured");
        }
        return caseView;
    }

    private String humanizeKeyName(String keyId) {
        return keyId == null || keyId.isBlank() ? "" : "&f" + keyId.replace('_', ' ');
    }

    public boolean isOpening(UUID playerId) { return openingPlayers.contains(playerId); }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer) {
        BlockKey key = def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
        return isCaseBusy(key, viewer);
    }

    public boolean isCaseBusy(CaseDefinition def, UUID viewer, Block block) {
        BlockKey key = block == null ? null : BlockKey.of(block);
        return isCaseBusy(key, viewer);
    }

    public boolean isCaseBlockBusy(Location blockLocation) {
        return blockLocation != null && busyCases.containsKey(BlockKey.of(blockLocation));
    }

    private boolean isCaseBusy(BlockKey key, UUID viewer) {
        if (key == null) return false;
        UUID who = busyCases.get(key);
        return who != null && !who.equals(viewer);
    }

    private boolean tryLockCase(Player p, BlockKey key) {
        if (key == null) return false;
        UUID prev = busyCases.putIfAbsent(key, p.getUniqueId());
        return prev == null || prev.equals(p.getUniqueId());
    }

    private void unlockCase(BlockKey key, UUID owner) {
        if (key != null && owner != null) {
            busyCases.remove(key, owner);
        }
    }

    private void releaseOpeningState(UUID playerId, BlockKey sourceBlock) {
        openingPlayers.remove(playerId);
        if (sourceBlock != null) {
            selectedCaseBlocks.remove(playerId, sourceBlock);
            unlockCase(sourceBlock, playerId);
        } else {
            selectedCaseBlocks.remove(playerId);
        }
    }

    private BlockKey selectedBlockKey(Player player, CaseDefinition def) {
        BlockKey selected = selectedCaseBlocks.get(player.getUniqueId());
        if (selected != null && def.name().equals(caseByBlock.get(selected))) {
            return selected;
        }
        if (selected != null) selectedCaseBlocks.remove(player.getUniqueId(), selected);
        BlockKey fallback = def.blockLocation() == null ? null : BlockKey.of(def.blockLocation());
        return fallback != null && def.name().equals(caseByBlock.get(fallback)) ? fallback : null;
    }

    private Location locationFromBlockKey(BlockKey key) {
        if (key == null) {
            return null;
        }
        World world = Bukkit.getWorld(key.world());
        return world == null ? null : new Location(world, key.x(), key.y(), key.z());
    }

    private void refundCostAsync(UUID playerId, CostReceipt receipt) {
        if (playerId == null || receipt == null || !receipt.refunded.compareAndSet(false, true)) return;
        databaseExecutor.completeSync(
                databaseExecutor.run(playerId, () -> database.addKeys(playerId, receipt.keyId, 1)),
                ignored -> { },
                failure -> plugin.getLogger().log(Level.SEVERE,
                        "Не удалось вернуть стоимость открытия игроку " + playerId + ".", failure));
    }

    private void cancelReservationAsync(UUID playerId, CostReceipt receipt) {
        if (playerId == null || receipt == null || !receipt.refunded.compareAndSet(false, true)) return;
        databaseExecutor.completeSync(
                databaseExecutor.run(playerId, () -> {
                    database.cancelOpening(playerId, receipt.keyId);
                }),
                ignored -> { },
                failure -> plugin.getLogger().log(Level.SEVERE,
                        "Не удалось отменить резерв открытия игрока " + playerId + ".", failure));
    }

    private record PreparedOpening(
            Reward reward,
            AbstractCaseAnimation animation,
            Location base,
            Location caseBlockLocation,
            UUID worldId
    ) {
    }

    private record PlayerAnimationKey(UUID playerId, String caseId) {
        private PlayerAnimationKey {
            Objects.requireNonNull(playerId, "playerId");
            caseId = CaseConfigRepository.normalizeName(caseId);
        }
    }

    private static final class CostReceipt {
        private final String keyId;
        private final AtomicBoolean refunded = new AtomicBoolean();

        private CostReceipt(String keyId) {
            this.keyId = keyId;
        }

        private static CostReceipt key(String keyId) {
            return new CostReceipt(keyId);
        }
    }

    private static final class ActiveOpening {
        private final UUID playerId;
        private final Player player;
        private final CaseDefinition definition;
        private final String caseName;
        private final Reward reward;
        private final BlockKey sourceBlock;
        private final Location caseBlockLocation;
        private final UUID worldId;
        private final AtomicBoolean completed = new AtomicBoolean();
        private volatile BukkitTask watchdog;
        private volatile AnimationSession animationRun;
        private volatile boolean pendingStored;
        private volatile boolean hologramHidden;

        private ActiveOpening(
                UUID playerId,
                Player player,
                CaseDefinition definition,
                Reward reward,
                BlockKey sourceBlock,
                Location caseBlockLocation,
                UUID worldId
        ) {
            this.playerId = playerId;
            this.player = player;
            this.definition = definition;
            this.caseName = definition.name();
            this.reward = reward;
            this.sourceBlock = sourceBlock;
            this.caseBlockLocation = caseBlockLocation;
            this.worldId = worldId;
        }
    }

    public record BlockKey(String world, int x, int y, int z) {
        public BlockKey {
            world = world == null ? "" : world.toLowerCase(Locale.ROOT);
        }

        public static BlockKey of(Block b) { return new BlockKey(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
        public static BlockKey of(Location l) { return new BlockKey(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()); }
    }
}
