package ru.privatenull.pncases.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.hologram.provider.HologramProvider;
import ru.privatenull.pncases.hologram.provider.HologramProviderResolver;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

public final class CaseHologramManager {

    private final PnCasesPlugin plugin;
    private final Set<String> externalNames = new HashSet<>();
    private final Set<String> transientNames = new HashSet<>();
    private final AtomicLong transientSequence = new AtomicLong();
    private final HologramProviderResolver providerResolver;
    private final HologramDefinitionFactory specFactory;

    private HologramProvider provider;
    private boolean missingProviderWarningLogged;

    public CaseHologramManager(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.providerResolver = new HologramProviderResolver(plugin);
        this.specFactory = new HologramDefinitionFactory();
        reloadProvider();
    }

    public void shutdown() {
        clearManaged();
        provider = null;
    }

    public boolean hasProvider() {
        reloadProvider();
        HologramProvider current = provider;
        if (current == null) return false;
        try {
            return current.isAvailable();
        } catch (Throwable failure) {
            logProviderFailure("check required provider", failure);
            return false;
        }
    }

    public String providerName() {
        reloadProvider();
        HologramProvider current = provider;
        if (current == null) return "none";
        String implementation = current.getClass().getSimpleName();
        if (implementation.startsWith("FancyHolograms")) return "FancyHolograms";
        if (implementation.startsWith("DecentHolograms")) return "DecentHolograms";
        return "none";
    }

    public void clearManaged() {
        HologramProvider currentProvider = provider;
        for (String name : new ArrayList<>(externalNames)) {
            removeExternal(name, currentProvider);
        }
        for (String name : new ArrayList<>(transientNames)) {
            removeExternal(name, currentProvider);
        }

        externalNames.clear();
        transientNames.clear();
    }

    /** Creates a short-lived text label for a case animation. */
    public HologramText createTransientText(Location location, String text) {
        return createTransientText(location, splitLines(text));
    }

    /** Creates a provider-backed multi-line label for a case animation. */
    public HologramText createTransientText(Location location, List<String> lines) {
        return createTransientText(location, lines, HologramTextStyle.DEFAULT);
    }

    /** Creates a provider-backed multi-line label with an explicit display scale. */
    public HologramText createTransientText(Location location, List<String> lines, float scale) {
        return createTransientText(location, lines, HologramTextStyle.DEFAULT.withScale(scale));
    }

    /** Creates a provider-backed multi-line label with complete display styling. */
    public HologramText createTransientText(Location location, List<String> lines, HologramTextStyle style) {
        if (location == null || location.getWorld() == null) return HologramText.EMPTY;
        HologramTextStyle safeStyle = style == null ? HologramTextStyle.DEFAULT : style.normalized();
        reloadProvider();
        String name = "pncases_animation_" + transientSequence.incrementAndGet();
        HologramProvider current = provider;
        if (current != null) {
            try {
                if (!current.isAvailable()) return HologramText.EMPTY;
                List<String> normalized = normalizeLines(lines);
                current.create(HologramDefinition.animationText(name, location, normalized, safeStyle));
                transientNames.add(name);
                return new ManagedHologramText(name, current, location, normalized, safeStyle.movementTicks(),
                        () -> {
                            transientNames.remove(name);
                            removeExternal(name, current);
                        }, this::logProviderFailure);
            } catch (Throwable failure) {
                logProviderFailure("create animation text " + name, failure);
            }
        }
        logMissingProviderOnce();
        return HologramText.EMPTY;
    }

    private static List<String> splitLines(String text) {
        if (text == null) return List.of(" ");
        return normalizeLines(text.lines().toList());
    }

    private static List<String> normalizeLines(List<String> lines) {
        if (lines == null || lines.isEmpty()) return List.of(" ");
        return lines.stream().map(line -> line == null || line.isEmpty() ? " " : line).toList();
    }

