package ru.privatenull.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiConfigTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void validReadbackReplacesTargetWithoutCreatingBackupFile() throws Exception {
        File target = temporaryDirectory.resolve("gui.yml").toFile();
        YamlConfiguration previous = new YamlConfiguration();
        previous.set("title", "previous");
        previous.save(target);

        YamlConfiguration updated = new YamlConfiguration();
        updated.set("title", "updated");
        assertTrue(UiConfig.saveYaml(updated, target, Logger.getLogger("test")));

        assertEquals("updated", YamlConfiguration.loadConfiguration(target).getString("title"));
        assertFalse(new File(target.getPath() + ".bak").exists());
    }
}
