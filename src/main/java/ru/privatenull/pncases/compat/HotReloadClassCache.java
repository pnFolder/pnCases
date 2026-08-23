package ru.privatenull.pncases.compat;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;
import java.util.stream.Stream;

/**
 * Loads the plugin's own classes while its JAR is still open.
 *
 * <p>PlugManX closes Bukkit's plugin classloader and then may enable the same
 * plugin instance again. Preloading prevents later GUI and command paths from
 * trying to read another class from that already closed ZIP file.</p>
 */
public final class HotReloadClassCache {

    private static final String CLASS_PATH_PREFIX = "ru/privatenull/pncases/";
    private static boolean loaded;

    private HotReloadClassCache() {
    }

    public static synchronized void preload(JavaPlugin plugin) {
        if (loaded) return;

        List<String> classNames;
        try {
            Path source = Path.of(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            classNames = Files.isDirectory(source)
                    ? classesFromDirectory(source)
                    : classesFromJar(source);
        } catch (IOException | URISyntaxException | RuntimeException exception) {
            plugin.getLogger().warning("Не удалось подготовить классы для безопасного restart: "
                    + message(exception));
            return;
        }

        ClassLoader loader = plugin.getClass().getClassLoader();
        int prepared = 0;
        int skipped = 0;
        for (String className : classNames) {
            try {
                Class.forName(className, false, loader);
                prepared++;
            } catch (ClassNotFoundException | LinkageError exception) {
                skipped++;
            }
        }
        loaded = true;
        plugin.getLogger().info("Классы подготовлены для безопасного restart: "
                + prepared + (skipped == 0 ? "" : ", пропущено необязательных: " + skipped));
    }

    private static List<String> classesFromJar(Path source) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(source.toFile())) {
            jar.stream()
                    .map(entry -> entry.getName())
                    .filter(HotReloadClassCache::isPluginClass)
                    .map(HotReloadClassCache::toClassName)
                    .forEach(names::add);
        }
        names.sort(Comparator.naturalOrder());
        return names;
    }

    private static List<String> classesFromDirectory(Path source) throws IOException {
        try (Stream<Path> files = Files.walk(source.resolve("ru/privatenull/pncases"))) {
            return files.filter(Files::isRegularFile)
                    .map(source::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(HotReloadClassCache::isPluginClass)
                    .map(HotReloadClassCache::toClassName)
                    .sorted()
                    .toList();
        }
    }

    private static boolean isPluginClass(String path) {
        return path.startsWith(CLASS_PATH_PREFIX)
                && path.endsWith(".class")
                && !path.endsWith("module-info.class");
    }

    private static String toClassName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static String message(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
