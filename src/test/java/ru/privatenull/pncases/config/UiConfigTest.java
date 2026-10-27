package ru.privatenull.pncases.config;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import ru.privatenull.pncases.support.text.ColorUtil;

import static org.junit.jupiter.api.Assertions.*;

class UiConfigTest {
    @Test
    void batchNotificationsAreSingleSummariesWithWorkingNumericPlaceholders() throws Exception {
        try (var input = getClass().getResourceAsStream("/messages.yml")) {
            assertNotNull(input);
            YamlConfiguration messages = new YamlConfiguration();
            messages.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            for (String key : List.of("completed", "broadcast")) {
                String template = messages.isList("bulk-opening." + key)
                        ? String.join(" ", messages.getStringList("bulk-opening." + key))
                        : messages.getString("bulk-opening." + key);
                assertNotNull(template);
                String formatted = UiConfig.replace(template, "prefix", "", "amount", "10",
                        "case", "Донат", "case_id", "donate", "player", "Tester");
                String plain = ChatColor.stripColor(ColorUtil.colorize(formatted));
                assertTrue(plain.contains("10"));
                assertTrue(plain.contains("Донат"));
                assertTrue(plain.contains("{rewards}") || !plain.contains("{"));
            }
        }
    }

    @Test
    void rgbAndRgbaTemplatesPreserveNumericPlaceholders() {
        for (String prefix : List.of("&#A7F3D0", "&#A7F3D080")) {
            for (String number : List.of("10", "112", "0.112%")) {
                String result = ColorUtil.colorize(UiConfig.replace(prefix + "{amount}", "amount", number));
                assertEquals(number, ChatColor.stripColor(result));
            }
        }
    }

    @Test
    void replacementCanStillContainItsOwnColors() {
        String result = ColorUtil.colorize(UiConfig.replace("&#A7F3D0{reward}", "reward", "&cРыцарь"));
        assertEquals("Рыцарь", ChatColor.stripColor(result));
        assertTrue(result.endsWith("§cРыцарь"));
    }

    @Test
    void bundledGuiParsesAndBulkCountersKeepTheirDigits() throws Exception {
        try (var input = getClass().getResourceAsStream("/gui.yml")) {
            assertNotNull(input);
            YamlConfiguration gui = new YamlConfiguration();
            gui.load(new InputStreamReader(input, StandardCharsets.UTF_8));
            List<String> rendered = gui.getStringList("bulk-open.option.lore").stream()
                    .map(line -> UiConfig.replace(line, "amount", "10", "have", "112"))
                    .map(ColorUtil::colorize).map(ChatColor::stripColor).toList();
            assertTrue(rendered.contains(" ↳ 10"));
            assertTrue(rendered.contains(" ↳ 112"));
            assertEquals("&0Награды серии · {amount}", gui.getString("bulk-open.results.title"));
            assertFalse(gui.getStringList("bulk-open.results.lore").isEmpty());
        }
    }
}
