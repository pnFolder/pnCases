package ru.privatenull.hologram;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.inventory.ItemStack;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.model.CaseDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class HologramService {

    private final PnCasesPlugin plugin;
    private final Set<String> externalNames = new HashSet<>();
    private final ProviderResolver providerResolver;
    private final HologramSpecFactory specFactory;

    private Provider provider;
    private boolean missingProviderWarningLogged;

    public HologramService(PnCasesPlugin plugin) {
        this.plugin = plugin;
        this.providerResolver = new ProviderResolver(plugin);
        this.specFactory = new HologramSpecFactory();
        reloadProvider();
    }

    public void shutdown() {
        clearManaged();
        provider = null;
    }

    public void clearManaged() {
        clearNativeDisplays();

        Provider currentProvider = provider;
        for (String name : new ArrayList<>(externalNames)) {
            removeExternal(name, currentProvider);
        }

        externalNames.clear();
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
        Spec spec = specFactory.build(def, config, blockLocation);
        removeExternal(spec.name(), provider);
        externalNames.remove(spec.name());

        Provider currentProvider = provider;
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

    private void removeExternal(String name, Provider currentProvider) {
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
            Provider fancy = providerResolver.fancy();
            if (fancy != null && fancy != currentProvider) {
                try {
                    fancy.remove(name);
                } catch (Throwable failure) {
                    logProviderFailure("remove FancyHolograms entry " + name, failure);
                }
            }
        }

        if (Bukkit.getPluginManager().isPluginEnabled("DecentHolograms")) {
            Provider decent = providerResolver.decent();
            if (decent != null && decent != currentProvider) {
                try {
                    decent.remove(name);
                } catch (Throwable failure) {
                    logProviderFailure("remove DecentHolograms entry " + name, failure);
                }
            }
        }
    }

    private void clearNativeDisplays() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getScoreboardTags().contains("pncases_hologram")) {
                    entity.remove();
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

    public interface Provider {
        boolean isAvailable();

        void create(Spec spec);

        void remove(String name);
    }

    public record Spec(
            String name,
            String caseName,
            Type type,
            Location location,
            List<String> lines,
            ItemStack item,
            Material block,
            String billboard,
            String textAlignment,
            Boolean textShadow,
            Boolean seeThrough,
            Color background,
            Integer textUpdateInterval,
            Float shadowRadius,
            Float shadowStrength,
            Integer visibilityDistance,
            Integer interpolationDuration,
            Vector scale,
            Vector translation,
            Brightness brightness
    ) {
        public enum Type {
            TEXT,
            ITEM,
            BLOCK
        }

        public record Vector(float x, float y, float z) {
        }

        public record Brightness(int block, int sky) {
        }
    }

    private static final class ProviderResolver {
        private static final Pattern SERVER_VERSION = Pattern.compile("(\\d+)\\.(\\d+)(?:\\.(\\d+))?");
        private static final int FANCY_MIN_MINOR_VERSION = 20;

        private final PnCasesPlugin plugin;

        private ProviderResolver(PnCasesPlugin plugin) {
            this.plugin = plugin;
        }

        private Provider select() {
            ProviderMode mode = readMode();
            if (mode == ProviderMode.TEXT) return null;

            boolean fancyAvailable = Bukkit.getPluginManager().isPluginEnabled("FancyHolograms");
            boolean decentAvailable = Bukkit.getPluginManager().isPluginEnabled("DecentHolograms");
            if (mode == ProviderMode.FANCY) return fancyAvailable ? fancy() : null;
            if (mode == ProviderMode.DECENT) return decentAvailable ? decent() : null;

            if (preferDecent()) {
                Provider decent = decentAvailable ? decent() : null;
                return decent != null ? decent : fancyAvailable ? fancy() : null;
            }
            Provider fancy = fancyAvailable ? fancy() : null;
            return fancy != null ? fancy : decentAvailable ? decent() : null;
        }

        private Provider fancy() {
            try {
                return new FancyHologramsProvider();
            } catch (LinkageError | RuntimeException ignored) {
                return null;
            }
        }

        private Provider decent() {
            try {
                return new DecentHologramsProvider();
            } catch (LinkageError | RuntimeException ignored) {
                return null;
            }
        }

        private ProviderMode readMode() {
            String raw = plugin.getConfig().getString("holograms.provider", "AUTO");
            try {
                return ProviderMode.valueOf(raw == null ? "AUTO" : raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return ProviderMode.AUTO;
            }
        }

        private boolean preferDecent() {
            Matcher matcher = SERVER_VERSION.matcher(Bukkit.getBukkitVersion());
            if (!matcher.find()) return false;
            int major = integer(matcher.group(1), 1);
            int minor = integer(matcher.group(2), 21);
            return major == 1 && minor < FANCY_MIN_MINOR_VERSION;
        }

        private int integer(String raw, int fallback) {
            if (raw == null || raw.isBlank()) return fallback;
            try {
                return Integer.parseInt(raw);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }

        private enum ProviderMode {
            AUTO,
            FANCY,
            DECENT,
            TEXT
        }
    }
}
