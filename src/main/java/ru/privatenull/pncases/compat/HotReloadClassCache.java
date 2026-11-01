package ru.privatenull.pncases.compat;

import ru.privatenull.pncases.integration.Log;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
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
    private static final String LIBRARY_PATH_PREFIX = CLASS_PATH_PREFIX + "libs/";
    private static final String PN_LIBRARY_PATH_PREFIX = LIBRARY_PATH_PREFIX + "pnlibrary/";
    private static final String ANVIL_GUI_PATH_PREFIX = LIBRARY_PATH_PREFIX + "anvilgui/";
    private static final String ANVIL_GUI_WRAPPER_PATH_PREFIX = ANVIL_GUI_PATH_PREFIX + "version/Wrapper";
    private static boolean loaded;

    private HotReloadClassCache() {
    }

    public static synchronized void preload(JavaPlugin plugin) {
        if (loaded) return;

        List<String> classNames;
        List<String> wrapperNames = List.of();
        try {
            Path source = Path.of(plugin.getClass().getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            classNames = Files.isDirectory(source)
                    ? classesFromDirectory(source)
                    : classesFromJar(source);
            if (!Files.isDirectory(source)) wrapperNames = anvilWrappersFromJar(source);
        } catch (IOException | URISyntaxException | RuntimeException exception) {
            Log.warning("Не удалось подготовить классы для безопасного restart: "
                    + message(exception));
            return;
        }

        ClassLoader loader = plugin.getClass().getClassLoader();
        int prepared = 0;
        int skipped = 0;
        for (String className : classNames) {
            if (!supportsRuntimeBytecode(loader, className)) {
                skipped++;
                continue;
            }
            try {
                Class.forName(className, false, loader);
                prepared++;
            } catch (ClassNotFoundException | LinkageError exception) {
                skipped++;
            }
        }
        // The AnvilGUI wrapper for this server version (and its AnvilContainer) is picked and loaded only when the
        // first input window opens. If the JAR was replaced or the classloader restarted by then, that load fails
        // and no window can open. Wrappers of other versions cannot be loaded here and are skipped quietly.
        int wrappers = 0;
        for (String className : wrapperNames) {
            try {
                Class.forName(className, false, loader);
                wrappers++;
            } catch (ClassNotFoundException | LinkageError ignored) {
                // A wrapper for another Minecraft version: its server classes do not exist here.
            }
        }
        loaded = true;
        Log.info("Классы подготовлены для безопасного restart: "
                + prepared + (skipped == 0 ? "" : ", пропущено необязательных: " + skipped)
                + ", классов окна ввода для этой версии сервера: " + wrappers);
    }

    private static List<String> anvilWrappersFromJar(Path source) throws IOException {
        List<String> names = new ArrayList<>();
        try (JarFile jar = new JarFile(source.toFile())) {
            jar.stream()
                    .map(entry -> entry.getName())
                    .filter(path -> path.startsWith(ANVIL_GUI_WRAPPER_PATH_PREFIX) && path.endsWith(".class"))
                    .map(HotReloadClassCache::toClassName)
                    .forEach(names::add);
        }
        names.sort(Comparator.naturalOrder());
        return names;
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
        boolean firstParty = path.startsWith(CLASS_PATH_PREFIX)
                && !path.startsWith(LIBRARY_PATH_PREFIX);
        boolean restartCriticalLibrary = path.startsWith(PN_LIBRARY_PATH_PREFIX)
                || (path.startsWith(ANVIL_GUI_PATH_PREFIX)
                && !path.startsWith(ANVIL_GUI_WRAPPER_PATH_PREFIX));
        return (firstParty || restartCriticalLibrary)
                && path.endsWith(".class")
                && !path.endsWith("module-info.class");
    }

    private static String toClassName(String path) {
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static boolean supportsRuntimeBytecode(ClassLoader loader, String className) {
        String resource = className.replace('.', '/') + ".class";
        try (InputStream stream = loader.getResourceAsStream(resource)) {
            if (stream == null) return true;
            DataInputStream data = new DataInputStream(stream);
            if (data.readInt() != 0xCAFEBABE) return true;
            data.readUnsignedShort();
            int major = data.readUnsignedShort();
            return major <= Runtime.version().feature() + 44;
        } catch (IOException ignored) {
            return true;
        }
    }

    private static String message(Throwable exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
