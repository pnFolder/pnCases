package ru.privatenull.pncases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.config.CaseConfigRepository;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Synchronizes user YAML with bundled templates without release-specific migration code.
 * The previous bundled templates are stored internally and used for a three-way merge.
 */
public final class ConfigurationSyncService {

    private static final List<String> DOCUMENTS = List.of("config.yml", "gui.yml", "messages.yml");
    private static final String STATE_DIRECTORY = ".pncases";
    private static final String STATE_FILE = "yaml-templates.yml";

    private ConfigurationSyncService() {
    }

    public static Result synchronize(PnCasesPlugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        File stateFile = new File(new File(plugin.getDataFolder(), STATE_DIRECTORY), STATE_FILE);
        YamlConfiguration state = loadOptional(stateFile, plugin);
        if (state == null) {
            return Result.failure("внутреннее состояние YAML повреждено");
        }

        YamlConfiguration nextState = new YamlConfiguration();
        nextState.set("format", 1);
        List<String> updated = new ArrayList<>();

        for (String document : DOCUMENTS) {
            YamlConfiguration bundled = loadResource(plugin, document);
            if (bundled == null) {
                return Result.failure("не удалось прочитать встроенный " + document);
            }

            File userFile = new File(plugin.getDataFolder(), document);
            if (!userFile.exists()) {
                plugin.saveResource(document, false);
            }
            YamlConfiguration user = loadRequired(userFile, plugin);
            if (user == null) {
                return Result.failure(document + " повреждён; файл не был перезаписан");
            }

            ConfigurationSection previousSection = state.getConfigurationSection("templates." + documentKey(document));
            Map<String, Object> previous = flatten(previousSection);
            Map<String, Object> current = flatten(user);
            Map<String, Object> defaults = flatten(bundled);
            Map<String, Object> merged = merge(current, previous, defaults);

            if (!current.equals(merged)) {
                YamlConfiguration output = inflate(merged);
                if (!UiConfig.saveYaml(output, userFile, plugin.getLogger())) {
                    return Result.failure("не удалось безопасно обновить " + document);
                }
                updated.add(document);
            }
            writeTemplate(nextState, document, defaults);
        }

        CaseConfigRepository cases = new CaseConfigRepository(plugin);
        for (String caseId : cases.configuredNames()) {
            CaseConfigRepository.Writable writable = cases.writable(caseId);
            if (writable == null) return Result.failure("не удалось прочитать cases/" + caseId + ".yml");
            Map<String, Object> current = flatten(writable.yaml());
            Map<String, Object> defaults = flatten(CaseConfigRepository.defaultConfiguration(caseId));
            Map<String, Object> previous = flatten(
                    state.getConfigurationSection("case-templates." + caseId));
            Map<String, Object> merged = merge(current, previous, defaults);
            if (!current.equals(merged)) {
                replace(writable.yaml(), merged);
                if (!cases.save(writable)) {
                    return Result.failure("не удалось безопасно обновить cases/" + caseId + ".yml");
                }
                updated.add("cases/" + caseId + ".yml");
            }
            writeValues(nextState, "case-templates." + caseId, defaults);
        }

        if (!flatten(state).equals(flatten(nextState))
                && !UiConfig.saveYaml(nextState, stateFile, plugin.getLogger())) {
            return Result.failure("не удалось сохранить внутреннее состояние YAML");
        }

        if (!updated.isEmpty()) {
            plugin.getLogger().info("YAML sync: обновлены " + String.join(", ", updated)
                    + "; пользовательские значения сохранены.");
        }
        return Result.success(updated);
    }

    private static Map<String, Object> merge(
            Map<String, Object> user,
            Map<String, Object> previousDefaults,
            Map<String, Object> newDefaults
    ) {
        boolean bootstrap = previousDefaults.isEmpty();
        Map<String, Object> merged = new LinkedHashMap<>();
        Set<String> movedSources = new HashSet<>();
        for (Map.Entry<String, Object> entry : newDefaults.entrySet()) {
            String path = entry.getKey();
            Object newDefault = entry.getValue();
            if (user.containsKey(path) && compatible(user.get(path), newDefault)) {
                Object userValue = user.get(path);
                if (!bootstrap && previousDefaults.containsKey(path)
                        && equivalent(userValue, previousDefaults.get(path))) {
                    merged.put(path, copy(newDefault));
                } else {
                    merged.put(path, copy(userValue));
                }
                continue;
            }

            String movedFrom = bootstrap ? null
                    : findMovedPath(path, newDefault, user, previousDefaults, newDefaults);
            if (movedFrom != null) {
                movedSources.add(movedFrom);
                Object movedValue = user.get(movedFrom);
                if (!bootstrap && previousDefaults.containsKey(movedFrom)
                        && equivalent(movedValue, previousDefaults.get(movedFrom))) {
                    merged.put(path, copy(newDefault));
                } else {
                    merged.put(path, copy(movedValue));
                }
            } else {
                merged.put(path, copy(newDefault));
            }
        }
        for (Map.Entry<String, Object> entry : user.entrySet()) {
            String path = entry.getKey();
            if (newDefaults.containsKey(path) || movedSources.contains(path)) continue;
            boolean customPath = bootstrap || !previousDefaults.containsKey(path);
            if (customPath) merged.put(path, copy(entry.getValue()));
        }
        return merged;
    }

