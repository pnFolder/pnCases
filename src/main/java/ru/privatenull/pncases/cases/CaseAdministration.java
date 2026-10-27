package ru.privatenull.pncases.cases;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.config.CaseBlockCodec;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;
import ru.privatenull.pncases.cases.config.CaseDefinitionLoader;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.cases.opening.CaseBlockKey;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Creates, edits, binds and reloads cases from their YAML files and keeps the registry in sync. */
public final class CaseAdministration {

    /** Visual and GUI side effects of a registry change, owned by the caller. */
    public interface Effects {
        void reloaded(CaseRegistry registry);

        void refreshed(CaseDefinition previous, CaseDefinition updated,
                       boolean refreshHologram, boolean refreshShowcase);
    }

    private final PnCasesPlugin plugin;
    private final CaseRegistry registry;
    private final CaseConfigRepository configRepository;
    private final CaseBlockCodec blockCodec;
    private final CaseDefinitionLoader definitionLoader;
    private final Effects effects;

    public CaseAdministration(PnCasesPlugin plugin, CaseRegistry registry, CaseConfigRepository configRepository,
                              CaseBlockCodec blockCodec, CaseDefinitionLoader definitionLoader, Effects effects) {
        this.plugin = plugin;
        this.registry = registry;
        this.configRepository = configRepository;
        this.blockCodec = blockCodec;
        this.definitionLoader = definitionLoader;
        this.effects = effects;
    }

    public boolean updateCaseConfig(String caseName, Consumer<ConfigurationSection> updater,
                                    boolean refreshHologram, boolean refreshShowcase) {
        if (caseName == null || caseName.isBlank() || updater == null) {
            return false;
        }
        if (!configRepository.update(caseName, updater)) return false;
        return refreshCaseFromConfig(caseName, refreshHologram, refreshShowcase);
    }

    public CaseManager.CopyCaseResult copyCase(String fromCase, String toCase) {
        return switch (configRepository.copy(fromCase, toCase)) {
            case SOURCE_NOT_FOUND -> CaseManager.CopyCaseResult.SOURCE_NOT_FOUND;
            case ALREADY_EXISTS -> CaseManager.CopyCaseResult.ALREADY_EXISTS;
            case INVALID_ID -> CaseManager.CopyCaseResult.INVALID_ID;
            case SAVE_FAILED -> CaseManager.CopyCaseResult.SAVE_FAILED;
            case COPIED -> reloadFromConfig()
                    ? CaseManager.CopyCaseResult.COPIED
                    : CaseManager.CopyCaseResult.RELOAD_FAILED;
        };
    }

    public int undoDepth(String caseName) {
        return configRepository.undoDepth(caseName);
    }

    /** Reverts the last saved change of a case and refreshes it. False when nothing could be undone. */
    public boolean undoLastChange(String caseName) {
        if (!configRepository.undo(caseName)) return false;
        return refreshCaseFromConfig(caseName, true, true);
    }

    public CaseManager.CreateCaseResult createCustomCase(String caseName) {
        if (!CaseConfigRepository.isValidId(caseName)) return CaseManager.CreateCaseResult.INVALID_ID;
        if (configRepository.exists(caseName)) return CaseManager.CreateCaseResult.ALREADY_EXISTS;

        CaseConfigRepository.CreateResult result = configRepository.create(caseName);
        if (result == CaseConfigRepository.CreateResult.CREATED) {
            return reloadFromConfig()
                    ? CaseManager.CreateCaseResult.CREATED
                    : CaseManager.CreateCaseResult.RELOAD_FAILED;
        }
        return CaseManager.CreateCaseResult.valueOf(result.name());
    }

