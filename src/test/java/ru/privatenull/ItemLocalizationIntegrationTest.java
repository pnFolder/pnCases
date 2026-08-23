package ru.privatenull;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import ru.privatenull.pnlibrary.localization.ItemLocalization;
import ru.privatenull.pnlibrary.localization.MinecraftLocale;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemLocalizationIntegrationTest {

    @Test
    void loadsBundledLocalesFromPnLibrary() {
        ItemLocalization russian = ItemLocalization.load(MinecraftLocale.RU_RU);
        ItemLocalization english = ItemLocalization.load(MinecraftLocale.EN_US);

        assertTrue(russian.translations().size() > 5_000);
        assertTrue(english.translations().size() > 5_000);
        assertEquals("Вагонетка с динамитом",
                russian.translate("item.minecraft.tnt_minecart"));
        assertEquals("Minecart with TNT",
                english.translate("item.minecraft.tnt_minecart"));
    }

    @Test
    void resourcesDeclareRouterLocalizationAndProtocolLib() throws Exception {
        var configStream = getClass().getClassLoader().getResourceAsStream("config.yml");
        assertNotNull(configStream);
        var config = YamlConfiguration.loadConfiguration(
                new InputStreamReader(configStream, StandardCharsets.UTF_8));
        assertEquals("ru_ru", config.getString("localization.locale"));
        assertNotNull(config.getString("database.redis.uri"));
        assertNotNull(config.getString("database.redis.namespace"));

        var pluginStream = getClass().getClassLoader().getResourceAsStream("plugin.yml");
        assertNotNull(pluginStream);
        var plugin = YamlConfiguration.loadConfiguration(
                new InputStreamReader(pluginStream, StandardCharsets.UTF_8));
        assertTrue(plugin.getStringList("depend").contains("ProtocolLib"));
    }
}
