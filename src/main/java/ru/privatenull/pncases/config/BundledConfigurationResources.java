package ru.privatenull.pncases.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BundledConfigurationResources {

    public static final List<String> DOCUMENTS = List.of("config.yml", "gui.yml", "messages.yml");

    private static final Map<String, byte[]> CACHE = new ConcurrentHashMap<>();

    private BundledConfigurationResources() {
    }

    /** Sample cases shipped in the jar; they go to a separate folder, so the server never loads them. */
    public static final List<String> EXAMPLES = List.of("README.txt", "01-items.yml", "02-economy.yml",
            "03-luckperms.yml", "04-console.yml", "05-full.yml", "06-bundle.yml", "07-proximity.yml",
            "08-placeholders.yml", "PLACEHOLDERS.txt");

    public static void installExamples(JavaPlugin plugin) {
        for (String name : EXAMPLES) {
            copyIfMissing(plugin, "examples/" + name, new java.io.File(plugin.getDataFolder(), "examples/" + name));
        }
    }

    public static void capture(JavaPlugin plugin) {
        for (String document : DOCUMENTS) {
            byte[] content = readDirect(plugin, document);
            if (content != null) CACHE.put(document, content);
        }
    }

    public static InputStream open(JavaPlugin plugin, String name) {
        byte[] current = readDirect(plugin, name);
        if (current != null) {
            CACHE.put(name, current);
            return new ByteArrayInputStream(current);
        }
        byte[] cached = CACHE.get(name);
        return cached == null ? null : new ByteArrayInputStream(cached);
    }

    public static boolean copyIfMissing(JavaPlugin plugin, String name, java.io.File target) {
        if (target.exists()) return true;
        try (InputStream input = open(plugin, name)) {
            if (input == null) return false;
            java.io.File parent = target.getParentFile();
            if (parent != null) Files.createDirectories(parent.toPath());
            Files.copy(input, target.toPath());
            return true;
        } catch (IOException exception) {
            plugin.getLogger().severe("Не удалось создать " + name + ": " + exception.getMessage());
            return false;
        }
    }

    private static byte[] readDirect(JavaPlugin plugin, String name) {
        try (InputStream input = plugin.getResource(name)) {
            return input == null ? null : input.readAllBytes();
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }
}