    public CaseManager.DeleteCaseResult deleteCase(String caseName) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        if (!CaseConfigRepository.isValidId(normalized) || !configRepository.exists(normalized)) {
            return CaseManager.DeleteCaseResult.NOT_FOUND;
        }
        if (!configRepository.delete(normalized)) {
            return CaseManager.DeleteCaseResult.DELETE_FAILED;
        }
        return reloadFromConfig()
                ? CaseManager.DeleteCaseResult.DELETED
                : CaseManager.DeleteCaseResult.RELOAD_FAILED;
    }

    public CaseManager.BindCaseResult bindCaseToBlock(String caseName, Block target) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        if (target == null || !configRepository.exists(normalized)) return CaseManager.BindCaseResult.NOT_FOUND;

        CaseBlockKey targetKey = CaseBlockKey.of(target);
        String owner;
        try {
            owner = configuredOwner(targetKey, normalized);
        } catch (IllegalStateException ex) {
            plugin.getLogger().severe(ex.getMessage());
            return CaseManager.BindCaseResult.REFRESH_FAILED;
        }
        if (owner != null && !owner.equals(normalized)) return CaseManager.BindCaseResult.BLOCK_OCCUPIED;
        if (normalized.equals(owner)) return CaseManager.BindCaseResult.ALREADY_BOUND;

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
        if (!saved) return CaseManager.BindCaseResult.SAVE_FAILED;
        return refreshCaseFromConfig(normalized, true, true)
                ? CaseManager.BindCaseResult.BOUND
                : CaseManager.BindCaseResult.REFRESH_FAILED;
    }

    public CaseManager.UnbindCaseResult unbindCaseFromBlock(String caseName) {
        if (caseName == null || caseName.isBlank()) {
            return CaseManager.UnbindCaseResult.NOT_FOUND;
        }

        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.findSource(normalized);
        if (source == null || source.section() == null) {
            return CaseManager.UnbindCaseResult.NOT_FOUND;
        }

        if (blockCodec.readConfiguredBlocks(source.section()).isEmpty()) {
            return CaseManager.UnbindCaseResult.NOT_BOUND;
        }

        boolean saved = configRepository.update(normalized, section -> {
            section.set("block", null);
            section.set("blocks", null);
        });
        if (!saved) return CaseManager.UnbindCaseResult.SAVE_FAILED;
        return refreshCaseFromConfig(normalized, true, true)
                ? CaseManager.UnbindCaseResult.REMOVED
                : CaseManager.UnbindCaseResult.REFRESH_FAILED;
    }

    public boolean reloadFromConfig() {
        Map<String, CaseDefinition> loadedCases = new HashMap<>();
        Map<CaseBlockKey, String> loadedBlocks = new HashMap<>();
        Map<CaseBlockKey, String> configuredBlocks = new HashMap<>();
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
                    CaseBlockKey blockKey = blockKey(configured);
                    String previousOwner = configuredBlocks.putIfAbsent(blockKey, definition.name());
                    if (previousOwner != null && !previousOwner.equals(definition.name())) {
                        throw new IllegalStateException("Блок " + blockKey + " одновременно принадлежит кейсам '"
                                + previousOwner + "' и '" + definition.name() + "'.");
                    }
                }
                for (Location location : definition.blockLocations()) {
                    CaseBlockKey blockKey = CaseBlockKey.of(location);
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

        registry.replaceAll(loadedCases, loadedBlocks, loadedSections);
        effects.reloaded(registry);
        plugin.getLogger().info("Loaded cases: " + registry.size() + ", active blocks: " + registry.blockCount()
                + ", virtual keys: " + registry.size());
        return true;
    }

    /**
     * Applies an edit made through the machine GUI without rebuilding visuals of
     * unrelated cases.  A full reload is reserved for /pncases reload and world
     * discovery, where rebuilding the complete registry is intentional.
     */
    public boolean refreshCaseFromConfig(String caseName, boolean refreshHologram, boolean refreshShowcase) {
        String normalized = CaseConfigRepository.normalizeName(caseName);
        CaseConfigRepository.Source source = configRepository.findSource(normalized);
        if (source == null || source.section() == null) {
            return false;
        }

        CaseDefinition previous = registry.byName(normalized);
        CaseDefinition updated;
        try {
            updated = definitionLoader.load(source.name(), source.section());
        } catch (RuntimeException ex) {
            plugin.getLogger().severe("Не удалось обновить кейс '" + normalized + "': " + ex.getMessage());
            return false;
        }

        List<CaseBlockKey> previousBlocks = previous == null
                ? List.of()
                : previous.blockLocations().stream().map(CaseBlockKey::of).toList();
        List<CaseBlockKey> newBlocks = updated.blockLocations().stream().map(CaseBlockKey::of).toList();
        Map<CaseBlockKey, String> updatedBlockMap = CaseRegistry.replaceOwnedBlocks(
                registry.blocks(), normalized, previousBlocks, newBlocks);
        if (updatedBlockMap == null) {
            plugin.getLogger().severe("Кейс '" + normalized
                    + "' не обновлён: один из его блоков уже принадлежит другому кейсу.");
            return false;
        }

        registry.replaceOne(updated, source.section(), updatedBlockMap);
        effects.refreshed(previous, updated, refreshHologram, refreshShowcase);
        return true;
    }

    private String configuredOwner(CaseBlockKey target, String requestedCase) {
        CaseConfigRepository.LoadResult loadResult = configRepository.loadAll();
        if (!loadResult.successful()) {
            throw new IllegalStateException("Нельзя привязать блок: в конфигах кейсов есть ошибки id, дубликаты или повреждённый YAML.");
        }

        String requestedOwner = null;
        for (CaseConfigRepository.Source source : loadResult.sources()) {
            for (Map<String, Object> configured : blockCodec.readConfiguredBlocks(source.section())) {
                CaseBlockKey configuredKey = blockKey(configured);
                if (!target.equals(configuredKey)) continue;
                if (!source.name().equals(requestedCase)) return source.name();
                requestedOwner = source.name();
            }
        }
        return requestedOwner;
    }

    private CaseBlockKey blockKey(Map<String, Object> configured) {
        if (configured == null) return null;
        return new CaseBlockKey(
                String.valueOf(configured.getOrDefault("world", "")),
                integer(configured.get("x")),
                integer(configured.get("y")),
                integer(configured.get("z"))
        );
    }

    private static int integer(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return 0;
        }
    }
}
