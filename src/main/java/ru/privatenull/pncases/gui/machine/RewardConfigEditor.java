package ru.privatenull.pncases.gui.machine;

import org.bukkit.configuration.ConfigurationSection;
import ru.privatenull.pncases.cases.model.Reward;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure edits of the {@code rewards} list of a case file. Nothing here touches the Bukkit world. */
final class RewardConfigEditor {

    private RewardConfigEditor() {
    }

    static Map<String, Object> baseReward(Reward.Type type) {
        Map<String, Object> reward = new LinkedHashMap<>();
        reward.put("type", type.name());
        reward.put("chance", 100);
        reward.put("rarity", "COMMON");
        return reward;
    }

    static List<Map<String, Object>> rewardMaps(ConfigurationSection root) {
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Map<?, ?> map : root.getMapList("rewards")) rewards.add(deepMap(map));
        return rewards;
    }

    static Map<String, Object> deepMap(Map<?, ?> source) {
        Map<String, Object> copy = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) {
            if (entry.getKey() == null) continue;
            Object value = entry.getValue();
            if (value instanceof Map<?, ?> nested) value = deepMap(nested);
            else if (value instanceof List<?> list) value = new ArrayList<>(list);
            copy.put(String.valueOf(entry.getKey()), value);
        }
        return copy;
    }

    static List<String> configuredCommands(Map<String, Object> reward) {
        List<String> commands = new ArrayList<>();
        if (reward.get("commands") instanceof List<?> configured) {
            for (Object value : configured) {
                if (value == null) continue;
                String command = String.valueOf(value).trim().replaceFirst("^/", "");
                if (!command.isBlank()) commands.add(command);
            }
        }
        if (commands.isEmpty() && reward.get("command") != null) {
            String command = String.valueOf(reward.get("command")).trim().replaceFirst("^/", "");
            if (!command.isBlank()) commands.add(command);
        }
        return commands;
    }

    static void removeNestedMessage(Map<String, Object> reward, Reward.Type type) {
        String sectionName = switch (type) {
            case LUCKPERMS -> "luckperms";
            case VAULT -> "vault";
            case PLAYERPOINTS -> "playerpoints";
            case ITEM, PERSONAL -> null;
        };
        if (sectionName == null || !(reward.get(sectionName) instanceof Map<?, ?> configured)) return;
        Map<String, Object> section = deepMap(configured);
        section.remove("message");
        reward.put(sectionName, section);
    }

    static Integer positiveInteger(String value) {
        try {
            int parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    static Double positiveDouble(String value) {
        try {
            double parsed = Double.parseDouble(value.replace(',', '.'));
            return Double.isFinite(parsed) && parsed > 0.0 ? parsed : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    /** Validation message for the anvil input of a reward value, or null when the value is acceptable. */
    static String inputError(Reward.Type type, String value) {
        if (value == null || value.isBlank()) return "Введите значение";
        return switch (type) {
            case VAULT -> positiveDouble(value) == null ? "Введите число" : null;
            case PLAYERPOINTS -> positiveInteger(value) == null ? "Введите целое число" : null;
            case LUCKPERMS -> value.contains(" ") ? "Только название" : null;
            case PERSONAL -> value.equals("/") ? "Введите команду" : null;
            case ITEM -> null;
        };
    }

    /**
     * Weight that gives a reward the requested share of the pool, given the weights of all other
     * rewards. A lone reward always gets 100.
     */
    static int chanceForPercent(int otherWeights, double percent) {
        if (otherWeights == 0) return 100;
        double safePercent = Math.min(99.99, percent);
        return (int) Math.min(Integer.MAX_VALUE,
                Math.max(1L, Math.round(otherWeights * safePercent / (100.0 - safePercent))));
    }

    /** A share moved by {@code delta} percentage points, kept between 0.01 and 100 and rounded to two decimals. */
    static double steppedPercent(double current, double delta) {
        double moved = Math.round((current + delta) * 100.0) / 100.0;
        return Math.max(0.01, Math.min(100.0, moved));
    }

    /** Gives every reward the same weight, so all of them are equally likely. */
    static void equalizeChances(List<Map<String, Object>> rewards) {
        for (Map<String, Object> reward : rewards) reward.put("chance", 100);
    }

    /** Builds a new reward from the text typed in the create dialog, or null when the value is invalid. */
    static Map<String, Object> newReward(Reward.Type type, String value) {
        Map<String, Object> reward = baseReward(type);
        switch (type) {
            case VAULT -> {
                Double amount = positiveDouble(value);
                if (amount == null) return null;
                reward.put("vault", Map.of("amount", amount));
                reward.put("visual", Map.of("material", "EMERALD", "name", "&a" + amount + " монет"));
            }
            case PLAYERPOINTS -> {
                Integer amount = positiveInteger(value);
                if (amount == null) return null;
                reward.put("playerpoints", Map.of("amount", amount));
                reward.put("visual", Map.of("material", "EMERALD", "name", "&b" + amount + " поинтов"));
            }
            case LUCKPERMS -> {
                reward.put("luckperms", Map.of("group", value.trim()));
                reward.put("visual", Map.of("material", "NAME_TAG", "name", "&d" + value.trim()));
            }
            case PERSONAL -> {
                String command = value.startsWith("/") ? value.substring(1) : value;
                if (command.isBlank()) return null;
                reward.put("command", command);
                reward.put("name", "&eКомандная награда");
                reward.put("visual", Map.of("material", "COMMAND_BLOCK", "name", "&eКомандная награда"));
            }
            case ITEM -> {
                return null;
            }
        }
        return reward;
    }

    /** Changes the main value of an existing reward. Throws IllegalArgumentException for an invalid value. */
    static void applyValue(Map<String, Object> reward, Reward.Type type, String value) {
        switch (type) {
            case VAULT -> {
                Double amount = positiveDouble(value);
                if (amount == null) throw new IllegalArgumentException("amount");
                Map<String, Object> current = reward.get("vault") instanceof Map<?, ?> map
                        ? deepMap(map) : new LinkedHashMap<>();
                current.put("amount", amount);
                reward.put("vault", current);
            }
            case PLAYERPOINTS -> {
                Integer amount = positiveInteger(value);
                if (amount == null) throw new IllegalArgumentException("amount");
                Map<String, Object> current = reward.get("playerpoints") instanceof Map<?, ?> map
                        ? deepMap(map) : new LinkedHashMap<>();
                current.put("amount", amount);
                reward.put("playerpoints", current);
            }
            case LUCKPERMS -> {
                Map<String, Object> current = reward.get("luckperms") instanceof Map<?, ?> map
                        ? deepMap(map) : new LinkedHashMap<>();
                if (current.containsKey("node") && !current.containsKey("group")) current.put("node", value.trim());
                else current.put("group", value.trim());
                reward.put("luckperms", current);
            }
            case PERSONAL -> {
                String command = value.startsWith("/") ? value.substring(1) : value;
                if (reward.get("commands") instanceof List<?>) {
                    List<String> commands = configuredCommands(reward);
                    if (commands.isEmpty()) commands.add(command);
                    else commands.set(0, command);
                    reward.remove("command");
                    reward.put("commands", commands);
                } else {
                    reward.put("command", command);
                }
            }
            case ITEM -> throw new IllegalArgumentException("item");
        }
    }
}
