package ru.privatenull.pncases.config;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import ru.privatenull.pncases.support.text.ColorUtil;
import ru.privatenull.pncases.support.config.AtomicYamlStore;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Owns both player-facing YAML documents. Machine GUI paths intentionally use
 * their Java defaults; all other GUI and message paths remain configurable.
 */
public final class UiConfig {

    private final JavaPlugin plugin;
    private final File guiFile;
    private final File messagesFile;
    private YamlConfiguration gui;
    private YamlConfiguration messages;

    public UiConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.guiFile = new File(plugin.getDataFolder(), "gui.yml");
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        load();
    }

    public void load() {
        gui = loadDocument("gui.yml", guiFile, gui);
        messages = loadDocument("messages.yml", messagesFile, messages);
    }

    public String get(String path, String... replacements) {
        String prefix = color(messages.getString("prefix", ""));
        String raw = messages.getString(path, "&c[missing: " + path + "]");
        return color(replace(raw.replace("{prefix}", prefix), replacements));
    }

    public List<String> getList(String path, String... replacements) {
        String prefix = color(messages.getString("prefix", ""));
        List<String> result = new ArrayList<>();
        for (String raw : messages.getStringList(path)) {
            result.add(color(replace(raw.replace("{prefix}", prefix), replacements)));
        }
        return result;
    }

    public String text(String path, String fallback, String... replacements) {
        String raw = isMachinePath(path)
                ? fallback == null ? "" : fallback
                : gui.getString(path, fallback == null ? "" : fallback);
        return color(replace(raw, replacements));
    }

    public List<String> list(String path, List<String> fallback, String... replacements) {
        List<String> rawLines = isMachinePath(path)
                ? fallback == null ? List.of() : fallback
                : gui.isList(path)
                ? gui.getStringList(path)
                : fallback == null ? List.of() : fallback;
        List<String> result = new ArrayList<>();
        for (String raw : rawLines) {
            String formatted = replace(raw, replacements);
            for (String line : formatted.split("\\R", -1)) result.add(color(line));
        }
        return result;
    }

    public boolean contains(String path) {
        return !isMachinePath(path) && gui.contains(path, true);
    }

    public String raw(String path, String fallback) {
        return gui.getString(path, fallback);
    }

    public int integer(String path, int fallback) {
        return gui.getInt(path, fallback);
    }

    public double decimal(String path, double fallback) {
        return gui.getDouble(path, fallback);
    }

    public boolean bool(String path, boolean fallback) {
        return gui.getBoolean(path, fallback);
    }

    public List<Integer> integerList(String path, List<Integer> fallback) {
        if (!gui.isList(path)) return fallback == null ? List.of() : List.copyOf(fallback);
        List<Integer> result = new ArrayList<>();
        for (Object value : gui.getList(path, List.of())) {
            if (value instanceof Number number) {
                result.add(number.intValue());
                continue;
            }
            try {
                result.add(Integer.parseInt(String.valueOf(value)));
            } catch (NumberFormatException exception) {
                Log.warning("gui.yml: '" + path
                        + "' содержит неверный номер слота: " + value);
            }
        }
        return result;
    }

    private YamlConfiguration loadDocument(
            String resourceName,
            File file,
            YamlConfiguration current
    ) {
        if (!BundledConfigurationResources.copyIfMissing(plugin, resourceName, file)) {
            Log.error("Не удалось создать " + resourceName);
        }
        YamlConfiguration defaults = loadResource(resourceName);
        YamlConfiguration loaded = new YamlConfiguration();
        try {
            loaded.load(file);
        } catch (IOException | InvalidConfigurationException exception) {
            Log.error(resourceName + " повреждён и не был перезаписан", exception);
            if (current == null) return defaults;
            current.setDefaults(defaults);
            return current;
        }

        loaded.setDefaults(defaults);
        boolean changed = copyMissingDefaults(loaded, defaults);
        if (changed) saveYaml(loaded, file, plugin.getLogger());
        return loaded;
    }

    private YamlConfiguration loadResource(String resourceName) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream input = BundledConfigurationResources.open(plugin, resourceName)) {
            if (input == null) throw new IOException("resource " + resourceName + " not found");
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                defaults.load(reader);
            }
        } catch (IOException | InvalidConfigurationException exception) {
            Log.error("Не удалось прочитать встроенный " + resourceName, exception);
        }
        return defaults;
    }

    private boolean copyMissingDefaults(YamlConfiguration target, YamlConfiguration defaults) {
        boolean changed = false;
        for (String path : defaults.getKeys(true)) {
            if (defaults.isConfigurationSection(path) || target.contains(path, true)) continue;
            target.set(path, defaults.get(path));
            changed = true;
        }
        return changed;
    }

    static String replace(String raw, String... replacements) {
        if (replacements.length % 2 != 0) {
            throw new IllegalArgumentException("Replacements must be key-value pairs");
        }
        // Preserve the RGB/RGBA boundary before numeric placeholders are expanded.
        String value = color(raw);
        for (int index = 0; index < replacements.length; index += 2) {
            value = value.replace("{" + replacements[index] + "}", replacements[index + 1]);
        }
        return value;
    }

    private static String color(String value) {
        return ColorUtil.colorize(value);
    }

    private static boolean isMachinePath(String path) {
        return path != null && (path.equals("machine") || path.startsWith("machine."));
    }

    static boolean saveYaml(FileConfiguration yaml, File target, Logger logger) {
        return AtomicYamlStore.save(yaml, target, logger);
    }
}
