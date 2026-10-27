package ru.privatenull.pncases.cases.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.pncases.PnCasesPlugin;
import ru.privatenull.pncases.cases.model.CaseDefinition;
import ru.privatenull.pncases.support.config.AtomicYamlStore;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Consumer;

public final class CaseConfigRepository {

    private static final List<String> CASE_FILE_HEADER = List.of(
            "# pnCases — конфигурация кейса",
            "#",
            "# Плейсхолдеры меню и предметов:",
            "# {display-name} — цветное название кейса из display-name.",
            "# {case} — то же отображаемое название кейса.",
            "# {case_id} — ID кейса, то есть имя этого файла без .yml.",
            "# {title} — заголовок меню кейса.",
            "# {key} — ID виртуального ключа; он всегда совпадает с ID кейса.",
            "# {key_name} — отображаемое название требуемого ключа.",
            "# {have} — сколько ключей есть у игрока.",
            "# {need} — сколько ключей требуется для открытия.",
            "# {levels} — цена покупки ключа в уровнях опыта.",
            "# {left-click} — актуальное действие при нажатии ЛКМ.",
            "# {right-click} — актуальное действие при нажатии ПКМ.",
            "# {bulk-click} — актуальное действие при Shift + ПКМ.",
            "# {left_action}, {right_action}, {bulk_action} — короткие названия этих действий.",
            "#",
            "# Плейсхолдеры наград, сообщений и команд:",
            "# {player} — имя игрока, открывшего кейс.",
            "# {uuid} — UUID игрока, открывшего кейс.",
            "# {reward} — отображаемое название выпавшей награды.",
            "# {amount} — количество валюты, поинтов или предметов в награде.",
            "# {money}, {points} — сумма Vault или PlayerPoints.",
            "# {group}, {permission}, {duration} — данные награды LuckPerms.",
            "# {player}, {case}, {case_id}, {reward} также доступны в broadcast.lines.",
            "#",
            "# Доступные редкости rewards[].rarity:",
            "# COMMON — Обычная.",
            "# UNCOMMON — Необычная.",
            "# RARE — Редкая.",
            "# EPIC — Эпическая.",
            "# LEGENDARY — Легендарная.",
            "# MYTHIC — Мифическая.",
            "# Названия редкостей настраиваются в config.yml -> rarity.",
            "#",
            "# Дополнительные плейсхолдеры голограммы:",
            "# {world} — название мира, в котором установлен кейс.",
            "# {x}, {y}, {z} — координаты блока кейса.",
            "# {gui_title} — заголовок меню с цветами.",
            "# {gui_title_plain} — заголовок меню без цветов."
    );

    public enum CreateResult {
        CREATED,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED
    }

    public enum CopyResult {
        COPIED,
        SOURCE_NOT_FOUND,
        ALREADY_EXISTS,
        INVALID_ID,
        SAVE_FAILED
    }

    private final PnCasesPlugin plugin;
    private final ConfigHistory history = new ConfigHistory(10);

    public CaseConfigRepository(PnCasesPlugin plugin) {
        this.plugin = plugin;
    }