    public void syncCases(Collection<CaseDefinition> defs) {
        reloadProvider();
        clearManaged();

        for (CaseDefinition def : defs) {
            try {
                if (specFactory.blockLocations(def).isEmpty()) {
                    continue;
                }
                ConfigurationSection config = getHologramSection(def);
                if (config == null || !config.getBoolean("enabled", false)) {
                    continue;
                }
                for (Location location : specFactory.blockLocations(def)) {
                    createCaseHologram(def, config, location);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Holograms: ошибка создания голограммы для кейса '" + def.name() + "': " + t.getMessage());
            }
        }
    }

    /** Rebuilds holograms for one edited case only. */
    public void refreshCase(CaseDefinition previous, CaseDefinition updated) {
        if (previous != null) {
            hideCase(previous);
        }
        if (updated != null) {
            showCase(updated);
        }
    }

    public void hideCase(CaseDefinition def) {
        reloadProvider();
        if (def == null) return;

        String prefix = specFactory.prefix(def);
        for (String name : new ArrayList<>(externalNames)) {
            if (name.startsWith(prefix + "_")) {
                removeExternal(name, provider);
                externalNames.remove(name);
            }
        }
    }

    public void hideCase(CaseDefinition def, Location blockLocation) {
        reloadProvider();
        if (def == null || blockLocation == null) return;

        String name = specFactory.name(def, blockLocation);
        removeExternal(name, provider);
        externalNames.remove(name);
    }

    public void showCase(CaseDefinition def) {
        reloadProvider();
        if (def == null || specFactory.blockLocations(def).isEmpty()) return;

        try {
            ConfigurationSection config = getHologramSection(def);
            if (config == null || !config.getBoolean("enabled", false)) {
                return;
            }
            for (Location location : specFactory.blockLocations(def)) {
                createCaseHologram(def, config, location);
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("Holograms: ошибка showCase для '" + def.name() + "': " + t.getMessage());
        }
    }

    public void showCase(CaseDefinition def, Location blockLocation) {
        reloadProvider();
        if (def == null || blockLocation == null) return;

        try {
            ConfigurationSection config = getHologramSection(def);
            if (config == null || !config.getBoolean("enabled", false)) {
                return;
            }
            createCaseHologram(def, config, blockLocation);
        } catch (Throwable t) {
            plugin.getLogger().warning("Holograms: showCase error for '" + def.name() + "': " + t.getMessage());
        }
    }

    private void createCaseHologram(CaseDefinition def, ConfigurationSection config, Location blockLocation) {
        HologramDefinition spec = specFactory.build(def, config, blockLocation);
        removeExternal(spec.name(), provider);
        externalNames.remove(spec.name());

        HologramProvider currentProvider = provider;
        if (currentProvider == null) {
            logMissingProviderOnce();
            return;
        }

        try {
            if (!currentProvider.isAvailable()) {
                throw new IllegalStateException("провайдер выключен");
            }
            currentProvider.create(spec);
            externalNames.add(spec.name());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.FINE, "Hologram provider failed while creating " + spec.name(), t);
            provider = null;
            logMissingProviderOnce();
        }
    }

    private void removeExternal(String name, HologramProvider currentProvider) {
        if (name == null || name.isBlank()) {
            return;
        }

        if (currentProvider != null) {
            try {
                currentProvider.remove(name);
            } catch (Throwable failure) {
                logProviderFailure("remove " + name, failure);
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("FancyHolograms")) {
            HologramProvider fancy = providerResolver.fancy();
            if (fancy != null && fancy != currentProvider) {
                try {
                    fancy.remove(name);
                } catch (Throwable failure) {
                    logProviderFailure("remove FancyHolograms entry " + name, failure);
                }
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            HologramProvider decent = providerResolver.decent();
            if (decent != null && decent != currentProvider) {
                try {
                    decent.remove(name);
                } catch (Throwable failure) {
                    logProviderFailure("remove DecentHolograms entry " + name, failure);
                }
            }
        }
    }

    private void reloadProvider() {
        if (provider != null) {
            try {
                if (provider.isAvailable()) {
                    return;
                }
            } catch (Throwable failure) {
                logProviderFailure("check provider availability", failure);
                provider = null;
            }
        }

        provider = providerResolver.select();
        if (provider != null) {
            missingProviderWarningLogged = false;
        }
    }

    private void logMissingProviderOnce() {
        if (missingProviderWarningLogged) {
            return;
        }
        missingProviderWarningLogged = true;
        plugin.getLogger().warning("Голограммы: FancyHolograms или DecentHolograms не найдены. Голограммы кейсов отключены.");
    }

    private ConfigurationSection getHologramSection(CaseDefinition def) {
        ConfigurationSection config = plugin.getCaseManager() == null
                ? null
                : plugin.getCaseManager().getCaseSection(def.name());
        if (config == null) {
            config = plugin.getConfig().getConfigurationSection("cases." + def.name());
        }
        return config == null ? null : config.getConfigurationSection("hologram");
    }

    private void logProviderFailure(String action, Throwable failure) {
        plugin.getLogger().log(Level.FINE, "Holograms: failed to " + action, failure);
    }
}
