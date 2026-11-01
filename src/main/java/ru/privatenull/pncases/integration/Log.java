package ru.privatenull.pncases.integration;

import org.bukkit.plugin.java.JavaPlugin;
import org.pnfolder.pnlibrary.api.plugin.ModuleContext;

import java.util.logging.Level;

/**
 * Единая точка логирования pnCases. Пишет через логгер модуля pnLibrary; пока модуль не зарегистрирован
 * или уже закрыт, использует обычный логгер плагина, чтобы onEnable/onDisable ничего не роняли.
 */
public final class Log {

    private static volatile JavaPlugin plugin;
    private static volatile ModuleContext module;

    private Log() {
    }

    /** Запасной путь: обычный логгер плагина. */
    public static void bind(JavaPlugin owner) {
        plugin = owner;
    }

    /** Подключает логгер модуля pnLibrary; {@code null} возвращает запасной путь. */
    public static void bind(ModuleContext context) {
        module = context;
    }

    public static void info(String message) {
        try {
            ModuleContext context = module;
            if (context != null && !context.isClosed()) {
                context.getLogger().info(message);
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // падаем на запасной логгер
        }
        fallback(Level.INFO, message, null);
    }

    public static void success(String message) {
        try {
            ModuleContext context = module;
            if (context != null && !context.isClosed()) {
                context.getLogger().success(message);
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // падаем на запасной логгер
        }
        fallback(Level.INFO, message, null);
    }

    public static void warning(String message) {
        warning(message, null);
    }

    public static void warning(String message, Throwable failure) {
        try {
            ModuleContext context = module;
            // PnLogger.warning не принимает исключение: со стеком идём через JUL, его WARNING перехватывает pnLibrary.
            if (failure == null && context != null && !context.isClosed()) {
                context.getLogger().warning(message);
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // падаем на запасной логгер
        }
        fallback(Level.WARNING, message, failure);
    }

    public static void error(String message) {
        error(message, null);
    }

    public static void error(String message, Throwable failure) {
        try {
            ModuleContext context = module;
            if (context != null && !context.isClosed()) {
                context.getLogger().error(message, failure);
                return;
            }
        } catch (RuntimeException | LinkageError ignored) {
            // падаем на запасной логгер
        }
        fallback(Level.SEVERE, message, failure);
    }

    private static void fallback(Level level, String message, Throwable failure) {
        JavaPlugin owner = plugin;
        java.util.logging.Logger logger = owner != null ? owner.getLogger() : java.util.logging.Logger.getLogger("pnCases");
        if (failure == null) logger.log(level, message);
        else logger.log(level, message, failure);
    }
}