    public List<String> configuredNames() {
        Set<String> names = new TreeSet<>();
        File[] files = directory().listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            for (File file : files) {
                String name = fileId(file.getName());
                if (isValidId(name)) names.add(name);
            }
        }
        return new ArrayList<>(names);
    }

    public boolean exists(String caseName) {
        String normalized = normalizeName(caseName);
        return isValidId(normalized) && existingCaseFile(normalized) != null;
    }

    public boolean update(String caseName, Consumer<ConfigurationSection> updater) {
        if (!isValidId(normalizeName(caseName)) || updater == null) return false;
        Writable writable = writable(caseName);
        if (writable == null || writable.section() == null) return false;
        Map<String, Object> before = snapshotSection(writable.section());
        try {
            updater.accept(writable.section());
        } catch (RuntimeException ex) {
            restoreSection(writable.section(), before);
            plugin.getLogger().severe("Не удалось изменить конфиг кейса '" + normalizeName(caseName) + "': " + ex.getMessage());
            return false;
        }
        if (save(writable)) {
            history.push(normalizeName(caseName), before);
            return true;
        }
        restoreSection(writable.section(), before);
        return false;
    }

    /** How many saved changes of the case can be undone. */
    public int undoDepth(String caseName) {
        return history.depth(normalizeName(caseName));
    }

    /**
     * Restores the case file to the state before its last saved change. Returns false when there is
     * nothing to undo or the file could not be written; in that case the history is left unchanged.
     */
    public boolean undo(String caseName) {
        String normalized = normalizeName(caseName);
        Map<String, Object> snapshot = history.pop(normalized);
        if (snapshot == null) return false;
        Writable writable = writable(normalized);
        if (writable == null || writable.section() == null) {
            history.push(normalized, snapshot);
            return false;
        }
        restoreSection(writable.section(), snapshot);
        if (save(writable)) return true;
        history.push(normalized, snapshot);
        return false;
    }

    public CreateResult create(String caseName) {
        if (!isValidId(caseName)) return CreateResult.INVALID_ID;
        String normalized = normalizeName(caseName);
        if (exists(normalized)) return CreateResult.ALREADY_EXISTS;

        File directory = directory();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().severe("Не удалось создать папку кейсов: " + directory.getPath());
            return CreateResult.SAVE_FAILED;
        }

        File target = file(normalized);
        YamlConfiguration yaml = defaultConfiguration(normalized);
        return saveYamlAtomically(yaml, target) ? CreateResult.CREATED : CreateResult.SAVE_FAILED;
    }

    /** Copies the settings of a case into a new case that is not bound to any block yet. */
    public CopyResult copy(String fromCase, String toCase) {
        String target = normalizeName(toCase);
        if (!isValidId(target)) return CopyResult.INVALID_ID;
        if (exists(target)) return CopyResult.ALREADY_EXISTS;
        Source source = findSource(fromCase);
        if (source == null || source.section() == null) return CopyResult.SOURCE_NOT_FOUND;

        File directory = directory();
        if (!directory.exists() && !directory.mkdirs()) return CopyResult.SAVE_FAILED;
        YamlConfiguration yaml = new YamlConfiguration();
        restoreSection(yaml, copiedValues(snapshotSection(source.section()), target));
        return saveYamlAtomically(yaml, file(target)) ? CopyResult.COPIED : CopyResult.SAVE_FAILED;
    }

    /** Settings of a copy: everything except the bound blocks, with the id of the new case. */
    static Map<String, Object> copiedValues(Map<String, Object> source, String targetId) {
        Map<String, Object> values = new LinkedHashMap<>(source);
        values.remove("block");
        values.put("blocks", new ArrayList<>());
        if (values.containsKey("id")) values.put("id", targetId);
        return values;
    }

    public List<Source> loadSources() {
        return loadAll().sources();
    }

    public LoadResult loadAll() {
        Map<String, Source> sources = new LinkedHashMap<>();
        boolean successful = true;
        File casesDirectory = directory();
        if (!ensureDirectory(casesDirectory)) {
            return new LoadResult(List.of(), false);
        }
        File[] files = casesDirectory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            plugin.getLogger().severe("Не удалось прочитать папку cases: путь недоступен или не является папкой.");
            return new LoadResult(List.of(), false);
        }

        Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
        for (File file : files) {
            String canonicalId = fileId(file.getName());
            if (!isValidId(canonicalId)) {
                plugin.getLogger().severe("Пропущен конфиг кейса с недопустимым именем файла: " + file.getName());
                successful = false;
                continue;
            }
            YamlConfiguration yaml = loadYaml(file);
            if (yaml == null) {
                successful = false;
                continue;
            }
            warnIdMismatch(file.getName(), canonicalId, yaml);
            if (sources.putIfAbsent(canonicalId, new Source(canonicalId, yaml)) != null) {
                plugin.getLogger().severe("Дубликат id кейса '" + canonicalId + "' в папке cases. Файл "
                        + file.getName() + " пропущен.");
                successful = false;
            }
        }
        return new LoadResult(new ArrayList<>(sources.values()), successful);
    }

    public Source findSource(String caseName) {
        String normalized = normalizeName(caseName);
        if (!isValidId(normalized)) return null;

        File caseFile = existingCaseFile(normalized);
        if (caseFile == null) return null;
        YamlConfiguration yaml = loadYaml(caseFile);
        if (yaml == null) return null;
        warnIdMismatch(caseFile.getName(), normalized, yaml);
        return new Source(normalized, yaml);
    }

    public Writable writable(String caseName) {
        String normalized = normalizeName(caseName);
        if (!isValidId(normalized)) return null;

        File caseFile = existingCaseFile(normalized);
        if (caseFile == null) return null;
        YamlConfiguration yaml = loadYaml(caseFile);
        return yaml == null ? null : new Writable(yaml, caseFile);
    }

    public boolean save(Writable writable) {
        if (writable == null) return false;
        return saveYamlAtomically(writable.yaml(), writable.file());
    }

    public boolean saveMainConfig() {
        return saveYamlAtomically(plugin.getConfig(), new File(plugin.getDataFolder(), "config.yml"));
    }

    public boolean delete(String caseName) {
        File caseFile = existingCaseFile(normalizeName(caseName));
        if (caseFile == null) return false;
        history.clear(normalizeName(caseName));
        try {
            return Files.deleteIfExists(caseFile.toPath());
        } catch (IOException ex) {
            plugin.getLogger().severe("Не удалось удалить конфиг кейса '" + normalizeName(caseName) + "': " + ex.getMessage());
            return false;
        }
    }

    public static String normalizeName(String caseName) {
        return (caseName == null ? "" : caseName.trim()).toLowerCase(Locale.ROOT);
    }

    public static boolean isValidId(String caseName) {
        return caseName != null && caseName.matches("[a-z0-9_-]{1,64}");
    }

    private boolean ensureDirectory(File directory) {
        if (directory.exists() || directory.mkdirs()) return true;
        plugin.getLogger().warning("Не удалось создать папку cases для отдельных конфигов кейсов.");
        return false;
    }

    private File existingCaseFile(String caseId) {
        File resolved = resolveExportTarget(caseId);
        return resolved != null && resolved.isFile() ? resolved : null;
    }

    private File resolveExportTarget(String caseId) {
        File[] matches = directory().listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(".yml") && fileId(name).equals(caseId));
        if (matches == null || matches.length == 0) return file(caseId);
        return matches.length == 1 ? matches[0] : null;
    }

    private File directory() {
        return new File(plugin.getDataFolder(), "cases");
    }

    private File file(String caseName) {
        return new File(directory(), normalizeName(caseName) + ".yml");
    }

    static String fileId(String fileName) {
        if (fileName == null) return "";
        String name = fileName.trim();
        if (name.toLowerCase(Locale.ROOT).endsWith(".yml")) {
            name = name.substring(0, name.length() - 4);
        }
        return normalizeName(name);
    }

    private static void clearSection(ConfigurationSection section) {
        for (String key : new ArrayList<>(section.getKeys(false))) {
            section.set(key, null);
        }
    }

    private YamlConfiguration loadYaml(File file) {
        YamlConfiguration yaml = new YamlConfiguration();
        try {
            yaml.load(file);
            return yaml;
        } catch (IOException | InvalidConfigurationException ex) {
            plugin.getLogger().severe("Не удалось прочитать конфиг кейса " + file.getName() + ": " + ex.getMessage());
            return null;
        }
    }

    private void warnIdMismatch(String source, String canonicalId, ConfigurationSection section) {
        String explicitId = section == null ? null : section.getString("id");
        if (explicitId != null && !explicitId.isBlank() && !canonicalId.equals(normalizeName(explicitId))) {
            plugin.getLogger().warning(source + " содержит id='" + explicitId + "', но используется канонический id '"
                    + canonicalId + "' из имени файла/секции. Измените id, чтобы убрать предупреждение.");
        }
    }

    private boolean saveYamlAtomically(FileConfiguration yaml, File target) {
        return AtomicYamlStore.save(yaml, target, plugin.getLogger(), temporary -> {
            if (isCaseFile(target)) {
                formatCaseYaml(temporary);
            } else {
                compactIntegerSlotLists(temporary);
            }
        });
    }

    static void compactIntegerSlotLists(Path yamlFile) throws IOException {
        List<String> lines = Files.readAllLines(yamlFile, StandardCharsets.UTF_8);
        List<String> compact = new ArrayList<>(lines.size());
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            String trimmed = line.trim();
            if (!"slots:".equals(trimmed)) {
                compact.add(line);
                continue;
            }

            String indent = line.substring(0, line.indexOf('s'));
            List<String> values = new ArrayList<>();
            int cursor = index + 1;
            while (cursor < lines.size()) {
                String candidate = lines.get(cursor);
                String candidateTrimmed = candidate.trim();
                if (!candidate.startsWith(indent) || !candidateTrimmed.startsWith("- ")) break;
                String value = candidateTrimmed.substring(2).trim();
                if (!value.matches("-?\\d+")) break;
                values.add(value);
                cursor++;
            }
            if (values.isEmpty()) {
                compact.add(line);
                continue;
            }

            compact.add(indent + "slots: [" + String.join(", ", values) + "]");
            index = cursor - 1;
        }
        Files.write(yamlFile, compact, StandardCharsets.UTF_8);
    }

    static void formatCaseYaml(Path yamlFile) throws IOException {
        compactIntegerSlotLists(yamlFile);
        List<String> lines = Files.readAllLines(yamlFile, StandardCharsets.UTF_8);
        lines = stripManagedDecorations(lines);
        lines = compactBlockLocations(lines);
        lines = addCaseHeader(lines);
        Files.write(yamlFile, lines, StandardCharsets.UTF_8);
    }

    private static boolean isCaseFile(File target) {
        File parent = target == null ? null : target.getParentFile();
        return parent != null
                && "cases".equalsIgnoreCase(parent.getName())
                && target.getName().toLowerCase(Locale.ROOT).endsWith(".yml");
    }

    private static List<String> compactBlockLocations(List<String> source) {
        List<String> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String line = source.get(index);
            result.add(line);
            if (!"blocks:".equals(line.trim()) || !leadingWhitespace(line).isEmpty()) continue;

            int cursor = index + 1;
            while (cursor < source.size()) {
                String first = source.get(cursor);
                if (!first.startsWith("- ")) break;
                String firstValue = first.substring(2).trim();
                if (firstValue.startsWith("{") && firstValue.endsWith("}")) {
                    result.add(first);
                    cursor++;
                    continue;
                }
                List<String> values = new ArrayList<>();
                values.add(firstValue);
                int next = cursor + 1;
                while (next < source.size()) {
                    String child = source.get(next);
                    if (!child.startsWith("  ") || child.trim().startsWith("- ")) break;
                    values.add(child.trim());
                    next++;
                }
                result.add("- {" + String.join(", ", values) + "}");
                cursor = next;
            }
            if (cursor > index + 1) {
                index = cursor - 1;
            }
        }
        return result;
    }

    private static List<String> addCaseHeader(List<String> source) {
        List<String> result = new ArrayList<>(CASE_FILE_HEADER.size() + source.size() + 1);
        result.addAll(CASE_FILE_HEADER);
        result.add("");
        result.addAll(source);
        return result;
    }

    private static List<String> stripManagedDecorations(List<String> source) {
        Set<String> sectionComments = Set.of(
                "# Основное",
                "# Голограмма",
                "# Витрина",
                "# Меню",
                "# Меню кейса",
                "# Стоимость открытия",
                "# Анимация",
                "# Награды"
        );
        List<String> result = new ArrayList<>(source.size());
        for (int index = 0; index < source.size(); index++) {
            String trimmed = source.get(index).trim();
            if (trimmed.equals("# Плейсхолдеры pnCases")
                    || trimmed.equals("# pnCases — конфигурация кейса")) {
                int cursor = index + 1;
                while (cursor < source.size()) {
                    String next = source.get(cursor).trim();
                    if (!next.isEmpty() && !next.startsWith("#")) break;
                    cursor++;
                }
                index = cursor - 1;
                continue;
            }
            if (sectionComments.contains(trimmed)
                    || (trimmed.startsWith("# ━") && trimmed.endsWith("━"))) {
                continue;
            }
            result.add(source.get(index));
        }
        while (!result.isEmpty() && result.get(0).isBlank()) result.remove(0);
        return result;
    }

    private static String leadingWhitespace(String value) {
        int index = 0;
        while (index < value.length() && Character.isWhitespace(value.charAt(index))) index++;
        return value.substring(0, index);
    }

    static Map<String, Object> snapshotSection(ConfigurationSection section) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            copy.put(key, deepCopyValue(section.get(key)));
        }
        return copy;
    }

    static void restoreSection(ConfigurationSection section, Map<String, Object> snapshot) {
        clearSection(section);
        restoreEntries(section, snapshot);
    }

    private static void restoreEntries(ConfigurationSection section, Map<?, ?> values) {
        for (Map.Entry<?, ?> entry : values.entrySet()) {
            String key = String.valueOf(entry.getKey());
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) {
                ConfigurationSection child = section.createSection(key);
                restoreEntries(child, nested);
            } else {
                section.set(key, deepCopyValue(value));
            }
        }
    }

    private static List<?> deepCopyList(List<?> source) {
        List<Object> copy = new ArrayList<>();
        if (source == null) return copy;
        for (Object value : source) copy.add(deepCopyValue(value));
        return copy;
    }

    private static Object deepCopyValue(Object value) {
        if (value instanceof ConfigurationSection section) return snapshotSection(section);
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), deepCopyValue(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) return deepCopyList(list);
        return value;
    }

    public static String defaultDisplayName(String caseName) {
        return "&x&4&2&9&F&9&1" + caseName;
    }

    public static YamlConfiguration defaultConfiguration(String caseName) {
        YamlConfiguration yaml = new YamlConfiguration();
        writeNewCaseDefaults(yaml, normalizeName(caseName));
        return yaml;
    }

    static void writeNewCaseDefaults(ConfigurationSection root, String caseName) {
        String visibleName = defaultDisplayName(caseName);
        root.set("blocks", List.of());
        root.set("display-name", visibleName);

        ConfigurationSection hologram = root.createSection("hologram");
        hologram.set("enabled", true);
        hologram.set("type", "TEXT");
        hologram.set("y", 1.5D);
        hologram.set("lines", List.of("{display-name}", "&7Нажмите правую кнопку мыши, чтобы открыть"));

        ConfigurationSection showcase = root.createSection("idle-particles");
        showcase.set("enabled", true);
        showcase.set("theme", "DRAGON");
        showcase.set("view_distance", 28);

        ConfigurationSection gui = root.createSection("gui");
        gui.set("title", "{display-name}");
        gui.set("size", 54);
        gui.set("open_slot", 22);
        gui.set("animation_slot", 49);
        gui.set("preview_enabled", true);
        ConfigurationSection decor = gui.createSection("decor");
        decor.set("slots", List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 17, 18, 26, 27, 35,
                36, 37, 38, 39, 40, 41, 42, 43, 44));
        ConfigurationSection decorItem = decor.createSection("item");
        decorItem.set("material", "GRAY_STAINED_GLASS_PANE");
        decorItem.set("name", " ");
        ConfigurationSection history = gui.createSection("history");
        history.set("slots", List.of(45, 46, 47, 48, 50, 51, 52, 53));
        ConfigurationSection openItem = gui.createSection("button");
        openItem.set("material", "CHEST");
        openItem.set("name", "{display-name}");
        openItem.set("lore", List.of(
                "",
                " &#A6B0BAОткрытие и просмотр содержимого кейса",
                "",
                " &#C2CBD3Ключи",
                " &#69747E↳ &#A7F3D0{have} / {need}",
                "",
                " &#FFB347[&#FFA43C Л&#FF9536К&#FF8630М &#FF772A] &f{left_action}",
                " &#FF8A8A[&#FF7979 П&#F96868К&#F25757М &#EB4646] &f{right_action}",
                " &#FFB347[&#FFA43C SHIFT + ПКМ &#FF772A] &f{bulk_action}",
                ""
        ));
        ConfigurationSection insufficientKeys = gui.createSection("insufficient-keys");
        insufficientKeys.set("name", "&#FF5A64Недостаточно ключей");
        insufficientKeys.set("lore", List.of(
                "",
                " &#A6B0BAДля открытия нужен ещё один ключ",
                "",
                " &#C2CBD3Ключи",
                " &#69747E↳ &#A7F3D0{have} / {need}",
                ""
        ));
        ConfigurationSection broadcast = root.createSection("broadcast");
        broadcast.set("enabled", true);
        broadcast.set("lines", CaseDefinition.DEFAULT_BROADCAST_LINES);

        ConfigurationSection cost = root.createSection("cost");
        ConfigurationSection xpPurchase = cost.createSection("xp-purchase");
        xpPurchase.set("enabled", false);
        xpPurchase.set("levels", 0);

        root.set("rewards", List.of(Map.of(
                "chance", 100,
                "rarity", "COMMON",
                "type", "ITEM",
                "item", Map.of("material", "CHEST", "amount", 1, "name", "&fНаграда кейса")
        )));
    }

    public record Source(String name, ConfigurationSection section) {
    }

    public record LoadResult(List<Source> sources, boolean successful) {
        public LoadResult {
            sources = List.copyOf(sources);
        }
    }

    public record Writable(YamlConfiguration yaml, File file) {
        public ConfigurationSection section() {
            return yaml;
        }
    }
}
