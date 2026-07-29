package ru.privatenull.config;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import ru.privatenull.PnCasesPlugin;
import ru.privatenull.cases.config.CaseConfigRepository;
import ru.privatenull.cases.model.Reward;

import static ru.privatenull.cases.config.RewardConfigParser.firstPresent;
import static ru.privatenull.cases.config.RewardConfigParser.parseType;
import static ru.privatenull.cases.config.RewardConfigParser.nestedMap;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class ConfigValidator {

    private ConfigValidator() {
    }

    public static Result validateAndPatch(PnCasesPlugin plugin) {
        FileConfiguration config = plugin.getConfig();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        List<String> patches = new ArrayList<>();

        boolean changed = patchMissing(config, patches, "reward-symbols.vault", "$");
        changed |= patchMissing(config, patches, "database.type", "SQLITE");
        changed |= patchMissing(config, patches, "database.sqlite.file", "data.db");
        changed |= patchMissing(config, patches, "database.sqlite.connection-timeout-ms", 10_000L);
        changed |= patchMissing(config, patches, "reward-symbols.playerpoints", "✦");
        changed |= patchMissing(config, patches, "holograms.provider", "AUTO");

        Set<String> knownKeys = validateKeys(config, warnings, errors);
        validateCases(plugin, knownKeys, warnings, errors);

        if (changed) {
            boolean saved = new CaseConfigRepository(plugin).saveMainConfig();
            if (!saved) {
                errors.add("Не удалось безопасно сохранить добавленные поля config.yml.");
            }
        }

        if (!patches.isEmpty()) {
            plugin.getLogger().info("Config validation: добавлены недостающие поля config.yml: " + String.join(", ", patches));
        }
        for (String warning : warnings) {
            plugin.getLogger().warning("Config warning: " + warning);
        }
        for (String error : errors) {
            plugin.getLogger().severe("Config error: " + error);
        }

        if (warnings.isEmpty() && errors.isEmpty()) {
            plugin.getLogger().info("Config validation: config.yml корректен.");
        } else {
            plugin.getLogger().warning("Config validation: найдено ошибок: " + errors.size() + ", предупреждений: " + warnings.size() + ".");
        }

        return new Result(errors.size(), warnings.size(), patches.size());
    }

    private static boolean patchMissing(FileConfiguration config, List<String> patches, String path, Object value) {
        if (config.contains(path)) {
            return false;
        }
        config.set(path, value);
        patches.add(path);
        return true;
    }

    private static Set<String> validateKeys(FileConfiguration config, List<String> warnings, List<String> errors) {
        Set<String> knownKeys = new HashSet<>();
        ConfigurationSection keys = config.getConfigurationSection("keys");
        if (keys == null) {
            errors.add("Секция keys отсутствует. Виртуальные ключи кейсов не смогут работать.");
            return knownKeys;
        }

        for (String keyId : keys.getKeys(false)) {
            knownKeys.add(keyId.toLowerCase(Locale.ROOT));
            ConfigurationSection key = keys.getConfigurationSection(keyId);
            if (key == null) {
                warnings.add("keys." + keyId + " должен быть секцией с name, например: name: '&aКлюч'.");
                continue;
            }
            if (!key.isString("name")) {
                warnings.add("keys." + keyId + ".name отсутствует. В игре будет показан технический id ключа.");
            }
        }
        return knownKeys;
    }

    private static void validateCases(PnCasesPlugin plugin, Set<String> knownKeys,
                                      List<String> warnings, List<String> errors) {
        Set<String> seenIds = new HashSet<>();
        Map<String, String> blockOwners = new HashMap<>();
        File directory = new File(plugin.getDataFolder(), "cases");
        File[] files = directory.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files != null) {
            Arrays.sort(files, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            for (File file : files) {
                String fileName = file.getName();
                String caseId = CaseConfigRepository.normalizeName(fileName.substring(0, fileName.length() - 4));
                String path = "cases/" + fileName;
                if (!CaseConfigRepository.isValidId(caseId)) {
                    errors.add(path + " имеет недопустимое имя. Используйте только a-z, 0-9, _ и - (до 64 символов).");
                    continue;
                }
                if (!seenIds.add(caseId)) {
                    errors.add(path + " дублирует id кейса '" + caseId + "'.");
                    continue;
                }

                YamlConfiguration yaml = new YamlConfiguration();
                try {
                    yaml.load(file);
                } catch (IOException | InvalidConfigurationException ex) {
                    errors.add(path + " не читается как YAML: " + ex.getMessage());
                    continue;
                }
                ConfigurationSection section = yaml;
                warnIdMismatch(path, caseId, section, warnings);
                validateCase(path, section, knownKeys, warnings, errors);
                validateUniqueBlocks(path, caseId, section, blockOwners, errors);
            }
        }

        // A new installation intentionally has no cases. Administrators create
        // standalone configs with /pncases createcase <name>.
    }

    private static void warnIdMismatch(String path, String canonicalId, ConfigurationSection section, List<String> warnings) {
        String explicitId = section.getString("id");
        if (explicitId != null && !explicitId.isBlank()
                && !canonicalId.equals(CaseConfigRepository.normalizeName(explicitId))) {
            warnings.add(path + " содержит id='" + explicitId + "', но будет использован id '" + canonicalId
                    + "' из имени файла.");
        }
    }

    private static void validateCase(
            String path,
            ConfigurationSection section,
            Set<String> knownKeys,
            List<String> warnings,
            List<String> errors
    ) {
        validateBlock(path, section, warnings);
        validateGui(path, section, warnings);
        validateBroadcast(path, section, warnings);
        validateCost(path, section, warnings);
        validateAnimation(path, section, warnings);
        validateRewards(path, section, warnings, errors);
    }

    private static void validateUniqueBlocks(
            String path,
            String caseId,
            ConfigurationSection section,
            Map<String, String> owners,
            List<String> errors
    ) {
        registerBlock(path + ".block", caseId, blockKey(section.getConfigurationSection("block")), owners, errors);
        List<?> blocks = section.getList("blocks", List.of());
        for (int index = 0; index < blocks.size(); index++) {
            Object raw = blocks.get(index);
            String key = null;
            if (raw instanceof ConfigurationSection block) {
                key = blockKey(block);
            } else if (raw instanceof Map<?, ?> block) {
                key = blockKey(block.get("world"), block.get("x"), block.get("y"), block.get("z"));
            }
            registerBlock(path + ".blocks[" + index + "]", caseId, key, owners, errors);
        }
    }

    private static void registerBlock(
            String path,
            String caseId,
            String blockKey,
            Map<String, String> owners,
            List<String> errors
    ) {
        if (blockKey == null) return;
        String owner = owners.putIfAbsent(blockKey, caseId);
        if (owner != null && !owner.equals(caseId)) {
            errors.add(path + " конфликтует с кейсом '" + owner + "': один блок нельзя привязать к двум кейсам.");
        }
    }

    private static String blockKey(ConfigurationSection block) {
        return block == null ? null : blockKey(block.get("world"), block.get("x"), block.get("y"), block.get("z"));
    }

    private static String blockKey(Object worldValue, Object xValue, Object yValue, Object zValue) {
        if (worldValue == null || String.valueOf(worldValue).isBlank()) return null;
        Integer x = strictInt(xValue);
        Integer y = strictInt(yValue);
        Integer z = strictInt(zValue);
        if (x == null || y == null || z == null) return null;
        return String.valueOf(worldValue).trim().toLowerCase(Locale.ROOT) + ':' + x + ':' + y + ':' + z;
    }

    private static Integer strictInt(Object value) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static void validateBlock(String path, ConfigurationSection section, List<String> warnings) {
        List<?> blocks = section.getList("blocks");
        if (blocks != null && !blocks.isEmpty()) {
            for (int i = 0; i < blocks.size(); i++) {
                Object entry = blocks.get(i);
                if (entry instanceof Map<?, ?> map) {
                    validateBlockMap(path + ".blocks[" + i + "]", map, warnings);
                } else {
                    warnings.add(path + ".blocks[" + i + "] must be a section with world, x, y, z.");
                }
            }
            return;
        }

        ConfigurationSection block = section.getConfigurationSection("block");
        if (block == null) {
            warnings.add(path + ".block отсутствует. Кейс загружен как настройка, но не привязан к блоку. Используйте /pncases setcase <кейс>.");
            return;
        }

        String world = block.getString("world");
        if (world == null || world.isBlank()) {
            warnings.add(path + ".block.world отсутствует. Кейс загружен, но блок не активен.");
        } else if (Bukkit.getWorld(world) == null) {
            warnings.add(path + ".block.world = '" + world + "' не найден на сервере. Настройки кейса загружены, но блок и голограмма будут активны после /pncases setcase <кейс> или загрузки мира.");
        }

        requireInt(block, path + ".block", "x", warnings);
        requireInt(block, path + ".block", "y", warnings);
        requireInt(block, path + ".block", "z", warnings);
    }

    private static void validateBlockMap(String path, Map<?, ?> block, List<String> warnings) {
        Object worldRaw = block.get("world");
        String world = worldRaw == null ? null : String.valueOf(worldRaw);
        if (world == null || world.isBlank()) {
            warnings.add(path + ".world is missing. The case config is loaded, but this block is inactive.");
        } else if (Bukkit.getWorld(world) == null) {
            warnings.add(path + ".world = '" + world + "' is not loaded on the server. The case block will work after the world exists or after /pncases setcase <case>.");
        }

        requireIntValue(block.get("x"), path, "x", warnings);
        requireIntValue(block.get("y"), path, "y", warnings);
        requireIntValue(block.get("z"), path, "z", warnings);
    }

    private static void requireIntValue(Object value, String path, String key, List<String> warnings) {
        if (value instanceof Number) {
            return;
        }
        try {
            Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            warnings.add(path + "." + key + " must be an integer.");
        }
    }

    private static void validateGui(String path, ConfigurationSection section, List<String> warnings) {
        ConfigurationSection gui = section.getConfigurationSection("gui");
        if (gui == null) {
            warnings.add(path + ".gui отсутствует. Будет использована стандартная кнопка CHEST.");
            return;
        }

        ConfigurationSection item = gui.getConfigurationSection("button");
        if (item == null) {
            warnings.add(path + ".gui.button отсутствует. Будет использована стандартная кнопка CHEST.");
            return;
        }

        validateMaterial(path + ".gui.button", item, warnings);
    }

    private static void validateBroadcast(
            String path,
            ConfigurationSection section,
            List<String> warnings
    ) {
        ConfigurationSection broadcast = section.getConfigurationSection("broadcast");
        if (broadcast == null) return;
        if (broadcast.contains("enabled", true) && !broadcast.isBoolean("enabled")) {
            warnings.add(path + ".broadcast.enabled должен быть true или false.");
        }
        if (broadcast.getBoolean("enabled", true) && !broadcast.isList("lines")) {
            warnings.add(path + ".broadcast.lines должен быть списком строк.");
        }
    }

    private static void validateCost(String path, ConfigurationSection section, List<String> warnings) {
        ConfigurationSection cost = section.getConfigurationSection("cost");
        if (cost == null) {
            warnings.add(path + ".cost отсутствует. Кейс будет бесплатным.");
            return;
        }

        ConfigurationSection xpPurchase = cost.getConfigurationSection("xp-purchase");
        if (xpPurchase != null) {
            if (xpPurchase.contains("enabled", true) && !xpPurchase.isBoolean("enabled")) {
                warnings.add(path + ".cost.xp-purchase.enabled должен быть true или false.");
            }
            if (xpPurchase.getBoolean("enabled", false) && xpPurchase.getInt("levels", 0) <= 0) {
                warnings.add(path + ".cost.xp-purchase.levels должен быть больше 0, когда покупка включена.");
            }
        }

    }

    private static void validateAnimation(String path, ConfigurationSection section, List<String> warnings) {
        ConfigurationSection animation = section.getConfigurationSection("animation");
        if (animation == null) return;

        List<?> items = animation.getList("items");
        if (items == null || items.isEmpty()) return;

        for (int i = 0; i < items.size(); i++) {
            Object item = items.get(i);
            if (item instanceof ConfigurationSection itemSection) {
                validateMaterial(path + ".animation.items[" + i + "]", itemSection, warnings);
            } else if (item instanceof java.util.Map<?, ?> map) {
                validateMaterial(path + ".animation.items[" + i + "]", map.get("material"), map.get("base64"), warnings);
            } else {
                warnings.add(path + ".animation.items[" + i + "] должен быть секцией предмета.");
            }
        }
    }

    private static void validateRewards(String path, ConfigurationSection section, List<String> warnings, List<String> errors) {
        List<?> rewards = section.getList("rewards");
        if (rewards == null || rewards.isEmpty()) {
            errors.add(path + ".rewards отсутствует или пустой. Кейс не будет загружен.");
            return;
        }

        for (int i = 0; i < rewards.size(); i++) {
            Object reward = rewards.get(i);
            String rewardPath = path + ".rewards[" + i + "]";
            if (!(reward instanceof java.util.Map<?, ?> map)) {
                errors.add(rewardPath + " должен быть секцией награды.");
                continue;
            }

            int chance = asInt(map.get("chance"), 0);
            if (chance <= 0) {
                errors.add(rewardPath + ".chance должен быть больше 0. Награда будет пропущена.");
            }

            String rawType = String.valueOf(map.containsKey("type") ? map.get("type") : "ITEM");
            Reward.Type rewardType = parseType(rawType);
            String type = rewardType == null ? null : rewardType.name();
            if (type == null) {
                errors.add(rewardPath + ".type = '" + rawType + "' неизвестен. Доступно: ITEM, LUCKPERMS, VAULT, PLAYERPOINTS, PERSONAL.");
                continue;
            }
            Object rarity = map.get("rarity");
            if (rarity != null && !isKnownRarity(String.valueOf(rarity))) {
                warnings.add(rewardPath + ".rarity = '" + rarity + "' unknown. Available: COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC.");
            }

            switch (type) {
                case "ITEM" -> validateItemReward(rewardPath, map, warnings, errors);
                case "LUCKPERMS" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    validateLuckPermsReward(rewardPath, map, errors);
                }
                case "VAULT" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    validateVaultReward(rewardPath, map, warnings, errors);
                }
                case "PLAYERPOINTS" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    validatePlayerPointsReward(rewardPath, map, warnings, errors);
                }
                case "PERSONAL" -> {
                    validateVisualReward(rewardPath, map, warnings);
                    Object command = map.get("command");
                    if (command == null || String.valueOf(command).isBlank()) {
                        errors.add(rewardPath + ".command обязателен для type: PERSONAL.");
                    }
                }
                default -> {
                }
            }
        }
    }

    private static void validateItemReward(String path, java.util.Map<?, ?> map, List<String> warnings, List<String> errors) {
        Object item = map.get("item");
        if (!(item instanceof java.util.Map<?, ?> itemMap)) {
            errors.add(path + ".item отсутствует для type: ITEM.");
            return;
        }
        validateMaterial(path + ".item", itemMap.get("material"), itemMap.get("base64"), warnings);
    }

    private static void validateVisualReward(String path, java.util.Map<?, ?> map, List<String> warnings) {
        if (map.containsKey("item")) {
            warnings.add(path + ".item is an old visual alias for non-ITEM rewards. Use .visual instead.");
        }

        Object visual = firstPresent(map, "visual", "item");
        if (visual instanceof java.util.Map<?, ?> visualMap) {
            validateMaterial(path + ".visual", visualMap.get("material"), visualMap.get("base64"), warnings);
        }
    }

    private static void validateLuckPermsReward(String path, java.util.Map<?, ?> map, List<String> errors) {
        Object luckPerms = map.get("luckperms");
        if (!(luckPerms instanceof java.util.Map<?, ?> lpMap)) {
            errors.add(path + ".luckperms отсутствует для type: LUCKPERMS.");
            return;
        }

        Object group = lpMap.get("group");
        Object node = lpMap.get("node");
        if (isBlank(group) && isBlank(node)) {
            errors.add(path + ".luckperms должен содержать group или node.");
        }
    }

    private static void validateVaultReward(String path, java.util.Map<?, ?> map, List<String> warnings, List<String> errors) {
        java.util.Map<?, ?> vault = nestedMap(map, "vault");
        warnIgnoredCurrencyDisplayName(path + ".vault", vault, warnings);
        Object amount = firstPresent(vault, "amount");
        if (asDouble(amount, 0.0) <= 0.0) {
            errors.add(path + ".vault.amount должен быть больше 0.");
        }
    }

    private static void validatePlayerPointsReward(String path, java.util.Map<?, ?> map, List<String> warnings, List<String> errors) {
        java.util.Map<?, ?> points = nestedMap(map, "playerpoints");
        warnIgnoredCurrencyDisplayName(path + ".playerpoints", points, warnings);
        Object amount = firstPresent(points, "amount");
        if (asInt(amount, 0) <= 0) {
            errors.add(path + ".playerpoints.amount должен быть больше 0.");
        }
    }

    private static void warnIgnoredCurrencyDisplayName(String path, java.util.Map<?, ?> map, List<String> warnings) {
        if (map.get("name") != null) {
            warnings.add(path + ".name игнорируется для виртуальной валюты. Используйте .visual.name.");
        }
    }

    private static void validateMaterial(String path, ConfigurationSection section, List<String> warnings) {
        validateMaterial(path, section.get("material"), section.get("base64"), warnings);
    }

    private static void validateMaterial(String path, Object materialRaw, Object base64Raw, List<String> warnings) {
        if (hasBase64(base64Raw) || hasMaterialBase64(materialRaw)) {
            return;
        }

        String material = materialRaw == null ? null : String.valueOf(materialRaw);
        if (material == null || material.isBlank()) {
            warnings.add(path + ".material отсутствует и base64 не указан. Будет использован STONE.");
            return;
        }

        if (Material.matchMaterial(material) == null) {
            warnings.add(path + ".material = '" + material + "' не найден в этой версии Paper. Будет использован STONE.");
        }
    }

    private static boolean hasBase64(Object value) {
        return value instanceof String base64 && !base64.isBlank();
    }

    private static boolean hasMaterialBase64(Object value) {
        if (!(value instanceof String material) || material.isBlank()) {
            return false;
        }
        String normalized = material.trim().toLowerCase(Locale.ROOT);
        return normalized.startsWith("base64-") || normalized.startsWith("base64:");
    }

    private static void requireInt(ConfigurationSection section, String path, String key, List<String> errors) {
        if (!section.isInt(key)) {
            errors.add(path + "." + key + " должен быть целым числом.");
        }
    }

    private static boolean isKnownRarity(String value) {
        if (value == null || value.isBlank()) return true;
        return switch (value.trim().toUpperCase(Locale.ROOT)) {
            case "COMMON", "ОБЫЧНАЯ", "DEFAULT",
                 "UNCOMMON", "НЕОБЫЧНАЯ",
                 "RARE", "РЕДКАЯ",
                 "EPIC", "ЭПИЧЕСКАЯ",
                 "LEGENDARY", "ЛЕГЕНДАРНАЯ",
                 "MYTHIC", "MYTHICAL", "МИФИЧЕСКАЯ" -> true;
            default -> false;
        };
    }

    private static boolean isBlank(Object value) {
        return value == null || String.valueOf(value).isBlank();
    }

    private static int asInt(Object value, int fallback) {
        if (value instanceof Number number) return number.intValue();
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static double asDouble(Object value, double fallback) {
        if (value instanceof Number number) return number.doubleValue();
        try {
            return Double.parseDouble(String.valueOf(value));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    public record Result(int errors, int warnings, int patches) {
        public boolean hasProblems() {
            return errors > 0 || warnings > 0;
        }
    }
}
