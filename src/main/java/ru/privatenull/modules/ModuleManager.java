package ru.privatenull.modules;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.config.CaseConfigRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ModuleManager {

    private final PnCasesPlugin plugin;
    private final Map<ModuleId, PnModule> registered = new EnumMap<>(ModuleId.class);
    private final Set<ModuleId> enabled = EnumSet.noneOf(ModuleId.class);

    public ModuleManager(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public static ModuleManager defaults(PnCasesPlugin plugin) {
        return new ModuleManager(plugin)
                .register(module(ModuleId.RARITY))
                .register(module(ModuleId.PREVIEW))
                .register(module(ModuleId.GUI_ANIMATION))
                .register(module(ModuleId.TITLE));
    }

    public ModuleManager register(PnModule module) {
        ModuleId id = module.id();
        if (registered.putIfAbsent(id, module) != null) {
            throw new IllegalArgumentException("Module already registered: " + id.configKey());
        }
        return this;
    }

    public void reload() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("modules");
        warnAboutUnknownModules(section);

        for (Map.Entry<ModuleId, PnModule> entry : registered.entrySet()) {
            ModuleId id = entry.getKey();
            PnModule module = entry.getValue();
            boolean shouldEnable = readEnabled(section, id.configKey(), module.enabledByDefault());
            boolean active = enabled.contains(id);

            try {
                if (shouldEnable && !active) {
                    module.enable(plugin);
                    enabled.add(id);
                } else if (shouldEnable) {
                    module.reload(plugin);
                } else if (active) {
                    module.disable(plugin);
                    enabled.remove(id);
                }
            } catch (RuntimeException | LinkageError exception) {
                enabled.remove(id);
                plugin.getLogger().severe("Could not load module '" + id.configKey() + "': " + exception.getMessage());
            }
        }

        plugin.getLogger().info("Modules enabled: " + (enabled.isEmpty()
                ? "none" : String.join(", ", enabledModules())));
    }

    public void shutdown() {
        List<ModuleId> reverseOrder = new ArrayList<>(enabled);
        Collections.reverse(reverseOrder);
        for (ModuleId id : reverseOrder) {
            try {
                registered.get(id).disable(plugin);
            } catch (RuntimeException | LinkageError exception) {
                plugin.getLogger().warning("Could not disable module '" + id.configKey() + "': " + exception.getMessage());
            }
        }
        enabled.clear();
    }

    public boolean isEnabled(ModuleId id) {
        return enabled.contains(id);
    }

    public boolean setEnabled(ModuleId id, boolean value) {
        if (!registered.containsKey(id)) {
            return false;
        }
        String path = "modules." + id.configKey();
        Object previous = plugin.getConfig().get(path);
        plugin.getConfig().set(path, value);
        if (!new CaseConfigRepository(plugin).saveMainConfig()) {
            plugin.getConfig().set(path, previous);
            return false;
        }
        reload();
        return true;
    }

    public Collection<String> enabledModules() {
        return enabled.stream().map(ModuleId::configKey).toList();
    }

    private boolean readEnabled(ConfigurationSection section, String id, boolean fallback) {
        if (section == null || !section.contains(id)) {
            return fallback;
        }
        if (!section.isBoolean(id)) {
            plugin.getLogger().warning("modules." + id + " must be true or false; using " + fallback + '.');
            return fallback;
        }
        return section.getBoolean(id);
    }

    private void warnAboutUnknownModules(ConfigurationSection section) {
        if (section == null) {
            return;
        }
        for (String configuredId : section.getKeys(false)) {
            boolean known = registered.keySet().stream()
                    .anyMatch(id -> id.configKey().equalsIgnoreCase(configuredId));
            if (!known) {
                plugin.getLogger().warning("Unknown module in config.yml: " + configuredId);
            }
        }
    }

    private static PnModule module(ModuleId id) {
        return () -> id;
    }
}