    private static String findMovedPath(
            String newPath,
            Object newDefault,
            Map<String, Object> user,
            Map<String, Object> previousDefaults,
            Map<String, Object> newDefaults
    ) {
        String leaf = leaf(newPath);
        List<String> candidates = new ArrayList<>();
        Map<String, Object> source = previousDefaults.isEmpty() ? user : previousDefaults;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String oldPath = entry.getKey();
            if (newDefaults.containsKey(oldPath)
                    || !user.containsKey(oldPath)
                    || !leaf.equals(leaf(oldPath))
                    || !compatible(user.get(oldPath), newDefault)) {
                continue;
            }
            candidates.add(oldPath);
        }
        return candidates.size() == 1 ? candidates.get(0) : null;
    }

    private static void writeTemplate(
            YamlConfiguration state,
            String document,
            Map<String, Object> defaults
    ) {
        writeValues(state, "templates." + documentKey(document), defaults);
    }

    private static void writeValues(YamlConfiguration state, String root, Map<String, Object> defaults) {
        for (Map.Entry<String, Object> entry : defaults.entrySet()) {
            state.set(root + "." + entry.getKey(), copy(entry.getValue()));
        }
    }

    private static void replace(YamlConfiguration yaml, Map<String, Object> values) {
        for (String key : new ArrayList<>(yaml.getKeys(false))) yaml.set(key, null);
        for (Map.Entry<String, Object> entry : values.entrySet()) yaml.set(entry.getKey(), copy(entry.getValue()));
    }

    private static String documentKey(String document) {
        return document.substring(0, document.length() - ".yml".length());
    }

    private static String leaf(String path) {
        int separator = path.lastIndexOf('.');
        return separator < 0 ? path : path.substring(separator + 1);
    }

    private static boolean compatible(Object value, Object template) {
        if (value == null || template == null) return value == template;
        if (value instanceof Number && template instanceof Number) return true;
        return value instanceof List<?> && template instanceof List<?>
                || value.getClass().equals(template.getClass());
    }

    private static boolean equivalent(Object left, Object right) {
        if (left instanceof Number leftNumber && right instanceof Number rightNumber) {
            return Double.compare(leftNumber.doubleValue(), rightNumber.doubleValue()) == 0;
        }
        return Objects.equals(left, right);
    }

    private static Map<String, Object> flatten(ConfigurationSection section) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (section == null) return values;
        for (String path : section.getKeys(true)) {
            if (!section.isConfigurationSection(path)) {
                values.put(path, copy(section.get(path)));
            }
        }
        return values;
    }

    private static YamlConfiguration inflate(Map<String, Object> values) {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            yaml.set(entry.getKey(), copy(entry.getValue()));
        }
        return yaml;
    }

    private static Object copy(Object value) {
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object entry : list) result.add(copy(entry));
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                result.put(String.valueOf(entry.getKey()), copy(entry.getValue()));
            }
            return result;
        }
        return value;
    }

    private static YamlConfiguration loadOptional(File file, PnCasesPlugin plugin) {
        if (!file.exists()) return new YamlConfiguration();
        return loadRequired(file, plugin);
    }

    private static YamlConfiguration loadRequired(File file, PnCasesPlugin plugin) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Не удалось прочитать " + file.getName() + ": " + exception.getMessage());
            return null;
        }
    }

    private static YamlConfiguration loadResource(PnCasesPlugin plugin, String name) {
        try (InputStream input = plugin.getResource(name)) {
            if (input == null) throw new IOException("resource " + name + " not found");
            YamlConfiguration yaml = new YamlConfiguration();
            try (InputStreamReader reader = new InputStreamReader(input, StandardCharsets.UTF_8)) {
                yaml.load(reader);
            }
            return yaml;
        } catch (IOException | InvalidConfigurationException exception) {
            plugin.getLogger().severe("Не удалось прочитать встроенный " + name + ": "
                    + exception.getMessage());
            return null;
        }
    }

    public record Result(boolean successful, List<String> updatedDocuments, String error) {
        public Result {
            updatedDocuments = List.copyOf(updatedDocuments);
            error = error == null ? "" : error;
        }

        private static Result success(List<String> updatedDocuments) {
            return new Result(true, updatedDocuments, "");
        }

        private static Result failure(String error) {
            return new Result(false, List.of(), error);
        }
    }
}
